"""Small-heap real controller/NBT regression on GTNH 2.9.0 / GT .265 / Java 26; no game or Gradle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("product", "core", "out"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--classpath", type=Path, default=ROOT / "build/recipe-energy-launch-v213/compile-classpath.txt")
    parser.add_argument("--java-home", type=Path, default=Path("C:/Program Files/Java/jdk-26.0.2.1"))
    parser.add_argument("--mode", choices=("red", "green"), required=True)
    parser.add_argument("--jar-only", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / "build")
    classes = out / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    empty = out / "empty"
    empty.mkdir(exist_ok=True)
    dependencies = []
    for value in args.classpath.read_text(encoding="utf-8-sig").strip().split(os.pathsep):
        path = Path(value)
        if not path.exists() or "miaokatzecore" in path.name.lower() or "gtsr" in path.name.lower():
            continue
        if "/build/classes/java/main" in path.as_posix().lower():
            continue
        if path.suffix == ".jar":
            with zipfile.ZipFile(path) as jar:
                if "META-INF/services/com.sun.source.util.Plugin" in jar.namelist():
                    continue
        dependencies.append(path.resolve())
    gt = [path for path in dependencies if path.name.startswith("GT5-Unofficial-")]
    if len(gt) != 1 or gt[0].name != "GT5-Unofficial-5.09.54.265-dev.jar":
        raise ValueError("Expected exactly one GT .265 dev JAR")
    dependencies += sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/com.falsepattern/chunkapi-mc1.7.10/0.8.3").glob("*/*-dev.jar"))
    cp = os.pathsep.join(map(str, [classes, args.product.resolve(), args.core.resolve(),
        ROOT / "build/classes/java/patchedMc", ROOT / "build/resources/patchedMc", *dependencies]))
    sources = [ROOT / "tools/tests/Issue37RegistrationRegression.java", Path(__file__).with_name("CriticalResourceNbtRegression.java")]
    if not args.jar_only:
        sources += [ROOT / "src/main/java/com/miaokatze/gtsr/common/critical" / (name + ".java")
                    for name in ("CriticalMachineState", "CriticalResourceNbt")]
    elif any((classes / "com/miaokatze/gtsr/common/critical").glob("CriticalMachineState*.class")):
        raise ValueError("Source overlay in jar-only output directory")
    receipt = {"status": "FAIL", "mode": args.mode, "sourceOverlay": not args.jar_only,
        "sourceSha256": {str(path.relative_to(ROOT)): sha(path) for path in sources},
        "productSha256": sha(args.product), "coreSha256": sha(args.core), "gtSha256": sha(gt[0]),
        "javaHome": str(args.java_home), "heap": "256m", "commands": [],
        "boundary": "Actual controller read/write and shared ledger/refund code with native Minecraft NBT and GT .265. Isolated registry bootstrap; no Forge lifecycle, real world, game launch or crash atomicity claim."}
    commands = [
        ("compile", "javac", ["-encoding", "UTF-8", "-proc:none", "-sourcepath", empty, "-cp", cp, "-d", classes, *sources]),
        ("run", "java", ["-Xmx256m", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", "-cp", cp,
            "com.miaokatze.gtsr.common.critical.CriticalResourceNbtRegression", args.mode]),
    ]
    for name, executable, values in commands:
        argfile = out / (name + ".args")
        argfile.write_text("\n".join('"' + str(value).replace("\\", "/") + '"' for value in values), encoding="utf-8")
        result = subprocess.run([str(args.java_home / "bin" / (executable + ".exe")), "@" + str(argfile)],
            cwd=out, capture_output=True, timeout=120)
        (out / (name + ".stdout.log")).write_bytes(result.stdout)
        (out / (name + ".stderr.log")).write_bytes(result.stderr)
        receipt["commands"].append({"name": name, "exitCode": result.returncode})
        if result.returncode:
            if name == "run" and args.mode == "red" and b"must lock malformed uncertainFundingItems" in result.stderr:
                receipt["status"] = "REPRODUCED_SILENT_OVERFLOW"
            else:
                print((result.stdout + result.stderr).decode("utf-8", errors="replace")[-6500:])
            break
    else:
        receipt["status"] = "PASS"
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + " " + str(out / "receipt.json"))
    return 0 if receipt["status"] in ("PASS", "REPRODUCED_SILENT_OVERFLOW") else 1


if __name__ == "__main__":
    raise SystemExit(main())
