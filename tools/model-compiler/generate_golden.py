"""Independent source-OBJ oracle. No production Java parser or compiled mesh is consulted."""
from pathlib import Path
from collections import OrderedDict
import hashlib
import struct
import json

REPO = Path(__file__).resolve().parents[2]
ROOT = REPO / 'src/main/resources/assets/gtsr/liminal'
OUT = REPO.parent / '.artifacts/gtsr-rigmesh'
rows = []
for obj in sorted(ROOT.glob('*/models/*.obj')):
    raw = obj.read_bytes()
    vertices, uv = [], []
    faces = OrderedDict()
    group = 'root'
    count = 0
    for line in raw.decode('utf-8').splitlines():
        words = line.partition('#')[0].split()
        if not words:
            continue
        kind, values = words[0], words[1:]
        if kind == 'v':
            vertices.append(tuple(map(float, values[:3])))
        elif kind == 'vt':
            u, v = map(float, values[:2])
            uv.append((u, 1.0 - v))
        elif kind == 'g':
            group = values[0]
        elif kind == 'f':
            polygon = []
            for pair in values:
                parts = pair.split('/')
                vi, ti = int(parts[0]), int(parts[1])
                assert vi and ti
                vi = vi - 1 if vi > 0 else len(vertices) + vi
                ti = ti - 1 if ti > 0 else len(uv) + ti
                polygon.append(vertices[vi] + uv[ti])
            target = faces.setdefault(group, [])
            # A polygon's literal first vertex defines its ordered triangle fan.
            for edge in zip(polygon[1:-1], polygon[2:]):
                target.extend((polygon[0], edge[0], edge[1]))
            count += 1
    digest = hashlib.sha256()
    triangles = 0
    for name, corners in faces.items():
        name_bytes = name.encode('utf-8')
        digest.update(struct.pack('>I', len(name_bytes)) + name_bytes)
        digest.update(struct.pack('>I', len(corners)))
        for corner in corners:
            digest.update(struct.pack('>5d', *corner))
        triangles += len(corners) // 3
    rows.append([obj.parent.parent.name, hashlib.sha256(raw).hexdigest(), digest.hexdigest(),
                 triangles, len(faces), len(vertices), len(uv), count,
                 len({struct.pack('>2d', *pair) for pair in uv})])
assert len(rows) == 40
OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'golden.tsv').write_text('\n'.join('\t'.join(map(str, row)) for row in rows) + '\n', encoding='utf-8')
tracked = REPO / 'src/test/resources/encounter/rigmesh-golden.tsv'
tracked.parent.mkdir(parents=True, exist_ok=True)
tracked.write_bytes((OUT / 'golden.tsv').read_bytes())
(OUT / 'golden-origin.json').write_text(json.dumps({
    'status': 'GOLDEN_FROM_ORIGINAL_OBJ_ONLY', 'models': len(rows),
    'root': str(ROOT), 'generator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'columns': ['code', 'original_obj_sha256', 'ordered_resolved_xyz_uv_sha256', 'triangles',
                'groups', 'positions', 'texture_coordinates', 'original_polygons', 'unique_uv_raw_bit_pairs'],
    'java_parser_or_compiled_input_used': False,
    'digest_contract': 'UTF8 group length int32 BE/name; corner count int32 BE; ordered xyzUV raw IEEE754 binary64 BE',
    'rows': rows}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('PASS: 40 original-OBJ independent goldens')
