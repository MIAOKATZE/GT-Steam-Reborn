"""Jar-only detached terminal authorization/NBT contracts; does not start Minecraft or transform Mixins."""

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[2]
CACHES = (
    ROOT / "build/gradle-testlab-private/caches/modules-2/files-2.1",
    Path.home() / ".gradle/caches/modules-2/files-2.1",
)
PRODUCT_CLASSES = (
    "com/miaokatze/gtsr/common/terminal/MachineTerminalHistory.class",
    "com/miaokatze/gtsr/common/terminal/RemoteTerminalFactory.class",
    "com/miaokatze/gtsr/common/terminal/RemoteTerminalGuiData.class",
    "com/miaokatze/gtsr/common/terminal/MachineTerminalIdentity$State.class",
)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dependency(group, artifact, version):
    for cache in CACHES:
        matches = sorted((cache / group / artifact / version).glob("*/*-dev.jar"))
        if matches:
            return matches[0].resolve()
    raise FileNotFoundError(f"Missing cached {artifact} {version} dev jar")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path, help="Candidate GTSR dev jar; the sole product CodeSource")
    parser.add_argument("--core-jar", type=Path, default=ROOT.parent / "MIAOKATZE-Core/build/v015/maven/com/miaokatze/miaokatzecore/miaokatzecore/0.1.15/miaokatzecore-0.1.15-dev.jar")
    parser.add_argument("--out", required=True, type=Path, help="Owned verification directory below this repository's build/")
    parser.add_argument("--gtnh-version", choices=("2.9.0", "2.9.1", "2.9.2"), default="2.9.2")
    parser.add_argument("--java-bin", type=Path, default=Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin")
    args = parser.parse_args()
    candidate = args.jar.resolve(strict=True)
    core = args.core_jar.resolve(strict=True)
    out = args.out.resolve()
    out.relative_to((ROOT / "build").resolve())
    if out == (ROOT / "build").resolve():
        raise ValueError("Use an owned child directory below build/")
    if not candidate.name.endswith("-dev.jar") or not core.name.endswith("-dev.jar"):
        raise ValueError("Both candidate and Core must be dev jars")
    with zipfile.ZipFile(candidate) as archive:
        missing = set(PRODUCT_CLASSES) - set(archive.namelist())
        if missing:
            raise ValueError(f"Candidate lacks terminal implementation: {sorted(missing)}")
    with zipfile.ZipFile(core) as archive:
        if "com/miaokatze/miaokatzecore/api/collection/BoundedMru.class" not in archive.namelist():
            raise ValueError("Core dev jar lacks the 0.1.15 BoundedMru API")
    out.mkdir(parents=True, exist_ok=True)
    classes = Path(tempfile.mkdtemp(prefix="test-classes-", dir=out))
    empty = out / "empty"
    empty.mkdir(exist_ok=True)
    sources = [ROOT / "tools/tests/MachineTerminalRemoteContract.java", ROOT / "tools/tests/MachineHistoryPanelContract.java"]
    hashes = {"candidate": sha256(candidate), "core": sha256(core),
        "testSources": {str(source.relative_to(ROOT)): sha256(source) for source in sources}}

    # The selected official compatibility graph precedes other transitive cached jars. No product baseline,
    # source overlay, build/classes/java/main or build/libs enumeration is ever added.
    profiles = json.loads((ROOT / "gradle/gtnh-compatibility.json").read_text(encoding="utf-8"))["profiles"]
    profile = next(p for p in profiles if p["version"] == args.gtnh_version)
    fixed = [dependency("com.github.GTNewHorizons", artifact, profile["dependencies"][artifact])
        for artifact in ("GT5-Unofficial", "ModularUI2", "ModularUI", "Applied-Energistics-2-Unofficial")]
    blocked = ("gtsr", "gt-steam-reborn", "steamreborn", "miaokatzecore", "miaokatze-core", "jabel")
    dependencies = []
    for cache in CACHES:
        dependencies.extend(p.resolve() for p in sorted(cache.glob("*/*/*/*/*.jar"))
            if not any(token in p.relative_to(cache).as_posix().lower() for token in blocked)
            and not p.name.endswith(("-sources.jar", "-javadoc.jar")))
    minecraft = [ROOT / "build/rfg/recompiled_minecraft-1.7.10.jar", ROOT / "build/rfg/mclauncher-1.7.10.jar"]
    if not minecraft[0].is_file():
        raise FileNotFoundError("A completed development build must provide recompiled_minecraft-1.7.10.jar")
    # The build's patched MC classes provide its mapped engine aliases, never GTSR product classes.
    patched_mc = ROOT / "build/classes/java/patchedMc"
    if any((patched_mc / "com/miaokatze").rglob("*.class")):
        raise AssertionError("Product classes found in the MC engine overlay")
    entries = list(dict.fromkeys([classes, candidate, core, patched_mc, *fixed, *minecraft, *dependencies]))
    for entry in entries:
        if "build/classes/java/main" in entry.as_posix().lower():
            raise AssertionError("Product source overlay entered jar-only classpath")
    cp = os.pathsep.join(str(entry) for entry in entries)
    (out / "classpath.json").write_text(json.dumps([str(p) for p in entries], indent=2), encoding="utf-8")

    def run(executable, name, values):
        argfile = out / f"{name}.args"
        argfile.write_text("\n".join('"' + str(v).replace("\\", "/") + '"' for v in values), encoding="utf-8")
        suffix = ".exe" if os.name == "nt" else ""
        result = subprocess.run([str(args.java_bin / (executable + suffix)), "-J-Duser.language=en", "@" + str(argfile)]
            if executable == "javac" else [str(args.java_bin / (executable + suffix)), "@" + str(argfile)],
            cwd=out, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        output = result.stdout.decode("utf-8", errors="replace")
        (out / f"{name}.log").write_text(output, encoding="utf-8")
        if result.returncode:
            raise RuntimeError(f"{name} failed: {output[-5000:]}")
        return output

    run("javac", "compile", ["-encoding", "UTF-8", "--release", "17", "-proc:none", "-processorpath", empty,
        "-sourcepath", empty, "-cp", cp, "-d", classes, *sources])
    emitted = list(classes.rglob("*.class"))
    if not emitted or any(not p.name.startswith(("MachineTerminalRemoteContract", "MachineHistoryPanelContract")) for p in emitted):
        raise AssertionError("Jar-only compilation emitted a production overlay")
    result = run("java", "contract", ["-cp", cp, "com.miaokatze.gtsr.common.terminal.MachineTerminalRemoteContract", candidate, core])
    panel_result = run("java", "history-panel", ["-cp", cp, "com.miaokatze.gtsr.common.terminal.MachineHistoryPanelContract", candidate])
    if hashes != {"candidate": sha256(candidate), "core": sha256(core),
        "testSources": {str(source.relative_to(ROOT)): sha256(source) for source in sources}}:
        raise RuntimeError("Candidate, Core or test source changed during verification")
    summary = re.search(r"PASS MachineTerminalRemoteContract checks=(\d+)", result)
    shared = re.search(r"PASS sharedCriticalAccess checks=(\d+)", result)
    if not summary or not shared or int(summary[1]) < 40 or int(shared[1]) < 15:
        raise AssertionError("Expected existing remote contracts and two-player shared-critical regression checks")
    receipt = {
        "status": "PASS", "mode": "jar-only", "gtnhVersion": args.gtnh_version, "productOverlay": False, "compiledSources": [str(source) for source in sources],
        "candidateJar": str(candidate), "coreJar": str(core), "sha256": hashes, "classes": str(classes),
        "classCount": len(emitted), "result": result.strip(), "historyPanelResult": panel_result.strip(),
        "scope": "Real production shared access, authorization and NBT-codec checks with detached World/GT/player fixtures. "
            "FML Loader and side are isolated fixtures. BaseMetaTileEntity has NOT been transformed by Mixin; "
            "identity checks exercise the exact State.write/read codec used by production hooks. "
            "No live Mixin injection/startup, successful GUI opening, slot transaction, packet dispatch or client rendering claim.",
    }
    (out / "verification.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
    print(result.strip())
    print(panel_result.strip())


if __name__ == "__main__":
    main()
