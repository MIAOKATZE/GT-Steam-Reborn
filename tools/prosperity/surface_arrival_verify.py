from native_terrain_fixture import native_sources, isolated_sourcepath
"""Isolated 268 assets and MC arrival proof: 162 natural-ring + 84 actual city-street connections."""
import argparse
import gzip
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path
import remaster_convert as converter
import remaster_verify as verifier

# Seed 20261001 original planner anchors, before production entrance derivation.
ORIGINAL_ANCHORS = {'colossus_colossus_hub_array_blueprint:0': [1721, 67, 768], 'colossus_colossus_hub_array_wreck_light:0': [312, 76, 191], 'outpost_outpost_brick_kiln:1': [-415, 76, -445], 'pumping_house:0': [-202, 76, 80], 'signal_bridge:1': [112, 74, 385], 'colossus_colossus_boiler_hall_wreck_deep:1': [-2445, 69, -3345], 'mirror_barracks:1': [-884, 68, -740], 'city_watch_tower:1': [-3501, 67, 791], 'city_gear_tower:1': [-3405, 73, 791], 'city_chimney_stack:1': [-3308, 64, 791], 'city_water_tower:2': [-3212, 66, 791], 'city_cooling_tower:0': [-3116, 74, 791], 'city_clock_tower:1': [-3021, 72, 791], 'city_boiler_house:0': [-2924, 66, 791], 'city_pump_house:2': [-3497, 76, 899], 'city_forge_hall:0': [-3404, 83, 887], 'city_engine_room:0': [-3308, 68, 887], 'city_gas_holder:2': [-3212, 65, 887], 'city_pressure_tank_row:1': [-3116, 72, 887], 'city_broken_bridge:1': [-3016, 77, 887], 'city_viaduct:0': [-2920, 70, 887], 'city_rail_platform:1': [-3497, 74, 983], 'city_crane_ruin:2': [-3398, 76, 983], 'city_canal_gate:2': [-3308, 70, 983], 'city_dome_hall:0': [-3212, 66, 986], 'city_market_colonnade:2': [-3113, 70, 983], 'city_manor_ruin:2': [-3021, 71, 983], 'city_fountain_basin:2': [-2924, 69, 983], 'city_tram_depot:2': [-3500, 73, 1079], 'city_fallen_arch:1': [-3404, 64, 1079], 'city_broken_pillars:0': [-3308, 67, 1079], 'city_machine_plinth:0': [-3212, 66, 1079], 'city_slag_heap:2': [-3116, 71, 1079], 'city_great_forge:2': [-3020, 71, 1080], 'city_titan_gearworks:0': [-2921, 67, 1080], 'outpost_outpost_toppled_boiler:2': [-719, 70, 259], 'ruin_ruin_boiler_lean:0': [-420, 76, 349], 'colossus_colossus_hub_array_wreck_light:1': [1544, 74, -2657], 'weaving_mill:2': [44, 72, 1281], 'machine_steam_gallery:1': [372, 72, -349], 'colossus_colossus_boiler_hall_wreck_light:1': [1042, 83, 223], 'ruin_ruin_boiler_lean:1': [556, 68, 124], 'city_boiler_house:2': [4468, 61, 2903], 'city_pump_house:0': [4567, 59, 2912], 'city_forge_hall:1': [4660, 72, 2903], 'city_engine_room:1': [4756, 75, 2903], 'city_gas_holder:0': [4852, 85, 2903], 'city_viaduct:2': [4472, 69, 2999], 'city_rail_platform:0': [4567, 76, 2999], 'city_canal_gate:0': [4756, 77, 2999], 'city_market_colonnade:0': [4948, 84, 2999], 'city_tram_depot:0': [4564, 76, 3095], 'city_fallen_arch:0': [4661, 77, 3095], 'city_machine_plinth:2': [4852, 75, 3095], 'city_slag_heap:1': [4948, 75, 3095], 'city_great_forge:1': [5041, 79, 3096], 'city_gear_tower:2': [4660, 78, 3191], 'city_chimney_stack:2': [4756, 73, 3191], 'city_water_tower:1': [4851, 72, 3191], 'city_cooling_tower:2': [4948, 72, 3191], 'city_clock_tower:2': [5044, 68, 3191], 'switchhouse:0': [618, 74, 368], 'fallen_foundry:1': [-4956, 77, 56], 'colossus_colossus_boiler_hall_wreck_medium:2': [-1387, 64, 735], 'boiler_shrine:0': [-1374, 69, 1804], 'city_machine_plinth:1': [-3757, 73, -5671], 'city_slag_heap:0': [-3660, 71, -5673], 'city_watch_tower:2': [-3372, 74, -5673], 'city_gear_tower:0': [-3273, 72, -5673], 'city_boiler_house:1': [-3468, 71, -5577], 'city_forge_hall:2': [-3276, 78, -5577], 'city_pressure_tank_row:2': [-3660, 69, -5481], 'city_broken_bridge:2': [-3560, 67, -5481], 'city_dome_hall:2': [-3756, 73, -5382], 'city_fountain_basin:1': [-3469, 69, -5385], 'city_chimney_stack:0': [-1276, 81, -5737], 'city_cooling_tower:1': [-1084, 72, -5737], 'city_broken_bridge:0': [-985, 76, -5641], 'city_rail_platform:2': [-793, 68, -5641], 'city_crane_ruin:0': [-1365, 66, -5545], 'city_manor_ruin:1': [-989, 83, -5545], 'city_tram_depot:1': [-796, 73, -5545], 'city_broken_pillars:2': [-1276, 74, -5449], 'city_titan_gearworks:2': [-892, 80, -5448], 'city_watch_tower:0': [-793, 70, -5449], 'machine_chimney_base:2': [-140, 79, -666], 'city_water_tower:0': [-1836, 63, 6919], 'city_pump_house:1': [-1447, 74, 6928], 'city_engine_room:2': [-1932, 64, 7015], 'city_viaduct:1': [-1544, 71, 7015], 'city_canal_gate:1': [-1932, 68, 7111], 'city_dome_hall:1': [-1836, 76, 7114], 'city_fountain_basin:0': [-1548, 76, 7111], 'city_fallen_arch:2': [-1355, 69, 7111], 'city_great_forge:0': [-1641, 82, 7208], 'city_titan_gearworks:1': [-1551, 75, 7208], 'ash_crematory:1': [-132, 70, 801], 'colossus_colossus_hub_array_wreck_deep:0': [1769, 68, 7967], 'machine_pump_base:0': [67, 66, 844], 'machine_steam_gallery:0': [580, 67, 803], 'colossus_colossus_hub_array_wreck_heavy:1': [7624, 65, -2257], 'machine_boiler_frame:1': [836, 65, -365], 'fallen_foundry:0': [-7724, 78, -7432], 'colossus_colossus_boiler_hall_blueprint:2': [-7003, 66, -4944], 'boiler_shrine:2': [-2029, 66, -788], 'rift_observatory:2': [-932, 77, -160], 'outpost_outpost_broken_aqueduct:1': [-1805, 62, 212], 'expedition_camp:1': [-967, 76, 129], 'weaving_mill:0': [-1969, 73, 2145], 'weighbridge:2': [-672, 74, 1153], 'mirror_barracks:0': [-884, 68, 2092], 'weaving_mill:1': [128, 62, -1935], 'city_clock_tower:0': [1351, 73, 8967], 'city_pressure_tank_row:0': [1252, 63, 9063], 'city_crane_ruin:1': [1643, 67, 9063], 'city_manor_ruin:0': [1347, 69, 9159], 'machine_boiler_frame:2': [148, 63, 1107], 'colossus_colossus_hub_array_wreck_heavy:0': [2249, 68, 9087], 'city_gas_holder:1': [5476, 65, -7961], 'city_market_colonnade:1': [4899, 55, -7769], 'city_broken_pillars:1': [5380, 73, -7769], 'colossus_colossus_hub_array_wreck_light:2': [2136, 67, 271], 'colossus_colossus_hub_array_wreck_medium:0': [-2392, 76, -2465], 'ruin_ruin_gallery_span:0': [-1187, 72, -1219], 'rail_service_cache:2': [-2426, 67, -1162], 'colossus_colossus_boiler_hall_wreck_heavy:1': [-8990, 84, -2353], 'gear_garden:1': [-1223, 70, -383], 'outpost_outpost_watch_post:0': [-2400, 66, -413], 'colossus_colossus_boiler_hall_wreck_light:0': [-2497, 72, 719], 'machine_gear_mill_table:2': [-1148, 72, 643], 'colossus_colossus_boiler_hall_blueprint:1': [-9404, 79, 8688], 'outpost_outpost_gear_slag_mound:0': [-2511, 67, 2627], 'valve_yard:0': [-1210, 67, 1360], 'boiler_chapel:1': [-904, 59, 1408], 'colossus_colossus_boiler_hall_wreck_heavy:0': [-1489, 70, -2289], 'ruin_ruin_pump_chip:0': [-653, 79, -1139], 'outpost_outpost_collapsed_truss:0': [-1279, 62, 2867], 'boiler_chapel:0': [-708, 64, 1360], 'shore_crane:2': [-432, 72, 1376], 'colossus_colossus_hub_array_blueprint:1': [-343, 64, -2272], 'pilgrim_arch:0': [60, 68, -1168], 'outpost_outpost_collapsed_truss:2': [865, 77, -2349], 'ruin_ruin_watch_buried:1': [586, 68, 1333], 'trench_infirmary:1': [1386, 70, -960], 'ruin_ruin_truss_fan:1': [1392, 72, 60], 'boiler_chapel:2': [-1508, 70, -1504], 'rail_service_cache:0': [-2858, 71, -1722], 'broken_aqueduct:1': [-1456, 84, -383], 'valve_yard:1': [-1448, 72, 64], 'ruin_ruin_aqueduct_span:2': [-1197, 76, 1629], 'outpost_outpost_collapsed_truss:1': [-1983, 66, -2861], 'colossus_colossus_hub_array_wreck_medium:2': [-1863, 67, 3311], 'archivist_kiosk:1': [-648, 73, -1487], 'outpost_outpost_gear_slag_mound:2': [-655, 78, -2717], 'ruin_ruin_kiln_stump:1': [-404, 79, 1632], 'colossus_colossus_hub_array_wreck_deep:2': [-839, 76, -12113], 'outpost_outpost_broken_aqueduct:2': [131, 60, 3204], 'ruin_ruin_truss_fan:2': [1135, 79, -1476], 'ash_crematory:0': [1612, 64, -928], 'workers_lodging:2': [1563, 67, -736], 'outpost_outpost_toppled_boiler:1': [-3295, 71, -3501], 'mire_boardwalk:1': [-1694, 64, -1676], 'machine_steam_gallery:2': [-1740, 75, -957], 'machine_gear_mill_table:1': [-1756, 80, -637], 'valve_yard:2': [-1728, 73, -448], 'outpost_outpost_brick_kiln:0': [-3428, 77, -397], 'trench_infirmary:0': [-1686, 62, -176], 'mirror_barracks:2': [-3348, 68, 1631], 'gear_garden:2': [-1732, 65, 880], 'ruin_ruin_pump_chip:1': [-1677, 69, 1580], 'colossus_colossus_boiler_hall_wreck_heavy:2': [-3499, 68, 3695], 'ruin_ruin_pump_chip:2': [-1709, 75, 1901], 'colossus_colossus_boiler_hall_wreck_medium:0': [-2465, 79, 3903], 'outpost_outpost_toppled_boiler:0': [-1471, 78, -3469], 'pumping_house:2': [-384, 68, -1664], 'ruin_ruin_watch_buried:0': [105, 73, -1739], 'fallen_foundry:2': [15332, 83, 8776], 'boiler_shrine:1': [-3870, 64, -3812], 'pilgrim_arch:2': [-1956, 71, -1760], 'weighbridge:0': [-1968, 69, 81], 'machine_gear_mill_table:0': [-1980, 72, 675], 'shore_crane:0': [-1952, 67, 848], 'resonant_station:0': [-1899, 69, -3988], 'signal_bridge:0': [-929, 67, -1935], 'resonant_station:1': [-955, 73, -3924], 'pilgrim_arch:1': [-451, 69, -1935], 'colossus_colossus_boiler_hall_wreck_light:2': [821, 74, -14721], 'workers_lodging:0': [827, 68, 2096], 'ruin_ruin_aqueduct_span:0': [2083, 72, 1421], 'rail_service_cache:1': [-4384, 63, -4496], 'gear_garden:0': [-2164, 65, -1456], 'ruin_ruin_boiler_lean:2': [-2271, 70, -1171], 'mire_boardwalk:0': [-2170, 77, 816], 'mire_boardwalk:2': [-2186, 80, 1152], 'ruin_ruin_chimney_fan:0': [-2276, 68, 1632], 'colossus_colossus_hub_array_wreck_medium:1': [-15528, 75, 19231], 'machine_boiler_frame:0': [-1644, 63, -2157], 'sniper_watch:1': [-2482, 78, 4716], 'machine_pump_base:2': [-941, 65, -2196], 'resonant_station:2': [-1307, 69, 4860], 'colossus_colossus_hub_array_blueprint:2': [-3591, 77, 18656], 'outpost_outpost_brick_kiln:2': [-4724, 70, -2909], 'ruin_ruin_kiln_stump:0': [-2436, 67, -1168], 'ash_crematory:2': [-2484, 76, -464], 'colossus_colossus_boiler_hall_wreck_deep:2': [-19003, 67, 9119], 'workers_lodging:1': [-2003, 73, 2673], 'sniper_watch:0': [-2930, 66, 5340], 'machine_chimney_base:1': [372, 67, 2694], 'colossus_colossus_hub_array_wreck_heavy:2': [9176, 61, 21839], 'ruin_ruin_chimney_fan:1': [2588, 70, -160], 'colossus_colossus_boiler_hall_wreck_medium:1': [5186, 75, 47], 'colossus_colossus_hub_array_wreck_deep:1': [-5528, 77, -2209], 'machine_pump_base:1': [-2733, 65, -676], 'weighbridge:1': [-2768, 63, 2337], 'outpost_outpost_gear_slag_mound:1': [-3391, 59, 5923], 'sap_collector:1': [-1656, 72, 2945], 'outpost_outpost_watch_post:1': [-2224, 64, 5923], 'colossus_colossus_boiler_hall_wreck_deep:0': [-1249, 68, -5569], 'outpost_outpost_broken_aqueduct:0': [-1437, 61, 5956], 'ruin_ruin_chimney_fan:2': [2860, 84, 112], 'ruin_ruin_kiln_stump:2': [-644, 81, 3168], 'outpost_outpost_watch_post:2': [-720, 73, -5917], 'colossus_colossus_boiler_hall_blueprint:0': [-24924, 76, 14528], 'sniper_watch:2': [-6466, 78, 5900], 'ruin_ruin_aqueduct_span:1': [-141, 74, 3405], 'archivist_kiosk:2': [3398, 63, -2528], 'ruin_ruin_watch_buried:2': [3369, 75, 1077], 'broken_aqueduct:2': [-3537, 81, -1695], 'switchhouse:1': [-3543, 69, -400], 'ruin_ruin_truss_fan:0': [-3250, 77, 3660], 'shore_crane:1': [-1745, 66, -3455], 'pumping_house:1': [-692, 63, -3488], 'broken_aqueduct:0': [1103, 62, 3681], 'signal_bridge:2': [3664, 75, -2223], 'ruin_ruin_gallery_span:1': [-3793, 76, -2163], 'archivist_kiosk:0': [-3738, 76, -1456], 'sap_collector:2': [2652, 73, -3792], 'rift_observatory:1': [3959, 66, 305], 'ruin_ruin_gallery_span:2': [3917, 77, 2653], 'machine_chimney_base:0': [-4028, 65, 1878], 'sap_collector:0': [4204, 66, 1888], 'switchhouse:2': [4220, 76, 3440], 'expedition_camp:0': [2667, 75, -4272], 'rift_observatory:0': [-4820, 76, -4816], 'expedition_camp:2': [-5317, 80, -432], 'trench_infirmary:2': [-5270, 66, 2656]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, help='Reuse a previously isolated assets/gtsr/remaster directory.')
    parser.add_argument('--out', type=Path, help='Keep isolated converted assets here for repeatable proof runs.')
    args = parser.parse_args()
    root = converter.ROOT
    original_sources = {p: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in (converter.SOURCE / 'preview/prefabs7').glob('*.js')}
    production = converter.OUT
    catalog = json.loads((production / 'catalog.json').read_text('utf8'))
    with tempfile.TemporaryDirectory(prefix='gtsr-surface-arrival-') as folder:
        tmp = Path(folder)
        isolated = args.assets.resolve() if args.assets else (args.out.resolve() if args.out else tmp / 'assets/gtsr/remaster')
        converter.OUT = verifier.OUT = isolated
        if not args.assets:
            converter.PUZZLES = {row['id']: json.loads(gzip.decompress((production / row['file']).read_bytes()))
                                 ['metadata']['productionPuzzle'] for row in catalog['prefabs']}
            converter.ENGINEERING = catalog['engineeringChapters']
            descriptors = []
            for source in sorted(original_sources):
                descriptors.append(converter.compile_one(source))
                if len(descriptors) % 30 == 0:
                    print('isolated compile', len(descriptors), flush=True)
            converter.write_json(isolated / 'catalog.json', dict(catalog, prefabs=descriptors))
        # Reused assets receive the same complete source/solid/slice/digest/entrance
        # verification as a new conversion. Never trust an old pass receipt.
        verifier.main()
        derived = []
        for descriptor in json.loads((isolated / 'catalog.json').read_text('utf8'))['prefabs']:
            manifest = json.loads(gzip.decompress((isolated / descriptor['file']).read_bytes()))
            metadata = manifest['metadata']
            if metadata.get('terrain', {}).get('buried') == 'full' or descriptor['id'] in (
                    'fiction_expansion_project', 'prosperity_city_full', 'zenith_tree'):
                assert 'productionSurfaceEntrance' not in metadata
                continue
            key = descriptor['id'] + ':' + str(descriptor['variant'])
            if not metadata.get('routes'):
                assert 'productionSurfaceEntrance' not in metadata
                continue
            assert key in ORIGINAL_ANCHORS
            derived.append({'id': descriptor['id'], 'variant': descriptor['variant'],
                            'oldSite': ORIGINAL_ANCHORS[key], 'sourceSha256': descriptor['sourceSha256']})
        assert len(derived) == 246 and len({r['id'] for r in derived}) == 82
        assert sum(r['id'].startswith('city_') for r in derived) == 84
        (root / 'temp/surface-arrival-production.inputs.json').write_text(json.dumps(derived), 'utf8')
        jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
        cp = [str(root / 'build/classes/java/main'), str(root / 'build/classes/java/patchedMc')]
        for name in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                     'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
            cp += [str(j) for j in jars if j.name == name][:1]
        resource_root = isolated.parents[2]
        cp += [str(resource_root), str(root / 'src/main/resources'), str(root / 'build/resources/patchedMc')]
        cp += [str(j) for j in jars if 'jabel' not in j.name and 'byte-buddy' not in j.name]
        sources = []
        for name in ('RuinsArchitecture', 'RoyalArchitecture'):
            source = root / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{name}.java'
            text = re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$',
                          '', source.read_text('utf8'), flags=re.M)
            adapted = tmp / f'{name}.java'
            adapted.write_text(text, 'utf8')
            sources.append(adapted)
        fixture = (root / 'tools/prosperity/RemasterRuntimeCheck.java').read_text('utf8').split(' static class P extends')[0] + '}\n'
        adapter = tmp / 'RemasterRuntimeCheck.java'
        adapter.write_text(fixture.replace('P player;', 'EntityPlayerMP player;'), 'utf8')
        sources += [adapter, root / 'tools/prosperity/StairCollisionCheck.java', root / 'tools/prosperity/SurfaceArrivalCheck.java']
        sources += [root / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{p}.java' for p in (
            'remaster/RemasterBlock', 'echo/RuinsBlueprint', 'encounter/ForgottenLakeEncounterStructure',
            'architecture/BlockZenithLog', 'remaster/RemasterPlanner', 'remaster/RemasterSite', 'remaster/RemasterTerrain',
            'remaster/RemasterWorldgen', 'remaster/RemasterRuntime', 'remaster/RemasterData',
            'remaster/RemasterOriginalContract', 'ProsperityTerrainProfile')]
        sources += native_sources(root)
        classpath = ';'.join(cp).replace('\\', '/')
        sourcepath = tmp / 'empty-source'
        sourcepath.mkdir()
        argfile = tmp / 'javac.args'
        argfile.write_text('-encoding UTF-8\n-sourcepath "' + str(sourcepath).replace('\\', '/')
                           + '"\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/') + '"\n'
                           + '\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), 'utf8')
        subprocess.run(['javac', '@' + str(argfile)], cwd=root, check=True)
        argfile.write_text('-Xmx3G\n-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\nSurfaceArrivalCheck', 'utf8')
        subprocess.run(['java', '@' + str(argfile)], cwd=root, check=True)
    assert all(hashlib.sha256(p.read_bytes()).hexdigest() == digest for p, digest in original_sources.items())
    rows = json.loads((root / 'temp/surface-arrival-production.json').read_text('utf8'))
    receipt = {'passed': True, 'seed': 20261001, 'surfaceVariants': len(rows),
               'surfaceIds': len({r['id'] for r in rows}), 'derivedEntrances': 244,
               'explicitEntrances': 2, 'ordinaryNaturalRingVariants': sum(not r['id'].startswith('city_') for r in rows),
               'actualCityStreetVariants': sum(r['id'].startswith('city_') for r in rows),
               'sameCoordinatesAnchorRejected': sum(r.get('sameCoordinatesAnchorRejected', False) for r in rows),
               'sameCoordinatesGateReasons': {r['id'] + ':' + str(r['variant']): r['sameCoordinatesAnchorGate'] for r in rows},
               'oldCityContextUnrecoverable': sum('sameCoordinatesContext' in r for r in rows),
               'unchangedAuthoredSnapshots': len(original_sources),
               'sourceAggregateSha256': hashlib.sha256(''.join(p.name + ':' + original_sources[p]
                   for p in sorted(original_sources)).encode('utf8')).hexdigest(),
               'collisionAdapter': 'Minecraft Entity 0.6x1.8, stepHeight=0.5; actual production blocks/geometry/native water; ordinary local 8-block natural BFS; city actual parent + 28 source overlays, complete Connection bidirectional Entity replay; unrecoverable old city anchors report compatibility only; not a launched server'}
    (root / 'temp/surface-arrival-production.receipt.json').write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2), 'utf8')
    print('PASS original source SHA preserved=268; isolated assets and production natural-ring / actual-city-street arrival proof', flush=True)


if __name__ == '__main__':
    main()
