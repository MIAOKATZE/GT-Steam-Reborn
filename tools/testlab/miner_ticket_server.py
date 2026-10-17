"""Owned GT205 dedicated-server miner ticket check; no user instance or other process touched."""
from pathlib import Path
import argparse, os, json, subprocess, zipfile, re, hashlib, shutil
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--label',required=True);p.add_argument('--helper',type=Path);p.add_argument('--mode',choices=['core','mixed-stable'],default='core');a=p.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+',a.label):raise ValueError('Unsafe evidence label')
    run=ROOT/'build/ui-miner-v12141'/a.label;run.mkdir(exist_ok=False);classes=run/'classes';classes.mkdir()
    sandbox=ROOT/'build/server-testlab-mui2-v12138';lease=ROOT.parent/'.gtsr-native-server.lease'
    helper=sandbox/'mods/gtsr-miner-ticket-qa-owned-only.jar';config=sandbox/'config/gtsr/gtsr.cfg';before=config.read_bytes()
    if helper.exists():raise RuntimeError('Helper already exists')
    mode_file=sandbox/'miner-ticket-mode.txt'
    if mode_file.exists():raise RuntimeError('QA mode file already exists')
    fd=os.open(lease,os.O_EXCL|os.O_CREAT|os.O_WRONLY);os.write(fd,json.dumps({'pid':os.getpid(),'task':'miner-ticket','sandbox':str(sandbox)}).encode());os.close(fd)
    qa_status='NOT_RUN';receipt={}
    try:
        mode_file.write_text(a.mode,encoding='utf-8')
        if a.helper:
            shutil.copy2(a.helper.resolve(strict=True),helper)
        else:
            cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
            cp=';'.join(map(str,[ROOT/'build/classes/java/patchedMc',sandbox/'lwjgl3ify-forgePatches.jar']+list((sandbox/'libraries').rglob('*.jar'))+list((sandbox/'mods').rglob('*.jar'))+list(cache.glob('*/*/*/*/*.jar'))))
            opts=['-encoding','UTF-8','-proc:none','--release','8','-cp',cp,'-d',str(classes),str(Path(__file__).with_name('MinerTicketServerQa.java'))]
            argfile=run/'compile.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in opts),encoding='utf-8')
            r=subprocess.run([str(JAVA/'javac.exe'),'@'+str(argfile)],capture_output=True);(run/'compile.log').write_bytes(r.stdout+r.stderr)
            if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode('utf-8','replace'))
            with zipfile.ZipFile(helper,'w') as z:
                for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
        shutil.copy2(helper,run/'helper.jar')
        text=before.decode('utf-8-sig');text=re.sub(r'(testlab\s*\{.*?B:enabled\s*=\s*)false',r'\g<1>true',text,flags=re.S);config.write_text(text,encoding='utf-8')
        scenario=run/'scenario.json';scenario.write_text(json.dumps([{'command':'gtsrtest status','expect':'GTSR-MINER-QA.*COMPLETE','timeout':180}]),encoding='utf-8')
        result=sandbox/'miner-ticket-server-result.json';result.unlink(missing_ok=True)
        cmd=['python','-X','utf8',str(ROOT/'tools/testlab/server_verify.py'),'run','--sandbox',str(sandbox),'--jar',str(a.jar.resolve()),'--scenario',str(scenario),'--java',str(JAVA/'java.exe'),'--xmx-gib','4','--wait-memory-seconds','0','--deadline','700']
        with (run/'runner.log').open('wb') as log:r=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT)
        if result.exists():
            shutil.copy2(result,run/'result.json');qa_report=json.loads(result.read_text(encoding='utf-8'));qa_status=qa_report['status']
        receipt={'runnerExit':r.returncode,'qaStatus':qa_status,'jarSHA256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'helperSHA256':hashlib.sha256(helper.read_bytes()).hexdigest()}
        if result.exists() and 'mixedScope' in qa_report:
            receipt['mixedChecksPass']=qa_report.get('mixedBothTicketsHeldBeforeStop') is True and qa_report.get('mixedHeldTicks',0)>=120 and qa_report.get('mixedUnloadClearsNativeAndLocal') is True and qa_report.get('mixedServerStoppedAllMapsClear') is True
            if a.mode=='mixed-stable':receipt['mixedChecksPass']&=qa_report.get('mixedCriticalNativeIdentityStable120Ticks') is True and qa_report.get('mixedWorkerNativeIdentityStable120Ticks') is True and qa_report.get('mixedWorldIdentityLive120Ticks') is True
        for line in (run/'runner.log').read_text(encoding='utf-8',errors='replace').splitlines():
            if not line.startswith('{'):continue
            try:entry=json.loads(line)
            except json.JSONDecodeError:continue
            if 'receipt' in entry:receipt['serverReceipt']=entry['receipt']
        if receipt.get('serverReceipt'):
            native_receipt=Path(receipt['serverReceipt']);console=native_receipt.with_name('console.log')
            lines=console.read_text(encoding='utf-8',errors='replace').splitlines()
            markers=[line for line in lines if re.search(r'java\.lang\.(?:NullPointerException|StackOverflowError)|Exception ticking world|Exception stopping the server|Encountered an unexpected exception',line)]
            receipt['nativeCrashGate']={'pass':not markers,'markers':markers,'consoleSHA256':hashlib.sha256(console.read_bytes()).hexdigest()}
        print((run/'runner.log').read_text(encoding='utf-8',errors='replace')[-3000:])
    finally:
        config.write_bytes(before);helper.unlink(missing_ok=True);mode_file.unlink(missing_ok=True);lease.unlink()
        receipt['configRestored']=config.read_bytes()==before
        (run/'binding.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    if receipt.get('runnerExit')!=0 or qa_status!='PASS' or receipt.get('mixedChecksPass') is False or not receipt.get('nativeCrashGate',{}).get('pass'):raise SystemExit(1)
if __name__=='__main__':main()
