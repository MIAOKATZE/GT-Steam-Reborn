"""Reproduce the old hatch catalog leak, then verify native GT/StructureLib/BlockRenderer contracts.

No game launch or GL rendering. World/placement-environment bootstrap is gated; the actual
GT builder, StructureLib chain and unmodified BlockRenderer stack consumer execute.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
UTILITY = 'src/main/java/com/miaokatze/gtsr/common/structure/GTSRStructureUtility.java'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8')


def native_calls(text):
    """Remove only the wrapper and its redundant candidate-type arguments."""
    pattern = re.compile(r'(?:GTSRStructureUtility\.)?hatchWithCandidates\(')
    count = 0
    while match := pattern.search(text):
        depth, quote, escaped, comma = 1, None, False, None
        for pos in range(match.end(), len(text)):
            char = text[pos]
            if quote:
                if escaped:
                    escaped = False
                elif char == '\\':
                    escaped = True
                elif char == quote:
                    quote = None
                continue
            if char in '\"\'':
                quote = char
            elif char == '(':
                depth += 1
            elif char == ')':
                depth -= 1
                if depth == 0:
                    break
            elif char == ',' and depth == 1 and comma is None:
                comma = pos
        assert comma is not None and depth == 0
        text = text[:match.start()] + text[match.end():comma].strip() + '.build()' + text[pos + 1:]
        count += 1
    return text, count


def java_tokens(text):
    text = re.sub(r'^import .+;\s*', '', text, flags=re.M)
    return [token for token in re.findall(
        r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[\w$]+|[^\s]', text, flags=re.S)
        if not token.startswith(('//', '/*'))]


def audit(baseline):
    files = git('grep', '-l', 'hatchWithCandidates', baseline, '--', 'src/main/java/').splitlines()
    result = []
    for entry in files:
        path = entry.split(':', 1)[1]
        if path == UTILITY:
            continue
        before = git('show', baseline + ':' + path)
        expected, count = native_calls(before)
        actual = (ROOT / path).read_text(encoding='utf-8')
        assert java_tokens(expected) == java_tokens(actual), 'Non-mechanical production change: ' + path
        result.append({'path': path, 'calls': count})
    assert sum(row['calls'] for row in result) == 73
    assert 'hatchWithCandidates' not in (ROOT / UTILITY).read_text(encoding='utf-8')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', default='v1.21.42')
    parser.add_argument('--classpath-file', type=Path,
        default=ROOT / 'build/critical-v2-runtime-v12129/probe/classpath.txt')
    args = parser.parse_args()
    out = ROOT / 'build/nei-hatch-regression'
    out.mkdir(exist_ok=True)
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    renderer = ROOT / 'build/critical-machines-v12119/structure-client/red-v18-01/fixture/mods/blockrenderer6343-1.4.24.jar'
    dependencies = [p for p in args.classpath_file.read_text().split(';') if Path(p).suffix == '.jar']
    dependencies = [p for p in dependencies
        if ('/GT5-Unofficial/' not in p.replace('\\', '/') or '/5.09.54.205/' in p.replace('\\', '/'))
        and ('/StructureLib/' not in p.replace('\\', '/') or '/1.4.45/' in p.replace('\\', '/'))
        and ('/GTNHLib/' not in p.replace('\\', '/') or '/0.11.52/' in p.replace('\\', '/'))]
    gt_jars = [p for p in dependencies if '/GT5-Unofficial/' in p.replace('\\', '/')]
    assert len(gt_jars) == 1 and '/5.09.54.205/' in gt_jars[0].replace('\\', '/'), gt_jars
    structure_jars = [p for p in dependencies if '/StructureLib/' in p.replace('\\', '/')]
    assert len(structure_jars) == 1 and '/1.4.45/' in structure_jars[0].replace('\\', '/'), structure_jars
    expected_jars = {
        'gregtech.api.util.HatchElementBuilder': Path(gt_jars[0]),
        'com.gtnewhorizon.structurelib.structure.IStructureElementChain': Path(structure_jars[0]),
        'blockrenderer6343.integration.nei.StructureHacks': renderer,
    }
    production = [ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc',
        ROOT / 'build/resources/patchedMc', ROOT / 'src/main/resources']
    source = Path(__file__).with_name('HatchPreviewRegression.java')
    sources = [source, *Path(__file__).with_name('gates').glob('*.java')]
    old = out / 'baseline/GTSRStructureUtility.java'
    old.parent.mkdir(exist_ok=True)
    old.write_text(git('show', args.baseline + ':' + UTILITY), encoding='utf-8')

    def run(label, tool, values):
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(java / (tool + '.exe')), '@' + str(argfile)], cwd=out,
            capture_output=True, encoding='utf-8', errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
        (out / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(label + '\n' + result.stdout + result.stderr)
        return result.stdout

    results, runtime_sources = {}, {}
    for phase in ('red', 'green'):
        classes = out / (phase + '-classes')
        classes.mkdir(exist_ok=True)
        cp = ';'.join(map(str, [classes, *production, renderer, *dependencies]))
        run(phase + '-compile', 'javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes,
            *sources, *([old] if phase == 'red' else [])])
        output = run(phase, 'java', ['-Dfile.encoding=UTF-8', '-cp', cp, 'HatchPreviewRegression', phase])
        assert 'RESULT ' + phase + ' PASS cases=12' in output
        assert 'GT5-Unofficial-5.09.54.205-dev.jar' in output
        assert 'StructureLib-1.4.45-dev.jar' in output
        runtime_sources[phase] = [dict(zip(('class', 'location', 'sha256'), match)) for match in
            re.findall(r'^SOURCE (\S+) (\S+) SHA256=([0-9a-f]{64})$', output, flags=re.M)]
        assert len(runtime_sources[phase]) == 3
        for actual in runtime_sources[phase]:
            expected = expected_jars[actual['class']]
            assert expected.name in actual['location']
            assert actual['sha256'] == hashlib.sha256(expected.read_bytes()).hexdigest()
        results[phase] = [line for line in output.splitlines() if line.startswith(('SOURCE ', 'CASE ', 'RESULT '))]
    receipt = {'status': 'PASS', 'baseline': args.baseline, 'productionAudit': audit(args.baseline),
        'results': results, 'runtimeSources': runtime_sources,
        'rendererSHA256': hashlib.sha256(renderer.read_bytes()).hexdigest(),
        'boundary': 'Real hatch classes, GT hatch builder, StructureLib chain and unmodified BlockRenderer '
        'StructureHacks.getStacksForElement; 6 hatch classes x required/optional. All 73 production calls '
        'are mechanically identical to baseline after removing wrappers; builder arguments, chain order, '
        'counts, adders and machine validation remain identical.',
        'limitations': ['No full machine initialization, world construction, particles, rendering or GL UI.',
            'DummyWorld.INSTANCE and BRUtil.AUTO_PLACE_ENVIRONMENT are null bootstrap gates; queried elements '
            'do not use either. Fixture hatches have proxy base tiles and allocate without constructors.',
            'Legacy SteamHatchElementOutputBusMixin blacklist is unchanged; fix does not depend on it.'],
        'repeatCommand': 'python -X utf8 tools/nei/verify_hatch_preview.py'}
    (out / 'checks.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
