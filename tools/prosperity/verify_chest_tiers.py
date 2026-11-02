"""Bounded actual-source chest tier regression with native Minecraft NBT; no game launch."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "build/prosperity-v218/loot-tiers")
    parser.add_argument("--classpath", type=Path, default=ROOT / "build/recipe-energy-launch-v213/compile-classpath.txt")
    parser.add_argument("--java-home", type=Path, default=Path("C:/Program Files/Java/jdk-26.0.2.1"))
    parser.add_argument("--scenario", choices=("all", "categories", "bosses", "native", "saved", "migration"), default="all")
    parser.add_argument("--candidate-root", type=Path, help="Compile a build-directory draft without applying it to src")
    parser.add_argument("--rare-percent", type=int, choices=(5, 10), default=10)
    parser.add_argument("--expect-red", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / "build")
    classes = out / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    empty = out / "empty"
    empty.mkdir(exist_ok=True)
    dependencies = [Path(value) for value in args.classpath.read_text(encoding="utf-8-sig").strip().split(os.pathsep)]
    dependencies = [path for path in dependencies if path.exists()
                    and "miaokatzecore" not in path.name.lower() and "jabel" not in path.name.lower()]
    cp = os.pathsep.join(map(str, [classes, ROOT / "build/classes/java/main", ROOT / "build/classes/java/patchedMc",
        ROOT / "build/resources/patchedMc", ROOT / "src/main/resources", *dependencies]))
    prefix = ROOT / "src/main/java/com/miaokatze/gtsr/common/dimension"
    sources = [prefix / "prosperity/remaster" / (name + ".java")
               for name in ("ChestTier", "RemasterSite", "RemasterCatalog", "RemasterPrefab")]
    sources += [prefix / "framework/structure/GTSRWorldgenHash.java",
                prefix / "prosperity/encounter/TileEntitySealedChest.java",
                prefix / "prosperity/encounter/TileEntityUnsealedChest.java",
                Path(__file__).with_name("ChestTierDistributionCheck.java")]
    if args.candidate_root:
        candidate = args.candidate_root.resolve()
        candidate.relative_to(ROOT / "build")
        sources = [candidate / path.relative_to(ROOT) if (candidate / path.relative_to(ROOT)).exists() else path
                   for path in sources]
    receipt = {"status": "FAIL", "scenario": args.scenario, "sourceOverlay": True,
        "draftNotApplied": bool(args.candidate_root),
        "rarePercent": args.rare_percent,
        "sourceSha256": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources},
        "boundary": "Actual resolver, packaged authoring data and native sealed TileEntity initialization/NBT. "
                    "Seed/update-only detached World; no Forge boot, live world, loot contents or gameplay.",
        "commands": []}
    commands = [
        ("compile", "javac", ["-proc:none", "-encoding", "UTF-8", "-sourcepath", empty, "-cp", cp, "-d", classes, *sources]),
        ("run", "java", ["-Xmx256m", "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
                         "-Dgtsr.test.rarePercent=" + str(args.rare_percent), "-cp", cp, "ChestTierDistributionCheck", args.scenario]),
    ]
    for label, executable, values in commands:
        argfile = out / (label + ".args")
        argfile.write_text("\n".join('"' + str(value).replace("\\", "/") + '"' for value in values), encoding="utf-8")
        vm = ["-J-Dfile.encoding=UTF-8"] if executable == "javac" else []
        result = subprocess.run([str(args.java_home / "bin" / (executable + ".exe")), *vm, "@" + str(argfile)],
                                cwd=ROOT, capture_output=True, timeout=60)
        (out / (label + ".log")).write_bytes(result.stdout + result.stderr)
        receipt["commands"].append({"name": label, "exitCode": result.returncode})
        if result.returncode:
            if label == "run" and args.expect_red and b"java.lang.AssertionError:" in result.stderr:
                receipt["status"] = "EXPECTED_RED"
            print((result.stdout + result.stderr).decode("utf-8", errors="replace")[-3500:])
            break
    else:
        receipt["status"] = "UNEXPECTED_PASS" if args.expect_red else "DRAFT_NOT_APPLIED" if args.candidate_root else "PASS"
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + " " + str(out / "receipt.json"))
    return 0 if receipt["status"] in ("PASS", "EXPECTED_RED", "DRAFT_NOT_APPLIED") else 1


if __name__ == "__main__":
    raise SystemExit(main())
