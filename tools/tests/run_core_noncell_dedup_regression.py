"""Pinned real GT .265/Core NonCell dedup regression; no game or Gradle launch."""
import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GT_SHA = "ea27d29d97692885c6f32d350e9130a37bb7adf17bbb48eecfa00697f3498357"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--core-source-root", type=Path)
    parser.add_argument("--reference-receipt", type=Path,
                        default=ROOT / "build/recipe-energy-launch-v213/core-candidate-native/receipt.json")
    parser.add_argument("--jdk", type=Path, default=Path("C:/Program Files/Java/jdk-26.0.2.1/bin"))
    args = parser.parse_args()
    reference = json.loads(args.reference_receipt.read_text(encoding="utf-8-sig"))
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    classes = out / "classes"
    # A reused source-overlay directory could otherwise override the JAR in a native-only run.
    classes.mkdir()
    empty = out / "empty-sourcepath"
    empty.mkdir(exist_ok=True)
    dependencies = [Path(p) for p in reference["classpath"][1:]
                    if "miaokatzecore" not in p.lower()]
    for path in dependencies:
        if not path.exists():
            raise FileNotFoundError(path)
    gt = next(path for path in dependencies if path.name == "GT5-Unofficial-5.09.54.265-dev.jar")
    if sha(gt) != GT_SHA:
        raise ValueError("The native fixture requires the exact GT .265 dev JAR")
    cp = os.pathsep.join(map(str, [classes, args.core.resolve(), *dependencies]))
    receipt = {
        "status": "FAIL", "kind": "native_core_noncell_dedup_regression",
        "gtSha256": sha(gt), "coreSha256": sha(args.core),
        "sourceOverlay": args.core_source_root is not None,
        "classpath": cp.split(os.pathsep), "commands": [],
        "boundary": "Real GT converter and package-private Core delta adapter with isolated registry ingredients. No game, FML event-bus transformation or full consumer bootstrap.",
    }
    commands = []
    if args.core_source_root is not None:
        source = args.core_source_root / "src/main/java/com/miaokatze/miaokatzecore/api/recipe/GTNonCellRecipes.java"
        receipt["coreSourceSha256"] = sha(source)
        production_sources = out / "core-source"
        production_sources.mkdir(exist_ok=True)
        source_copy = production_sources / source.name
        source_copy.write_bytes(source.read_bytes())
        commands.append(("compile-core", "javac", ["--release", "8", "-encoding", "UTF-8", "-proc:none",
                         "-sourcepath", empty, "-cp", cp, "-d", classes, source_copy]))
    fixture = Path(__file__).with_name("CoreNonCellDedupRegression.java")
    bootstrap = Path(__file__).with_name("Issue37RegistrationRegression.java")
    receipt["fixtureSha256"] = sha(fixture)
    sources = out / "fixture-source"
    sources.mkdir(exist_ok=True)
    fixture_copy = sources / fixture.name
    fixture_copy.write_bytes(fixture.read_bytes())
    bootstrap_copy = sources / bootstrap.name
    bootstrap_copy.write_bytes(bootstrap.read_bytes())
    commands.append(("compile-fixture", "javac", ["-encoding", "UTF-8", "-proc:none", "-sourcepath", empty,
                     "-cp", cp, "-d", classes, bootstrap_copy, fixture_copy]))
    commands.append(("run", "java", ["-Xmx1G", "-Dfile.encoding=UTF-8", "-cp", cp,
                     "CoreNonCellDedupRegression", "5.09.54.265"]))
    for name, tool, values in commands:
        command = [str(args.jdk / (tool + ".exe")), *map(str, values)]
        process = subprocess.run(command, cwd=out, capture_output=True)
        (out / (name + ".stdout.log")).write_bytes(process.stdout)
        (out / (name + ".stderr.log")).write_bytes(process.stderr)
        receipt["commands"].append({"name": name, "command": command, "exitCode": process.returncode})
        if name == "run":
            stdout = process.stdout.decode("utf-8", errors="replace")
            receipt["checks"] = [{"id": key, "status": status}
                                 for key, status in re.findall(r"CHECK ([\w-]+)=(PASS|FAIL)", stdout)]
            summary = re.search(r"RESULT checks=(\d+) failures=(\d+)", stdout)
            if summary:
                receipt["summary"] = {"checks": int(summary[1]), "failures": int(summary[2])}
        if process.returncode:
            break
    else:
        receipt["status"] = "PASS"
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + ": " + str(out / "receipt.json"))
    return 0 if receipt["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
