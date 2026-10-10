"""Verify packaging, construction disjointness and source texture timing without a client."""
import argparse
import hashlib
import json
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'src/main/resources/assets/gtsr/critical'
parser = argparse.ArgumentParser()
parser.add_argument('--index', action='store_true', help='Also verify staged Git blobs')
parser.add_argument('--jar', type=Path, help='Also verify resources in the delivered jar')
options = parser.parse_args()
manifest = json.loads((DEST / 'source-sha256.json').read_text(encoding='utf-8'))
for filename, expected in manifest['files'].items():
    assert hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == expected, filename
    if options.index:
        data = subprocess.check_output(['git', 'show', ':' + filename], cwd=ROOT)
        assert hashlib.sha256(data).hexdigest() == expected, 'Git normalization changed ' + filename
if options.jar:
    with zipfile.ZipFile(options.jar) as archive:
        for filename, expected in manifest['files'].items():
            entry = filename.removeprefix('src/main/resources/')
            assert hashlib.sha256(archive.read(entry)).hexdigest() == expected, 'Jar changed ' + entry
models = {p.name.removesuffix('.geometry.json'): json.loads(p.read_text(encoding='utf-8'))
          for p in (DEST / 'models').glob('*.geometry.json')}
assert len(models) == 22
for tier in range(1, 4):
    base = {tuple(v[:3]) for v in models[f'base_t{tier}']['blocks']}
    loom = {tuple(v[:3]) for v in models['loom']['blocks']}
    assert not base & loom, f'T{tier} overlaps loom'
    assert (40, 0, 32) not in base | loom
    for machine in ('solar', 'turbine', 'processing', 'entangler', 'sun', 'dimension', 'battery', 'assembly', 'accelerator'):
        upper = {tuple(v[:3]) for v in models[machine]['blocks']}
        assert not upper & (base | loom), (tier, machine)
animated = 0
for metadata in (ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical').glob('*.png.mcmeta'):
    data = json.loads(metadata.read_text(encoding='utf-8'))['animation']
    assert data.get('frametime') == 10 and not data.get('interpolate', False), metadata.name
    image = metadata.with_suffix('')
    width, height = struct.unpack('>II', image.read_bytes()[16:24])
    assert (width, height) == (32, 256), image.name
    animated += not metadata.name.endswith('_emission.png.mcmeta')
assert animated == 27, animated
print(f'PASS {len(manifest["files"])} SHA256 assets; 22 models; 27 eight-frame textures; 27 disjoint tier/machine structures')
report = ROOT / 'build/critical-probe/assets-report.json'
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({'passed': True, 'sha256Files': len(manifest['files']), 'models': 22,
    'animatedMaterials': 27, 'ticksPerFrame': 10, 'interpolation': False,
    'disjointStructures': 27, 'gitIndexVerified': options.index,
    'jar': str(options.jar.resolve()) if options.jar else None,
    'jarSha256': hashlib.sha256(options.jar.read_bytes()).hexdigest() if options.jar else None}, indent=2), encoding='utf-8')
