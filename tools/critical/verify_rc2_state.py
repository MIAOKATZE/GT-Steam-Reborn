"""Candidate-jar-only RC2 State/public-command integration; no product source overlay or FML startup."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-rc2-state-contract')
    args = parser.parse_args()
    candidate, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = [p for p in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix()
        and '/Applied-Energistics-2-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower()]
    cp = os.pathsep.join(map(str, [classes, candidate, gt, ae, ROOT / 'build/classes/java/patchedMc',
        ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar', ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/critical/CriticalV32Fixture.java', *[ROOT / 'tools/tests' / (name + '.java') for name in (
        'CriticalFoundationContract', 'CriticalConstructionOwnershipContract', 'CriticalRC2StateContract')]]
    original_hash = hashlib.sha256(candidate.read_bytes()).hexdigest()
    source_hashes = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}

    def run(executable, name, arguments):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in arguments), encoding='utf-8')
        result = subprocess.run([str(java / executable), '@' + str(argfile)], cwd=ROOT, capture_output=True,
            timeout=60, creationflags=subprocess.CREATE_NO_WINDOW)
        text = result.stdout.decode('utf-8', errors='replace') + result.stderr.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(text, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(text[-14000:])
        return text

    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp, '-d', classes, *sources])
    text = run('java.exe', 'contract', ['-Dfile.encoding=UTF-8', '-cp', cp, 'com.miaokatze.gtsr.common.critical.CriticalRC2StateContract', candidate])
    match = re.search(r'PASS CriticalRC2StateContract checks=(\d+)', text)
    assert match and hashlib.sha256(candidate.read_bytes()).hexdigest() == original_hash
    assert source_hashes == {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    receipt = {'status': 'PASS', 'jar': str(candidate), 'jarSHA256': original_hash, 'productOverlay': False,
        'checks': int(match.group(1)), 'sourceOrigins': {key: value.strip() for key, value in re.findall(r'^SOURCE ([^=]+)=(.+)$', text, re.M)},
        'fixtureSHA256': source_hashes,
        'scope': 'Real State commands/ticks/NBT, GT physical bus funding, manual loom validation and native port discovery; sparse world, fixture registry identities and native tile initialization seam; no FML/live ME/full 118k placement'}
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(receipt, ensure_ascii=False))


if __name__ == '__main__':
    main()
