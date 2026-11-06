"""Run the existing 26 detached turbine restart assertions, without invoking Gradle.

Uses real GT265, StructureLib 1.4.47, Core 0.2.5, Minecraft NBT and native fuel drain.
Structure results, energy insertion, pollution, FML side and observer networking are
isolated boundaries. This is not a full-world, AE-grid or client integration test.

Run from the repository root:
    python tools/tests/turbine_restart/run.py
    python tools/tests/turbine_restart/run.py --classpath-file <exported-classpath.txt>
The default is the existing task03 compile-classpath export; it must already exist.
"""

import argparse
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[3]
FIXTURE = Path(__file__).resolve().parent
OUT = ROOT / "build/turbine-restart-contract-tracked"
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
JAVA = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"


def cached(group, name, version):
    return next((CACHE / group / name / version).glob("*/*-dev.jar"))


def run(executable, name, args):
    argfile = OUT / (name + ".args")
    argfile.write_text(
        "\n".join('"' + str(arg).replace("\\", "/") + '"' for arg in args),
        encoding="utf-8",
    )
    result = subprocess.run(
        [str(JAVA / executable), "@" + str(argfile)], cwd=ROOT, capture_output=True
    )
    log = result.stdout + result.stderr
    (OUT / (name + ".log")).write_bytes(log)
    print(name, "PASS" if result.returncode == 0 else "FAIL")
    if result.returncode or name == "run":
        print(log.decode("utf-8", errors="replace"))
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--classpath-file", type=Path,
        default=ROOT / "build/task03-compile-classpath.txt",
    )
    args = parser.parse_args()
    exported = args.classpath_file.read_text(encoding="utf-8-sig").strip()
    # Pin the exercised runtime dependencies and omit the unneeded compiler plugin.
    dependencies = [
        entry for entry in exported.split(";")
        if not any(name in entry for name in (
            "GT5-Unofficial", "StructureLib", "miaokatzecore", "jabel-javac-plugin",
        ))
    ]
    classpath = ";".join(map(str, [
        OUT / "classes", ROOT / "build/classes/java/main",
        ROOT / "build/classes/java/patchedMc", ROOT / "build/resources/patchedMc",
        ROOT / "src/main/resources",
        cached("com.github.GTNewHorizons", "GT5-Unofficial", "5.09.54.265"),
        cached("com.github.GTNewHorizons", "StructureLib", "1.4.47"),
        cached("com.miaokatze.miaokatzecore", "miaokatzecore", "0.2.5"),
        # GT's Java-17 multi-release classes need this runtime shim, absent from compileClasspath.
        next((CACHE / "xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api/1.3.5")
             .glob("*/*-downgraded-8.jar")),
        *dependencies,
    ]))
    OUT.mkdir(parents=True, exist_ok=True)
    production = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine"
    sources = [production / (name + ".java") for name in (
        "MTEHeavySteamTurbine", "MTEMegaSteamTurbineArray", "MTEKineticProcessingArray",
    )]
    compile_code = run("javac.exe", "compile", [
        "-encoding", "UTF-8", "-proc:none", "-cp", classpath, "-d", OUT / "classes",
        *sources, FIXTURE / "TurbineRestartContract.java", FIXTURE / "MachineVisualNetwork.java",
        ROOT / "tools/tests/structurelib-gates/FMLCommonHandler.java",
    ])
    if compile_code:
        return compile_code
    return run("java.exe", "run", [
        "-Djava.awt.headless=true", "-cp", classpath, "TurbineRestartContract",
    ])


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
