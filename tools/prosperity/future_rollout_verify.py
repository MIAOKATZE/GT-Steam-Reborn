"""Verify every production slice against the committed refined author cells."""
import gzip,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/gtsr/remaster'
catalog=json.loads((OUT/'catalog.json').read_text(encoding='utf8'))
report=[]
for d in catalog['prefabs']:
    if 'futurePlacement' not in d.get('placement',{}):continue
    source=ROOT/'plan/prosperity/structures/future'/(d['id']+'.json')
    raw=source.read_bytes(); model=json.loads(raw)['model']
    assert hashlib.sha256(raw).hexdigest()==d['sourceSha256'],d['id']+' source provenance'
    p=json.loads(gzip.decompress((OUT/d['file']).read_bytes()))
    actual={}
    for s in p['slices']:
        rows=gzip.decompress((OUT/s['file']).read_bytes())
        assert hashlib.sha256(rows).hexdigest()==s['sha256']
        for x,y,z,n,k in json.loads(rows):
            for i in range(n):
                at=(x+i,y,z);assert at not in actual
                actual[at]=p['palette'][k]
    expected={tuple(v[:3]):v[3] for v in model['voxels']}
    assert actual==expected,d['id']+' production changed authored cells'
    loot=p['metadata']['lootPlan7']
    for n in p['metadata']['nodes']+loot+p['metadata']['spawnerPlan']:
        assert n.get('runtimeEnabled') is True,n['id']+' runtime disabled interactive node'
        if n['role'] in ('memory','story'):assert n.get('text','').strip(),n['id']+' readable prose missing'
    for i,a in enumerate(loot):
        assert actual[a['x'],a['y'],a['z']]=='gtsr:SealedChest#2'
        assert actual.get((a['x'],a['y']+1,a['z']))=='minecraft:air#0',a['id']+' blocked lid'
        for b in loot[i+1:]:assert (a['x']-b['x'])**2+(a['z']-b['z'])**2>=16
        if a['unlockMode']=='combat':
            group=next(g for g in p['metadata']['encounterClusters7'] if g['module']==a['combatModule'])
            assert group['members'] or group.get('sealedSpawners'),a['id']+' vacuous combat gate'
        if a['unlockMode']=='story': assert any(n['id']==a['storyNode'] for n in p['metadata']['nodes'])
    assert all(not n['code'].startswith('mb-') for n in p['metadata']['spawns'])
    report.append(dict(id=d['id'],cells=len(actual),chests=len(loot),memories=len(p['metadata']['nodes']),spawners=len(p['metadata']['spawnerPlan']),slices=len(p['slices']),sourceExact=True))
assert len(report)==61 and len(catalog['prefabs'])==64
target=ROOT/'temp/refinement-v72/structure/asset-verification.json'
target.write_text(json.dumps({'passed':True,'models':61,'cells':sum(r['cells'] for r in report),'rows':report},indent=2),encoding='utf8')
print('PASS 61 exact source models, '+str(sum(r['cells'] for r in report))+' authored cells, 64 active prefabs; runtime enabled '+str({k:sum(r[k] for r in report) for k in ('chests','memories','spawners')}))
