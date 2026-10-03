from native_terrain_fixture import native_sources, isolated_sourcepath
"""Run CityGuardCheck against real production worldgen and MC classes; adapt FML factory startup only."""
import re
import subprocess
import tempfile
from pathlib import Path
from remaster_convert import ROOT


def main(include_voxels=False):
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/classes/java/patchedMc')]
    for filename in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                     'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        matches = [jar for jar in jars if jar.name == filename]
        if matches:
            cp.append(str(matches[0]))
    cp += [str(ROOT / 'src/main/resources'), str(ROOT / 'build/resources/patchedMc')]
    cp += [str(jar) for jar in jars if 'jabel' not in jar.name and 'byte-buddy' not in jar.name]
    with tempfile.TemporaryDirectory(prefix='gtsr-city-guard-check-') as folder:
        tmp = Path(folder)
        sources = []
        for name in ('RuinsArchitecture', 'RoyalArchitecture'):
            source = ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{name}.java'
            text = source.read_text(encoding='utf8')
            text = re.sub(r'^\s*GameRegistry\.registerBlock\([^\n]+\);\s*$', '', text, flags=re.M)
            text = re.sub(r'^\s*CreativeTabManager\.addItemToTab\([^\n]+\);\s*$', '', text, flags=re.M)
            target = tmp / f'{name}.java'
            target.write_text(text, encoding='utf8')
            sources.append(str(target))
        sources += [str(ROOT / f'tools/prosperity/{name}.java') for name in ('RemasterRuntimeCheck', 'CityGuardCheck', 'TerrainPlacementCheck')]
        if include_voxels:
            sources.append(str(ROOT / 'tools/RemasterWorldgenCheck.java'))
        sources += [str(ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/{name}.java')
                    for name in ('RemasterRuntime', 'RemasterOriginalContract', 'RemasterEngineering', 'RemasterWitness', 'RemasterLoot', 'TileRemasterNode',
                                 'RemasterWorldgen', 'RemasterSite', 'RemasterData', 'RemasterTerrain', 'RemasterPlanner', 'RemasterSpawn')]
        sources.append(str(ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/lore/HistoryProgress.java'))
        sources += [str(ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{name}.java')
                    for name in ('echo/EchoKind', 'echo/EntityOldEcho', 'encounter/TileEntitySealedChest')]
        sources += [str(p) for p in native_sources(ROOT)]
        args = tmp / 'javac.args'
        classpath = ';'.join(cp).replace('\\', '/')
        args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) + '"\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/')
                        + '"\n' + '\n'.join('"' + p.replace('\\', '/') + '"' for p in sources), encoding='utf8')
        subprocess.run(['javac', '@' + str(args)], check=True)
        args.write_text('-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\nCityGuardCheck', encoding='utf8')
        subprocess.run(['java', '@' + str(args)], check=True)
        args.write_text('-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\nTerrainPlacementCheck', encoding='utf8')
        subprocess.run(['java', '@' + str(args)], check=True)
        if include_voxels:
            args.write_text('-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\nRemasterWorldgenCheck', encoding='utf8')
            subprocess.run(['java', '@' + str(args)], check=True)


if __name__ == '__main__':
    main()
