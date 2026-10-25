"""Minimal restart/loading regression: source overlay or candidate-only, without a game process."""
import argparse
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-structure-loading-contract')
    options = parser.parse_args()
    out = options.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    candidate = options.jar.resolve() if options.jar else None
    candidate_hash = hashlib.sha256(candidate.read_bytes()).hexdigest() if candidate else None
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    jars = [path for path in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in path.as_posix()]
    cp = os.pathsep.join(map(str, [classes, *([candidate] if candidate else []), gt,
        ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))
    product = [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in (
        'common/critical/CriticalStructureMonitor.java', 'common/critical/CriticalMachineState.java',
        'common/machine/MTECriticalMachine.java')]
    fixtures = [ROOT / 'tools/critical' / name for name in ('CriticalV32Fixture.java', 'CriticalV32Contract.java')]
    fixtures += [ROOT / 'tools/tests' / (name + '.java') for name in (
        'CriticalFoundationContract', 'CriticalConstructionOwnershipContract', 'CriticalStructureLoadingContract')]
    sources = fixtures if candidate else product + fixtures
    bindings = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}

    def run(exe, name, arguments):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(arg).replace('\\', '/') + '"' for arg in arguments), encoding='utf-8')
        result = subprocess.run([str(java / exe), '@' + str(argfile)], cwd=ROOT, capture_output=True,
            timeout=60, creationflags=subprocess.CREATE_NO_WINDOW)
        text = result.stdout.decode('utf-8', errors='replace') + result.stderr.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(text, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(text[-10000:])
        return text

    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])
    gates = out / 'gates'
    gates.mkdir(exist_ok=True)
    run('javac.exe', 'gates', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', gates,
        *[ROOT / 'tools/tests/structurelib-gates' / name for name in ('StructureLibAPI.java', 'FMLCommonHandler.java')]])
    result = run('java.exe', 'run', ['-Xmx1536m', '-Djava.awt.headless=true', '-cp', str(gates) + os.pathsep + cp,
        'com.miaokatze.gtsr.common.critical.CriticalStructureLoadingContract', *([candidate] if candidate else [])])
    assert bindings == {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    assert not candidate or hashlib.sha256(candidate.read_bytes()).hexdigest() == candidate_hash
    match = re.search(r'PASS CriticalStructureLoadingContract checks=(\d+)', result)
    assert match, result
    receipt = {'status': 'PASS', 'checks': int(match.group(1)), 'sourceSHA256': bindings,
        'productOverlay': not bool(candidate), 'candidate': str(candidate) if candidate else None,
        'candidateSHA256': candidate_hash,
        'sourceOrigins': {key: value.strip() for key, value in re.findall(r'^SOURCE ([^=]+)=(.+)$', result, re.M)},
        'authorityGT205': str(gt), 'authoritySHA256': hashlib.sha256(gt.read_bytes()).hexdigest(),
        'scope': 'Production State NBT/update/star boundary plus real GT/StructureLib loom validation in SparseWorld; no game process or disk chunk IO'}
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': receipt['status'], 'checks': receipt['checks'], 'report': str(out / 'verification.json')}))


if __name__ == '__main__':
    main()
