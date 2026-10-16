"""Export the approved geometry and original textures; never generate replacement art."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'plan/临时计划/临界机器设计计划'
DEST = ROOT / 'src/main/resources/assets/gtsr/critical'


def export():
    DEST.mkdir(parents=True, exist_ok=True)
    palette = json.loads((SOURCE / 'textures/atlaspalette.json').read_text(encoding='utf-8'))
    materials = {}
    for alias, value in palette.items():
        materials[alias] = {k: value[k] for k in ('id', 'alphaMode', 'emissionStrength')}
        materials[alias]['animated'] = bool(value.get('animation'))
    (DEST / 'materials.json').write_text(json.dumps(materials, ensure_ascii=False), encoding='utf-8')
    manifest = {'source': 'plan/临时计划/临界机器设计计划', 'files': {}}
    for source in sorted((SOURCE / 'models').glob('*.geometry.json')):
        model = json.loads(source.read_text(encoding='utf-8'))
        positions = set()
        for voxel in model['blocks']:
            assert len(voxel) == 5 and voxel[3] in materials
            assert all(isinstance(x, int) for x in voxel[:3])
            assert tuple(voxel[:3]) not in positions, source.name
            positions.add(tuple(voxel[:3]))
        target = DEST / 'models' / source.name
        target.parent.mkdir(exist_ok=True)
        shutil.copyfile(source, target)
        manifest['files'][str(target.relative_to(ROOT)).replace('\\', '/')] = hashlib.sha256(source.read_bytes()).hexdigest()
    textures = SOURCE / 'textures/resourcepack/assets/gtsr/textures/blocks'
    out = ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical'
    out.mkdir(parents=True, exist_ok=True)
    for source in sorted(textures.iterdir()):
        if source.suffix not in ('.png', '.mcmeta'):
            continue
        target = out / source.name
        shutil.copyfile(source, target)
        manifest['files'][str(target.relative_to(ROOT)).replace('\\', '/')] = hashlib.sha256(source.read_bytes()).hexdigest()
    (DEST / 'source-sha256.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Exported 22 models, {len(set(x["id"] for x in materials.values()))} original materials, {len(manifest["files"])} verified files')


if __name__ == '__main__':
    export()
