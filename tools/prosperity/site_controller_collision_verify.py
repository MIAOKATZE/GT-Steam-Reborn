"""Prove final independent consoles with actual Minecraft collisions and ray tracing.

Compiles current production sources into a system TemporaryDirectory. Only FML
registration calls in temporary architecture factory copies are omitted.
"""
import argparse
import gzip
import hashlib
import json
import math
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/gtsr/remaster'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snapshot(path):
    return json.loads(path.read_text(encoding='utf8').split(']=', 1)[1].rstrip(';\r\n'))


def positions(value):
    """All authored mechanism coordinates, including nested future states/deltas."""
    result = set()
    if isinstance(value, dict):
        if all(isinstance(value.get(k), (int, float)) for k in ('x', 'y', 'z')):
            result.add(tuple(int(value[k]) for k in ('x', 'y', 'z')))
        for item in value.values():
            if isinstance(item, (dict, list)):
                result.update(positions(item))
    elif isinstance(value, list):
        if len(value) >= 3 and all(isinstance(i, (int, float)) for i in value[:3]):
            result.add(tuple(int(i) for i in value[:3]))
        else:
            for item in value:
                result.update(positions(item))
    return result


def ray_cells(eye, target):
    count = max(1, math.ceil(math.dist(eye, target) * 40))
    return {tuple(math.floor(eye[j] + (target[j] - eye[j]) * i / count) for j in range(3))
            for i in range(count + 1)}


