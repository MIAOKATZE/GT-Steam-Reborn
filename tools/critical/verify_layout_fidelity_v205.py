"""Compare real v2.0.4 and v2.0.5 candidate jars in isolated JVMs, without GL/bootstrap."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    baseline, candidate, out = args.baseline.resolve(), args.candidate.resolve(), args.out.resolve()
    out.relative_to(ROOT / 'build')
    out.mkdir(parents=True, exist_ok=True)
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    probe = ROOT / 'tools/critical/CriticalLayoutFidelityProbe.java'
    hashes = {str(p): sha(p) for p in (baseline, candidate, probe, Path(__file__).resolve())}
    assert sha(baseline) == 'a9d1a0e6dd7e0cb630c3dadee204d6b5c1b08da9a4c4e1da63d4f5a181518117', 'Unknown formal baseline'
    assert sha(candidate) == '2e8543f1c00fb59ad6eaf5efb047429452e7faae20eee3e22a551cfd35873e62', 'Unknown immutable candidate'
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = sorted(p for p in CACHE.glob('*/*/*/*/*.jar') if '/GT5-Unofficial/' not in p.as_posix()
                          and '/Applied-Energistics-2-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower())
    empty, classes, runtime = out / 'empty', out / 'classes', out / 'runtime'
    for directory in (empty, classes, runtime):
        directory.mkdir(exist_ok=True)

    def cp(jar):
        return os.pathsep.join(map(str, [classes, jar, gt, ae, ROOT / 'build/classes/java/patchedMc',
                                        ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
                                        ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))

    def run(executable, label, values):
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(java / executable), '@' + str(argfile)], cwd=runtime,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=240,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        (out / (label + '.log')).write_bytes(result.stdout)
        if result.returncode:
            raise RuntimeError(f'{label} failed: {out / (label + ".log")}')

    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty, '-cp', cp(baseline), '-d', classes, probe])
    for label, jar in [('baseline', baseline), ('candidate', candidate)]:
        run('java.exe', label, ['-Xmx1536m', '-Djava.awt.headless=true', '-cp', cp(jar),
                               'com.miaokatze.gtsr.common.critical.CriticalLayoutFidelityProbe', jar,
                               out / label, out / 'old-save-nbt', str(label == 'baseline').lower()])
    old = json.loads((out / 'baseline/actual.json').read_text(encoding='utf-8'))
    new = json.loads((out / 'candidate/actual.json').read_text(encoding='utf-8'))
    differences = []
    if old['cases'] != new['cases']:
        for name in old['cases']:
            for key, value in old['cases'][name].items():
                if value != new['cases'][name].get(key):
                    differences.append({'case': name, 'field': key, 'baseline': value, 'candidate': new['cases'][name].get(key)})
    row_count = 0
    digests = {}
    for old_file in sorted((out / 'baseline').glob('*.tsv.gz')):
        new_file = out / 'candidate' / old_file.name
        old_bytes, new_bytes = gzip.decompress(old_file.read_bytes()), gzip.decompress(new_file.read_bytes())
        row_count += old_bytes.count(b'\n')
        digests[old_file.name] = {'baseline': hashlib.sha256(old_bytes).hexdigest(), 'candidate': hashlib.sha256(new_bytes).hexdigest(), 'rows': old_bytes.count(b'\n')}
        if old_bytes != new_bytes:
            first = next((i for i, pair in enumerate(zip(old_bytes.splitlines(), new_bytes.splitlines())) if pair[0] != pair[1]), None)
            differences.append({'file': old_file.name, 'firstDifferentIndex': first, 'baselineRows': old_bytes.count(b'\n'), 'candidateRows': new_bytes.count(b'\n'),
                                'baselineExample': old_bytes.splitlines()[first].decode() if first is not None else None,
                                'candidateExample': new_bytes.splitlines()[first].decode() if first is not None else None})
    assert hashes == {str(p): sha(p) for p in (baseline, candidate, probe, Path(__file__).resolve())}, 'Inputs changed'
    report = {'status': 'PASS' if not differences else 'DIFFERENT', 'productOverlay': False, 'cases': len(old['cases']), 'comparedRows': row_count,
              'baseline': str(baseline), 'candidate': str(candidate), 'inputSHA256': hashes, 'baselineSources': old['codeSources'], 'candidateSources': new['codeSources'],
              'actual': {'baseline': old['cases'], 'candidate': new['cases']}, 'sequenceDigests': digests, 'differences': differences,
              'scope': ['Real jar allVoxels, material alias/canonical block registry identity, metadata, stage, group, model and per-index order',
                        'Formal baseline writeToNBT snapshots loaded by candidate; placed/repair byte arrays and paid index mapping compared',
                        'Six families; current four released kinds T3/8parallel; historical total-eight parallel/speed/economy/mixed representatives',
                        'Real hatch/resource/optional ports, construction resource routing and model/job fingerprints',
                        'Block numeric IDs use a deterministic fixture registry, not a user world ID map; no FML, server, client or GL']}
    (out / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'cases': report['cases'], 'comparedRows': row_count, 'differences': len(differences), 'report': str(out / 'verification.json')}))


if __name__ == '__main__':
    main()
