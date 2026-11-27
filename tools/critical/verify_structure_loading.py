"""Run the current CriticalRecoveryContracts against a fresh exported GT277 classpath.

This replaces the retired loading contract entry, not the complete historical construction suite.
No Gradle, Minecraft process, host lease or chunk disk IO is started here.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / 'tools/critical/CriticalRecoveryContracts.java'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True,
                        help='Fresh classpath exported from the current build, with GT 5.09.54.277')
    parser.add_argument('--jar', type=Path, help='Optional exact candidate jar; otherwise use exported production classes')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-recovery-contract')
    parser.add_argument('--java-home', type=Path, help='JDK bin directory parent; defaults to java/javac on PATH')
    options = parser.parse_args()
    exported = options.classpath_file.resolve(strict=True)
    entries = [entry for entry in exported.read_text(encoding='utf-8-sig').strip().split(os.pathsep) if entry]
    gt = [entry for entry in entries if '/GT5-Unofficial/' in entry.replace(chr(92), '/')]
    if not gt or any('/5.09.54.277/' not in entry.replace(chr(92), '/') for entry in gt):
        parser.error('Use a fresh exported GT 5.09.54.277 classpath; no historical GT205 cache fallback is supported')
    # Jabel's auto-start javac plugin belongs to Gradle compilation, not this standalone probe.
    entries = [entry for entry in entries if 'jabel-javac-plugin' not in entry]
    out = options.out.resolve()
    out.relative_to(ROOT / 'build')
    candidate = options.jar.resolve(strict=True) if options.jar else None
    candidate_hash = digest(candidate) if candidate else None
    source_hash = digest(CONTRACT)
    java = str(options.java_home / 'bin/java') if options.java_home else shutil.which('java')
    javac = str(options.java_home / 'bin/javac') if options.java_home else shutil.which('javac')
    if not java or not javac:
        parser.error('Both java and javac are required; provide --java-home')
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    receipt_path = out / 'verification.json'
    # Replace any previous PASS before compilation, including failed/aborted launches.
    receipt_path.write_text(json.dumps(dict(status='RUNNING', contractSHA256=source_hash,
        classpathSHA256=digest(exported), realRestart='NOT_RUN'), indent=2), encoding='utf-8')
    cp = os.pathsep.join(map(str, [classes, *([candidate] if candidate else []), *entries]))

    def run(command, name):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(arg).replace(chr(92), '/') + '"' for arg in command[1:]), encoding='utf-8')
        result = subprocess.run([command[0], '@' + str(argfile)], cwd=ROOT, capture_output=True, timeout=60,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        log = result.stdout + result.stderr
        (out / (name + '.log')).write_bytes(log)
        if result.returncode:
            raise RuntimeError(name + ' failed; inspect ' + str(out / (name + '.log')))

    run([javac, '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), str(CONTRACT)], 'compile')
    report_path = out / 'contract.json'
    # A failed run must not leave an older PASS report looking current.
    if report_path.exists():
        report_path.unlink()
    run([java, '-Djava.awt.headless=true', '-cp', cp,
         'com.miaokatze.gtsr.common.critical.CriticalRecoveryContracts', str(report_path)], 'run')
    report = json.loads(report_path.read_text(encoding='utf-8'))
    if report.get('status') != 'PASS' or not report.get('checks') or not all(row.get('pass') for row in report['checks']):
        raise RuntimeError('Current recovery contract did not explicitly pass')
    if source_hash != digest(CONTRACT) or candidate and candidate_hash != digest(candidate):
        raise RuntimeError('Contract or candidate changed during the run')
    if candidate:
        actual = Path(unquote(urlparse(report['managerSource']).path).lstrip('/')).resolve()
        if actual != candidate:
            raise RuntimeError('Candidate manager CodeSource mismatch: ' + report['managerSource'])
    receipt = dict(status='PASS', contract=CONTRACT.relative_to(ROOT).as_posix(), checks=len(report['checks']),
                   contractSHA256=source_hash, classpathFile=str(exported), classpathSHA256=digest(exported),
                   candidate=str(candidate) if candidate else None, candidateSHA256=candidate_hash,
                   scope=report['scope'], historicalConstructionOwnershipSuite='NOT_RUN', realRestart='NOT_RUN')
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(dict(status='PASS', checks=receipt['checks'], report=str(out / 'verification.json'))))


if __name__ == '__main__':
    main()
