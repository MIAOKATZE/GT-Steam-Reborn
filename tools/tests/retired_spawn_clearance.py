"""Recompute retired-species admission geometry from current sources; never edit prefab assets.

This is a source/prefab proof, not a Minecraft world or entity admission test. A run fails closed
when geometry, support implementation, resource hashes or the approved roster cannot be resolved.
Run after formatting: python -B tools/tests/retired_spawn_clearance.py
"""

import argparse
import bisect
import collections
import csv
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct


KNOWN_HASH_DIFFERENCE = {
    "prefabs/fallen_foundry-v0.json.gz": (
        "4d53055381e992eb3402c4e6273b4dda44b5608a85f8433ccebdb585dce3648b",
        "f2d07a3bcf457fadcf6b0eaffcf7408b5f3886d051a05dc06218c75988ac693d",
    )
}
NUMBER = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?[fFdD]?"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def body(source, declaration):
    """Extract a brace-delimited method/class body, independently of formatter whitespace."""
    found = re.search(declaration, source)
    require(found is not None, "Missing source authority: " + declaration)
    start = source.index("{", found.end())
    depth = 1
    for end in range(start + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start + 1:end]
    raise AssertionError("Unclosed source authority: " + declaration)


def uncomment(source):
    return re.sub(r"/\*.*?\*/|//[^\r\n]*", "", source, flags=re.S)


def f32(literal):
    return struct.unpack("f", struct.pack("f", float(literal.rstrip("fFdD"))))[0]


def read_roster(source, manifest):
    declarations = re.findall(
        r'(\w+)\s*\(\s*"([a-z]+-\d+)"\s*,\s*"[^"\r\n]+"\s*,\s*'
        + NUMBER + r"\s*,\s*(" + NUMBER + r")\s*,\s*(" + NUMBER + r")",
        source,
    )
    require(len(declarations) == 39, "Expected all 39 released enum slots")
    codes = {name: code for name, code, _, _ in declarations}
    dimensions = {code: [f32(width), f32(height)] for _, code, width, height in declarations}
    method = uncomment(body(source, r"public\s+EchoKind\s+canonical\s*\(\s*\)"))
    aliases, pending = {}, []
    for token in re.finditer(r"case\s+(\w+)\s*:|return\s+(\w+)\s*;", method):
        if token.group(1):
            pending.append(token.group(1))
        elif token.group(2) != "this":
            target = token.group(2)
            require(target in codes and pending, "Unsupported canonical branch")
            for name in pending:
                aliases[codes[name]] = codes[target]
            pending = []
        else:
            require(not pending, "Unresolved canonical cases")
    require(len(aliases) == 14, "Expected fourteen retired source identities")
    require(not set(aliases.values()) & set(aliases), "Canonical aliases must not chain or cycle")
    active = {aliases.get(code, code) for code in dimensions}
    active.add("dc-10")
    lines = [line.strip() for line in manifest.splitlines() if line.strip() and not line.lstrip().startswith("#")]
    require(len(lines) == len(set(lines)) == 26 and set(lines) == active, "Active model manifest disagrees with roster")
    flying_names = re.findall(r"this\s*==\s*(\w+)", body(source, r"public\s+boolean\s+flies\s*\(\s*\)"))
    require(flying_names and all(name in codes for name in flying_names), "Unresolved flight authority")
    return dimensions, aliases, {codes[name] for name in flying_names}


