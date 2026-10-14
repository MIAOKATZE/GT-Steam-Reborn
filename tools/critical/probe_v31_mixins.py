"""Candidate-bound real protection Mixin micro bootstrap. No game/FML/server launch."""
from pathlib import Path
import argparse, hashlib, json, subprocess
ROOT=Path(__file__).resolve().parents[2]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
JAVA=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',required=True,type=Path);p.add_argument('--phase',default='candidate-mixins');args=p.parse_args()
    jar=args.jar.resolve();out=ROOT/'build/critical-v2-runtime-v12131/probe'/args.phase;out.mkdir(parents=True,exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
    unimixins=next(CACHE.glob('io.github.legacymoddingmc/unimixins/0.3.1/*/*-dev.jar'))
    gt=next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    chisel=ROOT/'build/coke-rebuild-v12114/native/natural-final/fixture/mods/chisel-2.17.33-GTNH.jar'
    jars=[unimixins,gt,chisel]+[x for x in CACHE.glob('*/*/*/*/*.jar') if not x.name.startswith(('unimixins-','GT5-Unofficial-'))]
    cp=';'.join(map(str,[classes,jar,ROOT/'build/classes/java/patchedMc']+jars))
    sources=[ROOT/'tools/critical'/n for n in ['CriticalV31MixinLauncher.java','CriticalV31ProtectionMixinRuntime.java','CriticalProtectionContract.java']]+[ROOT/'tools/tests/structurelib-gates'/n for n in ['FMLCommonHandler.java','StructureLibAPI.java']]
    (classes/'mixins.gtsr.probe.v31.json').write_text(json.dumps(dict(required=True,minVersion='0.8.5',package='com.miaokatze.gtsr.mixin',compatibilityLevel='JAVA_8',mixins=['MixinBlockCriticalProtection','MixinEntityCriticalProtection','BlockMachinesCriticalProtectionMixin'])))
    binding=dict(candidate=str(jar),sha256=sha(jar),probeSources={str(s.relative_to(ROOT)):sha(s) for s in sources},unimixinsSHA256=sha(unimixins),GT205SHA256=sha(gt),chiselApiJar=str(chisel),chiselSHA256=sha(chisel),fullMinecraftStarted=False,FMLStarted=False)
    def call(exe,values,stem):
        argfile=out/(stem+'.args');argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values))
        with (out/(stem+'.log')).open('w',encoding='utf-8') as log:r=subprocess.run([str(JAVA/exe),'@'+str(argfile)],cwd=out,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        if r.returncode:print((out/(stem+'.log')).read_text(encoding='utf-8',errors='replace')[-10000:]);raise RuntimeError(stem+' failed')
    call('javac.exe',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes]+sources,'compile')
    call('java.exe',['-Djava.awt.headless=true','-Dmixin.debug.export=true','-Dmixin.debug.export.decompile=false','-cp',cp,'CriticalV31MixinLauncher',out/'runtime.json'],'run')
    if sha(jar)!=binding['sha256']:raise RuntimeError('Candidate changed during probe')
    binding['postrunSHA256']=sha(jar);binding['transformedClassSHA256']={str(x.relative_to(out)):sha(x) for x in (out/'.mixin.out/class').rglob('*.class')};binding['status']='PASS';(out/'binding.json').write_text(json.dumps(binding,indent=2));print((out/'runtime.json').read_text())
if __name__=='__main__':main()
