"""Compile the owned-only opt-in QA helper; does not modify the production jar."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=ROOT / "build/mui2-client-qa")
    parser.add_argument("--client-sandbox", type=Path, default=ROOT / "build/client-testlab-mui2")
    parser.add_argument("--javac", type=Path, default=Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe")
    args = parser.parse_args()
    out, fixture = args.workspace.resolve(), args.client_sandbox.resolve()
    if out.parent != ROOT / "build" or fixture.parent != ROOT / "build" or not fixture.name.startswith("client-testlab-"):
        raise ValueError("Use direct owned build children only")
    lease = ROOT.parent / ".gtsr-native-server.lease"
    if lease.exists():
        raise RuntimeError("A test process holds the shared lease; do not replace its helper jar")
    classes = out / "qa-classes"
    classes.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).with_name("Mui2ClientQa.java")
    classpath = ";".join(str(p) for p in [*sorted((fixture / "libraries").rglob("*.jar")), *sorted((fixture / "mods").glob("*.jar"))])
    options = ["-encoding", "UTF-8", "-proc:none", "--release", "8", "-cp", classpath, "-d", str(classes), *[str(p) for p in sorted(source.parent.glob("*.java"))]]
    argfile = out / "qa-compile.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in options), encoding="utf-8")
    result = subprocess.run([str(args.javac.resolve(strict=True)), "-J-Duser.language=en", "@" + str(argfile)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    (out / "qa-compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(result.stderr)
    jar = fixture / "mods/gtsr-mui2-qa-owned-only.jar"
    artifacts = sorted(classes.rglob("*.class"))
    # Only this helper's output is packaged; stale unrelated classes are not included.
    artifacts = [p for p in artifacts if p.relative_to(classes).as_posix().startswith(("audit/Mui2ClientQa", "audit/UiQaWireCounter", "audit/UiQaScript", "audit/UiQaGallery", "audit/UiGalleryBatch", "audit/TicketRestartQa"))]
    with zipfile.ZipFile(jar, "w") as archive:
        for path in artifacts:
            info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(info, path.read_bytes())
    receipt = {
        "status": "COMPILED_NOT_RUN",
        "sourceSHA256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "sources": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.parent.glob("*.java"))},
        "jarSHA256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "classCount": len(artifacts),
        "classMajors": sorted({int.from_bytes(p.read_bytes()[6:8], "big") for p in artifacts}),
        "installScope": "Owned client only; production deliverable unaffected",
    }
    (out / "qa-compile-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
