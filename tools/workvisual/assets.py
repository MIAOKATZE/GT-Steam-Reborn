"""Current resource closure and deterministic R5 export, separate from authoring provenance."""
import importlib.util
import json
import re
import zipfile
from pathlib import Path

from common import SCHEMA, ROOT, canonical, check_finite_tree, file_sha, parse_json, relative_name, rooted, sha, walk_sprites, write_json

CRITICAL = "assets/gtsr/critical/"
MACHINE = "assets/gtsr/machinevisual/"
EFFECTS = "assets/gtsr/workeffects/"


class Resources:
    def __init__(self, directory=None, jar=None):
        if (directory is None) == (jar is None):
            raise ValueError("Choose exactly one resource directory or product JAR")
        self.directory = Path(directory).resolve() if directory else None
        self.jar = zipfile.ZipFile(jar) if jar else None
        self.identity = {"kind": "jar", "sha256": file_sha(jar)} if jar else {"kind": "source_tree"}
        if self.jar:
            names = self.jar.namelist()
            if len(names) != len(set(names)):
                raise ValueError("Duplicate archive entry")
            self.names = {entry.filename for entry in self.jar.infolist() if not entry.is_dir()}
        else:
            self.names = {p.relative_to(self.directory).as_posix() for p in self.directory.rglob("*") if p.is_file()}

    def close(self):
        if self.jar:
            self.jar.close()

    def read(self, name):
        relative_name(name)
        raw = self.jar.read(name) if self.jar else rooted(self.directory, name).read_bytes()
        if len(raw) > 32 * 1024 * 1024:
            raise ValueError("Resource byte budget exceeded: " + name)
        return raw

    def json(self, name):
        value = parse_json(self.read(name))
        check_finite_tree(value)
        return value


def palette_document(resources):
    spec = importlib.util.spec_from_file_location("workvisual_material_palette", ROOT / "tools/critical/material_palette.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.expand_palette(resources.json(CRITICAL + "materials.json"))


def _geometry(model, palette, name):
    rows = model["blocks"]
    if not rows or len(rows) > 250000:
        raise ValueError("Empty or oversized geometry: " + name)
    occupied = set()
    for row in rows:
        if len(row) != 5 or row[3] not in palette or not isinstance(row[4], str):
            raise ValueError("Invalid geometry row/material: " + name)
        point = tuple(row[:3])
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) for v in point) or point in occupied:
            raise ValueError("Invalid/duplicate geometry coordinate: " + name)
        occupied.add(point)
    order = model.get("meta", {}).get("constructionOrder")
    return {"blocks": len(rows), "groups": len(model["animationGroups"]),
            "blockOrderSha256": sha(canonical(rows)),
            "constructionOrderSha256": sha(canonical(order)) if order is not None else None,
            "constructionOrderFullPermutation": (len(order) == len(rows) and set(order) == set(range(len(rows)))) if order is not None else None,
            "layoutRevision": model.get("meta", {}).get("runtimeLayoutRevision")}


