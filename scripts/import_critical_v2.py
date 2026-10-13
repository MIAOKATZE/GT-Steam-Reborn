"""Import the frozen V2 geometry without changing the eight legacy machine assets."""
import hashlib
import json
import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = next(p for p in ROOT.glob('plan/*/*v2/models') if (p / 'loom.geometry.json').is_file())
TARGET = ROOT / 'src/main/resources/assets/gtsr/critical/models'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--slugs', nargs='+', default=['loom', 'base_t3', 'solar'],
                    choices=['loom', 'base_t3', 'sun', 'solar', 'plugin_parallel', 'plugin_speed', 'plugin_efficiency'])
parser.add_argument('--verification-dir', type=Path,
                    default=ROOT / 'build/critical-v2-runtime-v12126/geometry')
args = parser.parse_args()
receipt = {}
# Freeze the persisted-journal layout from the last released runtime, never
# from the amended working tree. Retain original bytes and block order.
for slug in ('loom', 'base_t3'):
    snapshot = TARGET / f'v2_legacy_{slug}.geometry.json'
    if snapshot.exists():
        continue
    original = subprocess.check_output([
        'git', 'show', f'v1.21.24:src/main/resources/assets/gtsr/critical/models/v2_{slug}.geometry.json'
    ], cwd=ROOT)
    if snapshot.exists() and snapshot.read_bytes() != original:
        raise ValueError(f'{snapshot.name}: compatibility snapshot differs from v1.21.24')
    if not snapshot.exists():
        snapshot.write_bytes(original)
# V25 jobs keep their original membership, byte order and fingerprint. These
# snapshots are copied before the V26 amendment; never synthesize them from it.
for slug in ('loom', 'base_t3'):
    snapshot = TARGET / f'v2_v25_{slug}.geometry.json'
    if not snapshot.is_file():
        raise ValueError(f'{snapshot.name}: freeze the released V25 bytes before importing V26')
for slug in args.slugs:
    path = SOURCE / (slug + '.geometry.json')
    model = json.loads(path.read_text(encoding='utf-8'))
    for row in model['blocks']:
        row[3] = 'v2_' + row[3]
    # Preserve runtime and animation metadata, omit historical cell-by-cell design audits.
    for key in list(model['meta']):
        if 'Audit' in key or key in ('refinementChanges', 'r3Geometry', 'r4Geometry', 'r3EndcapTopRim'):
            del model['meta'][key]
    model['meta']['runtimeGeometryVersion'] = 2
    model['meta']['runtimeRegistered'] = True
    model['meta']['sourceGeometrySha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    target = TARGET / ('v2_' + slug + '.geometry.json')
    target.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    receipt[slug] = {'source': model['meta']['sourceGeometrySha256'], 'imported': hashlib.sha256(target.read_bytes()).hexdigest(), 'blocks': len(model['blocks'])}
report = args.verification_dir / 'geometry-import.json'
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(receipt))
