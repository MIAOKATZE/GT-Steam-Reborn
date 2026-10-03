"""Execute MC/NBT fixture; only offline FML registration startup is removed from temporary factory copies."""
import re
import subprocess
import sys
import os
import tempfile
from pathlib import Path
from remaster_convert import ROOT
from native_terrain_fixture import native_sources, isolated_sourcepath


def main():
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [str(ROOT / 'build/classes/java/main'), str(ROOT / 'build/classes/java/patchedMc')]
    # Prefer the Minecraft-era dependencies, then discover the remaining mod runtime jars.
    for filename in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                     'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        matches = [jar for jar in jars if jar.name == filename]
        if matches:
            cp.append(str(matches[0]))
    cp += [str(ROOT / 'src/main/resources'), str(ROOT / 'build/resources/patchedMc')]
    cp += [str(jar) for jar in jars if 'jabel' not in jar.name and 'byte-buddy' not in jar.name]
    with tempfile.TemporaryDirectory(prefix='gtsr-runtime-check-') as folder:
        tmp = Path(folder)
        factories = []
        for name in ('RuinsArchitecture', 'RoyalArchitecture'):
            source = ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{name}.java'
            text = source.read_text(encoding='utf8')
            text = re.sub(r'^\s*GameRegistry\.registerBlock\([^\n]+\);\s*$', '', text, flags=re.M)
            text = re.sub(r'^\s*CreativeTabManager\.addItemToTab\([^\n]+\);\s*$', '', text, flags=re.M)
            target = tmp / f'{name}.java'
            target.write_text(text, encoding='utf8')
            factories.append(str(target))
        # Isolated players have no network handler. Remove channel construction alone: send's
        # production handler guard and every history/NBT/achievement method remain unchanged.
        network = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/lore/LoreNetwork.java'
        network_copy = tmp / 'LoreNetwork.java'
        network_copy.write_text(network.read_text(encoding='utf8').replace(
            'NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_lore")', 'null'), encoding='utf8')
        factories.append(str(network_copy))
        args = tmp / 'javac.args'
        classpath = ';'.join(cp).replace('\\', '/')
        args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) + '"\n-cp "' + classpath + '"\n-d "' + str(tmp).replace('\\', '/')
                        + '"\n' + '\n'.join('"' + p.replace('\\', '/') + '"' for p in factories + [str(p) for p in native_sources(ROOT)] +
                          [str(ROOT / 'tools/prosperity/RemasterRuntimeCheck.java'), str(ROOT / 'tools/prosperity/OriginalContractCheck.java')] +
                          [str(ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/{name}.java')
                           for name in ('RemasterRuntime', 'RemasterEngineering', 'RemasterWitness', 'RemasterLoot', 'TileRemasterNode', 'RemasterSpawn', 'RemasterBlock', 'RemasterWorldgen', 'RemasterTerrain', 'RemasterPlanner', 'RemasterSite', 'RemasterData', 'RemasterOriginalContract')] +
                          [str(ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{package}/{name}.java')
                           for package, name in [('encounter','TileEntitySealedChest'),('encounter','BlockSealedChest'),
                                                 ('echo','EchoKind'),('echo','EntityOldEcho'),
                                                 ('encounter','ForgottenLakeEncounterStructure'),
                                                 ('architecture','BlockZenithLog'),('ruins','ProsperityDecorPlacer')]] +
                          [str(ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/lore/HistoryProgress.java'), str(ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/lore/HistoryEvents.java')]), encoding='utf8')
        if '--baseline' in sys.argv:
            text = args.read_text(encoding='utf8')
            for name in ('RemasterRuntime', 'HistoryEvents', 'HistoryProgress'):
                path = ROOT / ('src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/' + ('remaster/' if name == 'RemasterRuntime' else 'lore/') + name + '.java')
                text = text.replace(str(path).replace('\\', '/'), str(ROOT / ('temp/original-contract-baseline/' + name + '.java')).replace('\\', '/'))
            args.write_text(text, encoding='utf8')
        subprocess.run(['javac', '@' + str(args)], check=True)
        args.write_text('-cp "' + str(tmp).replace('\\', '/') + ';' + classpath + '"\nOriginalContractCheck', encoding='utf8')
        subprocess.run(['java', '@' + str(args)] + [arg for arg in sys.argv[1:] if arg != '--baseline'], check=True)


if __name__ == '__main__':
    main()