def runtime_manifest(resources):
    """Rebuild from live references; never trust the stale texture list in source-sha256.json."""
    palette = palette_document(resources)
    binding = resources.json(CRITICAL + "source-sha256.json")
    geometry_names = sorted(n for n in resources.names if n.startswith(CRITICAL + "models/") and n.endswith(".geometry.json"))
    if not geometry_names:
        raise ValueError("No critical geometries found")
    closure = {CRITICAL + "materials.json", CRITICAL + "source-sha256.json"}
    geometries = []
    for name in geometry_names:
        raw = resources.read(name)
        model = resources.json(name)
        info = _geometry(model, palette, name)
        # Existing geometry bytes include physical construction indices and must remain exact.
        old = binding.get("files", {}).get("src/main/resources/" + name)
        if old is not None and sha(raw) != old:
            raise ValueError("Historical construction geometry changed: " + name)
        closure.add(name)
        geometries.append({"path": name, **info})
    snapshots = binding.get("compatibilitySnapshots", {})
    if not snapshots:
        raise ValueError("No historical compatibility snapshot identities")
    for slug, entry in snapshots.items():
        name = CRITICAL + "models/" + relative_name(slug) + ".geometry.json"
        if name not in closure or sha(resources.read(name)) != entry["sha256"]:
            raise ValueError("Frozen historical geometry missing/changed: " + name)
    sprites = set(walk_sprites(palette))
    # Legacy materials have implicit top/side paths in CriticalMaterials, not sprite properties.
    for properties in palette.values():
        identifier = properties["id"]
        if not identifier.startswith("v2_"):
            sprites.update(("gtsr:critical/" + identifier, "gtsr:critical/" + identifier + "__top"))
            if properties["emissionStrength"] > 0:
                sprites.add("gtsr:critical/" + identifier + "_emission")
    machine_definitions = sorted(n for n in resources.names if n.startswith(MACHINE) and n.endswith(".json"))
    if not machine_definitions:
        raise ValueError("No current machinevisual definition resources")
    for name in machine_definitions:
        sprites.update(walk_sprites(resources.json(name)))
        closure.add(name)
    effects = sorted(n for n in resources.names if n.startswith(EFFECTS))
    if not effects:
        raise ValueError("No current workeffects provenance resources")
    closure.update(effects)
    # Effects authored for this pipeline may include PNG/metadata beneath the same namespace.
    closure.update(n for n in resources.names if n.startswith("assets/gtsr/textures/") and
                   ("/machinevisual/" in n or "/workeffects/" in n))
    external = []
    animated = 0
    for sprite in sorted(sprites):
        if not isinstance(sprite, str) or sprite.count(":") != 1:
            raise ValueError("Invalid sprite identifier: " + str(sprite))
        namespace, path = sprite.split(":")
        relative_name(path)
        if namespace != "gtsr":
            external.append(sprite)
            continue
        name = "assets/gtsr/textures/blocks/" + path + ".png"
        raw = resources.read(name)
        # IHDR is sufficient for frame/metadata validity and keeps inventory Pillow-free.
        if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
            raise ValueError("Invalid PNG header: " + name)
        width = int.from_bytes(raw[16:20], "big")
        height = int.from_bytes(raw[20:24], "big")
        if not width or not height or height % width:
            raise ValueError("Invalid block sprite dimensions: " + name)
        closure.add(name)
        metadata = name + ".mcmeta"
        if metadata in resources.names:
            animation = resources.json(metadata)["animation"]
            frame_count = height // width
            for frame in animation.get("frames", []):
                index = frame["index"] if isinstance(frame, dict) else frame
                if not isinstance(index, int) or not 0 <= index < frame_count:
                    raise ValueError("Invalid animation frame: " + metadata)
                if isinstance(frame, dict) and frame.get("time", 1) <= 0:
                    raise ValueError("Invalid animation time: " + metadata)
            if animation.get("frametime", 1) <= 0:
                raise ValueError("Invalid frametime: " + metadata)
            closure.add(metadata)
        elif height > width:
            raise ValueError("Animated sprite lacks mcmeta: " + name)
        animated += height > width
    # PNG and mcmeta bytes are separately hashed; aliases are allowed to share exact payloads.
    files = [{"path": n, "bytes": len(resources.read(n)), "sha256": sha(resources.read(n))} for n in sorted(closure)]
    content = sha(canonical(files))
    return {"schema": SCHEMA, "status": "PASS", "kind": "runtime_resource_closure", "contentSha256": content,
            "resourceSource": resources.identity, "files": files, "geometries": geometries,
            "counts": {"geometry": len(geometries), "frozenSnapshots": len(snapshots), "materialAliases": len(palette),
                       "materialDefinitions": len({p["id"] for p in palette.values()}), "sprites": len(sprites),
                       "animatedSprites": animated, "files": len(files)}, "externalSprites": external,
            "legacyWarnings": [g["path"] + ": released constructionOrder is not a full block-index permutation; exact bytes preserved"
                               for g in geometries if g["constructionOrderFullPermutation"] is False],
            "boundary": "Current resource bytes and historical construction order only; native GT icons require the locked host JAR; no GL/game acceptance.",
            "reloadBoundary": "This inventory does not claim live geometry reload; existing critical geometry caches retain their production behavior."}


