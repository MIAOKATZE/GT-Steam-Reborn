"""Directly verify authored solids, negative spaces, slice bounds and asset digests."""
import gzip
import hashlib
import json
import copy
import math
import re
import argparse
from collections import Counter
from pathlib import Path
from remaster_convert import OUT, SOURCE, snapshot, derive_site_controller, runtime_node_owners, controller_approach, official_runtime_node_owners


def read(path, sha=None):
    raw = gzip.decompress(path.read_bytes())
    if sha:
        assert hashlib.sha256(raw).hexdigest() == sha, path
    return json.loads(raw)


def rollout():
    """Read the runtime authority; fail if its representation changes instead of guessing a list."""
    policy = (Path(__file__).resolve().parents[2] /
              'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterRollout.java').read_text(encoding='utf8')
    ids = re.search(r'private static final List<String> ACTIVE\s*=\s*Collections\s*\.\s*unmodifiableList\(\s*Arrays\s*\.\s*asList\((.*?)\)\s*\);', policy, re.S)
    variant = re.search(r'public static int standardVariant\(String id\)\s*\{\s*if \(!isActive\(id\)\) throw [^;]+;\s*return (\d+);\s*\}', policy)
    assert ids and variant, 'Unrecognized central rollout policy; update the verifier explicitly'
    active = json.loads('[' + ids.group(1) + ']')
    assert active and len(set(active)) == len(active)
    return set(active), int(variant.group(1))


def simple_controls(id):
    # Accepted scene interaction contract, independent of the converter's data table.
    return {'fallen_foundry': ['control-0', 'control-2'],
            'subsided_factory': ['factory-stage-0', 'factory-stage-3'],
            'forgotten_lake_court': []}[id]


def verify_simple_ambient(source, manifest, actual_solids, owners):
    """Loose authored interactive carriers become complete, metadata-only read-only records."""
    definitions = json.loads((OUT / 'blocks.json').read_text(encoding='utf8'))['blocks']
    interactive = {row['id'] if ':' in row['id'] else 'gtsr:' + row['id']
                   for row in definitions if row['category'] != 'decoration'} | {'gtsr:draft_notice_board'}
    authored = {(xx, y, z): source['voxel']['palette'][k]
                for x, y, z, length, k in source['voxel']['runs'] for xx in range(x, x + length)
                if source['voxel']['palette'][k].split('#')[0] in interactive}
    expected = {at: material for at, material in authored.items()
                if at not in owners and actual_solids.get(at) == material}
    notices = manifest['metadata'].get('productionAmbientNotices', [])
    assert len({n['id'] for n in notices}) == len(notices)
    assert len({(n['x'], n['y'], n['z']) for n in notices}) == len(notices)
    assert {tuple(n[axis] for axis in ('x', 'y', 'z')) for n in notices} == set(expected), (manifest['id'], 'loose authored device coverage')
    ids = simple_controls(manifest['id'])
    targets = {at: node for at, (node, role) in owners.items() if node['id'] in ids and role == 'control'}
    assert {node['id'] for node in targets.values()} == set(ids), (manifest['id'], 'missing formal guide target')
    base_keys = {'id', 'role', 'x', 'y', 'z', 'block', 'label', 'text', 'readOnly', 'simpleSceneAmbient'}
    route = ('沿灯光和楼梯前进，清除守卫并确认两处联锁后挑战Boss。' if ids
             else '沿灯光和楼梯清除八室守卫，再从西侧面对缄王。')
    for notice in notices:
        at = tuple(notice[axis] for axis in ('x', 'y', 'z'))
        assert at not in owners and notice['block'] == expected[at] == actual_solids[at]
        assert notice['id'] == 'ambient:' + ':'.join(map(str, at)) and notice['role'] == 'ambient-notice'
        assert notice['readOnly'] is True and notice['simpleSceneAmbient'] is True
        plaque = notice['block'].split('#')[0] == 'gtsr:draft_notice_board'
        assert notice['label'] == ('行进方向' if plaque else '设备陈列')
        assert notice['text'] == ('' if plaque else '此处为设备陈列，无需操作。') + route
        assert set(notice) == base_keys | ({'guideTarget', 'guideTargetNode'} if ids else set()), (manifest['id'], 'ambient record must contain no puzzle, reward or fixed tree target')
        if ids:
            target_at = min(targets, key=lambda target: (math.dist(at, target), targets[target]['id']))
            assert notice['guideTarget'] == list(target_at) and notice['guideTargetNode'] == targets[target_at]['id'], (manifest['id'], 'ambient guide must identify nearest real scene control')
    return len(notices)


