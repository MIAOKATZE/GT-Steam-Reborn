"""Bind imported runtime assets to the legacy snapshot and frozen R4 design sources."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'src/main/resources/assets/gtsr/critical'
path = DEST / 'source-sha256.json'
manifest = json.loads(path.read_text(encoding='utf-8'))
legacy = manifest.get('authorities', {}).get('legacy')
if legacy is None:
    original = json.loads(subprocess.check_output(['git', 'show', 'HEAD:src/main/resources/assets/gtsr/critical/materials.json'], cwd=ROOT))
    legacy = {'source': manifest['source'], 'files': dict(manifest['files']),
              'materialAliases': sorted(original),
              'materialEntriesSha256': hashlib.sha256(json.dumps(original, sort_keys=True, separators=(',', ':')).encode()).hexdigest()}
source = next(p for p in ROOT.glob('plan/*/*v2') if (p / 'models/loom.geometry.json').is_file())
sources = [*sorted((source / 'models').glob('*.geometry.json')), *sorted((source / 'models').glob('*.structure.json')),
           source / 'models/manifest.json', source / 'textures/palette.json']
inputs = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
manifest['authorities'] = {'legacy': legacy, 'v2': {'source': source.relative_to(ROOT).as_posix(),
    'designTag': 'design-20261013-critical-machines-v2-r4', 'revision': 'R4', 'inputs': inputs,
    'materialAliasTransform': 'v2_ prefix; legacy aliases retain their original definitions'}}
resources = [*sorted((DEST / 'models').glob('v2_*.geometry.json')), DEST / 'materials.json', DEST / 'v2-material-language.json',
             *sorted((ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2').rglob('*'))]
for p in resources:
    if p.is_file(): manifest['files'][p.relative_to(ROOT).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
manifest['schema'] = 'gtsr.runtime-asset-bindings/2'
path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'files': len(manifest['files']), 'v2Inputs': len(inputs), 'legacyAliases': len(legacy['materialAliases'])}))
