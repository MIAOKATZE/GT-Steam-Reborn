"""Directly verify authored solids, negative spaces, slice bounds and asset digests."""
import gzip
import hashlib
import json
from collections import Counter
from remaster_convert import OUT, SOURCE, snapshot


def read(path, sha=None):
    raw = gzip.decompress(path.read_bytes())
    if sha:
        assert hashlib.sha256(raw).hexdigest() == sha, path
    return json.loads(raw)


def main():
    catalog = json.loads((OUT / 'catalog.json').read_text(encoding='utf8'))
    assert len(catalog['structures']) == 90
    assert len(catalog['prefabs']) == 268
    assert len(catalog['engineeringChapters']) == 8
    sums = Counter()
    for descriptor in catalog['prefabs']:
        d = read(OUT / descriptor['file'], descriptor['sha256'])
        assert d['schemaVersion'] == 7
        assert d['extent'][1] <= 256
        assert d['palette'][0] == 'minecraft:air#0'
        actual = {}
        for s in d['slices']:
            for x, y, z, n, k in read(OUT / s['file'], s['sha256']):
                assert x // 16 == s['x'] == (x + n - 1) // 16 and z // 16 == s['z']
                assert n > 0 and 0 <= k < len(d['palette'])
                for xx in range(x, x + n):
                    assert (xx, y, z) not in actual
                    actual[xx, y, z] = k
        original = snapshot(SOURCE / 'preview/prefabs7' / f"{d['id']}-v{d['variant']}.js")
        count = 0
        for x, y, z, n, k in original['voxel']['runs']:
            for xx in range(x, x + n):
                assert d['palette'][actual[xx, y, z]] == original['voxel']['palette'][k]
                count += 1
        assert count == d['solidCount'] == original['voxel']['count']
        assert sum(k == 0 for k in actual.values()) == d['airCount']
        for k, value in original['metadata'].items():
            assert d['metadata'][k] == value, (d['id'], k)
        assert d['metadata']['productionPuzzle']['fields']
        if d['id'] == 'fiction_expansion_project':
            assert len(d['metadata']['productionEngineering']) == 8
            assert len(d['metadata']['testimonyPedestals']) == 38
        sums['solids'] += count
        sums['air'] += d['airCount']
        sums['slices'] += len(d['slices'])
        for key in ('spawns', 'rooms', 'shapeMechanisms', 'lootPlan7', 'spawnerPlan', 'puzzleObjects7'):
            sums[key] += len(d['metadata'].get(key, []))
    print(json.dumps({'passed': True, 'prefabs': 268, **sums}))


if __name__ == '__main__':
    main()
