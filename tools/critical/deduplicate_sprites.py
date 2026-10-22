"""Audit byte-identical V2 sprites together with animation metadata; optionally consolidate palette refs.

Geometry files and legacy texture paths are never removed. Reports use raw DEFLATE size (ZIP method 8),
not PNG file size; --jar reports actual compressed entry sizes from a built JAR.
"""
import argparse
import collections
import hashlib
import json
from material_palette import expand_palette
from pathlib import Path
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / 'src/main/resources'
TEXTURES = RESOURCES / 'assets/gtsr/textures/blocks'
PALETTE = RESOURCES / 'assets/gtsr/critical/materials.json'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sprite_path(name):
    domain, path = name.split(':', 1)
    return RESOURCES / 'assets' / domain / 'textures/blocks' / (path + '.png')


def payload(path):
    meta = Path(str(path) + '.mcmeta')
    return (path.read_bytes(), meta.read_bytes() if meta.exists() else None)


def deflated(data):
    return len(zlib.compress(data, 6)) - 6


def references(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ('albedo', 'emission', 'frozenAlbedo', 'frozenEmission') and child:
                yield value, key, child
            else:
                yield from references(child)
    elif isinstance(value, list):
        for child in value:
            yield from references(child)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--baseline-jar', type=Path, help='Verify every palette image/frame and historical geometry against this JAR')
    args = parser.parse_args()
    palette = json.loads(PALETTE.read_text(encoding='utf-8'))
    before_refs = list(references(palette))
    before = [payload(sprite_path(name)) for _, _, name in before_refs]
    groups = collections.defaultdict(list)
    for path in sorted((TEXTURES / 'critical/v2').rglob('*.png')):
        png, meta = payload(path)
        groups[(digest(png), digest(meta) if meta is not None else None)].append(path)
    replacements = {}
    redundant = []
    for paths in groups.values():
        if len(paths) < 2:
            continue
        # Prefer authored base names to generated CTM names when bytes AND animation are equal.
        keep = min(paths, key=lambda p: ('ctm' in p.parts, len(p.as_posix()), p.as_posix()))
        name = 'gtsr:' + keep.relative_to(TEXTURES).with_suffix('').as_posix()
        for path in paths:
            if path == keep:
                continue
            old = 'gtsr:' + path.relative_to(TEXTURES).with_suffix('').as_posix()
            replacements[old] = name
            redundant.append(path)
    changed = 0
    for parent, key, name in before_refs:
        if name in replacements:
            parent[key] = replacements[name]
            changed += 1
    after = [payload(sprite_path(name)) for _, _, name in references(palette)]
    if before != after:
        raise RuntimeError('Palette texture pixels or animation metadata changed')
    # Refuse removal if a V2 sprite is loaded directly by product code or another resource.
    text_files = list((ROOT / 'src/main/java').rglob('*.java'))
    text_files += [p for p in RESOURCES.rglob('*') if p.is_file() and p != PALETTE
                   and p.suffix in ('.json', '.properties', '.lang', '.mcmeta')]
    for path in text_files:
        source = path.read_text(encoding='utf-8')
        for name in replacements:
            if name in source:
                raise RuntimeError(f'Direct sprite reference in {path}: {name}')
    removed = [file for p in redundant for file in (p, Path(str(p) + '.mcmeta')) if file.exists()]
    report = {
        'status': 'PASS', 'applied': args.apply, 'equivalentPaletteReferences': len(before),
        'rewrittenReferences': changed, 'duplicateSprites': len(redundant), 'removedFiles': len(removed),
        'redundantRawBytes': sum(p.stat().st_size for p in removed),
        'redundantDeflateBytes': sum(deflated(p.read_bytes()) for p in removed),
        'replacements': replacements,
        'boundary': 'Exact PNG and mcmeta bytes, including metadata absence; all geometry paths retained.'}
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            entries = [e for e in jar.infolist() if not e.is_dir()]
            report['jar'] = {'bytes': args.jar.stat().st_size, 'entries': len(entries),
                'resourcesCompressedBytes': sum(e.compress_size for e in entries if e.filename.startswith('assets/')),
                'v2SpritesCompressedBytes': sum(e.compress_size for e in entries if e.filename.startswith('assets/gtsr/textures/blocks/critical/v2/'))}
            duplicate_resources = collections.defaultdict(list)
            for entry in entries:
                if entry.filename.startswith('assets/'):
                    duplicate_resources[digest(jar.read(entry))].append(entry)
            duplicate_resources = [group for group in duplicate_resources.values() if len(group) > 1]
            report['jar']['remainingDuplicateResourceGroups'] = len(duplicate_resources)
            report['jar']['remainingDuplicateResources'] = [
                {'paths': [entry.filename for entry in group],
                 'redundantCompressedBytes': sum(entry.compress_size for entry in group[1:]),
                 'metadataBoundToTexturePath': all(entry.filename.endswith('.png.mcmeta') for entry in group)}
                for group in duplicate_resources]
    if args.baseline_jar:
        with zipfile.ZipFile(args.baseline_jar) as baseline:
            candidate = zipfile.ZipFile(args.jar) if args.jar else None
            def read_current(name):
                return candidate.read(name) if candidate else (RESOURCES / name).read_bytes()
            def read_sprite(archive, name):
                domain, path = name.split(':', 1)
                png = 'assets/' + domain + '/textures/blocks/' + path + '.png'
                meta = png + '.mcmeta'
                if archive:
                    return (archive.read(png), archive.read(meta) if meta in archive.namelist() else None)
                return payload(RESOURCES / png)
            old_palette = expand_palette(json.loads(baseline.read('assets/gtsr/critical/materials.json')))
            new_palette = expand_palette(json.loads(read_current('assets/gtsr/critical/materials.json')))
            old_names = [name for _, _, name in references(old_palette)]
            new_names = [name for _, _, name in references(new_palette)]
            if len(old_names) != len(new_names):
                raise RuntimeError('Baseline palette reference count changed')
            for old, new in zip(old_names, new_names):
                old_png, old_meta = read_sprite(baseline, old)
                new_png, new_meta = read_sprite(candidate, new)
                # Git checkouts and processResources may normalize JSON line endings.
                old_animation = json.loads(old_meta) if old_meta is not None else None
                new_animation = json.loads(new_meta) if new_meta is not None else None
                if old_png != new_png or old_animation != new_animation:
                    raise RuntimeError(f'Baseline sprite/frame mismatch: {old} -> {new}')
            models = [n for n in baseline.namelist() if n.startswith('assets/gtsr/critical/models/') and not n.endswith('/')]
            for name in models:
                if json.loads(baseline.read(name)) != json.loads(read_current(name)):
                    raise RuntimeError('Historical geometry changed: ' + name)
            report['baselineEquivalence'] = {'paletteReferences': len(old_names), 'historicalModelResources': len(models),
                'comparison': 'Exact PNG bytes; parsed animation and geometry JSON (line endings ignored).'}
            if candidate:
                old_entries = {e.filename: e for e in baseline.infolist() if not e.is_dir()}
                new_entries = {e.filename: e for e in candidate.infolist() if not e.is_dir()}
                removed_sprite_entries = [e for n, e in old_entries.items() if n not in new_entries and n.startswith('assets/gtsr/textures/blocks/critical/v2/')]
                report['baselineEquivalence'].update({
                    'removedSpriteEntries': len(removed_sprite_entries),
                    'removedSpriteCompressedBytes': sum(e.compress_size for e in removed_sprite_entries),
                    'totalJarBytesSaved': args.baseline_jar.stat().st_size - args.jar.stat().st_size,
                    'assetsCompressedBytesSaved': sum(e.compress_size for e in old_entries.values() if e.filename.startswith('assets/')) - report['jar']['resourcesCompressedBytes']})
                candidate.close()
    if args.apply:
        # Keep JSON layout stable: replace only exact quoted resource identifiers.
        source = PALETTE.read_text(encoding='utf-8')
        for old, new in replacements.items():
            source = source.replace(json.dumps(old), json.dumps(new))
        if json.loads(source) != palette:
            raise RuntimeError('Unexpected palette rewrite')
        PALETTE.write_text(source, encoding='utf-8')
        for path in removed:
            path.unlink()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    summary = {k: v for k, v in report.items() if k != 'replacements'}
    if 'jar' in summary:
        summary['jar'] = {k: v for k, v in summary['jar'].items() if k != 'remainingDuplicateResources'}
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