def cube_support_authority(source):
    """Require the actual canonical lookup target to instantiate an unchanged full Block cube."""
    source = uncomment(source)
    cube = body(source, r"class\s+Cube\s+extends\s+Block\b")
    require(not re.search(r"setBlockBounds|CollisionBoundingBox|addCollisionBoxes|isSideSolid", cube),
            "Cube support acquired custom collision/bounds; needs a native shape review")
    get = body(source, r"public\s+static\s+Block\s+get\s*\(\s*String\s+id\s*\)")
    uses_aliases = bool(re.fullmatch(r"\s*return\s+REGISTRY\.get\(\s*canonicalId\(\s*id\s*\)\s*\)\s*;\s*", get))
    direct = bool(re.fullmatch(r"\s*return\s+REGISTRY\.get\(\s*id\s*\)\s*;\s*", get))
    require(uses_aliases or direct, "Unresolved RuinsArchitecture.get implementation")
    aliases = dict(re.findall(r'aliases\.put\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)',
                              body(source, r"Map\s*<\s*String\s*,\s*String\s*>\s+visualAliases\s*\(\s*\)")))
    if uses_aliases:
        canonical = body(source, r"public\s+static\s+String\s+canonicalId\s*\(\s*String\s+id\s*\)")
        require(re.search(r"VISUAL_ALIASES\.get\(\s*id\s*\)", canonical)
                and re.search(r"return\s+canonical\s*==\s*null\s*\?\s*id\s*:\s*canonical\s*;", canonical),
                "Unresolved visual alias lookup")
    cubes = set(re.findall(r'register\(\s*"([^"]+)"\s*,\s*new\s+Cube\(', source))
    for name, initializer in re.findall(r"String\s*\[\s*\]\s*(\w+)\s*=\s*\{(.*?)\}\s*;", source, re.S):
        loop = r"for\s*\(\s*String\s+id\s*:\s*" + re.escape(name) + r"\s*\)\s*\{?\s*register\(\s*id\s*,\s*new\s+Cube\("
        if re.search(loop, source):
            cubes.update(re.findall(r'"([^"]+)"', initializer))
    require("slag_masonry" in cubes and "sootstone_tiles" in cubes, "Expected full-cube registrations are absent")
    return cubes, aliases if uses_aliases else {}, uses_aliases


def entity_box(code, at, dimensions):
    width, height = dimensions[code]
    x, y, z = at
    return [x + .5 - width / 2, y, z + .5 - width / 2,
            x + .5 + width / 2, y + height, z + .5 + width / 2]


def horizontal_cells(box):
    return [(x, z) for x in range(math.floor(box[0]), math.floor(box[3] - .0001) + 1)
            for z in range(math.floor(box[2]), math.floor(box[5] - .0001) + 1)]


