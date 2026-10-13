"""Detached GTMB declaration and StructureLib coordinate contract. Never starts Minecraft."""
from pathlib import Path
import hashlib
import json
import subprocess
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12128/structure'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
GTMB = Path('E:/CodeGT/GT-Miao-Blueprint')
SOURCES = [
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalStructureAlignment.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalStructures.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalLoomStructure.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/MTECriticalMachine.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalMachineState.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalRenderAnchors.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalLoomProxyVisibility.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalGeometry.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalMaterials.java',
    ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalRuntime.java',
]
TEXT_PATHS = [ROOT / 'src/main/resources/assets/gtsr/lang' / language for language in ['zh_CN.lang', 'en_US.lang']]


def argfile(name, args):
    target = OUT / name
    target.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    return '@' + str(target)


def geometry_reference():
    models = ROOT / 'src/main/resources/assets/gtsr/critical/models'
    results = []
    def java_hash(value):
        h = 0
        for char in value:
            h = (31 * h + ord(char)) & 0xffffffff
        return h if h < 0x80000000 else h - 0x100000000
    for revision in (24, 25, 26, 27, 28):
        suffix = 'legacy_' if revision == 24 else 'v25_' if revision == 25 else 'v27_' if revision <= 27 else ''
        for kind in ('solar', 'sun'):
            def read(slug):
                relative = f'src/main/resources/assets/gtsr/critical/models/{slug}.geometry.json'
                if revision <= 27:
                    original_slug = slug.replace('v2_v27_', 'v2_')
                    relative = relative.replace(slug, original_slug)
                    raw = subprocess.check_output(['git', 'show', f'16c15ed2:{relative}'], cwd=ROOT)
                else:
                    raw = (models / (slug + '.geometry.json')).read_bytes()
                return json.loads(raw)['blocks']
            loom_slug = f'v2_{suffix}loom'
            base_slug = f'v2_{suffix}base_t3'
            cells = {}
            slugs = [loom_slug, base_slug, f'v2_{kind}'] if revision == 24 else [base_slug, f'v2_{kind}', loom_slug]
            for slug in slugs:
                for x, y, z, material, group in read(slug):
                    stage = 0 if slug.endswith('loom') else min(3, y // 16 + 1) if 'base_' in slug else 4
                    cells[x, y, z] = (x, y, z, stage, material, group, slug.endswith('loom'))
            rows = sorted(cells.values(), key=lambda v: (v[3], v[1], v[0], v[2]))
            fingerprint = 0xcbf29ce484222325
            lines = []
            for x, y, z, stage, material, group, manual in rows:
                position = ((x & 0xfffff) << 40) | ((y & 0xfffff) << 20) | (z & 0xfffff)
                fingerprint = ((fingerprint ^ position) * 0x100000001b3) & 0xffffffffffffffff
                fingerprint = ((fingerprint ^ java_hash(material) ^ java_hash(group) ^ stage) * 0x100000001b3) & 0xffffffffffffffff
                lines.append(f'{x},{y},{z},{stage},{material},{group}\n')
            results.append({'revision': revision, 'kind': kind, 'total': len(rows),
                            'manual': sum(v[6] for v in rows), 'paid': sum(not v[6] for v in rows),
                            'fingerprint': fingerprint if fingerprint < 0x8000000000000000 else fingerprint - 0x10000000000000000,
                            'indexSHA256': hashlib.sha256(''.join(lines).encode()).hexdigest()})
    (OUT / 'geometry-reference.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    return results


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    previous = (ROOT / 'build/critical-v2-runtime-v12126/render/compile.args').read_text().splitlines()
    previous_cp = previous[previous.index('"-cp"') + 1].strip('"').split(';')
    dependencies = [str(ROOT / 'build/classes/java/main'), str(GTMB / 'build/classes/java/main')] + previous_cp
    assert dependencies, 'Existing detached dependency classpath is required'
    cp = ';'.join([str(classes), str(ROOT / 'src/main/resources')] + dependencies)
    probe = ROOT / 'tools/tests/CriticalStructureAlignmentContract.java'
    lifecycle = ROOT / 'tools/tests/CriticalLoomLifecycleContract.java'
    upstream_nbt = GTMB / 'src/main/java/com/miaokatze/gtmb/hologram/HologramBlueprintNbt.java'
    gate = ROOT / 'tools/tests/structurelib-gates/StructureLibAPI.java'
    fml_gate = ROOT / 'tools/tests/structurelib-gates/FMLCommonHandler.java'
    compile_result = subprocess.run([str(JAVA / 'javac.exe'), argfile('compile.args', [
        '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *SOURCES, probe, lifecycle, upstream_nbt,
    ])], capture_output=True, text=True)
    (OUT / 'compile.log').write_text(compile_result.stdout + compile_result.stderr, encoding='utf-8')
    assert compile_result.returncode == 0, compile_result.stderr
    (OUT / 'compile-check.json').write_text(json.dumps({
        'status': 'PASS', 'exitCode': compile_result.returncode,
        'mode': 'Detached javac; all changed structure/common production Java sources plus contract probe.',
        'productionSourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES},
        'compiler': str(JAVA / 'javac.exe'), 'arguments': str(OUT / 'compile.args'),
        'log': str(OUT / 'compile.log'),
    }, indent=2), encoding='utf-8')
    gates = OUT / 'diagnostic-gates'
    gates.mkdir(exist_ok=True)
    gate_compile = subprocess.run([str(JAVA / 'javac.exe'), argfile('gates.args', ['-cp', cp, '-d', gates, gate, fml_gate])], capture_output=True, text=True)
    assert gate_compile.returncode == 0, gate_compile.stderr
    run = subprocess.run([str(JAVA / 'java.exe'), argfile('run.args', [
        '-Djava.awt.headless=true', '-cp', str(gates) + ';' + cp,
        'com.miaokatze.gtsr.common.critical.CriticalStructureAlignmentContract',
        ROOT / 'src/main/resources/assets/gtsr/critical/models', OUT / 'coordinates.json',
    ])], capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT / 'stdout.log').write_text(run.stdout + run.stderr, encoding='utf-8')
    assert run.returncode == 0, run.stderr
    geometry = geometry_reference()
    lifecycle_run = subprocess.run([str(JAVA / 'java.exe'), argfile('lifecycle.args', [
        '-Djava.awt.headless=true', '-cp', str(gates) + ';' + cp,
        'com.miaokatze.gtsr.common.critical.CriticalLoomLifecycleContract', OUT / 'lifecycle.json', OUT / 'geometry-reference.json',
    ])], capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT / 'lifecycle.log').write_text(lifecycle_run.stdout + lifecycle_run.stderr, encoding='utf-8')
    assert lifecycle_run.returncode == 0, lifecycle_run.stderr

    checks = []

    def check(name, passed, detail):
        checks.append({'name': name, 'pass': bool(passed), 'detail': detail})
        assert passed, name

    mixin_path = GTMB / 'src/mixin/java/com/miaokatze/gtmb/mixin/hologram/MixinHologramBuildPiece.java'
    capture_path = GTMB / 'src/main/java/com/miaokatze/gtmb/hologram/HologramCapture.java'
    mixin = mixin_path.read_text(encoding='utf-8')
    capture = capture_path.read_text(encoding='utf-8')
    check('gtmb.actual-hook', 'buildPiece(Ljava/lang/String;' in mixin
          and 'HologramCapture.piece(this, name, trigger, hints, a, b, c)' in mixin,
          'Actual GTMB mixin intercepts GT buildPiece; direct definition.buildOrHints bypasses this hook.')
    check('gtmb.actual-piece-conditions', 'capture.context != controller || !hints' in capture
          and 'new Piece(name, a, b, c,' in capture and 'capture.pieces.isEmpty()' in capture
          and 'NO_BUILD_PIECES' in capture,
          'Exact ACTIVE capture/controller identity/hints-only conditions; zero declarations fails NO_BUILD_PIECES.')
    check('gtmb.actual-anchor-facing', 'walkPieces(capture, definition, world, machine.getExtendedFacing(), x, y, z)' in capture
          and 'piece.a, piece.b, piece.c' in capture,
          'Real controller tile XYZ and returned native facing are used with captured offsets.')

    current = [p.read_text(encoding='utf-8') for p in SOURCES]
    baseline_loom = subprocess.run(['git', 'show', 'ab0a48ac:src/main/java/com/miaokatze/gtsr/common/critical/CriticalLoomStructure.java'],
                                   cwd=ROOT, capture_output=True, text=True, encoding='utf-8', check=True).stdout
    check('baseline.bypass-reproduced', 'definition(host).buildOrHints(' in baseline_loom
          and '.buildCriticalPiece(' not in baseline_loom,
          'V1.21.26 calls the definition directly: under the exact GTMB hook conditions it declares zero pieces.')
    check('candidate.gt-build-entry', 'host.buildCriticalPiece(' in current[2]
          and 'return buildPiece(piece, trigger, hintsOnly, a, b, c);' in current[3]
          and '.buildOrHints(' not in current[1] and '.buildOrHints(' not in current[2],
          'Every critical hints/build declaration now reaches GT buildPiece, with the same host identity.')
    check('candidate.survival-entry', 'host.survivalBuildCriticalPiece(' in current[2]
          and 'return survivalBuildPiece(piece, trigger, a, b, c, budget, environment, false);' in current[3],
          'Survival uses the native GT entry point and same controller-relative offset helper.')
    check('candidate.both-pieces', 'CriticalLoomStructure.PIECE.equals(piece) ? loom : full' in current[1],
          'Either controller exposes both the loom and full target definitions for the declared piece.')
    check('candidate.real-host-check-anchor', re.search(r'tile\.getXCoord\(\),\s*tile\.getYCoord\(\),\s*tile\.getZCoord\(\),', current[2]),
          'The loom check anchors at the host tile, including a main controller that proxies the loom journal.')
    check('candidate.minimum-interfaces', 'items || ignoreItems' in current[2]
          and 'fluids || ignoreFluids' in current[2] and 'energy || ignoreEnergy' in current[2],
          'Normal construction requires at least one item input bus, one fluid input hatch, one energy input.')
    check('candidate.v2-casing', 'CriticalControllerTextures.casingIndex(v2)' in current[1]
          and 'CriticalControllerTextures.casingIndex(current.getGeometryVersion() == 2)' in current[3],
          'Native structure matching and natural hatch discovery select the V2 reinforced panel texture.')
    service = GTMB / 'src/main/java/com/miaokatze/gtmb/hologram/HologramService.java'
    upstream_service = service.read_text(encoding='utf-8')
    check('gtmb.palette-direct-placement', 'w.setBlock(c.x, c.y, c.z, c.block, c.meta, 3)' in upstream_service
          and 'placedTile.readFromNBT(data)' in upstream_service,
          'Schema>=2 native-supported blocks use direct World.setBlock; other targets use declared/survival placement. Retained GT NBT uses placedTile.readFromNBT(data).')
    check('candidate.unaligned-origin-and-safety', '施工区域原点必须按区块对齐' not in current[4]
          and 'ensureConstructionWindow(Collections.emptyList(), 0, 0)' in current[4]
          and 'getOriginY() + getRegionHeight() > worldObj.getHeight()' in current[4]
          and 'worldObj.canMineBlock(player, wx(v), wy(v), wz(v))' in current[4],
          'Origin alignment is removed while loaded-window, world-height and permission gates remain.')
    binding = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES + TEXT_PATHS + [probe, lifecycle, gate, fml_gate, Path(__file__)]}
    report = {
        'status': 'PASS', 'mode': 'Detached JVM plus exact upstream source contracts; no game/server/client launched.',
        'coordinates': json.loads((OUT / 'coordinates.json').read_text()), 'checks': checks,
        'lifecycle': json.loads((OUT / 'lifecycle.json').read_text()),
        'geometry': geometry,
        'sourceSHA256': binding, 'productionSourceSHA256': binding,
        'upstreamSHA256': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [mixin_path, capture_path, upstream_nbt, service]},
        'limitations': 'Does not claim live GTMB right-click, paid inventory placement, native GT World hatch/EU checks, or gameplay validation. Conditional retained native NBT is replayed through actual upstream forPlacement bytecode, real GTSR read/write and orphan guards.',
        'traversalHarness': 'Real StructureLib traversal and actual GTSR geometry definition/shell matching in a sparse detached World. Test-only StructureLibAPI debug/instrument gates return false; FMLCommonHandler supplies only Side.SERVER for GT superclass side queries, preventing FML startup. GTMB ownership rewriting is unmodified source.',
    }
    (OUT / 'checks.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    owned_assets = [ROOT / 'src/main/resources/assets/gtsr/critical/source-sha256.json',
                   *[ROOT / 'src/main/resources/assets/gtsr/critical/models' / (slug + '.geometry.json')
                     for slug in ['v2_loom', 'v2_base_t3', 'v2_v27_loom', 'v2_v27_base_t3']],
                   ROOT / 'tools/critical/amend_loom_v28.py']
    changed = set(subprocess.check_output(['git', 'diff', '--name-only'], cwd=ROOT).decode().splitlines())
    owned_production = [p for p in SOURCES if p.relative_to(ROOT).as_posix() in changed]
    (OUT / 'owned-paths.json').write_text(json.dumps({'owner': 'loom_lifecycle_v28',
        'ownedPaths': [str(p.relative_to(ROOT)).replace('\\', '/') for p in owned_production + [probe, lifecycle, fml_gate, Path(__file__)] + owned_assets],
        'reportsRoot': str(OUT.relative_to(ROOT)).replace('\\', '/')}, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'checks': len(checks), 'coordinates': report['coordinates']}))


if __name__ == '__main__':
    main()
