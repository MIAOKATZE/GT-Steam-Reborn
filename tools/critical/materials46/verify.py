"""JAR-only real Forge/creative/NEI material acceptance and old/new IIcon reload comparison."""
import argparse
import importlib.util
import json
import os
import zipfile
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('v33', ROOT / 'tools/critical/verify_v33.py')
v33 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v33)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--baseline', required=True, type=Path)
    parser.add_argument('--core', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.resolve(); out.relative_to(ROOT / 'build'); out.mkdir(parents=True, exist_ok=True)
    artifacts = [args.jar.resolve(), args.baseline.resolve(), args.core.resolve()]
    artifact_hashes = {str(p): v33.sha(p) for p in artifacts}
    sources = [ROOT/'tools/critical/v33/ProbeSupport.java', ROOT/'tools/critical/v33/ProbeLauncher.java',
               Path(__file__).with_name('LegacyMaterialReference.java')]
    tracked = sources + [Path(__file__).with_name('MaterialsProbe.java'), Path(__file__), ROOT/'tools/critical/verify_v33.py'] + v33.GATES
    source_hashes = {str(p.relative_to(ROOT)): v33.sha(p) for p in tracked}
    gt = next((v33.CACHE/'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    nei = next((v33.CACHE/'com.github.GTNewHorizons/NotEnoughItems/2.8.145-GTNH').glob('*/*-dev.jar'))
    jars = sorted(p for p in v33.CACHE.glob('*/*/*/*/*.jar')
                  if '/GT5-Unofficial/' not in p.as_posix() and '/NotEnoughItems/' not in p.as_posix()
                  and 'gtsr' not in p.name.lower() and 'miaokatzecore' not in p.name.lower())
    jdk = Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    classpaths = {}
    for stage, product in [('baseline', args.baseline), ('candidate', args.jar)]:
        run = out/stage; run.mkdir(exist_ok=True)
        dirs = {n:run/n for n in ('classes','gates','empty','runtime')}
        for d in dirs.values(): d.mkdir(exist_ok=True)
        (dirs['classes']/'mcpmod.info').write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]',encoding='utf-8')
        cp = os.pathsep.join(map(str,[dirs['classes'],dirs['gates'],product.resolve(),args.core.resolve(),gt,nei,
                                     ROOT/'build/classes/java/patchedMc',ROOT/'build/rfg/recompiled_minecraft-1.7.10.jar',
                                     ROOT/'build/rfg/mclauncher-1.7.10.jar',*jars]))
        classpaths[stage] = cp.split(os.pathsep)
        def compile(name,target,files):
            v33.execute(jdk/'javac.exe',name,['-encoding','UTF-8','-proc:none','-sourcepath',dirs['empty'],'-cp',cp,'-d',target,*files],run,dirs['runtime'])
        compile('gates',dirs['gates'],v33.GATES)
        compile('probe',dirs['classes'],sources + ([Path(__file__).with_name('MaterialsProbe.java')] if stage=='candidate' else []))
        report = out/(stage+'.json')
        main = 'LegacyMaterialReference' if stage=='baseline' else 'MaterialsProbe'
        v33.execute(jdk/'java.exe','probe-run',['-Djava.awt.headless=true','-Dprobe.gt=5.09.54.205',
                    '-Dprobe.main=com.miaokatze.gtsr.common.critical.'+main,'-cp',cp,'ProbeLauncher',report,*([out/'baseline.json'] if stage=='candidate' else [])],run,dirs['runtime'])
    baseline = json.loads((out/'baseline.json').read_text(encoding='utf-8'))
    result = json.loads((out/'candidate.json').read_text(encoding='utf-8'))
    def location(value):
        path = unquote(urlsplit(value.removeprefix('jar:').split('!',1)[0]).path)
        if os.name=='nt': path=path.lstrip('/')
        return Path(path).resolve()
    for name in ['product','definitions','catalog','textures','clientProxy']:
        if location(result[name]) != args.jar.resolve():
            raise RuntimeError(f'{name} was not loaded from candidate: {result[name]}')
    if location(result['Core']) != args.core.resolve():
        raise RuntimeError(f"Core was not loaded from explicit candidate: {result['Core']}")
    textures = {}
    with zipfile.ZipFile(args.baseline) as old, zipfile.ZipFile(args.jar) as new:
        names = sorted(n for n in old.namelist() if n.startswith('assets/gtsr/textures/blocks/critical/') and n.endswith(('.png','.mcmeta')))
        changed = [n for n in names if n not in new.namelist() or old.read(n)!=new.read(n)]
        added = sorted(n for n in new.namelist() if n.startswith('assets/gtsr/textures/blocks/critical/') and n.endswith(('.png','.mcmeta')) and n not in names)
        textures = {'compared':len(names),'changed':changed,'added':added}
        if changed or added: raise RuntimeError('Material pixel/animation assets changed')
    if source_hashes != {str(p.relative_to(ROOT)):v33.sha(p) for p in tracked} or artifact_hashes != {str(p):v33.sha(p) for p in artifacts}:
        raise RuntimeError('Fixture or artifact changed during verification')
    receipt = {'status':result['status'],'productOverlay':False,'jar':str(args.jar.resolve()),'jarSHA256':v33.sha(args.jar),
               'artifactSHA256':artifact_hashes,'sources':source_hashes,'classpath':classpaths,'baseline':{k:baseline[k] for k in ['product','textures']},
               'materialAssetComparison':textures,'result':result}
    (out/'verification.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:result[k] for k in ['status','registered','aliases','creative','neiEnumerated','iconChecks','checks']}))
    print('materialAssetComparison',textures)
    if result['status']!='PASS':raise RuntimeError('Material acceptance failed')


if __name__=='__main__':main()
