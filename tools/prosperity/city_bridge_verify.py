"""Fresh native terrain and bridge sources, source-pinned real Minecraft collision proof."""
import hashlib
import argparse
import json
import subprocess
import sys
from pathlib import Path
from native_terrain_fixture import native_sources, isolated_sourcepath
from remaster_convert import ROOT


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resources', type=Path, default=ROOT / 'src/main/resources')
    parser.add_argument('--out', type=Path, default=ROOT / 'temp/city-bridge-verification')
    options, java_options = parser.parse_known_args()
    folder = options.out.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    resource_root = options.resources.resolve()
    if not resource_root.exists():
        raise RuntimeError('Resource root is missing: ' + str(resource_root))
    resource_pins = {p.relative_to(resource_root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                     for p in (resource_root / 'assets/gtsr/remaster').rglob('*') if p.is_file()}
    cp = [ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc', resource_root,
          ROOT / 'src/main/resources', ROOT / 'build/resources/patchedMc']
    cp += [j for j in jars if 'jabel' not in j.name and 'byte-buddy' not in j.name]
    adapter = folder / 'RemasterRuntimeCheck.java'
    adapter.write_text((ROOT / 'tools/prosperity/RemasterRuntimeCheck.java').read_text('utf8')
                       .split(' static class P extends')[0].replace('P player;', 'EntityPlayerMP player;') + '}\n', 'utf8')
    base = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
    sources = [adapter, ROOT / 'tools/prosperity/CityBridgeCollisionCheck.java']
    sources += [base / ('remaster/' + n + '.java') for n in
                ('RemasterRuntime', 'RemasterOriginalContract', 'RemasterEngineering', 'RemasterWitness',
                 'RemasterLoot', 'TileRemasterNode', 'RemasterSpawn', 'RemasterBlock', 'RemasterBlocks',
                 'RemasterWorldgen', 'RemasterTerrain', 'RemasterCatalog', 'RemasterPrefab')]
    sources += [base / (n + '.java') for n in
                ('encounter/TileEntitySealedChest', 'encounter/BlockSealedChest', 'echo/EchoKind',
                 'echo/EntityOldEcho', 'encounter/ForgottenLakeEncounterStructure', 'architecture/BlockZenithLog',
                 'architecture/RuinsArchitecture', 'architecture/RoyalArchitecture', 'ruins/ProsperityDecorPlacer',
                 'lore/HistoryProgress')]
    sources += native_sources(ROOT)
    sources = list(dict.fromkeys(sources))
    pins = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p != adapter}
    (folder / 'sources-before.json').write_text(json.dumps(pins, indent=2), 'utf8')
    classpath = ';'.join(str(p).replace('\\', '/') for p in cp)
    args = folder / 'compile.args'
    compile_sources = []
    for source in sources:
        if source.stem in ('RuinsArchitecture', 'RoyalArchitecture'):
            # Standalone JVM has no FML LaunchClassLoader; omit registry/UI calls only.
            # All production block factories and collision methods remain byte-for-byte source-identical.
            adapted = folder / source.name
            body = source.read_text('utf8')
            body = '\n'.join(line for line in body.splitlines()
                             if 'GameRegistry.registerBlock(' not in line and 'CreativeTabManager.addItemToTab(' not in line)
            adapted.write_text(body, 'utf8')
            compile_sources.append(adapted)
        else:
            compile_sources.append(source)
    args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(folder) + '"\n-cp "' + classpath +
                    '"\n-d "' + folder.as_posix() + '"\n' +
                    '\n'.join('"' + p.as_posix() + '"' for p in compile_sources), 'utf8')
    compiled = subprocess.run(['javac', '@' + str(args)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (folder / 'compile.log').write_bytes(compiled.stdout)
    compiled.check_returncode()
    run_args = folder / 'run.args'
    run_args.write_text('-Xmx3G\n-cp "' + folder.as_posix() + ';' + classpath +
                       '"\nCityBridgeCollisionCheck\n--report\n"' + (folder / 'report.json').as_posix() +
                       '"\n' + '\n'.join(java_options), 'utf8')
    run = subprocess.run(['java', '@' + str(run_args)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (folder / 'run.log').write_bytes(run.stdout)
    print(run.stdout.decode('utf8', errors='replace'))
    after = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p != adapter}
    changed = [p for p in pins if pins[p] != after[p]]
    (folder / 'sources-after.json').write_text(json.dumps(after, indent=2), 'utf8')
    report = folder / 'report.json'
    if report.exists():
        data = json.loads(report.read_text('utf8'))
        if isinstance(data, list):
            data = {'cities': data}
        data['sourceSHA256'] = pins
        data['sourceChangedDuringRun'] = changed
        data['javaExitCode'] = run.returncode
        data['compilerExitCode'] = compiled.returncode
        data['resourceRoot'] = resource_root.as_posix()
        data['resourceSHA256'] = resource_pins
        resource_changed = [name for name, digest in resource_pins.items()
                            if hashlib.sha256((resource_root / name).read_bytes()).hexdigest() != digest]
        data['resourceChangedDuringRun'] = resource_changed
        report.write_text(json.dumps(data, indent=2), 'utf8')
    if changed:
        raise RuntimeError('Source changed during proof: ' + ', '.join(changed))
    if report.exists() and resource_changed:
        raise RuntimeError('Resources changed during proof')
    run.check_returncode()


if __name__ == '__main__':
    main()
