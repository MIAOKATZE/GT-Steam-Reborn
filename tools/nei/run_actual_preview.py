"""Replay real BlockRenderer GUI survival construction in an owned <=5 GiB client."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/nei-preview-v12145'
SOURCE = ROOT / 'build/critical-machines-v12119/structure-client/red-v18-01'
PACK = Path('E:/Prism Launcher/instances/GTNH-2.9.0-RC-2-SH/.minecraft')
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def prepare():
    fixture = BASE / 'client-fixture'
    fixture.mkdir(parents=True, exist_ok=False)
    old = SOURCE / 'fixture'
    for name in ('libraries', 'assets', 'natives'):
        shutil.copytree(old / name, fixture / name, copy_function=os.link)
    for name in ('config', 'scripts', 'resourcepacks', 'fonts'):
        if (PACK / name).exists():
            shutil.copytree(PACK / name, fixture / name)
    (fixture / 'mods').mkdir()
    for source in (PACK / 'mods').rglob('*'):
        if not source.is_file() or source.name.lower().startswith(('gtsr-', 'production-gtsr', 'critical-gui', 'actual-nei')):
            continue
        dest = fixture / 'mods' / source.relative_to(PACK / 'mods')
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
    (fixture / 'options.txt').write_text('fullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\nrenderDistance:2\nguiScale:2\n', encoding='utf-8')
    spec = json.loads((SOURCE / 'launch.json').read_text())
    spec['command'] = [s.replace(str(SOURCE), str(BASE)).replace(str(BASE / 'fixture'), str(fixture)) for s in spec['command']]
    spec['command'] = [s for s in spec['command'] if not s.startswith('-Dcritical.')]
    spec['command'] = [s.replace('-Xmx6G', '-Xmx4G') for s in spec['command']]
    spec['cwd'] = str(fixture)
    spec['scope'] = 'Owned client. No source saves/accounts. 4 GiB heap. Original launch/runtime artifacts retained.'
    (BASE / 'client-launch.json').write_text(json.dumps(spec, indent=2))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--expect-red', action='store_true')
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--matrix', choices=('core','all'), default='core')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.phase): raise ValueError('Unsafe phase name')
    lease = ROOT.parent / '.gtsr-native-server.lease'
    if lease.exists(): raise RuntimeError('A native test lease is active; fixture and helper must remain unchanged')
    BASE.mkdir(exist_ok=True)
    fixture = BASE / 'client-fixture'
    if not fixture.exists(): prepare()
    disabled = fixture / 'disabled-project-mods'
    disabled.mkdir(exist_ok=True)
    for filename in ('gtfi-v0.1.1.jar', 'gtit-v1.8.95.jar', 'gtmb-v0.1.16.jar', 'gtswn-v1.8.82.jar', 'gtudk-v0.5.14.jar'):
        path = fixture / 'mods' / filename
        if path.exists():
            if not path.resolve().is_relative_to(fixture.resolve()): raise RuntimeError('Fixture path escaped')
            shutil.move(str(path), str(disabled / filename))
    (fixture / 'saves/ActualNeiPreviewAuditBaseline/betterquesting').mkdir(parents=True, exist_ok=True)
    out = BASE / args.phase
    out.mkdir(exist_ok=False)
    shutil.copy2(args.jar, out / 'production.jar')
    production = fixture / 'mods/production-gtsr.jar'
    shutil.copy2(args.jar, production)
    classes = out / 'classes'
    classes.mkdir()
    cp = ';'.join(str(p) for p in [ROOT / 'build/classes/java/patchedMc', *sorted((fixture / 'libraries').rglob('*.jar')), *sorted((fixture / 'mods').rglob('*.jar'))])
    source = Path(__file__).with_name('ActualNeiPreviewProbe.java')
    options = ['-encoding', 'UTF-8', '-proc:none', '--release', '8', '-cp', cp, '-d', str(classes), str(source)]
    argfile = out / 'compile.args'
    argfile.write_text('\n'.join('"' + s.replace('\\', '/') + '"' for s in options))
    compiled = subprocess.run([str(JAVA), '@' + str(argfile)], capture_output=True)
    (out / 'compile.log').write_bytes(compiled.stdout + compiled.stderr)
    if compiled.returncode: raise RuntimeError(compiled.stderr.decode('utf-8', 'replace'))
    helper = fixture / 'mods/actual-nei-preview-audit.jar'
    with zipfile.ZipFile(helper, 'w') as archive:
        for path in classes.rglob('*.class'): archive.write(path, path.relative_to(classes).as_posix())
    spec = json.loads((BASE / 'client-launch.json').read_text())
    cmd = spec['command']
    cmd[1:1] = ['-Dnei.audit.receipt=' + str(out / 'checks.json'), '-Dnei.audit.expectRed=' + str(args.expect_red).lower(), '-Dnei.audit.matrix=' + args.matrix]
    dependencies = {}
    for filename in ('gregtech-5.09.54.205.jar', 'structurelib-1.4.45.jar', 'blockrenderer6343-1.4.24.jar'):
        path = fixture / 'mods' / filename
        expected = Path('E:/CodeGT/测试实例-GTSR/mods') / filename
        if sha(path) != sha(expected): raise RuntimeError('Dependency differs from test instance: ' + filename)
        dependencies[filename] = dict(path=str(path), sha256=sha(path), testInstanceSHA256=sha(expected))
    receipt = dict(productionJar=str(args.jar.resolve()), productionSHA256=sha(args.jar), helperSHA256=sha(helper), sourceSHA256=sha(source), dependencies=dependencies, heapGiB=4, command=cmd, cwd=str(fixture))
    receipt['immutableProductionCopy'] = str(out / 'production.jar')
    receipt['disabledUnrelatedProjectMods'] = {p.name: sha(p) for p in disabled.glob('*.jar')}
    shutil.copy2(source, out / source.name)
    shutil.copy2(helper, out / 'helper.jar')
    (out / 'launch.json').write_text(json.dumps(receipt, indent=2))
    if args.prepare_only: print('PREPARED', out); return
    fd = os.open(lease, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.write(fd, json.dumps(dict(pid=os.getpid(), fixture=str(fixture), phase=args.phase)).encode()); os.close(fd)
    proc = None
    try:
        start = time.monotonic()
        with (out / 'stdout.log').open('wb') as log:
            proc = subprocess.Popen(cmd, cwd=fixture, stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            (out / 'pid.json').write_text(json.dumps(dict(pid=proc.pid, runner=os.getpid())))
            print('OWNED CLIENT', proc.pid, 'HEAP 4 GiB', flush=True)
            try: code = proc.wait(timeout=900)
            except subprocess.TimeoutExpired:
                proc.terminate(); code = proc.wait(timeout=30); receipt['forcedStop'] = True
        receipt.update(exitCode=code, seconds=time.monotonic()-start, productionUnchanged=sha(production)==receipt['productionSHA256'])
        payload = json.loads((out / 'checks.json').read_text()) if (out / 'checks.json').exists() else {}
        receipt['status'] = payload.get('status', 'NO_RECEIPT')
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
        print(json.dumps({k: receipt[k] for k in ('status','exitCode','seconds','productionUnchanged')}), flush=True)
        expected_status = 'EXPECTED_RED' if args.expect_red else 'PASS'
        if code != 0 or receipt.get('forcedStop') or not receipt['productionUnchanged'] or receipt['status'] != expected_status:
            raise RuntimeError('Actual NEI preview audit failed: ' + receipt['status'])
    finally:
        if proc is None or proc.poll() is not None: lease.unlink()

if __name__ == '__main__': main()
