"""Focused Forge interaction regression; --jar replays without product classes/source overlays."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
PROBE = ROOT / 'tools/critical/CriticalPortPlacementContract.java'
FIXTURE = ROOT / 'tools/critical/CriticalV32Fixture.java'
RUNTIME = ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalRuntime.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--label', default='candidate')
    parser.add_argument('--legacy-addon', action='store_true', help='Only the supplemented V31 forty-three-port branch')
    parser.add_argument('--hull', action='store_true', help='Only ninety-port V31/V32 upgrades and new V33 across four facings')
    options = parser.parse_args()
    candidate = options.jar.resolve() if options.jar else None
    out = ROOT / 'build/critical-port-placement' / options.label
    out.mkdir(parents=True, exist_ok=True)
    classes = out / ('classes-' + sha(candidate)[:12] if candidate else 'classes')
    gates, empty = (out / name for name in ('gates', 'empty-sourcepath'))
    for path in (classes, gates, empty):
        path.mkdir(exist_ok=True)
    jars = sorted(CACHE.glob('*/*/*/*/*.jar'))
    gt = [p for p in jars if '/GT5-Unofficial/5.09.54.205/' in p.as_posix() and p.name.endswith('-dev.jar')]
    ae = [p for p in jars if '/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH/' in p.as_posix() and p.name.endswith('-dev.jar')]
    assert len(gt) == 1 and len(ae) == 1, (gt, ae)
    jars = [p for p in jars if '/GT5-Unofficial/' not in p.as_posix()
            and '/Applied-Energistics-2-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower()]
    cp = os.pathsep.join(map(str, [classes, gates, *([candidate] if candidate else [ROOT / 'build/classes/java/main']),
        *gt, *ae, ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))

    def execute(name, tool, values):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(JAVA / (tool + '.exe')), '@' + str(argfile)], cwd=ROOT,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        (out / (name + '.log')).write_bytes(result.stdout)
        return result.returncode

    gate_sources = [ROOT / 'tools/tests/structurelib-gates' / (n + '.java')
                    for n in ('StructureLibAPI', 'FMLCommonHandler')]
    base = ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp]
    assert execute('gates', 'javac', [*base, '-d', gates, *gate_sources]) == 0, out / 'gates.log'
    sources = [PROBE, FIXTURE] + ([] if candidate else [RUNTIME])
    binding = {p.relative_to(ROOT).as_posix(): sha(p) for p in sources + gate_sources + [Path(__file__)]}
    assert execute('compile', 'javac', [*base, '-d', classes, *sources]) == 0, out / 'compile.log'
    report = out / 'runtime.json'
    code = execute('run', 'java', ['-Djava.awt.headless=true', '-cp', cp,
        'com.miaokatze.gtsr.common.critical.CriticalPortPlacementContract', report,
        *(['hull'] if options.hull else ['legacy-addon'] if options.legacy_addon else [])])
    receipt = json.loads(report.read_text(encoding='utf-8'))
    receipt.update(sourceSHA256=binding, productOverlay=candidate is None,
                   authorityGT205JarSHA256=sha(gt[0]), authorityAE1080JarSHA256=sha(ae[0]))
    if candidate:
        receipt.update(candidate=str(candidate), candidateSHA256=sha(candidate))
        source_path = unquote(urlparse(receipt['runtimeCodeSource']).path).lstrip('/')
        assert Path(source_path).resolve() == candidate, receipt['runtimeCodeSource']
    (out / 'verification.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2))
    raise SystemExit(code)


if __name__ == '__main__':
    main()
