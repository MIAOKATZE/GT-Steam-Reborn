"""Bounded foundation contracts against one built dev jar; never overlays product classes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
CONTRACTS = ('CriticalFoundationContract', 'CriticalFoundationRuntimeContract', 'CriticalFoundationPortsContract',
             'CriticalConstructionOwnershipContract', 'CriticalConstructionLedgerContract', 'CriticalConstructionRecoveryContract',
             'CriticalConstructionViewContract')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--contract', action='append', choices=CONTRACTS,
                        help='Run only the named contracts for a focused diagnosis loop')
    parser.add_argument('--java-home', type=Path,
                        default=Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2')
    args = parser.parse_args()
    candidate, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(ROOT / 'build')
    if not candidate.is_file():
        parser.error('Built dev jar is required')
    classes, runtime = out / 'classes', out / 'runtime'
    classes.mkdir(parents=True, exist_ok=True)
    runtime.mkdir(exist_ok=True)
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
                          if '/GT5-Unofficial/' not in p.as_posix()
                          and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
                          and 'gtsr' not in p.name.lower())
    cp = os.pathsep.join(map(str, [classes, candidate, gt, ae, ROOT / 'build/classes/java/patchedMc',
                                   ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
                                   ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/tests' / (name + '.java') for name in CONTRACTS]
    sources.append(ROOT / 'tools/critical/CriticalV32Fixture.java')
    before = {str(path.relative_to(ROOT)): sha(path) for path in sources}
    candidate_hash = sha(candidate)

    def execute(executable, label, arguments):
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in arguments), encoding='utf-8')
        result = subprocess.run([str(args.java_home / 'bin' / executable), '@' + str(argfile)],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=runtime, timeout=60,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        text = result.stdout.decode('utf-8', errors='replace')
        (out / (label + '.log')).write_text(text, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(f'{label} failed ({result.returncode}); see {out / (label + ".log")}')
        return text

    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    execute('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                     '-cp', cp, '-d', classes, *sources])
    results, origins = {}, {}
    for name in args.contract or CONTRACTS:
        text = execute('java.exe', name, ['-cp', cp, 'com.miaokatze.gtsr.common.critical.' + name, candidate])
        match = re.search(r'PASS ' + name + r' checks=(\d+)', text)
        if not match:
            raise RuntimeError(f'{name} returned no receipt')
        results[name] = int(match.group(1))
        origins.update(dict(re.findall(r'^SOURCE ([^=]+)=(.+)$', text, re.MULTILINE)))
    if not args.contract and len(origins) != 11:
        raise RuntimeError(f'Expected eleven bound production classes, found {origins}')
    if sha(candidate) != candidate_hash or before != {str(path.relative_to(ROOT)): sha(path) for path in sources}:
        raise RuntimeError('Candidate or fixture changed during verification')
    receipt = {'status': 'PASS', 'jar': str(candidate), 'jarSHA256': candidate_hash,
               'productOverlay': False, 'gtJar': str(gt), 'aeJar': str(ae),
               'sourceOrigins': origins, 'fixtureSHA256': before, 'checks': results,
               'totalChecks': sum(results.values()),
               'scope': 'Detached real GT205/AE1080 classes; real NBT and State tick; sparse clocks; no FML/server/GL or live ME network'}
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': receipt['status'], 'totalChecks': receipt['totalChecks'], 'jarSHA256': candidate_hash}))


if __name__ == '__main__':
    main()
