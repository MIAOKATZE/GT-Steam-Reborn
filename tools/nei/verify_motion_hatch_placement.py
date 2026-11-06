"""Replay production survival construction's next native hatch request without launching Minecraft.

Fixtures seed a partially built motion piece, then execute production checkMachine and
survivalConstruct. The source stops at the first real GT/StructureLib item request, so
this verifies placement selection rather than just BlocksToPlace catalog enumeration.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MACHINE = 'src/main/java/com/miaokatze/gtsr/common/machine/MTEAtmosphericCentrifuge.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', type=Path, default=ROOT / 'build/task03-compile-classpath.txt')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/motion-hatch-placement')
    parser.add_argument('--baseline', help='Also reproduce the defect using this Git revision of the machine source')
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    dependencies = [ROOT / path for path in ['build/classes/java/main', 'build/classes/java/patchedMc',
                    'build/rfg/recompiled_minecraft-1.7.10.jar', 'build/rfg/mclauncher-1.7.10.jar', 'src/main/resources']]
    for value in args.classpath.read_text(encoding='utf-8-sig').strip().split(os.pathsep):
        path = Path(value)
        if not path.exists() or '/build/classes/java/main' in path.as_posix().lower():
            continue
        if path.suffix == '.jar':
            with zipfile.ZipFile(path) as archive:
                if 'META-INF/services/com.sun.source.util.Plugin' in archive.namelist():
                    continue
        dependencies.append(path)
    for group in ['com.falsepattern/chunkapi-mc1.7.10/0.8.3/*/*-dev.jar',
                  'xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api/1.3.5/*/*downgraded-8.jar']:
        dependencies.extend(cache.glob(group))
    expected = ['GT5-Unofficial-5.09.54.265-dev.jar', 'StructureLib-1.4.47-dev.jar']
    versions = {name: [path for path in dependencies if path.name == name] for name in expected}
    assert all(len(paths) == 1 for paths in versions.values()), versions
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    receipt = {'boundary': 'Actual production checkMachine/survivalConstruct and native GT265/SL47 survival '
                'placement filter; sparse world, seeded frames/material IDs, empty item source interrupted '
                'at first item query. No completed construction, Mixins, GUI, particles or GL assertion.',
               'dependencies': {name: {'path': str(paths[0]), 'sha256': sha(paths[0])}
                                for name, paths in versions.items()}, 'phases': {}}
    source = ROOT / 'tools/nei/MotionHatchPlacementRegression.java'
    for phase in (['red', 'green'] if args.baseline else ['green']):
        phase_out = out / phase
        classes = phase_out / 'classes'
        classes.mkdir(parents=True, exist_ok=True)
        machine = ROOT / MACHINE
        if phase == 'red':
            machine = phase_out / 'MTEAtmosphericCentrifuge.java'
            machine.write_bytes(subprocess.check_output(['git', 'show', args.baseline + ':' + MACHINE], cwd=ROOT))
        cp = os.pathsep.join(map(str, [classes, *dependencies]))
        commands = [('compile', 'javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes,
                     ROOT / 'tools/tests/Issue37RegistrationRegression.java', source, machine]),
                    ('run', 'java', ['-Xmx768m', '-Djava.awt.headless=true', '-cp', cp,
                                    'MotionHatchPlacementRegression', str(phase == 'green').lower()])]
        for label, tool, options in commands:
            argfile = phase_out / (label + '.args')
            argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf-8')
            process = subprocess.run([str(java / (tool + '.exe')), '@' + str(argfile)], cwd=ROOT,
                                     capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, timeout=90)
            (phase_out / (label + '.log')).write_bytes(process.stdout + process.stderr)
            if process.returncode:
                raise RuntimeError(label + ' failed; see ' + str(phase_out / (label + '.log')))
        output = process.stdout.decode('utf-8', 'replace')
        assert 'RESULT ' + phase + ' PASS cases=8' in output
        receipt['phases'][phase] = {'machineSourceSHA256': sha(machine), 'cases':
            [line for line in output.splitlines() if line.startswith(('CASE ', 'RESULT ', 'SOURCE '))]}
    receipt['status'] = 'PASS'
    receipt['baseline'] = args.baseline
    (out / 'checks.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('PASS ' + str(out / 'checks.json'))


if __name__ == '__main__':
    main()
