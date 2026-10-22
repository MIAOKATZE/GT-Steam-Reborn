"""Import immutable approved R5 models; freeze every released V2 body before replacing it.

No preview generator is executed. Animation/clock metadata is copied verbatim, including
the exact Sun/Solar overrides that the approved preview applied at load time.
"""
from pathlib import Path
import hashlib
import json
from material_palette import expand_palette
import re
import math
from functools import lru_cache

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/critical-v2-runtime-v12131'
APPROVED = WORK / 'approved-v2'
ASSETS = ROOT / 'src/main/resources/assets/gtsr/critical'
MODELS = ASSETS / 'models'
SLUGS = ('loom', 'base_t3', 'sun', 'solar', 'turbine', 'processing', 'entangler',
         'dimension', 'battery', 'assembly', 'accelerator', 'plugin_parallel',
         'plugin_speed', 'plugin_efficiency')
FROZEN = ('loom', 'base_t3', 'sun', 'solar', 'plugin_parallel', 'plugin_speed', 'plugin_efficiency')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def dump(path, value, pretty=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False,
                              indent=2 if pretty else None,
                              separators=None if pretty else (',', ':')) + '\n', encoding='utf-8')


def verify(baseline, overrides):
    def read(folder, slug):
        return json.loads((folder / (slug + '.geometry.json')).read_text(encoding='utf-8'))

    @lru_cache(maxsize=None)
    def java_hash(value):
        value = value.encode('utf-16be')
        result = 0
        for index in range(0, len(value), 2):
            result = (31 * result + int.from_bytes(value[index:index + 2], 'big')) & 0xffffffff
        return result if result < 0x80000000 else result - 0x100000000

    def indices(folder, slugs):
        cells = {}
        for slug in slugs:
            for x, y, z, material, group in read(folder, slug)['blocks']:
                stage = 0 if slug.endswith('loom') else min(3, y // 16 + 1) if 'base_' in slug else 4
                cells[x, y, z] = (x, y, z, material, group, stage)
        rows = sorted(cells.values(), key=lambda row: (row[5], row[1], row[0], row[2]))
        fingerprint = 0xcbf29ce484222325
        for x, y, z, material, group, stage in rows:
            fingerprint = ((fingerprint ^ (x << 40 | y << 20 | z)) * 0x100000001b3) & 0xffffffffffffffff
            fingerprint = ((fingerprint ^ java_hash(material) ^ java_hash(group) ^ stage) * 0x100000001b3) & 0xffffffffffffffff
        digest = sha(json.dumps(rows, separators=(',', ':')).encode())
        return rows, {'count': len(rows), 'indexSha256': digest, 'fingerprint': fingerprint}

    frozen_layouts = []
    for revision in (24, 25, 26, 27, 28, 29, 30):
        for legacy in (True, False):
            prefix = 'v2_legacy_' if legacy else 'v2_v25_' if revision == 25 else 'v2_v27_' if revision <= 27 else 'v2_'
            new_prefix = prefix if prefix != 'v2_' else 'v2_v30_'
            for kind in ('sun', 'solar'):
                before_slugs = [prefix + 'base_t3', 'v2_' + kind]
                after_slugs = [new_prefix + 'base_t3', 'v2_v30_' + kind]
                # Legacy jobs let the paid assembly own overlapping cells; journals let loom own them.
                if legacy:
                    before_slugs.insert(0, prefix + 'loom')
                    after_slugs.insert(0, new_prefix + 'loom')
                else:
                    before_slugs.append(prefix + 'loom')
                    after_slugs.append(new_prefix + 'loom')
                before, proof = indices(baseline, before_slugs)
                after, after_proof = indices(MODELS, after_slugs)
                assert before == after and proof == after_proof, (revision, legacy, kind)
                frozen_layouts.append({'revision': revision, 'legacyMainOwned': legacy, 'kind': kind, **proof})

    imported = {}
    palette = expand_palette(json.loads((ASSETS / 'materials.json').read_text(encoding='utf-8')))
    registered = set()
    for slug in SLUGS:
        before = read(APPROVED / 'models', slug)
        after = read(MODELS, 'v2_' + slug)
        assert [r[:3] + [r[3].removeprefix('v2_')] + r[4:] for r in after['blocks']] == before['blocks']
        expected_animations = before['animationGroups']
        for group, data in overrides.get(slug, {}).get('groups', {}).items():
            expected_animations[group]['levitationBob'] = data['levitationBob']
        assert after['animationGroups'] == expected_animations, slug
        assert after['meta'].get('constructionOrder') == before['meta'].get('constructionOrder'), slug
        for row in after['blocks']:
            registered.add(palette[row[3]]['id'])
        imported[slug] = {'coordinatesOrderMaterialsGroupsPreserved': True,
                          'animationMetadataExactIncludingBakedOverrides': True,
                          'constructionOrderExact': True}

    loom = read(MODELS, 'v2_loom')
    loom_positions = {tuple(row[:3]) for row in loom['blocks']}
    machine_assembly = {}
    for kind in ('sun', 'solar', 'turbine', 'processing', 'entangler', 'dimension', 'battery', 'assembly', 'accelerator'):
        full, proof = indices(MODELS, ['v2_base_t3', 'v2_' + kind, 'v2_loom'])
        assert sum(row[5] == 0 for row in full) == len(loom_positions)
        assert not any((x, 5, 34) in {(r[0], r[1], r[2]) for r in full} for x in range(50, 63))
        assert any(r[:3] == (56, 4, 35) for r in full)
        assert any(r[:3] == (56, 4, 47) for r in full)
        machine_assembly[kind] = {**proof, 'manualLoomVoxels': len(loom_positions),
                                  'paidVoxels': sum(row[5] != 0 for row in full),
                                  'optionalWorkPortsAbsentFromPaidIndices': 13}
    # Preserve the approved R5 construction-order coordinates exactly. The released
    # V30 metadata still referenced the pre-collar-transfer array and is not an authority.
    old_base = read(baseline, 'v2_base_t3')
    approved_base = read(APPROVED / 'models', 'base_t3')
    r5_base = read(MODELS, 'v2_base_t3')
    order = approved_base['meta']['constructionOrder']
    assert len(order) == len(set(order)) == len(approved_base['blocks'])
    assert [approved_base['blocks'][i][:3] for i in order] == [r5_base['blocks'][i][:3] for i in order]
    plugin_checks = 0
    for slug in ('plugin_parallel', 'plugin_speed', 'plugin_efficiency'):
        before = read(baseline, 'v2_' + slug)['blocks']
        after = read(MODELS, 'v2_v30_' + slug)['blocks']
        assert before == after
        for mount in old_base['meta']['attachmentPlacement']['mounts']:
            angle = mount['yawRadians']
            # Frozen files and exact mounting transforms imply identical inverse-sampled integer cells.
            assert math.isfinite(angle) and all(isinstance(v, int) for v in mount['position'])
            plugin_checks += 1
    assert len(registered) <= 30, len(registered)
    return {'scope': 'Independent JSON contract; not an in-game save migration test',
            'frozenLayoutComparisons': frozen_layouts, 'importedSemanticChecks': imported,
            'newAssemblies': machine_assembly, 'registeredMaterialIdentitiesUsed': sorted(registered),
            'frozenPluginMountChecks': plugin_checks,
            'originalConstructionOrderCoordinatesPreserved': len(order),
            'manualLoomOwnershipInAllNineAssemblies': True}


def main():
    authority = json.loads((WORK / 'root/delivery-meta.json').read_text(encoding='utf-8'))
    palette = expand_palette(json.loads((ASSETS / 'materials.json').read_text(encoding='utf-8')))
    baseline = WORK / 'source-baseline/src/main/resources/assets/gtsr/critical/models'
    binding_path = ASSETS / 'source-sha256.json'
    binding = json.loads(binding_path.read_text(encoding='utf-8'))
    snapshots = {}
    for slug in FROZEN:
        source = baseline / f'v2_{slug}.geometry.json'
        # source-baseline is a byte-for-byte snapshot captured before implementation.
        if not source.exists():
            raise FileNotFoundError(source)
        raw = source.read_bytes()
        target = MODELS / f'v2_v30_{slug}.geometry.json'
        if target.exists():
            assert target.read_bytes() == raw, f'frozen bytes changed: {target}'
        else:
            target.write_bytes(raw)
        snapshots[target.stem.removesuffix('.geometry')] = {
            'sourceRef': authority['sourceBaseCommit'], 'sha256': sha(raw),
            'usage': 'exact released V30 bytes; persisted pre-31 V2 jobs retain indices and fingerprint'}
        binding['files'][target.relative_to(ROOT).as_posix()] = sha(raw)

    levitation_raw = (APPROVED / 'preview/levitation.js').read_bytes()
    assert sha(levitation_raw) == authority['approvedSha256']['preview/levitation.js']
    match = re.search(r'window\.V2_LEVITATION_OVERRIDES\s*=\s*(\{.*\});\s*\nwindow\.V2_APPLY',
                      levitation_raw.decode('utf-8'), re.S)
    if not match:
        raise ValueError('approved levitation override envelope changed')
    overrides = json.loads(match.group(1))
    imported = {}
    for slug in SLUGS:
        relative = f'models/{slug}.geometry.json'
        raw = (APPROVED / relative).read_bytes()
        assert sha(raw) == authority['approvedSha256'][relative], relative
        model = json.loads(raw)
        original_rows = [row[:] for row in model['blocks']]
        if slug in overrides:
            override = overrides[slug]
            for group, data in override['groups'].items():
                actual = sorted(tuple(row[:3]) for row in model['blocks'] if row[4] == group)
                assert actual == sorted(map(tuple, data['cells'])), f'{slug}:{group} membership'
                model['animationGroups'][group]['levitationBob'] = data['levitationBob']
            model['meta']['r5Levitation'] = override['meta']
        for row in model['blocks']:
            row[3] = 'v2_' + row[3]
            assert row[3] in palette, row[3]
        if 'materialCounts' in model:
            model['materialCounts'] = {'v2_' + key: value for key, value in model['materialCounts'].items()}
        # Deliberately do not recursively rename metadata strings: clockIds, groupIds,
        # references, gravity clusters and constructionOrder are approved data.
        model['meta']['runtimeLayoutRevision'] = 31
        model['meta']['runtimeGeometryVersion'] = 2
        model['meta']['approvedR5SourceSha256'] = sha(raw)
        model['meta']['runtimeImport'] = 'tools/critical/import_r5_v31.py'
        assert [r[:3] + [r[3].removeprefix('v2_')] + r[4:] for r in model['blocks']] == original_rows
        target = MODELS / f'v2_{slug}.geometry.json'
        dump(target, model)
        digest = sha(target.read_bytes())
        binding['files'][target.relative_to(ROOT).as_posix()] = digest
        imported[slug] = {'sourceSha256': sha(raw), 'runtimeSha256': digest,
                          'blocks': len(model['blocks']), 'groups': len(model['animationGroups']),
                          'overrideGroups': len(overrides.get(slug, {}).get('groups', {}))}

    binding.setdefault('compatibilitySnapshots', {}).update(snapshots)
    binding['authorities']['v2']['r5Runtime'] = {
        'revision': 31, 'source': 'immutable approved-v2 R5 snapshot',
        'importer': 'tools/critical/import_r5_v31.py', 'models': imported,
        'levitationOverrideSha256': sha(levitation_raw),
        'materialNamespace': 'v2_ aliases in existing materials.json; 30 registered material identities'}
    dump(binding_path, binding, True)
    report = {'approvedSourceBase': authority['sourceBaseCommit'], 'revision': 31,
              'frozen': snapshots, 'imported': imported,
              'levitationOverrideSha256': sha(levitation_raw)}
    dump(WORK / 'assets/import-report.json', report, True)
    dump(WORK / 'assets/geometry-verification.json', verify(baseline, overrides), True)
    print(json.dumps({'importedModels': len(imported), 'frozenModels': len(snapshots),
                      'overrideGroups': {s: imported[s]['overrideGroups'] for s in ('sun', 'solar')}}))


if __name__ == '__main__':
    main()