def authoring_manifest(repo, task_root, r5_root, config):
    if config.get("schema") != SCHEMA or not config.get("sources"):
        raise ValueError("Unsupported/empty authoring source configuration")
    roots = {"repo": Path(repo), "task": Path(task_root), "r5": Path(r5_root)}
    entries = []
    for source in config["sources"]:
        path = rooted(roots[source["root"]], source["path"])
        entries.append({**source, "bytes": path.stat().st_size, "sha256": file_sha(path)})
    motions = parse_json((Path(repo) / "src/main/resources" / MACHINE / "motions.json").read_bytes())
    effects = parse_json((Path(repo) / "src/main/resources" / EFFECTS / "provenance.json").read_bytes())
    source_paths = {"src/main/java/com/miaokatze/gtsr/common/machine/MTE" + value["controller"] + ".java" for value in motions["definitions"]}
    source_paths.update(value["source"] for value in effects["machines"])
    for name in sorted(source_paths):
        path = rooted(repo, name)
        entries.append({"root": "repo", "path": name, "role": "actual_shape_and_native_material_source",
                        "bytes": path.stat().st_size, "sha256": file_sha(path)})
    binding = parse_json((Path(repo) / "src/main/resources" / CRITICAL / "source-sha256.json").read_bytes())
    r5 = binding["authorities"]["v2"]["r5Runtime"]
    for slug, value in sorted(r5["models"].items()):
        name = "models/" + relative_name(slug) + ".geometry.json"
        path = rooted(r5_root, name)
        digest = file_sha(path)
        if digest != value["sourceSha256"]:
            raise ValueError("R5 author source hash mismatch: " + name)
        entries.append({"root": "r5", "path": name, "role": "approved_r5_authority",
                        "sha256": digest, "bytes": path.stat().st_size,
                        "runtimeTransform": "v2_material_alias_and_approved_levitation_metadata"})
    override = rooted(r5_root, "preview/levitation.js")
    if file_sha(override) != r5["levitationOverrideSha256"]:
        raise ValueError("Approved levitation override hash mismatch")
    entries.append({"root": "r5", "path": "preview/levitation.js", "role": "approved_r5_override",
                    "sha256": file_sha(override), "bytes": override.stat().st_size})
    return {"schema": SCHEMA, "status": "PASS", "kind": "authoring_source_manifest", "files": entries,
            "contentSha256": sha(canonical(entries)), "roots": {key: {"kind": key} for key in roots},
            "licenseMetadata": config["licenseMetadata"],
            "boundary": "Author input identity is independent of runtime closure. R5 export alone does not reconstruct revision33 supply hull; license ownership is not inferred."}


def export_r5(source_root, binding_path, out):
    binding = parse_json(Path(binding_path).read_bytes())
    authority = binding["authorities"]["v2"]["r5Runtime"]
    override_raw = rooted(source_root, "preview/levitation.js").read_bytes()
    if sha(override_raw) != authority["levitationOverrideSha256"]:
        raise ValueError("Override hash differs from approved authority")
    match = re.search(r"window\.V2_LEVITATION_OVERRIDES\s*=\s*(\{.*\});\s*\nwindow\.V2_APPLY", override_raw.decode("utf-8"), re.S)
    if match is None:
        raise ValueError("Approved levitation envelope changed")
    overrides = parse_json(match.group(1))
    records = []
    for slug, expected in sorted(authority["models"].items()):
        name = "models/" + relative_name(slug) + ".geometry.json"
        source = rooted(source_root, name)
        raw = source.read_bytes()
        if sha(raw) != expected["sourceSha256"]:
            raise ValueError("Unapproved R5 source: " + name)
        model = parse_json(raw)
        check_finite_tree(model)
        before = [row[:] for row in model["blocks"]]
        if slug in overrides:
            override = overrides[slug]
            for group, data in override["groups"].items():
                actual = sorted(tuple(row[:3]) for row in model["blocks"] if row[4] == group)
                if actual != sorted(map(tuple, data["cells"])):
                    raise ValueError("Override group membership changed: " + slug + ":" + group)
                model["animationGroups"][group]["levitationBob"] = data["levitationBob"]
            model["meta"]["r5Levitation"] = override["meta"]
        for row in model["blocks"]:
            row[3] = "v2_" + row[3]
        if "materialCounts" in model:
            model["materialCounts"] = {"v2_" + key: value for key, value in model["materialCounts"].items()}
        model["meta"].update({"runtimeLayoutRevision": 31, "runtimeGeometryVersion": 2,
                              "approvedR5SourceSha256": sha(raw), "runtimeImport": "tools/critical/import_r5_v31.py"})
        if [row[:3] + [row[3].removeprefix("v2_")] + row[4:] for row in model["blocks"]] != before:
            raise ValueError("Construction/member order changed in export")
        # Match the existing importer encoding byte-for-byte, including its provenance key.
        # The approved importer ran on Windows; its recorded canonical output ends CRLF.
        payload = (json.dumps(model, ensure_ascii=False, separators=(",", ":")) + "\r\n").encode("utf-8")
        target = rooted(out, "v2_" + slug + ".geometry.json")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        digest = sha(payload)
        if digest != expected["runtimeSha256"]:
            raise ValueError("Deterministic R5 output differs from recorded importer: " + slug)
        records.append({"source": name, "sourceSha256": sha(raw), "path": target.name, "sha256": digest,
                        "blocks": len(before), "constructionMemberOrderPreserved": True})
    result = {"schema": SCHEMA, "status": "PASS", "kind": "deterministic_r5_export", "files": records,
              "boundary": "Reproduces R5 revision31 only, not subsequent revision32/33 supply-hull amendments. Output is staged; production resources are never overwritten."}
    write_json(Path(out) / "export-manifest.json", result)
    return result