def verify_simple_scene(source, manifest, actual, expected_solids):
    """Verify the narrowed metadata and formerly absent foot layer against authored inputs."""
    before, after = source['metadata'], manifest['metadata']
    controls = simple_controls(manifest['id'])
    assert after['productionSimpleScene'] == {'version': 1, 'controls': controls,
                                             'guardDeathRequired': True, 'legacyCompatible': True}
    puzzle = after['productionPuzzle']
    assert puzzle['kind'] == 'simple-scene' and puzzle['title'] and puzzle['explain']
    assert puzzle['fields'] == puzzle['target'] == puzzle['order'] == []
    nodes = {n['id']: n for n in before.get('nodes', [])}
    bindings = {n['id']: n for n in after['productionNodeBindings']}
    for id in controls:
        assert nodes[id]['role'] == bindings[id]['role'] == 'control'
        assert bindings[id]['at'] == [nodes[id][axis] for axis in ('x', 'y', 'z')]
        at = tuple(bindings[id]['at'])
        assert manifest['palette'][actual[at]] == bindings[id]['after']
    shapes = copy.deepcopy(before.get('shapeMechanisms', []))
    for shape in shapes:
        shape.update(fields=[], answer=[], order=[], clue='可选侧门：点击打开。主路不依赖此门。')
    assert after.get('shapeMechanisms', []) == shapes, (manifest['id'], 'simple shape changed authored geometry or safety')
    if manifest['id'] == 'forgotten_lake_court':
        return 0  # Its live placement is the existing natural tree, with no new room foot carving.
    source_cells = {(xx, y, z): source['voxel']['palette'][k]
                    for x, y, z, length, k in source['voxel']['runs'] for xx in range(x, x + length)}
    reserved = {tuple(binding['at']) for binding in after['productionNodeBindings']}
    for shape in before.get('shapeMechanisms', []):
        reserved.add(tuple(shape['control']))
        reserved.update(tuple(delta['at']) for delta in shape.get('delta', []))
    for node in before.get('nodes', []):
        if all(axis in node for axis in ('x', 'y', 'z')) and node.get('role') not in ('elite', 'guard', 'boss'):
            reserved.add(tuple(node[axis] for axis in ('x', 'y', 'z')))
    # Geometric definition: interior floor+one, excluding source voxels and authored owners.
    missing = {(x, room['y'] + 1, z) for room in before.get('rooms', [])
               for x in range(room['x'] + 1, room['x'] + room['w'] - 1)
               for z in range(room['z'] + 1, room['z'] + room['d'] - 1)} - set(source_cells) - reserved
    assert missing, (manifest['id'], 'fixture no longer exercises missing-foot geology')
    for at in missing:
        assert at in actual and manifest['palette'][actual[at]] == 'minecraft:air#0', (manifest['id'], at, 'missing-foot must explicitly carve native geology')
    # Every retained authored solid has already been reconstructed from source and declared changes.
    for at, material in expected_solids.items():
        assert manifest['palette'][actual[at]] == material, (manifest['id'], at, 'authored solid lost while clearing foot layer')
    return len(missing)


