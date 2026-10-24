"""Run the bounded material descriptor contract on actual MC/Forge/Netty APIs.

Requires the candidate's generated build/critical-v209-preview/compile.args.
Uses the repository launcher fixture; never opens a game, world or GUI.
"""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v209-material'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/common/gui/CriticalMaterialSnapshot.java'
PROBE = ROOT / 'tools/critical/CriticalMaterialSnapshotProbe.java'
LAUNCHER = ROOT / 'tools/critical/v33/ProbeLauncher.java'
CLASSPATH_ARGS = ROOT / 'build/critical-v209-preview/compile.args'
args = [json.loads(line) for line in CLASSPATH_ARGS.read_text(encoding='utf-8').splitlines() if line.strip()]
cp_option = '-classpath' if '-classpath' in args else '-cp'
classpath = args[args.index(cp_option) + 1]


def argfile(name, values):
    path = OUT / name
    path.write_text('\n'.join(json.dumps(str(value).replace('\\', '/')) for value in values), encoding='utf-8')
    return '@' + str(path)


compile_result = subprocess.run([
    'javac', argfile('compile.args', [
        '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-processorpath', str(OUT),
        '-sourcepath', str(OUT), '-cp', classpath, '-d', str(OUT), str(SOURCE), str(PROBE), str(LAUNCHER)
    ])
], cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
(OUT / 'compile.log').write_text(compile_result.stdout + compile_result.stderr, encoding='utf-8')
if compile_result.returncode:
    print((compile_result.stdout + compile_result.stderr).encode('ascii', 'backslashreplace').decode('ascii'))
    raise SystemExit(compile_result.returncode)

run_result = subprocess.run([
    'java', argfile('run.args', [
        '-Dfile.encoding=UTF-8', '-Dprobe.main=com.miaokatze.gtsr.common.gui.CriticalMaterialSnapshotProbe',
        '-cp', str(OUT) + ';' + classpath, 'ProbeLauncher', str(OUT / 'probe.json')
    ])
], cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
(OUT / 'run.log').write_text(run_result.stdout + run_result.stderr, encoding='utf-8')
print((run_result.stdout[-2500:] + run_result.stderr[-1500:]).encode('ascii', 'backslashreplace').decode('ascii'))
match = re.search(r'MATERIAL_SNAPSHOT_PASS checks=(\d+)', run_result.stdout)
checks = int(match.group(1)) if match else 0
receipt = {
    'command': 'python tools/critical/verify_material_snapshot.py',
    'javacExit': compile_result.returncode,
    'javaExit': run_result.returncode,
    'checks': checks,
    'status': 'PASS' if run_result.returncode == 0 and checks == 11 else 'FAIL',
    'scope': 'Actual Item/Fluid registries, bounded SNBT and native PacketBuffer; no game/world/client launch',
    'sources': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                for path in (SOURCE, PROBE, LAUNCHER, Path(__file__).resolve())},
}
(OUT / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
raise SystemExit(0 if receipt['status'] == 'PASS' else run_result.returncode or 1)
