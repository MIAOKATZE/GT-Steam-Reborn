"""Verify compilation clearance precedence without touching production assets."""
import gzip
import json
import tempfile
import argparse
import copy
import hashlib
from collections import Counter
from pathlib import Path
import remaster_convert as converter


def controller_regressions():
    metadata = {'id': 'fixture', 'variant': 0,
                'productionPuzzle': {'title': '现场核验', 'fields': [['工序', ['错误', '正确']]], 'target': [1]},
                'nodes': [{'id': 'objective-0', 'role': 'memory', 'x': 5, 'y': 1, 'z': 5}],
                'routes': [[[0, 0, 5], [15, 0, 5]]],
                'shapeMechanisms': [{'id': 'gate', 'control': [6, 1, 5],
                                     'states': [{'delta': [[5, 1, 3, 'gtsr:wall#0']]}]}]}
    palette = ['minecraft:air#0', 'gtsr:ruins_sootstone_tiles#0']
    cells = {(x, 0, z): 1 for x in range(16) for z in range(16)}
    original = copy.deepcopy(metadata)
    node = converter.derive_site_controller(metadata, palette, cells, 'fixture-sha')
    assert node and metadata == original, 'derivation mutates original clues/plan'
    at = tuple(node[k] for k in ('x', 'y', 'z'))
    assert at != (5, 1, 3) and at[2] not in (4, 5, 6), 'console overwrites future shape or route'
    assert node['role'] == 'control' and 'evidenceRecordId' not in node, 'console reads evidence in place of solving'
    metadata['nodes'].append({'id': 'existing-control', 'role': 'control', 'x': 8, 'y': 1, 'z': 8})
    assert converter.derive_site_controller(metadata, palette, cells, 'fixture-sha') is None
    metadata['navigationHints'] = [{'id': 'overriding-memory', 'x': 8, 'y': 1, 'z': 8}]
    assert converter.derive_site_controller(metadata, palette, cells, 'fixture-sha'), 'last coordinate owner ignored'
    try:
        converter.derive_site_controller(original, palette, {}, 'fixture-sha')
        raise AssertionError('unsupported floating console accepted')
    except ValueError:
        pass


def entrance_regressions():
    metadata = {'id': 'fixture', 'variant': 0, 'routes': [[[0, 0, 0], [8, 0, 0]]],
                'rooms': [], 'nodes': []}
    palette = ['minecraft:air#0', 'gtsr:ruins_mossroot_paving#0', 'gtsr:draft_notice_board#0']
    source = {(x, 0, z): 1 for x in range(9) for z in (-1, 0, 1)}
    original = copy.deepcopy(metadata)
    entrance = converter.derive_surface_entrance(metadata, palette, source, palette, source, 'source-sha')
    assert [entrance['x'], entrance['topY'], entrance['z']] == [0, 0, 0]
    blocked = dict(source)
    blocked[0, 1, 0] = 2
    entrance = converter.derive_surface_entrance(metadata, palette, blocked, palette, blocked, 'source-sha')
    assert [entrance['x'], entrance['topY'], entrance['z']] == [2, 0, 0]
    assert len(entrance['productionDerived']['laneClearNeighbors']) == 4
    assert entrance['productionDerived']['originalFirstPoint'] == [0, 0, 0]
    assert metadata == original, 'entrance derivation changes original source metadata'
    reviewed_feet = dict(metadata, structureDesignReview={'stairFunctionalAccessRoutes': [
        {'originalRouteKey': 'routes:0', 'actualPoints': [[0, 1, 0], [8, 1, 0]]}]})
    entrance = converter.derive_surface_entrance(reviewed_feet, palette, source, palette, source, 'source-sha')
    assert entrance['topY'] == 0 and entrance['productionDerived']['reviewedFirstPoint'] == [0, 1, 0]
    roof = {(0, 3, 0): 1}
    assert converter.derive_surface_entrance(metadata, palette, roof, palette, roof, 'source-sha') is None
    for excluded in ({'surfaceEntrance': {'x': 0, 'z': 0, 'topY': 0}}, {'terrain': {'buried': 'full'}}):
        assert converter.derive_surface_entrance(dict(metadata, **excluded), palette, source,
                                                palette, source, 'source-sha') is None
    # A distant, disconnected paver must never replace the original initial platform.
    disconnected = {(0, 0, 0): 1, (0, 1, 0): 2, (2, 0, 0): 1}
    try:
        converter.derive_surface_entrance(metadata, palette, disconnected, palette, disconnected, 'source-sha')
        raise AssertionError('disconnected entrance accepted')
    except ValueError:
        pass