def verify_surface_entrance(source, manifest, actual, descriptor):
    """Independently reconstruct the bounded first-platform selection and its proof."""
    metadata = source['metadata']
    produced = manifest['metadata'].get('productionSurfaceEntrance')
    excluded = (metadata.get('surfaceEntrance') or metadata.get('terrain', {}).get('buried') == 'full'
                or metadata['id'] in ('fiction_expansion_project', 'prosperity_city_full', 'zenith_tree'))
    routes = metadata.get('routes', [])
    if excluded or not routes or not routes[0]:
        assert produced is None, (manifest['id'], 'unexpected derived surface entrance')
        return False
    source_blocks = {(xx, y, z): source['voxel']['palette'][k]
                     for x, y, z, n, k in source['voxel']['runs'] for xx in range(x, x + n)}
    original = routes[0][0]
    review = metadata.get('structureDesignReview', {})
    matches = [r for r in review.get('stairFunctionalAccessRoutes', []) + review.get('stairExceptionRoutes', [])
               if r.get('originalRouteKey') == 'routes:0' and r.get('actualPoints')]
    first = matches[0]['actualPoints'][0] if matches else original
    floor = next(((first[0], first[1] + delta, first[2])
                  for delta in (0, -1)
                  if source_blocks.get((first[0], first[1] + delta, first[2]), '').split('#')[0]
                  == 'gtsr:ruins_mossroot_paving'), None)
    if floor is None:
        assert produced is None
        return False
    platform = {floor}
    pending = [floor]
    for p in pending:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (p[0] + dx, floor[1], p[2] + dz)
            if (q not in platform and max(abs(q[0] - floor[0]), abs(q[2] - floor[2])) <= 2
                    and source_blocks.get(q, '').split('#')[0] == 'gtsr:ruins_mossroot_paving'):
                platform.add(q)
                pending.append(q)
    candidates = [floor]
    for radius in (1, 2):
        candidates.extend((floor[0] + dx, floor[1], floor[2] + dz)
                          for dx, dz in ((radius, 0), (-radius, 0), (0, radius), (0, -radius),
                                         (radius, radius), (-radius, radius), (radius, -radius), (-radius, -radius)))
    def production(at):
        return manifest['palette'][actual.get(at, 0)]
    def open_lane(p):
        return all(source_blocks.get((p[0], p[1] + h, p[2]), 'minecraft:air#0') == 'minecraft:air#0'
                   and production((p[0], p[1] + h, p[2])) == 'minecraft:air#0' for h in (1, 2))
    usable = [p for p in candidates if p in platform and production(p) == source_blocks[p] and open_lane(p)]
    def neighbors(p):
        return [(p[0] + dx, p[1], p[2] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                if source_blocks.get((p[0] + dx, p[1], p[2] + dz), '').split('#')[0]
                == 'gtsr:ruins_mossroot_paving' and open_lane((p[0] + dx, p[1], p[2] + dz))]
    chosen = (floor if floor in usable else sorted(usable, key=lambda p: -len(neighbors(p)))[0]) if usable else None
    assert chosen and produced, (manifest['id'], 'missing usable authored first-platform entrance')
    assert [produced['x'], produced['topY'], produced['z']] == list(chosen)
    proof = produced['productionDerived']
    assert proof['format'] == 'gtsr:surface-entrance/v1'
    assert proof['sourceSha256'] == manifest['sourceSha256'] == descriptor['sourceSha256']
    assert proof['originalRouteKey'] == 'routes:0'
    assert proof['originalFirstPoint'] == original and proof['reviewedFirstPoint'] == first
    assert proof['actualSourceFloor'] == list(chosen)
    assert proof['sourceFloorMaterial'] == source_blocks[chosen] and proof['searchRadius'] == 2
    assert proof['laneClearNeighbors'] == [list(p) for p in neighbors(chosen)]
    path = proof['platformPath']
    assert path[0] == list(floor) and path[-1] == list(chosen)
    assert all(tuple(p) in platform for p in path)
    assert all(sum(abs(a[i] - b[i]) for i in range(3)) == 1 for a, b in zip(path, path[1:]))
    assert proof['feet'] == [chosen[0], chosen[1] + 1, chosen[2]]
    assert proof['head'] == [chosen[0], chosen[1] + 2, chosen[2]]
    a, b = proof['clearanceRay']
    assert a == [chosen[0] + .5, chosen[1] + 1.01, chosen[2] + .5]
    assert b == [chosen[0] + .5, chosen[1] + 2.8, chosen[2] + .5]
    for step in range(100):
        at = tuple(math.floor(a[i] + (b[i] - a[i]) * step / 99) for i in range(3))
        assert source_blocks.get(at, 'minecraft:air#0') == production(at) == 'minecraft:air#0'
    assert descriptor['placement']['productionSurfaceEntrance'] == produced
    return True


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--active-only', action='store_true', help='Check only centrally enabled standard variants')
    options = args.parse_args()
    active, standard = rollout()
    catalog = json.loads((OUT / 'catalog.json').read_text(encoding='utf8'))
    assert len(catalog['structures']) == 90
    assert len(catalog['prefabs']) == 268
    assert len(catalog['engineeringChapters']) == 8
    sums = Counter()
    for descriptor in catalog['prefabs']:
        simple = descriptor['id'] in active and descriptor['variant'] == standard
        if options.active_only and not simple:
            continue
        d = read(OUT / descriptor['file'], descriptor['sha256'])
        assert descriptor['placement'] == {key: d['metadata'][key] for key in
                                           ('terrain', 'surfaceEntrance', 'productionSurfaceEntrance', 'rooms', 'entryApronEnvelope')
                                           if key in d['metadata']}
        assert d['schemaVersion'] == 7
        assert d['extent'][1] <= 256
        assert d['palette'][0] == 'minecraft:air#0'
        actual = {}
        for s in d['slices']:
            for x, y, z, n, k in read(OUT / s['file'], s['sha256']):
                assert x // 16 == s['x'] == (x + n - 1) // 16 and z // 16 == s['z']
                assert n > 0 and 0 <= k < len(d['palette'])
                for xx in range(x, x + n):
                    assert (xx, y, z) not in actual
                    actual[xx, y, z] = k
        original = snapshot(SOURCE / 'preview/prefabs7' / f"{d['id']}-v{d['variant']}.js")
        source_path = SOURCE / 'preview/prefabs7' / f"{d['id']}-v{d['variant']}.js"
        assert hashlib.sha256(source_path.read_bytes()).hexdigest() == d['sourceSha256']
        count = 0
        expected_solids = {}
        for x, y, z, n, k in original['voxel']['runs']:
            for xx in range(x, x + n):
                key = original['voxel']['palette'][k]
                if key.split('#')[0] == 'gtsr:draft7_seal_chest':
                    key = 'gtsr:SealedChest#2'
                if key != 'minecraft:air#0':
                    expected_solids[xx, y, z] = key
                count += 1
        assert count == original['voxel']['count']
        for change in d['metadata']['productionGeometryChanges']:
            at = tuple(change['at'])
            assert change['reason'] in ('declared-clearance', 'authored-damage')
            assert expected_solids.get(at) == change['before'], (d['id'], at, 'unexplained removal')
            expected_solids.pop(at)
        for binding in d['metadata']['productionNodeBindings']:
            at = tuple(binding['at'])
            assert binding['role'] in ('memory', 'testimony', 'control', 'shape', 'spawner', 'chest', 'puzzle-object')
            if binding['role'] == 'chest':
                assert binding['after'] == 'gtsr:SealedChest#2'
            expected_solids[at] = binding['after']
        owners = official_runtime_node_owners(d['metadata'])
        bindings = d['metadata']['productionNodeBindings']
        assert len(bindings) == len(owners) and {tuple(n['at']) for n in bindings} == set(owners)
        for binding in bindings:
            node, role = owners[tuple(binding['at'])]
            assert (binding['id'], binding['role']) == (node['id'], role), (d['id'], 'non-runtime binding')
        actual_solids = {at: d['palette'][k] for at, k in actual.items() if k != 0}
        source_notices = {(xx, y, z): original['voxel']['palette'][k]
                          for x, y, z, length, k in original['voxel']['runs']
                          for xx in range(x, x + length)
                          if original['voxel']['palette'][k].split('#')[0] == 'gtsr:draft_notice_board'}
        ambient = d['metadata'].get('productionAmbientNotices', [])
        assert len({n['id'] for n in ambient}) == len(ambient)
        assert len({(n['x'], n['y'], n['z']) for n in ambient}) == len(ambient)
        if simple:
            sums['simpleReadOnlyAmbient'] += verify_simple_ambient(original, d, actual_solids, owners)
        else:
            for notice in ambient:
                assert not notice.get('simpleSceneAmbient'), (d['id'], d['variant'], 'archived ambient contract must not change')
                at = tuple(notice[k] for k in ('x', 'y', 'z'))
                assert notice['role'] == 'ambient-notice' and at not in owners
                assert source_notices.get(at) == actual_solids.get(at) == notice['block']
                assert notice['text'] and len(notice['guideTarget']) == 3
                target, _ = owners[tuple(notice['guideTarget'])]
                assert target['id'] == notice['guideTargetNode']
                site = d['metadata']['exteriorDetail7']['sites'][notice['exteriorSiteIndex']]
                assert site['location'] == notice['exteriorSiteAt'] and math.dist(at, site['location']) <= 12
                assert site['purpose'] in notice['text'] and d['metadata']['exteriorDetail7']['erosionSource'] in notice['text']
        assert len(runtime_node_owners(d['metadata'])) == len(owners) + len(ambient)
        assert actual_solids == expected_solids, (d['id'], d['variant'], 'unexplained geometry change')
        assert len(actual_solids) == d['solidCount']
        assert not any(key.startswith('gtsr:draft7_seal_chest') for key in actual_solids.values())
        assert sum(k == 0 for k in actual.values()) == d['airCount']
        sums['derivedSurfaceEntrances'] += int(verify_surface_entrance(original, d, actual, descriptor))
        for k, value in original['metadata'].items():
            if simple and k == 'shapeMechanisms':
                continue  # Complete exact authored-key comparison is performed by verify_simple_scene below.
            if k in ('nodes', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7', 'testimonyPedestals', 'navigationHints'):
                # Only the physical carrier block may be normalized. IDs, locations, prerequisites,
                # clues, identities and reward rules remain exactly those of the reviewed snapshot.
                without_block = lambda rows: [{key: v for key, v in row.items() if key != 'block'} for row in rows]
                assert without_block(d['metadata'][k]) == without_block(value), (d['id'], k)
            else:
                assert d['metadata'][k] == value, (d['id'], k)
        if simple:
            sums['simpleRoomFootAir'] += verify_simple_scene(original, d, actual, expected_solids)
            sums['simpleSceneVariants'] += 1
        else:
            assert 'productionSimpleScene' not in d['metadata'], (d['id'], d['variant'], 'archived variant changed to simple scene')
            assert d['metadata']['productionPuzzle']['fields']
        source_metadata = copy.deepcopy(original['metadata'])
        source_metadata['productionPuzzle'] = d['metadata']['productionPuzzle']
        source_palette = ['minecraft:air#0'] + original['voxel']['palette']
        source_cells = {(xx, y, z): k + 1 for x, y, z, n, k in original['voxel']['runs']
                        for xx in range(x, x + n)}
        expected_controller = derive_site_controller(source_metadata, source_palette, source_cells, d['sourceSha256'])
        assert d['metadata'].get('productionSiteController') == expected_controller, (d['id'], 'derived controller mismatch')
        if expected_controller:
            assert d['metadata']['productionInteractionApproaches'] == [controller_approach(expected_controller, source_palette, source_cells)]
            controller = next(n for n in d['metadata']['productionNodeBindings'] if n['id'] == 'site-puzzle-controller')
            assert controller['role'] == 'control' and controller['after'] == expected_controller['block']
            at = tuple(controller['at'])
            assert source_palette[source_cells.get(at, 0)] == 'minecraft:air#0'
            derived = expected_controller['productionDerived']
            support = tuple(derived['supportAt'])
            standing = tuple(derived['standingAt'])
            assert actual_solids[support] == source_palette[source_cells[support]]
            standing_support = (standing[0], standing[1] - 1, standing[2])
            assert actual_solids[standing_support] == source_palette[source_cells[standing_support]]
            assert standing not in actual_solids and (standing[0], standing[1] + 1, standing[2]) not in actual_solids
            sums['derivedSiteControllers'] += 1
        if d['id'] not in ('fiction_expansion_project', 'forgotten_lake_court'):
            assert any(role == 'control' for _, role in runtime_node_owners(d['metadata']).values()), (d['id'], 'site puzzle has no actionable control')
            sums['ordinarySitePuzzleVariants'] += 1
            sums['reachableOrdinarySitePuzzleBoxes'] += sum(n.get('unlock') == 'puzzle-completed' and n.get('reference') == 'site-puzzle'
                                                         for n in d['metadata'].get('lootPlan7', []))
        if d['id'] == 'fiction_expansion_project':
            assert len(d['metadata']['productionEngineering']) == 8
            assert len(d['metadata']['testimonyPedestals']) == 38
        sums['solids'] += d['solidCount']
        sums['prefabs'] += 1
        sums['authoredSolids'] += count
        sums['nodeNormalizations'] += len(d['metadata']['productionNodeChanges'])
        sums['clearanceCorrections'] += len(d['metadata']['productionGeometryChanges'])
        sums['air'] += d['airCount']
        sums['slices'] += len(d['slices'])
        for key in ('spawns', 'rooms', 'shapeMechanisms', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7',
                    'navigationHints', 'interactionApproaches', 'productionInteractionApproaches'):
            sums[key] += len(d['metadata'].get(key, []))
    assert sums['simpleSceneVariants'] == len(active)
    assert sums['prefabs'] == (len(active) if options.active_only else 268)
    print(json.dumps({'passed': True, **sums}))


if __name__ == '__main__':
    main()
