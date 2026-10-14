"""Rebuild and execute the V31 production integrity contract without FML/server startup."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12131/integrity'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OWNED = [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in (
    'common/critical/CriticalMachineState.java', 'common/critical/CriticalStructureMonitor.java',
    'common/critical/TileEntityCriticalRenderAnchor.java', 'common/machine/MTECriticalMachine.java')]
PRODUCTION = [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / name for name in (
    'CriticalStructureMonitor.java', 'CriticalMachineState.java', 'CriticalChunkLoading.java',
    'CriticalChunkTicketManager.java', 'CriticalBlockProtection.java', 'CriticalWorldData.java',
    'CriticalRuntime.java', 'CriticalGeometry.java', 'CriticalStructures.java', 'CriticalLoomStructure.java',
    'CriticalConfiguration.java', 'CriticalMachineKind.java', 'CriticalMaterials.java',
    'CriticalRenderAnchors.java', 'TileEntityCriticalRenderAnchor.java')]
PRODUCTION += [OWNED[-1]]
PROBE = ROOT / 'tools/critical/CriticalIntegrityContract.java'
GATES = [ROOT / 'tools/tests/structurelib-gates' / f for f in ('StructureLibAPI.java', 'FMLCommonHandler.java')]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def binding(paths):
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}


def args(name, values):
    p = OUT / name
    p.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
    return '@' + str(p)


def run(exe, name, values):
    r = subprocess.run([str(JAVA / (exe + '.exe')), args(name + '.args', values)], capture_output=True,
                       creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT / (name + '.log')).write_bytes(r.stdout + r.stderr)
    if r.returncode:
        raise RuntimeError(name + ' failed; inspect ' + str(OUT / (name + '.log')))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    sources = PRODUCTION + [PROBE]
    before = binding(sources + GATES + [Path(__file__).resolve()])
    jars = list(CACHE.glob('*/*/*/*/*.jar'))
    gt = [p for p in jars if 'GT5-Unofficial/5.09.54.205/' in p.as_posix()]
    assert gt, 'Authority GT205 dev jar missing'
    cp = ';'.join(map(str, [classes, *gt, ROOT / 'build/classes/java/main',
        ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))
    run('javac', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])
    gates = OUT / 'gates'
    gates.mkdir(exist_ok=True)
    run('javac', 'gates', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', gates, *GATES])
    run('java', 'run', ['-Djava.awt.headless=true', '-cp', str(gates) + ';' + cp,
        'com.miaokatze.gtsr.common.critical.CriticalIntegrityContract', OUT / 'runtime.json'])
    assert before == binding(sources + GATES + [Path(__file__).resolve()]), 'Sources changed during probe'
    report = json.loads((OUT / 'runtime.json').read_text(encoding='utf-8'))
    report['sourceSHA256'] = before
    report['authorityGT205JarSHA256'] = {str(p): sha(p) for p in gt}
    report['roundTicks'] = 6000
    report['maxWorldPositionsPerTick'] = 128
    report['initialValidation'] = '6000 ticks, identical to regular rounds; no placement-result reuse fast path'
    report['loomTiming'] = dict(authority='GT205 MTEMultiBlockBase.java:624-630',
        upstreamStructureUpdateDelay=50, criticalStructureUpdateDelay=100,
        upstreamStartupDelay=100, criticalStartupDelay=200,
        note='GT structure updates are event delayed; idle recipe CHECK_INTERVAL is not structure timing')
    (OUT / 'verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (OUT / 'owned-sha256.json').write_text(json.dumps(binding(OWNED + [PROBE, Path(__file__).resolve()]), indent=2), encoding='utf-8')
    print(json.dumps(dict(status=report['status'], checks=report['checks'], report=str(OUT / 'verification.json'))))


if __name__ == '__main__':
    main()
