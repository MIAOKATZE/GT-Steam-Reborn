"""Freeze released V27 bytes and transfer only its three moving collars to the permanent loom."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / 'src/main/resources/assets/gtsr/critical/models'
REF = '16c15ed2'
GROUPS = {'loom_hoop_low', 'loom_hoop_mid', 'loom_hoop_high'}


def main():
    originals = {}
    for slug in ('loom', 'base_t3'):
        path = f'src/main/resources/assets/gtsr/critical/models/v2_{slug}.geometry.json'
        raw = subprocess.check_output(['git', 'show', f'{REF}:{path}'], cwd=ROOT)
        snapshot = MODELS / f'v2_v27_{slug}.geometry.json'
        if snapshot.exists():
            assert snapshot.read_bytes() == raw, f'{snapshot.name}: released bytes changed'
        else:
            snapshot.write_bytes(raw)
        originals[slug] = json.loads(raw)
    loom, base = originals['loom'], originals['base_t3']
    collars = [row for row in base['blocks'] if row[4] in GROUPS]
    assert len(collars) == 310
    loom_positions = {tuple(row[:3]) for row in loom['blocks']}
    assert not any(tuple(row[:3]) in loom_positions for row in collars)
    loom['blocks'].extend(collars)
    for name in sorted(GROUPS):
        loom['animationGroups'][name] = base['animationGroups'].pop(name)
    base['blocks'] = [row for row in base['blocks'] if row[4] not in GROUPS]
    for model in (loom, base):
        model['meta']['runtimeLayoutRevision'] = 28
        model['meta']['worldOriginChunkAligned'] = False
        model['meta']['runtimeMembershipSource'] = {'sourceRef': REF, 'tool': 'tools/critical/amend_loom_v28.py',
                                                  'transferGroups': sorted(GROUPS)}
    loom['meta']['hoopMotion']['physicalOwner'] = 'loom'
    loom['meta']['startupPrerequisite']['consumption'] += ', and the three original moving collars'
    base['meta']['floatingLoomGroups'] = [g for g in base['meta']['floatingLoomGroups'] if g not in GROUPS]
    for slug, model in (('loom', loom), ('base_t3', base)):
        (MODELS / f'v2_{slug}.geometry.json').write_text(
            json.dumps(model, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    binding_path = MODELS.parent / 'source-sha256.json'
    binding = json.loads(binding_path.read_text(encoding='utf-8'))
    for slug in ('v2_loom', 'v2_base_t3', 'v2_v27_loom', 'v2_v27_base_t3'):
        path = MODELS / f'{slug}.geometry.json'
        binding['files'][path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    binding.setdefault('compatibilitySnapshots', {}).update({
        f'v2_v27_{slug}': {'sourceRef': REF, 'sha256': hashlib.sha256((MODELS / f'v2_v27_{slug}.geometry.json').read_bytes()).hexdigest(),
                         'usage': 'persisted revision 26 and 27 loom-owned jobs; exact released bytes and order'}
        for slug in ('loom', 'base_t3')})
    binding['authorities']['v2']['sharedStructureRevision'] = {
        'revision': 'v1.21.28', 'models': ['loom', 'base_t3'],
        'source': 'tools/critical/amend_loom_v28.py against released 16c15ed2 V27 runtime bytes'}
    binding_path.write_text(json.dumps(binding, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'manual': len(loom['blocks']), 'paidBase': len(base['blocks']), 'transferredCollars': len(collars)}))


if __name__ == '__main__':
    main()
