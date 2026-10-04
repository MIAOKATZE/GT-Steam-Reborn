"""Publish validated authored future models verbatim into production chunk slices."""
import gzip
import hashlib
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / 'temp/refinement-v72/structure/refined'
OUT = ROOT / 'src/main/resources/assets/gtsr/remaster'


def write(path, obj, zipped=False):
    raw = json.dumps(obj, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(raw, mtime=0) if zipped else raw)
    return hashlib.sha256(raw).hexdigest()


def publish(path):
    source = json.loads(path.read_text(encoding='utf8'))
    entry, model = source['entry'], source['model']
    ident = entry['id']
    source_path=ROOT/'plan/prosperity/structures/future'/path.name
    source_path.parent.mkdir(parents=True,exist_ok=True)
    source_path.write_bytes(path.read_bytes())
    nodes = json.loads(json.dumps(model['nodes']))
    for n in nodes:
        if n['role'] in ('memory','story','chest','spawner'):
            assert n.get('runtimeEnabled') is True, n['id']+' unsupported disabled interaction'
            n.pop('actionDesignOnly',None)
    memories = [n for n in nodes if n['role'] == 'memory']
    actors = [n for n in nodes if n['role'] in ('guard', 'boss', 'mob', 'entity', 'elite')]
    guards, bosses = [n for n in actors if n['role'] != 'boss'], [n for n in actors if n['role'] == 'boss']
    loot = [n for n in nodes if n['role'] == 'chest']
    boss_loot = []
    for n in loot:
        old = next(q for q in model['nodes'] if q['id'] == n['id'])
        mode = old.get('lootMode', old.get('mode', old.get('unlockMode', 'direct')))
        if mode == 'minor-boss-defeat':
            n.update(unlockMode='combat', combatModule=ident+'-boss-group', tier=5)
            boss_loot.append(n)
        elif n['unlockMode'] == 'combat':
            n.update(combatModule=ident+'-guard-group', tierMin=2, tierMax=4)
        elif n['unlockMode'] == 'story':
            n.setdefault('storyNode', memories[0]['id'])
            n.update(tierMin=1 if entry['category'] != 'medium' else 2, tierMax=3)
        else:
            n.update(unlockMode='direct', tierMin=1, tierMax=3 if entry['category']=='medium' else 2)
    if boss_loot and entry.get('relic'):
        boss_loot[0]['storyRelic'] = entry['relic']['id']
    boss_codes=['mb-pressure-auditor','mb-return-overseer','mb-weft-curator','mb-tide-collector','mb-parallax-master','mb-oath-captain','mb-ash-keeper','mb-counterbeat','mb-route-warden','mb-anchor-surveyor']
    for n in actors:
        if n.get('code') in boss_codes:
            n['originalDesignCode']=n['code']
            n['code']='di-'+str(16+boss_codes.index(n['code']))
        n.update(designOnly=False, implemented=True, spawn=True,
                 module=ident+('-boss-group' if n['role']=='boss' else '-guard-group'), productionBattleGuard=n['role']!='boss')
    spawners = [n for n in nodes if n['role'] == 'spawner']
    for n in spawners:
        n['tier'] = n.get('type', 'fragile')
        n['mob'] = n.get('entityCode', n.get('code', 'dr-02'))
    entrance = model['routes'][0][0]
    floor = entrance[1] if not model['routeDetails'][0].get('feet') else entrance[1]-1
    placement = {'category':entry['category'], 'biome':entry['biomePlacement'],
                 'entrance':[entrance[0], floor, entrance[2]], 'layout':'natural-prefab'}
    metadata = {k:v for k,v in model.items() if k not in ('voxels', 'materialPalette')}
    metadata.update(variant=0, nominal=model['size'], designOnly=False, implemented=True,
        productionIdentity=ident, nodes=memories, lootPlan7=loot, spawnerPlan=spawners, spawns=actors,
        surfaceEntrance={'x':entrance[0], 'z':entrance[2], 'topY':floor},
        futurePlacement=placement, encounterClusters7=[
            {'id':ident+'-guard-group','module':ident+'-guard-group','members':[n['id'] for n in guards], 'sealedSpawners':[n['id'] for n in spawners]},
            {'id':ident+'-boss-group','module':ident+'-boss-group','members':[n['id'] for n in bosses]}],
        productionNodeBindings=[{'id':n['id'],'role':n['role'],'at':[n['x'],n['y'],n['z']],
                                 'material':n.get('material')} for n in nodes if n['role'] not in ('guard','boss','mob','entity','elite')])
    # No speculative DO actor is installed in the reading-only expansion.
    if entry['category']=='expansion':
        metadata['spawns']=[]
        metadata['encounterClusters7']=[]
        for n in memories:
            if n['id']=='core-story-reading': n['action']='fiction-complete'
            if n['id']=='single-archive-submit': n['action']='fiction-submit'
    palette=['minecraft:air#0']+sorted({v[3] for v in model['voxels']} - {'minecraft:air#0'})
    indexes={v:i for i,v in enumerate(palette)}
    grouped=defaultdict(list)
    for x,y,z,mat in sorted(model['voxels'],key=lambda v:(v[1],v[2],v[0])):
        rows=grouped[x//16,z//16]; k=indexes[mat]
        if rows and rows[-1][1:3]==[y,z] and rows[-1][0]+rows[-1][3]==x and rows[-1][4]==k: rows[-1][3]+=1
        else: rows.append([x,y,z,1,k])
    slices=[]
    for (x,z),runs in sorted(grouped.items()):
        file=f'prefabs/{ident}-v0/{x}_{z}.json.gz'
        slices.append({'x':x,'z':z,'file':file,'sha256':write(OUT/file,runs,True)})
    lo=[min(v[i] for v in model['voxels']) for i in range(3)]
    hi=[max(v[i] for v in model['voxels']) for i in range(3)]
    solid=sum(v[3]!='minecraft:air#0' for v in model['voxels'])
    record=dict(schemaVersion=7,id=ident,variant=0,min=lo,max=hi,extent=[hi[i]-lo[i]+1 for i in range(3)],
        nominal=model['size'],yMin=lo[1],yMax=hi[1],palette=palette,metadata=metadata,slices=slices,
        solidCount=solid,airCount=len(model['voxels'])-solid,sourceSha256=hashlib.sha256(source_path.read_bytes()).hexdigest())
    file=f'prefabs/{ident}-v0.json.gz'
    digest=write(OUT/file,record,True)
    return ({k:record[k] for k in ('id','variant','min','max','extent','nominal','yMin','yMax','solidCount','airCount','sourceSha256')}
            | {'file':file,'sha256':digest,'placement':{'futurePlacement':placement}})


if __name__=='__main__':
    baseline=json.loads((SRC/'model-baseline.json').read_text(encoding='utf8'))
    assert baseline['models']==61 and baseline['passed'], 'Original source geometry checks must pass first'
    catalog=json.loads((OUT/'catalog.json').read_text(encoding='utf8'))
    originals={'fallen_foundry','subsided_factory','forgotten_lake_court'}
    catalog['structures']=[e for e in catalog['structures'] if e['id'] in originals]
    catalog['prefabs']=[e for e in catalog['prefabs'] if e['id'] in originals]
    for report in baseline['reports']:
        path=SRC/(report['id']+'.json')
        catalog['prefabs'].append(publish(path))
        catalog['structures'].append({'id':report['id'],'category':report['category'],'source':'plan/prosperity/structures/future/'+path.name})
    write(OUT/'catalog.json',catalog)
    write(SRC/'published.json',{'prefabs':len(catalog['prefabs']),'structures':len(catalog['structures']),
        'futureIds':[r['id'] for r in baseline['reports']], 'sourceExact':True})
    print('Published 61 future prefabs; preserved 3 original scenes')
