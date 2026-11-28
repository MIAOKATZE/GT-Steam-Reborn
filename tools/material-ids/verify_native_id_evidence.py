#!/usr/bin/env python3
"""Read-only exact-jar evidence check. Never discovers or reads game instances."""
import argparse
import hashlib
import re
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--gt-jar", type=Path, required=True)
    args = parser.parse_args()
    expected = "88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7"
    actual_hash = hashlib.sha256(args.gt_jar.read_bytes()).hexdigest()
    assert actual_hash == expected, f"exact .277 artifact required, got {actual_hash}"
    bytecode = subprocess.check_output(
        ["javap", "-classpath", str(args.gt_jar), "-c", "-p", "gregtech.api.enums.MaterialsIDMap"],
        text=True,
    )
    pattern = (
        r"\d+: (iconst_[0-5]|bipush\s+\d+|sipush\s+\d+)\s*\n"
        r"\s*\d+: getstatic\s+#\d+\s+// Field gregtech/api/enums/Materials\.(\w+):[^\n]+\n"
        r"\s*\d+: invokevirtual\s+#\d+\s+// Method r:"
    )
    rows = {}
    for encoded, name in re.findall(pattern, bytecode):
        number = int(encoded.split("_")[-1]) if encoded.startswith("iconst") else int(encoded.split()[-1])
        assert number not in rows, f"duplicate bytecode slot {number}"
        rows[number] = name
    snapshot = {}
    for line in Path(__file__).with_name("gt-277-native-slots.properties").read_text(encoding="ascii").splitlines():
        if line.startswith("#") or not line:
            continue
        number, name = line.split("=", 1)
        assert int(number) not in snapshot
        snapshot[int(number)] = name
    assert rows == snapshot, "checked-in occupancy evidence differs from exact jar"
    assert len(rows) == 780 and rows[705] == "DistilledWater"
    assert set(re.findall(r"// Method r:", bytecode)) and len(re.findall(r"// Method r:", bytecode)) == len(rows)
    print(f"PASS exact GT .277: {len(rows)} native slots; field705=DistilledWater; artifact={actual_hash}")


if __name__ == "__main__":
    main()
