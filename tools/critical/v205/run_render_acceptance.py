"""Owned sequential client render audit. No user saves, product classes or Gradle writes."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, time, zipfile

ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / 'build/critical-v205/render-acceptance'
OLD = ROOT / 'build/nei-preview-v12145'
PACK = Path('E:/Prism Launcher/instances/GTNH-2.9.0-RC-2-SH/.minecraft')
JAVAC = Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def prepare():
    fixture=BASE/'fixture'; fixture.mkdir(parents=True,exist_ok=False)
    for name in ('libraries','assets','natives'):
        shutil.copytree(OLD/'client-fixture'/name,fixture/name,copy_function=os.link)
    for name in ('config','scripts','resourcepacks','fonts'):
        if (PACK/name).exists(): shutil.copytree(PACK/name,fixture/name)
    (fixture/'mods').mkdir()
    disabled=('gtsr-','production-gtsr','critical-','actual-nei','gtfi-','gtit-','gtmb-','gtswn-','gtudk-','outpost-')
    for p in (PACK/'mods').rglob('*'):
        if not p.is_file() or p.name.lower().startswith(disabled): continue
        dest=fixture/'mods'/p.relative_to(PACK/'mods');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
    (fixture/'options.txt').write_text('fullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\nrenderDistance:12\nguiScale:2\nparticles:0\nfboEnable:true\n',encoding='utf-8')
    spec=json.loads((OLD/'client-launch.json').read_text(encoding='utf-8'))
    cmd=[s.replace(str(OLD/'client-fixture'),str(fixture)) for s in spec['command']]
    cmd=[s for s in cmd if not s.startswith(('-Dcritical.','-Dnei.audit.'))]
    (BASE/'command.json').write_text(json.dumps(cmd,indent=2))
    return fixture
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--phase',required=True);ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--core',type=Path);ap.add_argument('--mode',choices=('all','world'),default='all');ap.add_argument('--prepare-only',action='store_true');a=ap.parse_args()
    BASE.mkdir(parents=True,exist_ok=True);fixture=BASE/'fixture'
    if not fixture.exists():prepare()
    # Keep the user's actual texture packs, mipmaps, gamma and graphics configuration.
    options={}
    for line in (PACK/'options.txt').read_text(encoding='utf-8').splitlines():
        if ':' in line:
            key,value=line.split(':',1);options[key]=value
    options.update(fullscreen='false',pauseOnLostFocus='false',soundCategory_master='0.0',renderDistance='12',guiScale='2',fboEnable='true')
    (fixture/'options.txt').write_text(''.join(k+':'+v+'\n' for k,v in options.items()),encoding='utf-8')
    if (PACK/'optionsnf.txt').exists():shutil.copy2(PACK/'optionsnf.txt',fixture/'optionsnf.txt')
    out=BASE/a.phase;out.mkdir(exist_ok=False)
    (fixture/('saves/RenderAcceptance-'+a.phase)/'betterquesting').mkdir(parents=True,exist_ok=True)
    for p in (fixture/'mods').rglob('*miaokatzecore*.jar'):p.unlink()
    core=a.core or PACK/'mods/miaokatzecore-v0.1.10.jar'
    shutil.copy2(core,fixture/'mods/miaokatzecore-audit.jar');shutil.copy2(a.jar,fixture/'mods/production-gtsr.jar')
    classes=out/'classes';classes.mkdir()
    cp=';'.join(map(str,[ROOT/'build/classes/java/patchedMc',*sorted((fixture/'libraries').rglob('*.jar')),*sorted((fixture/'mods').rglob('*.jar'))]))
    source=Path(__file__).with_name('RenderAcceptanceProbe.java')
    args=['-encoding','UTF-8','-proc:none','--release','8','-cp',cp,'-d',str(classes),str(source)]
    argfile=out/'compile.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args))
    c=subprocess.run([str(JAVAC),'@'+str(argfile)],capture_output=True);(out/'compile.log').write_bytes(c.stdout+c.stderr)
    if c.returncode:raise RuntimeError(c.stderr.decode('utf-8','replace'))
    helper=fixture/'mods/render-acceptance-audit.jar'
    with zipfile.ZipFile(helper,'w') as z:
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    cmd=json.loads((BASE/'command.json').read_text(encoding='utf-8'));cmd[1:1]=['-Drender.audit.out='+str(out),'-Drender.audit.phase='+a.phase,'-Drender.audit.mode='+a.mode]
    receipt=dict(phase=a.phase,productionJar=str(a.jar.resolve()),productionSHA256=sha(a.jar),coreJar=str(core.resolve()),coreSHA256=sha(core),helperSHA256=sha(helper),sourceSHA256=sha(source),heapGiB=4,command=cmd,cwd=str(fixture),userSavesCopied=False,sourceOptionsSHA256=sha(PACK/'options.txt'),fixtureOptionsSHA256=sha(fixture/'options.txt'),resourcePacks=options.get('resourcePacks'),mipmapLevels=options.get('mipmapLevels'),gamma=options.get('gamma'))
    shutil.copy2(a.jar,out/'production.jar');shutil.copy2(core,out/'core.jar');shutil.copy2(source,out/source.name);shutil.copy2(helper,out/'helper.jar')
    (out/'launch.json').write_text(json.dumps(receipt,indent=2))
    if a.prepare_only:print('PREPARED',out);return
    lease=ROOT.parent/'.gtsr-native-server.lease';fd=os.open(lease,os.O_CREAT|os.O_EXCL|os.O_WRONLY);os.write(fd,json.dumps(dict(pid=os.getpid(),fixture=str(fixture),phase=a.phase)).encode());os.close(fd)
    proc=None
    try:
        start=time.monotonic()
        with (out/'stdout.log').open('wb') as log:
            proc=subprocess.Popen(cmd,cwd=fixture,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            (out/'pid.json').write_text(json.dumps(dict(pid=proc.pid,runner=os.getpid())));print('OWNED CLIENT',proc.pid,'Xmx4G',flush=True)
            try:code=proc.wait(timeout=1500)
            except subprocess.TimeoutExpired:proc.terminate();code=proc.wait(timeout=30);receipt['forcedStop']=True
        receipt.update(exitCode=code,seconds=time.monotonic()-start,productionUnchanged=sha(fixture/'mods/production-gtsr.jar')==receipt['productionSHA256'])
        (out/'process-exit.json').write_text(json.dumps(dict(exitCode=code,seconds=receipt['seconds'],forcedStop=receipt.get('forcedStop',False))),encoding='utf-8')
        payload=json.loads((out/'checks.json').read_text(encoding='utf-8')) if (out/'checks.json').exists() else {}
        receipt['status']=payload.get('status','NO_RECEIPT');(out/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps({k:receipt[k] for k in ('status','exitCode','seconds','productionUnchanged')}),flush=True)
        if code or receipt.get('forcedStop') or receipt['status']!='PASS':raise RuntimeError('Render audit failed: '+receipt['status'])
    finally:
        if proc is None or proc.poll() is not None:lease.unlink()
if __name__=='__main__':main()