def chunk_footprint(box):
    return {(x // 16, z // 16) for x, z in horizontal_cells(box)}


def intersects(a, b):
    return all(a[axis] < b[axis + 3] and a[axis + 3] > b[axis] for axis in range(3))


def analyze(root):
    relative_sources = {
        "roster": "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java",
        "spawn": "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterSpawn.java",
        "floors": "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/RuinsArchitecture.java",
        "king": "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/encounter/EntitySilentKing.java",
        "manifest": "src/main/resources/assets/gtsr/liminal/active-models.txt",
        "catalog": "src/main/resources/assets/gtsr/remaster/catalog.json",
    }
    source_bytes = {key: (root / path).read_bytes() for key, path in relative_sources.items()}
    dimensions, retired, flying = read_roster(source_bytes["roster"].decode("utf8"), source_bytes["manifest"].decode("utf8"))
    cubes, floor_aliases, canonical_floor_lookup = cube_support_authority(source_bytes["floors"].decode("utf8"))
    king_size = re.search(r"setSize\(\s*(" + NUMBER + r")\s*,\s*(" + NUMBER + r")\s*\)",
                          source_bytes["king"].decode("utf8"))
    require(king_size is not None, "Missing native king dimensions for actor-pair comparison")
    dimensions["dc-10"] = [f32(king_size.group(1)), f32(king_size.group(2))]
    catalog = json.loads(source_bytes["catalog"])
    require(len(catalog["prefabs"]) == 64, "Expected 64 authored prefabs")
    assets = root / "src/main/resources/assets/gtsr/remaster"
    pins, mismatches = {}, []

    def read_resource(relative, expected):
        path = (assets / relative).resolve()
        require(assets.resolve() in path.parents, "Resource path escapes remaster tree")
        compressed = path.read_bytes()
        raw = gzip.decompress(compressed)
        digest = sha(raw)
        pins[relative] = {"gzipSha256": sha(compressed), "rawSha256": digest, "declaredRawSha256": expected}
        if digest != expected:
            require(KNOWN_HASH_DIFFERENCE.get(relative) == (expected, digest), "Unrecognized resource hash mismatch: " + relative)
            mismatch = {"file": relative, "actual": digest, "declared": expected}
            if mismatch not in mismatches:
                mismatches.append(mismatch)
        return raw

    headers = []
    for descriptor in catalog["prefabs"]:
        header = json.loads(read_resource(descriptor["file"], descriptor["sha256"]))
        require(header["id"] == descriptor["id"] and header["variant"] == descriptor["variant"], "Prefab identity mismatch")
        headers.append(header)
        for part in header["slices"]:
            read_resource(part["file"], part["sha256"])
    require(len(pins) == 804, "Expected complete 804-gzip resource closure")
    nodes, new_overlaps, new_chunks = [], [], []
    checked_pairs = 0
    floor_materials = collections.Counter()
    support_proof = {}
    for header in headers:
        spawns = header.get("metadata", {}).get("spawns", [])
        affected = [(index, spawn) for index, spawn in enumerate(spawns) if spawn.get("code") in retired]
        if not affected:
            continue
        parts = {(part["x"], part["z"]): part for part in header["slices"]}
        require(len(parts) == len(header["slices"]), "Duplicate slice ownership")
        cache = {}

        def at(x, y, z):
            key = (x // 16, z // 16)
            if key not in parts:
                return None
            if key not in cache:
                part = parts[key]
                rows = collections.defaultdict(list)
                for xx, yy, zz, length, palette in json.loads(read_resource(part["file"], part["sha256"])):
                    require(length > 0 and 0 <= palette < len(header["palette"]), "Invalid slice run")
                    require(xx // 16 == (xx + length - 1) // 16 == key[0] and zz // 16 == key[1], "Wrong slice owner")
                    rows[(yy, zz)].append((xx, xx + length, header["palette"][palette]))
                for runs in rows.values():
                    runs.sort()
                    require(all(left[1] <= right[0] for left, right in zip(runs, runs[1:])), "Overlapping prefab runs")
                cache[key] = rows
            runs = cache[key].get((y, z), [])
            index = bisect.bisect_right(runs, (x, math.inf, "")) - 1
            if index >= 0 and runs[index][0] <= x < runs[index][1]:
                return runs[index][2]
            return None

        def examine(code, position):
            box = entity_box(code, position, dimensions)
            occupied, unknown, floors = [], [], []
            for x, z in horizontal_cells(box):
                for y in range(math.floor(box[1]), math.floor(box[4] - .0001) + 1):
                    material = at(x, y, z)
                    if material is None:
                        unknown.append([x, y, z])
                    elif material != "minecraft:air#0":
                        occupied.append([x, y, z, material])
                y = math.floor(position[1]) - 1
                material = at(x, y, z)
                if material not in (None, "minecraft:air#0"):
                    floors.append([x, y, z, material])
            return {"box": box, "bodyNonAir": occupied, "unknownBody": unknown, "floorNonAir": floors,
                    "conservativeClear": not occupied and not unknown and (code in flying or bool(floors))}

        for index, spawn in affected:
            position = [spawn[axis] for axis in ("x", "y", "z")]
            require(all(type(value) is int for value in position), "Spawn coordinates need explicit integral source semantics")
            source = spawn["code"]
            target = retired[source]
            original, replacement = examine(source, position), examine(target, position)
            require(original["conservativeClear"] and replacement["conservativeClear"],
                    "Authored spawn is not clear: " + header["id"] + ":" + str(index))
            for floor in replacement["floorNonAir"] + original["floorNonAir"]:
                material = floor[3]
                require(material.startswith("gtsr:ruins_"), "Unproved support collision shape: " + material)
                identifier = material.split("#", 1)[0][len("gtsr:ruins_"):]
                target_id = floor_aliases.get(identifier, identifier)
                require(target_id in cubes and target_id not in floor_aliases,
                        "Support is not a proven full cube: " + material)
                support_proof[material] = {"sourceId": identifier, "resolvedId": target_id,
                                           "implementation": "RuinsArchitecture.Cube extends Block; no bounds/collision override",
                                           "canonicalLookup": canonical_floor_lookup, "collisionBox": [0, 0, 0, 1, 1, 1]}
            floor_materials.update(floor[3] for floor in replacement["floorNonAir"])
            extra = chunk_footprint(replacement["box"]) - chunk_footprint(original["box"])
            if extra:
                new_chunks.append({"prefab": header["id"], "index": index, "extra": sorted(extra)})
            nodes.append({"prefab": header["id"], "variant": header["variant"], "index": index,
                          "node": spawn.get("id"), "source": source, "target": target, "at": position,
                          "role": spawn.get("role"), "module": spawn.get("module"), "spawn": spawn.get("spawn", True),
                          "original": original, "replacement": replacement, "sameFloorCandidates": []})
        actors = [(index, spawn) for index, spawn in enumerate(spawns)
                  if spawn.get("spawn", True) and spawn.get("code") in dimensions]
        for offset, (index, spawn) in enumerate(actors):
            for other_index, other in actors[offset + 1:]:
                if spawn["code"] not in retired and other["code"] not in retired:
                    continue
                checked_pairs += 1
                old_a = entity_box(spawn["code"], [spawn[k] for k in ("x", "y", "z")], dimensions)
                old_b = entity_box(other["code"], [other[k] for k in ("x", "y", "z")], dimensions)
                new_a = entity_box(retired.get(spawn["code"], spawn["code"]), [spawn[k] for k in ("x", "y", "z")], dimensions)
                new_b = entity_box(retired.get(other["code"], other["code"]), [other[k] for k in ("x", "y", "z")], dimensions)
                if intersects(new_a, new_b) and not intersects(old_a, old_b):
                    new_overlaps.append({"prefab": header["id"], "indices": [index, other_index]})
    require(len(nodes) == 124 and len({node["prefab"] for node in nodes}) == 19, "Approved affected-node closure changed")
    require(not new_overlaps and not new_chunks, "New actor overlap or chunk ownership needs review")
    require(all(node["spawn"] for node in nodes), "Unexpected disabled affected node")
    script_relative = Path(__file__).resolve().relative_to(root).as_posix()
    input_hashes = {relative_sources[key]: sha(data) for key, data in source_bytes.items()}
    input_hashes[script_relative] = sha(Path(__file__).read_bytes())
    return {
        "status": "PASS",
        "kind": "Source-prefab geometry admission proof: explicit AIR covers complete old/new AABBs and supports resolve to full Cube blocks; not native world/entity admission",
        "prefabs": len(headers), "affectedPrefabs": 19,
        "summary": {"total": len(nodes), "newConservativeClear": len(nodes)},
        "nodes": nodes, "floorMaterials": dict(floor_materials), "supportImplementationProof": support_proof,
        "sourceOccurrences": dict(collections.Counter(node["source"] for node in nodes)),
        "roles": dict(collections.Counter(node["role"] for node in nodes)),
        "spawnFlags": dict(collections.Counter(str(node["spawn"]) for node in nodes)),
        "absentRetiredCodes": sorted(set(retired) - {node["source"] for node in nodes}),
        "resources": pins, "resourceHashChecks": len(pins), "inputFileSha256": input_hashes,
        "preexistingDeclaredHashMismatches": mismatches,
        "actorOverlapScan": {"checkedPairs": checked_pairs, "newOverlaps": new_overlaps},
        "newChunkFootprints": new_chunks,
        "recommendedProductionChange": "none: no authored-coordinate adjustment or fallback justified by checked geometry",
        "nativeAdmissionStatus": "NOT_RUN by this script",
    }


def csv_report(report):
    output = io.StringIO(newline="")
    writer = csv.writer(output)
    writer.writerow(["prefab", "variant", "index", "node", "role", "module", "source", "canonical", "x", "y", "z",
                     "old_AABB", "new_AABB", "old_clear", "new_clear", "new_support_cells", "new_support_materials"])
    for node in report["nodes"]:
        writer.writerow([node["prefab"], node["variant"], node["index"], node["node"], node["role"], node["module"],
                         node["source"], node["target"], *node["at"], json.dumps(node["original"]["box"]),
                         json.dumps(node["replacement"]["box"]), node["original"]["conservativeClear"],
                         node["replacement"]["conservativeClear"], len(node["replacement"]["floorNonAir"]),
                         ";".join(sorted({floor[3] for floor in node["replacement"]["floorNonAir"]}))])
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    output = args.out or root.parent / ".artifacts/gtsr-dimension-trim/spawn-clearance"
    output.mkdir(parents=True, exist_ok=True)
    result = output / "prefab-spawn-clearance.json"
    try:
        report = analyze(root)
        (output / "prefab-spawn-nodes.csv").write_text(csv_report(report), encoding="utf8", newline="")
        result.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    except Exception as failure:
        # Never leave a previous PASS JSON usable after an unsuccessful current-source recomputation.
        result.write_text(json.dumps({"status": "FAIL", "error": str(failure)}, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        raise
    print("RETIRED_SPAWN_CLEARANCE PASS prefabs=64 resources=804 nodes=124 floors="
          + str(sum(report["floorMaterials"].values())) + " actorPairs=" + str(report["actorOverlapScan"]["checkedPairs"])
          + " newOverlaps=0 newChunks=0 preexistingHashDifferences=" + str(len(report["preexistingDeclaredHashMismatches"])))


if __name__ == "__main__":
    main()
