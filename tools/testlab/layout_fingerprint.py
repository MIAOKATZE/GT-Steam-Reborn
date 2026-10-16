#!/usr/bin/env python3
"""Read a stopped testlab world without changing blocks or NBT.

The fixture journal supplies exact authorized positions. Anvil block IDs/metadata,
GT meta IDs/facings and complete persisted tile NBT are retained as evidence.
Compare only geometry/identity as a compatibility gate: inventories may change
during real production and are reported separately, never silently ignored.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct
import zlib


class NbtReader:
    def __init__(self, data: bytes):
        self.data, self.offset = data, 0

    def take(self, count: int) -> bytes:
        result = self.data[self.offset:self.offset + count]
        if len(result) != count:
            raise ValueError("Truncated NBT")
        self.offset += count
        return result

    def number(self, fmt: str):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(">" + fmt)))[0]

    def string(self) -> str:
        return self.take(self.number("H")).decode("utf-8", errors="replace")

    def payload(self, kind: int):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return list(self.take(self.number("i")))
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.number("B"), self.number("i")
            return [self.payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                child = self.number("B")
                if child == 0:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        if kind in (11, 12):
            return [self.number("i" if kind == 11 else "q") for _ in range(self.number("i"))]
        raise ValueError(f"Unknown NBT kind {kind}")

    def root(self):
        kind = self.number("B")
        self.string()
        return self.payload(kind)


def digest(value) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=False).encode("utf-8")).hexdigest()


def nibble(values, index):
    return (values[index >> 1] >> ((index & 1) * 4)) & 15 if values else 0


def read_chunk(dimension: Path, cx: int, cz: int):
    path = dimension / "region" / f"r.{cx >> 5}.{cz >> 5}.mca"
    with path.open("rb") as stream:
        stream.seek(((cx & 31) + (cz & 31) * 32) * 4)
        location = int.from_bytes(stream.read(4), "big")
        if location >> 8 == 0:
            raise ValueError(f"Journal references absent saved chunk {cx},{cz}")
        stream.seek((location >> 8) * 4096)
        length = int.from_bytes(stream.read(4), "big")
        compression = stream.read(1)
        payload = stream.read(length - 1)
    if compression == b"\x01":
        payload = gzip.decompress(payload)
    elif compression == b"\x02":
        payload = zlib.decompress(payload)
    elif compression != b"\x03":
        raise ValueError(f"Unsupported region compression {compression!r}")
    level = NbtReader(payload).root()["Level"]
    if level["xPos"] != cx or level["zPos"] != cz:
        raise ValueError(f"Wrong saved chunk identity for {cx},{cz}")
    return level


def snapshot(world: Path, dimension_id: int):
    if (world.parent / "active-testlab-run.json").exists():
        raise ValueError("Stop the isolated testlab server before reading persisted world evidence")
    dimension = world / f"DIM{dimension_id}"
    journal = dimension / "data" / "gtsr_testlab_fixtures.dat"
    records = NbtReader(gzip.decompress(journal.read_bytes())).root()["data"]
    positions = []
    for entry in records["positions"]:
        packed = entry["p"] & ((1 << 64) - 1)
        x, z, y = packed >> 38, (packed >> 12) & 0x3ffffff, packed & 0xfff
        if x >= 0x2000000:
            x -= 0x4000000
        if z >= 0x2000000:
            z -= 0x4000000
        positions.append((x, y, z))
    groups = {}
    for position in positions:
        groups.setdefault((position[0] >> 4, position[2] >> 4), []).append(position)
    cells, tiles = {}, {}
    for (cx, cz), group in sorted(groups.items()):
        chunk = read_chunk(dimension, cx, cz)
        sections = {section["Y"]: section for section in chunk.get("Sections", [])}
        tile_map = {(tile["x"], tile["y"], tile["z"]): tile for tile in chunk.get("TileEntities", [])}
        for x, y, z in sorted(group):
            key = f"{x},{y},{z}"
            section = sections.get(y >> 4)
            index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            block_id = (section["Blocks"][index] + (nibble(section.get("Add"), index) << 8)
                        if section else 0)
            cell = {"blockId": block_id, "metadata": nibble(section.get("Data"), index) if section else 0}
            tile = tile_map.get((x, y, z))
            if tile is not None:
                cell["tileId"] = tile.get("id")
                cell["metaTileId"] = tile.get("mID")
                cell["facing"] = tile.get("mFacing")
                tiles[key] = tile
            cells[key] = cell
    reports = []
    for path in sorted((world / "gtsr-testlab").glob("*.json")):
        if path.name == "latest.json" or path.name.endswith("-performance.json"):
            continue
        report = json.loads(path.read_text(encoding="utf-8-sig"))
        if report.get("category") == "MACHINE":
            reports.append({"file": str(path), "version": report.get("gtsrVersion"),
                            "results": report.get("results", [])})
    return {"schemaVersion": 1, "world": str(world), "dimension": dimension_id,
            "coordinateCount": len(cells), "blockIdentitySha256": digest(cells),
            "cells": cells, "persistedTiles": tiles, "machineReports": reports}


def compare(before, after):
    changed = []
    for key in sorted(before["cells"].keys() | after["cells"].keys()):
        old, new = before["cells"].get(key), after["cells"].get(key)
        if old != new:
            changed.append({"position": key, "before": old, "after": new})
    nbt_changed = [key for key in before["persistedTiles"].keys() | after["persistedTiles"].keys()
                   if before["persistedTiles"].get(key) != after["persistedTiles"].get(key)]
    return {"blockIdentityUnchanged": not changed, "changedCells": changed,
            "persistedTileNbtChangedPositions": sorted(nbt_changed),
            "inventoryAndFluidEvidence": "Complete tile NBT retained in both snapshots; changes during natural work are expected"}


def conservation_fields(tile):
    """Select persisted inventory/fluid/binding/identity payloads, leaving transient heat/progress aside."""
    import re
    return {key: value for key, value in tile.items()
            if re.search(r"inventory|fluid|stored|owner|facing|hubpos|bound|cache.*node|binding|storage|clusterunits|clusterchain", key, re.I)}


def compare_loaded(before, reports_dir: Path):
    observations, differences = {}, []
    for path in sorted(reports_dir.glob("*.json")):
        if path.name == "latest.json":
            continue
        report = json.loads(path.read_text(encoding="utf-8-sig"))
        if report.get("category") != "EXISTING_LAYOUT":
            continue
        for entry in report.get("results", []):
            details = entry.get("details", {})
            for loaded in details.get("loadedBeforeAnyFixtureMutation", []):
                position = loaded["position"]
                if position in observations:
                    continue
                observations[position] = {"sourceReport": str(path), "task": entry["id"],
                                          "loaded": loaded["completeTileNbt"]}
                persisted = before["persistedTiles"].get(position)
                old = conservation_fields(persisted or {})
                new = conservation_fields(loaded["completeTileNbt"])
                if old != new:
                    differences.append({"position": position, "task": entry["id"], "sourceReport": str(path),
                                        "persistedOld": old, "loadedBeforeFixtureMutation": new})
    conserved_differences, topology_hints = [], []
    for entry in differences:
        old = entry["persistedOld"]
        new = entry["loadedBeforeFixtureMutation"]
        changed = {key for key in old.keys() | new.keys() if old.get(key) != new.get(key)}
        if changed == {"clusterUnits"}:
            topology_hints.append(entry)
        else:
            conserved_differences.append(entry)
    return {"loadedTilesCompared": len(observations), "allSelectedFieldsEqual": not differences,
            "differences": differences, "allConservedFieldsEqual": not conserved_differences,
            "conservedFieldDifferences": conserved_differences,
            "topologyHintReloadDifferences": topology_hints,
            "topologyHintPolicy": "ClusterPersistence.read loads clusterUnits into pending reconnect hints, not live topology. Preserve these differences; require native structure/binding/function evidence separately.",
            "interpretation": "Snapshots precede probe stock changes; passive real loading ticks can change generated fluids/EU and must be assessed from each difference"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", required=True, type=Path)
    parser.add_argument("--dimension", type=int, default=1048577)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--loaded-reports", type=Path,
                        help="Compare baseline persisted stock to EXISTING_LAYOUT pre-mutation snapshots in this report directory")
    args = parser.parse_args()
    result = snapshot(args.world.resolve(strict=True), args.dimension)
    if args.baseline:
        before = json.loads(args.baseline.read_text(encoding="utf-8-sig"))
        result["comparison"] = compare(before, result)
        if args.loaded_reports:
            result["preMutationLoadedConservation"] = compare_loaded(before, args.loaded_reports)
    elif args.loaded_reports:
        parser.error("--loaded-reports requires --baseline")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items()
                      if key not in ("cells", "persistedTiles", "machineReports")}, ensure_ascii=False))
    if result.get("comparison", {}).get("blockIdentityUnchanged") is False:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
