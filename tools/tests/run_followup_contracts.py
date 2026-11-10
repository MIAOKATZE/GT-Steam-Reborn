"""Native focused follow-up contracts; no Gradle, game launch or external installation."""
import argparse, hashlib, json, os, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--classpath-file',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=True)
    classes=out/'classes';classes.mkdir(exist_ok=True)
    cp=a.classpath_file.read_text(encoding='utf-8-sig').strip().split(os.pathsep)
    cp += list(map(str,(Path.home()/'.gradle/caches/modules-2/files-2.1/com.falsepattern/chunkapi-mc1.7.10/0.8.3').glob('*/*-dev.jar')))
    cp += list(map(str,(Path.home()/'.gradle/caches/modules-2/files-2.1/com.github.GTNewHorizons/Baubles-Expanded').glob('*/*/*-dev.jar')))
    cp=os.pathsep.join([str(classes),str(ROOT/'build/classes/java/main'),str(ROOT/'build/classes/java/patchedMc'),str(ROOT/'build/resources/patchedMc'),str(ROOT/'src/main/resources'),*cp])
    paths=subprocess.check_output(['git','diff','--name-only','--diff-filter=M'],cwd=ROOT,text=True).splitlines()+subprocess.check_output(['git','ls-files','--others','--exclude-standard','src/main/java'],cwd=ROOT,text=True).splitlines()
    sources=[ROOT/x for x in paths if x.endswith('.java') and x.startswith('src/')]
    sources += [ROOT/'tools/tests'/f for f in ['Issue37RegistrationRegression.java','GtsrFollowupContracts.java','AmmoniaNativeRegression.java','CriticalResourceNbtRegression.java','GtsrVisualScopeContract.java','ClientDisplayListContract.java']]
    receipt={'boundary':'Native MC NBT/GT .265 and real production classes with isolated registry bootstrap and controlled external IO. No Forge full lifecycle, game, AE topology, GL or disk-crash atomicity claim.','sources':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'commands':[]}
    commands=[('compile',['javac','-J-Duser.language=en','-proc:none','-encoding','UTF-8','-sourcepath',str(out/'empty'),'-cp',cp,'-d',str(classes),*map(str,sources)])]+[(n,['java','-Xmx512m','-Djava.awt.headless=true','-cp',cp,n,*(['5.09.54.265'] if n=='AmmoniaNativeRegression' else [])]) for n in ['GtsrFollowupContracts','AmmoniaNativeRegression','com.miaokatze.gtsr.common.critical.CriticalResourceNbtRegression','GtsrVisualScopeContract','com.miaokatze.gtsr.client.render.ClientDisplayListContract']]
    for name,command in commands:
        result=subprocess.run(command,capture_output=True,timeout=60,cwd=out)
        log=(result.stdout+result.stderr).decode('utf-8',errors='replace');(out/(name+'.log')).write_text(log,encoding='utf-8')
        receipt['commands'].append({'name':name,'exitCode':result.returncode,'tail':log[-4000:]})
        (out/'receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
        print(name,result.returncode,log[-4500:] if result.returncode else '\n'.join(line for line in log.splitlines() if line.startswith(('PASS','RESULT','SOURCE'))))
        if result.returncode:return result.returncode
    return 0
if __name__=='__main__':raise SystemExit(main())
