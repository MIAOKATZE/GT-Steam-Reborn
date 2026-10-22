"""Old/new real GL regression for V2 rail overlay; never starts Minecraft, FML or a server."""
from pathlib import Path
import hashlib
import json
from material_palette import expand_palette
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12128/render'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
CLIENT = ROOT / 'src/main/java/com/miaokatze/gtsr/client/critical'
OWNED = [CLIENT / (name + '.java') for name in (
    'CriticalMaterialRenderer', 'CriticalControllerRenderer', 'CriticalGroupVisibility',
)] + [Path(__file__), Path(__file__).with_name('CriticalRailRenderV28Probe.java')]
HELPER = '''package net.minecraft.client.renderer;
import org.lwjgl.opengl.*;
public class OpenGlHelper {
 public static int defaultTexUnit=GL13.GL_TEXTURE0, lightmapTexUnit=GL13.GL_TEXTURE1;
 public static float lastBrightnessX,lastBrightnessY;
 public static void setLightmapTextureCoords(int u,float x,float y){lastBrightnessX=x;lastBrightnessY=y;GL13.glMultiTexCoord2f(u,x,y);}
 public static void setClientActiveTexture(int u){GL13.glClientActiveTexture(u);}
 public static void setActiveTexture(int u){GL13.glActiveTexture(u);}
 public static void glBlendFunc(int a,int b,int c,int d){GL14.glBlendFuncSeparate(a,b,c,d);}
}
'''


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path):
    return path.relative_to(ROOT).as_posix()


def argfile(folder, name, args):
    path = folder / name
    path.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    return '@' + str(path)


def run_probe(label, renderer_source, jars, natives):
    folder = OUT / label
    classes = folder / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    helper = folder / 'src/net/minecraft/client/renderer/OpenGlHelper.java'
    helper.parent.mkdir(parents=True, exist_ok=True)
    helper.write_text(HELPER, encoding='utf-8')
    cp = ';'.join(map(str, [classes, ROOT / 'build/classes/java/main', ROOT / 'build/resources/main',
        ROOT / 'src/main/resources', ROOT / 'build/classes/java/patchedMc', *jars]))
    sources = [renderer_source, CLIENT / 'CriticalMaterialTextures.java', CLIENT / 'CriticalMeshCache.java',
        CLIENT / 'CriticalGroupVisibility.java', helper, Path(__file__).with_name('CriticalRailRenderV28Probe.java')]
    compiled = subprocess.run([str(JAVA / 'javac.exe'), argfile(folder, 'compile.args', [
        '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources,
    ])], capture_output=True, text=True, encoding='utf-8', errors='replace')
    (folder / 'compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
    assert compiled.returncode == 0, compiled.stderr
    run = subprocess.run([str(JAVA / 'java.exe'), argfile(folder, 'run.args', [
        '-Djava.awt.headless=true', '-Djava.library.path=' + str(natives), '-cp', cp,
        'com.miaokatze.gtsr.client.critical.CriticalRailRenderV28Probe', folder,
    ])], capture_output=True, text=True, encoding='utf-8', errors='replace',
        creationflags=subprocess.CREATE_NO_WINDOW)
    (folder / 'stdout.log').write_text(run.stdout + run.stderr, encoding='utf-8')
    report = json.loads((folder / 'runtime.json').read_text(encoding='utf-8'))
    assert run.returncode == (0 if report['status'] == 'PASS' else 2), run.stderr
    assert Path(report['materialRendererCodeSource'].replace('file:/', '').replace('/', '\\')).resolve() == classes.resolve()
    return report


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hashes = {relative(p): digest(p) for p in OWNED}
    natives = OUT / 'natives'
    natives.mkdir(exist_ok=True)
    archive = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(archive) as zipped:
        zipped.extractall(natives)
    jars = list(CACHE.glob('*/*/*/*/*.jar'))
    before_source = OUT / 'before/source/CriticalMaterialRenderer.java'
    before_source.parent.mkdir(parents=True, exist_ok=True)
    baseline = subprocess.run(['git', 'show', '16c15ed2:src/main/java/com/miaokatze/gtsr/client/critical/CriticalMaterialRenderer.java'],
        cwd=ROOT, capture_output=True, check=True)
    before_source.write_bytes(baseline.stdout)
    before = run_probe('before', before_source, jars, natives)
    assert before['status'] == 'FAIL', 'Baseline must reproduce real emission loss'
    after = run_probe('after', CLIENT / 'CriticalMaterialRenderer.java', jars, natives)
    assert after['status'] == 'PASS', 'Candidate must match the depth independent emission oracle'
    controller = (CLIENT / 'CriticalControllerRenderer.java').read_text(encoding='utf-8')
    assert 'CriticalLoomProxyVisibility.publishVisibleGroups(anchor, activeGroups)' in controller
    assert controller.index('activeGroups = checked.update(controller)') < controller.index('publishVisibleGroups(anchor, activeGroups)')
    assert hashes == {relative(p): digest(p) for p in OWNED}, 'Owned sources changed during probe'
    materials = ROOT / 'src/main/resources/assets/gtsr/critical/materials.json'
    palette = expand_palette(json.loads(materials.read_text(encoding='utf-8')))
    rail = palette['v2_loom_guide']
    assert rail['alphaMode'] == 'opaque' and rail['ctm']['family'] == 'vertical-only'
    report = {
        'status': 'PASS', 'baselineCommit': '16c15ed2', 'before': before, 'after': after,
        'sourceSHA256': hashes, 'materialsSHA256': digest(materials),
        'exercisedReadOnlySourceSHA256': {relative(path): digest(path) for path in [
            CLIENT / 'CriticalMeshCache.java', CLIENT / 'CriticalMaterialTextures.java',
            ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalMaterials.java',
        ]},
        'realCTMRailTextureSHA256': {relative(path): digest(path) for path in
            (ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2/ctm/loom_guide').rglob('*.png')},
        'baselineRendererSHA256': digest(before_source),
        'candidateRendererClassSHA256': digest(OUT / 'after/classes/com/miaokatze/gtsr/client/critical/CriticalMaterialRenderer.class'),
        'fixtureOverrides': ['OpenGlHelper lightmap/texture-unit plumbing; actual patched Minecraft Tessellator retained'],
        'nativeArchive': str(archive), 'nativeArchiveSHA256': digest(archive),
        'rootCause': 'V2 chunk emission .0005 world-space offset yields different floating/24-bit raster depth. Exact same vertices under native LEQUAL eliminate view-dependent mask loss.',
        'ownershipWiring': 'Group visibility publishes the exact rendered slug:group set before proxy hiding; V27 frozen loom is recognized. Common lifecycle contract checks index transitions separately.',
        'limitations': 'No full client/server, FML, native full atlas stitching, chunk dispatcher or saved-world replay. Winding is verified from actual emitted chunk vertices and actual native CORNERS; GL exercise is the production chunk handler.',
    }
    (OUT / 'checks.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (OUT / 'owned-paths.json').write_text(json.dumps({'owner': 'rail_render_fix_v28',
        'ownedPaths': [relative(p) for p in OWNED], 'reportsRoot': relative(OUT)}, indent=2), encoding='utf-8')
    print(json.dumps({'status': 'PASS', 'baselineMissingEmissionPixels': before['overlay']['diagnosticMissingEmissionPixels'],
        'candidateMissingEmissionPixels': after['overlay']['diagnosticMissingEmissionPixels'],
        'realMaskPixelDifferences': after['overlay']['realMaskPixelsDifferentFromDepthIndependentOracle'],
        'renderer': after['renderer']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
