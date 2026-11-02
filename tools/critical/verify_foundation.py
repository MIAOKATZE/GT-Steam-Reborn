"""Bounded foundation contracts against one built dev jar; never overlays product classes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
CONTRACTS = ('CriticalFoundationContract', 'CriticalFoundationRuntimeContract', 'CriticalFoundationPortsContract',
             'CriticalConstructionOwnershipContract', 'CriticalConstructionLedgerContract', 'CriticalConstructionRecoveryContract',
             'CriticalConstructionViewContract', 'CriticalConstructionDebugContract')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--contract', action='append', choices=CONTRACTS,
                        help='Run only the named contracts for a focused diagnosis loop')
    parser.add_argument('--recovery-scenario', action='append',
                        help='Run only named recovery scenarios; does not claim the entire recovery suite passed')
    parser.add_argument('--classpath', type=Path,
                        help='Explicit resolved dependency classpath; replaces historical GT205/AE1080 cache discovery')
    parser.add_argument('--overlay-state', action='store_true',
                        help='Compile current State/monitor/ownership sources for recovery diagnosis; not candidate-only acceptance')
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
    state_class = classes / 'com/miaokatze/gtsr/common/critical/CriticalMachineState.class'
    if not args.overlay_state and state_class.exists():
        parser.error('State source overlay remains in output; choose a fresh --out for candidate-only acceptance')
    if args.overlay_state and args.contract != ['CriticalConstructionRecoveryContract']:
        parser.error('--overlay-state requires only --contract CriticalConstructionRecoveryContract')
    if args.classpath:
        dependencies = []
        for value in args.classpath.read_text(encoding='utf-8-sig').strip().split(os.pathsep):
            path = Path(value)
            if not path.exists():
                parser.error(f'Classpath entry is missing: {path}')
            if path.is_dir() and (path / 'com/miaokatze/gtsr/common/critical/CriticalMachineState.class').exists():
                continue
            if path.is_file() and path.suffix == '.jar':
                with zipfile.ZipFile(path) as archive:
                    if ('META-INF/services/com.sun.source.util.Plugin' in archive.namelist()
                            or 'com/miaokatze/gtsr/common/critical/CriticalMachineState.class' in archive.namelist()):
                        continue
            dependencies.append(path)
        gt_candidates = [p for p in dependencies if p.name.startswith('GT5-Unofficial-')]
        ae_candidates = [p for p in dependencies if p.name.startswith('Applied-Energistics-2-Unofficial-')]
        if len(gt_candidates) != 1 or len(ae_candidates) != 1:
            parser.error('Explicit classpath must contain exactly one GT and one AE artifact')
        gt, ae = gt_candidates[0], ae_candidates[0]
    else:
        gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
        ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
        dependencies = [gt, ae, *sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
                              if '/GT5-Unofficial/' not in p.as_posix()
                              and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
                              and 'gtsr' not in p.name.lower())]
    cp = os.pathsep.join(map(str, [classes, candidate, ROOT / 'build/classes/java/patchedMc',
                                   ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
                                   ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/tests' / (name + '.java') for name in CONTRACTS]
    sources.append(ROOT / 'tools/tests/CriticalRC2StateContract.java')
    sources.append(ROOT / 'tools/tests/CriticalConstructionEconomyContract.java')
    sources.append(ROOT / 'tools/critical/CriticalV32Fixture.java')
    if args.overlay_state:
        sources.extend(ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java') for name in
                       ('CriticalMachineState', 'CriticalStructureMonitor', 'CriticalWorldData', 'CriticalBlockProtection'))
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
        selected = [','.join(args.recovery_scenario)] if name == 'CriticalConstructionRecoveryContract' and args.recovery_scenario else []
        expected_origin = classes if args.overlay_state else candidate
        text = execute('java.exe', name, ['-cp', cp, 'com.miaokatze.gtsr.common.critical.' + name, expected_origin, *selected])
        match = re.search(r'PASS ' + name + r' checks=(\d+)', text)
        if not match:
            raise RuntimeError(f'{name} returned no receipt')
        results[name] = int(match.group(1))
        origins.update(dict(re.findall(r'^SOURCE ([^=]+)=(.+)$', text, re.MULTILINE)))
    if not args.contract and len(origins) != 13:
        raise RuntimeError(f'Expected thirteen bound production classes, found {origins}')
    if sha(candidate) != candidate_hash or before != {str(path.relative_to(ROOT)): sha(path) for path in sources}:
        raise RuntimeError('Candidate or fixture changed during verification')
    receipt = {'status': 'PASS', 'jar': str(candidate), 'jarSHA256': candidate_hash,
               'productOverlay': args.overlay_state, 'gtJar': str(gt), 'aeJar': str(ae),
               'sourceOrigins': origins, 'fixtureSHA256': before, 'checks': results,
               'totalChecks': sum(results.values()),
               'scope': f'Detached real {gt.name}/{ae.name} classes; real NBT and State tick; sparse clocks; no FML/server/GL or live ME network'}
    if args.recovery_scenario:
        receipt['recoveryScenarios'] = args.recovery_scenario
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': receipt['status'], 'totalChecks': receipt['totalChecks'], 'jarSHA256': candidate_hash}))


if __name__ == '__main__':
    main()