def export_motions(models_path, profile, out):
    """Explicit GTSR import profile for the approved full-size block source, not an editor."""
    source = parse_json(Path(models_path).read_bytes())
    definitions = []
    for model in source["models"]:
        controller = model["id"]
        if controller not in profile["machines"]:
            continue
        old, new = model["old"], model["new"]
        layers = [list(layer) for layer in new.get("rawLayers", old["rawLayers"])]
        offset = new.get("offset", old["offset"])
        if controller == "AirCompressor":
            # The source's new blocks include this approved glass; its old rawLayers do not.
            for y in (0, 1):
                for z in (1, 2, 3):
                    row = list(layers[offset[1] - y][z + offset[2]])
                    row[0] = row[-1] = "G"
                    layers[offset[1] - y][z + offset[2]] = "".join(row)
        blocks = {block["id"]: block for block in new["blocks"]}
        groups = []
        for group in new["motionGroups"]:
            rotating = group["type"] == "rotate"
            if group["type"] not in ("rotate", "inward_translate"):
                raise ValueError("Unsupported approved motion kind: " + group["type"])
            members = []
            for member in group["memberIds"]:
                block = blocks[member]
                family = block.get("textureFamily")
                if family not in ("gear", "pipe", "frame"):
                    raise ValueError("Unsupported native moving material: " + str(family))
                members.append({key: block[key] for key in ("x", "y", "z")} | {"material": family})
            groups.append({"id": group["id"], "kind": "rotate_y" if rotating else "translate", "axis": group["axis"],
                           "travel": 0 if rotating else group["direction"] * group["maxDisplacement"],
                           "periodMs": 9666 if rotating else round(group["periodSeconds"] * 1000),
                           "pivot": group.get("pivot", [0, 0, 0]), "members": members})
        materials = {key: {"role": value["role"], "blockChoices": value.get("blockChoices", [])}
                     for key, value in old["elements"].items() if key not in (" ", "~")}
        if controller == "AirCompressor":
            materials["G"] = {"role": "reinforced_glass", "blockChoices": [["GregTechAPI.sBlockGlass1", 10]]}
        for properties in materials.values():
            if not properties["blockChoices"]:
                properties["blockChoices"] = profile["nativeRoleMaterials"][properties["role"]]
        definitions.append({"id": profile["machines"][controller]["id"], "controller": controller, "schema": SCHEMA,
                            "shapeLayers": layers, "offset": offset, "tokenMaterials": materials,
                            "skipTokens": [" "], "requiredAirTokens": [], "groups": groups})
    if {definition["controller"] for definition in definitions} != set(profile["machines"]):
        raise ValueError("Approved motion source omits a selected controller")
    document = {"schema": SCHEMA, "coordinates": profile["coordinates"],
                "source": "TASK03 final scope 2026-11-01; visual-reference runtime models revision v7", "definitions": definitions}
    from motion import validate_definitions
    validate_definitions(document, profile)
    write_json(out, document)
    return {"schema": SCHEMA, "status": "PASS", "kind": "approved_motion_export", "sourceSha256": file_sha(models_path),
            "outputSha256": file_sha(out), "definitionContentSha256": sha(canonical(document)),
            "boundary": "Deterministic GTSR profile/staged export; browser/reference source is not runtime acceptance."}
