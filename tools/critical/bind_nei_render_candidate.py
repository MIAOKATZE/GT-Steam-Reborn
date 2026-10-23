"""Bind existing 14 GL checks to a candidate through instruction/exception-table equivalence; no GL rerun."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javap.exe'
CLASS = 'com/miaokatze/gtsr/client/nei/CriticalNEIShowcase'


def disassemble(path, verbose=False):
    result = subprocess.run([str(JAVA), '-v' if verbose else '-c', '-p', '-constants', str(path)],
        capture_output=True, timeout=20, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', 'replace'))
    return result.stdout.decode('utf-8', 'replace')


def normalize(text, lower_concat=False):
    methods = {}
    name, rows = None, []

    def flush():
        if name is None:
            return
        instructions, exceptions = [], []
        for row in rows:
            match = re.match(r'\s*(\d+):\s+(\w+)(.*)', row)
            if match:
                arg = match[3].split('//', 1)[-1].strip() if '//' in match[3] else match[3].strip()
                instructions.append((int(match[1]), match[2], arg))
            match = re.match(r'\s*(\d+)\s+(\d+)\s+(\d+)\s+(any|Class .*)$', row)
            if match:
                exceptions.append((int(match[1]), int(match[2]), int(match[3]), match[4]))
        kept, concat_dup = [], False
        for off, op, arg in instructions:
            if lower_concat and ' page(' in name:
                if op == 'new' and arg == 'class java/lang/StringBuilder':
                    concat_dup = True
                    continue
                if concat_dup and op == 'dup':
                    concat_dup = False
                    continue
                if 'java/lang/StringBuilder.' in arg:
                    continue
                if op == 'invokedynamic' and 'makeConcatWithConstants' in arg:
                    continue
                if op in ('ldc', 'ldc_w') and arg in (
                    'String showcase.', 'String _phase_', 'String gtsr.critical.kind.'):
                    continue
            kept.append((off, op, arg))

        def label(offset):
            return next((i for i, (off, _, _) in enumerate(kept) if off >= offset), len(kept))

        normalized = []
        for off, op, arg in kept:
            if op.startswith('if') or op in ('goto', 'goto_w', 'jsr'):
                arg = '@' + str(label(int(arg)))
            normalized.append(('ldc' if op == 'ldc_w' else op) + ' ' + re.sub(r'#\d+', '#', arg))
        methods[name] = {'instructions': normalized,
            'exceptions': [(label(start), label(end), label(target), typ) for start, end, target, typ in exceptions]}

    for row in text.splitlines():
        if row.startswith('  ') and not row.startswith('    ') and ('(' in row or row.strip() == 'static {};'):
            flush()
            name, rows = row.strip(), []
        else:
            rows.append(row)
    flush()
    return methods


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    jar, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=True)
    original = ROOT / 'build/v208-nei-contract/classes' / (CLASS + '.class')
    receipt = json.loads((ROOT / 'build/v208-nei-render/verification.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(original.read_bytes()).hexdigest() == receipt['compiledClassSHA256'], 'GL-tested bytecode changed'
    candidate_hash = hashlib.sha256(jar.read_bytes()).hexdigest()
    with zipfile.ZipFile(jar) as archive:
        candidate = out / 'CriticalNEIShowcase.class'
        candidate.write_bytes(archive.read(CLASS + '.class'))
        page = out / 'CriticalNEIShowcase$Page.class'
        page.write_bytes(archive.read(CLASS + '$Page.class'))
    old, new = disassemble(original), disassemble(candidate)
    (out / 'gl-tested.javap').write_text(old, encoding='utf-8')
    (out / 'candidate.javap').write_text(new, encoding='utf-8')
    a, b = normalize(old), normalize(new)
    differing = [key for key in a if a[key] != b.get(key)]
    assert set(a) == set(b)
    assert len(differing) == 1 and ' page(' in differing[0], differing
    verbose = disassemble(original, True)
    # These four recipes are the only Java17 concat operations removed below. Java8 emits their StringBuilder form.
    for recipe in (r'showcase.\u0001', r'\u0001_phase_', r'showcase.\u0001\u0001', r'gtsr.critical.kind.\u0001'):
        assert recipe in verbose, recipe
    assert normalize(old, True) == normalize(new, True), 'Difference beyond Java8 string concatenation lowering'
    old_page = ROOT / 'build/v208-nei-contract/classes' / (CLASS + '$Page.class')
    assert normalize(disassemble(old_page)) == normalize(disassemble(page)), 'Page container behavior changed'
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == candidate_hash
    result = {'status': 'PASS', 'jar': str(jar), 'jarSHA256': candidate_hash,
        'glEvidence': str(ROOT / 'build/v208-nei-render/verification.json'),
        'glTestedClassSHA256': receipt['compiledClassSHA256'],
        'candidateClassSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(),
        'methodCount': len(a), 'instructionAndExceptionTableExactMethods': len(a) - 1,
        'onlyCompilerLoweringDifference': differing[0],
        'lowering': 'Four verified Java17 StringConcatFactory recipes vs Java8 StringBuilder; all remaining instructions/branches/exception tables equal',
        'pageContainerInstructionEquivalent': True, 'inheritedGLChecks': 14, 'additionalGLFrames': 0,
        'scope': 'Bytecode semantic equivalence of display class; inherits fixture GL/cache checks, not real NEI screenshot coverage'}
    (out / 'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
