"""Native GT .265 NonCell conversion/query + Core stage probe; no game or Gradle launch."""
import argparse
import hashlib
import os
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GT_SHA = "ea27d29d97692885c6f32d350e9130a37bb7adf17bbb48eecfa00697f3498357"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("gt", "core", "product", "out"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--classpath-file", type=Path, default=ROOT / "build/task03-compile-classpath.txt")
    parser.add_argument("--mode", choices=("red", "green"), required=True)
    parser.add_argument("--no-source-overlay", action="store_true")
    parser.add_argument("--core-source-root", type=Path)
    parser.add_argument("--extra-jar", action="append", type=Path, default=[])
    parser.add_argument("--jdk", type=Path, default=Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin")
    args = parser.parse_args()
    if sha(args.gt) != GT_SHA:
        raise ValueError("Probe requires the exact .265 dev JAR")
    out = args.out.resolve(); out.mkdir(parents=True, exist_ok=True)
    classes = out / "classes"; classes.mkdir(exist_ok=True)
    empty = out / "empty-sourcepath"; empty.mkdir(exist_ok=True)
    jars = []
    for value in args.classpath_file.read_text(encoding="utf-8-sig").strip().split(os.pathsep):
        path = Path(value).resolve()
        normalized = path.as_posix().lower()
        if "/gt5-unofficial/" in normalized or "miaokatzecore" in normalized or "gtsr" in path.name.lower():
            continue
        if path.suffix == ".jar":
            with zipfile.ZipFile(path) as archive:
                if "META-INF/services/com.sun.source.util.Plugin" in archive.namelist(): continue
        if path not in jars: jars.append(path)
    cp = os.pathsep.join(map(str, [classes, args.gt.resolve(), args.core.resolve(), args.product.resolve(),
                                   ROOT / "build/classes/java/patchedMc", ROOT / "build/resources/patchedMc", *args.extra_jar, *jars]))
    import json
    receipt = {"status": "FAIL", "kind": "native_noncell_recipe_regression", "mode": args.mode,
               "gtSha256": sha(args.gt), "coreSha256": sha(args.core), "productSha256": sha(args.product),
               "productionCoreOverlay": args.core_source_root is not None and not args.no_source_overlay,
               "classpath": cp.split(os.pathsep), "commands": [], "boundary": "Actual GT converter/query and Core lifecycle methods invoked directly with isolated registry ingredients. No FML EventBus transformation, client/server, production world or full GTSR content bootstrap."}
    commands = []
    if args.core_source_root is not None and not args.no_source_overlay:
        source = args.core_source_root / "src/main/java/com/miaokatze/miaokatzecore/api/recipe"
        sources = [source / (name + ".java") for name in ("RecipeRegistration", "RecipeRegistrationEngine", "RecipeRegistrationLifecycle", "GTNonCellRecipes")]
        receipt["coreSourceSha256"] = {str(path): sha(path) for path in sources}
        commands.append(("compile-core", "javac", ["--release", "8", "-encoding", "UTF-8", "-proc:none", "-sourcepath", empty, "-cp", cp, "-d", classes, *sources]))
    commands.append(("compile-probe", "javac", ["-encoding", "UTF-8", "-proc:none", "-sourcepath", empty, "-cp", cp, "-d", classes,
                                               Path(__file__).with_name("Issue37RegistrationRegression.java"), Path(__file__).with_name("LateNonCellRecipeRegression.java")]))
    commands.append(("run", "java", ["-Xmx1G", "-Dfile.encoding=UTF-8", "-cp", cp, "LateNonCellRecipeRegression", "5.09.54.265", str(args.mode == "green").lower()]))
    for name, tool, values in commands:
        command = [str(args.jdk / (tool + ".exe")), *map(str, values)]
        process = subprocess.run(command, cwd=out, capture_output=True)
        (out / (name + ".stdout.log")).write_bytes(process.stdout); (out / (name + ".stderr.log")).write_bytes(process.stderr)
        receipt["commands"].append({"name": name, "command": command, "exitCode": process.returncode})
        if process.returncode:
            if name == "run" and args.mode == "red" and b"target-own-lookup-" in process.stdout and b"RESULT mode=false" in process.stdout:
                receipt["status"] = "REPRODUCED_MISSING_NONCELL_LOOKUP"
            break
    else:
        receipt["status"] = "PASS"
    (out / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + ": " + str(out / "receipt.json"))
    return 0 if receipt["status"] in ("PASS", "REPRODUCED_MISSING_NONCELL_LOOKUP") else 1


if __name__ == "__main__":
    raise SystemExit(main())
