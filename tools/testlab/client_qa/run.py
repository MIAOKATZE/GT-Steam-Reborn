"""Owned offline full-client UI QA. No launcher account/token or source world is used."""
from pathlib import Path
import argparse, ctypes, hashlib, json, os, re, shutil, subprocess, time

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/mui2-client-qa'
FIXTURE=ROOT/'build/client-testlab-mui2'
LEASE=ROOT.parent/'.gtsr-native-server.lease'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def free_memory():
    class Status(ctypes.Structure):
        _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong),('total',ctypes.c_ulonglong),('available',ctypes.c_ulonglong),('pageTotal',ctypes.c_ulonglong),('pageAvailable',ctypes.c_ulonglong),('virtualTotal',ctypes.c_ulonglong),('virtualAvailable',ctypes.c_ulonglong),('extended',ctypes.c_ulonglong)]
    s=Status();s.length=ctypes.sizeof(s);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(s));return s.available

def process_memory(proc):
    class Counters(ctypes.Structure):
        _fields_=[('cb',ctypes.c_ulong),('faults',ctypes.c_ulong)]+[(name,ctypes.c_size_t) for name in
            ['peakWorkingSet','workingSet','peakPagedPool','pagedPool','peakNonPagedPool','nonPagedPool','pagefile','peakPagefile','privateBytes']]
    counters=Counters();counters.cb=ctypes.sizeof(counters)
    if not ctypes.windll.psapi.GetProcessMemoryInfo(ctypes.c_void_p(int(proc._handle)),ctypes.byref(counters),counters.cb):
        return {'samplingError':ctypes.get_last_error()}
    return {name:getattr(counters,name) for name in ['workingSet','peakWorkingSet','privateBytes']}

