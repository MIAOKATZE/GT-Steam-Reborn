"""Compile only structure test probes onto an immutable production jar for explicit RED evidence."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile
from probe_srg_remap import Mapper

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'com/miaokatze/gtsr/common/dimension/testlab/'
TASKS = ('TestLabStructureContractTask', 'TestLabStructureFailureTask', 'TestLabStructurePolicyTask')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sandbox', type=Path, default=ROOT / 'build/server-testlab-mui2-v12138')
    parser.add_argument('--javac', type=Path, default=Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to(ROOT / 'build'):
        raise ValueError('Probe overlay output must stay in build/')
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        raise ValueError('Use a new immutable overlay filename')
    work = out.parent / (out.stem + '-compile')
    work.mkdir(exist_ok=False)
    classes = work / 'classes'
    classes.mkdir()
    sources = [ROOT / ('src/main/java/' + PREFIX + name + '.java') for name in TASKS]
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    dependencies = [ROOT / 'build/classes/java/patchedMc', args.jar.resolve(), args.sandbox / 'lwjgl3ify-forgePatches.jar']
    dependencies += sorted((args.sandbox / 'libraries').rglob('*.jar'))
    dependencies += [p for p in sorted((args.sandbox / 'mods').rglob('*.jar')) if not p.name.lower().startswith('gtsr')]
    dependencies += sorted(cache.glob('*/*/*/*/*.jar'))
    options = ['-encoding', 'UTF-8', '-proc:none', '--release', '8', '-implicit:none', '-sourcepath', str(work),
               '-cp', ';'.join(map(str, dependencies)), '-d', str(classes), *map(str, sources)]
    argfile = work / 'compile.args'
    argfile.write_text('\n'.join('"' + s.replace('\\', '/') + '"' for s in options), encoding='utf-8')
    result = subprocess.run([str(args.javac), '-J-Duser.language=en', '@' + str(argfile)], capture_output=True)
    (work / 'compile.log').write_bytes(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr).decode('utf-8', 'replace'))
    replacements = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    if not replacements or any(not any(name.startswith(PREFIX + task + '$') or name == PREFIX + task + '.class'
                                        for task in TASKS) for name in replacements):
        raise ValueError('Compilation produced a class outside the explicit test probe allowlist')
    mapper = Mapper(ROOT)
    remapping = {}
    for name, data in list(replacements.items()):
        replacements[name], remapping[name] = mapper.remap(data)
    original_hashes = {}
    with zipfile.ZipFile(args.jar) as original, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as overlay:
        for entry in original.infolist():
            if any(entry.filename == PREFIX + task + '.class' or entry.filename.startswith(PREFIX + task + '$')
                   for task in TASKS):
                continue
            data = original.read(entry.filename)
            overlay.writestr(entry, data)
            original_hashes[entry.filename] = sha(data)
        for name, data in sorted(replacements.items()):
            overlay.writestr(name, data)
    with zipfile.ZipFile(out) as overlay:
        unchanged = all(sha(overlay.read(name)) == digest for name, digest in original_hashes.items())
    receipt = {'kind': 'TEST_PROBE_OVERLAY_NOT_ORIGINAL_PRODUCTION_JAR', 'production_jar': str(args.jar.resolve()),
               'production_sha256': sha(args.jar.read_bytes()), 'overlay': str(out), 'overlay_sha256': sha(out.read_bytes()),
               'sources': {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in sources},
               'replaced_test_classes': {name: sha(data) for name, data in replacements.items()},
               'all_other_entries_byte_identical': unchanged, 'class_count': len(replacements)}
    receipt['minecraft_member_remapping'] = remapping
    out.with_suffix('.receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2))
    if not unchanged:
        raise RuntimeError('Production entry changed')


if __name__ == '__main__':
    main()
