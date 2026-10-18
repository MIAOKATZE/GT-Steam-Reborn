"""Audit 13 production steam structure definitions without launching Minecraft.

Loads exact GT205/StructureLib 1.4.45 and verifies real getStructureDefinition
chains. Unsafe instances and registry fixtures intentionally exclude world checks,
Mixin application, actual placement, particles, GL and complete BR rendering.
"""
from pathlib import Path
import argparse
import collections
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
NAMES = ('MTEAirCompressor MTEAmmoniaPlant MTEAtmosphericCentrifuge '
         'MTEGearSteamCompressor MTELargeCokeOven MTELargeGeothermalSteamBoiler '
         'MTELargeSolarOverpressureArray MTELargeSteamFurnace '
         'MTEReinforcedBrickBlastFurnace MTESiemensMartinFurnace MTESteamFluidDrill '
         'MTEVeinSteamPyrolyzer MTECrustSteamBorer').split()
MACHINES = ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def shape_audit(name):
    source = (MACHINES / (name + '.java')).read_text(encoding='utf-8')
    if 'String[][] SHAPE_MAIN' in source:
        start = source.index('{', source.index('String[][] SHAPE_MAIN'))
    else:
        start = source.index('{', source.index('new String[][]', source.index('.addShape(')))
    stack = []
    for match in re.finditer(r'"(?:\\.|[^"\\])*"|[{}]', source[start:]):
        token = match.group()
        if token == '{':
            value = []
            if stack:
                stack[-1].append(value)
            stack.append(value)
        elif token == '}':
            value = stack.pop()
            if not stack:
                shape = value
                break
        else:
            stack[-1].append(json.loads(token))
    counts = collections.Counter(''.join(row for layer in shape for row in layer))
    markers = [[x, y, z] for y, layer in enumerate(shape) for z, row in enumerate(layer)
               for x, char in enumerate(row) if char == '~']
    offsets = [int(re.search(r'\b' + part + r'_OFF_SET\s*=\s*(\d+)', source).group(1))
               for part in ['HORIZONTAL', 'VERTICAL', 'DEPTH']]
    defined = set(re.findall(r"\.addElement\(\s*'(.)'", source))
    undefined = sorted(set(counts) - defined - {' ', '~'})
    width = {len(row) for layer in shape for row in layer}
    depth = {len(layer) for layer in shape}
    shell_symbols = ('BE' if name == 'MTEAmmoniaPlant' else 'A' if name in {
        'MTELargeGeothermalSteamBoiler', 'MTELargeSolarOverpressureArray',
        'MTEReinforcedBrickBlastFurnace'} else 'B')
    declared = int(re.search(r'REPLACEABLE_CASING_POSITIONS\s*=\s*(\d+)', source).group(1))
    actual = sum(counts[char] for char in shell_symbols)
    assert markers == [offsets] and not undefined and len(width) == len(depth) == 1, name
    assert actual == declared, (name, actual, declared)
    assert 'hatchWithCandidates' not in source
    return {'class': name, 'status': 'PASS', 'dimensionsWHL': [next(iter(width)), len(shape), next(iter(depth))],
            'markerXYZ': markers[0], 'offsetXYZ': offsets, 'undefinedSymbols': undefined,
            'counts': dict(sorted(counts.items())), 'shellSymbols': shell_symbols,
            'replaceablePositions': declared, 'minimumCasings': max(1, min(8, declared // 10)),
            'sourceSHA256': sha(MACHINES / (name + '.java'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path,
                        default=ROOT / 'build/critical-v2-runtime-v12129/probe/classpath.txt')
    parser.add_argument('--output-dir', type=Path,
                        default=ROOT / 'build/structure-audit-v12143/steam')
    args = parser.parse_args()
    out = args.output_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    rows = [shape_audit(name) for name in NAMES]
    for name, key, expected in [('MTEGearSteamCompressor', 'pipe', 18),
                                ('MTELargeCokeOven', 'frame', 14),
                                ('MTELargeCokeOven', 'firebrick', 45)]:
        source = (MACHINES / (name + '.java')).read_text(encoding='utf-8')
        assert re.search(r'addCasingInfoExactly\(StatCollector.translateToLocal\("gtsr.tooltip.shared\.'
                         + key + r'"\),\s*' + str(expected) + r', false\)', source), (name, key)
    dependencies = [path for path in args.classpath_file.read_text().split(';') if Path(path).suffix == '.jar']
    dependencies = [path for path in dependencies
                    if ('/GT5-Unofficial/' not in path.replace('\\', '/') or '/5.09.54.205/' in path.replace('\\', '/'))
                    and ('/StructureLib/' not in path.replace('\\', '/') or '/1.4.45/' in path.replace('\\', '/'))
                    and ('/GTNHLib/' not in path.replace('\\', '/') or '/0.11.52/' in path.replace('\\', '/'))]
    gt = [Path(path) for path in dependencies if '/GT5-Unofficial/' in path.replace('\\', '/')]
    sl = [Path(path) for path in dependencies if '/StructureLib/' in path.replace('\\', '/')]
    assert len(gt) == len(sl) == 1
    renderer = ROOT / 'build/critical-machines-v12119/structure-client/red-v18-01/fixture/mods/blockrenderer6343-1.4.24.jar'
    expected_jars = {'gregtech.api.util.HatchElementBuilder': gt[0],
                     'com.gtnewhorizon.structurelib.structure.IStructureElementChain': sl[0],
                     'blockrenderer6343.integration.nei.StructureHacks': renderer}
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    production = ROOT / 'build/classes/java/main'
    cp = ';'.join(map(str, [classes, production, ROOT / 'build/classes/java/patchedMc',
                            ROOT / 'build/resources/patchedMc', ROOT / 'src/main/resources', renderer, *dependencies]))
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

    def run(label, tool, values):
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in values), encoding='utf-8')
        result = subprocess.run([str(java / (tool + '.exe')), '@' + str(argfile)], cwd=out,
                                capture_output=True, encoding='utf-8', errors='replace',
                                creationflags=subprocess.CREATE_NO_WINDOW)
        (out / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
        assert result.returncode == 0, label + '\n' + (result.stdout + result.stderr)[-7000:]
        return result.stdout

    sources = [Path(__file__).with_name('SteamStructureDefinitions.java'), *Path(__file__).with_name('gates').glob('*.java')]
    run('compile', 'javac', ['-J-Xmx512m', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])
    properties = ['-Daudit.sha.' + name + '=' + sha(path) for name, path in expected_jars.items()]
    properties += ['-Daudit.productionSource=' + production.as_uri() + '/']
    compiled = {}
    for name in NAMES:
        path = production / 'com/miaokatze/gtsr/common/machine' / (name + '.class')
        compiled[name] = {'path': str(path), 'sha256': sha(path),
                          'olderThanSource': path.stat().st_mtime < (MACHINES / (name + '.java')).stat().st_mtime}
        properties.append('-Daudit.classSha.' + name + '=' + sha(path))
    output = run('run', 'java', ['-Xmx512m', '-Dfile.encoding=UTF-8', *properties, '-cp', cp,
                               'SteamStructureDefinitions', 'definitions'])
    matches = re.findall(r'^MACHINE (\w+) PASS hatchChars=(\d+) nativeHatches=(\d+) tierCasingQueries=(\d+)$',
                         output, flags=re.M)
    assert len(matches) == len(NAMES) and 'RESULT machine-definitions PASS machines=13' in output
    runtime_sources = [dict(zip(('class', 'location', 'sha256'), match)) for match in
                       re.findall(r'^SOURCE (\S+) (\S+) SHA256=([0-9a-f]{64})$', output, flags=re.M)]
    assert len(runtime_sources) == 3
    for actual in runtime_sources:
        expected = expected_jars[actual['class']]
        assert expected.name in actual['location'] and sha(expected) == actual['sha256']
    assert len(re.findall(r'^PRODUCTION ', output, flags=re.M)) == len(NAMES)
    for row, (name, chars, branches, queries) in zip(rows, matches):
        assert row['class'] == name
        row.update({'hatchCharacters': int(chars), 'nativeHatchBranches': int(branches),
                    'tierCasingQueries': int(queries), 'compiledClass': compiled[name]})
    receipt = {'status': 'PASS', 'machines': rows, 'runtimeSources': runtime_sources,
               'totals': {'machines': len(rows), 'hatchCharacters': sum(row['hatchCharacters'] for row in rows),
                          'nativeHatchBranches': sum(row['nativeHatchBranches'] for row in rows),
                          'tierCasingQueries': sum(row['tierCasingQueries'] for row in rows)},
               'tooltipCorrections': {'GearSteamCompressor.pipe': [20, 18],
                                      'LargeCokeOven.frame': [12, 14], 'LargeCokeOven.firebrick': [37, 45]},
               'boundary': 'Real production getStructureDefinition methods and native GT/SL BlocksToPlace chains; '
                           'all hatch branches predicate-only, each tier query returns one explicit casing and no hatch.',
               'limitations': ['Unsafe machine instances with fixture registries; no complete machine initialization.',
                               'No Mixin transformation, world checks, survival placement, particles, GL, or complete BR rendering.',
                               'Separate actual BR consumer attempt hit fixture SRG ItemStack.func_77946_l mapping boundary; NOTRUN beyond it.',
                               'olderThanSource marks compiled classes predating source edits; tooltip edits do not alter structure definitions.',
                               'SteamOutputHatch dormant adder mismatch has no production call site; not changed.'],
               'repeatCommand': 'python -X utf8 tools/nei/verify_steam_structure_definitions.py'}
    (out / 'checks.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': receipt['status'], 'totals': receipt['totals'], 'checks': str(out / 'checks.json')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
