"""Deterministically compile authored R7 snapshots into chunk-addressable assets."""
import gzip
import hashlib
import json
import math
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = next(p for p in (ROOT / 'plan').iterdir() if (p / 'preview/prefabs7').is_dir())
OUT = ROOT / 'src/main/resources/assets/gtsr/remaster'
PUZZLES = {}
ENGINEERING = []


def write_json(path, value, compressed=False):
    raw = json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(raw, mtime=0) if compressed else raw)
    return hashlib.sha256(raw).hexdigest()


def snapshot(path):
    text = path.read_text(encoding='utf8')
    return json.loads(text.split(']=', 1)[1].rstrip(';\r\n'))


def compile_one(path):
    d = snapshot(path)
    m = d['metadata']
    m['productionPuzzle'] = PUZZLES[m['id']]
    if m['id'] == 'fiction_expansion_project':
        m['productionEngineering'] = ENGINEERING
    palette = ['minecraft:air#0'] + d['voxel']['palette']
    cells = {}
    for x, y, z, n, k in d['voxel']['runs']:
        for xx in range(x, x + n):
            cells[xx, y, z] = k + 1
    solid_count = len(cells)

    def carve(x, y, z):
        cells.setdefault((x, y, z), 0)

    # Preserve every authored solid; record only the formerly implicit negative space.
    for r in m.get('rooms', []):
        for y in range(r['y'] + 2, r['y'] + r['h'] + 1):
            for z in range(r['z'] + 1, r['z'] + r['d'] - 1):
                for x in range(r['x'] + 1, r['x'] + r['w'] - 1):
                    carve(x, y, z)
    routes = [(r['points'], r.get('width', 5), 4) for r in m.get('stairRoutes', [])]
    routes += [(r, 3, 3) for r in m.get('routes', [])]
    for entry in [m.get('entranceLogic7'), m.get('terrain', {})]:
        if entry and (entry.get('points') or entry.get('entrance')):
            routes.append((entry.get('points') or entry['entrance'], 5, 4))
    for points, width, height in routes:
        for a, b in zip(points, points[1:]):
            n = max(abs(b[0] - a[0]), abs(b[2] - a[2]), 1)
            along_x = abs(b[0] - a[0]) >= abs(b[2] - a[2])
            for i in range(n + 1):
                q = [math.floor(a[j] + (b[j] - a[j]) * i / n + .5) for j in range(3)]
                for side in range(-(width // 2), width // 2 + 1):
                    for dy in range(1, height + 1):
                        carve(q[0] + (0 if along_x else side), q[1] + dy,
                              q[2] + (side if along_x else 0))
    # Exact actor birth clearance is also explicit air, including airborne guards.
    actors = [(s, [s['x'], s['y'], s['z']], s.get('clearance', {})) for s in m.get('spawns', [])]
    actors += [(s, s['spawnZone']['center'], s['spawnZone'].get('clearance', {}))
               for s in m.get('spawnerPlan', [])]
    for actor, at, clearance in actors:
        for x in range(at[0] - int(clearance.get('width', 1)) // 2,
                       at[0] + int(clearance.get('width', 1)) // 2 + 1):
            for z in range(at[2] - int(clearance.get('depth', 1)) // 2,
                           at[2] + int(clearance.get('depth', 1)) // 2 + 1):
                for y in range(at[1], at[1] + int(clearance.get('height', 3))):
                    carve(x, y, z)
    for damage in m.get('damageManifest', []):
        for p in damage.get('removedVoxels', []):
            carve(*p[:3])
    coords = list(cells)
    # Include actionable metadata coordinates in the advertised placement envelope.
    def coordinates(value):
        if isinstance(value, dict):
            if all(isinstance(value.get(k), (int, float)) for k in ('x', 'y', 'z')):
                coords.append(tuple(math.floor(value[k]) for k in ('x', 'y', 'z')))
            for k, v in value.items():
                if k in ('at', 'control', 'anchor', 'center', 'min', 'max', 'home') and isinstance(v, list) and len(v) == 3:
                    if all(isinstance(i, (int, float)) for i in v):
                        coords.append(tuple(math.floor(i) for i in v))
                elif k in ('points', 'routes', 'entrance') and isinstance(v, list):
                    for p in v:
                        if isinstance(p, list) and len(p) == 3 and all(isinstance(i, (int, float)) for i in p):
                            coords.append(tuple(math.floor(i) for i in p))
                coordinates(v)
        elif isinstance(value, list):
            for v in value:
                coordinates(v)
    coordinates(m)
    lo = [min(p[i] for p in coords) for i in range(3)]
    hi = [max(p[i] for p in coords) for i in range(3)]
    if hi[1] - lo[1] + 1 > 256:
        raise ValueError(f'{path.name}: vertical extent exceeds world height')
    grouped = defaultdict(list)
    for (x, y, z), k in sorted(cells.items(), key=lambda p: (p[0][1], p[0][2], p[0][0])):
        rows = grouped[x // 16, z // 16]
        if rows and rows[-1][1] == y and rows[-1][2] == z and rows[-1][0] + rows[-1][3] == x and rows[-1][4] == k:
            rows[-1][3] += 1
        else:
            rows.append([x, y, z, 1, k])
    key = f"{m['id']}-v{m['variant']}"
    slices = []
    for (cx, cz), runs in sorted(grouped.items()):
        name = f'prefabs/{key}/{cx}_{cz}.json.gz'
        digest = write_json(OUT / name, runs, True)
        slices.append({'x': cx, 'z': cz, 'file': name, 'sha256': digest})
    record = {'schemaVersion': 7, 'id': m['id'], 'variant': m['variant'], 'min': lo, 'max': hi,
              'extent': [hi[i] - lo[i] + 1 for i in range(3)], 'nominal': m['nominal'],
              'yMin': lo[1], 'yMax': hi[1], 'palette': palette, 'metadata': m, 'slices': slices,
              'solidCount': solid_count, 'airCount': len(cells) - solid_count,
              'sourceSha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    manifest = f'prefabs/{key}.json.gz'
    digest = write_json(OUT / manifest, record, True)
    return {k: record[k] for k in ('id', 'variant', 'min', 'max', 'extent', 'nominal', 'yMin', 'yMax',
                                  'solidCount', 'airCount', 'sourceSha256')} | {'file': manifest, 'sha256': digest}


def main():
    # Read the existing declaration scripts in an isolated JS context, never execute authoring tools.
    js = r"""const fs=require('fs'),vm=require('vm'),p=require('path'),s={window:{}};vm.createContext(s);
for(const n of ['catalog','catalog5','textures','geometry','blocks4','story4','blocks5','blocks6','blocks7','puzzles4'])
vm.runInContext(fs.readFileSync(p.join(process.argv[1],'preview',n+'.js'),'utf8'),s);
let specs={};for(const m of s.window.REMASTER_CATALOG.structures)specs[m.id]=s.window.RemasterPuzzles4.spec(m);
const engineering=fs.readFileSync(p.join(process.argv[1],'preview','experience5.js'),'utf8');
vm.runInContext('window.STATIC_CHAPTERS='+engineering.match(/const chapters=(\[[\s\S]*?\n\]);/)[1],s);
let t={};for(const [k,v]of Object.entries(s.window.REMASTER_TEXTURES))if(k.includes(':draft')){
const q={...v};delete q.texture;delete q.faces;t[k]={...q,faceNames:Object.keys(v.faces||{})};}
process.stdout.write(JSON.stringify({structures:s.window.REMASTER_CATALOG.structures,blocks:s.window.REMASTER_BLOCKS,materials:t,
specs,chapters:s.window.STATIC_CHAPTERS,story:s.window.REMASTER_STORY,sets:s.window.RemasterPuzzles4.sets}));"""
    declarations = json.loads(subprocess.check_output(['node', '-e', js, str(SOURCE)], encoding='utf8'))
    global PUZZLES, ENGINEERING
    PUZZLES = declarations['specs']
    ENGINEERING = declarations['chapters']
    write_json(OUT / 'blocks.json', {'schemaVersion': 7, 'blocks': declarations['blocks'],
                                   'materials': declarations['materials']})
    records = []
    for path in sorted((SOURCE / 'preview/prefabs7').glob('*.js')):
        records.append(compile_one(path))
        if len(records) % 30 == 0:
            print(f'compiled {len(records)}', flush=True)
    mapping = [{k: m[k] for k in ('id', 'category', 'source')} for m in declarations['structures']]
    write_json(OUT / 'catalog.json', {'schemaVersion': 7, 'structures': mapping, 'prefabs': records,
                                    'puzzleSets': declarations['sets'], 'engineeringChapters': ENGINEERING,
                                    'story': declarations['story']})
    print(json.dumps({'prefabs': len(records), 'solidCount': sum(r['solidCount'] for r in records),
                      'airCount': sum(r['airCount'] for r in records),
                      'maxVerticalExtent': max(r['extent'][1] for r in records)}))


if __name__ == '__main__':
    main()
