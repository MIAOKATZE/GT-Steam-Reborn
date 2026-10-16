"""Isolated real Angelica/Mixin/native-mesh probe; does not start Minecraft or FML."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v2-runtime-v12129/probe'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OWNED = [Path(__file__), Path(__file__).with_name('CriticalAngelicaV29Probe.java')]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def args(name, values):
    p = OUT / name
    p.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
    return '@' + str(p)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    natives=OUT/'natives'
    natives.mkdir(exist_ok=True)
    native_archive=next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(native_archive) as archive: archive.extractall(natives)
    angelica = next(CACHE.glob('com.github.GTNewHorizons/Angelica/2.2.28/*/*-dev.jar'))
    unimixins = next(CACHE.glob('io.github.legacymoddingmc/unimixins/0.3.1/*/*-dev.jar'))
    jars = [unimixins, angelica] + [p for p in CACHE.glob('*/*/*/*/*.jar')
        if p not in (unimixins, angelica) and not p.name.startswith(('unimixins-', 'Angelica-'))]
    cp = ';'.join(map(str, [classes, ROOT/'build/classes/java/main', ROOT/'build/classes/java/patchedMc',
        ROOT/'src/main/resources', ROOT/'build/resources/main', *jars]))
    (OUT/'classpath.txt').write_text(cp, encoding='utf-8')
    fixture_sources = {
        'mega/fluidlogged/api/FLBlockAccess.java':'package mega.fluidlogged.api; public interface FLBlockAccess {}',
        'com/gtnewhorizons/angelica/proxy/ClientProxy.java':'''package com.gtnewhorizons.angelica.proxy;
public final class ClientProxy {
 private static final me.jellysquid.mods.sodium.client.gui.SodiumGameOptions OPTIONS = new me.jellysquid.mods.sodium.client.gui.SodiumGameOptions();
 public static me.jellysquid.mods.sodium.client.gui.SodiumGameOptions options(){return OPTIONS;}
}''',
        'net/irisshaders/iris/api/v0/IrisApi.java':'''package net.irisshaders.iris.api.v0;
public interface IrisApi {
 static IrisApi getInstance(){return () -> false;}
 boolean isShaderPackInUse();
}''',
    }
    fixtures=[]
    for name,source in fixture_sources.items():
        fixture=OUT/'fixtures'/name
        fixture.parent.mkdir(parents=True,exist_ok=True)
        fixture.write_text(source,encoding='utf-8')
        fixtures.append(fixture)
    (classes/'mixins.gtsr.probe.v29.json').write_text(json.dumps({
        'required': True, 'minVersion': '0.8.5', 'package': 'com.miaokatze.gtsr.mixin.angelica',
        'compatibilityLevel': 'JAVA_8', 'mixins': ['CriticalMeshingTaskMixin',
        'CriticalChunkBuildContextMixin', 'CriticalMaterialFaceMixin']}), encoding='utf-8')
    compiled = subprocess.run([str(JAVA/'javac.exe'), args('compile.args', ['-encoding','UTF-8',
        '-proc:none','-cp',cp,'-d',classes,OWNED[1],*fixtures])], capture_output=True, text=True,
        encoding='utf-8', errors='replace')
    (OUT/'compile.log').write_text(compiled.stdout+compiled.stderr,encoding='utf-8')
    if compiled.returncode: raise RuntimeError(compiled.stderr)
    (OUT/'runtime.json').unlink(missing_ok=True)
    run = subprocess.run([str(JAVA/'java.exe'),args('run.args', ['-Djava.awt.headless=true',
        '--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED',
        '-Djava.library.path='+str(natives),'-Dmixin.debug.export=true','-Dmixin.debug.export.decompile=false',
        '-Dmixin.debug.verbose=true','-cp',cp,
        'CriticalAngelicaV29Probe',OUT])],capture_output=True,text=True,encoding='utf-8',errors='replace',
        cwd=OUT,creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT/'stdout.log').write_text(run.stdout+run.stderr,encoding='utf-8')
    complete=run.returncode==0 and (OUT/'runtime.json').exists()
    metadata = {'status': 'PASS' if complete else 'FAIL', 'exitCode':run.returncode,
        'angelicaJar':str(angelica),'angelicaJarSHA256':digest(angelica),
        'unimixinsJar':str(unimixins),'unimixinsJarSHA256':digest(unimixins),
        'ownedSourceSHA256':{p.relative_to(ROOT).as_posix():digest(p) for p in OWNED},
        'compileLogSHA256':digest(OUT/'compile.log'),'executionLogSHA256':digest(OUT/'stdout.log'),
        'nativeArchive':str(native_archive),'nativeArchiveSHA256':digest(native_archive),
        'fixtureSourceSHA256':{p.relative_to(OUT).as_posix():digest(p) for p in fixtures},
        'transformedClassSHA256':{p.relative_to(OUT).as_posix():digest(p)
            for p in (OUT/'.mixin.out/class').rglob('*.class')},
        'classpathSHA256':digest(OUT/'classpath.txt'),
        'repeatCommand':'python tools/critical/verify_angelica_v29.py',
        'prerequisite':'gradlew compileJava must first produce the current production classes; no game or FML startup.',
        'fixtureOverrides':[
            'ClientProxy options accessor: real SodiumGameOptions defaults without Minecraft singleton startup.',
            'IrisApi: shader-pack-active=false only; no Iris client startup.',
            'Absent FLBlockAccess: empty optional interface for linking the unused WorldSlice type.',
            'Unsafe constructs BuildContext with real Vertex[] and BlockRenderContext; null-sprite atlas fixture; vanilla AO and RGB tint disabled.',
            'MeshingTask outer renderBlock is not executed: its actual transformed merged WrapOperation handler is invoked with real native copyRawBuffer as Operation.',
            'GPU uses unmodified jar compute shader with one synthetic section containing the actual UNASSIGNED quad range; native SSBO dispatch and indirect commands are read back.',
            'Global-list fixture contains actual anchor in real RenderSection/MinecraftBuiltRenderSectionData; real iterator receives an empty SortedRenderLists.',
        ],
        'limitations':[
            'No full Minecraft/FML/server/client, saved-world replay, dispatcher, resource reload or final item rasterization.',
            'Global renderer distance/live-anchor-AABB checks are upstream source/bytecode review only; the actual runtime checks cover registry classification and global iteration.',
            'Production gate runs independently in four isolated loaders: actual 2.2.28, hidden resources, modified getVertexBuffer call, modified copyRawBuffer call. Other Angelica versions are not claimed.',
            'GPU fixture verifies face-mask retention and live frustum rejection, not a complete GPU-driven terrain frame or section occlusion query.',
            'The micro Mixin config applies exactly the three current production mixins. The actual repository plugin is tested independently against the same jar resources and absence/shape-negative fixtures.',
        ]}
    production=[ROOT/'src/main/java/com/miaokatze/gtsr/client/critical/CriticalAngelicaCompat.java',
        ROOT/'src/main/java/com/miaokatze/gtsr/client/critical/CriticalMaterialRenderer.java',
        ROOT/'src/main/java/com/miaokatze/gtsr/client/critical/CriticalMaterialTextures.java',
        ROOT/'src/main/java/com/miaokatze/gtsr/common/critical/CriticalMaterials.java',
        ROOT/'src/main/java/com/miaokatze/gtsr/common/critical/TileEntityCriticalRenderAnchor.java',
        ROOT/'src/main/java/com/miaokatze/gtsr/mixin/GTSRMixinPlugin.java',
        *(ROOT/'src/main/java/com/miaokatze/gtsr/mixin/angelica').glob('*.java'),
        ROOT/'src/main/resources/assets/gtsr/critical/materials.json']
    metadata['exercisedReadOnlySourceSHA256']={p.relative_to(ROOT).as_posix():digest(p) for p in production}
    with zipfile.ZipFile(angelica) as archive:
        shader=archive.read('assets/angelica/shaders/culling/chunk_cull.csh')
    metadata['actualComputeShaderSHA256']=hashlib.sha256(shader).hexdigest()
    if (OUT/'runtime.json').exists(): metadata['runtime']=json.loads((OUT/'runtime.json').read_text())
    (OUT/'checks.json').write_text(json.dumps(metadata,indent=2),encoding='utf-8')
    (OUT/'owned-paths.json').write_text(json.dumps({'owner':'angelica_probe_v29',
        'paths':[p.relative_to(ROOT).as_posix() for p in OWNED],
        'ownedPaths':[p.relative_to(ROOT).as_posix() for p in OWNED]},indent=2),encoding='utf-8')
    print(json.dumps(metadata,indent=2))
    if not complete: print(run.stdout+run.stderr); raise SystemExit(run.returncode or 1)

if __name__ == '__main__': main()
