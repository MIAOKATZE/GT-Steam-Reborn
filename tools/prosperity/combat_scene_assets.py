"""Publish the two verified combat snapshots without rewriting unrelated assets."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]

def main():
    folder = ROOT / (sys.argv[1] if len(sys.argv)>1 else 'temp/combat-v71/scene')
    verification = json.loads((folder/'geometry-verification.json').read_text(encoding='utf8'))
    assert verification['passed']
    production=ROOT/'src/main/resources/assets/gtsr/remaster'
    catalog=json.loads((production/'catalog.json').read_text(encoding='utf8'))
    index=ROOT/'plan/prosperity/structures/remaster-index.js'
    index_text=index.read_text(encoding='utf8')
    match=re.search(r'b\.structures=(\[.*\]);\}\)\(window\);',index_text,re.S)
    entries=json.loads(match.group(1))
    formal=next(p for p in (ROOT/'plan').iterdir() if (p/'preview/prefabs7').is_dir())
    for report in verification['reports']:
        identity=report['id']
        record=next(r for r in catalog['prefabs'] if r['id']==identity and r['variant']==0)
        target=production/record['file']
        old=json.loads(gzip.decompress(target.read_bytes()))
        prepared=folder/'verification-production'/record['file']
        new=json.loads(gzip.decompress(prepared.read_bytes()))
        assert new['sourceSha256']==hashlib.sha256((folder/(identity+'-v0.js')).read_bytes()).hexdigest(), 'Stale verified snapshot'
        assert new['solidCount']==report['solidCount'] and new['airCount']==report['airCount']
        old_files={s['file'] for s in old['slices']}
        new_files={s['file'] for s in new['slices']}
        for relative in new_files | {record['file']}:
            destination=production/relative
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(folder/'verification-production'/relative,destination)
        # Remove only this descriptor's retired chunk slices; never scan other models.
        for relative in old_files-new_files:
            destination=(production/relative).resolve()
            assert destination.is_relative_to(production.resolve())
            destination.unlink()
        record.update({k:new[k] for k in ('min','max','extent','nominal','yMin','yMax','solidCount','airCount','sourceSha256')})
        record['placement']={'terrain':new['metadata']['terrain'],'rooms':new['metadata']['rooms']}
        record['sha256']=hashlib.sha256(gzip.decompress(prepared.read_bytes())).hexdigest()
        shutil.copyfile(folder/(identity+'-v0.js'),formal/'preview/prefabs7'/(identity+'-v0.js'))
        solids=[];airs=[]
        for sliced in new['slices']:
            for run in json.loads(gzip.decompress((production/sliced['file']).read_bytes())):
                (airs if new['palette'][run[4]]=='minecraft:air#0' else solids).append(run)
        scene_id=identity+'-r7-v0'
        data={'palette':new['palette'],'runs':solids}
        (ROOT/'plan/prosperity/structures/remaster'/(scene_id+'.js')).write_text(
            'window.PROSPERITY_STRUCTURES_DATA=window.PROSPERITY_STRUCTURES_DATA||{};window.PROSPERITY_STRUCTURES_DATA['+json.dumps(scene_id)+']='+json.dumps(data,separators=(',',':'),ensure_ascii=False)+';\n',encoding='utf8')
        entry=next(e for e in entries if e['id']==scene_id)
        metadata=new['metadata']
        for field in ('nodes','spawns','spawnerPlan','lootPlan7','roofKeys','rooms','triggers','story','playerReviewViews','productionNodeBindings','productionSiteController'):
            if field in metadata: entry[field]=metadata[field]
        entry.update(count=new['solidCount'],size=new['extent'],origin=new['min'],airRuns=airs,
            implementationRevision='v71-combat-landscape',interactionApproaches=json.loads((folder/('interaction-approaches-'+identity+'.json')).read_text(encoding='utf8')))
        entry['source']={'file':metadata['sourceDraft'],'sha256':hashlib.sha256((ROOT/metadata['sourceDraft']).read_bytes()).hexdigest(),'label':'实际设计源 → 编译 → 生产切片逐cell核对'}
    (production/'catalog.json').write_text(json.dumps(catalog,separators=(',',':'),ensure_ascii=False),encoding='utf8')
    index.write_text(index_text[:match.start(1)]+json.dumps(entries,separators=(',',':'),ensure_ascii=False)+index_text[match.end(1):],encoding='utf8')
    print('Published 2 verified combat scenes; unrelated descriptors and block registry retained.')

if __name__=='__main__': main()
