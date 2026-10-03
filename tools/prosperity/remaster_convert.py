"""Deterministically compile authored R7 snapshots into chunk-addressable assets."""
import argparse
import gzip
import hashlib
import json
import math
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = next(p for p in (ROOT / 'plan').iterdir() if (p / 'preview/prefabs7').is_dir())
OUT = ROOT / 'src/main/resources/assets/gtsr/remaster'
PUZZLES = {}
ENGINEERING = []
INTERACTIVE = {('gtsr:' + b['id'] if ':' not in b['id'] else b['id'])
               for b in json.loads((OUT / 'blocks.json').read_text(encoding='utf8'))['blocks']
               if b['category'] != 'decoration'} | {'gtsr:draft_notice_board'}


def write_json(path, value, compressed=False):
    raw = json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(raw, mtime=0) if compressed else raw)
    return hashlib.sha256(raw).hexdigest()


def snapshot(path):
    text = path.read_text(encoding='utf8')
    return json.loads(text.split(']=', 1)[1].rstrip(';\r\n'))


def official_runtime_node_records(metadata):
    """Preserve the existing formal ownership order, independent of ambient derivation."""
    if metadata.get('nativeScene'):
        # These are source-derived annotations. The native encounter owns actual tiles and actors.
        return []
    records = [(n, n['role']) for n in metadata.get('nodes', [])
               if n.get('role') in ('control', 'memory', 'testimony')]
    for field, role in (('lootPlan7', 'chest'), ('spawnerPlan', 'spawner'),
                        ('puzzleObjects7', 'puzzle-object'), ('testimonyPedestals', 'testimony'),
                        ('navigationHints', 'memory')):
        records += [(n, role) for n in metadata.get(field, [])]
    if metadata.get('productionSiteController'):
        records.append((metadata['productionSiteController'], 'control'))
    for shape in metadata.get('shapeMechanisms', []):
        at = shape['control']
        records.append(({'id': 'shape:' + shape['id'], 'x': at[0], 'y': at[1], 'z': at[2]}, 'shape'))
    return records


def runtime_node_records(metadata):
    """Ambient records never override a formal coordinate (formal records come last)."""
    official = official_runtime_node_records(metadata)
    occupied = {tuple(int(n[k]) for k in ('x', 'y', 'z')) for n, _ in official}
    return [(n, 'ambient-notice') for n in metadata.get('productionAmbientNotices', [])
            if tuple(int(n[k]) for k in ('x', 'y', 'z')) not in occupied] + official


def runtime_node_owners(metadata):
    return {tuple(int(n[k]) for k in ('x', 'y', 'z')): (n, role)
            for n, role in runtime_node_records(metadata)}


def official_runtime_node_owners(metadata):
    return {tuple(int(n[k]) for k in ('x', 'y', 'z')): (n, role)
            for n, role in official_runtime_node_records(metadata)}


def derive_ambient_notices(metadata, source_palette, source_cells, palette, cells):
    """Bind only surviving authored notices; never create geometry or story evidence."""
    if metadata.get('combatOnly'):
        return []
    owners = official_runtime_node_owners(metadata)
    if metadata.get('variant') == 0 and metadata['id'] in ('fallen_foundry', 'subsided_factory', 'forgotten_lake_court'):
        ids = {'fallen_foundry': ('control-0', 'control-2'),
               'subsided_factory': ('factory-stage-0', 'factory-stage-3'),
               'forgotten_lake_court': ('entrance-story-board',)}[metadata['id']]
        targets = [(at, node) for at, (node, _) in owners.items() if node.get('id') in ids]
        notices = []
        for at, key in sorted(source_cells.items()):
            material = source_palette[key]
            if material.split('#')[0] not in INTERACTIVE or at in owners or palette[cells.get(at, 0)] != material:
                continue
            notice = material.split('#')[0] == 'gtsr:draft_notice_board'
            text = ('沿灯光和楼梯清除八室守卫，再从西侧面对缄王。' if metadata['id'] == 'forgotten_lake_court'
                    else '沿灯光和楼梯前进，清除守卫并确认两处联锁后挑战Boss。')
            if not notice:
                text = '此处为设备陈列，无需操作。' + text
            node = {'id': 'ambient:' + ':'.join(map(str, at)), 'role': 'ambient-notice',
                    'x': at[0], 'y': at[1], 'z': at[2], 'block': material,
                    'label': '行进方向' if notice else '设备陈列', 'text': text,
                    'readOnly': True, 'simpleSceneAmbient': True}
            if targets and metadata['id'] != 'forgotten_lake_court':
                target_at, target = min(targets, key=lambda item: (math.dist(at, item[0]), item[1]['id']))
                node.update(guideTarget=list(target_at), guideTargetNode=target['id'])
            notices.append(node)
        return notices
    reviewed = {a.get('node') for a in metadata.get('interactionApproaches', [])
                + metadata.get('productionInteractionApproaches', [])
                if a.get('headClearance') and a.get('rayUnobstructed')}
    targets = [(at, n) for at, (n, role) in owners.items()
               if n.get('id') == 'entrance-story-board' or n.get('id') in reviewed]
    detail = metadata.get('exteriorDetail7', {})
    sites = [(index, s) for index, s in enumerate(detail.get('sites', []))
             if len(s.get('location', [])) == 3 and s.get('purpose')
             and s.get('actualDecorCount', 0) > 0
             and any(palette[cells.get(tuple(support.get('at', [])), 0)] != 'minecraft:air#0'
                     for support in s.get('supports', []))]
    if not targets or not sites or not detail.get('erosionSource'):
        return []
    notices = []
    for at, key in sorted(source_cells.items()):
        material = source_palette[key]
        if (material.split('#')[0] != 'gtsr:draft_notice_board' or at in owners
                or palette[cells.get(at, 0)] != material):
            continue
        index, site = min(sites, key=lambda item: (math.dist(at, item[1]['location']), item[0]))
        if math.dist(at, site['location']) > 12:
            continue
        target_at, target = min(targets, key=lambda item: (
            item[1].get('id') != 'entrance-story-board', math.dist(at, item[0]), item[1]['id']))
        label = target.get('label') or ('入口记录' if target['id'] == 'entrance-story-board' else '现场记录')
        notices.append({'id': 'ambient:' + ':'.join(map(str, at)), 'role': 'ambient-notice',
                        'x': at[0], 'y': at[1], 'z': at[2], 'block': material,
                        'label': '现场封锁告示',
                        'text': f"本处现场标记：{site['purpose']}。侵蚀来源：{detail['erosionSource']}。"
                                f"请沿保留通路前往{label}读取正式记录；此告示仅说明现场，不解除封锁。",
                        'guideTarget': list(target_at), 'guideTargetNode': target['id'],
                        'exteriorSiteIndex': index, 'exteriorSiteAt': site['location']})
    return notices


