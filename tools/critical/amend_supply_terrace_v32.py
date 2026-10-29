"""Build the shared five-level supply terrace, freezing the released V31 paid base."""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/gtsr/critical'
BASE = 'models/v2_base_t3.geometry.json'
REF = 'v2.0.41'
# Backwards from the controller: a broad, alternating 2/3-block walking rhythm.
TREADS = [(32, 33, 4, 6), (29, 31, 5, 7), (27, 28, 6, 8),
          (24, 26, 7, 9), (22, 23, 8, 8)]


def supply_supports():
    """Six real supply casings per tread, with the original thirteen-port cable lane left open."""
    return [(x, top, 32 if top == 4 else high_z - abs(x - 56)//4)
            for _,high_z,top,_ in TREADS for x in (52,53,54,58,59,60)]


def baseline():
    return subprocess.check_output(['git', 'show', f'{REF}:src/main/resources/assets/gtsr/critical/{BASE}'], cwd=ROOT)


def terrace(original):
    model = json.loads(original)
    occupied = {tuple(r[:3]) for r in model['blocks']}
    added = []
    supplies = set(supply_supports())
    for low_z, high_z, top, half_width in TREADS:
        for tread_z in range(low_z, high_z + 1):
            # Chamfer each shoulder. The outside lips widen before the next rise.
            width = half_width - (tread_z == low_z or top == 7 and tread_z == low_z + 1)
            for x in range(56 - width, 57 + width):
                # Stagger the shoulders towards the branches, giving the outline
                # a shallow bow instead of five parallel rectangular bars.
                z = tread_z - abs(x - 56) // 4
                for y in range(top + 1):
                    pos = x, y, z
                    assert pos not in occupied, ('terrace overlaps released base', pos)
                    edge = abs(x - 56) == width
                    material = 'v2_ceramic_dark'
                    if y == top:
                        material = 'v2_ceramic_dark' if abs(x - 56) <= 1 or edge else 'v2_ceramic'
                        if abs(x - 56) in (2, 3) and tread_z == high_z:
                            material = 'v2_panel'
                    elif edge and tread_z == high_z and top in (4, 6, 8):
                        material = 'v2_titanium'
                    elif y == 1 and abs(x - 56) in (3, 4):
                        material = 'v2_panel'
                    if pos in supplies:
                        material = 'v2_service_hatch'
                    added.append([x, y, z, material, 'supply_terrace'])
                    occupied.add(pos)
    model['blocks'].extend(added)
    model['materialCounts'] = dict(Counter(row[3] for row in model['blocks']))
    model['bounds'] = [[min(row[i] for row in model['blocks']) for i in range(3)],
                       [max(row[i] for row in model['blocks']) for i in range(3)]]
    order = model['meta']['constructionOrder']
    assert len(order) == len(model['blocks']) - len(added)
    order.extend(range(len(order), len(model['blocks'])))
    model['meta']['runtimeLayoutRevision'] = 32
    model['meta']['supplyTerrace'] = dict(
        owner='paid base', generator='tools/critical/amend_supply_terrace_v32.py',
        baselineRef=REF, baselineSHA256=hashlib.sha256(original).hexdigest(),
        treads=[dict(z=[a,b],topY=y,halfWidth=w) for a,b,y,w in TREADS],
        foundationY=0, centralGapToExistingBranchSeatZ=[20,21],
        unchangedSupplyRow=dict(x=[50,62],y=4,z=34),
        unchangedOptionalPorts=dict(x=[50,62],y=5,z=34),
        supplySupports=[list(p) for p in supply_supports()],
        optionalWorkPorts=[[x,y+1,z] for x,y,z in supply_supports()],
        addedBlocks=len(added))
    return model


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT/'build/v2042-release/geometry/candidate')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    raw = baseline()
    model = terrace(raw)
    target = ASSETS if args.apply else args.output
    if args.apply:
        frozen = ASSETS/'models/v2_v31_base_t3.geometry.json'
        if frozen.exists():
            assert frozen.read_bytes() == raw
        else:
            frozen.write_bytes(raw)
    dump(target/BASE, model)
    if args.apply:
        binding_path = ASSETS/'source-sha256.json'
        binding = json.loads(binding_path.read_text(encoding='utf-8'))
        for path in (target/BASE, frozen):
            binding['files'][path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
        binding.setdefault('compatibilitySnapshots', {})['v2_v31_base_t3'] = dict(
            sourceRef=REF, sha256=hashlib.sha256(raw).hexdigest(),
            usage='persisted V31 journals retain paid geometry, order and fingerprint; free terrace uses a separate ownership bitmap')
        binding['authorities']['v2']['sharedStructureRevision'] = dict(
            revision='v2.0.42 / layout 32', models=['base_t3'],
            source='tools/critical/amend_supply_terrace_v32.py against released v2.0.41 V31 bytes')
        binding_path.write_text(json.dumps(binding, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(model['meta']['supplyTerrace']))


if __name__ == '__main__':
    main()
