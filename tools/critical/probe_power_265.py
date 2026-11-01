"""Small source-bound .265 energy-port/debit probe. No Gradle or game launch; one explicit product JAR."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--classpath', type=Path, default=ROOT / 'build/task03-compile-classpath.txt')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--green', action='store_true')
    parser.add_argument('--overlay', action='store_true', help='Compile only the three explicitly changed product sources; report as source overlay')
    args = parser.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=True);classes=out/'classes';classes.mkdir(exist_ok=True)
    if not args.overlay and any((classes/'com/miaokatze/gtsr').rglob('*.class')):
        raise ValueError('Product source overlay in candidate-only output directory; choose a fresh --out')
    product=args.jar.resolve();deps=[]
    deps += [ROOT/'build/classes/java/patchedMc', ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',
             ROOT/'build/rfg/mclauncher-1.7.10.jar']
    for item in args.classpath.read_text(encoding='utf-8-sig').strip().split(os.pathsep):
        p=Path(item)
        if not p.exists(): continue
        if p.is_file() and p.suffix=='.jar':
            with zipfile.ZipFile(p) as z:
                if 'META-INF/services/com.sun.source.util.Plugin' in z.namelist(): continue
                if 'com/miaokatze/gtsr/common/machine/MTECriticalMachine.class' in z.namelist(): continue
        if '/build/classes/java/main' in p.as_posix().lower(): continue
        deps.append(p)
    gt=[p for p in deps if p.name=='GT5-Unofficial-5.09.54.265-dev.jar']
    if len(gt)!=1: raise ValueError('Classpath must contain exactly one .265 GT dev artifact')
    cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
    deps += list((cache/'com.falsepattern/chunkapi-mc1.7.10/0.8.3').glob('*/*-dev.jar'))
    cp=os.pathsep.join(map(str,[classes,product,*deps]))
    sources=[ROOT/'tools/tests/Issue37RegistrationRegression.java',ROOT/'tools/critical/CriticalPower265Probe.java']
    if args.overlay:
        sources += [ROOT/'src/main/java/com/miaokatze/gtsr/common'/p for p in
                    ['critical/CriticalMachineState.java','critical/CriticalStructures.java','machine/MTECriticalMachine.java']]
    receipt={'status':'FAIL','product':str(product),'productSha256':sha(product),'GT':str(gt[0]),'gtSha256':sha(gt[0]),
             'sourceOverlay':args.overlay,'sources':{str(p.relative_to(ROOT)):sha(p) for p in sources},'commands':[]}
    commands=[('compile',['javac','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)]),
              ('run',['java','-Xmx1G','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',cp,'CriticalPower265Probe',str(out/'result.json'),str(args.green).lower()])]
    for name,command in commands:
        af=out/(name+'.args');af.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in command[1:]),encoding='utf-8')
        prefix=['-J-Duser.language=en','-J-Duser.country=US'] if name=='compile' else []
        run=subprocess.run([command[0],*prefix,'@'+str(af)],cwd=ROOT,capture_output=True,timeout=120)
        (out/(name+'.stdout.log')).write_bytes(run.stdout);(out/(name+'.stderr.log')).write_bytes(run.stderr)
        receipt['commands'].append({'name':name,'exitCode':run.returncode})
        if run.returncode: break
    else: receipt['status']='PASS_PROBE_EXECUTED'
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    print(receipt['status']+' '+str(out/'receipt.json'))
    return 0 if receipt['status']=='PASS_PROBE_EXECUTED' else 1


if __name__=='__main__': raise SystemExit(main())
