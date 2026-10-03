"""Fresh Java 8 numerical planning, NBT, owner writes and real Minecraft entrance collision proof."""
import argparse, gzip, hashlib, json, re, subprocess, zipfile
from pathlib import Path
from native_terrain_fixture import native_sources, isolated_sourcepath
from remaster_convert import ROOT, SOURCE


def verify_envelopes(resources):
    assets = resources / 'assets/gtsr/remaster'
    fallback = ROOT / 'src/main/resources/assets/gtsr/remaster'
    catalog = json.loads((assets / 'catalog.json').read_text('utf8'))
    audited = []
    for descriptor in catalog['prefabs']:
        envelope = descriptor.get('placement', {}).get('entryApronEnvelope')
        if not envelope:
            continue
        source = SOURCE / 'preview/prefabs7' / f"{descriptor['id']}-v{descriptor['variant']}.js"
        sha = hashlib.sha256(source.read_bytes()).hexdigest()
        assert sha == descriptor['sourceSha256'] == envelope['sourceSha256']
        authored = json.loads(source.read_text('utf8').split(']=', 1)[1].rstrip(';\r\n'))
        manifest_file = assets / descriptor['file']
        manifest = json.loads(gzip.decompress((manifest_file if manifest_file.exists() else fallback / descriptor['file']).read_bytes()))
        assert manifest['metadata']['entryApronEnvelope'] == envelope
        protected = {}
        for x, z, y, length, kind in envelope['runs']:
            assert length > 0 and kind in (0, 1, 2, 10, 11, 12, 13)
            for yy in range(y, y + length):
                assert (x, yy, z) not in protected
                protected[x, yy, z] = kind
        ex, _, ez = envelope['entry']
        source_count = 0
        for x, y, z, length, index in authored['voxel']['runs']:
            if abs(z - ez) > 9:
                continue
            for xx in range(max(x, ex - 9), min(x + length, ex + 10)):
                kind = protected.get((xx, y, z))
                assert kind is not None, ('missing source protection', descriptor['id'], xx, y, z)
                material = authored['voxel']['palette'][index]
                if material.split('#')[0] != 'minecraft:air':
                    assert kind > 0
                source_count += 1
        for section in manifest['slices']:
            if section['x'] * 16 > ex + 9 or section['x'] * 16 + 15 < ex - 9 or section['z'] * 16 > ez + 9 or section['z'] * 16 + 15 < ez - 9:
                continue
            path = assets / section['file']
            for x, y, z, length, index in json.loads(gzip.decompress((path if path.exists() else fallback / section['file']).read_bytes())):
                if abs(z - ez) > 9:
                    continue
                for xx in range(max(x, ex - 9), min(x + length, ex + 10)):
                    kind = protected.get((xx, y, z))
                    assert kind is not None, 'missing final geometry protection'
                    if kind == 2:
                        assert manifest['palette'][index] == 'gtsr:ruins_mossroot_paving#0'
                    if kind >= 10:
                        assert manifest['palette'][index] == f'gtsr:ruins_mossroot_paving_stairs#{kind - 10}'
        audited.append({'id': descriptor['id'], 'variant': descriptor['variant'], 'sourceSHA': sha,
                        'protectedCells': len(protected), 'authoredCells': source_count})
    return audited


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=ROOT / 'src/main/resources')
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--fixed-only', action='store_true')
    parser.add_argument('--out', type=Path, default=ROOT / 'temp/entry-apron-exec')
    args = parser.parse_args()
    tmp = args.out.resolve()
    tmp.mkdir(parents=True, exist_ok=True)
    envelopes = verify_envelopes(args.resources.resolve())
    (tmp / 'envelope-audit.json').write_text(json.dumps(envelopes, indent=2), 'utf8')
    java_home = args.java_home or next((Path.home() / '.gradle/jdks').glob('*8-amd64-windows.2'))
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    classes = tmp / 'classes'
    classes.mkdir(exist_ok=True)
    cp = [classes, ROOT / 'build/classes/java/main', ROOT / 'build/classes/java/patchedMc']
    for name in ('guava-17.0.jar', 'commons-lang3-3.3.2.jar', 'log4j-api-2.0-beta9-fixed.jar',
                 'log4j-core-2.0-beta9-fixed.jar', 'gson-2.2.4.jar'):
        cp += [next(j for j in jars if j.name == name)]
    cp += [args.resources.resolve(), ROOT / 'src/main/resources', ROOT / 'build/resources/patchedMc']
    cp += [j for j in jars if 'jabel' not in j.name and 'byte-buddy' not in j.name]
    sources = []
    for name in ('RuinsArchitecture', 'RoyalArchitecture'):
        source = ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{name}.java'
        text = re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$',
                      '', source.read_text('utf8'), flags=re.M)
        target = tmp / f'{name}.java'
        target.write_text(text, 'utf8')
        sources.append(target)
    sources += [ROOT / f'tools/prosperity/{n}.java' for n in
                ('RemasterRuntimeCheck', 'StairCollisionCheck', 'SurfaceArrivalCheck', 'EntryApronCheck')]
    sources += [ROOT / f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/{n}.java'
                for n in ('RemasterEntryApron', 'RemasterSite', 'RemasterPlanner', 'RemasterWorldgen', 'RemasterTerrain')]
    sources = list(dict.fromkeys(sources + native_sources(ROOT)))
    argfile = tmp / 'compile.args'
    classpath = ';'.join(str(p).replace('\\', '/') for p in cp)
    argfile.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp)
                       + '"\n-cp "' + classpath + '"\n-d "' + classes.as_posix() + '"\n'
                       + '\n'.join('"' + p.as_posix() + '"' for p in sources), 'utf8')
    compiled = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    producer_sha = hashlib.sha256((ROOT / 'tools/prosperity/remaster_convert.py').read_bytes()).hexdigest()
    verifier_sha = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    subprocess.run([str(java_home / 'bin/javac.exe'), '@' + str(argfile)], check=True)
    # Java 8 has no runtime @argfile support; a manifest avoids Windows command length limits.
    entries = ' '.join(p.as_uri() + ('/' if p.is_dir() else '') for p in cp)
    line = 'Class-Path: ' + entries
    manifest = 'Manifest-Version: 1.0\r\n' + '\r\n '.join(line[i:i+70] for i in range(0, len(line), 70)) + '\r\n\r\n'
    with zipfile.ZipFile(tmp / 'classpath.jar', 'w') as archive:
        archive.writestr('META-INF/MANIFEST.MF', manifest)
    command = [str(java_home / 'bin/java.exe'), '-Xmx3G', '-Dgtsr.entry.output=' + str(tmp),
               '-cp', str(tmp / 'classpath.jar'), 'EntryApronCheck']
    if args.fixed_only:
        command.append('--fixed-only')
    with (tmp / 'run.log').open('w', encoding='utf8') as log:
        execution = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    receipt = {'javaHome': str(java_home), 'exitCode': execution.returncode, 'compiledSources': compiled,
               'producerSHA256': producer_sha,
               'verifierSHA256': verifier_sha,
               'auditedEnvelopes': len(envelopes),
               'classes': {str(p.relative_to(tmp)): hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in classes.rglob('*.class')},
               'catalogSHA256': hashlib.sha256((args.resources / 'assets/gtsr/remaster/catalog.json').read_bytes()).hexdigest(),
               'resources': str(args.resources), 'fixedOnly': args.fixed_only,
               'MC': 'real .6 x 1.8 Entity, step .5, strict radius8 / budget3000 unchanged; bilateral edges replayed'}
    (tmp / 'receipt.json').write_text(json.dumps(receipt, indent=2), 'utf8')
    print((tmp / 'run.log').read_text('utf8')[-1800:])
    if execution.returncode:
        raise SystemExit(execution.returncode)


if __name__ == '__main__':
    main()
