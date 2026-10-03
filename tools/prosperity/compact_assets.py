"""Remove retired production prefabs and block factories after compact scene compilation."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/gtsr/remaster'
BACKUP = ROOT / 'temp/compact-rebuild/retired-production'

def retire(path, base, group):
    resolved = path.resolve()
    if path.is_symlink() or not resolved.is_relative_to(base.resolve()):
        raise ValueError('Refusing redirected asset: ' + str(path))
    rel = path.relative_to(base)
    dest = BACKUP / group / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, dest)
    record = {'file': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    path.unlink()
    return record

def main():
    global BACKUP
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive-directory', type=Path, default=BACKUP,
                        help='Isolated archive/report directory for this compilation.')
    args = parser.parse_args()
    BACKUP = args.archive_directory.resolve()
    if not BACKUP.parent.is_relative_to((ROOT / 'temp').resolve()):
        parser.error('Archive directory must stay within this workspace temp directory')
    catalog = json.loads((OUT / 'catalog.json').read_text(encoding='utf8'))
    assert len(catalog['prefabs']) == 3
    referenced = {'catalog.json', 'blocks.json'}
    for record in catalog['prefabs']:
        referenced.add(record['file'])
        data = json.loads(gzip.decompress((OUT / record['file']).read_bytes()))
        referenced.update(s['file'] for s in data['slices'])
    retired = [retire(p, OUT, 'prefabs') for p in (OUT / 'prefabs').rglob('*.json.gz')
               if p.relative_to(OUT).as_posix() not in referenced]
    blocks = json.loads((OUT / 'blocks.json').read_text(encoding='utf8'))['blocks']
    wanted = {('gtsr:' + b['id'] if ':' not in b['id'] else b['id']) for b in blocks}
    java = ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java'
    text = java.read_text(encoding='utf8')
    pattern = re.compile(r'    private static RemasterBlock block(\d+)\(\) \{.*?(?=\n    private static RemasterBlock block|\n\}\s*$)', re.S)
    removed = []
    removed_indices = set()
    def select_factory(match):
        name = re.search(r'"(gtsr:[a-z0-9_]+)"', match.group())
        if name and name.group(1) not in wanted:
            removed.append(name.group(1))
            removed_indices.add(match.group(1))
            return ''
        return match.group()
    text = pattern.sub(select_factory, text)
    text = re.sub(r'(?m)^\s*register\(block(\d+)\(\)\);\n',
                  lambda m: '' if m.group(1) in removed_indices else m.group(), text)
    remaining = set(re.findall(r'new RemasterBlock\(\s*"(gtsr:[a-z0-9_]+)"', text))
    assert remaining == wanted, {'missingFactories': sorted(wanted - remaining), 'unusedFactories': sorted(remaining - wanted)}
    text = text.replace('Ninety-four authored materials, imported exactly from revision 4 through 7 snapshots.',
                        'Only materials used by the three current scenes are registered.')
    java.write_text(text, encoding='utf8')
    retained_textures = set()
    for source in (ROOT / 'src/main/java').rglob('*.java'):
        retained_textures.update(re.findall(r'remaster_[0-9a-f]{16}', source.read_text(encoding='utf8')))
    textures = ROOT / 'src/main/resources/assets/gtsr/textures/blocks'
    retired_textures = [retire(p, textures, 'textures') for p in textures.glob('remaster_*.png')
                        if p.stem not in retained_textures]
    actual = {p.relative_to(OUT).as_posix() for p in OUT.rglob('*') if p.is_file()}
    assert actual == referenced
    report = {'productionScenes': 3, 'registeredBlocks': len(wanted), 'removedBlockFactories': removed,
              'retiredPrefabs': retired, 'retiredTextures': retired_textures,
              'referencedFiles': len(referenced), 'missingOrphanFiles': 0}
    BACKUP.parent.mkdir(parents=True, exist_ok=True)
    (BACKUP.parent / 'pruned-assets.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if not isinstance(v, list)}))

if __name__ == '__main__':
    main()
