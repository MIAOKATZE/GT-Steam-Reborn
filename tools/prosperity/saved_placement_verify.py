"""Fresh normal-six-biome saved-site regression. --compile-only never reports runtime PASS."""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

from native_terrain_fixture import native_sources, isolated_sourcepath

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compile-only', action='store_true')
    parser.add_argument('--report', type=Path, default=ROOT / 'temp/saved-placement-check.json')
    args = parser.parse_args()
    report = args.report.resolve()
    report.parent.mkdir(parents=True, exist_ok=True)
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc']
    for name in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                 'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        cp += [p for p in jars if p.name == name][:1]
    cp += [ROOT / 'src/main/resources', ROOT / 'build/resources/patchedMc']
    cp += [p for p in jars if 'jabel' not in p.name and 'byte-buddy' not in p.name]
    base = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
    sources = [ROOT / 'tools/prosperity/SavedPlacementCheck.java',
               ROOT / 'tools/prosperity/RemasterRuntimeCheck.java']
    sources += [base / ('remaster/' + name + '.java') for name in (
        'RemasterRuntime', 'RemasterOriginalContract', 'RemasterEngineering', 'RemasterWitness',
        'RemasterLoot', 'TileRemasterNode', 'RemasterSpawn', 'RemasterBlock', 'RemasterBlocks',
        'RemasterCatalog', 'RemasterPrefab', 'RemasterWorldgen', 'RemasterTerrain')]
    sources += [base / (name + '.java') for name in (
        'encounter/TileEntitySealedChest', 'encounter/BlockSealedChest', 'echo/EchoKind',
        'echo/EntityOldEcho', 'encounter/ForgottenLakeEncounterStructure', 'architecture/BlockZenithLog',
        'ruins/ProsperityDecorPlacer', 'lore/HistoryProgress', 'architecture/RuinsArchitecture',
        'architecture/RoyalArchitecture')]
    sources += native_sources(ROOT)
    sources = list(dict.fromkeys(sources))
    asset_root = ROOT / 'src/main/resources/assets/gtsr/remaster'
    pins = sources + [Path(__file__), ROOT / 'tools/prosperity/native_terrain_fixture.py']
    pins += sorted(p for p in asset_root.rglob('*') if p.is_file())
    before = {p.relative_to(ROOT).as_posix(): digest(p) for p in pins}
    receipt = {'compileOnly': args.compile_only, 'MCExecuted': False,
               'sourceSHA256Before': before, 'freshSourceCount': len(sources)}
    exit_code = 0
    try:
        with tempfile.TemporaryDirectory(prefix='gtsr-saved-placement-') as folder:
            tmp = Path(folder)
            copies = []
            for source in sources:
                target = tmp / source.name
                target.write_bytes(source.read_bytes())
                copies.append(target)
            classpath = os.pathsep.join(p.as_posix() for p in cp)
            compile_args = tmp / 'compile.args'
            compile_args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) +
                '"\n-cp "' + classpath + '"\n-d "' + tmp.as_posix() + '"\n' +
                '\n'.join('"' + p.as_posix() + '"' for p in copies), encoding='utf8')
            compiled = subprocess.run(['javac', '@' + str(compile_args)], cwd=ROOT,
                                      capture_output=True, text=True)
            report.with_suffix('.compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf8')
            receipt['compileExit'] = compiled.returncode
            exit_code = compiled.returncode
            if exit_code == 0 and not args.compile_only:
                # The Java process writes only this unique report after every required case passes.
                current_report = tmp / 'current-report.json'
                run_args = tmp / 'run.args'
                run_args.write_text('-Xmx3G\n-cp "' + tmp.as_posix() + os.pathsep + classpath +
                    '"\nSavedPlacementCheck\n"' + current_report.as_posix() + '"', encoding='utf8')
                receipt['MCExecuted'] = True
                run = subprocess.run(['java', '@' + str(run_args)], cwd=ROOT,
                                     capture_output=True, text=True)
                report.with_suffix('.mc.log').write_text(run.stdout + run.stderr, encoding='utf8')
                receipt['MCExit'] = run.returncode
                exit_code = run.returncode
                print(run.stdout[-1800:])
                if run.returncode:
                    print(run.stderr[-3000:])
                elif not current_report.exists():
                    receipt['failure'] = 'Java returned zero without a current report'
                    exit_code = 1
                else:
                    result = json.loads(current_report.read_text(encoding='utf8'))
                    receipt['caseReport'] = result
                    if (result.get('status') != 'PASS' or result.get('assertions', 0) < 60
                            or result.get('majorRecords') != 1 or result.get('savedCityRecords') != 29
                            or result.get('realChildPlans') != 28 or result.get('untouchedChildPlans') != 26
                            or result.get('legacyFlatRoadColumns') != 320):
                        receipt['failure'] = 'missing or empty required saved-site/city/geometry cases'
                        exit_code = 1
    except Exception as failure:
        receipt['failure'] = repr(failure)
        exit_code = 1
    after = {p.relative_to(ROOT).as_posix(): digest(p) for p in pins}
    receipt['sourceSHA256After'] = after
    receipt['sourceChanged'] = [name for name in before if before[name] != after[name]]
    if receipt['sourceChanged']:
        exit_code = 1
    receipt['exit'] = exit_code
    receipt['status'] = 'FAIL' if exit_code else ('COMPILE_READY' if args.compile_only else 'PASS')
    report.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print('saved-placement ' + receipt['status'] + ' exit=' + str(exit_code) +
          ' sourceChanged=' + str(receipt['sourceChanged']))
    if exit_code:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
