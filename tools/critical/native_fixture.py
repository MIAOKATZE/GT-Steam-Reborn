"""Private GTNH205 native validation fixture. Never modifies the user's instance."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, socket, subprocess, time, zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-machines-v12115/native'
BASE = ROOT / 'build/coke-rebuild-v12114/native/natural-final/fixture'
LEASE = Path('E:/CodeGT/.gtsr-native-server.lease')

def java_processes():
    cmd = 'Get-CimInstance Win32_Process -Filter "name=\'java.exe\'" | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress'
    r = subprocess.run(['powershell', '-NoProfile', '-Command', cmd], capture_output=True, text=True, check=True)
    data = json.loads(r.stdout) if r.stdout.strip() else []
    return data if isinstance(data, list) else [data]

def prepare(jar, phase):
    target = OUT / phase
    target.mkdir(parents=True, exist_ok=False)
    fixture = target / 'fixture'
    fixture.mkdir()
    for name in ['config', 'scripts', 'libraries']:
        shutil.copytree(BASE / name, fixture / name)
    for source in (BASE / 'mods').rglob('*'):
        if not source.is_file() or any(s in source.name.lower() for s in ['input.jar', 'gtsr', 'probe', 'helper', 'sealed-runtime']):
            continue
        dest = fixture / source.relative_to(BASE)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, dest)
    for name in ['java9args.txt', 'eula.txt', 'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar', 'minecraft_server.1.7.10.jar', 'lwjgl3ify-forgePatches.jar']:
        shutil.copy2(BASE / name, fixture / name)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
    (fixture / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=CriticalAudit\nlevel-seed=1211501\nlevel-type=FLAT\ngenerate-structures=false\nview-distance=2\nmax-tick-time=-1\nspawn-animals=false\nspawn-monsters=false\nsnooper-enabled=false\n', encoding='utf-8')
    shutil.copy2(jar, fixture / 'mods/production-gtsr.jar')
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-21-amd64-windows.2/bin/java.exe'
    command = [str(java), '-Xms512M', '-Xmx3G', '-Djava.awt.headless=true', '-Dcritical.receipt=' + str(target / 'checks.json'), '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
    spec = dict(command=command, cwd=str(fixture), jar=str(jar.resolve()), sha256=hashlib.sha256(jar.read_bytes()).hexdigest(), port=port, seed='1211501', baseline=str(BASE))
    (target / 'launch.json').write_text(json.dumps(spec, indent=2))
    print(target)

def compile_probe(phase):
    target = OUT / phase; fixture = target / 'fixture'; classes = target / 'classes'
    classes.mkdir(exist_ok=True)
    gson = next((Path.home() / '.gradle/caches/modules-2/files-2.1').glob('com.google.code.gson/gson/2.2.4/*/*.jar'))
    javac = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe'
    cp = [ROOT / 'build/classes/java/patchedMc', fixture / 'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar', fixture / 'minecraft_server.1.7.10.jar', gson, fixture / 'mods/*']
    source = target / 'CriticalNativeProbe.java'
    shutil.copy2(Path(__file__).with_name('CriticalNativeProbe.java'), source)
    proc = subprocess.run([str(javac), '-encoding', 'UTF-8', '-proc:none', '-source', '8', '-target', '8', '-cp', ';'.join(map(str, cp)), '-d', str(classes), str(source)], capture_output=True, text=True)
    (target / 'compile.log').write_text(proc.stdout + proc.stderr)
    if proc.returncode: raise RuntimeError(proc.stderr)
    with zipfile.ZipFile(fixture / 'mods/critical-native-helper.jar', 'w') as archive:
        for file in classes.rglob('*.class'): archive.write(file, file.relative_to(classes).as_posix())
        archive.writestr('mcmod.info', json.dumps([dict(modid='criticalnative12115', name='Critical native audit', version='1')]))
    spec = json.loads((target / 'launch.json').read_text(encoding='utf-8'))
    spec['probe_source_sha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
    spec['probe_jar_sha256'] = hashlib.sha256((fixture / 'mods/critical-native-helper.jar').read_bytes()).hexdigest()
    (target / 'launch.json').write_text(json.dumps(spec, indent=2), encoding='utf-8')

def run(phase):
    target = OUT / phase; spec = json.loads((target / 'launch.json').read_text())
    if phase.startswith('final'):
        spec['command'].insert(4, '-Dcritical.final=true')
    processes = java_processes()
    occupied = [p for p in processes if any(s in (p.get('CommandLine') or '').lower() for s in ['lwjgl3ify-forgepatches', 'minecraft_server', 'launchwrapper.launch', 'net.minecraft.server'])]
    (target / 'preflight.json').write_text(json.dumps(dict(time=time.time(), processes=processes, occupied=occupied), indent=2))
    if occupied: raise RuntimeError('Native server already occupied: ' + str(occupied))
    fd = os.open(LEASE, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.write(fd, json.dumps(dict(pid=os.getpid(), fixture=spec['cwd'], created=time.time())).encode()); os.close(fd)
    start = time.time(); proc = None
    try:
        with (target / 'stdout.log').open('wb') as stdout, (target / 'stderr.log').open('wb') as stderr:
            proc = subprocess.Popen(spec['command'], cwd=spec['cwd'], stdin=subprocess.PIPE, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
            (target / 'pid.json').write_text(json.dumps(dict(pid=proc.pid)))
            try: code = proc.wait(timeout=1800)
            except subprocess.TimeoutExpired:
                proc.stdin.write(b'stop\n'); proc.stdin.flush(); code = proc.wait(timeout=120)
        checks = target / 'checks.json'
        status = json.loads(checks.read_text(encoding='utf-8')).get('status') if checks.exists() else 'NO_RECEIPT'
        receipt = dict(spec, pid=proc.pid, exit_code=code, seconds=time.time()-start, status=status)
        (target / 'receipt.json').write_text(json.dumps(receipt, indent=2)); print(json.dumps(receipt))
    finally:
        if proc is None or proc.poll() is not None: LEASE.unlink()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['prepare', 'compile', 'run']); parser.add_argument('phase'); parser.add_argument('--jar', type=Path)
    args = parser.parse_args()
    if args.action == 'prepare':
        if not args.jar: parser.error('--jar is required for prepare')
        prepare(args.jar, args.phase)
    elif args.action == 'compile': compile_probe(args.phase)
    else: run(args.phase)
