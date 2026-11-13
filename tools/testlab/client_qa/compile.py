"""Compile the owned-only opt-in QA helper; does not modify the production jar."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT.parent / 'tools/testlab'))
import host


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=ROOT / "build/mui2-client-qa")
    parser.add_argument("--client-sandbox", type=Path)
    parser.add_argument("--javac", type=Path)
    parser.add_argument("--include-mega-turbine-profile", action="store_true",
                        help="Explicitly compile the issue155 testlab fixture into this owned-only helper")
    args = parser.parse_args()
    item = host.registered(host.REGISTRY, 'gtsr', 'client')
    out, fixture = args.workspace.resolve(), item['_path']
    if args.client_sandbox and args.client_sandbox.resolve() != fixture:
        raise ValueError('Use registered client_game only')
    if out.parent != ROOT / 'build':
        raise ValueError('Use direct build metadata workspace')
    if (fixture / '.testlab-host/lease.json').exists() or host.recovery_pending(item):
        raise RuntimeError('Registered client leased or pending recovery')
    args.javac = args.javac or Path(item['client_java']).with_name('javac.exe')
    lease = ROOT.parent / ".gtsr-native-server.lease"
    if lease.exists():
        raise RuntimeError("A test process holds the shared lease; do not replace its helper jar")
    classes = out / "qa-classes"
    classes.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).with_name("Mui2ClientQa.java")
    environment_source = ROOT.parent / 'tools/testlab/client_qa/HostEnvironment.java'
    scope_source = ROOT.parent / 'tools/testlab/client_qa/OwnedClientScope.java'
    profile_source = ROOT / 'src/testlab/java/com/miaokatze/gtsr/common/dimension/testlab/MegaTurbineProfileFixture.java'
    extra_sources = [profile_source.resolve(strict=True), profile_source.with_name("MegaTurbineProximityFixture.java").resolve(strict=True)] if args.include_mega_turbine_profile else []
    launch = json.loads((out / 'client-launch.json').read_text(encoding='utf-8'))['command']
    classpath = launch[launch.index('-cp') + 1] + ';' + ';'.join(str(p) for p in sorted((fixture / 'mods').rglob('*.jar')))
    options = ["-encoding", "UTF-8", "-proc:none", "--release", "8", "-cp", classpath, "-d", str(classes), *[str(p) for p in sorted(source.parent.glob("*.java"))], str(environment_source), str(scope_source), *map(str, extra_sources)]
    argfile = out / "qa-compile.args"
    argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in options), encoding="utf-8")
    result = subprocess.run([str(args.javac.resolve(strict=True)), "-J-Duser.language=en", "@" + str(argfile)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    (out / "qa-compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(result.stderr)
    jar = out / "gtsr-mui2-qa-owned-only.jar"
    artifacts = sorted(classes.rglob("*.class"))
    # Only this helper's output is packaged; stale unrelated classes are not included.
    artifacts = [p for p in artifacts if p.relative_to(classes).as_posix().startswith(("audit/Mui2ClientQa", "audit/UiQaWireCounter", "audit/UiQaScript", "audit/UiQaGallery", "audit/UiGalleryBatch", "audit/TicketRestartQa", "miaotest/host/HostEnvironment", "miaotest/host/OwnedClientScope"))]
    if args.include_mega_turbine_profile:
        artifacts.extend(sorted((classes / 'com/miaokatze/gtsr/common/dimension/testlab').glob('MegaTurbineProfileFixture*.class')))
        artifacts.extend(sorted((classes / 'com/miaokatze/gtsr/common/dimension/testlab').glob('MegaTurbineProximityFixture*.class')))
    with zipfile.ZipFile(jar, "w") as archive:
        for path in artifacts:
            info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(info, path.read_bytes())
    receipt = {
        "status": "COMPILED_NOT_RUN",
        "sourceSHA256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "sources": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.parent.glob("*.java"))},
        "environmentSourceSHA256": hashlib.sha256(environment_source.read_bytes()).hexdigest(),
        "ownedScopeSourceSHA256": hashlib.sha256(scope_source.read_bytes()).hexdigest(),
        "explicitProfileSources": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in extra_sources},
        "jarSHA256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "classCount": len(artifacts),
        "classMajors": sorted({int.from_bytes(p.read_bytes()[6:8], "big") for p in artifacts}),
        "installScope": "Metadata cache only; runner installs/removes this exact helper within its host lease",
    }
    (out / "qa-compile-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
