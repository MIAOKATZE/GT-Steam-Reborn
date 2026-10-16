"""Bind imported runtime assets to the legacy snapshot and frozen R4 design sources."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / 'src/main/resources/assets/gtsr/critical'
path = DEST / 'source-sha256.json'
manifest = json.loads(path.read_text(encoding='utf-8'))
for slug in ('loom', 'base_t3'):
    original = subprocess.check_output([
        'git', 'show', f'v1.21.24:src/main/resources/assets/gtsr/critical/models/v2_{slug}.geometry.json'
    ], cwd=ROOT)
    if (DEST / 'models' / f'v2_legacy_{slug}.geometry.json').read_bytes() != original:
        raise ValueError(f'{slug}: compatibility snapshot differs from released v1.21.24 bytes')
legacy = manifest.get('authorities', {}).get('legacy')
if legacy is None:
    original = json.loads(subprocess.check_output(['git', 'show', 'HEAD:src/main/resources/assets/gtsr/critical/materials.json'], cwd=ROOT))
    legacy = {'source': manifest['source'], 'files': dict(manifest['files']),
              'materialAliases': sorted(original),
              'materialEntriesSha256': hashlib.sha256(json.dumps(original, sort_keys=True, separators=(',', ':')).encode()).hexdigest()}
source = next(p for p in ROOT.glob('plan/*/*v2') if (p / 'models/loom.geometry.json').is_file())
sources = [*sorted((source / 'models').glob('*.geometry.json')), *sorted((source / 'models').glob('*.structure.json')),
           source / 'models/manifest.json', source / 'models/solar.manifest.json',
           source / 'scripts/import_runtime_materials.py',
           *[source / 'textures' / name for name in ('palette.json', 'ctm-rules.json', 'catalog.json', 'atlas.png', 'emission_atlas.png')]]
inputs = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
manifest['authorities'] = {'legacy': legacy, 'v2': {'source': source.relative_to(ROOT).as_posix(),
    'designTag': 'design-20261013-critical-machines-v2-r4', 'revision': 'R4 + v1.21.25 shared runtime membership', 'inputs': inputs,
    'designTagScope': 'approved Solar R4 geometry, motion, FX and original material artwork; excludes amended loom/base membership',
    'sharedStructureRevision': {'revision': 'v1.21.25', 'models': ['loom', 'base_t3'],
        'source': 'approved runtime membership amendment; current input hashes identify revised shared models'},
    'runtimeAmendment': 'v1.21.25 Solar: loom journal and same-row ports; main controller and supported service-shell row paid by base',
    'directionalFaceRegistration': 'gravity ny bottom pair reuses approved mask_00 albedo, live twelve-frame emission and frozen emission; CTM rules retained',
    'materialAliasTransform': 'v2_ prefix; service_hatch uses gt_hatch shell; solar_flux_collector follows approved R4 silver mapping; legacy aliases retain original definitions'}}
resources = [*sorted((DEST / 'models').glob('v2_*.geometry.json')), DEST / 'materials.json', DEST / 'v2-material-language.json',
             *sorted((ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2').rglob('*'))]
for p in resources:
    if p.is_file(): manifest['files'][p.relative_to(ROOT).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
manifest['schema'] = 'gtsr.runtime-asset-bindings/2'
manifest['compatibilitySnapshots'] = {slug: {
    'source': 'v1.21.24 runtime V2 assets, frozen before Solar membership amendment',
    'sourceRef': 'v1.21.24',
    'sha256': hashlib.sha256((DEST / 'models' / (slug + '.geometry.json')).read_bytes()).hexdigest(),
    'usage': 'existing main-owned persisted journals only; excluded from new loom-owned construction'}
    for slug in ('v2_legacy_loom', 'v2_legacy_base_t3')}
path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'files': len(manifest['files']), 'v2Inputs': len(inputs), 'legacyAliases': len(legacy['materialAliases'])}))
