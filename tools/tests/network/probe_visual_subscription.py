"""Actual Forge packet entry/drain probe; isolated players, no TCP or game launch."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--mode', choices=['red', 'green'], required=True)
    parser.add_argument('--overlay', action='store_true')
    parser.add_argument('--core', type=Path)
    parser.add_argument('--jdk', type=Path, default=Path('C:/Program Files/Java/jdk-26.0.2.1/bin'))
    args = parser.parse_args()
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    classes=out/'classes';classes.mkdir();empty=out/'empty';empty.mkdir()
    deps=[ROOT/'build/classes/java/patchedMc',ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',args.jar.resolve()]
    for value in (ROOT/'build/task03-compile-classpath.txt').read_text(encoding='utf-8-sig').strip().split(os.pathsep):
        p=Path(value)
        if not p.is_file() or p.suffix!='.jar':continue
        with zipfile.ZipFile(p) as jar:
            if 'com/miaokatze/gtsr/main/GTSteamReborn.class' in jar.namelist() or 'META-INF/services/com.sun.source.util.Plugin' in jar.namelist():continue
            if args.core and 'com/miaokatze/miaokatzecore/api/recipe/RecipeRegistration.class' in jar.namelist():continue
        deps.append(p)
    if args.core:deps.append(args.core.resolve(strict=True))
    deps+=list((Path.home()/'.gradle/caches/modules-2/files-2.1/com.falsepattern/chunkapi-mc1.7.10/0.8.3').glob('*/*-dev.jar'))
    cp=os.pathsep.join(map(str,[classes,*dict.fromkeys(deps)]))
    sources=[ROOT/'tools/tests/Issue37RegistrationRegression.java',Path(__file__).with_name('VisualSubscriptionRegression.java')]
    if args.overlay:sources.append(ROOT/'src/main/java/com/miaokatze/gtsr/common/machinevisual/MachineVisualNetwork.java')
    commands=[('compile','javac',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',classes,*sources]),
              ('run','java',['-Xmx512m','-Dfile.encoding=UTF-8','-cp',cp,'VisualSubscriptionRegression',args.mode])]
    receipt={'status':'FAIL','mode':args.mode,'overlay':args.overlay,'product':str(args.jar.resolve()),
             'productSha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'commands':[],
             'scope':'Production packet handler and main-thread drain, actual Forge/Netty types, isolated native players/world. No TCP, game or full host subscription.'}
    receipt['sourceSha256']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    if args.core:receipt['coreSha256']=hashlib.sha256(args.core.read_bytes()).hexdigest()
    for name,tool,values in commands:
        af=out/(name+'.args');af.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values),encoding='utf-8')
        result=subprocess.run([str(args.jdk/(tool+'.exe')),'@'+str(af)],cwd=out,capture_output=True)
        (out/(name+'.stdout.log')).write_bytes(result.stdout);(out/(name+'.stderr.log')).write_bytes(result.stderr)
        receipt['commands'].append({'name':name,'exit':result.returncode})
        if result.returncode:break
    else:receipt['status']='PASS' if args.mode=='green' else 'REPRODUCED'
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
    print(receipt['status'],out)
    return 0 if receipt['status']!='FAIL' else 1

if __name__=='__main__':raise SystemExit(main())
