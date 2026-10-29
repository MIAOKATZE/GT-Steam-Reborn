"""Compile and exercise the actual critical ME outputs and their narrow provider accessor mixin."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/gtsr-v2.0.42-dev.jar')
parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-me-outputs43')
parser.add_argument('--source-overlay', action='store_true', help='Compile production sources over the specified jar')
args = parser.parse_args()
BASELINE = args.jar.resolve()
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OUT = args.out.resolve()
OUT.relative_to(ROOT / 'build')
CLASSES = OUT / 'classes'
CLASSES.mkdir(parents=True, exist_ok=True)
if not args.source_overlay and (CLASSES / 'com/miaokatze/gtsr').exists():
    raise RuntimeError('Production overlay in fixture directory; select a fresh --out')
EMPTY = OUT / 'empty'
EMPTY.mkdir(exist_ok=True)
GT = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
AE = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
DEPS = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
              if '/GT5-Unofficial/' not in p.as_posix()
              and '/Applied-Energistics-2-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower())
CP = os.pathsep.join(map(str, [CLASSES, BASELINE, GT, AE,
    ROOT / 'build/classes/java/patchedMc', ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
    ROOT / 'build/rfg/mclauncher-1.7.10.jar', *DEPS]))
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
SOURCES = [ROOT / 'tools/tests/Issue37RegistrationRegression.java',
           ROOT / 'tools/tests/CriticalMEOutputsContract.java']
if args.source_overlay:
    SOURCES += [ROOT / 'src/main/java/com/miaokatze/gtsr' / p for p in (
    'mixin/CriticalMEOutputProviderAccess.java',
    'common/machine/hatch/critical/CriticalMEOutputProvider.java',
    'common/machine/hatch/critical/MTEHatchCriticalOutputME.java',
        'common/machine/hatch/critical/MTEHatchCriticalOutputBusME.java')]
(CLASSES / 'mixins.gtsr.critical-output-probe.json').write_text(json.dumps({
    'required': True, 'minVersion': '0.8.5-GTNH', 'package': 'com.miaokatze.gtsr.mixin',
    'compatibilityLevel': 'JAVA_8', 'mixins': ['CriticalMEOutputProviderAccess']
}), encoding='utf-8')


def run(exe, name, args):
    argfile = OUT / (name + '.args')
    argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    result = subprocess.run([str(JAVA / exe), '@' + str(argfile)], cwd=OUT,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180,
        creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    output = result.stdout.decode('utf-8', errors='replace')
    (OUT / (name + '.log')).write_text(output, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(name + ': ' + output[-16000:])
    return output


run('javac.exe', 'compile', ['-J-Xmx1G', '-encoding', 'UTF-8', '-proc:none', '-sourcepath', EMPTY,
    '-cp', CP, '-d', CLASSES, *SOURCES])
output = run('java.exe', 'contract', ['-Xmx1G', '-Dfile.encoding=UTF-8', '-cp', CP, 'CriticalMEOutputsContract'])
lines = [line for line in output.splitlines() if line.startswith(('PASS ', 'VALUE '))]
lines += [line[line.index('PASS CriticalMEOutputsContract'):]
          for line in output.splitlines() if 'PASS CriticalMEOutputsContract' in line and not line.startswith('PASS ')]
if not any(line.startswith('PASS CriticalMEOutputsContract') for line in lines):
    raise RuntimeError('Missing explicit pass receipt')
(OUT / 'verification.json').write_text(json.dumps({'status': 'PASS', 'result': lines,
    'sourceOverlay': args.source_overlay,
    'sourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES},
    'baselineJar': str(BASELINE), 'baselineSHA256': hashlib.sha256(BASELINE.read_bytes()).hexdigest(),
    'gtJar': str(GT), 'aeJar': str(AE),
    'scope': 'Real narrow accessor Mixin, GT205 native provider/cache/NBT and AE1080 powered insertion; '
             'finite network/energy seams, no full client or live AE grid.'}, indent=2), encoding='utf-8')
print('\n'.join(lines))
