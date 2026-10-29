"""Run actual candidate JAR State/NBT/free-decoration ownership against a bounded sparse World."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home()/'.gradle/caches/modules-2/files-2.1'


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=ROOT/'build/v2042-release/geometry/runtime')
    parser.add_argument('--java-home',type=Path,default=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2')
    args = parser.parse_args()
    jar,out=args.jar.resolve(),args.out.resolve()
    out.relative_to(ROOT/'build')
    classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
    runtime=out/'runtime';runtime.mkdir(exist_ok=True)
    empty=out/'empty';empty.mkdir(exist_ok=True)
    gt=next((CACHE/'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae=next((CACHE/'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies=sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
                        if '/GT5-Unofficial/' not in p.as_posix() and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
                        and 'gtsr' not in p.name.lower())
    gates=out/'gates';gates.mkdir(exist_ok=True)
    gate_sources=[ROOT/'tools/tests/structurelib-gates'/name for name in ('FMLCommonHandler.java','StructureLibAPI.java')]
    cp=os.pathsep.join(map(str,[gates,classes,jar,gt,ae,ROOT/'build/classes/java/patchedMc',
        ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',ROOT/'build/rfg/mclauncher-1.7.10.jar',*dependencies]))
    sources=[ROOT/'tools/critical'/name for name in ('CriticalSupplyTerraceContract.java','CriticalV32Fixture.java',
             'CriticalV32Contract.java','CriticalTerracePortsContract.java')]
    before={str(p.relative_to(ROOT)):sha(p) for p in [*sources,*gate_sources,Path(__file__).resolve()]}
    candidate_hash=sha(jar)
    baseline=out/'released-v31-base.json'
    baseline.write_bytes(subprocess.check_output(['git','show','v2.0.41:src/main/resources/assets/gtsr/critical/models/v2_base_t3.geometry.json'],cwd=ROOT))

    def run(exe,label,argv):
        argfile=out/(label+'.args')
        argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in argv),encoding='utf-8')
        r=subprocess.run([str(args.java_home/'bin'/exe),'@'+str(argfile)],cwd=runtime,
                         stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180,
                         creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        (out/(label+'.log')).write_bytes(r.stdout)
        if r.returncode:
            raise RuntimeError(f'{label} failed; inspect {out/(label+".log")}')

    run('javac.exe','gates',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',gates,*gate_sources])
    run('javac.exe','compile',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',classes,*sources])
    run('java.exe','contract',['-Xmx3G','-cp',cp,'com.miaokatze.gtsr.common.critical.CriticalSupplyTerraceContract',out/'contract.json',baseline])
    report=json.loads((out/'contract.json').read_text(encoding='utf-8'))
    assert all(Path(p).resolve()==jar for p in report['codeSources'].values()),'product must load from candidate JAR'
    assert sha(jar)==candidate_hash
    assert before=={str(p.relative_to(ROOT)):sha(p) for p in [*sources,*gate_sources,Path(__file__).resolve()]}
    report.update(jar=str(jar),jarSHA256=candidate_hash,productOverlay=False,fixtureSHA256=before)
    (out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=report['status'],checks=report['checks'],report=str(out/'verification.json'))))


if __name__=='__main__':
    main()
