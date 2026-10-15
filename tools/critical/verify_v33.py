"""Run jar-only V33 contracts against one real GT version, without starting a server.

Requires a built dev jar, the project's patched Minecraft classes, and resolved Gradle
dependencies. Only fixture Java is compiled; product source/classes are never overlaid.
All generated classes, logs, runtime files and receipts stay below the project build/.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / 'tools/critical/v33'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
CHECKS = {'core': 'CoreProbe', 'energy': 'EnergyProbe', 'solar': 'SolarProbe',
          'gui': 'ThermalGuiProbe', 'thermal': 'ThermalProcessingProbe',
          'dropdown': 'CriticalDropdownProbe'}
DEFAULT_CHECKS = ['core', 'energy', 'solar', 'gui']
GATES = [ROOT / 'tools/tests/structurelib-gates' / name
         for name in ('StructureLibAPI.java', 'FMLCommonHandler.java')]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def argfile(path, values):
    path.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
    return '@' + str(path)


def execute(java, name, values, directory, runtime):
    args = argfile(directory / (name + '.args'), values)
    with (directory / (name + '.log')).open('wb') as log:
        result = subprocess.run([str(java), args], stdout=log, stderr=subprocess.STDOUT,
                                cwd=runtime, timeout=120,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if result.returncode:
        raise RuntimeError(f'{name} failed ({result.returncode}); see {directory / (name + ".log")}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True, help='Built product dev jar; no source overlay')
    parser.add_argument('--gt', choices=['183', '205'], required=True)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-functional-v33/probe')
    parser.add_argument('--checks', default='all', help='all (four default entries) or comma-separated core,energy,solar,gui,thermal,dropdown')
    parser.add_argument('--java-home', type=Path)
    options = parser.parse_args()
    candidate, out = options.jar.resolve(), options.out.resolve()
    if not candidate.is_file() or candidate.suffix != '.jar':
        parser.error('--jar must be an existing dev jar')
    try:
        out.relative_to(ROOT / 'build')
    except ValueError:
        parser.error('--out must remain below this project build/ directory')
    selected = DEFAULT_CHECKS if options.checks == 'all' else options.checks.split(',')
    if not selected or any(name not in CHECKS for name in selected):
        parser.error('unknown --checks entry')
    java_home = options.java_home or Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2'
    suffix = '.exe' if os.name == 'nt' else ''
    java = java_home / 'bin' / ('java' + suffix)
    javac = java_home / 'bin' / ('javac' + suffix)
    if not java.is_file() or not javac.is_file():
        parser.error('JDK17 missing; supply --java-home')
    gt_jars = list((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial' /
                    ('5.09.54.' + options.gt)).glob('*/*-dev.jar'))
    if len(gt_jars) != 1:
        parser.error(f'Expected one resolved GT5U 5.09.54.{options.gt} dev jar; found {gt_jars}')
    patched = ROOT / 'build/classes/java/patchedMc'
    if not patched.is_dir():
        parser.error('Patched Minecraft classes missing; build the project first')
    jars = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
                  if '/GT5-Unofficial/' not in p.as_posix() and 'gtsr' not in p.name.lower())
    run = out / ('replay-' + options.gt + '-' + sha(candidate)[:12]
                 + ('' if options.checks == 'all' else '-' + '-'.join(selected)))
    classes, gates, runtime, empty = [run / n for n in ('classes', 'gates', 'runtime', 'empty-sourcepath')]
    for path in (classes, gates, runtime, empty):
        path.mkdir(parents=True, exist_ok=True)
    (classes / 'mcpmod.info').write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]', encoding='utf-8')
    source_files = sorted(SOURCES.glob('*.java'))
    before = {str(p.relative_to(ROOT)): sha(p) for p in source_files + GATES + [Path(__file__).resolve()]}
    # No build/classes/java/main, historical product jars, or independently compiled product
    # classes. The two gates replace only FMLCommonHandler/StructureLibAPI platform seams.
    cp = os.pathsep.join(map(str, [classes, gates, candidate, gt_jars[0], patched,
                                  ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
                                  ROOT / 'build/rfg/mclauncher-1.7.10.jar', *jars]))
    execute(javac, 'compile-gates', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                    '-cp', cp, '-d', gates, *GATES], run, runtime)
    execute(javac, 'compile-probes', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                     '-cp', cp, '-d', classes, *source_files], run, runtime)
    receipt = {'status': 'RUNNING', 'jar': str(candidate), 'jarSHA256': sha(candidate),
               'gt': '5.09.54.' + options.gt, 'gtJar': str(gt_jars[0]), 'gtSHA256': sha(gt_jars[0]),
               'fixtureSHA256': before, 'classpath': cp.split(os.pathsep),
               'productOverlay': False, 'results': {},
               'boundaries': ['Real GT recipe/helper/hatch/transaction/wireless and MUI sync classes',
                              'Unsafe allocation skips registry constructors; vanilla/fluid/item registry seed and sparse World',
                              'FMLCommonHandler and StructureLibAPI gates are platform seams only',
                              'Solar startSolar is invoked directly; world-data night ownership checked separately',
                              'GUI uses supplied server timeline with real IntSyncValue codec and text callbacks',
                              'No complete Forge server, real ME grid, GL rendering, packet transport or TPS measurement']}
    receipt_path = run / 'verification.json'
    receipt_path.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    for name in selected:
        target = 'com.miaokatze.gtsr.common.critical.' + CHECKS[name]
        report_path = run / (name + '.json')
        execute(java, name, ['-Djava.awt.headless=true', '-Dprobe.gt=5.09.54.' + options.gt,
                            '-Dprobe.main=' + target, '-cp', cp, 'ProbeLauncher', report_path], run, runtime)
        report = json.loads(report_path.read_text(encoding='utf-8'))
        if report.get('status') != 'PASS':
            raise RuntimeError(f'{name} did not report PASS')
        receipt['results'][name] = report
        receipt_path.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
        print(f'PASS GT{options.gt} {name}', flush=True)
    after = {str(p.relative_to(ROOT)): sha(p) for p in source_files + GATES + [Path(__file__).resolve()]}
    if before != after or receipt['jarSHA256'] != sha(candidate):
        raise RuntimeError('Fixture or candidate changed during the run')
    receipt['status'] = 'PASS'
    receipt_path.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(str(receipt_path))


if __name__ == '__main__':
    main()