def all_controller_regressions():
    """Recompile and strictly verify all reviewed snapshots in a temporary output."""
    import remaster_verify as verifier
    catalog = json.loads((converter.OUT / 'catalog.json').read_text(encoding='utf8'))
    puzzles = {}
    for entry in catalog['prefabs']:
        manifest = json.loads(gzip.decompress((converter.OUT / entry['file']).read_bytes()))
        puzzles[entry['id']] = manifest['metadata']['productionPuzzle']
    saved = converter.OUT, converter.PUZZLES, converter.ENGINEERING, verifier.OUT
    summary = Counter()
    report = []
    try:
        with tempfile.TemporaryDirectory(prefix='gtsr-site-controller-') as folder:
            converter.OUT = Path(folder)
            verifier.OUT = converter.OUT
            converter.PUZZLES = puzzles
            converter.ENGINEERING = catalog['engineeringChapters']
            descriptors = []
            for source in sorted((converter.SOURCE / 'preview/prefabs7').glob('*.js')):
                descriptor = converter.compile_one(source)
                descriptors.append(descriptor)
                manifest = json.loads(gzip.decompress((converter.OUT / descriptor['file']).read_bytes()))
                metadata = manifest['metadata']
                original = converter.snapshot(source)['metadata']
                assert metadata['productionPuzzle'] == puzzles[descriptor['id']]
                for field in ('nodes', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7', 'testimonyPedestals', 'navigationHints'):
                    without_block = lambda rows: [{k: v for k, v in row.items() if k != 'block'} for row in rows]
                    assert without_block(metadata.get(field, [])) == without_block(original.get(field, [])), (source, field)
                controllers = [n for n, role in converter.runtime_node_owners(metadata).values() if role == 'control']
                ordinary = descriptor['id'] not in ('fiction_expansion_project', 'forgotten_lake_court')
                gated = [n for n in metadata.get('lootPlan7', [])
                         if n.get('unlock') == 'puzzle-completed' and n.get('reference') == 'site-puzzle']
                if ordinary:
                    assert controllers, (source, 'site-puzzle state has no actionable control')
                    summary['ordinaryVariants'] += 1
                    summary['reachableSitePuzzleBoxes'] += len(gated)
                summary['derivedControllers'] += int(bool(metadata.get('productionSiteController')))
                summary['prefabs'] += 1
                report.append({'prefab': source.stem, 'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                               'ordinary': ordinary, 'siteControllers': [n['id'] for n in controllers],
                               'derivedController': metadata.get('productionSiteController'),
                               'productionInteractionApproaches': metadata.get('productionInteractionApproaches', []),
                               'sitePuzzleBoxes': len(gated)})
            isolated_catalog = dict(catalog, prefabs=descriptors)
            converter.write_json(converter.OUT / 'catalog.json', isolated_catalog)
            verifier.main()
    finally:
        converter.OUT, converter.PUZZLES, converter.ENGINEERING, verifier.OUT = saved
    report_path = converter.ROOT / 'temp/site-controller-isolated-report.json'
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps({'summary': dict(summary), 'rows': report}, ensure_ascii=False), encoding='utf8')
    for identity in ('trench_infirmary-v0', 'city_pump_house-v0'):
        assert next(r for r in report if r['prefab'] == identity)['siteControllers'] == ['site-puzzle-controller']
    print(json.dumps({'passed': True, **summary, 'unreachableOrdinarySitePuzzleBoxes': 0}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--all', action='store_true', help='Also recompile and strictly verify all 268 snapshots in isolation.')
    args = parser.parse_args()
    controller_regressions()
    entrance_regressions()
    fixture = {'metadata': {'id': 'fixture', 'variant': 0, 'nominal': [8, 8, 8],
        'rooms': [{'x': 0, 'y': 0, 'z': 0, 'w': 8, 'h': 7, 'd': 8}],
        'routes': [[[0, 2, 2], [6, 2, 2]]],
        'nodes': [{'x': 3, 'y': 3, 'z': 2}],
        'shapeMechanisms': [{'id': 'fixture-shape', 'control': [4, 3, 2], 'states': [{'delta': [[5, 3, 2, 'solid']]}]}],
        'damageManifest': [{'removedVoxels': [[6, 3, 2]]}]},
        'voxel': {'palette': ['gtsr:wall#0', 'gtsr:tool_rack#0', 'minecraft:air#0'],
                  'runs': [[0, 3, 2, 7, 0], [1, 3, 2, 1, 1], [7, 1, 7, 1, 2]]}}
    with tempfile.TemporaryDirectory(prefix='gtsr-compile-clearance-') as folder:
        root = Path(folder)
        source = root / 'fixture.js'
        source.write_text('window.fixture["fixture"]=' + json.dumps(fixture) + ';', encoding='utf8')
        original_out, original_puzzles = converter.OUT, converter.PUZZLES
        try:
            converter.OUT = root / 'out'
            converter.PUZZLES = {'fixture': {}}
            descriptor = converter.compile_one(source)
            manifest = json.loads(gzip.decompress((converter.OUT / descriptor['file']).read_bytes()))
            cells = {}
            for entry in manifest['slices']:
                for x, y, z, n, k in json.loads(gzip.decompress((converter.OUT / entry['file']).read_bytes())):
                    for xx in range(x, x + n):
                        cells[xx, y, z] = manifest['palette'][k]
            assert cells[2, 3, 2] == 'minecraft:air#0', 'route obstruction survives'
            assert cells[0, 3, 2] == 'gtsr:wall#0', 'room shell destroyed'
            assert cells[1, 3, 2] == 'gtsr:tool_rack#0', 'furniture destroyed'
            assert all(cells[x, 3, 2] == 'gtsr:wall#0' for x in (3, 5)), 'protected node/delta overwritten'
            assert cells[4, 3, 2] == 'gtsr:draft_pressure_console#0', 'shape control lacks physical interactive carrier'
            assert cells[6, 3, 2] == 'minecraft:air#0', 'intended break backfilled'
            assert manifest['solidCount'] == sum(v != 'minecraft:air#0' for v in cells.values())
            assert manifest['airCount'] == sum(v == 'minecraft:air#0' for v in cells.values())
            assert 'placement' in descriptor
        finally:
            converter.OUT, converter.PUZZLES = original_out, original_puzzles
    print('PASS: explicit route clearance; protected room shell/furniture/node/control/delta; intended damage; air accounting; lightweight placement descriptors.')
    if args.all:
        all_controller_regressions()


if __name__ == '__main__':
    main()
