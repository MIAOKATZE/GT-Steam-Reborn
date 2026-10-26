"""Focused production ammonia / GT205 output transaction probe; no game or Gradle launch."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True)
    parser.add_argument('--baseline', action='store_true', help='Compile the HEAD machine instead of the working copy')
    args = parser.parse_args()
    assert args.label.replace('-', '').replace('_', '').isalnum()
    out = ROOT / 'build/ammonia-v2010' / args.label
    out.mkdir(parents=True, exist_ok=False)
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    gt = next(cache.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    dependencies = [p for p in (ROOT / 'build/critical-v2-runtime-v12129/probe/classpath.txt').read_text().split(';')
                    if Path(p).suffix == '.jar' and '/GT5-Unofficial/' not in p.replace('\\', '/')]
    dependencies = [p for p in dependencies if
                    ('/StructureLib/' not in p.replace('\\', '/') or '/1.4.45/' in p.replace('\\', '/')) and
                    ('/GTNHLib/' not in p.replace('\\', '/') or '/0.11.52/' in p.replace('\\', '/'))]
    sources = [Path(__file__).with_name('Issue37RegistrationRegression.java'),
               Path(__file__).with_name('AmmoniaNativeRegression.java'),
               ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/MTEAmmoniaPlant.java',
               ROOT / 'src/main/java/com/miaokatze/gtsr/api/compat/GTSRHatchFluidAccess.java',
               ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/base/MTESteamOutputHatch.java',
               ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/base/MTEPressureSteamOutputHatch.java']
    if args.baseline:
        original = subprocess.check_output(['git', 'show', 'HEAD:src/main/java/com/miaokatze/gtsr/common/machine/MTEAmmoniaPlant.java'], cwd=ROOT)
        baseline = out / 'MTEAmmoniaPlant.java'
        baseline.write_bytes(original)
        sources[2] = baseline
    classes = out / 'classes'
    classes.mkdir()
    production = [ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc',
                  ROOT / 'build/resources/patchedMc', ROOT / 'src/main/resources']

    def run(name, tool, values):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(JAVA / (tool + '.exe')), '@' + str(argfile)], cwd=out,
                                capture_output=True, encoding='utf-8', errors='replace',
                                creationflags=subprocess.CREATE_NO_WINDOW)
        (out / (name + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
        return result

    cp = ';'.join(map(str, [classes, gt, *production, *dependencies]))
    compiled = run('compile', 'javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])
    if compiled.returncode:
        print(compiled.stdout + compiled.stderr)
        raise SystemExit(compiled.returncode)
    result = run('runtime', 'java', ['-Xmx1G', '-Dfile.encoding=UTF-8', '-cp', cp,
                                   'AmmoniaNativeRegression', '5.09.54.205'])
    receipt = {'exitCode': result.returncode, 'gtVersion': '5.09.54.205', 'gtJar': str(gt),
               'sourceSha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
               'result': [s for s in result.stdout.splitlines() if s.startswith(('RESULT', 'SOURCE'))]}
    (out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print((result.stdout + result.stderr)[-8000:])
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
