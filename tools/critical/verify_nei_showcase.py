"""Narrow GT205 NEI source-overlay compilation and catalogue/render contracts; no full build."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OUT = ROOT / 'build/v208-nei-contract'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Compile fixture/test only; production classes come exclusively from this candidate')
    parser.add_argument('--baseline', type=Path, help='Dependency baseline for a source-overlay run')
    parser.add_argument('--out', type=Path, default=OUT)
    parser.add_argument('--template-only', action='store_true', help='Only validate native zero-input showcase template and indexing')
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=True)
    (out / 'verification.json').unlink(missing_ok=True)
    classes = out / ('fixture-classes' if args.jar else 'classes')
    empty = out / 'empty'
    classes.mkdir(exist_ok=True)
    if args.jar and any((classes / 'com/miaokatze/gtsr').rglob('*.class')):
        raise RuntimeError('Jar-only fixture output contains production overlays; choose a fresh --out')
    empty.mkdir(exist_ok=True)
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    deps = [p for p in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix()]
    baseline = args.jar.resolve() if args.jar else args.baseline.resolve() if args.baseline else ROOT / 'build/libs/gtsr-v2.0.7-dev.jar'
    candidate_hash = hashlib.sha256(baseline.read_bytes()).hexdigest()
    cp = os.pathsep.join(map(str, [classes, baseline,
        ROOT / 'build/classes/java/patchedMc', ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
        ROOT / 'build/rfg/mclauncher-1.7.10.jar', gt, *deps]))
    files = [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in (
        'api/recipe/CriticalRecipeMaps.java', 'api/recipe/CriticalRecipeFrontend.java',
        'client/nei/CriticalNEIShowcase.java', 'loader/recipes/CriticalMachineRecipes.java')]
    production_sources = files
    fixtures = [ROOT / 'tools/tests/Issue37RegistrationRegression.java', ROOT / 'tools/tests/CriticalNEICatalogContract.java']
    files = fixtures if args.jar else production_sources + fixtures

    def run(exe, name, args):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
        result = subprocess.run([str(JAVA / exe), '@' + str(argfile)], cwd=out,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = result.stdout.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(output[-8000:])
        return output

    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp,
        '-d', classes, *files])
    binding = ['-Dcritical.nei.expectedJar=' + str(baseline)] if args.jar else []
    template = ['-Dcritical.nei.templateOnly=true'] if args.template_only else []
    result = run('java.exe', 'catalog', ['-Xmx384m' if args.template_only else '-Xmx1G', *binding, *template, '-cp', cp, 'CriticalNEICatalogContract'])
    assert hashlib.sha256(baseline.read_bytes()).hexdigest() == candidate_hash, 'Candidate changed during verification'
    if args.jar:
        assert not any((classes / 'com/miaokatze/gtsr').rglob('*.class')), 'Production overlay appeared during jar-only run'
    print(result)
    (out / 'verification.json').write_text(json.dumps({'status': 'PASS', 'productOverlay': not bool(args.jar),
        'jar': str(baseline), 'jarSHA256': candidate_hash, 'jarUnchanged': True,
        'gtJar': str(gt), 'sourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in files}, 'productionSourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in production_sources}, 'result': result.splitlines(),
        'scope': ('GT205 jar-only product classes; fixture/test compilation only' if args.jar else 'GT205 source overlay')
        + '; native catalog invariants; no game client'},
        ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
