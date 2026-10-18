"""Finite real Forge registration/icon probe. Optional source overlay is explicitly recorded."""
import argparse
import importlib.util
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('v33', ROOT / 'tools/critical/verify_v33.py')
v33 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v33)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--source-overlay', action='store_true')
    parser.add_argument('--expect-reproduced', action='store_true', help='Only for a preserved pre-fix repro; must report REPRODUCED')
    args = parser.parse_args()
    out = args.out.resolve(); out.relative_to(ROOT / 'build'); out.mkdir(parents=True, exist_ok=True)
    dirs = {n: out/n for n in ('classes', 'gates', 'overlay', 'empty', 'runtime')}
    for d in dirs.values(): d.mkdir(exist_ok=True)
    (dirs['classes']/'mcpmod.info').write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]')
    gt = next((v33.CACHE/'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    nei=next((v33.CACHE/'com.github.GTNewHorizons/NotEnoughItems/2.8.145-GTNH').glob('*/*-dev.jar'))
    jars = sorted(p for p in v33.CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix() and '/NotEnoughItems/' not in p.as_posix() and 'gtsr' not in p.name.lower())
    cp = os.pathsep.join(map(str,[dirs['classes'], dirs['gates'], *([dirs['overlay'],ROOT/'src/main/resources'] if args.source_overlay else []),args.jar.resolve(),gt,nei,ROOT/'build/classes/java/patchedMc',ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',ROOT/'build/rfg/mclauncher-1.7.10.jar',*jars]))
    jdk=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    def compile(name,target,sources):
        v33.execute(jdk/'javac.exe',name,['-encoding','UTF-8','-proc:none','-sourcepath',dirs['empty'],'-cp',cp,'-d',target,*sources],out,dirs['runtime'])
    sources=[ROOT/'tools/critical/v33/ProbeSupport.java',ROOT/'tools/critical/v33/ProbeLauncher.java',Path(__file__).with_name('MaterialsProbe.java')]
    prod=[]
    if args.source_overlay:
        prod=[ROOT/'src/main/java/com/miaokatze/gtsr'/p for p in ['common/critical/CriticalMaterials.java','client/critical/CriticalMaterialTextures.java','client/critical/CriticalMaterialRenderer.java']]
    tracked=sources+prod+v33.GATES+[Path(__file__),ROOT/'tools/critical/verify_v33.py']
    before={str(p.relative_to(ROOT)):v33.sha(p) for p in tracked}
    jar_hash=v33.sha(args.jar)
    compile('gates',dirs['gates'],v33.GATES)
    if args.source_overlay:
        compile('overlay',dirs['overlay'],prod)
    compile('probe',dirs['classes'],sources)
    report=out/'materials.json'
    v33.execute(jdk/'java.exe','probe-run',['-Djava.awt.headless=true','-Dprobe.gt=5.09.54.205','-Dprobe.main=com.miaokatze.gtsr.common.critical.MaterialsProbe','-cp',cp,'ProbeLauncher',report],out,dirs['runtime'])
    result=json.loads(report.read_text(encoding='utf-8'))
    after={str(p.relative_to(ROOT)):v33.sha(p) for p in tracked}
    if before!=after or jar_hash!=v33.sha(args.jar): raise RuntimeError('Fixture, overlay source or product jar changed during verification')
    receipt={'jar':str(args.jar.resolve()),'jarSHA256':jar_hash,'productOverlay':args.source_overlay,'sources':before,'result':result}
    (out/'verification.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:result[k] for k in ['status','registered','enumerated','neiEnumerated','creative','iconChecks']}))
    print('iconMismatches',len(result['iconMismatches']),'transientRegistrationNulls',len(result['transientRegistrationNulls']))
    expected = 'REPRODUCED' if args.expect_reproduced else 'PASS'
    if result['status'] != expected: raise RuntimeError(f"Expected {expected}, got {result['status']}")
    if result['iconMismatches']: raise RuntimeError('Production icon lookup mismatches its resource contract')


if __name__=='__main__':main()
