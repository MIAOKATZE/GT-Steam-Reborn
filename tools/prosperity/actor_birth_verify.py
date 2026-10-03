"""Read-only real Minecraft entity admission over sparse production geometry.

Synthetic prefab admission is separate from normal-six-biome legal anchor probes.
Does not boot a server, create support blocks, or change packaged resources.
"""
import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

from native_terrain_fixture import native_sources, isolated_sourcepath

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=ROOT / 'src/main/resources',
                        help='Classpath resource root containing assets/gtsr/remaster.')
    parser.add_argument('--report', type=Path, default=ROOT / 'temp/actor-birth-check.json')
    parser.add_argument('--mode', choices=('all', 'geometry', 'actual', 'resonant', 'residual'), default='all')
    args = parser.parse_args()
    resource_root = args.resources.resolve()
    asset_root = resource_root / 'assets/gtsr/remaster'
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc']
    for name in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                 'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        cp += [p for p in jars if p.name == name][:1]
    cp += [resource_root, ROOT / 'src/main/resources', ROOT / 'build/resources/patchedMc']
    cp += [p for p in jars if 'jabel' not in p.name and 'byte-buddy' not in p.name]
    base = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
    production = list(dict.fromkeys(native_sources(ROOT) + list((base / 'remaster').glob('*.java')) +
        [base / (name + '.java') for name in ('echo/EchoKind', 'echo/EntityOldEcho',
          'encounter/TileEntitySealedChest', 'encounter/BlockSealedChest', 'architecture/BlockZenithLog',
          'lore/HistoryProgress', 'lore/HistoryEvents')]))
    factories = [base / ('architecture/' + name + '.java') for name in ('RuinsArchitecture', 'RoyalArchitecture')]
    fixture = ROOT / 'tools/prosperity/ActorBirthCheck.java'
    pinned = production + factories + [fixture, Path(__file__), ROOT / 'tools/prosperity/remaster_convert.py']
    pinned += list(asset_root.rglob('*.*'))
    digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    before = {p.as_posix(): digest(p) for p in pinned}
    result = {'scope': 'Independent body admission, loaded local geometry; pairwise AABB overlap is reported separately. '
              'Synthetic probes are not normal world coverage. Native foundation follows inclusive heightAt and '
              'naturalWaterTopAt; biome surface replacements, bedrock shape, grotto and decoration remain unknown.',
              'sourceSHA256Before': before, 'cases': {}}
    with tempfile.TemporaryDirectory(prefix='gtsr-actor-birth-') as folder:
        tmp = Path(folder)
        sources = production + [fixture]
        for source in factories:
            body = re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$',
                          '', source.read_text(encoding='utf8'), flags=re.M)
            target = tmp / source.name
            target.write_text(body, encoding='utf8')
            sources.append(target)
        classpath = ';'.join(p.as_posix() for p in cp)
        javac_args = tmp / 'javac.args'
        javac_args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) +
            '"\n-cp "' + classpath + '"\n-d "' + tmp.as_posix() + '"\n' +
            '\n'.join('"' + p.as_posix() + '"' for p in sources), encoding='utf8')
        compiled = subprocess.run(['javac', '@' + str(javac_args)], capture_output=True, text=True)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.with_suffix('.compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf8')
        compiled.check_returncode()
        modes = ('geometry', 'actual', 'resonant', 'residual') if args.mode == 'all' else (args.mode,)
        for mode in modes:
            output = tmp / (mode + '.json')
            java_args = tmp / 'java.args'
            java_args.write_text('-Xmx1800m\n-Dactor.birth.report="' + output.as_posix() +
                '"\n-cp "' + tmp.as_posix() + ';' + classpath + '"\nActorBirthCheck\n' + mode, encoding='utf8')
            executed = subprocess.run(['java', '@' + str(java_args)], capture_output=True, text=True)
            args.report.with_suffix('.' + mode + '.log').write_text(executed.stdout + executed.stderr, encoding='utf8')
            executed.check_returncode()
            result['cases'][mode] = json.loads(output.read_text(encoding='utf8'))
    result['sourceChanged'] = [p for p, value in before.items() if digest(Path(p)) != value]
    result['sourceSnapshotStable'] = not result['sourceChanged']
    native_examples = {(row['sourceId'], row['variant'], row['index']): row
                       for name, case in result['cases'].items() if name != 'geometry'
                       for row in case['rows']}
    for row in result['cases'].get('geometry', {}).get('rows', []):
        if row.get('sourceGeometryAdmission') is False:
            example = native_examples.get((row['sourceId'], row['variant'], row['index']))
            row['normalExampleAdmission'] = example.get('sourceGeometryAdmission') if example else None
            row['classification'] = ('requires_native_support_example_verified' if example and
                                     example.get('sourceGeometryAdmission') else 'synthetic_admission_unknown')
    failures = []
    for name, case in result['cases'].items():
        if case['pairwiseOverlapCount']:
            failures.append(name + ': overlapping admitted bodies')
        if name == 'geometry':
            if case['confirmedGeometryBlockers']:
                failures.append(name + ': source geometry blocks every candidate')
            case['syntheticAdmissionUnknown'] = case['sourceGeometryRejected']
            if args.mode == 'all' and any(row.get('sourceGeometryAdmission') is False and
                    row.get('normalExampleAdmission') is not True for row in case['rows']):
                failures.append(name + ': rejected synthetic birth has no verified native example')
        elif case['sourceGeometryRejected']:
            failures.append(name + ': native admission rejected planned entities')
    if result['sourceChanged']:
        failures.append('source snapshot changed during verification')
    result['semanticFailures'] = failures
    result['semanticPassed'] = not failures
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps({'sourceSnapshotStable': result['sourceSnapshotStable'], 'semanticPassed': not failures,
        'cases': {name: {k: case[k] for k in ('prefabs', 'plannedSpawns', 'sourceGeometryAdmitted',
            'sourceGeometryRejected', 'productionSkipped', 'actualPlannedAnchorUnknown', 'pairwiseOverlapCount')}
            for name, case in result['cases'].items()}}))
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
