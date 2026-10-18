"""Focused native GT205 hub I/O contract; default is jar-only, optional source overlay for diagnosis."""
import argparse
import importlib.util
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("v33", ROOT / "tools/critical/verify_v33.py")
v33 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v33)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--out", type=Path, default=ROOT / "build/hub-release-v12146/verification")
    parser.add_argument("--source-overlay", action="store_true")
    args = parser.parse_args()
    candidate, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(ROOT / "build")
    folders = {n: out / n for n in ("classes", "gates", "runtime", "empty-sourcepath", "overlay")}
    for folder in folders.values():
        folder.mkdir(parents=True, exist_ok=True)
    (folders["classes"] / "mcpmod.info").write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]')
    gt = list((v33.CACHE / "com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205").glob("*/*-dev.jar"))
    assert len(gt) == 1
    jars = sorted(p for p in v33.CACHE.glob("*/*/*/*/*.jar")
                  if "/GT5-Unofficial/" not in p.as_posix() and "gtsr" not in p.name.lower())
    entries = [folders["classes"], folders["gates"]]
    if args.source_overlay:
        entries.append(folders["overlay"])
    entries += [candidate, gt[0], ROOT / "build/classes/java/patchedMc",
                ROOT / "build/rfg/recompiled_minecraft-1.7.10.jar",
                ROOT / "build/rfg/mclauncher-1.7.10.jar", *jars]
    cp = os.pathsep.join(map(str, entries))
    java_home = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2"
    java, javac = java_home / "bin/java.exe", java_home / "bin/javac.exe"
    sources = [v33.SOURCES / "ProbeLauncher.java", v33.SOURCES / "ProbeSupport.java",
               Path(__file__).with_name("HubGateProbe.java")]
    product = [ROOT / "src/main/java/com/miaokatze/gtsr/common/machine" / (n + ".java")
               for n in ("MTESteamHubArray", "MTEWaterHubArray")]
    files = sources + v33.GATES + [Path(__file__), ROOT / "tools/critical/verify_v33.py"]
    if args.source_overlay:
        files += product
    hashes = {str(p.relative_to(ROOT)): v33.sha(p) for p in files}
    receipt = {"status": "RUNNING", "jar": str(candidate), "jarSHA256": v33.sha(candidate),
               "gtJar": str(gt[0]), "gtSHA256": v33.sha(gt[0]), "fixtureSHA256": hashes,
               "classpath": list(map(str, entries)), "productOverlay": args.source_overlay,
               "boundary": "Real native structure element, adders, candidate filters, storage capacity and binding classification; Unsafe allocation; no Forge server/client or 98-machine matrix"}
    def save():
        (out / "verification.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    save()
    try:
        for name, output, inputs in [("compile-gates", folders["gates"], v33.GATES),
                                    *(([("compile-overlay", folders["overlay"], product)]) if args.source_overlay else []),
                                    ("compile-probes", folders["classes"], sources)]:
            v33.execute(javac, name, ["-encoding", "UTF-8", "-proc:none", "-sourcepath",
                folders["empty-sourcepath"], "-cp", cp, "-d", output, *inputs], out, folders["runtime"])
        report = out / "hub-gate.json"
        v33.execute(java, "hub-gate", ["-Djava.awt.headless=true", "-Dprobe.gt=5.09.54.205",
            "-Dprobe.main=com.miaokatze.gtsr.common.critical.HubGateProbe",
            "-cp", cp, "ProbeLauncher", report], out, folders["runtime"])
        receipt["result"] = json.loads(report.read_text(encoding="utf-8"))
        assert receipt["result"]["status"] == "PASS"
        assert receipt["jarSHA256"] == v33.sha(candidate)
        assert hashes == {str(p.relative_to(ROOT)): v33.sha(p) for p in files}
        receipt["status"] = "PASS"
    except Exception as failure:
        receipt["status"] = "FAIL"
        receipt["failure"] = str(failure)
        raise
    finally:
        save()
    print(out / "verification.json")


if __name__ == "__main__":
    main()
