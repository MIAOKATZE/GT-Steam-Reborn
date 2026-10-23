"""Bounded stellar thermal accounting contract against real Minecraft NBT, without FML startup."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--source', action='store_true', help='Compile current three production classes; source-only evidence')
    mode.add_argument('--jar', type=Path, help='Use production classes exclusively from one built dev jar')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--native', action='store_true', help='Also test real GT205 builder metadata with isolated bootstrap')
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    dependencies = sorted(p for p in CACHE.glob('*/*/*/*/*.jar') if 'gtsr' not in p.name.lower()
        and '/GT5-Unofficial/' not in p.as_posix() and '/Applied-Energistics-2-Unofficial/' not in p.as_posix())
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    candidate = [args.jar.resolve()] if args.jar else []
    candidate_hash = hashlib.sha256(args.jar.read_bytes()).hexdigest() if args.jar else None
    main = [ROOT / 'build/classes/java/main', *sorted((ROOT / 'build/libs').glob('*-dev.jar'))] if args.source and args.native else []
    cp = os.pathsep.join(map(str, [classes, *candidate, *main, gt, ae, ROOT / 'build/classes/java/patchedMc',
        ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar', ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/tests/CriticalStellarContract.java']
    if args.source:
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java') for name in
            ('CriticalEnergyDebit', 'CriticalStellarPolicy', 'CriticalStellarRuntime')]
    if args.native:
        sources += [ROOT / 'tools/tests' / name for name in
            ('Issue37RegistrationRegression.java', 'CriticalStellarNativeContract.java')]
        if args.source:
            sources += [ROOT / 'src/main/java/com/miaokatze/gtsr' / name for name in
                ('common/critical/CriticalGTRecipes.java', 'common/critical/recipe/RecipeMatch.java',
                 'common/machine/MTECriticalMachine.java')]
    hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}

    def run(exe, name, values):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(java / exe), '@' + str(argfile)], cwd=out,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = result.stdout.decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed: ' + output[-4000:])
        return output

    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp, '-d', classes, *sources])
    result = run('java.exe', 'contract', ['-cp', cp, 'com.miaokatze.gtsr.common.critical.CriticalStellarContract', *candidate])
    if args.native:
        properties = ['-Dstellar.jar=' + str(args.jar.resolve())] if args.jar else []
        result += run('java.exe', 'native-contract', [*properties, '-cp', cp, 'CriticalStellarNativeContract'])
    if hashes != {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}:
        raise RuntimeError('Tested source changed during verification; use a fresh output directory and retry')
    if args.jar and hashlib.sha256(args.jar.read_bytes()).hexdigest() != candidate_hash:
        raise RuntimeError('Candidate jar changed during verification')
    receipt = {'status': 'PASS', 'productOverlay': args.source, 'sourceSHA256': hashes,
        'jar': str(args.jar.resolve()) if args.jar else None, 'result': result.strip(),
        'scope': 'Real NBT with synthetic debit receipts; no FML, real GT hatches, live ME or crash atomicity claim'}
    if args.jar:
        receipt['jarSHA256'] = candidate_hash
    (out / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print(result.strip())


if __name__ == '__main__':
    main()
