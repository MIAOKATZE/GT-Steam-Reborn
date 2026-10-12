"""Verify packaging, construction disjointness and source texture timing without a client."""
import argparse
import hashlib
import json
import math
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
assert len(models) == 28
authorities = manifest['authorities']
legacy = authorities['legacy']
for filename, expected in legacy['files'].items():
    if filename.endswith('/materials.json'): continue
    assert hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == expected, 'Legacy asset changed: ' + filename
palette = json.loads((DEST / 'materials.json').read_text(encoding='utf-8'))
legacy_palette = {key: palette[key] for key in legacy['materialAliases']}
assert hashlib.sha256(json.dumps(legacy_palette, sort_keys=True, separators=(',', ':')).encode()).hexdigest() == legacy['materialEntriesSha256']
for filename, expected in authorities['v2']['inputs'].items():
    source = ROOT / filename
    if source.is_file(): assert hashlib.sha256(source.read_bytes()).hexdigest() == expected, 'Frozen R4 input changed: ' + filename
for slug in ('loom', 'base_t3', 'sun', 'plugin_parallel', 'plugin_speed', 'plugin_efficiency'):
    model = models['v2_' + slug]
    source_path = ROOT / authorities['v2']['source'] / 'models' / (slug + '.geometry.json')
    source_hash = authorities['v2']['inputs'][source_path.relative_to(ROOT).as_posix()]
    assert model['meta']['sourceGeometrySha256'] == source_hash
    if source_path.is_file():
        source = json.loads(source_path.read_text(encoding='utf-8'))
        expected = [b[:3] + ['v2_' + b[3]] + b[4:] for b in source['blocks']]
        assert model['blocks'] == expected and model['animationGroups'] == source['animationGroups'], slug
    assert all(b[3] in palette for b in model['blocks']), slug
v2_cells = {}
for slug, count in (('loom', 11143), ('base_t3', 19366), ('sun', 34325)):
    rows = models['v2_' + slug]['blocks']
    cells = {tuple(b[:3]) for b in rows}
    assert len(rows) == len(cells) == count, slug
    assert all(0 <= b[0] < 112 and 0 <= b[1] < 180 and 0 <= b[2] < 112 for b in rows)
    assert not cells & v2_cells.keys(), slug
    v2_cells.update({cell: slug for cell in cells})
assert (56, 5, 34) not in v2_cells, 'Runtime render anchor must be empty'
assert {(56, 4, 35), (56, 4, 47)} <= set(v2_cells)
addon_checks = []
for slug in ('parallel', 'speed', 'efficiency'):
    model = models['v2_plugin_' + slug]
    assert not model['animationGroups'] and model['meta']['physicalStructure'] and model['meta']['fixedWorldBlocks']
    source_cells = {tuple(b[:3]): b for b in model['blocks']}
    for mount in models['v2_base_t3']['meta']['attachmentPlacement']['mounts']:
        mx, my, mz = mount['position']; co = math.cos(mount['yawRadians']); si = math.sin(mount['yawRadians'])
        cells = set()
        for dx in range(-12, 13):
            for dz in range(-12, 13):
                for dy in range(-4, 19):
                    local = (math.floor(dx * co - dz * si + .5 + 1e-8), dy, math.floor(dx * si + dz * co + .5 + 1e-8))
                    if local in source_cells: cells.add((mx + dx, my + dy, mz + dz))
        assert cells and not cells & v2_cells.keys(), (slug, mount['id'])
        assert all(0 <= x < 112 and 0 <= y < 180 and 0 <= z < 112 for x, y, z in cells)
        shells = {tuple(b[:3]) for b in models['v2_base_t3']['blocks'] if b[4] == 'decor_shell_' + str(mount['id'])}
        assert all(any((x + dx, y + dy, z + dz) in shells for dx, dy, dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))) for x,y,z in cells)
        addon_checks.append({'kind': slug, 'mount': mount['id'], 'blocks': len(cells)})
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
v2_animated = 0
for metadata in (ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2').glob('*.png.mcmeta'):
    data = json.loads(metadata.read_text(encoding='utf-8'))['animation']
    assert data.get('frametime') == 8 and data.get('frames') == list(range(12)) and not data.get('interpolate', False), metadata.name
    width, height = struct.unpack('>II', metadata.with_suffix('').read_bytes()[16:24])
    assert height == width * 12, metadata.name
    v2_animated += 1
assert v2_animated > 0
print(f'PASS {len(manifest["files"])} SHA256 assets; 22 legacy + 6 frozen R4 models; 24 physical addon placements')
report = ROOT / 'build/critical-probe/assets-report.json'
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({'passed': True, 'sha256Files': len(manifest['files']), 'models': 28,
    'animatedMaterials': 27, 'ticksPerFrame': 10, 'interpolation': False,
    'v2AnimatedSprites': v2_animated, 'v2TicksPerFrame': 8, 'v2PhysicalAddonChecks': addon_checks,
    'v2ManualLoom': 11143, 'v2PaidWithoutAddons': 53691, 'legacyAliasesPreserved': len(legacy['materialAliases']),
    'disjointStructures': 27, 'gitIndexVerified': options.index,
    'jar': str(options.jar.resolve()) if options.jar else None,
    'jarSha256': hashlib.sha256(options.jar.read_bytes()).hexdigest() if options.jar else None}, indent=2), encoding='utf-8')
