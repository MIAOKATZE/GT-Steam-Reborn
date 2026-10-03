"""Independent packaged-scene checks, without importing converter expectations."""
import gzip, hashlib, json, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/gtsr/remaster'
IDS = {'fallen_foundry', 'subsided_factory', 'forgotten_lake_court'}
def read(path, sha):
    raw = gzip.decompress(path.read_bytes())
    assert hashlib.sha256(raw).hexdigest() == sha, path
    return json.loads(raw)
def main():
    catalog = json.loads((OUT/'catalog.json').read_text(encoding='utf8'))
    assert {d['id'] for d in catalog['structures']} == IDS
    assert len(catalog['prefabs']) == 3 and 'engineeringChapters' not in catalog
    assert not (OUT/'engineering').exists()
    src = ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
    defs = json.loads((OUT/'blocks.json').read_text(encoding='utf8'))['blocks']
    registered = {d['id'] if ':' in d['id'] else 'gtsr:'+d['id'] for d in defs if d['runtimeRegistered']}
    blocks = (src/'remaster/RemasterBlocks.java').read_text(encoding='utf8')
    registered = set()
    for number in re.findall(r'register\(block(\d+)\(\)\)', blocks):
        body = re.search(r'RemasterBlock block' + number + r'\(\)\s*\{(.*?)\n    \}', blocks, re.S)
        assert body, number
        registered.add(re.search(r'"(gtsr:[^"]+)"', body.group(1)).group(1))
    runtime = (src/'remaster/RemasterRuntime.java').read_text(encoding='utf8')
    for name in registered: assert '"'+name+'"' in blocks, name
    closure = set(); slices = 0; all_modes = set()
    for d in catalog['prefabs']:
        assert d['id'] in IDS and d['variant'] == 0
        path = OUT/d['file']; closure.add(path); s = read(path,d['sha256']); m=s['metadata']
        assert s['id']==d['id'] and s['variant']==0 and m['combatOnly'] is True
        assert not m.get('productionAmbientNotices') and not m.get('puzzleObjects7')
        assert 'productionPuzzle' not in m and 'productionSimpleScene' not in m
        palette=s['palette']; assert palette[0]=='minecraft:air#0'
        for mat in set(palette):
            name=mat.split('#')[0]
            assert 'notice' not in name.lower() and 'sign' not in name.lower(), mat
            if name.startswith('gtsr:draft'): assert name in registered, mat
            elif name.startswith(('gtsr:ruins_', 'gtsr:royal_')):
                prefix='ruins_' if name.startswith('gtsr:ruins_') else 'royal_'
                authority=src/('architecture/'+('RuinsArchitecture' if prefix=='ruins_' else 'RoyalArchitecture')+'.java')
                material_id=name.split(prefix,1)[1]
                authority_text=authority.read_text(encoding='utf8')
                if material_id.endswith('_stairs') and '"'+material_id+'"' not in authority_text:
                    assert '"_stairs"' in authority_text
                    material_id=material_id.removesuffix('_stairs')
                assert '"'+material_id+'"' in authority_text, mat
            elif not name.startswith('minecraft:'): assert '"'+name+'"' in runtime, mat
        cells={}; counts=[0,0]; owners=set()
        for part in s['slices']:
            owner=(part['x'],part['z']); assert owner not in owners; owners.add(owner)
            path=OUT/part['file']; closure.add(path)
            for x,y,z,length,index in read(path,part['sha256']):
                assert length>0 and 0<=index<len(palette)
                assert x//16==(x+length-1)//16==owner[0] and z//16==owner[1]
                mat=palette[index]; counts[mat=='minecraft:air#0']+=length
                for xx in range(x,x+length):
                    at=(xx,y,z)
                    assert all(s['min'][i]<=at[i]<=s['max'][i] for i in range(3)) and at not in cells
                    cells[at]=mat
            slices+=1
        assert counts==[s['solidCount'],s['airCount']]==[d['solidCount'],d['airCount']] and counts[1]>0
        if s['id']=='forgotten_lake_court':
            assert not m.get('productionNodeBindings') and not m.get('spawns') and not m.get('lootPlan7')
            source=ROOT/'plan/临时计划/preview/prefabs7/forgotten_lake_court-v0.js'
            text=source.read_text(encoding='utf8'); marker='["forgotten_lake_court-v0"]='
            raw=text.split(marker,1)[1].strip().removesuffix(';')
            native=json.loads(raw); voxel=native['voxel']
            original={(xx,y,z):voxel['palette'][index] for x,y,z,length,index in voxel['runs'] for xx in range(x,x+length)}
            assert cells==original, 'Native preview must preserve every captured solid and AIR cell'
        else:
            assert all(0 <= s['min'][i] <= s['max'][i] < 120 for i in (0, 2))
            assert not any(k.startswith(('minecraft:grass#', 'minecraft:dirt#')) for k in palette)
            assert len(m['rooms']) >= 5 and m['roofKeys'] and len(m['playerReviewViews']) >= 10
            nodes={n['id']:n for n in m['nodes']}; modes=set()
            for b in m['productionNodeBindings']:
                assert b['role'] in {'memory','story','chest','spawner'} and cells[tuple(b['at'])]==b['after']
            for chest in m['lootPlan7']:
                mode=chest['unlockMode']; modes.add(mode)
                assert mode in {'direct','story','combat'}
                at=tuple(chest[a] for a in ('x','y','z'))
                assert cells[at]=='gtsr:SealedChest#2'
                assert cells.get((at[0],at[1]+1,at[2]))=='minecraft:air#0'
                assert cells.get((at[0],at[1]-1,at[2])) not in {None,'minecraft:air#0'}
                if mode=='story': assert nodes[chest['storyNode']]['role'] in {'memory','story'}
                if mode=='combat': assert any(n['module']==chest['combatModule'] and n.get('spawn') for n in m['spawns'])
            assert {'direct','combat'} <= modes <= {'direct','story','combat'}
            all_modes.update(modes)
            groups = {n['module'] for n in m['spawns'] if n.get('spawn')}
            for actor in m['spawns']:
                assert set(actor.get('activationModules', [])) <= groups
                assert actor.get('module') not in actor.get('activationModules', [])
            for at in m['roofKeys']:
                assert cells.get(tuple(map(int, at.split(',')))) not in {None, 'minecraft:air#0'}
    assert all_modes == {'direct','story','combat'}
    assert set(OUT.rglob('*.json.gz'))==closure, 'Compressed asset closure mismatch'
    print(f'passed: 3 standard scenes, {slices} slices, SHA/AIR/native identity/bindings/materials/no puzzles or signs')
if __name__=='__main__': main()
