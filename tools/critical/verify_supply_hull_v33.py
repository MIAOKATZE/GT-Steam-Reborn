"""Approved HTML parity, immutable V31/V32 assets, and bounded floating-hull clearance."""
from pathlib import Path
from collections import Counter,defaultdict
import argparse,hashlib,json,subprocess
from verify_supply_terrace_v32 import envelope

ROOT=Path(__file__).resolve().parents[2]
MODELS=ROOT/'src/main/resources/assets/gtsr/critical/models'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--approval',type=Path,default=ROOT/'build/v2043-release/geometry/approved/approved-shape.json')
    parser.add_argument('--out',type=Path,default=ROOT/'build/v2043-release/geometry/static-contract.json')
    args=parser.parse_args()
    approval=json.loads(args.approval.read_text(encoding='utf-8'))
    materials={'shell':'v2_ceramic','trim':'v2_titanium','walk':'v2_titanium','supply':'v2_service_hatch'}
    current=json.loads((MODELS/'v2_base_t3.geometry.json').read_text(encoding='utf-8'))
    rows=[r for r in current['blocks'] if r[4]=='supply_hull_33']
    assert rows==[r[:3]+[materials[r[3]],'supply_hull_33'] for r in approval['body']], 'exact approved world coordinates/material roles'
    assert len(rows)==476 and Counter(r[3] for r in rows)['v2_service_hatch']==90
    body={tuple(r[:3]) for r in rows};ports={tuple(r) for r in approval['ports']};heads={(x,y+d,z) for x,y,z in ports for d in (1,2)}
    assert len(ports)==90 and len(heads)==180
    assert not ports&body and not heads&body and not ports&heads
    for x,y,z in approval['supplies']:assert (x,y-1,z) in body
    assert min(r[1] for r in rows)==2,'no new ground columns'
    baseline=subprocess.check_output(['git','show','v2.0.42:src/main/resources/assets/gtsr/critical/models/v2_base_t3.geometry.json'],cwd=ROOT)
    old=json.loads(baseline)
    frozen=MODELS/'v2_v32_base_t3.geometry.json';assert frozen.read_bytes()==baseline
    v31=subprocess.check_output(['git','show','v2.0.41:src/main/resources/assets/gtsr/critical/models/v2_base_t3.geometry.json'],cwd=ROOT)
    assert (MODELS/'v2_v31_base_t3.geometry.json').read_bytes()==v31
    retained=[r for r in old['blocks'] if r[4] not in ('supply_terrace','machine_service_ports','machine_service_support')]
    assert current['blocks']==retained+rows
    assert current['animationGroups']==old['animationGroups']
    assert current['meta']['attachmentPlacement']==old['meta']['attachmentPlacement']
    assert not any(r[4] in ('supply_terrace','machine_service_ports','machine_service_support') for r in current['blocks'])
    old_bitmap=sorted([r for r in old['blocks'] if r[4]=='supply_terrace'],key=lambda r:(r[1],r[0],r[2]))
    digest=hashlib.sha256(''.join(f'{x},{y},{z},{m};' for x,y,z,m,_ in old_bitmap).encode()).hexdigest()
    assert digest=='60fa36add408d290619860c086e3f841aea2e448f5d6ef3fbde647e316e102fd'
    dynamic=[];checks={}
    for slug in ('v2_base_t3','v2_loom','v2_solar','v2_turbine','v2_battery','v2_sun'):
        model=current if slug=='v2_base_t3' else json.loads((MODELS/(slug+'.geometry.json')).read_text(encoding='utf-8'))
        other=[r for r in model['blocks'] if r[4]!='supply_hull_33'];positions={tuple(r[:3]) for r in other}
        assert not (body|ports|heads)&positions,(slug,'static collision')
        groups=defaultdict(list)
        for r in other:groups[r[4]].append(r)
        count=0
        for name,g in model['animationGroups'].items():
            if name not in groups or g.get('kind')=='static-material-zone':continue
            bounds=envelope(groups[name],g);count+=1
            assert all(any(p[i]+.5<=bounds[0][i] or p[i]-.5>=bounds[1][i] for i in range(3)) for p in body|ports|heads),(slug,name,'motion overlap')
        checks[slug]=dict(staticOverlap=0,motionEnvelopes=count)
    result=dict(status='PASS',approvalSHA256=approval['approvedHtmlSHA256'],body=476,supplyBays=90,headroom=180,posts=0,
        levels=approval['levels'],unchangedV31SHA256=hashlib.sha256(v31).hexdigest(),unchangedV32SHA256=hashlib.sha256(baseline).hexdigest(),
        oldBitmapSHA256=digest,currentSHA256=hashlib.sha256((MODELS/'v2_base_t3.geometry.json').read_bytes()).hexdigest(),clearance=checks,
        scope='exact frozen approved payload and conservative geometry envelopes; no game/client or paid lifecycle execution')
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))


if __name__=='__main__':main()
