"""Run the V30 production Java contract without starting Minecraft, FML or a GTNH instance."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12130/critical'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
BASELINE = '8bf0be44011f202535c37fe7bc024e3507978d1f'
PRODUCTION = [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in (
    'common/critical/CriticalGeometry.java', 'common/critical/CriticalLoomProxyVisibility.java',
    'common/critical/CriticalMachineState.java', 'common/critical/CriticalRuntime.java',
    'common/critical/CriticalStructures.java', 'common/critical/CriticalWorldData.java',
    'common/critical/CriticalRenderAnchors.java',
    'client/critical/CriticalMaterialRenderer.java', 'client/critical/CriticalMeshCache.java',
)]
PROBE = ROOT / 'tools/tests/CriticalRuntimeV30Contract.java'
LANG = [ROOT / 'src/main/resources/assets/gtsr/lang' / language for language in ('zh_CN.lang', 'en_US.lang')]
GATES = [ROOT / 'tools/tests/structurelib-gates' / name for name in ('StructureLibAPI.java', 'FMLCommonHandler.java')]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def args(folder, name, values):
    p = folder / name
    p.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
    return '@' + str(p)


def run(label):
    folder = OUT / label
    classes = folder / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    jars = list(CACHE.glob('*/*/*/*/*.jar'))
    cp = ';'.join(map(str, [classes, ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc',
                            ROOT / 'src/main/resources', *jars]))
    sources = PRODUCTION[:]
    compiled = subprocess.run([str(JAVA / 'javac.exe'), args(folder, 'compile.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources, PROBE])],
        capture_output=True, text=True, encoding='utf-8', errors='replace')
    (folder / 'compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
    if compiled.returncode:
        raise RuntimeError(compiled.stderr)
    gates = folder / 'gates'
    gates.mkdir(exist_ok=True)
    compiled = subprocess.run([str(JAVA / 'javac.exe'), args(folder, 'gates.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', gates, *GATES])], capture_output=True)
    if compiled.returncode:
        raise RuntimeError(compiled.stderr.decode(errors='replace'))
    cp = str(gates) + ';' + cp
    receipt = folder / 'runtime.json'
    receipt.unlink(missing_ok=True)
    executed = subprocess.run([str(JAVA / 'java.exe'), args(folder, 'run.args',
        ['-Djava.awt.headless=true', '-cp', cp,
         'com.miaokatze.gtsr.common.critical.CriticalRuntimeV30Contract', receipt,
         'candidate'])], capture_output=True, text=True,
         encoding='utf-8', errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
    (folder / 'stdout.log').write_text(executed.stdout + executed.stderr, encoding='utf-8')
    if executed.returncode:
        raise RuntimeError(executed.stderr)
    return json.loads(receipt.read_text(encoding='utf-8'))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    binding = {p.relative_to(ROOT).as_posix(): sha(p) for p in PRODUCTION + LANG + [PROBE, Path(__file__)]}
    built_classes = [ROOT / 'build/classes/java/main' / p.relative_to(ROOT / 'src/main/java').with_suffix('.class') for p in PRODUCTION]
    built_binding = {p.relative_to(ROOT).as_posix(): sha(p) for p in built_classes}
    frozen = []
    def string_hash(text):
        h = 0
        for ch in text:
            h = (31 * h + ord(ch)) & 0xffffffff
        return h if h < 0x80000000 else h - 0x100000000
    for revision in (26, 27, 28, 29, 30):
        cells = {}
        prefix = 'v2_v27_' if revision <= 27 else 'v2_'
        for slug in (prefix + 'base_t3', 'v2_sun', prefix + 'loom'):
            raw = subprocess.check_output(['git', 'show', BASELINE + ':src/main/resources/assets/gtsr/critical/models/' + slug + '.geometry.json'], cwd=ROOT)
            for x, y, z, material, group in json.loads(raw)['blocks']:
                stage = 0 if slug.endswith('loom') else min(3, y // 16 + 1) if 'base_' in slug else 4
                cells[x, y, z] = (x, y, z, stage, material, group)
        rows = sorted(cells.values(), key=lambda row: (row[3], row[1], row[0], row[2]))
        fingerprint = 0xcbf29ce484222325
        text = ''
        for x, y, z, stage, material, group in rows:
            pos = x << 40 | y << 20 | z
            fingerprint = ((fingerprint ^ pos) * 0x100000001b3) & 0xffffffffffffffff
            fingerprint = ((fingerprint ^ string_hash(material) ^ string_hash(group) ^ stage) * 0x100000001b3) & 0xffffffffffffffff
            text += f'{x},{y},{z},{stage},{material},{group}\n'
        frozen.append(dict(revision=revision, count=len(rows), indexSHA256=hashlib.sha256(text.encode()).hexdigest(),
                           fingerprint=fingerprint if fingerprint < 0x8000000000000000 else fingerprint - 0x10000000000000000))
    (OUT / 'frozen-indices.json').write_text(json.dumps(frozen, indent=2), encoding='utf-8')
    # The before seam requires only the baseline renderer/index/geometry; current state APIs link but are not executed.
    # Compile the probe against current APIs first and put only baseline rendering classes ahead on its runtime classpath.
    candidate = run('candidate')
    baseline_folder = OUT / 'baseline'
    classes = baseline_folder / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    jars = list(CACHE.glob('*/*/*/*/*.jar'))
    cp = ';'.join(map(str, [classes, OUT / 'candidate/gates', OUT / 'candidate/classes', ROOT / 'build/classes/java/main',
                          ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))
    sources = []
    for name in ('common/critical/CriticalGeometry.java', 'common/critical/CriticalLoomProxyVisibility.java',
                 'client/critical/CriticalMaterialRenderer.java', 'client/critical/CriticalMeshCache.java'):
        relative = 'src/main/java/com/miaokatze/gtsr/' + name
        source = baseline_folder / 'source' / name
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_bytes(subprocess.check_output(['git', 'show', BASELINE + ':' + relative], cwd=ROOT))
        sources.append(source)
    result = subprocess.run([str(JAVA / 'javac.exe'), args(baseline_folder, 'compile.args',
        ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])],
        capture_output=True, text=True, encoding='utf-8', errors='replace')
    (baseline_folder / 'compile.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr)
    result = subprocess.run([str(JAVA / 'java.exe'), args(baseline_folder, 'run.args',
        ['-Djava.awt.headless=true', '-cp', cp,
         'com.miaokatze.gtsr.common.critical.CriticalRuntimeV30Contract', baseline_folder / 'runtime.json', 'baseline'])],
         capture_output=True, text=True, encoding='utf-8', errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
    (baseline_folder / 'stdout.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr)
    before = json.loads((baseline_folder / 'runtime.json').read_text(encoding='utf-8'))
    assert not before['decorativeSuppliesRendered'] and candidate['decorativeSuppliesRendered']
    assert binding == {p.relative_to(ROOT).as_posix(): sha(p) for p in PRODUCTION + LANG + [PROBE, Path(__file__)]}
    assert built_binding == {p.relative_to(ROOT).as_posix(): sha(p) for p in built_classes}, 'Production class rebuild overlapped probe'
    report = dict(status='PASS', baselineCommit=BASELINE, baseline=before, candidate=candidate,
        ownedSourceSHA256=binding, frozenIndices=frozen, fixtureAPISHA256={p.relative_to(ROOT).as_posix(): sha(p) for p in GATES},
        builtProductionClassSHA256=built_binding,
        fixtureOverrides=['Sparse World map replaces chunk/lighting persistence APIs, with real production coordinate/matching/ownership methods.',
                          'GT hatch instances have the real base/meta relation but no inventory or tick lifecycle.',
                          'Forge BreakEvent receives captured coordinates/block/world fields through Unsafe to avoid vanilla harvest-XP bootstrap.',
                          'Cancelable event subclasses provide the Forge cancelability flag without EventBus/FML initialization.',
                          'Existing StructureLibAPI and FMLCommonHandler diagnostic gates suppress only runtime startup.'],
        limitations=['Sparse World fixture and isolated production Java API execution; no Minecraft/FML/GTNH startup.',
                     'No saved live-world migration, real chunk dispatcher cache rebuild, gameplay raster/FPS or full client session claimed.',
                     'Native GT types are real dependency classes; their inventories and gameplay update lifecycle are not bootstrapped.'],
        repeatCommand='python tools/critical/verify_runtime_v30.py')
    (OUT / 'checks.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (OUT / 'owned-paths.json').write_text(json.dumps(dict(owner='critical_fix_v30',
        ownedPaths=list(binding), reportsRoot=OUT.relative_to(ROOT).as_posix()), indent=2), encoding='utf-8')
    print(json.dumps(dict(status='PASS', candidateChecks=candidate['checks'], baselineReproduced=True)))


if __name__ == '__main__':
    main()
