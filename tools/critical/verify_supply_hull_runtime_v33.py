"""Candidate-JAR-only V33 hull/legacy retirement/native GT inventory contract."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess
ROOT=Path(__file__).resolve().parents[2]
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=ROOT/'build/v2043-release/geometry/runtime')
    args=parser.parse_args();jar=args.jar.resolve();out=args.out.resolve();out.relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=True)
    classes=out/'classes';gates=out/'gates';empty=out/'empty';runtime=out/'runtime'
    for p in (classes,gates,empty,runtime):p.mkdir(exist_ok=True)
    gt=next((CACHE/'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae=next((CACHE/'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    jars=[p for p in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix() and '/Applied-Energistics-2-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower()]
    cp=os.pathsep.join(map(str,[gates,classes,jar,gt,ae,ROOT/'build/classes/java/patchedMc',ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',ROOT/'build/rfg/mclauncher-1.7.10.jar',*jars]))
    sources=[ROOT/'tools/critical'/n for n in ('CriticalSupplyHull33Contract.java','CriticalSupplyTerraceContract.java','CriticalTerracePortsContract.java','CriticalV32Contract.java','CriticalV32Fixture.java')]
    gate_sources=[ROOT/'tools/tests/structurelib-gates'/n for n in ('FMLCommonHandler.java','StructureLibAPI.java')]
    before={str(p.relative_to(ROOT)):sha(p) for p in [*sources,*gate_sources,Path(__file__).resolve()]};candidate=sha(jar)
    java=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    def run(exe,label,argv):
        af=out/(label+'.args');af.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in argv),encoding='utf-8')
        result=subprocess.run([str(java/exe),'@'+str(af)],cwd=runtime,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=240,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        (out/(label+'.log')).write_bytes(result.stdout)
        if result.returncode:raise RuntimeError(f'{label} failed; inspect {out/(label+".log")}')
    run('javac.exe','gates',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',gates,*gate_sources])
    run('javac.exe','compile',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',classes,*sources])
    run('java.exe','contract',['-Xmx3G','-cp',cp,'com.miaokatze.gtsr.common.critical.CriticalSupplyHull33Contract',out/'contract.json'])
    report=json.loads((out/'contract.json').read_text(encoding='utf-8'))
    assert all(Path(v).resolve()==jar for v in report['codeSources'].values())
    assert candidate==sha(jar) and before=={str(p.relative_to(ROOT)):sha(p) for p in [*sources,*gate_sources,Path(__file__).resolve()]}
    report.update(candidate=str(jar),candidateSHA256=candidate,productOverlay=False,fixtureSHA256=before,GT205SHA256=sha(gt),AE1080SHA256=sha(ae))
    (out/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status=report['status'],checks=report['checks'],migrationCases=report['migrationCases'],nativeIOCases=report['nativeIOCases'],report=str(out/'verification.json'))))
if __name__=='__main__':main()
