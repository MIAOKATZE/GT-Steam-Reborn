"""Verify resource-only equivalence and freeze one native irradiation screenshot, without launching."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/v2012-ui-qa'
KEY='gtsr.critical.nei.simple.intro.solar_dust'


def sha(raw):return hashlib.sha256(raw).hexdigest()
def entries(path):
    with zipfile.ZipFile(path) as z:return {name:z.read(name) for name in z.namelist() if not name.endswith('/')}
def language(raw):
    return {line.split('=',1)[0]:line.split('=',1)[1] for line in raw.decode('utf-8').splitlines() if '=' in line and not line.lstrip().startswith('#')}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);a=p.parse_args()
    old_path=OUT/'candidate-r1.jar';new_path=a.jar.resolve(strict=True)
    old,new=entries(old_path),entries(new_path)
    assert old.keys()==new.keys(),'Candidate entry set changed'
    changed=[name for name in old if old[name]!=new[name]]
    expected={'assets/gtsr/lang/zh_CN.lang','assets/gtsr/lang/en_US.lang'}
    assert set(changed)==expected,changed
    changes=[]
    for name in changed:
        before,after=language(old[name]),language(new[name])
        assert before.keys()==after.keys(),name
        different=[key for key in before if before[key]!=after[key]]
        assert different==[KEY],(name,different)
        changes.append(dict(resource=name,key=KEY,before=before[KEY],after=after[KEY]))
    rows=json.loads((OUT/'gallery-v2012-compact.json').read_text(encoding='utf-8'))
    row=next(row for row in rows if row['name']=='v2012.irradiation')
    row['name']=row['id']='v2012.r3.irradiation'
    plan=OUT/'gallery-v2012-r3-irradiation.json';plan.write_text(json.dumps([row],ensure_ascii=False,indent=2),encoding='utf-8')
    receipt=dict(status='PREPARED_NOT_LAUNCHED',sha256=sha(plan.read_bytes()),captures=1,candidateSHA256=sha(new_path.read_bytes()),baselineCandidateSHA256=sha(old_path.read_bytes()),
                 exactUncompressedClassEquality=True,classFilesCompared=sum(name.endswith('.class') for name in old),changedResources=changes,
                 inheritedScope='The other 18 r2 screenshots share every class/resource byte; only this irradiation introduction key differs in zh_CN and en_US. R3 visually replaces the zh_CN irradiation screenshot; physical input/business remain NOT_RUN.')
    plan.with_suffix('.manifest.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(plan=str(plan),**receipt),ensure_ascii=False,indent=2))


if __name__=='__main__':main()
