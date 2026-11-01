"""Exercise exact foreground SHA/reload source methods with in-memory bytes; no game/resource-pack install."""
import argparse
import re
import subprocess
import sys
import zipfile
from pathlib import Path

from common import ROOT, file_sha, sha, write_json

SOURCE = ROOT / "src/main/java/com/miaokatze/gtsr/client/workeffects/WorkEffectRenderer.java"
HARNESS = Path(__file__).with_name("ForegroundGuardContract.java")
EXPECTED = {
    "good_foreground_hash", "good_bronze_hash", "good_steel_hash", "working_guard_enters", "idle_guard_stops",
    "bitflip_foreground_rejected", "bitflip_preserves_base_flags", "bitflip_guard_stops", "leading_guard_no_resource_reads",
    "missing_foreground_rejected", "empty_foreground_rejected", "bad_bronze_only_disables_bronze",
    "reload_restores_good_foreground", "default_foreground_unchanged",
}


def method(source, signature):
    start = source.index(signature)
    body = source.index("{", start)
    depth, end = 1, body + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("jar", "gt", "out"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    write_json(out / "receipt.json", {"schema": 1, "status": "FAIL", "kind": "foreground_hash_source_contract",
                                      "error": "Source contract has not completed; inspect execution output"})
    source = SOURCE.read_text(encoding="utf-8")
    signatures = ["public void onResourceManagerReload(IResourceManager manager)",
                  "private static boolean matches(IResourceManager manager, String name, String expected)",
                  "private static boolean matches(IResourceManager manager, ResourceLocation location, String expected)"]
    methods = [method(source, signature) for signature in signatures]
    render = method(source, "public static void render(MachineVisualHost host, boolean working)")
    guard = re.match(r"\s*(if\s*\(\s*!working\s*\|\|\s*!foregroundAvailable\s*\)\s*return\s*;)", render[render.index("{") + 1:])
    if guard is None:
        raise ValueError("Production leading render guard changed; explicit review required")
    declarations = []
    for name in ("FOREGROUND", "FOREGROUND_SHA", "BRONZE_SHA", "STEEL_SHA", "bronzeCompatible", "steelCompatible", "foregroundAvailable"):
        match = re.search(r"^\s*private static [^\n]+\b" + name + r"\b[^\n]*;", source, re.M)
        if match is None:
            raise ValueError("Production guard declaration missing: " + name)
        declarations.append(match.group(0).strip())
    wrapper = out / "ForegroundGuardUnderTest.java"
    wrapper.write_text("import java.io.*;\nimport java.security.*;\nfinal class ForegroundGuardUnderTest {\n" +
                       "\n".join(declarations + methods) + "\nstatic int renderEntries;\n" +
                       "static void renderEntryProbe(boolean working) { " + guard.group(1) + " renderEntries++; }\n}\n",
                       encoding="utf-8", newline="\n")
    fixtures = []
    paths = ["assets/gtsr/workeffects/firebox_grate.png",
             "assets/gregtech/textures/blocks/iconsets/MACHINE_CASING_FIREBOX_BRONZE.png",
             "assets/gregtech/textures/blocks/iconsets/MACHINE_CASING_FIREBOX_STEEL.png"]
    expected_names = ["FOREGROUND_SHA", "BRONZE_SHA", "STEEL_SHA"]
    for jar_path, resource, expected_name in zip((args.jar, args.gt, args.gt), paths, expected_names):
        with zipfile.ZipFile(jar_path) as archive:
            payload = archive.read(resource)
        expected = re.search(expected_name + r'\s*=\s*"([0-9a-f]{64})"', source).group(1)
        if sha(payload) != expected:
            raise ValueError("Actual native PNG differs from production guard constant: " + resource)
        path = out / Path(resource).name
        path.write_bytes(payload)
        fixtures.append({"resource": resource, "file": path.name, "sha256": sha(payload), "bytes": len(payload)})
    inputs = {str(path): file_sha(path) for path in (SOURCE, HARNESS, args.jar, args.gt)}
    result = {"schema": 1, "status": "FAIL", "kind": "foreground_hash_source_contract", "inputSha256": inputs,
              "methodSha256": {signature: sha(body.encode("utf-8")) for signature, body in zip(signatures, methods)},
              "renderGuardStatementSha256": sha(guard.group(1).encode("utf-8")), "wrapperSha256": file_sha(wrapper),
              "harnessSha256": file_sha(HARNESS), "runnerSha256": file_sha(__file__), "fixtures": fixtures, "commands": [], "candidateJarExecution": False,
              "boundary": "Exact production reload/hash methods and leading render guard statement with a sentinel; synthetic in-memory resource API only. Candidate PNG bytes are read, but production classes/FML/GL/resource packs are not loaded or installed. This does not test GPU retirement ACK."}
    commands = [("compile", [args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(out), str(wrapper), str(HARNESS)]),
                ("test", [args.java, "-cp", str(out), "ForegroundGuardContract", *[str(out / value["file"]) for value in fixtures]])]
    for name, command in commands:
        process = subprocess.run(command, capture_output=True)
        (out / (name + ".stdout.log")).write_bytes(process.stdout)
        (out / (name + ".stderr.log")).write_bytes(process.stderr)
        result["commands"].append({"name": name, "command": command, "exitCode": process.returncode})
        if process.returncode:
            write_json(out / "receipt.json", result)
            print("FAIL foreground source contract; inspect " + str(out / (name + ".stderr.log")))
            return process.returncode
    lines = (out / "test.stdout.log").read_text(encoding="utf-8").splitlines()
    cases = [line.removeprefix("PASS\t") for line in lines if line.startswith("PASS\t")]
    if set(cases) != EXPECTED or len(cases) != len(EXPECTED):
        raise ValueError("Foreground source contract case universe differs")
    for path, expected in inputs.items():
        if file_sha(path) != expected:
            raise ValueError("Source contract input changed: " + path)
    result.update(status="PASS", cases=[{"id": identifier, "status": "PASS"} for identifier in sorted(cases)], checks=len(cases),
                  probeClassMajor=int.from_bytes((out / "ForegroundGuardContract.class").read_bytes()[6:8], "big"),
                  candidateJarSha256=file_sha(args.jar), defaultForegroundUnchanged=True)
    if result["probeClassMajor"] != 52:
        raise ValueError("Source contract bytecode must target Java8")
    write_json(out / "receipt.json", result)
    print("PASS foreground source contract checks=" + str(result["checks"]) + "; FML/GL/resource-pack install=NOT_RUN")
    return 0


if __name__ == "__main__":
    sys.exit(main())
