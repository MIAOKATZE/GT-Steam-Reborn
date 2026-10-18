"""Focused GT205 release46 verification; product classes load exclusively from the built dev jar."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("v33", ROOT / "tools/critical/verify_v33.py")
v33 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v33)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--out", type=Path, default=ROOT / "build/critical-release-v12146/verification")
    args = parser.parse_args()
    candidate, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(ROOT / "build")
    out.mkdir(parents=True, exist_ok=True)
    folders = {name: out / name for name in ("classes", "gates", "runtime", "empty-sourcepath")}
    for folder in folders.values():
        folder.mkdir(exist_ok=True)
    (folders["classes"] / "mcpmod.info").write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]')
    gt = list((v33.CACHE / "com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205").glob("*/*-dev.jar"))
    if len(gt) != 1:
        raise RuntimeError("Expected exactly one GT205 dev jar")
    jars = sorted(p for p in v33.CACHE.glob("*/*/*/*/*.jar")
                  if "/GT5-Unofficial/" not in p.as_posix() and "gtsr" not in p.name.lower())
    cp = os.pathsep.join(map(str, [folders["classes"], folders["gates"], candidate, gt[0],
        ROOT / "build/classes/java/patchedMc", ROOT / "build/rfg/recompiled_minecraft-1.7.10.jar",
        ROOT / "build/rfg/mclauncher-1.7.10.jar", *jars]))
    java_home = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2"
    java, javac = java_home / "bin/java.exe", java_home / "bin/javac.exe"
    sources = sorted(v33.SOURCES.glob("*.java")) + [Path(__file__).with_name("Release46Probe.java")]
    files = sources + v33.GATES + [Path(__file__), ROOT / "tools/critical/verify_v33.py"]
    hashes = {str(p.relative_to(ROOT)): v33.sha(p) for p in files}
    receipt = {"status": "RUNNING", "jar": str(candidate), "jarSHA256": v33.sha(candidate),
        "GT": str(gt[0]), "gtSHA256": v33.sha(gt[0]), "fixtureSHA256": hashes,
        "classpath": cp.split(os.pathsep), "productOverlay": False, "results": {},
        "NOT_RUN": ["Complete Forge server/modpack fuel registration", "NEI client UI/GL screenshots",
                    "98-machine matrix", "TPS/performance"],
        "boundary": "Native GT205 classes and production jar; seeded recipe fixtures, sparse world and two platform gates"}
    receipt_path = out / "verification.json"
    def save():
        receipt_path.write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    save()
    try:
        for name, output, inputs in [("compile-gates", folders["gates"], v33.GATES),
                                     ("compile-probes", folders["classes"], sources)]:
            v33.execute(javac, name, ["-encoding", "UTF-8", "-proc:none", "-sourcepath",
                folders["empty-sourcepath"], "-cp", cp, "-d", output, *inputs], out, folders["runtime"])
        for key, target in [("release46", "Release46Probe"), ("solar-unchanged", "SolarProbe")]:
            report = out / (key + ".json")
            v33.execute(java, key, ["-Djava.awt.headless=true", "-Dprobe.gt=5.09.54.205",
                "-Dprobe.main=com.miaokatze.gtsr.common.critical." + target,
                "-cp", cp, "ProbeLauncher", report], out, folders["runtime"])
            receipt["results"][key] = json.loads(report.read_text(encoding="utf-8"))
            if receipt["results"][key]["status"] != "PASS":
                raise RuntimeError(key + " failed")
            save()
        if hashes != {str(p.relative_to(ROOT)): v33.sha(p) for p in files} or v33.sha(candidate) != receipt["jarSHA256"]:
            raise RuntimeError("Fixture or product jar changed during verification")
        receipt["status"] = "PASS"
    except Exception as failure:
        receipt["status"] = "FAIL"
        receipt["failure"] = str(failure)
        raise
    finally:
        save()
    print(receipt_path)


if __name__ == "__main__":
    main()
