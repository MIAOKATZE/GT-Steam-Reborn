"""Detached source-compiled cardinal render checks; no GL context, FML or game process."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12127/render'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CLIENT = ROOT / 'src/main/java/com/miaokatze/gtsr/client/critical'
COMMON = ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical'
OWNED = [CLIENT / (name + '.java') for name in [
    'CriticalControllerRenderer', 'CriticalGroupVisibility', 'CriticalLightCache', 'CriticalMechanicalPose',
]] + [ROOT / 'tools/tests/CriticalCardinalRenderContract.java', Path(__file__)]


def argfile(name, args):
    path = OUT / name
    path.write_text('\n'.join('"' + str(arg).replace('\\', '/') + '"' for arg in args), encoding='utf-8')
    return '@' + str(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    previous = (ROOT / 'build/critical-v2-runtime-v12126/render/compile.args').read_text().splitlines()
    old_cp = previous[previous.index('"-cp"') + 1].strip('"').split(';')
    # All modified production sources compile into the first classpath entry. Legacy detached fixtures only
    # supply harmless texture/TESR plumbing; no OpenGL function is called by this contract.
    cp = ';'.join([str(classes), str(ROOT / 'build/classes/java/main')] + old_cp[1:])
    client_names = ['CriticalControllerRenderer', 'CriticalGroupVisibility', 'CriticalLightCache',
        'CriticalMechanicalPose', 'CriticalAnimation', 'CriticalMeshCache', 'CriticalMaterialRenderer',
        'CriticalFX', 'CriticalFXBudget', 'CriticalSolarFX', 'CriticalSunFX', 'CriticalRenderMetrics']
    common_names = ['CriticalStructureAlignment', 'CriticalMachineState', 'CriticalLoomStructure',
        'CriticalStructures', 'CriticalRenderAnchors', 'TileEntityCriticalRenderAnchor', 'CriticalLoomProxyVisibility']
    sources = [CLIENT / (name + '.java') for name in client_names] + [COMMON / (name + '.java') for name in common_names]
    sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/MTECriticalMachine.java',
        ROOT / 'tools/tests/CriticalCardinalRenderContract.java']
    compile_result = subprocess.run([str(JAVA / 'javac.exe'), argfile('compile.args', [
        '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources,
    ])], capture_output=True, text=True)
    (OUT / 'compile.log').write_text(compile_result.stdout + compile_result.stderr, encoding='utf-8')
    assert compile_result.returncode == 0, compile_result.stderr
    run = subprocess.run([str(JAVA / 'java.exe'), argfile('run.args', [
        '-Djava.awt.headless=true', '-cp', cp, 'com.miaokatze.gtsr.client.critical.CriticalCardinalRenderContract',
        ROOT / 'src/main/resources/assets/gtsr/critical/models', OUT / 'coordinates.json',
    ])], capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT / 'stdout.log').write_text(run.stdout + run.stderr, encoding='utf-8')
    assert run.returncode == 0, run.stderr
    checks = []

    def check(name, passed, detail):
        checks.append({'name': name, 'pass': bool(passed), 'detail': detail})
        assert passed, name

    renderer = (CLIENT / 'CriticalControllerRenderer.java').read_text()
    group = (CLIENT / 'CriticalGroupVisibility.java').read_text()
    light = (CLIENT / 'CriticalLightCache.java').read_text()
    material = (CLIENT / 'CriticalMaterialRenderer.java').read_text()
    check('tesr.outer-frame-applied-once', renderer.count('GL11.glRotated(controller.getStructureYawDegrees(), 0, 1, 0)') == 1
        and renderer.index('GL11.glRotated(controller.getStructureYawDegrees()') < renderer.index('if (anchor.isLoomVisible()) renderModel')
        and 'x + controller.getMeshOriginX() - tile.xCoord' in renderer
        and 'z + controller.getMeshOriginZ() - tile.zCoord' in renderer,
        'Single owner-frame transform encloses the loom, base, installation and FX. Local mechanical poses are untouched.')
    check('camera.batch-sort-and-fx-lod', re.search(r'cameraX\s*=\s*controller\s*\.localPointX\s*\(', renderer) is not None
        and re.search(r'cameraZ\s*=\s*controller\s*\.localPointZ\s*\(', renderer) is not None
        and 'matrix[14] - cameraZ' in renderer
        and 'dy = cameraY - controller.getRegionHeight() * .55' in renderer,
        'Both blend sorting and particle distance use the inverse owner frame, with unchanged thresholds.')
    check('visibility.physical-owner-cells', 'controller.blockWorldX(voxel)' in group
        and 'controller.blockWorldZ(voxel)' in group
        and 'CriticalLoomStructure.matchesVoxel(controller, voxel)' in group
        and 'controller.getStructureFacing()' in group,
        'Complete-group physical proxy checks and cache identity follow the same owner.')
    proxy = (COMMON / 'CriticalLoomProxyVisibility.java').read_text()
    check('chunk.proxy-index-owner-frame', 'CriticalStructureAlignment.rotateX(' in proxy
        and 'CriticalStructureAlignment.rotateZ(' in proxy and 'state.getStructureFacing()' in proxy,
        'Loom chunk proxy registration uses the same owner frame; detached common contract separately checks exact indexed cells.')
    check('light.pose-then-owner-frame', 'CriticalStructureAlignment.rotateX(x, z, facing)' in light
        and 'CriticalStructureAlignment.rotateZ(x, z, facing)' in light
        and 'renderingController.getMeshOriginX()' in renderer
        and 'renderingController.getStructureFacing()' in renderer,
        'Native World sampling consumes continuous mesh origin plus owner frame after the mechanical affine pose.')
    check('static.chunk-cube-has-world-coordinates', 'face(tessellator, x, y, z, side, albedo, 0)' in material
        and 'double cx = x + .5 + normal[0]' in material and 'getStructureYawDegrees' not in material,
        'Physical blocks already occupy owner-rotated world cells and use native unit-cube vertices.')
    check('frustum.owner-region', 'getVisualState().getRenderBoundingBox()' in (COMMON / 'TileEntityCriticalRenderAnchor.java').read_text(),
        'Anchor TESR frustum forwards the rotated owner region AABB.')
    model_names = ['v2_loom', 'v2_base_t3', 'v2_solar', 'v2_sun', 'v2_legacy_loom', 'v2_v25_loom']
    report = {'status': 'PASS', 'runtime': json.loads((OUT / 'coordinates.json').read_text()), 'sourceWiringChecks': checks,
        'sourceSHA256': {str(path.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sources + [Path(__file__)]},
        'modelsSHA256': {name: hashlib.sha256((ROOT / 'src/main/resources/assets/gtsr/critical/models' /
            (name + '.geometry.json')).read_bytes()).hexdigest() for name in model_names},
        'limitations': 'No live renderer, native GL transform, FML, texture atlas, chunk dispatcher or saved-world integration is claimed.'}
    (OUT / 'checks.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (OUT / 'owned-paths.json').write_text(json.dumps({'owner': 'cardinal_render_fix_v27',
        'ownedPaths': [str(path.relative_to(ROOT)).replace('\\', '/') for path in OWNED],
        'reportsRoot': str(OUT.relative_to(ROOT)).replace('\\', '/')}, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'runtime': report['runtime'], 'sourceWiringChecks': len(checks)}))


if __name__ == '__main__':
    main()
