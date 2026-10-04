"""Verify draft geometry and isolated production compilation without touching formal prefabs."""
import gzip
import itertools
import json
import math
from pathlib import Path
import re
import sys

import remaster_convert as converter

ROOT = Path(__file__).resolve().parents[2]
OUT = (ROOT / sys.argv[1]).resolve() if len(sys.argv)>1 else ROOT / 'temp/scene-refine-v70'


def main():
    converter.OUT = OUT / 'verification-production'
    converter.PUZZLES = {'fallen_foundry': {}, 'subsided_factory': {}}
    java = (ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java').read_text(encoding='utf8')
    registered = set(re.findall(r'new RemasterBlock\(\s*"(gtsr:[a-z0-9_]+)"', java))
    reports = []
    for prefab in ('subsided_factory', 'fallen_foundry'):
        source = OUT / (prefab + '-v0.js')
        draft = converter.snapshot(source)
        record = converter.compile_one(source)
        data = json.loads(gzip.decompress((converter.OUT / record['file']).read_bytes()))
        metadata = data['metadata']
        cells = {}
        for sliced in data['slices']:
            for x, y, z, n, material in json.loads(gzip.decompress((converter.OUT / sliced['file']).read_bytes())):
                cells.update({(xx, y, z): data['palette'][material] for xx in range(x, x + n)})
        assert not metadata['productionGeometryChanges'], metadata['productionGeometryChanges']
        source_cells = {}
        for x,y,z,n,material in draft['voxel']['runs']:
            source_cells.update({(xx,y,z):draft['voxel']['palette'][material] for xx in range(x,x+n)})
        changed_chests = 0
        for at,material in source_cells.items():
            expected = 'gtsr:SealedChest#2' if material=='gtsr:draft7_seal_chest#0' else material
            assert cells.get(at)==expected, ('unauthorized-cell-change',at,material,cells.get(at))
            changed_chests += material=='gtsr:draft7_seal_chest#0'
        extra = {at:material for at,material in cells.items() if at not in source_cells}
        assert all(material=='minecraft:air#0' for material in extra.values())
        assert not any(m.startswith(('minecraft:grass', 'minecraft:dirt')) for m in data['palette'])
        assert all(m.split('#')[0] in registered for m in data['palette'] if ':draft' in m)
        assert metadata['nativeScene'] is False and metadata['combatOnly'] and metadata['compactScene']
        assert len(metadata['playerReviewViews']) == 10
        assert all(cells.get(tuple(map(int, roof.split(','))), 'minecraft:air#0') != 'minecraft:air#0' for roof in metadata['roofKeys'])
        assert all(n['block'] == 'gtsr:draft_pressure_console#0' and cells[n['x'], n['y'], n['z']] == n['block'] for n in metadata['nodes'] if n['role'] == 'memory')
        assert all(n['block'] == 'gtsr:SealedChest#2' and cells[n['x'], n['y'], n['z']] == n['block'] for n in metadata['lootPlan7'])
        assert all(cells.get((n['x'], n['y']-1, n['z']), 'minecraft:air#0') != 'minecraft:air#0' for n in metadata['lootPlan7'])
        for chest in metadata['lootPlan7']:
            assert cells.get((chest['x'],chest['y']+1,chest['z']))=='minecraft:air#0', ('blocked-chest-lid',chest['id'])
            if chest['unlockMode']!='direct':
                room=next(r for r in metadata['rooms'] if r['moduleId']==chest['module'])
                assert chest['y']>=room['y']+2, ('conditional-chest-on-floor',chest['id'])
                for route in metadata['routes']:
                    for a,b in zip(route,route[1:]):
                        steps=max(abs(a[0]-b[0]),abs(a[2]-b[2]),1)
                        for step in range(steps+1):
                            at=[a[i]+(b[i]-a[i])*step/steps for i in range(3)]
                            if abs(chest['y']-(at[1]+1))<=3:
                                assert not(abs(chest['x']-at[0])<1.8 and abs(chest['z']-at[2])<1.8), ('reward-plinth-obstructs-route',chest['id'],at)
        for a,b in itertools.combinations(metadata['lootPlan7'],2):
            assert math.hypot(a['x']-b['x'],a['z']-b['z'])>=4, ('clustered-chests',a['id'],b['id'])
        boss_rewards=[n for n in metadata['lootPlan7'] if n.get('combatModule','').endswith('boss-group')]
        assert len(boss_rewards)>=3, ('boss-reward-distribution',prefab)
        approaches = []
        for n in metadata['lootPlan7'] + metadata['spawnerPlan'] + [n for n in metadata['nodes'] if n['role']=='memory']:
            candidates=[]
            for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                x,y,z=n['x']+dx,n['y'],n['z']+dz
                # A story console may be placed on a bench one block above its player aisle.
                for fy in [y,y-1]:
                    if cells.get((x,fy-1,z),'minecraft:air#0')!='minecraft:air#0' and all(cells.get((x,yy,z))=='minecraft:air#0' for yy in [fy,fy+1]):
                        candidates.append([x,fy,z])
            assert candidates, ('inaccessible-interaction',n['id'])
            approaches.append({'id':n['id'],'standing':candidates[0],'cardinalReachClear':True})
        assert not any('pollution_' in key or 'draft5_nest_vein' in key for key in data['palette'])
        assert len(metadata['spawnerPlan'])==9
        assert {n['tier'] for n in metadata['spawnerPlan']}=={'fragile','stable','runaway'}
        for n in metadata['spawnerPlan']:
            assert cells[n['x'],n['y'],n['z']]==n['block']
            assert n['block']=='gtsr:draft6_spawner_'+n['tier']+'#0'
            assert n['policy']['batch']=={'fragile':2,'stable':4,'runaway':6}[n['tier']]
            assert n['policy']['sealAt']=={'fragile':10,'stable':24,'runaway':45}[n['tier']]
        (OUT / ('interaction-approaches-'+prefab+'.json')).write_text(json.dumps(approaches,indent=2),encoding='utf8')
        collisions = []
        for spawn in metadata['spawns']:
            width = {'dc-08': 9, 'dc-02': 10.4, 'dr-18': .7, 'dr-08': .78, 'dr-16': 1.05, 'dr-14': .896, 'dr-04': .75, 'dr-02': .65, 'dr-15': .8}[spawn['code']]
            height = {'dc-08': 16, 'dc-02': 14, 'dr-18': .6, 'dr-08': .995, 'dr-16': 1.18, 'dr-14': 1.6, 'dr-04': .5, 'dr-02': .282, 'dr-15': 1.1}[spawn['code']]
            for x in range(math.floor(spawn['x']+.5-width/2), math.ceil(spawn['x']+.5+width/2)):
                for z in range(math.floor(spawn['z']+.5-width/2), math.ceil(spawn['z']+.5+width/2)):
                    assert cells.get((x,spawn['y']-1,z),'minecraft:air#0') != 'minecraft:air#0', (spawn['id'],'unsupported',x,z)
                    for y in range(spawn['y'],math.ceil(spawn['y']+height)):
                        material = cells.get((x,y,z))
                        if material != 'minecraft:air#0':
                            collisions.append({'actor':spawn['id'],'at':[x,y,z],'material':material})
        assert not collisions, collisions[:20]
        boss = next(s for s in metadata['spawns'] if s['role']=='boss')
        assert not boss.get('activationModules', [])
        assert metadata['bossActivation']['kind']=='destroy-all-spawners'
        assert set(metadata['bossActivation']['spawnerIds'])=={n['id'] for n in metadata['spawnerPlan']}
        for chest in metadata['lootPlan7']:
            assert chest['unlockMode'] in ('direct','story','combat') and chest['lootPool']
            if chest['unlockMode']=='story':
                assert any(n['id']==chest['storyNode'] and n['role']=='memory' for n in metadata['nodes'])
            elif chest['unlockMode']=='combat':
                assert any(s['module']==chest['combatModule'] for s in metadata['spawns'])
        reports.append({'id':prefab,'solidCount':data['solidCount'],'airCount':data['airCount'],'productionGeometryChanges':len(metadata['productionGeometryChanges']), 'nodes':len(metadata['productionNodeBindings']), 'chests':len(metadata['lootPlan7']), 'actorAabbCollisions':0, 'floatingChests':0, 'bossRewardChests':len(boss_rewards), 'minimumChestHorizontalSeparation':min(math.hypot(a['x']-b['x'],a['z']-b['z']) for a,b in itertools.combinations(metadata['lootPlan7'],2)), 'blockedChestLids':0, 'conditionalChestsOnFloor':0, 'unregisteredDraftMaterials':0, 'roofKeys':len(metadata['roofKeys']), 'playerReviewViews':len(metadata['playerReviewViews']), 'sourceCellsCompared':len(source_cells),'authorizedSealedChestMappings':changed_chests,'additionalProductionAir':len(extra),'unauthorizedSourceToProductionChanges':0})
    (OUT / 'geometry-verification.json').write_text(json.dumps({'passed':True,'reports':reports},indent=2),encoding='utf8')
    print(json.dumps(reports))


if __name__ == '__main__':
    main()
