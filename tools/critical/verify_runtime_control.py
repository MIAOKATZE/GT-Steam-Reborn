"""Linked native GT switches and production state; sparse World, no client startup."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import verify_v32 as fixture

ROOT = fixture.ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--snapshots-only', action='store_true', help='Candidate-only production capture/wire contract for four missing native gallery states')
    parser.add_argument('--shutdown-only', action='store_true', help='Solar main-power cancellation contract')
    parser.add_argument('--solar-receipt', type=Path)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-runtime-control')
    args = parser.parse_args()
    if args.snapshots_only and not args.jar:
        parser.error('--snapshots-only requires --jar')
    if args.shutdown_only and not args.solar_receipt:
        parser.error('--shutdown-only requires --solar-receipt')
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / 'classes'
    gates = out / 'gates'
    classes.mkdir(parents=True, exist_ok=True)
    gates.mkdir(exist_ok=True)
    log_config = out / 'log4j2.xml'
    log_config.write_text('<Configuration status="OFF"><Appenders/><Loggers><Root level="off"/></Loggers></Configuration>', encoding='utf-8')
    jars = list(fixture.CACHE.glob('*/*/*/*/*.jar'))
    gt = [p for p in jars if 'GT5-Unofficial/5.09.54.205/' in p.as_posix()]
    sources = [ROOT / 'tools/critical' / p for p in (
        'CriticalSolarShutdownContract.java' if args.shutdown_only else 'CriticalRuntimeSnapshotContract.java' if args.snapshots_only else 'CriticalRuntimeControlContract.java',
        'CriticalV32Contract.java', 'CriticalV32Fixture.java')]
    if not args.jar:
        sources += fixture.PRODUCTION + [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalEnergyRuntime.java']
    baseline = max((ROOT / 'build/libs').glob('*-dev.jar'), key=lambda p: p.stat().st_mtime)
    cp = os.pathsep.join(map(str, [classes, *([args.jar.resolve()] if args.jar else []), *gt,
        *([] if args.jar else [ROOT / 'build/classes/java/main', baseline]), ROOT / 'build/classes/java/patchedMc',
        ROOT / 'src/main/resources', *jars]))

    def run(exe, name, arguments):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in arguments), encoding='utf-8')
        result = subprocess.run([str(fixture.JAVA / (exe + '.exe')), '@' + str(argfile)],
            capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, timeout=180)
        log = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(log, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed:\n' + log[-7000:])
        return log

    run('javac', 'compile', ['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes,*sources])
    run('javac', 'gates', ['-encoding','UTF-8','-proc:none','-cp',cp,'-d',gates,*fixture.GATES])
    contract = 'CriticalSolarShutdownContract' if args.shutdown_only else 'CriticalRuntimeSnapshotContract' if args.snapshots_only else 'CriticalRuntimeControlContract'
    output = run('java', 'run', ['-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dlog4j.configurationFile='+str(log_config),'-cp',str(gates)+os.pathsep+cp,
        'com.miaokatze.gtsr.common.critical.'+contract,*([args.solar_receipt.resolve()] if args.shutdown_only else [args.jar.resolve()] if args.snapshots_only else [])])
    if 'PASS '+contract+' checks=' not in output:
        raise RuntimeError('Process exited without explicit contract PASS; inspect run.log')
    print(output.strip())
    (out / 'verification.json').write_text(json.dumps(dict(status='PASS',result=output.strip(),
        sourceSHA256=fixture.binding(sources), jar=str(args.jar) if args.jar else None,
        scope='Real GT205 switches and state with SparseWorld; no FML or full client'),indent=2),encoding='utf-8')


if __name__ == '__main__':
    main()
