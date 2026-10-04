"""Independent read-only v72 decode, registration, source pin and generation closure."""
from pathlib import Path
import io,json,re
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical
from combat_audio_refine import ROOT,AUTHORED,sha,SR

assets=ROOT/'src/main/resources/assets/gtsr'
evidence=ROOT/'temp/refinement-v72/audio'
registry=json.loads((assets/'sounds.json').read_text())
all_files=set((assets/'sounds/combat').glob('*.ogg'))
used=set();rows=[];peak=-100
for key,value in registry.items():
    assert ':' not in key
    for entry in value['sounds']:
        name=entry if isinstance(entry,str) else entry['name'];p=assets/'sounds'/(name.removeprefix('gtsr:')+'.ogg')
        assert p.exists(),str(p)
        if 'combat/' not in name:continue
        assert not entry.get('stream',False)
        raw=p.read_bytes();assert canonical(raw)==raw
        x,rate=sf.read(io.BytesIO(raw));assert rate==SR and x.ndim==1 and np.isfinite(x).all()
        assert .10<len(x)/rate<2.1
        maximum=max(float(max(abs(signal.resample_poly(x,p,q)))) for p,q in [(1,2),(1,1),(2,1)])
        assert .025<maximum<10**(-1/20)
        db=float(20*np.log10(maximum));peak=max(peak,db)
        used.add(p);rows.append({'key':key,'seconds':len(x)/rate,'peakDb':db,'sha256':sha(raw)})
assert used==all_files,'Orphan combat files: '+str(all_files-used)
pins={}
for p in (evidence/'families').glob('*/sha.json'):
    pins.update(json.loads(p.read_text()))
pins.update(json.loads((evidence/'boss-production/sha.json').read_text()))
mini_pins=evidence/'mini-roles/sha-production-v2.json'
if mini_pins.exists():
    pins.update(json.loads(mini_pins.read_text()))
    for code in json.loads((AUTHORED/'mini-canonical-map.json').read_text()).values():
        assert all('mini.'+code+'.'+event in registry for event in ('idle','hurt','death','windup','release'))
for key,row in pins.items():
    p=assets/'sounds/combat'/(key.replace('.','_')+'.ogg');assert sha(p.read_bytes())==row['ogg'],key
assert len(pins)==len(all_files),'Production asset has no v72 fingerprint'
source_rows=[]
for p in AUTHORED.glob('*sources.json'):
    for row in json.loads(p.read_text()).get('sources',[]):
        source=AUTHORED/'original'/row['file'];assert sha(source.read_bytes())==row['sha256'],str(source)
        source_rows.append(row['file'])
bird=json.loads((AUTHORED/'bird-source.json').read_text());assert sha((AUTHORED/'original'/bird['file']).read_bytes())==bird['sha256']
java=ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity';literal=set()
for p in java.rglob('*.java'):
    for key in re.findall(r'"gtsr:([a-z][a-z0-9_.-]+)"',p.read_text(encoding='utf-8')):
        if not key.endswith('.') and key.split('.')[0] in ('king','colossus','hive','entity','spawner','oathguard','creature','echo','mini'):literal.add(key)
assert literal<=set(registry),'Java sound keys missing: '+str(literal-set(registry))
report={'result':'PASS','productionAssets':len(all_files),'registeredKeys':len(registry),'sourceFilesVerified':len(set(source_rows))+1,'literalKeys':len(literal),'maxResampledPeakDb':peak,'orphans':0,'productionFingerprintCoverage':'PASS','subjective_audio_audition':'NOT_PERFORMED','userFeedback':'Direction feedback incorporated, no assumption that user fully played samples, no final listening acceptance','entries':rows}
(evidence/'production-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='entries'}))
