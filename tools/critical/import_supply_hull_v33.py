"""Import the approved floating hull verbatim; freeze the released V32 paid model and bitmap authority."""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/gtsr/critical'
REF='v2.0.42'
APPROVAL='c811ed2bb12a901cdf32a5b2f70a683faecbf952f5eb312298cea3db96fbe4e9'
MATERIALS={'shell':'v2_ceramic','trim':'v2_titanium','walk':'v2_titanium','supply':'v2_service_hatch'}
GROUP='supply_hull_33'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--shape',type=Path,default=ROOT/'build/v2043-release/geometry/approved/approved-shape.json')
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--out',type=Path,default=ROOT/'build/v2043-release/geometry/candidate')
    args=parser.parse_args()
    approved=json.loads(args.shape.read_text(encoding='utf-8'))
    assert approved['approvedHtmlSHA256']==APPROVAL
    assert approved['counts']==dict(body=476,deck=238,supplies=90,ports=90,posts=0)
    rows=[r[:3]+[MATERIALS[r[3]],GROUP] for r in approved['body']]
    assert len({tuple(r[:3]) for r in rows})==476
    raw=subprocess.check_output(['git','show',f'{REF}:src/main/resources/assets/gtsr/critical/models/v2_base_t3.geometry.json'],cwd=ROOT)
    model=json.loads(raw)
    retained=[i for i,r in enumerate(model['blocks']) if r[4] not in ('supply_terrace','machine_service_ports','machine_service_support')]
    remap={old:new for new,old in enumerate(retained)}
    old_order=model['meta']['constructionOrder']
    model['blocks']=[model['blocks'][i] for i in retained]+rows
    model['meta']['constructionOrder']=[remap[i] for i in old_order if i in remap]+list(range(len(retained),len(model['blocks'])))
    model['meta']['runtimeLayoutRevision']=33
    model['meta']['supplyTerrace']=dict(
        revision=33,owner='paid base for new jobs; independent free ownership for legacy jobs',
        generator='tools/critical/import_supply_hull_v33.py',approvedHtmlSHA256=APPROVAL,
        approvedGeneratorSHA256=approved['generatorSHA256'],levels=approved['levels'],
        supplySupports=approved['supplies'],optionalWorkPorts=approved['ports'],
        addedBlocks=476,deckBlocks=238,continuousLowerHull=True,supportPosts=0,
        originalSupplyRowRemoved=True,originalOptionalPortsRemoved=True)
    model['bounds']=[[min(r[i] for r in model['blocks']) for i in range(3)],[max(r[i] for r in model['blocks']) for i in range(3)]]
    model['materialCounts']=dict(Counter(r[3] for r in model['blocks']))
    target=ASSETS if args.apply else args.out
    (target/'models').mkdir(parents=True,exist_ok=True)
    path=target/'models/v2_base_t3.geometry.json'
    path.write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
    if args.apply:
        frozen=ASSETS/'models/v2_v32_base_t3.geometry.json'
        if frozen.exists():assert frozen.read_bytes()==raw
        else:frozen.write_bytes(raw)
        binding_path=ASSETS/'source-sha256.json';binding=json.loads(binding_path.read_text(encoding='utf-8'))
        for p in (path,frozen):binding['files'][p.relative_to(ROOT).as_posix()]=sha(p.read_bytes())
        binding.setdefault('compatibilitySnapshots',{})['v2_v32_base_t3']=dict(sourceRef=REF,sha256=sha(raw),usage='Exact released V32 paid membership/order and immutable 1314-position supplyTerracePlaced bitmap authority')
        binding['authorities']['v2']['sharedStructureRevision']=dict(revision='v2.0.43 / layout 33',models=['base_t3'],source='tools/critical/import_supply_hull_v33.py; approved HTML '+APPROVAL)
        binding_path.write_text(json.dumps(binding,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(model=str(path),sha256=sha(path.read_bytes()),blocks=len(model['blocks']),newHull=476,supplies=90,approval=APPROVAL)))


if __name__=='__main__':main()
