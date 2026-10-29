"""Source-compiled production dynamic emission rasterization; starts no game client."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/client/critical/CriticalMeshCache.java'
PROBE = Path(__file__).with_name('Emission39Probe.java')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--baseline', action='store_true', help='Compile v2.0.38 and require reproduced symptom')
    mode.add_argument('--jar', type=Path, help='Read production classes from candidate dev JAR, without recompiling them')
    args = parser.parse_args()
    out = ROOT / 'build/critical-emission-v2039' / ('baseline' if args.baseline else 'jar' if args.jar else 'candidate')
    classes, natives = out / 'classes', out / 'natives'
    classes.mkdir(parents=True, exist_ok=True)
    natives.mkdir(exist_ok=True)
    source = SOURCE
    if args.baseline:
        source = out / 'source/CriticalMeshCache.java'
        source.parent.mkdir(exist_ok=True)
        source.write_bytes(subprocess.run(['git', 'show', 'v2.0.38:' + SOURCE.relative_to(ROOT).as_posix()],
            cwd=ROOT, capture_output=True, check=True).stdout)
    candidate = args.jar.resolve() if args.jar else None
    archive = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(archive) as z:
        z.extractall(natives)
    lwjgl = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl/*/*/lwjgl-*.jar'))
    jars = [p for p in CACHE.glob('*/*/*/*/*.jar') if p != lwjgl and p.parts[-5] != 'org.lwjgl']
    cp = os.pathsep.join(map(str, [classes, *([candidate] if candidate else []), ROOT / 'build/classes/java/main',
        ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', lwjgl, *jars]))
    for exe, label, values in [
        ('javac', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes,
            *([] if candidate else [source]), PROBE]),
        ('java', 'run', ['-Djava.awt.headless=true', '-Djava.library.path=' + str(natives),
            '-cp', cp, 'com.miaokatze.gtsr.client.critical.Emission39Probe',
            ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2', out]),
    ]:
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        process = subprocess.run([str(JAVA / (exe + '.exe')), '@' + str(argfile)], capture_output=True,
            timeout=90, creationflags=subprocess.CREATE_NO_WINDOW)
        text = (process.stdout + process.stderr).decode('utf-8', errors='replace')
        (out / (label + '.log')).write_text(text, encoding='utf-8')
        if process.returncode:
            raise RuntimeError(text[-6000:])
    report = json.loads((out / 'runtime.json').read_text())
    passed = all(case['glError'] == 0 and case['unmaskedWorldLightMaxError'] <= 2
        and case['bodyAndDeclaredMaskStrengthMaxError'] <= 2 for case in report['cases'])
    report['status'] = 'PASS' if passed else 'REPRODUCED'
    report['sourceSHA256'] = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [SOURCE, PROBE, Path(__file__)]}
    report['compiledMeshSource'] = {'origin': 'git v2.0.38' if args.baseline else 'dev JAR' if candidate else 'working tree',
        'sha256': hashlib.sha256(candidate.read_bytes() if candidate else source.read_bytes()).hexdigest()}
    if candidate:
        report['candidateJar'] = str(candidate)
        assert report['meshCodeSource'] == candidate.as_uri().replace('file:///', 'file:/'), report['meshCodeSource']
    catalog = ROOT / 'src/main/resources/assets/gtsr/critical/materials.json'
    definitions = json.loads(catalog.read_text())['definitions']
    luminous = {key: value for key, value in definitions.items() if value['emissionStrength'] > 0}
    assert len(luminous) == 42 and all(value['lightValue'] == 15 for value in luminous.values())
    v2_luminous = {key: value['alphaMode'] for key, value in luminous.items() if key.startswith('v2_')}
    assert len(v2_luminous) == 15 and set(v2_luminous.values()) == {'opaque'}
    assert definitions['v2_loom_guide']['emissionStrength'] == .85
    assert definitions['v2_gravity_modulator']['emissionStrength'] == 1.15
    report['v2EmissiveAlphaModes'] = v2_luminous
    textures = ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2'
    report['resourceSHA256'] = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in [catalog, *(textures / (name + '.png') for name in ['superconducting_contact',
            'superconducting_contact__top', 'superconducting_contact_emission', 'loom_guide', 'loom_guide_emission',
            'gravity_modulator', 'gravity_modulator_emission', 'reinforced_panel'])]}
    if candidate:
        with zipfile.ZipFile(candidate) as archive:
            for name, digest in report['resourceSHA256'].items():
                assert hashlib.sha256(archive.read(name.removeprefix('src/main/resources/'))).hexdigest() == digest, name
    (out / 'receipt.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'cases': len(report['cases']),
        'maxPixelError': max(case['bodyAndDeclaredMaskStrengthMaxError'] for case in report['cases']),
        'meshCodeSource': report['meshCodeSource'], 'receipt': str(out / 'receipt.json')}, indent=2))
    if args.baseline:
        assert not passed, 'HEAD did not reproduce the regression'
    else:
        assert passed, 'Dynamic body ignores world light; see receipt.json'


if __name__ == '__main__':
    main()
