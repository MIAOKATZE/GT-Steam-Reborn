"""Bounded JSON geometry contract; does not claim a native game/save migration test."""
from pathlib import Path
from collections import Counter, defaultdict
import argparse
import hashlib
import itertools
import json
import math
from amend_supply_terrace_v32 import ASSETS, ROOT, BASE, baseline, terrace


def vector_length(v):
    return math.sqrt(sum(x*x for x in v))


def envelope(rows, group):
    """Conservative whole-cycle sphere: arbitrary rotations, maximum scale/offsets."""
    pivot = group.get('pivot', [0, 0, 0])
    frames = list(group.get('cycle', {}).get('keyframes', []))
    for cycle in group.get('cyclesByMode', {}).values():
        frames.extend(cycle.get('keyframes', []))
    scale = max([1] + [abs(f.get('scale', 1)) for f in frames])
    radius = max(vector_length([r[i]+corner[i]-pivot[i] for i in range(3)])
                 for r in rows for corner in itertools.product((-.5, .5), repeat=3)) * scale
    offset = [max([0] + [abs(f.get('offset', [0, 0, 0])[i]) for f in frames]) for i in range(3)]
    if group.get('cycleOffsetSpace') == 'local-before-continuous-rotation':
        radius += max([0] + [vector_length(f.get('offset',[0,0,0])) for f in frames])
        offset = [0, 0, 0]
    continuous = group.get('continuousMotion', {})
    if continuous and continuous.get('kind') != 'rotateY':
        offset['xyz'.index(continuous.get('axis','y'))] += abs(continuous.get('amplitude',0))
    for motion in ('idleMotion', 'workMotion'):
        v = group.get(motion, {}).get('offset', [0, 0, 0])
        offset = [offset[i] + abs(v[i]) for i in range(3)]
    # A secondary pivot may move the entire primary sphere as it turns.
    for motion in ('ambientTilt', 'ambientOrbit'):
        other = group.get(motion, {}).get('pivot', pivot)
        radius += 2 * vector_length([other[i]-pivot[i] for i in range(3)])
    offset[1] += abs(group.get('levitationBob', {}).get('amplitude', 0))
    if group.get('kind') == 'levitation-carrier':
        return [[min(r[i] for r in rows)-.5-offset[i] for i in range(3)],
                [max(r[i] for r in rows)+.5+offset[i] for i in range(3)]]
    return [[pivot[i]-radius-offset[i] for i in range(3)],
            [pivot[i]+radius+offset[i] for i in range(3)]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', type=Path, default=ROOT/'build/v2042-release/geometry/candidate')
    parser.add_argument('--report', type=Path, default=ROOT/'build/v2042-release/geometry/functional-geometry-contract.json')
    args = parser.parse_args()
    raw = baseline()
    original = json.loads(raw)
    model = json.loads((args.candidate/BASE).read_text(encoding='utf-8'))
    assert model == terrace(raw), 'candidate must exactly reproduce generator'
    frozen = args.candidate/'models/v2_v31_base_t3.geometry.json'
    if frozen.exists():
        assert frozen.read_bytes() == raw, 'released V31 paid base must retain exact bytes'
    old_count = len(original['blocks'])
    assert model['blocks'][:old_count] == original['blocks']
    assert model['animationGroups'] == original['animationGroups']
    added = model['blocks'][old_count:]
    # This is the persisted V32 compatibility bitmap contract, not an editable preview order.
    stable = sorted(added,key=lambda r:(r[1],r[0],r[2]))
    stable_hash = hashlib.sha256(''.join(f'{x},{y},{z},{material};' for x,y,z,material,_ in stable).encode()).hexdigest()
    assert stable_hash == '60fa36add408d290619860c086e3f841aea2e448f5d6ef3fbde647e316e102fd', 'never reorder or replace the persisted V32 terrace bitmap positions'
    positions = {tuple(r[:3]) for r in added}
    assert len(positions) == len(added)
    columns = defaultdict(set)
    for x, y, z, *_ in added:
        columns[x, z].add(y)
    assert all(ys == set(range(max(ys)+1)) for ys in columns.values()), 'solid casing below every tread'
    assert sorted({max(ys) for ys in columns.values()}) == [4, 5, 6, 7, 8]
    assert all((112-x, y, z) in positions for x, y, z in positions), 'symmetric shoulders'
    assert [max(columns[56, z]) for z in range(33, 21, -1)] == [4,4,5,5,5,6,6,7,7,7,8,8]
    assert len(model['meta']['constructionOrder']) == len(model['blocks'])
    assert len(set(model['meta']['constructionOrder'])) == len(model['blocks'])
    assert Counter(r[3] for r in model['blocks']) == model['materialCounts']
    assert model['bounds'][0][1] == 0
    all_rows = {tuple(r[:3]):r for r in model['blocks']}
    supplies = model['meta']['supplyTerrace']['supplySupports']
    work_ports = model['meta']['supplyTerrace']['optionalWorkPorts']
    assert len(supplies) == len(work_ports) == 30
    assert Counter(p[1] for p in supplies) == {4:6,5:6,6:6,7:6,8:6}
    assert sorted(work_ports) == sorted([[x,y+1,z] for x,y,z in supplies])
    for p in supplies:
        assert all_rows[tuple(p)][3:] == ['v2_service_hatch','supply_terrace']
    gt_slots = {tuple(p) for p in work_ports}
    gt_headroom = {(x,y+1,z) for x,y,z in work_ports}
    assert not (gt_slots | gt_headroom) & all_rows.keys(), 'GT block and cable headroom must stay air in the paid model'
    assert not gt_slots & gt_headroom, 'one GT chamber must not fill another chamber headroom'
    for x in range(50, 63):
        assert all_rows[x,4,34][3:] == ['v2_service_hatch','machine_service_ports']
        assert not any((x,y,34) in all_rows for y in range(5, 10)), '13 port headroom preserved'
    assert not any((x,5,35) in positions or (x,5,33) in positions for x in range(50,63))
    assemblies = {}
    historical = {}
    for slug in ('v2_legacy_base_t3','v2_v25_base_t3','v2_v27_base_t3','v2_v30_base_t3',
                 'v2_legacy_loom','v2_v25_loom','v2_v27_loom','v2_v30_loom'):
        other = json.loads((ASSETS/f'models/{slug}.geometry.json').read_text(encoding='utf-8'))
        overlap = positions & {tuple(r[:3]) for r in other['blocks']}
        assert not overlap, (slug,'historical paid or manual collision')
        historical[slug] = len(overlap)
    body_boxes = []
    for slug in ('v2_loom','v2_solar','v2_turbine','v2_battery','v2_sun'):
        other = json.loads((ASSETS/f'models/{slug}.geometry.json').read_text(encoding='utf-8'))
        occupied = {tuple(r[:3]) for r in other['blocks']}
        assert not positions & occupied, (slug, 'overlap')
        assert not (gt_slots | gt_headroom) & occupied, (slug,'GT chamber/headroom overlap')
        groups = defaultdict(list)
        for row in other['blocks']:
            groups[row[4]].append(row)
        for name, group in other['animationGroups'].items():
            if name not in groups or group.get('kind') == 'static-material-zone':
                continue
            body_boxes.append((slug,name,envelope(groups[name],group)))
        assemblies[slug] = dict(existingBlocks=len(occupied),newOverlap=0)
        if slug == 'v2_loom':
            branches = [r for r in other['blocks'] if r[4] in ('manual_low_branch_7','manual_low_branch_3')]
            distance = min(max(abs(p[i]-r[i]) for i in range(3)) for p in positions for r in branches)
            assert distance >= 2, 'at least one empty voxel width to original white low branches'
    groups = defaultdict(list)
    for row in original['blocks']:
        groups[row[4]].append(row)
    for name, group in original['animationGroups'].items():
        body_boxes.append(('v2_base_t3',name,envelope(groups[name],group)))
    for slug, name, bounds in body_boxes:
        for p in positions | gt_slots | gt_headroom:
            assert any(p[i]+.5 <= bounds[0][i] or p[i]-.5 >= bounds[1][i] for i in range(3)), (slug,name,p,'motion envelope overlap')
    result = dict(status='PASS',scope='JSON geometry and conservative whole-cycle bounds; no native client or paid save execution',
        baselineSHA256=hashlib.sha256(raw).hexdigest(),
        candidateSHA256=hashlib.sha256((args.candidate/BASE).read_bytes()).hexdigest(),
        addedBlocks=len(added),stableBitmapSHA256=stable_hash,solidColumns=len(columns),oldBaseRowsUnchanged=old_count,
        originalPorts=13,additionalSupplyCasings=30,additionalGTChambers=30,totalPorts=43,
        explicitGTHeadroom=30,lowBranchEmptyGap=distance-1,conservativeDynamicEnvelopes=len(body_boxes),
        assemblies=assemblies,historicalIntersections=historical,controllerRelativeFoundationBottom=-4)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
