"""Exercise actual scene HUD ownership with a draw-entrypoint recorder and independent clocks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath-args", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--expect-red", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / "build")
    classes = out / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    lines = args.classpath_args.read_text(encoding="utf-8").splitlines()
    cp = lines[lines.index('"-cp"') + 1].strip('"')
    asm = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/org.ow2.asm/asm").glob("9.*/*/*.jar"))[-1]
    cp = os.pathsep.join([str(asm), str(ROOT / "build/classes/java/main"), cp])
    sources = [ROOT / "src/main/java/com/miaokatze/gtsr/client/encounter" / (name + ".java")
               for name in ("SceneBossCache", "EncounterSignals", "EncounterClient", "EchoBossOverlay")]
    sources += [ROOT / "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/encounter/SceneBossSignal.java",
                ROOT / "tools/prosperity/SceneBossHudOwnerCheck.java", ROOT / "tools/critical/v33/ProbeLauncher.java"]

    def run(label, executable, values):
        argfile = out / (label + ".args")
        argfile.write_text("\n".join('"' + str(v).replace("\\", "/") + '"' for v in values), encoding="utf-8")
        result = subprocess.run([str(args.java_home / "bin" / executable), "@" + str(argfile)],
                                cwd=ROOT, capture_output=True, encoding="utf-8", errors="replace")
        (out / (label + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
        return result

    result = run("compile", "javac.exe", ["--release", "17", "-encoding", "UTF-8", "-proc:none", "-sourcepath", out / "empty",
                                           "-cp", cp, "-d", classes, *sources])
    if result.returncode:
        raise RuntimeError("Compile failed; see " + str(out / "compile.log"))
    runtime = os.pathsep.join([str(classes), str(ROOT / "src/main/resources"), cp])
    overlay = classes / "com/miaokatze/gtsr/client/encounter/EchoBossOverlay.class"
    result = run("draw-boundary", "java.exe", ["-cp", runtime,
                  "com.miaokatze.gtsr.client.encounter.SceneBossHudOwnerCheck", "instrument", overlay, overlay])
    if result.returncode:
        raise RuntimeError("Draw boundary instrumentation failed; see " + str(out / "draw-boundary.log"))
    result = run("run", "java.exe", ["-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED",
                  "--sun-misc-unsafe-memory-access=allow", "-Dio.netty.noUnsafe=true",
                  "-Dprobe.main=com.miaokatze.gtsr.client.encounter.SceneBossHudOwnerCheck", "-cp", runtime,
                  "ProbeLauncher", out / "result.json"])
    if not (out / "result.json").exists():
        raise RuntimeError("Owner loop did not produce a report; see " + str(out / "run.log"))
    report = json.loads((out / "result.json").read_text(encoding="utf-8"))
    expected = (report["status"] == "FAIL" and result.returncode != 0
                and all(r["firstMissingTick"] == 60 and r["explicitLeaveCleared"]
                        and r["worldUnchanged"] and r["playerUnchanged"]
                        and r["leavePacketsDuringPause"] == 0 for r in report["results"])) if args.expect_red else (
                            report["status"] == "PASS" and result.returncode == 0)
    receipt = {"expected": "RED" if args.expect_red else "GREEN", "matched": expected,
               "runExit": result.returncode, "report": report,
               "sources": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=True))
    if not expected:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