def final_inputs():
    catalog = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf8'))
    source_dir = next(p / 'preview/prefabs7' for p in (ROOT / 'plan').iterdir()
                      if (p / 'preview/prefabs7').is_dir())
    inputs = {ASSETS / 'catalog.json': sha(ASSETS / 'catalog.json')}
    audit_rows = []
    for descriptor in catalog['prefabs']:
        manifest_path = ASSETS / descriptor['file']
        inputs[manifest_path] = sha(manifest_path)
        manifest = json.loads(gzip.decompress(manifest_path.read_bytes()))
        m = manifest['metadata']
        console = m.get('productionSiteController')
        if not console:
            continue
        source = source_dir / f"{descriptor['id']}-v{descriptor['variant']}.js"
        if not source.is_file():
            matches = [p for p in source_dir.glob('*.js')
                       if snapshot(p)['metadata'].get('id') == descriptor['id']
                       and snapshot(p)['metadata'].get('variant') == descriptor['variant']]
            assert len(matches) == 1, (descriptor['id'], 'source snapshot missing')
            source = matches[0]
        inputs[source] = sha(source)
        original = snapshot(source)
        om = original['metadata']
        at = tuple(console[k] for k in ('x', 'y', 'z'))
        approach, = m['productionInteractionApproaches']
        assert console['role'] == 'control' and console['id'] == 'site-puzzle-controller'
        assert approach['node'] == console['id'] and approach['role'] == 'control'
        assert approach['sourceSha256'] == console['productionDerived']['sourceSha256'] == sha(source)
        assert approach['targetBlock'] == console['block']
        assert tuple(approach['floor']['at']) == (approach['feet'][0], approach['feet'][1] - 1, approach['feet'][2])
        original_palette = original['voxel']['palette']
        probes = {at, (at[0], at[1] + 1, at[2]), tuple(approach['floor']['at']),
                  tuple(console['productionDerived']['supportAt'])}
        source_material = {p: 'minecraft:air#0' for p in probes}
        for x, y, z, length, pi in original['voxel']['runs']:
            for p in probes:
                if p[1] == y and p[2] == z and x <= p[0] < x + length:
                    source_material[p] = original_palette[pi]
        assert source_material[at].split('#')[0] == 'minecraft:air', (source, at, 'overwrites authored solid')
        assert source_material[(at[0], at[1] + 1, at[2])].split('#')[0] == 'minecraft:air'
        assert source_material[tuple(approach['floor']['at'])] == approach['floor']['material'], (source, 'authored standing floor changed')
        reserved = set()
        for field in ('nodes', 'shapeMechanisms', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7',
                      'testimonyPedestals', 'navigationHints', 'outdoorFixtures', 'damageManifest'):
            reserved.update(positions(om.get(field, [])))
        assert at not in reserved, (source, at, 'reserved memory/node/future mechanism collision')
        for field in ('nodes', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7', 'testimonyPedestals', 'navigationHints'):
            strip = lambda items: [{k: v for k, v in item.items() if k != 'block'} for item in items]
            assert strip(m.get(field, [])) == strip(om.get(field, [])), (source, field, 'authored descriptor modified')
        assert m.get('shapeMechanisms', []) == om.get('shapeMechanisms', []), (source, 'shape state/delta modified')
        actors = [(a, [a['x'], a['y'], a['z']], a.get('clearance', {})) for a in om.get('spawns', [])]
        actors += [(a, a['spawnZone']['center'], a['spawnZone'].get('clearance', {})) for a in om.get('spawnerPlan', [])]
        for _, centre, clearance in actors:
            half_w, half_d = int(clearance.get('width', 1)) // 2, int(clearance.get('depth', 1)) // 2
            assert not (centre[0] - half_w <= at[0] <= centre[0] + half_w
                        and centre[2] - half_d <= at[2] <= centre[2] + half_d
                        and centre[1] <= at[1] < centre[1] + int(clearance.get('height', 3))), (source, at, 'actor clearance overwritten')
        for old in om.get('interactionApproaches', []):
            if old.get('feet'):
                feet = old['feet']
                assert at not in (tuple(feet), (feet[0], feet[1] + 1, feet[2])), (source, 'existing standing room overwritten')
            if old.get('floor', {}).get('at'):
                assert at != tuple(old['floor']['at']), (source, 'existing standing floor overwritten')
            if old.get('eye') and old.get('target'):
                assert at not in ray_cells(old['eye'], old['target']), (source, 'existing interaction ray overwritten')
        # Same shifted site origin as Java: include every local slice intersecting
        # every owner around the entire standing/support/ray envelope.
        feet, eye, target = approach['feet'], approach['eye'], approach['target']
        x0 = math.floor(min(feet[0] + .2, eye[0], target[0]) + 40003) - 2
        x1 = math.floor(max(feet[0] + .8, eye[0], target[0]) + 40003) + 2
        z0 = math.floor(min(feet[2] + .2, eye[2], target[2]) + 40007) - 2
        z1 = math.floor(max(feet[2] + .8, eye[2], target[2]) + 40007) + 2
        slices = {(s['x'], s['z']): s['file'] for s in manifest['slices']}
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            for cz in range(z0 >> 4, (z1 >> 4) + 1):
                lx, lz = (cx << 4) - 40003, (cz << 4) - 40007
                for sx in range(lx // 16, (lx + 15) // 16 + 1):
                    for sz in range(lz // 16, (lz + 15) // 16 + 1):
                        if (sx, sz) in slices:
                            path = ASSETS / slices[sx, sz]
                            inputs[path] = sha(path)
        audit_rows.append({'prefab': f"{descriptor['id']}-v{descriptor['variant']}",
                           'source': str(source.relative_to(ROOT)), 'sourceSha256': sha(source),
                           'controllerAt': at, 'authoredProtectionPassed': True})
    assert len(audit_rows) == 198, ('final resource derived controller count', len(audit_rows))
    return inputs, audit_rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', default='temp/site-controller-physical.json')
    args = parser.parse_args()
    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    inputs, audit_rows = final_inputs()
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/classes/java/patchedMc')]
    for name in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                 'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        cp += [str(j) for j in jars if j.name == name][:1]
    cp += [str(ROOT / 'src/main/resources'), str(ROOT / 'build/resources/patchedMc')]
    cp += [str(j) for j in jars if 'jabel' not in j.name and 'byte-buddy' not in j.name]
    with tempfile.TemporaryDirectory(prefix='gtsr-site-controller-physical-') as folder:
        tmp = Path(folder)
        sources = []
        for name in ('RuinsArchitecture', 'RoyalArchitecture'):
            path = ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{name}.java'
            inputs[path] = sha(path)
            text = re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$',
                          '', path.read_text(encoding='utf8'), flags=re.M)
            target = tmp / f'{name}.java'
            target.write_text(text, encoding='utf8')
            sources.append(target)
        sources += list((ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster').glob('*.java'))
        sources += [ROOT / 'tools/prosperity/SiteControllerCollisionCheck.java']
        sources += [ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{name}.java'
                    for name in ('encounter/TileEntitySealedChest', 'encounter/BlockSealedChest',
                                 'architecture/BlockZenithLog', 'lore/HistoryProgress', 'lore/HistoryEvents')]
        for path in sources:
            if path.is_relative_to(ROOT):
                inputs[path] = sha(path)
        inputs[Path(__file__).resolve()] = sha(Path(__file__).resolve())
        classpath = ';'.join(cp).replace('\\', '/')
        javac_args = tmp / 'javac.args'
        javac_args.write_text('-encoding UTF-8\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/')
                             + '"\n' + '\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf8')
        subprocess.run(['javac', '@' + str(javac_args)], check=True, cwd=ROOT)
        java_args = tmp / 'java.args'
        java_args.write_text('-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\n'
                             + 'SiteControllerCollisionCheck "' + str(report_path).replace('\\', '/') + '"', encoding='utf8')
        execution = subprocess.run(['java', '@' + str(java_args)], cwd=ROOT)
        data = json.loads(report_path.read_text(encoding='utf8'))
        changed = [str(p.relative_to(ROOT)) for p, digest in inputs.items() if sha(p) != digest]
        data['authoredProtectionAudit'] = audit_rows
        data['inputSHA256'] = {str(p.relative_to(ROOT)): digest for p, digest in inputs.items()}
        data['inputsChangedDuringRun'] = changed
        data['passed'] &= not changed
        report_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf8')
        assert not changed, ('inputs changed during physical verification', changed)
        execution.check_returncode()


if __name__ == '__main__':
    main()
