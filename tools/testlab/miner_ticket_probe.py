"""Run actual node / GT205 / Forge calls in a sparse World; never starts Minecraft."""
from pathlib import Path
import argparse, subprocess, hashlib, json, re
ROOT=Path(__file__).resolve().parents[2]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
JAVA=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--label',required=True);p.add_argument('--compile-source',action='store_true');a=p.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+',a.label):raise ValueError('Unsafe evidence label')
    run=ROOT/'build/ui-miner-v12141'/a.label;run.mkdir(parents=True,exist_ok=False);classes=run/'classes';classes.mkdir()
    gt=next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    cp=';'.join(map(str,[classes,a.jar.resolve(),ROOT/'build/classes/java/patchedMc',gt]+list(CACHE.glob('*/*/*/*/*.jar'))))
    source=Path(__file__).with_name('MinerTicketProbe.java')
    sources=[source]
    if a.compile_source:sources += [ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/base/RemoteWorkerChunkTickets.java',ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/base/MTERemoteWorkerNode.java',ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/MTESingularityDrillingHub.java']
    receipt={'jar':str(a.jar.resolve()),'sha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'probeSHA256':hashlib.sha256(source.read_bytes()).hexdigest()}
    receipt['productionSourceOverlays']={str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in sources[1:]}
    for stem,opts in [('compile',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes]+sources),('run',['-Djava.awt.headless=true','-cp',cp,'audit.MinerTicketProbe',run/'result.json'])]:
        argfile=run/(stem+'.args');argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in opts),encoding='utf-8')
        r=subprocess.run([str(JAVA/('javac.exe' if stem=='compile' else 'java.exe')),'@'+str(argfile)],capture_output=True,text=True,encoding='utf-8',errors='replace')
        (run/(stem+'.log')).write_text(r.stdout+r.stderr,encoding='utf-8');receipt[stem+'Exit']=r.returncode
        print(stem,r.returncode,r.stdout[-2000:],r.stderr[-2000:])
        if r.returncode:break
    (run/'binding.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    receipt['postrunJarSHA256']=hashlib.sha256(a.jar.read_bytes()).hexdigest()
    assert receipt['sha256']==receipt['postrunJarSHA256']
    (run/'binding.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    if receipt.get('compileExit')!=0 or receipt.get('runExit')!=0:raise SystemExit(1)
if __name__=='__main__':main()
