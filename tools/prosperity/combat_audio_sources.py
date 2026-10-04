"""One-time CC0 source freeze. Never silently update an existing pinned source."""
from pathlib import Path
import hashlib, json, shutil

root = Path(__file__).resolve().parents[2]
library = Path('D:/CodeMIAO/MIAOSound/backend/data/library/creature')
dest = root / 'plan/model/authored/audio/combat-v71'
(dest / 'original').mkdir(parents=True, exist_ok=True)
names = ['monster_01.ogg', 'alien_02.ogg', 'bug_02.ogg', 'weird_02.ogg',
         'roar_01.ogg', 'grunt_03.ogg', 'hurt_02.ogg', 'scream_01.ogg']
rows = []
for name in names:
    source = library / 'Audio' / name
    data = source.read_bytes()
    target = dest / 'original' / name
    if target.exists():
        assert target.read_bytes() == data, 'Pinned source differs; deliberate review required'
    else:
        shutil.copyfile(source, target)
    rows.append({'file': name, 'originalPath': str(source), 'sha256': hashlib.sha256(data).hexdigest()})
shutil.copyfile(library / 'License.txt', dest / 'original' / 'License.txt')
manifest = {'pack': '80 CC0 creature SFX', 'author': 'rubberduck', 'license': 'CC0-1.0',
    'sourcePage': 'https://opengameart.org/content/80-cc0-creature-sfx',
    'licenseVerified': '2026-10-04: source work page License(s): CC0; local License.txt copied',
    'sourceLicenseSha256': hashlib.sha256((library / 'License.txt').read_bytes()).hexdigest(),
    'sources': rows}
body = json.dumps(manifest, indent=2) + '\n'
path = dest / 'sources.json'
if path.exists():
    assert path.read_text() == body
else:
    path.write_text(body)
print('Pinned', len(rows), 'CC0 source recordings')
