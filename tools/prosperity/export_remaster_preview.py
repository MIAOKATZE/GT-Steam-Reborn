"""Export the production revision-seven chunk assets into the existing offline field archive."""
import base64
import gzip
import hashlib
import importlib.util
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/gtsr'
DEST = ROOT / 'plan/prosperity'


def rollout_policy():
    """Read the runtime's contained rollout, rather than maintaining a second list."""
    path = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterRollout.java'
    source = path.read_text(encoding='utf-8')
    active = re.search(r'Arrays\.asList\((.*?)\)', source, re.S)
    variant = re.search(r'int standardVariant\(String id\).*?return (\d+);', source, re.S)
    if not active or not variant:
        raise ValueError('Cannot resolve runtime rollout policy')
    ids = re.findall(r'"([a-z0-9_]+)"', active.group(1))
    if len(ids) != 3 or len(set(ids)) != 3:
        raise ValueError('Expected exactly three standard Boss scenes')
    return {'activeIds': ids, 'standardVariant': int(variant.group(1)),
            'status': 'refining', 'source': path.relative_to(ROOT).as_posix(),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def contained_index(models, policy, designs):
    active = []
    for scene_id in policy['activeIds']:
        model = next((dict(m) for m in models if m.get('productionId') == scene_id
                      and m.get('productionVariant') == policy['standardVariant']), None)
        if model is None:
            raise ValueError('Missing standard preview: ' + scene_id)
        model['name'] = re.sub(r' · 第七轮 \d+$', '', model['name']) + ' · 标准场景 v0'
        model['description'] = ['正在打磨的 Boss 标准场景；尚未完成游戏内验收。',
                                '本轮只保留三个标准场景；其他结构仅留设计介绍，暂缓并待后续大幅简化减量。',
                                '离线几何与真实材质供检查；地形接合及通行仍需游戏内验收。']
        model['reviewStatus'] = 'refining'
        if scene_id == 'forgotten_lake_court':
            model['description'].append('巨树王庭：沿真实树体通路进入八室，逐室击败守位，最终挑战缄王；战斗在游戏内实际进行。')
            sample = next((m for m in models if m.get('geometryMode') == 'production-royal-route-snapshot'), None)
            if sample:
                model['sampleSnapshots'] = [dict(sample, productionId=scene_id, reviewStatus='refining',
                                                description=list(sample.get('description', [])) + model['description'] + [
                                                    '巨树附属真实通路几何样本；不另计场景，尚未完成游戏内验收。'])]
            model['description'].append('作者预制与自然巨树通路快照分别查看；快照属于本场景的附属样本。')
        else:
            model['description'].append('紧凑场景：进入时展示结构名并解锁剧情；交互单元只读取剧情，无解谜或联锁操作。箱子分别直接开启、阅读剧情后开启、真实战斗结束后开启。')
        active.append(model)
    paused = [d for d in designs if d['id'] not in policy['activeIds']]
    return ('(function(w){const b=w.PROSPERITY_STRUCTURES;'
            'b.rollout=' + json.dumps(policy, ensure_ascii=False, separators=(',', ':')) + ';'
            'b.pausedDesigns=' + json.dumps(paused, ensure_ascii=False, separators=(',', ':')) + ';'
            'b.structures=' + json.dumps(active, ensure_ascii=False, separators=(',', ':')) + ';})(window);\n')


def dump(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')


def production_stair_textures():
    """Resolve exactly the registered stair bases used by the source-derived catalog."""
    spec = importlib.util.spec_from_file_location('prosperity_catalog_export', DEST / 'tools/catalog_export.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    result = {}
    for stair in module.architecture_stair_materials():
        blob = (ROOT / stair['assetSource']).read_bytes()
        material = {'block': 'gtsr:' + stair['registryName'], 'render': 'stairs',
                    'texture': 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii'),
                    'source': {'path': stair['assetSource'], 'sha256': hashlib.sha256(blob).hexdigest(),
                               'registrationFile': stair['sourceFile'], 'registrationLine': stair['sourceLine']}}
        if stair.get('topAssetSource'):
            top = (ROOT / stair['topAssetSource']).read_bytes()
            url = 'data:image/png;base64,' + base64.b64encode(top).decode('ascii')
            material['faces'] = {'top': url, 'bottom': url}
            material['source'].update({'topPath': stair['topAssetSource'], 'topSha256': hashlib.sha256(top).hexdigest(),
                                       'bottomPath': stair['topAssetSource'], 'bottomSha256': hashlib.sha256(top).hexdigest()})
        for meta in stair['metaVariants']:
            for name in (stair['id'], stair['registryName']):
                result['gtsr:' + name + '#' + str(meta)] = dict(material, meta=meta)
    return result


def city_road_textures():
    """Use the actual vanilla stone-brick PNG for the road's cube, stairs and brick slabs."""
    path = ROOT / 'build/resources/patchedMc/assets/minecraft/textures/blocks/stonebrick.png'
    blob = path.read_bytes()
    texture = 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii')
    source = {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(blob).hexdigest(),
              'label': 'Minecraft真实构建资源；生产city-grid道路材质'}
    materials = [('stonebrick', 0, 'cube')]
    materials += [('stone_brick_stairs', meta, 'stairs') for meta in range(8)]
    materials += [('stone_slab', meta, 'slab') for meta in (5, 13)]
    result = {'minecraft:' + block + '#' + str(meta):
            {'block': 'minecraft:' + block, 'meta': meta, 'render': render, 'faces': {},
             'texture': texture, 'source': dict(source)} for block, meta, render in materials}
    fence_path = ROOT / 'build/resources/patchedMc/assets/minecraft/textures/blocks/planks_oak.png'
    fence_blob = fence_path.read_bytes()
    result['minecraft:fence#0'] = {
        'block': 'minecraft:fence', 'meta': 0, 'render': 'fence', 'faces': {},
        'texture': 'data:image/png;base64,' + base64.b64encode(fence_blob).decode('ascii'),
        'source': {'path': fence_path.relative_to(ROOT).as_posix(),
                   'sha256': hashlib.sha256(fence_blob).hexdigest(),
                   'label': 'Minecraft oak fence used by the city bridge guardrail'}}
    return result


def surface_entrance_review_views(metadata, palette, runs):
    """Review the actual authored arrival paving, without inventing surrounding terrain."""
    derived = metadata.get('productionSurfaceEntrance')
    explicit = metadata.get('surfaceEntrance')
    entrance = derived or (explicit if explicit and explicit.get('floorMaterial') else None)
    if not entrance:
        return []
    provenance = entrance.get('productionDerived', {})
    floor = provenance.get('actualSourceFloor', [entrance['x'], entrance['topY'], entrance['z']])
    feet = provenance.get('feet', [floor[0], floor[1] + 1, floor[2]])
    head = provenance.get('head', [floor[0], floor[1] + 2, floor[2]])

    def material(at):
        for x, y, z, n, k in runs:
            if y == at[1] and z == at[2] and x <= at[0] < x + n:
                return palette[k]
        return 'minecraft:air#0'

    expected = provenance.get('sourceFloorMaterial', entrance.get('floorMaterial'))
    if material(floor) != expected or any(material(at) != 'minecraft:air#0' for at in (feet, head)):
        raise ValueError('Arrival paving or exact feet/head clearance mismatch: '
                         + str(metadata.get('id')) + ' ' + str(floor))
    first_route = next(iter(metadata.get('routes', [])), [])
    target = next((point for point in first_route if abs(point[0] - floor[0]) + abs(point[2] - floor[2]) > 1), None)
    if target is None:
        raise ValueError('Arrival platform has no source route direction: ' + str(metadata.get('id')))
    return [{'id': 'production-surface-entrance', 'label': '自然入站锚点 / 原建筑铺路近景',
             'eye': [feet[0] + .5, feet[1] + 1.62, feet[2] + .5],
             'target': [target[0] + .5, target[1] + 1.62, target[2] + .5],
             'floor': {'at': floor, 'material': expected},
             'headClearance': {'feet': feet, 'head': head, 'clear': True,
                               'evidence': 'actual-production-paving-and-empty-voxels'},
             'role': 'surface-entrance', 'productionDerived': bool(derived),
             'sourceSha256': provenance.get('sourceSha256'), 'entranceProvenance': entrance,
             'naturalTerrainRendered': False, 'minecraftRuntime': False}]


def merged_review_views(metadata):
    """Keep every source review pose and append every production-derived control pose."""
    views = list(metadata.get('playerReviewViews', []))
    ids = {view['id'] for view in views}
    controller = metadata.get('productionSiteController')
    derived = metadata.get('productionInteractionApproaches', [])
    if controller and not derived:
        raise ValueError('Production site controller has no review approach')
    for approach in derived:
        view_id = 'production-control:' + approach['node']
        if view_id in ids:
            raise ValueError('Duplicate production control review id: ' + view_id)
        if not all(key in approach for key in ('eye', 'target', 'feet', 'floor', 'sourceSha256')):
            raise ValueError('Production control review approach is incomplete: ' + approach['node'])
        if controller and approach['node'] == controller['id'] \
                and approach['sourceSha256'] != controller['productionDerived']['sourceSha256']:
            raise ValueError('Production controller and its review pose reference different sources')
        label = (controller or {}).get('label', approach['node'])
        views.append({'id': view_id, 'label': '现场控制台 / ' + label,
                      'eye': approach['eye'], 'target': approach['target'], 'floor': approach['floor'],
                      'headClearance': {'feet': approach['feet'], 'verified': approach.get('headClearance'),
                                        'evidence': 'production-derived-approach'},
                      'lineOfSight': 'clear' if approach.get('rayUnobstructed') else 'occluded',
                      'targetId': approach['node'], 'targetBlock': approach.get('targetBlock'),
                      'role': 'site-controller', 'sourceSha256': approach['sourceSha256'],
                      'productionDerived': True,
                      'interactionHint': (controller or {}).get('text', ''),
                      'offlineInteraction': False})
        ids.add(view_id)
    return views


def royal_route_snapshot(path=None):
    """Read a fresh placeInto capture and derive review poses from its actual walking timber."""
    path = path or ROOT / 'temp/royal-stair-geometry.json'
    royal = json.loads(path.read_text(encoding='utf8'))
    source = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/encounter/ForgottenLakeEncounterStructure.java'
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if royal.get('sourceSHA256') != digest or royal.get('layoutVersion') != 3:
        raise ValueError('Royal route capture is stale or is not the actual V3 placeInto output; regenerate after source changes.')
    if sum(r[3] for r in royal['runs']) != royal['count']:
        raise ValueError('Royal capture count does not match its runs')
    cells = {(r[0] + dx, r[1], r[2]): royal['palette'][r[4]]
             for r in royal['runs'] for dx in range(r[3])}
    lower = [min(c[i] for c in cells) for i in range(3)]
    upper = [max(c[i] for c in cells) for i in range(3)]
    ax, az, y0, height = (royal[k] for k in ('ax', 'az', 'y0', 'trunkH'))
    import math
    jround = lambda x: math.floor(x + .5)

    def room_cell(room, u, v):
        a = room * math.pi / 4
        p = [ax + jround(112 * math.cos(a)), y0 + height - 66 + room * 9,
             az + jround(112 * math.sin(a))]
        return [p[0] + jround(u * math.cos(a) - v * math.sin(a)), p[1],
                p[2] + jround(u * math.sin(a) + v * math.cos(a))]

    def spiral(step):
        t, side, radius = step % 192, 48, 24
        if t < side:
            x, z = -radius + t, -radius
        elif t < 2 * side:
            x, z = radius, -radius + t - side
        elif t < 3 * side:
            x, z = radius - (t - 2 * side), radius
        else:
            x, z = -radius, radius - (t - 3 * side)
        return [ax + x, y0 + step // 16, az + z]

    def standing_floor(expected):
        # A full timber block with two empty voxels above gives a reproducible pose.
        # This does not replace the separate production collision fixture.
        for radius in range(4):
            for dy in (0, -1, 1, -2, 2, -3, 3):
                for dx in range(-radius, radius + 1):
                    for dz in range(-radius, radius + 1):
                        if max(abs(dx), abs(dz)) != radius:
                            continue
                        p = (expected[0] + dx, expected[1] + dy, expected[2] + dz)
                        material = cells.get(p, '')
                        if ('ProsperityZenithLog#' in material or material.startswith('gtsr:royal_rootstone#')) \
                                and not cells.get((p[0], p[1] + 1, p[2])) \
                                and not cells.get((p[0], p[1] + 2, p[2])):
                            return list(p), material
        raise ValueError('Royal review pose has no supported clear timber near ' + str(expected))

    views = []

    def add_view(view_id, label, expected, target, role, target_id=None):
        floor, material = standing_floor(expected)
        eye = [floor[0] + .5, floor[1] + 2.62, floor[2] + .5]
        aim = [target[0] + .5, target[1] + 2, target[2] + .5]
        entry = {'id': view_id, 'label': label, 'eye': eye, 'target': aim, 'role': role,
                 'floor': {'at': floor, 'material': material, 'evidence': 'actual-placeInto-voxel'},
                 'headClearance': {'feet': [eye[0], floor[1] + 1, eye[2]],
                                   'head': [eye[0], floor[1] + 2.8, eye[2]],
                                   'evidence': 'two-empty-voxels; separate-MC-collision-check-required'}}
        if target_id:
            entry['targetId'] = target_id
            entry['targetGeometry'] = 'runtime-chest-location; chest-TE-not-exported'
        views.append(entry)

    add_view('natural-entry', '自然王庭 / 木阶起点', spiral(4), spiral(16), 'entrance')
    for step, name in ((44, '下层木阶转角'), (48 * 5 + 12, '中层木阶上行'), (48 * 9 + 12, '高层木阶下行')):
        direction = -12 if '下行' in name else 12
        add_view('spiral-' + str(step), '自然王庭 / ' + name, spiral(step), spiral(step + direction), 'stairs')
    top = y0 + height + 2
    base = y0 + 10 + 12 * ((height + 2 - 10) // 12)

    def connector(j):
        if j <= -27:
            y = top - jround((top - base) * (j + 55) / 28)
        elif j < -24:
            y = base
        else:
            y = base + jround((top - base) * (j + 24) / 24)
        return [ax + j, y, az]

    add_view('court-connector-up', '自然王庭 / 王庭接驳上行', connector(-35), connector(-24), 'connector')
    add_view('court-connector-down', '自然王庭 / 王庭接驳下行', connector(-8), connector(-20), 'connector')
    approaches = []
    for room in range(8):
        phase = (4, 6, 7, 9, 10, 0, 1, 3)[room]
        room_y = y0 + height - 66 + room * 9
        branch_base = y0 + phase + 12 * ((room_y - y0 - phase) // 12)

        def branch_point(u):
            angle = room * math.pi / 4
            start = 24 if room % 2 == 0 else math.sqrt(1152)
            radius, bend = start + (112 - start) * u, 8 * math.sin(math.pi * u)
            return [ax + jround(math.cos(angle) * radius - math.sin(angle) * bend),
                    branch_base + jround((room_y - branch_base) * u + 2 * math.sin(math.pi * u)),
                    az + jround(math.sin(angle) * radius + math.cos(angle) * bend)]

        add_view(f'room-{room}-branch', f'自然王庭 / 第{room + 1}室曲木支路',
                 branch_point(.58), branch_point(.66), 'branch-stairs')
        add_view(f'room-{room}-turn', f'自然王庭 / 第{room + 1}室偏门转角',
                 room_cell(room, -17, -2), room_cell(room, -17, -7), 'turn')
        add_view(f'room-{room}-door', f'自然王庭 / 第{room + 1}室叶幕偏门',
                 room_cell(room, -13, -7), room_cell(room, -5, -7), 'entrance')
        for ordinal in range((1, 2, 2, 1, 1, 2, 2, 1)[room]):
            node = f'platformChest{room}' + (f'_{ordinal}' if ordinal else '')
            target = room_cell(room, 11, -7 if ordinal == 0 else 7)
            add_view(f'room-{room}-chest-{ordinal}', f'自然王庭 / 第{room + 1}室箱位附近',
                     room_cell(room, 7, -7 if ordinal == 0 else 7), target, 'chest-location', node)
            view = views[-1]
            approaches.append({'node': node, 'target': [target[0], target[1] + 1, target[2]],
                               'eye': view['eye'], 'feet': view['headClearance']['feet'],
                               'floor': view['floor']['at'], 'targetGeometry': view['targetGeometry']})
    return royal, {'id': 'forgotten-lake-court-natural-snapshot', 'name': '自然王庭 · 真实通路几何快照',
                   'category': 'landmark', 'src': 'structures/remaster/forgotten-lake-court-natural-snapshot.js',
                   'size': [upper[i] - lower[i] + 1 for i in range(3)], 'origin': lower,
                   'count': royal['count'], 'variant': 'production-snapshot',
                   'geometryMode': 'production-royal-route-snapshot', 'layoutVersion': 3,
                   'captureParameters': {k: royal[k] for k in ('seed', 'ax', 'az', 'y0', 'trunkH')},
                   'playerReviewViews': views, 'interactionApproaches': approaches,
                   'description': ['直接捕获最新 ForgottenLakeEncounterStructure.placeInto 的 V3 输出；不是旧作者王庭含树蓝图。',
                                   '包含王庭平台、木阶、八条支路、偏门房间和叶幕；箱附近视点指向实际箱位，快照不放置运行时箱子或实体。',
                                   '不含完整自然巨树、岩层、水体、敌人 AI 与交互；离线可视几何与相机证据不代表实机验收。'],
                   'source': {'file': source.relative_to(ROOT).as_posix(), 'sha256': digest,
                              'captureFile': path.relative_to(ROOT).as_posix(),
                              'label': '实际 V3 placeInto 通路输出 / 源码 SHA256 ' + digest}}


def main():
    package = ASSETS / 'remaster'
    catalog = json.loads((package / 'catalog.json').read_text(encoding='utf-8'))
    block_data = json.loads((package / 'blocks.json').read_text(encoding='utf-8'))
    legacy = json.loads((DEST / 'data/catalog.json').read_text(encoding='utf-8'))
    names = {e['id']: e.get('name', e['id']) for e in legacy['structures']}
    structures = {e['id']: e for e in catalog['structures']}
    policy = rollout_policy()
    designs = []
    # Archived designs remain in the plan, while the production catalog contains only three scenes.
    design_script = "const fs=require('fs'),vm=require('vm'),p=require('path'),s={window:{}};vm.createContext(s);for(const n of ['catalog','catalog5'])vm.runInContext(fs.readFileSync(p.join(process.argv[1],n+'.js'),'utf8'),s);process.stdout.write(JSON.stringify(s.window.REMASTER_CATALOG.structures));"
    design_entries = json.loads(subprocess.check_output(['node', '-e', design_script,
        str(ROOT / 'plan/临时计划/preview')], encoding='utf8'))
    for entry in design_entries:
        fact = next((e for e in legacy['structures'] if e['id'] == entry['id']), {})
        descriptor = next((d for d in catalog['prefabs'] if d['id'] == entry['id'] and d['variant'] == 0), None)
        metadata = json.loads(gzip.decompress((package / descriptor['file']).read_bytes())).get('metadata', {}) if descriptor else {}
        label = metadata.get('productionSiteController', {}).get('label', '')
        designs.append({'id': entry['id'], 'name': fact.get('name') or entry.get('name') or label.split(' · ')[0] or entry['id'],
                        'category': entry['category'], 'description': [label] if label else fact.get('description', [])[:1],
                        'source': entry.get('source', ''), 'status': 'paused-needs-simplification'})
    models = []
    for descriptor in catalog['prefabs']:
        if descriptor['id'] not in policy['activeIds'] or descriptor['variant'] != policy['standardVariant']:
            continue
        stem = f"{descriptor['id']}-r7-v{descriptor['variant']}"
        manifest = json.loads(gzip.decompress((package / descriptor['file']).read_bytes()))
        if hashlib.sha256(gzip.decompress((package / descriptor['file']).read_bytes())).hexdigest() != descriptor['sha256']:
            raise ValueError('Production manifest changed during export: ' + stem)
        palette = manifest['palette']
        runs = []
        air_runs = []
        for entry in manifest['slices']:
            data = json.loads(gzip.decompress((package / entry['file']).read_bytes()))
            if hashlib.sha256(gzip.decompress((package / entry['file']).read_bytes())).hexdigest() != entry['sha256']:
                raise ValueError('Production slice changed during export: ' + entry['file'])
            runs.extend(r for r in data if palette[r[4]] != 'minecraft:air#0')
            air_runs.extend(r[:4] for r in data if palette[r[4]] == 'minecraft:air#0')
        runs.sort(key=lambda r: (r[1], r[2], r[0]))
        model_data = {'palette': palette, 'runs': runs}
        dump(DEST / f'structures/remaster/{stem}.js',
             'window.PROSPERITY_STRUCTURES_DATA=window.PROSPERITY_STRUCTURES_DATA||{};'
             f'window.PROSPERITY_STRUCTURES_DATA[{json.dumps(stem)}]='
             + json.dumps(model_data, ensure_ascii=False, separators=(',', ':')) + ';\n')
        entry = structures[descriptor['id']]
        metadata = manifest.get('metadata', {})
        entrance_views = surface_entrance_review_views(metadata, palette, runs)
        review_views = merged_review_views(metadata) + entrance_views
        description = [f"第七轮生产预制，方案 {descriptor['variant'] + 1}；各方案互斥。",
                       '从游戏使用的区块切片解码；此视图为离线体素，不代表实机验收。']
        if descriptor['id'] == 'fiction_expansion_project':
            description[0] = ['工程遗构', '地面构架完成', '高空扩大器闭合'][descriptor['variant']] + '；这是施工阶段。'
        if metadata.get('productionSiteController'):
            controller = metadata['productionSiteController']
            description.extend(['现场可操作线索 / ' + controller.get('label', controller['id']),
                                controller.get('text', ''),
                                '人物巡检中的现场控制台镜头对准实际生产台位；现场操作由游戏服务端执行，本离线页面只展示线索和几何。'])
        if entrance_views:
            description.append('自然入站锚点近景严格使用原建筑铺路及实际脚部/头部净空；本页面没有完整天然地形，天然入站需另核MC运行证据。')
        models.append({'id': stem, 'name': names.get(descriptor['id'], descriptor['id']) + f" · 第七轮 {descriptor['variant'] + 1}",
                       'category': entry['category'], 'src': f'structures/remaster/{stem}.js',
                       'size': descriptor['extent'], 'origin': descriptor['min'],
                       'count': descriptor['solidCount'], 'variant': 'authored', 'description': description,
                       'productionId': descriptor['id'], 'productionVariant': descriptor['variant'],
                       'compactScene': metadata.get('compactScene', False),
                       'combatOnly': metadata.get('combatOnly', False),
                       'nativeScene': metadata.get('nativeScene', False),
                       'nativeCapture': metadata.get('captureProof'),
                       'roofKeys': metadata.get('roofKeys', []), 'contextKeys': metadata.get('contextKeys', []),
                       'branchTerrain': metadata.get('branchTerrain', metadata.get('terrain')), 'burial': metadata.get('burial'),
                       'story': metadata.get('story', []), 'triggers': metadata.get('triggers', []),
                       'rooms': metadata.get('rooms', []), 'contextPolicy': metadata.get('contextPolicy'),
                       'sceneBounds': metadata.get('sceneBounds'), 'implementationRevision': metadata.get('implementationRevision'),
                       'nodes': metadata.get('nodes', []), 'spawns': metadata.get('spawns', []),
                       'spawnerPlan': metadata.get('spawnerPlan', []),
                       'lootPlan7': metadata.get('lootPlan7', []), 'airRuns': air_runs,
                       'playerReviewViews': review_views,
                       'playerReviewAbsentViews': manifest.get('metadata', {}).get('playerReviewAbsentViews', []),
                       'interactionApproaches': manifest.get('metadata', {}).get('interactionApproaches', []),
                       'interactionApproachCoverage': manifest.get('metadata', {}).get('interactionApproachCoverage', {}),
                       'productionSiteController': metadata.get('productionSiteController'),
                       'productionInteractionApproaches': metadata.get('productionInteractionApproaches', []),
                       'productionSurfaceEntrance': metadata.get('productionSurfaceEntrance'),
                       'surfaceEntrance': metadata.get('surfaceEntrance'),
                       'reviewCoverage': {'sampled': [v['id'] for v in review_views],
                                          'sourceViews': len(metadata.get('playerReviewViews', [])),
                                          'productionControlViews': len(metadata.get('productionInteractionApproaches', [])),
                                          'surfaceEntranceViews': len(entrance_views),
                                          'absent': manifest.get('metadata', {}).get('playerReviewAbsentViews', [])},
                       'geometryMode': 'authored-blueprint' if descriptor['id'] == 'prosperity_city_full' else 'production-prefab',
                       'source': {'file': 'src/main/resources/assets/gtsr/remaster/' + descriptor['file'],
                                  'authorFile': metadata.get('sourceAuthorFile', 'plan/临时计划/preview/prefabs7/' + descriptor['id'] + '-v' + str(descriptor['variant']) + '.js'),
                                  'authorSha256': descriptor['sourceSha256'],
                                  'sha256': descriptor['sha256'],
                                  'label': '生产预制 SHA256 ' + descriptor['sha256']}})
        if descriptor['id'] == 'prosperity_city_full':
            models[-1]['description'].append('历史整城作者蓝图；自然生成使用独立道路与28个按自然地形分别定高的地块，见实际城市合成条目。')
        if descriptor['id'] == 'forgotten_lake_court':
            models[-1]['description'].append('直接采集当前原生树体、八室与王庭的完整生成样本，包含真实箱位和守卫标注。树体与战斗由原生生成器拥有，旧作者覆盖层已停止写入；离线样本不代表所有种子或真人通关验收。')
    royal, royal_model = royal_route_snapshot()
    dump(DEST / royal_model['src'],
         'window.PROSPERITY_STRUCTURES_DATA=window.PROSPERITY_STRUCTURES_DATA||{};'
         f'window.PROSPERITY_STRUCTURES_DATA[{json.dumps(royal_model["id"])}]='
         + json.dumps({'palette': royal['palette'], 'runs': royal['runs']}, separators=(',', ':')) + ';\n')
    models.append(royal_model)
    dump(DEST / 'structures/remaster-index.js', contained_index(models, policy, designs))

    # The frozen authored boxes are also the input used to create RemasterBlocks.
    plan = ROOT / 'plan' / '\u4e34\u65f6\u8ba1\u5212' / 'preview'
    script = """const fs=require('fs'),vm=require('vm');const w={REMASTER_TEXTURES:{}};
const sandbox={window:w};vm.createContext(sandbox);
for(const p of process.argv.slice(1))vm.runInContext(fs.readFileSync(p,'utf8'),sandbox);
process.stdout.write(JSON.stringify(w.REMASTER_TEXTURES));"""
    result = subprocess.run(['node', '-e', script, *[str(plan / f'blocks{i}.js') for i in range(4, 8)]],
                            check=True, capture_output=True, text=True, encoding='utf-8')
    textures = json.loads(result.stdout)
    registered_ids = {('gtsr:' + b['id'] if ':' not in b['id'] else b['id']) for b in block_data['blocks']}
    textures = {key: value for key, value in textures.items() if key.split('#')[0] in registered_ids}
    production_hashes = {hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in (ASSETS / 'textures/blocks').glob('remaster_*.png')}
    for key, tex in textures.items():
        for image in [tex.get('texture'), *tex.get('faces', {}).values()]:
            if image and image.startswith('data:image/png;base64,'):
                digest = hashlib.sha256(base64.b64decode(image.split(',', 1)[1])).hexdigest()
                if digest not in production_hashes:
                    raise ValueError('Preview texture is not registered in production: ' + key)
        texture_bytes = base64.b64decode(tex['texture'].split(',', 1)[1])
        digest = hashlib.sha256(texture_bytes).hexdigest()
        tex['source'] = {'path': 'src/main/resources/assets/gtsr/textures/blocks/remaster_' + digest[:16] + '.png',
                         'sha256': digest}
    legacy_text = (DEST / 'textures/textures.js').read_text(encoding='utf-8')
    previous = json.loads(legacy_text[legacy_text.index('=') + 1:legacy_text.rindex(';')])
    for key, tex in previous.items():
        source = tex.get('source', {}).get('path', '').replace('\\', '/')
        if 'src/main/resources/' in source:
            path = ROOT / ('src/main/resources/' + source.split('src/main/resources/', 1)[1])
            if path.exists():
                blob = path.read_bytes()
                replacement = dict(tex)
                replacement['texture'] = 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii')
                replacement['source'] = {'path': path.relative_to(ROOT).as_posix(),
                                         'sha256': hashlib.sha256(blob).hexdigest()}
                textures.setdefault(key, replacement)
    for block_id, texture_name in (('ruin_debris_rivet_plate', 'ruin_debris_rivet_plate'),
                                   ('ruined_casing_rusted', 'ruined_casing_rusted'),
                                   ('ProsperityStone', 'prosperity_stone')):
        path = ASSETS / 'textures/blocks' / (texture_name + '.png')
        blob = path.read_bytes()
        textures['gtsr:' + block_id + '#0'] = {
            'block': 'gtsr:' + block_id, 'meta': 0, 'render': 'cube', 'faces': {},
            'texture': 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii'),
            'source': {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(blob).hexdigest()}}
    textures.update(city_road_textures())
    textures.update(production_stair_textures())
    dump(DEST / 'textures/remaster.js', 'Object.assign(window.PROSPERITY_TEXTURES,'
         + json.dumps(textures, ensure_ascii=False, separators=(',', ':')) + ');\n')
    blocks = [{'id': b['id'], 'name': b['name'], 'group': 'architecture', 'category': 'block',
               'description': [b.get('purpose', '')], 'registryName': 'gtsr:' + b['id'],
               'texture': 'gtsr:' + b['id'] + '#0', 'function': b.get('purpose', ''),
               'locations': '三个 Boss 标准场景的构件及暂缓设计的已注册资产；注册不表示当前生成', 'sources': [{'file':
                   'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java',
                   'label': '生产注册'}]} for b in block_data['blocks']]
    # The paused engineering testimony chain is retained in design assets, not the active guide.
    items = []
    terrain = [{'id': 'remaster_compact_branch', 'name': '草原工厂与齿轮森林战场伴生平原', 'group': 'terrain_variant',
                'description': ['工厂绑定草原分支，战场绑定齿轮森林分支；分支平原与建筑共享确定性锚点。',
                                '已移除为旧深井工厂添加的全域高台。局部平原取原锚点海拔，只在建筑周边渐变接合原地形。'],
                'sources': [{'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/CompactSceneTerrain.java', 'label': '伴生分支纯函数'}]},
               {'id': 'remaster_bounded_footings', 'name': '三个标准场景的地形接合', 'group': 'terrain_variant',
                'description': ['本轮全盘重构战场与工厂为紧凑建筑，室内和通道显式标注空气；巨树保留体量与真实守卫通路。',
                                '城市及其他结构已暂缓，仅保留设计介绍，后续大幅简化减量；城市桥路不属于本轮开放生成范围。',
                                '三个标准场景均在打磨中；通行、岩石侵入和树体接合仍待游戏内验收。'],
                'sources': [{'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterWorldgen.java', 'label': '结构实块与显式空气'} ,
                            {'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/encounter/ForgottenLakeEncounterStructure.java', 'label': '原生巨树八室通路'},
                            {'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterRollout.java', 'label': '三个标准场景的当前生成范围'}]}]
    version = re.search(r'^RELEASE_VERSION\s*=\s*(\S+)', (ROOT / 'gradle.properties').read_text(encoding='utf-8'), re.M).group(1)
    dump(DEST / 'data/remaster.js', '(function(c){c.meta.sourceVersion=' + json.dumps(version) + ';c.blocks.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in blocks) + ');c.items.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in items) + ');c.terrainVariants.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in terrain) + ');'
         'c.meta.counts.blocks=c.blocks.length;c.meta.counts.items=c.items.length;'
         'c.meta.counts.terrainVariants=c.terrainVariants.length;'
         '})(window.PROSPERITY_CATALOG);\n')
    print(f'exported: {len(policy["activeIds"])} refining Boss standard scenes + attached royal sample / {len(designs) - len(policy["activeIds"])} paused design introductions / registered textures')


if __name__ == '__main__':
    main()
