"""Run four real GT205 space-assembly registrations with isolated ingredient identities; no game launch."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Use only this built dev jar for production classes')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-me-recipes-contract')
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes, empty = out / 'fixture-classes', out / 'empty'
    classes.mkdir(parents=True, exist_ok=True)
    empty.mkdir(exist_ok=True)
    if args.jar and any((classes / 'com/miaokatze/gtsr').rglob('*.class')):
        raise RuntimeError('Jar-only fixture contains production overlays; use a fresh --out directory')
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    deps = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
                  if '/GT5-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower())
    candidate = args.jar.resolve() if args.jar else max((ROOT / 'build/libs').glob('*-dev.jar'),
                                                       key=lambda p: p.stat().st_mtime)
    cp = os.pathsep.join(map(str, [classes, candidate, gt, ROOT / 'build/classes/java/patchedMc',
        ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar', ROOT / 'build/rfg/mclauncher-1.7.10.jar', *deps]))
    sources = [ROOT / 'tools/tests' / name for name in
               ('Issue37RegistrationRegression.java', 'CriticalMERecipesContract.java')]
    if not args.jar:
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in
                    ('common/api/enums/GTSRItemList.java', 'loader/recipes/CriticalMEHatchRecipes.java')]
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

    def run(exe, name, arguments):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in arguments), encoding='utf-8')
        result = subprocess.run([str(java / exe), '@' + str(argfile)], cwd=out,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = result.stdout.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(output[-6500:])
        return output

    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                '-cp', cp, '-d', classes, *sources])
    result = run('java.exe', 'contract', ['-Xmx1G', '-Dfile.encoding=UTF-8', '-cp', cp,
                                        'CriticalMERecipesContract'])
    lines = [line for line in result.splitlines() if line.startswith(('PASS ', 'RECIPE ', 'SOURCE '))]
    if not any(line.startswith('PASS CriticalMERecipesContract checks=') for line in lines):
        raise RuntimeError('Missing explicit PASS receipt')
    receipt = {'status': 'PASS', 'productOverlay': not bool(args.jar), 'jar': str(candidate),
               'jarSHA256': hashlib.sha256(candidate.read_bytes()).hexdigest(), 'result': lines,
               'sourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
               'scope': 'Real GT205 builder and recipe maps, synthetic ingredient identities; no game, full native registry or live ME network'}
    (out / 'verification.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
