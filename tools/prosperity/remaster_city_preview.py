from native_terrain_fixture import native_sources, isolated_sourcepath
"""Invoke production Java geometry without booting or adapting Minecraft/FML."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def export_city():
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/classes/java/patchedMc'),
          str(ROOT / 'src/main/resources'), str(ROOT / 'build/resources/patchedMc')]
    cp += [str(jar) for jar in jars if 'jabel' not in jar.name and 'byte-buddy' not in jar.name]
    with tempfile.TemporaryDirectory(prefix='gtsr-city-preview-') as folder:
        tmp = Path(folder)
        sources = [ROOT / 'tools/prosperity/RemasterCityPreviewExport.java']
        sources += [ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/{name}.java'
                    for name in ('RemasterCatalog', 'RemasterPrefab', 'RemasterPlanner', 'RemasterSite', 'RemasterTerrain',
                                 'RemasterWorldgen', 'RemasterRuntime', 'RemasterSpawn', 'RemasterData',
                                 'RemasterEngineering', 'RemasterWitness', 'RemasterLoot', 'TileRemasterNode')]
        sources += [ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{name}.java'
                    for name in ('lore/HistoryProgress', 'echo/EchoKind', 'echo/EntityOldEcho', 'encounter/TileEntitySealedChest')]
        sources += native_sources(ROOT)
        args = tmp / 'javac.args'
        classpath = ';'.join(cp).replace('\\', '/')
        args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) + '"\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/')
                        + '"\n' + '\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf8')
        subprocess.run(['javac', '@' + str(args)], check=True)
        output = tmp / 'city.json'
        args.write_text('-Xmx2G\n-cp "' + str(tmp).replace('\\', '/') + ';' + classpath
                        + '"\nRemasterCityPreviewExport\n"' + str(output).replace('\\', '/') + '"', encoding='utf8')
        subprocess.run(['java', '@' + str(args)], check=True)
        return output.read_text(encoding='utf8')


if __name__ == '__main__':
    print(export_city())
