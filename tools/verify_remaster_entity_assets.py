"""Copy only appended roster assets; verify byte identity and authored skill clips."""
import hashlib
import json
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = next(p for p in pathlib.Path('E:/CodeGT/GT-Outpost/plan/model').iterdir() if p.name.startswith('mobV1'))
enum = (ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java').read_text(encoding='utf-8')
codes = re.findall(r'"((?:dr|di|do)-\d\d)"[^\n]+BattleStyle\.(?:AUTHORED|CONTROLLED)', enum)
records = []
for code in codes:
    source = next(p for p in SOURCE.iterdir() if p.name.startswith(code.upper()))
    dest = ROOT / 'src/main/resources/assets/gtsr/liminal' / code
    behavior = json.loads((source / 'code/behavior.json').read_text(encoding='utf-8'))
    rig = json.loads((source / 'models/rig_animation.json').read_text(encoding='utf-8'))
    clips = rig['animations']
    assert all(s['clip'] in clips for s in behavior['skills']), code
    obj = (source / f'models/{code}.obj').read_text(encoding='utf-8')
    groups = {g['name'] for g in rig['groups']}
    assert all(line.split()[1] in groups for line in obj.splitlines() if line.startswith('g ')), code
    files = [f'models/{code}.obj', 'models/rig_animation.json', f'textures/model/{code}.png', 'code/behavior.json']
    if code == 'do-02': files.append('textures/model/do-02_alt.png')
    for rel in files:
        src, dst = source / rel, dest / rel
        if '--copy' in sys.argv:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
        a, b = hashlib.sha256(src.read_bytes()).hexdigest(), hashlib.sha256(dst.read_bytes()).hexdigest()
        assert a == b, str(dst)
        records.append({'code': code, 'path': rel, 'sha256': a})
assert len(codes) == 22
print(json.dumps({'appended': len(codes), 'files': len(records), 'hashes': records if '--verbose' in sys.argv else 'all-identical'}, ensure_ascii=False))
