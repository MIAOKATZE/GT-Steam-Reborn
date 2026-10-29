"""Bounded full-renderer GPU diagnosis; never starts Minecraft/FML or a server."""
from pathlib import Path
import argparse, ctypes, hashlib, json, os, subprocess, time, zipfile
from ctypes import wintypes
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
CLIENT = ROOT / 'src/main/java/com/miaokatze/gtsr/client/critical'
PROBE = Path(__file__).with_name('Renderer41Probe.java')
NAMES = ['CriticalControllerRenderer', 'CriticalMeshCache', 'CriticalLightCache']
BASELINE = 'v2.0.40'


class ProcessMemory(ctypes.Structure):
    _fields_ = [('cb',wintypes.DWORD),('PageFaultCount',wintypes.DWORD)] + [(name,ctypes.c_size_t) for name in
        ['PeakWorkingSetSize','WorkingSetSize','QuotaPeakPagedPoolUsage','QuotaPagedPoolUsage',
         'QuotaPeakNonPagedPoolUsage','QuotaNonPagedPoolUsage','PagefileUsage','PeakPagefileUsage','PrivateUsage']]


def bounded_run(command):
    process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW)
    query=ctypes.windll.psapi.GetProcessMemoryInfo
    query.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD]
    peak=0;started=time.monotonic()
    while True:
        memory=ProcessMemory();memory.cb=ctypes.sizeof(memory)
        if query(wintypes.HANDLE(int(process._handle)),ctypes.byref(memory),memory.cb):
            peak=max(peak,memory.PeakWorkingSetSize)
        if peak>5*1024**3 or time.monotonic()-started>120:
            process.terminate();process.communicate();raise RuntimeError('Owned GPU probe exceeded memory/time bound')
        try:
            stdout,stderr=process.communicate(timeout=.1)
            return process.returncode,stdout+stderr,peak
        except subprocess.TimeoutExpired:
            pass


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--label', default='candidate')
    p.add_argument('--baseline', action='store_true', help='Compile historical v2.0.40 sources')
    p.add_argument('--jar', type=Path, help='Load all production rendering classes from candidate dev JAR')
    p.add_argument('--mask-only', action='store_true', help='Diagnostic mutation in a build copy only')
    p.add_argument('--posed-light', action='store_true', help='Diagnostic mutation in a build copy only')
    o = p.parse_args()
    out = ROOT / 'build/critical-emission-v2041' / o.label
    classes, sources, natives = out/('classes-jar' if o.jar else 'classes'), out/'sources', out/'natives'
    for folder in [classes,sources,natives]:folder.mkdir(parents=True,exist_ok=True)
    archive = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(archive) as z:z.extractall(natives)
    production=[]
    for name in NAMES:
        path=CLIENT/(name+'.java')
        historical=o.baseline or o.mask_only or o.posed_light
        text=(subprocess.run(['git','show',BASELINE+':'+path.relative_to(ROOT).as_posix()],capture_output=True,check=True,cwd=ROOT).stdout.decode('utf-8') if historical else path.read_text(encoding='utf-8'))
        if name=='CriticalMeshCache' and o.mask_only:text=text.replace('private static final float BODY_EMISSION = .18F;', 'private static final float BODY_EMISSION = 0F;')
        if name=='CriticalControllerRenderer' and o.posed_light:
            old='mesh.lightSamples,\n                        IDENTITY,'
            assert text.count(old)==1
            text=text.replace(old,'mesh.lightSamples,\n                        transform,')
        target=sources/(name+'.java');target.write_text(text,encoding='utf-8');production.append(target)
    fixture=sources/'TileEntitySpecialRenderer.java'
    fixture.write_text('''package net.minecraft.client.renderer.tileentity;
public abstract class TileEntitySpecialRenderer {
 public abstract void renderTileEntityAt(net.minecraft.tileentity.TileEntity t,double x,double y,double z,float p);
 protected void bindTexture(net.minecraft.util.ResourceLocation location) { /* Real atlas is already bound. */ }
}''',encoding='utf-8')
    lwjgl=next(CACHE.glob('org.lwjgl.lwjgl/lwjgl/*/*/lwjgl-*.jar'))
    jars=[q for q in CACHE.glob('*/*/*/*/*.jar') if q!=lwjgl and q.parts[-5]!='org.lwjgl']
    candidate=o.jar.resolve() if o.jar else None
    cp=os.pathsep.join(map(str,[classes,*([candidate] if candidate else []),ROOT/'build/classes/java/main',ROOT/'build/classes/java/patchedMc',ROOT/'src/main/resources',lwjgl,*jars]))
    started=time.monotonic()
    process_peaks={}
    for exe,label,values in [
        ('javac','compile',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes,*([] if candidate else production),fixture,PROBE]),
        ('java','run',['-Xms64M','-Xmx768M','-Djava.awt.headless=true','-Djava.library.path='+str(natives),'-cp',cp,'com.miaokatze.gtsr.client.critical.Renderer41Probe',out]),
    ]:
        argfile=out/(label+'.args');argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values),encoding='utf-8')
        code,result,peak=bounded_run([str(JAVA/(exe+'.exe')),'@'+str(argfile)])
        process_peaks[label]=peak
        result=result.decode('utf-8',errors='replace');(out/(label+'.log')).write_text(result,encoding='utf-8')
        if code:raise RuntimeError(result[-7000:])
    report=json.loads((out/'runtime.json').read_text())
    report['seconds']=time.monotonic()-started
    report['diagnosticMutation']={'maskOnly':o.mask_only,'posedLight':o.posed_light}
    report['baselineRef']=BASELINE if historical else None
    report['processPeakWorkingSetBytes']=process_peaks
    report['compiledSourceSHA256']={q.relative_to(ROOT).as_posix():hashlib.sha256(q.read_bytes()).hexdigest() for q in production}
    report['staticDynamicComparison']={}
    for level in [0,8,15]:
        a=Image.open(out/f'patch-light{level}-compile0-movedfalse.png').convert('RGB')
        b=Image.open(out/f'static-light{level}.png').convert('RGB')
        difference=ImageChops.difference(a,b)
        report['staticDynamicComparison'][str(level)]={'differentPixels':sum(max(pixel)>0 for pixel in difference.get_flattened_data()),'maxChannelError':max(high for low,high in difference.getextrema())}
    if candidate:
        report['candidateJarSHA256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
        assert all(report[name]==candidate.as_uri().replace('file:///','file:/') for name in ['controllerCodeSource','meshCodeSource','lightCacheCodeSource'])
    report['status']='DIAGNOSTIC' if historical else 'PASS'
    if not historical:
        assert all(value['maxChannelError']==0 for value in report['staticDynamicComparison'].values()),report['staticDynamicComparison']
        moved=report['cases'][-1]
        assert moved['lastSampleX']==10 and moved['foregroundMean']==0,moved
        assert all(case['glError']==0 and case.get('maxQueriesPerTick',0)<=16 for case in report['cases'])
    report['sourceSHA256']={q.relative_to(ROOT).as_posix():hashlib.sha256(q.read_bytes()).hexdigest() for q in [*(CLIENT/(n+'.java') for n in NAMES),PROBE,Path(__file__)]}
    (out/'receipt.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({'status':report['status'],'seconds':report['seconds'],'processPeakWorkingSetBytes':process_peaks,'staticDynamicComparison':report['staticDynamicComparison'],'moved':report['cases'][-1],'receipt':str(out/'receipt.json')},indent=2))


if __name__=='__main__':main()