def main():
    global OUT, FIXTURE
    p=argparse.ArgumentParser();p.add_argument('--workspace',type=Path,default=OUT);p.add_argument('--client-sandbox',type=Path,default=FIXTURE);p.add_argument('--plan',type=Path);p.add_argument('--mode',choices=['ui','gallery','gallery-batch','ticket-restart'],default='ui');p.add_argument('--keep-open',action='store_true');p.add_argument('--jar',type=Path,required=True);p.add_argument('--run',required=True);p.add_argument('--language',default='en_US');p.add_argument('--width',type=int,default=1280);p.add_argument('--height',type=int,default=720);p.add_argument('--scale',type=int,default=2);p.add_argument('--heap-gib',type=int,default=5);p.add_argument('--reserve-gib',type=float,default=4);p.add_argument('--render-distance',type=int,default=3);p.add_argument('--deadline',type=int,default=900);p.add_argument('--hidden-desktop',action='store_true',help='Create a private Windows desktop for the owned child; never switch the user desktop');p.add_argument('--allow-low-memory',action='store_true',help='Use only after the user explicitly requests launch below the advisory memory reserve; heap limit still applies');a=p.parse_args()
    if a.heap_gib>5:raise ValueError('User limit: client Java heap must not exceed 5GiB')
    OUT=a.workspace.resolve();FIXTURE=a.client_sandbox.resolve()
    if OUT.parent!=ROOT/'build' or FIXTURE.parent!=ROOT/'build' or not FIXTURE.name.startswith('client-testlab-'):raise ValueError('Use direct owned build children only')
    if a.mode=='ticket-restart' and a.plan:raise ValueError('ticket-restart is independent of UI plans')
    if a.mode in ('gallery','gallery-batch') and not a.plan:raise ValueError('gallery requires an explicit frozen visual plan')
    if not re.fullmatch(r'[a-z]{2}_[A-Z]{2}',a.language):raise ValueError('Invalid language code')
    if not re.fullmatch(r'[a-zA-Z0-9_-]+',a.run):raise ValueError('Unsafe run name')
    assert FIXTURE.resolve()==FIXTURE and FIXTURE.parent==ROOT/'build'
    result=OUT/'client-runs'/a.run;result.mkdir(parents=True,exist_ok=False)
    spec=json.loads((OUT/'client-launch.json').read_text());compile_receipt=json.loads((OUT/'qa-compile-receipt.json').read_text())
    helper=FIXTURE/'mods/gtsr-mui2-qa-owned-only.jar';assert sha(helper)==compile_receipt['jarSHA256']
    if a.heap_gib<3 or a.reserve_gib<1 or not 2<=a.render_distance<=16:raise ValueError('Require heap3..5GiB, explicit reserve>=1GiB and render distance2..16')
    available=free_memory();required=(a.heap_gib+a.reserve_gib)*1024**3
    preflight=dict(availableBytes=available,requiredBytes=required,heapGiB=a.heap_gib,reserveGiB=a.reserve_gib,renderDistance=a.render_distance,leaseExists=LEASE.exists(),userAuthorizedLowMemory=a.allow_low_memory,state='PRECHECK')
    (result/'preflight.json').write_text(json.dumps(preflight,indent=2))
    if available<required and not a.allow_low_memory:raise RuntimeError(f'Insufficient memory: {available/1024**3:.2f} GiB available, {required/1024**3:.2f} required; no process started')
    fd=os.open(LEASE,os.O_CREAT|os.O_EXCL|os.O_WRONLY);lease_token=dict(pid=os.getpid(),fixture=str(FIXTURE),run=a.run,created=time.time());os.write(fd,json.dumps(lease_token).encode());os.close(fd)
    proc=None;desktop=None;receipt=dict(status='PREPARED',sourceJar=str(a.jar.resolve()),candidateSHA256=sha(a.jar),helperSHA256=sha(helper),inputType='Synthetic actual ModularScreen hit-test/key/drag dispatch, lwjgl3ify injectTextEvent, legacy GuiScreen.keyTyped and native PlayerController.windowClick; physical mouse/keyboard/IME NOT_RUN',mode='Real full client with integrated server',forcedStop=False,qaMode=a.mode)
    if a.mode=='ticket-restart':receipt['inputType']='Synthetic same-JVM Minecraft launchIntegratedServer/loadWorld(null) and production CriticalChunkLoading ensure/release; UI suite and physical input NOT_RUN'
    try:
        destination=FIXTURE/'mods/production-gtsr.jar';destination.chmod(0o666);shutil.copy2(a.jar,destination)
        (FIXTURE/'options.txt').write_text(f'fullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\nguiScale:{a.scale}\nlang:{a.language}\nrenderDistance:{a.render_distance}\n',encoding='utf-8')
        cmd=[x for x in spec['command'] if not x.startswith('-Xmx')];cmd.insert(1,f'-Xmx{a.heap_gib}G')
        cmd=[x for x in cmd if not x.startswith('-Dgtsr.uiqa.receipt=')]
        cmd[1:1]=['-Dgtsr.uiqa.mode='+a.mode,'-Dgtsr.uiqa.batch.keepOpen='+str(a.keep_open).lower(),'-Dgtsr.uiqa.launchEpochMillis='+str(int(time.time()*1000)),'-Dgtsr.uiqa.timeoutMillis='+str(max(60,a.deadline-30)*1000),'-Dgtsr.uiqa.language='+a.language,'-Dgtsr.uiqa.receipt='+str(result/'checks.json'),'-Dgtsr.uiqa.world=Mui2Audit-'+a.run]
        if a.plan:cmd.insert(1,'-Dgtsr.uiqa.plan='+str(a.plan.resolve(strict=True)))
        shutil.copy2(helper,result/'helper.jar')
        shutil.copy2(OUT/'qa-compile-receipt.json',result/'helper-compile-receipt.json')
        for source in Path(__file__).parent.glob('*.java'):shutil.copy2(source,result/source.name)
        if a.plan:
            shutil.copy2(a.plan,result/'plan.json')
            manifest=a.plan.with_suffix('.manifest.json')
            if manifest.exists():
                frozen=json.loads(manifest.read_text(encoding='utf-8'))
                if frozen['sha256']!=sha(a.plan):raise RuntimeError('Plan manifest SHA mismatch')
                shutil.copy2(manifest,result/'plan-manifest.json')
        cmd[cmd.index('--width')+1]=str(a.width);cmd[cmd.index('--height')+1]=str(a.height)
        receipt.update(command=cmd,workingDirectory=str(FIXTURE),javaVersion=subprocess.run([cmd[0],'-version'],capture_output=True,text=True,encoding='utf-8',errors='replace').stderr,language=a.language,resolution=[a.width,a.height],guiScale=a.scale,heapGiB=a.heap_gib,reserveGiB=a.reserve_gib,renderDistance=a.render_distance)
        (result/'launch.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
        started=time.monotonic()
        with (result/'stdout.log').open('wb') as log:
            startup=None
            if a.hidden_desktop:
                user32=ctypes.WinDLL('user32',use_last_error=True)
                user32.CreateDesktopW.argtypes=[ctypes.c_wchar_p,ctypes.c_void_p,ctypes.c_void_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p]
                user32.CreateDesktopW.restype=ctypes.c_void_p
                user32.CloseDesktop.argtypes=[ctypes.c_void_p]
                desktop_name='GTSR-owned-'+a.run+'-'+str(os.getpid())
                desktop=user32.CreateDesktopW(desktop_name,None,None,0,0x10000000,None)
                if not desktop:raise ctypes.WinError(ctypes.get_last_error())
                startup=subprocess.STARTUPINFO();startup.lpDesktop='WinSta0\\'+desktop_name
                receipt['hiddenDesktop']=dict(name=desktop_name,switched=False,scope='Owned separate desktop from window creation; real GL, never SwitchDesktop')
            proc=subprocess.Popen(cmd,cwd=FIXTURE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW,startupinfo=startup)
            (result/'pid.json').write_text(json.dumps(dict(pid=proc.pid,ownerRunner=os.getpid())))
            print('OWNED CLIENT',proc.pid,'candidate',receipt['candidateSHA256'],flush=True)
            try:
                with (result/'memory-samples.jsonl').open('w',encoding='utf-8') as memory:
                    while True:
                        sample=dict(elapsedSeconds=time.monotonic()-started,availableBytes=free_memory(),**process_memory(proc))
                        memory.write(json.dumps(sample)+'\n');memory.flush()
                        remaining=a.deadline-(time.monotonic()-started)
                        if remaining<=0:raise subprocess.TimeoutExpired(cmd,a.deadline)
                        try:code=proc.wait(min(1,remaining));break
                        except subprocess.TimeoutExpired:
                            if time.monotonic()-started>=a.deadline:raise
            except subprocess.TimeoutExpired:
                # Only this exact owned child may be stopped. User/launcher processes are never touched.
                receipt['forcedStop']=True;proc.terminate();code=proc.wait(30)
        checks=result/'checks.json';payload=json.loads(checks.read_text(encoding='utf-8')) if checks.exists() else None
        receipt.update(exitCode=code,elapsedSeconds=time.monotonic()-started,checksStatus=payload.get('status') if payload else 'NO_RECEIPT',candidateUnchanged=sha(destination)==receipt['candidateSHA256'])
        if True:
            # BetterCrashes can recover and leave exit0. Lifecycle checks alone cannot mean crash-free.
            markers=('Unreported exception thrown!', 'Reported exception thrown!', 'Minecraft ran into a problem!', 'Exception in server tick loop')
            receipt['runtimeCrashFindings']=[dict(line=i,text=line) for i,line in enumerate((result/'stdout.log').read_text(encoding='utf-8',errors='replace').splitlines(),1) if any(marker in line for marker in markers)]
        receipt['status']='PASS' if code==0 and payload and payload['status']=='PASS' and not receipt['forcedStop'] and receipt['candidateUnchanged'] else 'FAIL'
        if receipt.get('runtimeCrashFindings'):receipt['status']='FAIL'
        receipt['screenshots']=[dict(file=str(p.relative_to(result)),sha256=sha(p),bytes=p.stat().st_size) for p in result.rglob('*.png')]
        print(json.dumps({k:receipt[k] for k in ['status','exitCode','elapsedSeconds','checksStatus','forcedStop']},indent=2),flush=True)
    finally:
        (result/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
        if proc is None or proc.poll() is not None:
            if LEASE.exists() and json.loads(LEASE.read_text()).get('pid')==os.getpid():LEASE.unlink()
        else:print('Owned process remains alive; lease retained for explicit recovery',flush=True)
        if desktop and (proc is None or proc.poll() is not None):
            receipt['hiddenDesktop']['handleClosed']=bool(user32.CloseDesktop(desktop))
            (result/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')

if __name__=='__main__':main()
