"""Import the frozen V2 geometry without changing the eight legacy machine assets."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = next(ROOT.glob('plan/*/*v2/models'))
TARGET = ROOT / 'src/main/resources/assets/gtsr/critical/models'
receipt = {}
for slug in ('loom', 'base_t3', 'sun', 'plugin_parallel', 'plugin_speed', 'plugin_efficiency'):
    path = SOURCE / (slug + '.geometry.json')
    model = json.loads(path.read_text(encoding='utf-8'))
    for row in model['blocks']:
        row[3] = 'v2_' + row[3]
    # Preserve runtime and animation metadata, omit historical cell-by-cell design audits.
    for key in list(model['meta']):
        if 'Audit' in key or key in ('refinementChanges', 'r3Geometry', 'r4Geometry', 'r3EndcapTopRim'):
            del model['meta'][key]
    model['meta']['runtimeGeometryVersion'] = 2
    model['meta']['sourceGeometrySha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    target = TARGET / ('v2_' + slug + '.geometry.json')
    target.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    receipt[slug] = {'source': model['meta']['sourceGeometrySha256'], 'imported': hashlib.sha256(target.read_bytes()).hexdigest(), 'blocks': len(model['blocks'])}
print(json.dumps(receipt))
