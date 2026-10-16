"""Production MaterialBlock/Forge-event World fixture plus frozen pre-fix RED control. No game startup."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12131/protection'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
PREFIX = 'src/main/java/com/miaokatze/gtsr/'
OWNED = [PREFIX + name for name in (
    'common/critical/CriticalBlockProtection.java', 'common/critical/CriticalMaterials.java',
    'common/critical/CriticalRuntime.java', 'mixin/MixinBlockCriticalProtection.java',
    'mixin/BlockMachinesCriticalProtectionMixin.java', 'mixin/MixinEntityCriticalProtection.java',
)] + ['src/main/resources/mixins.gtsr.json']
SUPPORT = [PREFIX + 'common/critical/' + name + '.java' for name in (
    'CriticalWorldData', 'CriticalMachineState', 'CriticalGeometry', 'CriticalStructures',
    'CriticalMachineKind', 'CriticalConfiguration', 'CriticalRenderAnchors', 'CriticalStructureMonitor',
    'CriticalChunkLoading', 'CriticalChunkTicketManager', 'CriticalLoomStructure', 'TileEntityCriticalRenderAnchor',
    'BlockCriticalRenderAnchor',
)]
PROBE = 'tools/critical/CriticalProtectionContract.java'
NATIVE = 'tools/critical/CriticalProtectionNativeProbe.java'
GATES = [ROOT / 'tools/tests/structurelib-gates' / name for name in ('StructureLibAPI.java', 'FMLCommonHandler.java')]
API = [ROOT / 'build/classes/java/patchedMc' / name for name in (
    'net/minecraft/block/Block.class', 'net/minecraft/entity/Entity.class', 'net/minecraft/world/Explosion.class',
    'net/minecraftforge/event/world/ExplosionEvent$Detonate.class',
)]


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def run(command, log):
    proc = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
    log.write_text(proc.stdout + proc.stderr, encoding='utf-8')
    return proc


def args(path, entries):
    path.write_text('\n'.join('"' + str(x).replace('\\', '/') + '"' for x in entries), encoding='utf-8')
    return '@' + str(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'; classes.mkdir(exist_ok=True)
    gates = OUT / 'gates'; gates.mkdir(exist_ok=True)
    binding = {name: sha(ROOT / name) for name in OWNED + SUPPORT + [PROBE, NATIVE]}
    jars = list(CACHE.glob('*/*/*/*/*.jar'))
    cp = ';'.join(map(str, [classes, ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc',
                          ROOT / 'src/main/resources', *jars]))
    sources = [ROOT / name for name in OWNED + SUPPORT + [PROBE] if name.endswith('.java')]
    result = run([str(JAVA / 'javac.exe'), args(OUT / 'compile.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])], OUT / 'compile.log')
    if result.returncode: raise RuntimeError(result.stderr)
    result = run([str(JAVA / 'javac.exe'), args(OUT / 'native-compile.args',
        ['-encoding', 'UTF-8', '-proc:none', '-source', '8', '-target', '8', '-cp', cp, '-d', OUT / 'native-compile', ROOT / NATIVE])], OUT / 'native-compile.log')
    if result.returncode: raise RuntimeError(result.stderr)
    result = run([str(JAVA / 'javac.exe'), args(OUT / 'gates.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', gates, *GATES])], OUT / 'gates.log')
    if result.returncode: raise RuntimeError(result.stderr)
    cp = str(gates) + ';' + cp
    mainclass = 'com.miaokatze.gtsr.common.critical.CriticalProtectionContract'
    result = run([str(JAVA / 'java.exe'), args(OUT / 'run.args',
        ['-Djava.awt.headless=true', '-cp', cp, mainclass, OUT / 'candidate.json'])], OUT / 'candidate.log')
    if result.returncode: raise RuntimeError(result.stderr)
    before = OUT / 'before-classes'; before.mkdir(exist_ok=True)
    old = ROOT / 'build/critical-v2-runtime-v12131/source-baseline' / PREFIX / 'common/critical/CriticalMaterials.java'
    result = run([str(JAVA / 'javac.exe'), args(OUT / 'red-compile.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', before, old])], OUT / 'red-compile.log')
    if result.returncode: raise RuntimeError(result.stderr)
    result = run([str(JAVA / 'java.exe'), args(OUT / 'red-run.args',
        ['-Djava.awt.headless=true', '-cp', str(before) + ';' + cp, mainclass, OUT / 'baseline-red.json'])], OUT / 'baseline-red.log')
    baseline = json.loads((OUT / 'baseline-red.json').read_text(encoding='utf-8'))
    assert result.returncode != 0 and 'actual block hardness -1 paid partial' in baseline.get('failure', ''), baseline
    assert binding == {name: sha(ROOT / name) for name in binding}, 'Owner/support sources changed during fixture run'
    candidate = json.loads((OUT / 'candidate.json').read_text(encoding='utf-8'))
    receipt = dict(status='PASS', baselineCommit='2254676', candidateChecks=len(candidate['checks']),
        baselineFailure=baseline['failure'], ownerSourceSHA256={name: binding[name] for name in OWNED},
        supportingSourceSHA256={name: binding[name] for name in SUPPORT},
        probeSourceSHA256={name: binding[name] for name in (PROBE, NATIVE)},
        fixtureAPISHA256={p.relative_to(ROOT).as_posix(): sha(p) for p in API + GATES},
        actualPaths=['MaterialBlock.getBlockHardness(World,III)',
                     'MaterialBlock.getExplosionResistance(Entity,World,III,DDD)',
                     'MaterialBlock.onBlockExploded(World,III,Explosion)',
                     'StructureChangeListener.onExplosion(ExplosionEvent.Detonate)',
                     'WorldData persistent claim NBT with absent controller',
                     'client anchor visual snapshot; no client WorldSavedData'],
        limitations=['Sparse World replaces chunk/persistence and tile lookup APIs; no game or FML startup.',
                     'StructureLibAPI/FMLCommonHandler existing fixture gates suppress diagnostics/startup only.',
                     'Block/GT/Entity Mixin execution and full explosion/GUI lifecycle require native fixture verification.'],
        repeatCommand='python tools/critical/verify_protection_v31.py')
    (OUT / 'checks.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(dict(status='PASS', checks=receipt['candidateChecks'], baselineReproduced=True)))


if __name__ == '__main__':
    main()
