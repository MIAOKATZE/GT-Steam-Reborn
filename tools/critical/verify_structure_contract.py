"""Detached GTMB declaration and StructureLib coordinate contract. Never starts Minecraft."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12127/structure'
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
]
TEXT_PATHS = [ROOT / 'src/main/resources/assets/gtsr/lang' / language for language in ['zh_CN.lang', 'en_US.lang']]


def argfile(name, args):
    target = OUT / name
    target.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    return '@' + str(target)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    previous = (ROOT / 'build/critical-v2-runtime-v12126/render/compile.args').read_text().splitlines()
    previous_cp = previous[previous.index('"-cp"') + 1].strip('"').split(';')
    dependencies = [str(ROOT / 'build/classes/java/main')] + previous_cp
    assert dependencies, 'Existing detached dependency classpath is required'
    cp = ';'.join([str(classes)] + dependencies)
    probe = ROOT / 'tools/tests/CriticalStructureAlignmentContract.java'
    gate = ROOT / 'tools/tests/structurelib-gates/StructureLibAPI.java'
    compile_result = subprocess.run([str(JAVA / 'javac.exe'), argfile('compile.args', [
        '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *SOURCES, probe,
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
    gate_compile = subprocess.run([str(JAVA / 'javac.exe'), '-d', str(gates), str(gate)], capture_output=True, text=True)
    assert gate_compile.returncode == 0, gate_compile.stderr
    run = subprocess.run([str(JAVA / 'java.exe'), argfile('run.args', [
        '-Djava.awt.headless=true', '-cp', str(gates) + ';' + cp,
        'com.miaokatze.gtsr.common.critical.CriticalStructureAlignmentContract',
        ROOT / 'src/main/resources/assets/gtsr/critical/models', OUT / 'coordinates.json',
    ])], capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT / 'stdout.log').write_text(run.stdout + run.stderr, encoding='utf-8')
    assert run.returncode == 0, run.stderr

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
    check('candidate.real-host-check-anchor', 'tile.getXCoord(),\n            tile.getYCoord(),\n            tile.getZCoord(),' in current[2],
          'The loom check anchors at the host tile, including a main controller that proxies the loom journal.')
    check('candidate.minimum-interfaces', 'items || ignoreItems' in current[2]
          and 'fluids || ignoreFluids' in current[2] and 'energy || ignoreEnergy' in current[2],
          'Normal construction requires at least one item input bus, one fluid input hatch, one energy input.')
    check('candidate.v2-casing', 'CriticalControllerTextures.casingIndex(v2)' in current[1]
          and 'CriticalControllerTextures.casingIndex(current.getGeometryVersion() == 2)' in current[3],
          'Native structure matching and natural hatch discovery select the V2 reinforced panel texture.')
    binding = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES + TEXT_PATHS + [probe, gate, Path(__file__)]}
    report = {
        'status': 'PASS', 'mode': 'Detached JVM plus exact upstream source contracts; no game/server/client launched.',
        'coordinates': json.loads((OUT / 'coordinates.json').read_text()), 'checks': checks,
        'sourceSHA256': binding, 'productionSourceSHA256': binding,
        'upstreamSHA256': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [mixin_path, capture_path]},
        'limitations': 'Does not claim live GTMB right-click, inventory placement, native World hatch checks, or gameplay validation.',
        'traversalHarness': 'Real StructureLib StructureDefinition/StructureUtility traversal bytecode. Test-only StructureLibAPI debug/instrument gates return false to avoid FML network bootstrap; a detached World overrides only blockExists=true.',
    }
    (OUT / 'checks.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (OUT / 'owned-paths.json').write_text(json.dumps({'owner': 'blueprint_structure_fix_v27',
        'ownedPaths': [str(p.relative_to(ROOT)).replace('\\', '/') for p in SOURCES + TEXT_PATHS + [probe, gate, Path(__file__)]],
        'reportsRoot': str(OUT.relative_to(ROOT)).replace('\\', '/')}, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'checks': len(checks), 'coordinates': report['coordinates']}))


if __name__ == '__main__':
    main()