def derive_site_controller(metadata, palette, cells, source_sha):
    """Add an independent console only when the authored site puzzle lacks one.

    Search existing supported air next to reviewed interactions, without replacing
    records, floors, route clearance, actor space or any future mechanism voxel.
    No platform or fallback geometry is synthesized when placement is impossible.
    """
    puzzle = metadata.get('productionPuzzle', {})
    if metadata['id'] in ('fiction_expansion_project', 'forgotten_lake_court'):
        return None
    if not puzzle.get('fields') or not puzzle.get('target'):
        return None
    if any(role == 'control' for _, role in official_runtime_node_owners(metadata).values()):
        return None
    reserved = set()
    def reserve(value):
        if isinstance(value, dict):
            if all(isinstance(value.get(k), (int, float)) for k in ('x', 'y', 'z')):
                reserved.add(tuple(int(value[k]) for k in ('x', 'y', 'z')))
            for key, item in value.items():
                if key in ('at', 'control', 'position', 'removedVoxels', 'delta', 'deltas', 'states', 'voxels', 'blocks'):
                    reserve(item)
                elif isinstance(item, dict):
                    reserve(item)
        elif isinstance(value, list):
            if len(value) >= 3 and all(isinstance(i, (int, float)) for i in value[:3]):
                reserved.add(tuple(int(i) for i in value[:3]))
            else:
                for item in value:
                    reserve(item)
    for key in ('nodes', 'shapeMechanisms', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7',
                'testimonyPedestals', 'navigationHints', 'outdoorFixtures', 'damageManifest'):
        reserve(metadata.get(key, []))
    actors = [(a, [a['x'], a['y'], a['z']], a.get('clearance', {})) for a in metadata.get('spawns', [])]
    actors += [(a, a['spawnZone']['center'], a['spawnZone'].get('clearance', {}))
               for a in metadata.get('spawnerPlan', [])]
    for _, at, clearance in actors:
        for x in range(at[0] - int(clearance.get('width', 1)) // 2, at[0] + int(clearance.get('width', 1)) // 2 + 1):
            for z in range(at[2] - int(clearance.get('depth', 1)) // 2, at[2] + int(clearance.get('depth', 1)) // 2 + 1):
                for y in range(at[1], at[1] + int(clearance.get('height', 3))):
                    reserved.add((x, y, z))
    routes = [(r['points'], r.get('width', 5), 4) for r in metadata.get('stairRoutes', [])]
    routes += [(r, 3, 3) for r in metadata.get('routes', [])]
    for entry in (metadata.get('entranceLogic7'), metadata.get('terrain', {})):
        if entry and (entry.get('points') or entry.get('entrance')):
            routes.append((entry.get('points') or entry['entrance'], 5, 4))
    route_space = set()
    route_carve = set()
    for points, width, height in routes:
        for a, b in zip(points, points[1:]):
            length = max(abs(b[0] - a[0]), abs(b[2] - a[2]), 1)
            along_x = abs(b[0] - a[0]) >= abs(b[2] - a[2])
            for i in range(length + 1):
                q = [math.floor(a[j] + (b[j] - a[j]) * i / length + .5) for j in range(3)]
                for side in range(-(width // 2), width // 2 + 1):
                    for dy in range(height + 1):
                        position = (q[0] + (0 if along_x else side), q[1] + dy,
                                    q[2] + (side if along_x else 0))
                        route_space.add(position)
                        if dy:
                            route_carve.add(position)
    def material(at):
        return palette[cells.get(at, 0)].split('#')[0]
    def air(at):
        return material(at) == 'minecraft:air' and at not in reserved
    def floor(at):
        key = material(at)
        return (at not in reserved and at not in route_carve and any(t in key for t in
                ('masonry', 'paving', 'tiles', 'pressed_brick', 'riveted_plate', ':stone', ':brick'))
                and not any(t in key for t in ('stairs', 'slab', 'wood', 'leaf', 'leaves', 'log', 'pollution')))
    interaction_standing = set()
    for approach in metadata.get('interactionApproaches', []):
        feet = approach.get('feet')
        if feet:
            interaction_standing.update((tuple(feet), (feet[0], feet[1] + 1, feet[2])))
        floor_at = approach.get('floor', {}).get('at')
        if floor_at:
            interaction_standing.add(tuple(floor_at))
        eye, target = approach.get('eye'), approach.get('target')
        if eye and target:
            steps = max(1, math.ceil(math.dist(eye, target) * 20))
            for step in range(steps):
                interaction_standing.add(tuple(math.floor(eye[i] + (target[i] - eye[i]) * step / steps) for i in range(3)))
    for position in reserved:
        for sx, sz in ((-1, 0), (0, -1), (0, 1), (1, 0)):
            for dy in (-1, 0):
                feet = (position[0] + sx, position[1] + dy, position[2] + sz)
                head = (feet[0], feet[1] + 1, feet[2])
                support = (feet[0], feet[1] - 1, feet[2])
                if floor(support) and air(feet) and air(head):
                    interaction_standing.update((feet, head))
    anchors = [(n, role) for n, role in official_runtime_node_owners(metadata).values()
               if role in ('memory', 'control', 'puzzle-object') and not n.get('navigationHint')]
    anchors.sort(key=lambda pair: (0 if pair[0]['id'].startswith('objective-') else 1, pair[0]['id']))
    offsets = sorted(((dx, dy, dz) for dx in range(-8, 9) for dz in range(-8, 9)
                      for dy in range(-3, 4) if 1 <= abs(dx) + abs(dz) <= 8),
                     key=lambda p: (abs(p[0]) + abs(p[2]) + 2 * abs(p[1]), abs(p[1]), p))
    for anchor, _ in anchors:
        base = tuple(int(anchor[k]) for k in ('x', 'y', 'z'))
        for dx, dy, dz in offsets:
            at = (base[0] + dx, base[1] + dy, base[2] + dz)
            support = (at[0], at[1] - 1, at[2])
            if at in route_space or at in interaction_standing or not floor(support) or not air(at) or not air((at[0], at[1] + 1, at[2])):
                continue
            for sx, sz in ((-1, 0), (0, -1), (0, 1), (1, 0)):
                stand = (at[0] + sx, at[1], at[2] + sz)
                if not floor((stand[0], stand[1] - 1, stand[2])) or not air(stand) or not air((stand[0], stand[1] + 1, stand[2])):
                    continue
                return {'id': 'site-puzzle-controller', 'role': 'control', 'block': 'gtsr:draft_pressure_console#0',
                        'x': at[0], 'y': at[1], 'z': at[2], 'label': puzzle.get('title', '现场工序核验'),
                        'text': puzzle.get('sceneClue', '') + '；核对原始记录与现场设备后，在此调节参数并核验工序。',
                        'productionDerived': {'format': 'gtsr:site-controller/v1', 'sourceSha256': source_sha,
                            'anchorId': anchor['id'], 'anchorAt': list(base), 'supportAt': list(support),
                            'standingAt': list(stand), 'searchRadius': 8}}
    raise ValueError(f"{metadata['id']}-v{metadata['variant']}: no supported, unobstructed site controller position near reviewed interactions")


def normalize_nodes(metadata, palette, cells, source_sha=''):
    """Geometry must install the same physical node blocks as the server authority."""
    controller = derive_site_controller(metadata, palette, cells, source_sha)
    if controller:
        metadata['productionSiteController'] = controller
        metadata['productionInteractionApproaches'] = [controller_approach(controller, palette, cells)]
    records = official_runtime_node_records(metadata)
    changes = []
    bindings = {}
    for node, role in records:
        at = tuple(int(node[k]) for k in ('x', 'y', 'z'))
        previous = palette[cells.get(at, 0)]
        key = node.get('block') or previous
        if ':' not in key:
            key = 'gtsr:' + key
        if '#' not in key:
            key += '#0'
        if role == 'chest':
            key = 'gtsr:SealedChest#2'
        elif metadata.get('combatOnly') and role in ('memory', 'testimony'):
            # Narrative units use a registered console, never an authored notice board.
            key = node.get('block', 'gtsr:draft_pressure_console#0')
            if key.split('#')[0] == 'gtsr:draft_notice_board':
                raise ValueError('Notice boards are not allowed in combat-only scenes')
        elif key.split('#')[0] not in INTERACTIVE:
            key = 'gtsr:draft_notice_board#0' if role in ('memory', 'testimony') else (
                'gtsr:draft7_index_frame#0' if role == 'puzzle-object' else
                'gtsr:draft6_spawner_stable#0' if role == 'spawner' else 'gtsr:draft_pressure_console#0')
        node['block'] = key
        if key not in palette:
            palette.append(key)
        cells[at] = palette.index(key)
        record = {'id': node['id'], 'role': role, 'at': list(at), 'before': previous, 'after': key}
        bindings[at] = record
        if previous != key:
            changes.append(record)
    metadata['productionNodeBindings'] = list(bindings.values())
    return changes


def controller_approach(controller, palette, cells):
    feet = controller['productionDerived']['standingAt']
    floor_at = [feet[0], feet[1] - 1, feet[2]]
    eye = [feet[0] + .5, feet[1] + 1.62, feet[2] + .5]
    target = [controller[k] + .5 for k in ('x', 'y', 'z')]
    return {'node': controller['id'], 'role': 'control', 'feet': feet, 'eye': eye,
            'floor': {'at': floor_at, 'material': palette[cells[tuple(floor_at)]]},
            'target': target, 'targetBlock': controller['block'], 'distance': round(math.dist(eye, target), 3),
            'headClearance': True, 'rayUnobstructed': True,
            'sourceSha256': controller['productionDerived']['sourceSha256']}


def derive_surface_entrance(metadata, source_palette, source_cells, palette, cells, source_sha):
    """Anchor only the authored first entrance platform, preserving all source metadata."""
    if (metadata.get('surfaceEntrance') or metadata.get('terrain', {}).get('buried') == 'full'
            or metadata['id'] in ('fiction_expansion_project', 'prosperity_city_full', 'zenith_tree')):
        return None
    routes = metadata.get('routes', [])
    if not routes or not routes[0]:
        return None
    original = routes[0][0]
    review = metadata.get('structureDesignReview', {})
    reviewed = next((r for r in review.get('stairFunctionalAccessRoutes', [])
                     + review.get('stairExceptionRoutes', [])
                     if r.get('originalRouteKey') == 'routes:0' and r.get('actualPoints')), None)
    first = reviewed['actualPoints'][0] if reviewed else original
    def material(at, source=True):
        return (source_palette if source else palette)[(source_cells if source else cells).get(at, 0)]
    def paving(at):
        return material(at).split('#')[0] == 'gtsr:ruins_mossroot_paving'
    def clear(at):
        return all(material((at[0], at[1] + h, at[2]), source).split('#')[0] == 'minecraft:air'
                   for h in (1, 2) for source in (True, False))
    primary = next(((first[0], first[1] + dy, first[2])
                    for dy in (0, -1)
                    if paving((first[0], first[1] + dy, first[2]))), None)
    if primary is None:
        return None
    # Adjacent lane selection never leaves the original small, connected paving platform.
    paths = {primary: [list(primary)]}
    queue = [primary]
    for at in queue:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (at[0] + dx, primary[1], at[2] + dz)
            if (q not in paths and abs(q[0] - primary[0]) <= 2
                    and abs(q[2] - primary[2]) <= 2 and paving(q)):
                paths[q] = paths[at] + [list(q)]
                queue.append(q)
    candidates = [primary] + [(primary[0] + dx, primary[1], primary[2] + dz)
                             for dist in (1, 2)
                             for dx, dz in ((dist, 0), (-dist, 0), (0, dist), (0, -dist),
                                            (dist, dist), (-dist, dist), (dist, -dist), (-dist, -dist))]
    usable = [at for at in candidates if at in paths and clear(at)
              and material(at, False) == material(at)]
    def clear_neighbors(at):
        return [(at[0] + dx, at[1], at[2] + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                if paving((at[0] + dx, at[1], at[2] + dz)) and clear((at[0] + dx, at[1], at[2] + dz))]
    # A blocked first point may sit at a railing corner. Prefer the widest clear
    # lane on its own initial platform, keeping the authored point when usable.
    actual = (primary if primary in usable else max(usable, key=lambda at: len(clear_neighbors(at)))) if usable else None
    if actual is None:
        raise ValueError(f"{metadata['id']}-v{metadata['variant']}: authored entrance paving has no clear supported lane")
    return {'x': actual[0], 'z': actual[2], 'topY': actual[1],
            'productionDerived': {'format': 'gtsr:surface-entrance/v1', 'sourceSha256': source_sha,
                'originalRouteKey': 'routes:0', 'originalFirstPoint': original,
                'reviewedFirstPoint': first, 'actualSourceFloor': list(actual),
                'sourceFloorMaterial': material(actual), 'platformPath': paths[actual], 'searchRadius': 2,
                'laneClearNeighbors': [list(at) for at in clear_neighbors(actual)],
                'feet': [actual[0], actual[1] + 1, actual[2]],
                'head': [actual[0], actual[1] + 2, actual[2]],
                'clearanceRay': [[actual[0] + .5, actual[1] + 1.01, actual[2] + .5],
                                 [actual[0] + .5, actual[1] + 2.8, actual[2] + .5]]}}


def entry_apron_envelope(metadata, source_palette, source_cells, palette, cells, source_sha):
    """Finite source occupancy contract; absence alone permits a new write (AIR is protected)."""
    entry = metadata.get('surfaceEntrance') or metadata.get('productionSurfaceEntrance')
    if (not entry or metadata.get('terrain', {}).get('buried') == 'full'
            or metadata['id'] in ('fiction_expansion_project', 'prosperity_city_full', 'zenith_tree')):
        return None
    ex, ez, fy = entry['x'], entry['z'], entry['topY']
    protected = {}
    for mapping, materials in ((source_cells, source_palette), (cells, palette)):
        for (x, y, z), k in mapping.items():
            if abs(x - ex) > 9 or abs(z - ez) > 9:
                continue
            block = materials[k].split('#')[0]
            kind = 0 if block == 'minecraft:air' else 1
            protected[x, y, z] = max(kind, protected.get((x, y, z), 0))
    for at in protected:
        if (at in source_cells and at in cells
                and source_palette[source_cells[at]] == palette[cells[at]]
                and palette[cells[at]].split('#')[0] == 'gtsr:ruins_mossroot_paving'):
            protected[at] = 2
        elif (at in source_cells and at in cells
              and source_palette[source_cells[at]] == palette[cells[at]]
              and palette[cells[at]].split('#')[0] == 'gtsr:ruins_mossroot_paving_stairs'):
            meta = int(palette[cells[at]].split('#')[1])
            if 0 <= meta < 4:
                protected[at] = 10 + meta
    # Actionable bindings also reserve their otherwise implicit source cells.
    for node, _ in runtime_node_records(metadata) + [(n, n.get('role')) for n in metadata.get('nodes', [])]:
        if all(k in node for k in ('x', 'y', 'z')):
            at = tuple(int(node[k]) for k in ('x', 'y', 'z'))
            if abs(at[0] - ex) <= 9 and abs(at[2] - ez) <= 9:
                protected.setdefault(at, 1)
    runs = []
    for (x, y, z), kind in sorted(protected.items(), key=lambda kv: (kv[0][0], kv[0][2], kv[0][1])):
        if runs and runs[-1][0] == x and runs[-1][1] == z and runs[-1][2] + runs[-1][3] == y and runs[-1][4] == kind:
            runs[-1][3] += 1
        else:
            runs.append([x, z, y, 1, kind])
    return {'version': 1, 'sourceSha256': source_sha, 'entry': [ex, fy, ez],
            'floor': 'gtsr:ruins_mossroot_paving', 'stairs': 'gtsr:ruins_mossroot_paving_stairs',
            'runs': runs}


def compile_one(path):
    d = snapshot(path)
    m = d['metadata']
    m['productionPuzzle'] = PUZZLES[m['id']]
    active_scene = m.get('variant') == 0 and m['id'] in ('fallen_foundry', 'subsided_factory', 'forgotten_lake_court')
    simple = active_scene and not m.get('combatOnly')
    if active_scene and m.get('combatOnly'):
        for key in ('productionPuzzle', 'productionSimpleScene', 'productionSiteController',
                    'productionAmbientNotices', 'productionInteractionApproaches', 'entryApronEnvelope'):
            m.pop(key, None)
        forbidden = {'control', 'shape', 'puzzle-object', 'ambient-notice'}
        if any(n.get('role') in forbidden for n in m.get('nodes', [])):
            raise ValueError('Combat-only scenes cannot contain puzzle nodes')
        if any(m.get(k) for k in ('shapeMechanisms', 'puzzleObjects7', 'navigationHints')):
            raise ValueError('Combat-only scenes cannot contain puzzle/notice plans')
    if simple:
        controls = {'fallen_foundry': ['control-0', 'control-2'],
                    'subsided_factory': ['factory-stage-0', 'factory-stage-3'],
                    'forgotten_lake_court': []}[m['id']]
        m['productionSimpleScene'] = {'version': 1, 'controls': controls,
                                     'guardDeathRequired': True, 'legacyCompatible': True}
        m['productionPuzzle'] = {'kind': 'simple-scene', 'title': '现场设备', 'fields': [],
                                'target': [], 'order': [], 'explain': '沿灯光和楼梯前进；设备只需点击一次。'}
        for shape in m.get('shapeMechanisms', []):
            shape['fields'], shape['answer'], shape['order'] = [], [], []
            shape['clue'] = '可选侧门：点击打开。主路不依赖此门。'

    if m['id'] == 'fiction_expansion_project':
        m['productionEngineering'] = ENGINEERING
    palette = ['minecraft:air#0'] + [
        'gtsr:SealedChest#2' if key.split('#')[0] == 'gtsr:draft7_seal_chest' else key
        for key in d['voxel']['palette']]
    cells = {}
    for x, y, z, n, k in d['voxel']['runs']:
        for xx in range(x, x + n):
            cells[xx, y, z] = k + 1
    source_palette, source_cells = list(palette), dict(cells)
    node_changes = normalize_nodes(m, palette, cells, hashlib.sha256(path.read_bytes()).hexdigest())
    geometry_changes = []
    protected = {tuple(n['at']) for n in m['productionNodeBindings']}
    for node in m.get('nodes', []):
        if all(k in node for k in ('x', 'y', 'z')):
            at = tuple(int(node[k]) for k in ('x', 'y', 'z'))
            actor_only = node.get('role') in ('elite', 'guard', 'boss') and node.get('code') and not node.get('block')
            # Actor markers are metadata, not tiles. Their implicit air must carve
            # native geology; existing source solids and real node bindings remain protected.
            if not actor_only or (at in cells and palette[cells[at]] != 'minecraft:air#0'):
                protected.add(at)
    def protect_mechanism(value):
        if isinstance(value, dict):
            for k, v in value.items():
                if k in ('at', 'control') and isinstance(v, list) and len(v) == 3:
                    protected.add(tuple(v))
                elif k in ('delta', 'deltas', 'states', 'voxels', 'blocks'):
                    protect_mechanism(v)
        elif isinstance(value, list):
            if len(value) >= 3 and all(isinstance(v, (int, float)) for v in value[:3]):
                protected.add(tuple(int(v) for v in value[:3]))
            else:
                for v in value:
                    protect_mechanism(v)
    protect_mechanism(m.get('shapeMechanisms', []))

    def carve(x, y, z, explicit=False):
        at = (x, y, z)
        if at in protected:
            return
        if explicit and at in cells and cells[at]:
            key = palette[cells[at]].lower()
            # Authored machinery/furniture stay intact. Only construction obstructing
            # a declared passage can be removed by synthesized clearance.
            structural = any(token in key for token in ('wall', 'brick', 'casing', 'stone', 'debris', 'girder'))
            shell = any((x in (r['x'], r['x'] + r['w'] - 1)
                         or z in (r['z'], r['z'] + r['d'] - 1)
                         or y >= r['y'] + r['h'])
                        and r['x'] <= x < r['x'] + r['w']
                        and r['z'] <= z < r['z'] + r['d']
                        and r['y'] <= y <= r['y'] + r['h']
                        for r in m.get('rooms', []))
            if not structural or shell:
                return
            geometry_changes.append({'at': list(at), 'before': palette[cells[at]], 'reason': 'declared-clearance'})
            cells[at] = 0
        else:
            cells.setdefault(at, 0)

    # Preserve every authored solid; record only the formerly implicit negative space.
    # Native snapshots record their own AIR; never invent clearance around annotation-only routes.
    for r in ([] if m.get('nativeScene') else m.get('rooms', [])):
        foot = 1 if (simple or m.get('combatOnly')) and m['id'] != 'forgotten_lake_court' else 2
        for y in range(r['y'] + foot, r['y'] + r['h'] + 1):
            for z in range(r['z'] + 1, r['z'] + r['d'] - 1):
                for x in range(r['x'] + 1, r['x'] + r['w'] - 1):
                    carve(x, y, z)
    routes = [] if m.get('nativeScene') else [(r['points'], r.get('width', 5), 4) for r in m.get('stairRoutes', [])]
    if not m.get('nativeScene'):
        routes += [(r, 3, 3) for r in m.get('routes', [])]
    for entry in ([] if m.get('nativeScene') else [m.get('entranceLogic7'), m.get('terrain', {})]):
        if entry and (entry.get('points') or entry.get('entrance')):
            routes.append((entry.get('points') or entry['entrance'], 5, 4))
    for points, width, height in routes:
        for a, b in zip(points, points[1:]):
            n = max(abs(b[0] - a[0]), abs(b[2] - a[2]), 1)
            along_x = abs(b[0] - a[0]) >= abs(b[2] - a[2])
            for i in range(n + 1):
                q = [math.floor(a[j] + (b[j] - a[j]) * i / n + .5) for j in range(3)]
                for side in range(-(width // 2), width // 2 + 1):
                    for dy in range(1, height + 1):
                        carve(q[0] + (0 if along_x else side), q[1] + dy,
                              q[2] + (side if along_x else 0), explicit=True)
    # Exact actor birth clearance is also explicit air, including airborne guards.
    actors = [] if m.get('nativeScene') else [(s, [s['x'], s['y'], s['z']], s.get('clearance', {})) for s in m.get('spawns', [])]
    if not m.get('nativeScene'):
        actors += [(s, s['spawnZone']['center'], s['spawnZone'].get('clearance', {}))
                   for s in m.get('spawnerPlan', [])]
    for actor, at, clearance in actors:
        for x in range(at[0] - int(clearance.get('width', 1)) // 2,
                       at[0] + int(clearance.get('width', 1)) // 2 + 1):
            for z in range(at[2] - int(clearance.get('depth', 1)) // 2,
                           at[2] + int(clearance.get('depth', 1)) // 2 + 1):
                for y in range(at[1], at[1] + int(clearance.get('height', 3))):
                    carve(x, y, z, explicit=True)
    for damage in m.get('damageManifest', []):
        for p in damage.get('removedVoxels', []):
            if tuple(p[:3]) not in protected:
                if cells.get(tuple(p[:3]), 0):
                    geometry_changes.append({'at': p[:3], 'before': palette[cells[tuple(p[:3])]], 'reason': 'authored-damage'})
                cells[tuple(p[:3])] = 0
    m['productionGeometryChanges'] = geometry_changes
    m['productionNodeChanges'] = node_changes
    solid_count = sum(1 for k in cells.values() if k != 0 and palette[k] != 'minecraft:air#0')
    # Normalize authored air palette entries so accounting and runtime agree.
    cells = {at: (0 if palette[k] == 'minecraft:air#0' else k) for at, k in cells.items()}
    entrance = derive_surface_entrance(m, source_palette, source_cells, palette, cells,
                                      hashlib.sha256(path.read_bytes()).hexdigest())
    if entrance:
        m['productionSurfaceEntrance'] = entrance
    envelope = None if m.get('combatOnly') else entry_apron_envelope(
        m, source_palette, source_cells, palette, cells, hashlib.sha256(path.read_bytes()).hexdigest())
    if envelope:
        m['entryApronEnvelope'] = envelope
    # Derive after formal normalization and all clearance/damage, without changing voxels or bindings.
    m['productionAmbientNotices'] = derive_ambient_notices(m, source_palette, source_cells, palette, cells)
    coords = list(cells)
    # Include actionable metadata coordinates in the advertised placement envelope.
    def coordinates(value):
        if isinstance(value, dict):
            if all(isinstance(value.get(k), (int, float)) for k in ('x', 'y', 'z')):
                coords.append(tuple(math.floor(value[k]) for k in ('x', 'y', 'z')))
            for k, v in value.items():
                if k == 'structureDesignReview':
                    # Historical removal/detail diagnostics are evidence, not active geometry.
                    continue
                if k in ('at', 'control', 'anchor', 'center', 'min', 'max', 'home') and isinstance(v, list) and len(v) == 3:
                    if all(isinstance(i, (int, float)) for i in v):
                        coords.append(tuple(math.floor(i) for i in v))
                elif k in ('points', 'routes', 'entrance') and isinstance(v, list):
                    for p in v:
                        if isinstance(p, list) and len(p) == 3 and all(isinstance(i, (int, float)) for i in p):
                            coords.append(tuple(math.floor(i) for i in p))
                coordinates(v)
        elif isinstance(value, list):
            for v in value:
                coordinates(v)
    coordinates(m)
    lo = [min(p[i] for p in coords) for i in range(3)]
    hi = [max(p[i] for p in coords) for i in range(3)]
    if hi[1] - lo[1] + 1 > 256:
        raise ValueError(f'{path.name}: vertical extent exceeds world height')
    grouped = defaultdict(list)
    for (x, y, z), k in sorted(cells.items(), key=lambda p: (p[0][1], p[0][2], p[0][0])):
        rows = grouped[x // 16, z // 16]
        if rows and rows[-1][1] == y and rows[-1][2] == z and rows[-1][0] + rows[-1][3] == x and rows[-1][4] == k:
            rows[-1][3] += 1
        else:
            rows.append([x, y, z, 1, k])
    key = f"{m['id']}-v{m['variant']}"
    slices = []
    for (cx, cz), runs in sorted(grouped.items()):
        name = f'prefabs/{key}/{cx}_{cz}.json.gz'
        digest = write_json(OUT / name, runs, True)
        slices.append({'x': cx, 'z': cz, 'file': name, 'sha256': digest})
    record = {'schemaVersion': 7, 'id': m['id'], 'variant': m['variant'], 'min': lo, 'max': hi,
              'extent': [hi[i] - lo[i] + 1 for i in range(3)], 'nominal': m['nominal'],
              'yMin': lo[1], 'yMax': hi[1], 'palette': palette, 'metadata': m, 'slices': slices,
              'solidCount': solid_count, 'airCount': len(cells) - solid_count,
              'sourceSha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    manifest = f'prefabs/{key}.json.gz'
    digest = write_json(OUT / manifest, record, True)
    return {k: record[k] for k in ('id', 'variant', 'min', 'max', 'extent', 'nominal', 'yMin', 'yMax',
                                  'solidCount', 'airCount', 'sourceSha256')} | {'file': manifest, 'sha256': digest,
        'placement': {k: m[k] for k in ('terrain', 'surfaceEntrance', 'productionSurfaceEntrance', 'rooms', 'entryApronEnvelope') if k in m}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prefab', help='Compile one authored id-vN snapshot and replace its catalog entry.')
    args = parser.parse_args()
    allowed = {'fallen_foundry-v0', 'subsided_factory-v0', 'forgotten_lake_court-v0'}
    snapshots = sorted(p for p in (SOURCE / 'preview/prefabs7').glob('*.js') if p.stem in allowed)
    if args.prefab:
        snapshots = [p for p in snapshots if p.stem == args.prefab]
        if len(snapshots) != 1:
            parser.error('Unknown authored snapshot: ' + args.prefab)
    # Read the existing declaration scripts in an isolated JS context, never execute authoring tools.
    js = r"""const fs=require('fs'),vm=require('vm'),p=require('path'),s={window:{}};vm.createContext(s);
for(const n of ['catalog','catalog5','textures','geometry','blocks4','story4','blocks5','blocks6','blocks7','puzzles4'])
vm.runInContext(fs.readFileSync(p.join(process.argv[1],'preview',n+'.js'),'utf8'),s);
let specs={};for(const m of s.window.REMASTER_CATALOG.structures)specs[m.id]=s.window.RemasterPuzzles4.spec(m);
const engineering=fs.readFileSync(p.join(process.argv[1],'preview','experience5.js'),'utf8');
vm.runInContext('window.STATIC_CHAPTERS='+engineering.match(/const chapters=(\[[\s\S]*?\n\]);/)[1],s);
let t={};for(const [k,v]of Object.entries(s.window.REMASTER_TEXTURES))if(k.includes(':draft')){
const q={...v};delete q.texture;delete q.faces;t[k]={...q,faceNames:Object.keys(v.faces||{})};}
process.stdout.write(JSON.stringify({structures:s.window.REMASTER_CATALOG.structures,blocks:s.window.REMASTER_BLOCKS,materials:t,
specs,chapters:s.window.STATIC_CHAPTERS,story:s.window.REMASTER_STORY,sets:s.window.RemasterPuzzles4.sets}));"""
    declarations = json.loads(subprocess.check_output(['node', '-e', js, str(SOURCE)], encoding='utf8'))
    global PUZZLES, ENGINEERING
    PUZZLES = declarations['specs']
    ENGINEERING = declarations['chapters']
    write_json(OUT / 'blocks.json', {'schemaVersion': 7, 'blocks': declarations['blocks'],
                                   'materials': declarations['materials']})
    records = []
    for path in snapshots:
        records.append(compile_one(path))
        if len(records) % 30 == 0:
            print(f'compiled {len(records)}', flush=True)
    if args.prefab:
        previous = json.loads((OUT / 'catalog.json').read_text(encoding='utf8'))['prefabs']
        replacement = records[0]
        identity = (replacement['id'], replacement['variant'])
        if sum((r['id'], r['variant']) == identity for r in previous) != 1:
            raise ValueError('Partial compilation requires exactly one existing catalog identity: ' + str(identity))
        records = [replacement if (r['id'], r['variant']) == identity else r for r in previous]
    records = [r for r in records if f"{r['id']}-v{r['variant']}" in allowed]
    active = {r['id'] for r in records}
    mapping = [{k: m[k] for k in ('id', 'category', 'source')} for m in declarations['structures'] if m['id'] in active]
    write_json(OUT / 'catalog.json', {'schemaVersion': 7, 'structures': mapping, 'prefabs': records})
    used = set()
    for record in records:
        manifest = json.loads(gzip.decompress((OUT / record['file']).read_bytes()))
        used.update(key.split('#')[0] for key in manifest['palette'])
    write_json(OUT / 'blocks.json', {'schemaVersion': 7,
        'blocks': [b for b in declarations['blocks'] if ('gtsr:' + b['id'] if ':' not in b['id'] else b['id']) in used],
        'materials': {k: v for k, v in declarations['materials'].items() if k.split('#')[0] in used}})
    print(json.dumps({'prefabs': len(records), 'solidCount': sum(r['solidCount'] for r in records),
                      'airCount': sum(r['airCount'] for r in records),
                      'maxVerticalExtent': max(r['extent'][1] for r in records)}))


if __name__ == '__main__':
    main()
