"""Isolated v31 production-class/Pbuffer probe. Never starts Minecraft, FML or a server."""
from pathlib import Path
import argparse, hashlib, json, subprocess, zipfile
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'build/critical-v2-runtime-v12131/probe'
CACHE = Path.home()/'.gradle/caches/modules-2/files-2.1'
JAVA = Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--jar',type=Path); parser.add_argument('--phase',default='exploratory');parser.add_argument('--section',choices=['all','chunks'],default='all')
    args=parser.parse_args(); out=OUT/args.phase; out.mkdir(parents=True,exist_ok=True)
    classes=out/'classes'; classes.mkdir(exist_ok=True); natives=out/'natives'; natives.mkdir(exist_ok=True)
    # Only the GL lightmap adapter is replaced; all GTSR/GT/World/Tessellator classes are real.
    import gl_fixture
    name='net/minecraft/client/renderer/OpenGlHelper.java'
    adapter=out/'src'/name; adapter.parent.mkdir(parents=True,exist_ok=True); adapter.write_text(gl_fixture.STUBS[name])
    archive=next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(archive) as z: z.extractall(natives)
    jars=list(CACHE.glob('*/*/*/*/*.jar'))
    gt=next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    production=[args.jar.resolve()] if args.jar else [ROOT/'build/critical-v2-runtime-v12131/integrity/classes',ROOT/'src/main/resources',ROOT/'build/libs/gtsr-v1.21.30-dev.jar']
    cp=';'.join(map(str,[classes]+production+[ROOT/'build/classes/java/patchedMc',gt]+jars))
    sources=[adapter,ROOT/'tools/critical/CriticalIntegrityContract.java',ROOT/'tools/critical/CriticalV31Probe.java',ROOT/'tools/critical/CriticalChunkLoadingV31Probe.java',ROOT/'tools/critical/CriticalV31ChunkCallbacks.java']
    if not args.jar:
        sources+=list((ROOT/'src/main/java/com/miaokatze/gtsr/client/critical').glob('*.java'))
        sources+=[ROOT/'src/main/java/com/miaokatze/gtsr/common/critical/CriticalLoomProxyVisibility.java']
    binding=dict(candidate=str(args.jar.resolve()) if args.jar else 'EXPLORATORY BUILD CLASSES',
        sha256=digest(args.jar) if args.jar else None,fixtures=[name],fullMinecraftStarted=False,FMLStarted=False,
        nativeArchive=str(archive),probeSources={str(p.relative_to(ROOT)):digest(p) for p in sources if p.is_relative_to(ROOT)})
    (out/'binding.json').write_text(json.dumps(binding,indent=2))
    def call(exe,values,stem):
        argfile=out/(stem+'.args');argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in values))
        with (out/(stem+'.log')).open('w') as log:
            r=subprocess.run([str(JAVA/exe),'@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        if r.returncode:
            print((out/(stem+'.log')).read_text(errors='replace')[-12000:]);raise RuntimeError(stem+' failed')
    call('javac.exe',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes]+sources,'compile')
    if args.section=='all':
        call('java.exe',['-Djava.library.path='+str(natives),'-Djava.awt.headless=true','-cp',cp,
             'com.miaokatze.gtsr.client.critical.CriticalV31Probe',out],'run')
    gates=out/'gates';gates.mkdir(exist_ok=True)
    gate_sources=[ROOT/'tools/tests/structurelib-gates'/name for name in ['FMLCommonHandler.java','StructureLibAPI.java']]
    call('javac.exe',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',gates]+gate_sources,'gates')
    call('java.exe',['-Djava.awt.headless=true','-cp',str(gates)+';'+cp,'com.miaokatze.gtsr.common.critical.CriticalV31ChunkCallbacks',out/'chunks.json'],'chunks')
    chunk_checks=json.loads((out/'chunks.json').read_text())
    if chunk_checks['status']!='PASS':raise RuntimeError('Chunk callbacks failed')
    if args.section=='chunks':
        print(json.dumps(chunk_checks,ensure_ascii=False))
        return
    checks=json.loads((out/'runtime.json').read_text()); print(json.dumps(checks,ensure_ascii=False))
    for name,expected in binding['probeSources'].items():
        if digest(ROOT/name)!=expected:raise RuntimeError('Probe source changed during execution: '+name)
    if args.jar:
        if digest(args.jar)!=binding['sha256']: raise RuntimeError('Candidate changed during probe')
        from urllib.parse import unquote,urlparse
        expected=args.jar.resolve().as_posix().lower()
        for source in checks['codeSources'].values():
            if unquote(urlparse(source).path).lstrip('/').lower()!=expected: raise RuntimeError('Wrong production CodeSource '+source)
        binding['postrunSHA256']=digest(args.jar);binding['candidateBinding']='PASS'
        (out/'binding.json').write_text(json.dumps(binding,indent=2))
    if checks['status']!='PASS': raise RuntimeError('Probe failure')
if __name__=='__main__': main()
