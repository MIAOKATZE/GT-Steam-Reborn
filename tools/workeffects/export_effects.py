"""Deterministic TASK03 work effects: current Java shape tokens -> typed catalog and selected pixel foreground.

No world, recipe, NBT, or model geometry is changed. --check compares resources bytewise and Java ignoring formatting.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/miaokatze/gtsr"
RES = ROOT / "src/main/resources/assets/gtsr/workeffects"
MACHINES = [
    ("LargeSteamFurnace", "C", None, None, 27),
    ("CrustSteamBorer", "E", None, None, 4),
    ("VeinSteamPyrolyzer", "D", None, None, 33),
    ("LargeCokeOven", "E", None, (4, -5, 1), 9),
    ("SiemensMartinFurnace", "E", None, (0, -16, 3), 91),
    ("ReinforcedBrickBlastFurnace", "B", None, (0, -3, 1), 9),
    ("LargeGeothermalSteamBoiler", "D", "G", None, 21),
    ("LargeSolarOverpressureArray", None, "F", None, 0),
]


def shape_and_offsets(source: str):
    match = re.search(r"SHAPE_MAIN\s*=\s*\{", source)
    start = match.end() - 1 if match else source.index("{", source.index("new String[][]"))
    depth, quoted = 0, False
    for end in range(start, len(source)):
        ch = source[end]
        if ch == '"':
            quoted = not quoted
        if not quoted:
            depth += (ch == "{") - (ch == "}")
            if depth == 0:
                break
    body = source[start:end + 1]
    layers = [re.findall(r'"([^"\\]*)"', layer) for layer in re.findall(r"\{([^{}]*)\}", body)]
    offsets = [int(re.search(name + r"\s*=\s*(\d+)", source).group(1))
               for name in ("HORIZONTAL_OFF_SET", "VERTICAL_OFF_SET", "DEPTH_OFF_SET")]
    return layers, offsets


def cells(layers, offsets, token):
    return [(a-offsets[0], b-offsets[1], c-offsets[2], a, b, c)
            for b, layer in enumerate(layers) for c, row in enumerate(layer)
            for a, value in enumerate(row) if value == token]


def cell_java(abc):
    return f"new Cell({abc[0]}, {abc[1]}, {abc[2]})"


def png():
    # Exact selected runtime/app.js proposal: the three native grate apertures only.
    pixels = [(0, 0, 0, 0)] * 256
    for left in (4, 7, 10):
        for y in range(4, 12):
            heat = 1 - abs(y - 8.5) / 5.5
            color = (round(190+60*heat), round(48+107*heat), 10, round(255*(.65+.30*heat)))
            for x in (left, left+1):
                pixels[y*16+x] = color
    for x in (7, 8):
        pixels[9*16+x] = (255, 198, 75, round(255*.95))
    raw = b"".join(b"\x00"+bytes(c for p in pixels[y*16:(y+1)*16] for c in p) for y in range(16))

    def chunk(kind, data):
        return struct.pack(">I", len(data))+kind+data+struct.pack(">I", zlib.crc32(kind+data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def outputs():
    imports, constants, choices, records = [], [], [], []
    for name, fire_token, particle_token, proposal, expected_fireboxes in MACHINES:
        path = JAVA / "common/machine" / f"MTE{name}.java"
        source = path.read_text(encoding="utf-8")
        layers, offsets = shape_and_offsets(source)
        fireboxes = cells(layers, offsets, fire_token) if fire_token else []
        assert len(fireboxes) == expected_fireboxes, (name, len(fireboxes))
        outlets = cells(layers, offsets, particle_token) if particle_token else []
        if particle_token:
            assert len(outlets) == 9, name
        key = re.sub(r"(?<!^)(?=[A-Z])", "_", name).upper()
        imports.append(f"import com.miaokatze.gtsr.common.machine.MTE{name};")
        constant = [f'    public static final WorkEffectDefinition {key} = new WorkEffectDefinition(',
                    f'        "{name}",', "        ImmutableList.of(" + ", ".join(cell_java(c) for c in fireboxes) + "),"]
        emitters, emitter_records = [], []
        for off in outlets:
            emitter_id = f"{name}:source-{particle_token}:{off[3]}:{off[4]}:{off[5]}"
            emitters.append(f'new Emitter("{emitter_id}", {cell_java(off)}, null, 0D, true)')
            emitter_records.append({"id": emitter_id, "abc": list(off[:3]), "origin": "existing", "centerYOffset": 0})
        if proposal:
            above = (proposal[0], proposal[1]-1, proposal[2])
            emitter_id = f"{name}:selected-stack-cloud"
            emitters.append(f'new Emitter("{emitter_id}", {cell_java(proposal)}, {cell_java(above)}, .55D, false)')
            emitter_records.append({"id": emitter_id, "abc": list(proposal), "aboveABC": list(above),
                                    "origin": "selected-proposal", "centerYOffset": .55})
        constant.append("        ImmutableList.of(" + ", ".join(emitters) + "));")
        constants.append("\n".join(constant))
        choices.append(f"        if (host.getClass() == MTE{name}.class) return {key};")
        records.append({"machine": name, "source": path.relative_to(ROOT).as_posix(),
                        "fireboxToken": fire_token, "fireboxesABC": [list(c[:3]) for c in fireboxes],
                        "nativeMaterial": "gregtech:sBlockCasings3 meta 13/14, horizontal faces only" if fire_token else None,
                        "emitters": emitter_records})
    # Approved R10 apertures, after preview X reflection and the R11 controller move to [-2, 2, 0].
    # Display XYZ is [-(modelX + 2), modelY - 2, modelZ]; StructureLib ABC reverses display Y.
    kinetic_sources = [("front-left", (-5, -3, -4.6), "impact"),
                       ("front-right", (-3, -3, -4.6), "impact"),
                       ("chimney", (0, -6.6, 2), "working")]
    imports.append("import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;")
    constants.append("\n".join([
        "    public static final WorkEffectDefinition KINETIC_PROCESSING_ARRAY = new WorkEffectDefinition(",
        '        "KineticProcessingArray",', "        ImmutableList.of(),", "        ImmutableList.of(" +
        ", ".join(f'new Emitter("KineticProcessingArray:{name}", {a}D, {b}D, {c}D)'
                  for name, (a, b, c), _ in kinetic_sources) + "));"]))
    choices.append("        if (host.getClass() == MTEKineticProcessingArray.class) return KINETIC_PROCESSING_ARRAY;")
    records.append({"machine": "KineticProcessingArray",
                    "source": "tools/workvisual/export_rebuilt_turbines.js (approved R10 preview + R11 controller move)",
                    "fireboxToken": None, "fireboxesABC": [], "nativeMaterial": None,
                    "emitters": [{"id": f"KineticProcessingArray:{name}", "preciseABC": list(point),
                                  "origin": "approved-preview-aperture", "schedule": schedule}
                                 for name, point, schedule in kinetic_sources],
                    "periodMs": 3000, "impactPhaseMsInclusive": [1695, 2070],
                    "impactClientTickInterval": 2, "impactParticlesPerRequest": 2,
                    "chimneyClientTickInterval": 10, "worldXZJitterWidth": .12,
                    "airPolicy": "actual jittered spawn cell must be loaded air; no block hiding bypass"})
    text = "\n".join([
        "package com.miaokatze.gtsr.common.workeffects;", "", "import com.google.common.collect.ImmutableList;", "",
        "import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;", *imports,
        "import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Cell;",
        "import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Emitter;", "",
        "/** Generated by tools/workeffects/export_effects.py from existing shape tokens. Do not edit by hand. */",
        "public final class WorkEffectDefinitions {", "", *constants, "",
        "    private WorkEffectDefinitions() {}", "",
        "    public static WorkEffectDefinition forHost(MachineVisualHost host) {", *choices,
        "        return null;", "    }", "}", ""])
    foreground = png()
    manifest = {"schema": "gtsr.workeffects.v1", "coordinates": "StructureLib ABC; B down; world +Y particle velocity",
                "machines": records, "tcds": {"existingAnchors": 20, "owner": "original TcdsParticleFxClient chain"},
                "cloudPolicy": {"worldVelocity": [0, .3, 0], "worldXZJitterWidth": .8,
                                "thermalExpectedPerControllerTick": "2 * existing native quantized mHeat; sample nine original points",
                                "selectedOutletRate": "one per ten client ticks while authoritative work snapshot is fresh and working",
                                "standardBudgetPerClientTick": 128, "lowBudgetPerClientTick": 32,
                                "maxQueuedOwnersPerTick": 256, "maxParticlesPerOwnerPerTick": 4,
                                "rangeBlocks": 64, "minimumParticlesSetting": "OFF",
                                "ownerPolicy": "exact loaded controller and world; one per owner per budget round"},
                "foreground": {"resource": "assets/gtsr/workeffects/firebox_grate.png",
                               "sha256": hashlib.sha256(foreground).hexdigest(), "maskedPixels": 48,
                               "apertures": {"x": [4, 5, 7, 8, 10, 11], "yInclusive": [4, 11],
                                             "nativeApertureMaxRGBRange": [0, 17],
                                             "same48PixelsInBronzeAndSteel": True},
                               "source": "TASK03 visual-reference/runtime/app.js proposal:firebox_heat_foreground",
                               "baseTextures": ["gregtech:iconsets/MACHINE_CASING_FIREBOX_BRONZE", "gregtech:iconsets/MACHINE_CASING_FIREBOX_STEEL"],
                               "baseTextureSHA256": ["9da8c4276feb8d1acc4e2a20be0cd4101da04f6faaf63c4675b56a9976e081cf",
                                                     "37a904e7c4f6a78c35a70e003557f332b499cf000b282d59841fca19ff799016"],
                               "resourcePackPolicy": "reload-time exact base PNG hash; incompatible material keeps native static base",
                               "nativeActive": False},
                "gameValidation": "NOT_RUN"}
    return {JAVA / "common/workeffects/WorkEffectDefinitions.java": text.encode(),
            RES / "firebox_grate.png": foreground,
            RES / "provenance.json": (json.dumps(manifest, ensure_ascii=False, indent=2)+"\n").encode()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = outputs()
    renderer = (JAVA / "client/workeffects/WorkEffectRenderer.java").read_text(encoding="utf-8")
    foreground_hash = re.search(r'FOREGROUND_SHA\s*=\s*"([a-f0-9]{64})"', renderer)
    assert foreground_hash and foreground_hash.group(1) == hashlib.sha256(result[RES / "firebox_grate.png"]).hexdigest(), "runtime foreground SHA gate is stale"
    for path, content in result.items():
        if args.check:
            actual = path.read_bytes() if path.is_file() else b""
            if path.suffix == ".java":
                def java_tokens(value):
                    imports = sorted(re.findall(rb"(?m)^import ([^;]+);", value))
                    body = re.sub(rb"(?m)^import [^;]+;", b"", value)
                    return b";".join(imports) + re.sub(rb"\s+", b"", body)
                actual, content = java_tokens(actual), java_tokens(content)
            assert actual == content, f"stale export: {path.relative_to(ROOT)}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    print(f"{'PASS' if args.check else 'EXPORTED'}: {len(result)} outputs; 194 firebox cells; 18 existing thermal anchors + 3 selected outlets + 3 KPA apertures; game=NOT_RUN")


if __name__ == "__main__":
    main()
