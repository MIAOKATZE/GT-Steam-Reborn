"""Candidate-jar-only construction economy contracts; never overlays product classes."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OUT = ROOT / 'build/critical-rc2-economy-contract'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
NAMES = ['CriticalConstructionLedgerContract', 'CriticalConstructionEconomyContract']
SOURCES = [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in (
    'common/critical/CriticalConstructionLedger.java', 'common/critical/CriticalConstructionEconomy.java',
    'common/critical/recipe/CriticalShellCatalog.java', 'loader/recipes/CriticalManufacturingRecipes.java',
    'loader/recipes/CriticalMachineRecipes.java')]


def registration_gates():
    text = SOURCES[-2].read_text(encoding='utf-8')
    flat = re.sub(r'\s+', '', text)
    calls = list(re.finditer(r'controller\(\s*GTSRItemList\.(Critical\w+),', text))
    expected = ['CriticalLoom', 'CriticalSolar', 'CriticalTurbine', 'CriticalBattery', 'CriticalSun']
    assert [match.group(1) for match in calls] == expected
    for index, match in enumerate(calls):
        part = text[match.end():calls[index + 1].start() if index + 1 < len(calls) else text.index('private static void controller')]
        body = part.split('new Object[] {', 1)[1].split('},', 1)[0]
        depth, items = 0, 1
        for char in body:
            if char in '({[':
                depth += 1
            elif char in ')}]':
                depth -= 1
            elif char == ',' and depth == 0:
                items += 1
        assert items == 16, (match.group(1), items)
    assert 'research.get(1),48000,64,(int)TierEU.RECIPE_UHV,1,items' in flat
    fluids = text.split('new FluidStack[] {', 1)[1].split('},', 1)[0]
    assert all(name in fluids for name in ['INDALLOY_140', 'Naquadria', 'third', 'Polybenzimidazole'])
    assert 'LiquidHelium.getFluidOrGas(32000)' in text and '.Hull_MAX.get(' in text and 'Hull_MAXV' not in text
    assert 'CriticalManufacturingRecipes.register();' in SOURCES[-1].read_text(encoding='utf-8')
    assert '.itemOutputs(CriticalShellCatalog.shell(family.identities.get(identity),16))' in flat
    return {'controllerRecipes': expected, 'itemSlotsEach': 16, 'fluidSlotsEach': 4,
            'research': [48000, 64, 'UHV', 1], 'shellBatchOutput': 16, 'type': 'static registration gates'}


def main():
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()
    OUT = args.out.resolve()
    candidate = args.jar.resolve()
    candidate_hash = hashlib.sha256(candidate.read_bytes()).hexdigest()
    registrations = registration_gates()
    classes = OUT / 'jar-contract-classes'
    classes.mkdir(parents=True, exist_ok=True)
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    deps = [p for p in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix()]
    cp = os.pathsep.join(map(str, [classes, candidate,
        ROOT / 'build/classes/java/patchedMc', gt, *deps]))

    def run(exe, name, args):
        argfile = OUT / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
        result = subprocess.run([str(JAVA / exe), '@' + str(argfile)], cwd=ROOT, capture_output=True,
            encoding='utf-8', errors='replace', timeout=60, creationflags=subprocess.CREATE_NO_WINDOW)
        text = result.stdout + result.stderr
        (OUT / (name + '.log')).write_text(text, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(text)
        return text.strip()

    empty = OUT / 'empty'
    empty.mkdir(exist_ok=True)
    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp, '-d', classes,
        *[ROOT / 'tools/tests' / (name + '.java') for name in NAMES]])
    results = {name: run('java.exe', name, ['-cp', cp, 'com.miaokatze.gtsr.common.critical.' + name, candidate]) for name in NAMES}
    assert hashlib.sha256(candidate.read_bytes()).hexdigest() == candidate_hash
    economy_output = results['CriticalConstructionEconomyContract']
    work_steps = {name: int(count) for name, count in re.findall(r'^STEPS (\S+)=(\d+)$', economy_output, re.M)}
    row_items = {}
    for row, identity, count in re.findall(r'^ITEM (\S+) (.+)=(\d+)$', economy_output, re.M):
        row_items.setdefault(row, {})[identity] = int(count)
    (OUT / 'verification.json').write_text(json.dumps({'productOverlay': False, 'jar': str(candidate),
        'jarSHA256': candidate_hash, 'results': results, 'registrations': registrations,
        'workSteps': work_steps, 'perStepItems': row_items}, indent=2), encoding='utf-8')
    print(json.dumps({name: re.findall(r'PASS .*', output)[-1] for name, output in results.items()}))


if __name__ == '__main__':
    main()
