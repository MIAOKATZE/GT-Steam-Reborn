"""Compile/run the pure candidate sampler probe with explicit JAR origins; never launch Forge."""
import argparse
import os
import subprocess
import sys
import time
import zipfile
from pathlib import Path

from common import ROOT, file_sha, read_json, write_json

PRODUCT_CLASS = "com/miaokatze/gtsr/common/machinevisual/MachineVisualDefinition.class"
CORE_CLASS = "com/miaokatze/miaokatzecore/api/animation/Sampler.class"
COMPILER_PLUGIN = "META-INF/services/com.sun.source.util.Plugin"


def contains(path, entry):
    if path.is_dir():
        return (path / entry).is_file()
    if path.suffix.lower() != ".jar":
        return False
    with zipfile.ZipFile(path) as archive:
        return entry in archive.namelist()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "javac", "product", "core", "classpath-file", "requests", "out"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).with_name("MotionOracleProbe.java")
    product, core = args.product.resolve(), args.core.resolve()
    if not contains(product, PRODUCT_CLASS) or not contains(core, CORE_CLASS):
        raise ValueError("Explicit product/Core JAR does not contain the production sample classes")
    inputs_before = {str(path): file_sha(path) for path in (product, core, args.requests, source, args.classpath_file)}
    external, excluded = [], []
    for value in args.classpath_file.read_text(encoding="utf-8-sig").strip().split(os.pathsep):
        path = Path(value).resolve()
        if not path.exists():
            raise FileNotFoundError(path)
        reason = None
        if contains(path, PRODUCT_CLASS) or "/build/classes/java/main" in path.as_posix().lower():
            reason = "other_product_classes"
        elif contains(path, CORE_CLASS) or "miaokatzecore" in path.name.lower():
            reason = "duplicate_core"
        elif contains(path, COMPILER_PLUGIN):
            # javac discovers these even with -proc:none; pure Java8 probe needs no source plugin.
            reason = "build_only_javac_plugin"
        if reason:
            excluded.append({"path": str(path), "reason": reason})
        elif path not in external:
            external.append(path)
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    compile_cp = os.pathsep.join(map(str, [product, core, *external]))
    run_cp = os.pathsep.join(map(str, [classes, product, core, *external]))
    output = out / "java-scalars.json"
    commands = [
        ("compile", [str(args.javac), "-J-Duser.language=en", "-J-Duser.country=US", "--release", "8", "-encoding", "UTF-8",
                     "-proc:none", "-cp", compile_cp, "-d", str(classes), str(source)]),
        ("sample", [str(args.java), "-Duser.language=en", "-Duser.country=US", "-cp", run_cp, "MotionOracleProbe",
                    str(args.requests.resolve()), str(output), str(product), str(core)])]
    receipts = []
    result = {"schema": 1, "status": "FAIL", "kind": "production_sampler_execution", "commands": receipts,
              "classpath": [str(path) for path in (product, core, *external)], "excludedClasspath": excluded,
              "inputSha256": inputs_before, "runnerSha256": file_sha(__file__),
              "boundary": "Pure Java production numeric probe only; no Forge/game launch or GL acceptance."}
    for name, command in commands:
        start = time.time()
        process = subprocess.run(command, cwd=ROOT, capture_output=True)
        (out / (name + ".stdout.log")).write_bytes(process.stdout)
        (out / (name + ".stderr.log")).write_bytes(process.stderr)
        receipts.append({"name": name, "command": command, "exitCode": process.returncode,
                         "elapsedSeconds": time.time() - start, "stdout": name + ".stdout.log", "stderr": name + ".stderr.log"})
        if process.returncode:
            write_json(out / "execution.json", result)
            print("FAIL " + name + "; see " + str(out / (name + ".stderr.log")))
            return process.returncode
    major = int.from_bytes((classes / "MotionOracleProbe.class").read_bytes()[6:8], "big")
    if major != 52:
        raise ValueError("Probe is not Java8-compatible bytecode")
    for path, expected in inputs_before.items():
        if file_sha(path) != expected:
            raise ValueError("Probe input changed during execution: " + path)
    samples = read_json(output)
    if samples.get("status") != "PASS" or not samples.get("cases"):
        raise ValueError("Production probe did not produce nonempty PASS samples")
    result.update(status="PASS", definitionSha256=samples["definitionSha256"], probeClassMajor=major, probeClassSha256=file_sha(classes / "MotionOracleProbe.class"),
                  outputSha256=file_sha(output), codeSources=samples["codeSources"], scalarCases=len(samples["cases"]))
    write_json(out / "execution.json", result)
    print("PASS production scalar cases=" + str(result["scalarCases"]) + "; bytecode=52; game=NOT_RUN")
    return 0


if __name__ == "__main__":
    sys.exit(main())
