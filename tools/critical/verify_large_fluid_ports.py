"""Frozen-jar real Ports / AEFluidStack large-fluid contract with finite detached network seams."""
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
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / 'fixture-classes'
    classes.mkdir(parents=True, exist_ok=True)
    if any((classes / 'com/miaokatze/gtsr').rglob('*.class')):
        raise RuntimeError('Production overlay in fixture directory; use a fresh --out')
    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    (out / 'verification.json').write_text('{"status":"RUNNING"}', encoding='utf-8')
    baseline = args.jar.resolve()
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
        if '/GT5-Unofficial/' not in p.as_posix() and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
        and 'gtsr' not in p.name.lower())
    cp = os.pathsep.join(map(str, [classes, baseline, gt, ae,
        ROOT / 'build/classes/java/patchedMc', ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
        ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/tests' / name for name in
        ('Issue37RegistrationRegression.java', 'CriticalLargeFluidPortsContract.java')]
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

    def run(exe, name, arguments):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in arguments), encoding='utf-8')
        result = subprocess.run([str(java / exe), '@' + str(argfile)], cwd=out,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = result.stdout.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed: ' + output[-12000:])
        return output

    run('javac.exe', 'compile', ['-J-Xmx1G', '-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
        '-cp', cp, '-d', classes, *sources])
    output = run('java.exe', 'contract', ['-Xmx1G', '-Dfile.encoding=UTF-8', '-cp', cp, 'CriticalLargeFluidPortsContract'])
    lines = [line for line in output.splitlines() if line.startswith(('PASS ', 'SOURCE ', 'VALUE '))]
    if not any(line.startswith('PASS CriticalLargeFluidPortsContract checks=') for line in lines):
        raise RuntimeError('No explicit PASS receipt; inspect contract.log')
    receipt = dict(status='PASS', sourceOverlay=False,
        sourceSHA256={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        baselineJar=str(baseline), baselineSHA256=hashlib.sha256(baseline.read_bytes()).hexdigest(),
        gtJar=str(gt), aeJar=str(ae), result=lines,
        detachedBootstrapWarnings=[line.strip() for line in output.splitlines()
            if 'NoSuchMethodException:' in line],
        scope='Real jar CriticalEnergyPorts, AEFluidStack, native GT205 hatch subclasses and standard hatch transactions; '
              'finite monitor/energy/storage seams. No live AE grid, cell driver, client, server, or crash atomicity claim. '
              'AE1080 powered extraction near Long.MAX_VALUE is explicitly expected to lock uncertain before debit. '
              'AE API definition bootstrap reports missing no-arg constructors for two unrelated event subscriptions '
              'because the detached fixture has no full FML event transformer lifecycle; these are recorded separately.')
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
