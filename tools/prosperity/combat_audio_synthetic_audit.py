"""Read-only independent v73 production/registration/preview/source audit."""
from pathlib import Path
import hashlib, io, json, re
import numpy as np
import soundfile as sf
from scipy import signal

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/gtsr'
SOURCE=ROOT/'plan/model/authored/audio/synthetic-v73'
sha=lambda b:hashlib.sha256(b).hexdigest()
registry=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
manifest=json.loads((SOURCE/'manifest.json').read_text(encoding='utf-8'))
preview_text=(ROOT/'plan/prosperity/sound-preview-data.js').read_text(encoding='utf-8')
preview=json.loads(preview_text.split('=',1)[1].strip().removesuffix(';'))
assert not any(k.startswith('mini.') for k in registry)
assert manifest['sampleInputs']==[] and not manifest['speechModels'] and not manifest['formantModels'] and not manifest['glottalModels']
assert manifest['producerSHA256']==sha((ROOT/manifest['producer']).read_bytes())
assert len(manifest['profiles'])==39
assert preview['soundsJSONSourceSHA256']==sha((ASSETS/'sounds.json').read_bytes())
assert set(e['event'] for e in preview['events'])==set(registry)
pins={r['path']:r for r in manifest['assets']};used=set();worst=-100;music=[];oathplate=[]
for key,item in registry.items():
    for entry in item['sounds']:
        name=entry if isinstance(entry,str) else entry['name'];relative=name[5:]+'.ogg';p=ASSETS/'sounds'/relative
        raw=p.read_bytes();assert sha(raw)==pins[relative]['oggSHA256']
        assert raw==(SOURCE/'source'/p.name).read_bytes()==(ROOT/'plan/prosperity/media/audio/synthetic-v73'/relative).read_bytes()
        x,rate=sf.read(io.BytesIO(raw));assert rate==32000 and x.ndim==1 and len(x)>3200 and np.isfinite(x).all()
        maximum=max(float(np.max(abs(signal.resample_poly(x,100,round(pitch*100))))) for pitch in [.5,.6,.75,.8,.85,.9,1,1.1,1.18,1.2,1.5,2])
        assert .02<maximum<10**(-1/20);worst=max(worst,float(20*np.log10(maximum)))
        if key.startswith(('entity.dr-09.','oathguard.')):
            recipe=pins[relative];event=key.rsplit('.',1)[1]
            assert recipe['recipeBranch']=='oathplate-r2' and recipe['pitchMotion']=='none' and recipe['fm']=='none'
            assert recipe['gestures']==(2 if event=='death' else 1)
            assert len(x)/rate<={'idle':.20,'hurt':.28,'attack':.40,'death':.65}[event]
            random_pitch_peaks=[float(np.max(abs(signal.resample_poly(x,100,round(pitch*100))))) for pitch in np.linspace(.8,1.2,41)]
            assert max(random_pitch_peaks)<10**(-1/20)
            if event=='death':
                tail_rms=float(np.sqrt(np.mean(x[-int(.1*rate):]**2)))
                assert tail_rms<maximum*.01,'Oathguard death has a sustained tail'
            oathplate.append({'key':key,'seconds':len(x)/rate,'gestures':recipe['gestures'],'normalRandomPitchRange':[.8,1.2],'normalRandomPitchPeakDb':float(20*np.log10(max(random_pitch_peaks)))})
        if item['category']=='music':
            assert entry['stream'];music.append({'key':key,'seconds':len(x)/rate,'sha256':sha(raw)})
        else:assert not entry.get('stream',False)
        used.add(p)
assert used==set((ASSETS/'sounds').rglob('*.ogg'))
for event in preview['events']:
    for variant in event['variants']:
        raw=(ROOT/variant['source']).read_bytes();assert sha(raw)==variant['sourceSHA256']
        assert raw==(ROOT/'plan/prosperity'/variant['url']).read_bytes()==(ROOT/'plan/prosperity'/variant['packagedURL']).read_bytes()
assert 'refinement-v72' not in preview_text and 'mini.di-' not in preview_text
literal=set()
for path in (ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity').rglob('*.java'):
    for key in re.findall(r'"gtsr:([a-z][a-z0-9_.-]+)"',path.read_text(encoding='utf-8')):
        if not key.endswith('.') and key.split('.')[0] in ('king','colossus','hive','entity','spawner','oathguard','creature','echo','mini','scene','silent_king'):literal.add(key)
assert literal<=set(registry),str(literal-set(registry))
assert len(oathplate)==8
result={'result':'PASS','assets':len(used),'keys':len(registry),'previewEvents':len(preview['events']),'manifestAssets':len(pins),'sampleInputs':0,'identities':39,'javaLiteralKeys':len(literal),'orphans':0,'worstResampledPeakDb':worst,'music':music,'oathplate':oathplate,'subjectiveAudition':'NOT_PERFORMED','userAcceptance':'PENDING'}
(ROOT/'temp/synthetic-audio-v73/independent-audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
