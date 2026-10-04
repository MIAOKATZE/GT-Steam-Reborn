"""Ten anatomy-specific mini-Boss roles. Publish only with an explicit canonical map."""
import argparse,io,json
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical
from combat_audio_refine import ROOT,AUTHORED,SR,sha,bp,put,encode,modal
from combat_audio_families import freeze_family_sources

OUT=ROOT/'temp/refinement-v72/audio/mini-roles'
PRODUCTION=ROOT/'src/main/resources/assets/gtsr/sounds/combat'
ROLES={
 'pressure':('metalLatch.ogg','impactPlate_heavy_001.ogg'),
 'return':('engineCircular_001.ogg','drawKnife1.ogg'),
 'weft':('clothBelt2.ogg','impactMetal_001.ogg'),
 'tide':('impactSoft_medium_001.ogg','forceField_000.ogg'),
 'parallax':('forceField_003.ogg','impactGlass_light_001.ogg'),
 'oath':('impactPlate_light_001.ogg','knifeSlice.ogg'),
 'ash':('creak3.ogg','impactMining_003.ogg'),
 'counterbeat':('metalLatch.ogg','metalPot3.ogg'),
 'route':('metalPot2.ogg','metalLatch.ogg'),
 'anchor':('metalPot1.ogg','impactPlate_heavy_001.ogg'),
}

def recipe(role,event,s):
    seed=int(sha((role+'.'+event+'.v72mini').encode())[:8],16);rng=np.random.default_rng(seed)
    seconds={'idle':.39,'hurt':.30,'death':1.15,'windup':.72,'release':.78}[event]
    if role=='counterbeat' and event=='release':seconds=.72
    n=int(seconds*SR);t=np.arange(n)/SR;noise=rng.normal(0,1,n)
    body=np.zeros(n);texture=np.zeros(n);a,b=ROLES[role]
    if event in ('idle','hurt','death'):
        physical=s[a] if event=='idle' else s[b]
        if event=='idle':physical=physical[:int(.22*SR)]
        put(body,bp(physical,90 if role not in ('anchor','pressure') else 40,3300),0,.45)
        texture+=bp(noise,700,4700)*.022*np.sin(np.pi*t/seconds)**2
        if event=='death':
            body*=np.exp(-t*1.5)
            put(body,bp(s['impactMining_001.ogg'],80,1100),.13,.12)
    elif role=='pressure':
        if event=='windup':
            put(texture,s[a],.03,.25)
            body+=bp(noise,40,700)*.19*np.sin(np.pi*t/seconds)**1.3
        else:
            put(body,bp(s[b],35,1800),0,.57)
            body+=bp(noise,32,180)*.28*np.exp(-t*8)
            texture+=bp(noise,850,4700)*.055*np.exp(-t*11)
    elif role=='return':
        put(body,bp(s[a][::-1] if event=='windup' else s[a],90,2800),0,.25)
        put(texture,bp(s[b][::-1] if event=='windup' else s[b],500,5800),.05,.28)
        body+=bp(noise,65,480)*.07*np.sin(np.pi*t/seconds)
    elif role=='weft':
        put(texture,bp(s[a],400,4100),0,.15)
        if event=='windup':
            body+=modal(t,[463,987,1661],[9,15,22],[.19,.09,.025])
        else:
            pluck=modal(t,[527,903,1597],[13,19,30],[.19,.095,.025])
            put(body,pluck,0,1);put(body,pluck,.145,.63)
            put(texture,bp(s[b],1300,6700),.018,.10)
    elif role=='tide':
        curve=np.sin(np.pi*t/seconds)**1.5
        body+=bp(noise,40,480)*curve*.21
        texture+=bp(noise,600,4700)*curve*.09
        put(body,bp(s[a],65,1600),.02,.17)
        if event=='release':
            put(texture,bp(noise,1100,5400)*curve,.12,.04)
    elif role=='parallax':
        put(body,bp(s[a],320,4000),0,.25)
        put(texture,bp(s[b],900,6700),.055,.25)
        if event=='release':
            edge=np.sin(np.pi*t/seconds)**1.2
            body+=bp(noise,150,750)*edge*.07
            texture+=bp(noise,1800,7200)*edge*.06
    elif role=='oath':
        if event=='windup':
            put(body,bp(s[a],55,1900),0,.40)
            put(texture,bp(s['metalLatch.ogg'],850,4700),.025,.13)
        else:
            put(texture,bp(s[b],250,6500),0,.60)
            put(body,bp(s[a],80,1500),.16,.12)
    elif role=='ash':
        if event=='windup':put(body,bp(s[a],70,3600),0,.45)
        else:put(texture,bp(s[b],500,6100),0,.35)
        body+=bp(noise,90,550)*.12*np.sin(np.pi*t/seconds)**.7
        texture+=bp(noise,1300,5800)*.065*np.sin(np.pi*t/seconds)**1.4
    elif role=='counterbeat':
        if event=='windup':put(texture,bp(s[a],250,5500),0,.35)
        else:
            # The module schedules two release calls at ticks32/54. Each cue is one
            # immediate strike so damage and audio align, avoiding four total bells.
            bell_t=np.arange(int(.6*SR))/SR
            bell=modal(bell_t,[72,131,217,393],[5,7,13,19],[.35,.18,.07,.02])
            put(body,bell,.018,1)
            put(texture,bp(s[b],190,3800),.018,.11)
    elif role=='route':
        if event=='windup':put(body,bp(s[a],100,3800),0,.35)
        else:
            put(body,bp(s[a],80,1500),0,.25)
            put(texture,bp(s[b],600,5900),.06,.43)
            put(texture,bp(s['impactPlate_light_001.ogg'],350,4700),.19,.2)
    elif role=='anchor':
        if event=='windup':put(texture,bp(s[a],400,3700),0,.33)
        else:
            put(body,bp(s[b],30,1100),0,.53)
            body+=bp(noise,30,165)*.22*np.exp(-t*7)
            put(texture,bp(s['metalLatch.ogg'],1800,6500),.055,.11)
    dry=body+texture;space=np.zeros(n)
    for at,gain in [(.089,.075),(.151,.04)]:put(space,bp(dry,80,1400),at,gain)
    x=body+texture+space;x-=x.mean();ramp=240
    x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    x*=10**((-11 if event=='idle' else -7)/20)/max(np.max(abs(x)),1e-9);x[0]=x[-1]=0
    return np.round(x*32767).astype(np.int16).astype(float)/32768,seed

def main(codes_path,publish):
    codes=json.loads(Path(codes_path).read_text(encoding='utf-8')) if codes_path else {}
    if publish:assert set(codes)==set(ROLES) and len(set(codes.values()))==10,'Explicit complete canonical map required'
    source=freeze_family_sources();OUT.mkdir(parents=True,exist_ok=True)
    registry_path=ROOT/'src/main/resources/assets/gtsr/sounds.json';registry=json.loads(registry_path.read_text())
    fingerprints={};rows=[];html=['<!doctype html><meta charset="utf-8"><style>body{font:16px sans-serif;max-width:950px;margin:30px auto}article{padding:12px;border-bottom:1px solid #ccc}</style><h1>十小Boss 道具与技能声族</h1><p>各角色独立道具配方，非统一打击声改音高。没有代理人工听感验收。</p>']
    for role in ROLES:
        for event in ['idle','hurt','death','windup','release']:
            x,seed=recipe(role,event,source);ogg=canonical(encode(x,'OGG','VORBIS'));wav=encode(x,'WAV','PCM_16')
            assert ogg==canonical(encode(recipe(role,event,source)[0],'OGG','VORBIS'))
            decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and len(decoded)==len(x)
            peak=max(max(abs(signal.resample_poly(decoded,p,q))) for p,q in [(1,2),(1,1),(2,1)])
            assert peak<10**(-1/20)
            key='mini.'+codes.get(role,role)+'.'+event;name=key.replace('.','_')
            (OUT/(name+'.ogg')).write_bytes(ogg);(OUT/(name+'.wav')).write_bytes(wav)
            fingerprints[key]={'ogg':sha(ogg),'wav':sha(wav)}
            rows.append({'key':key,'role':role,'bodySource':ROLES[role][0],'textureSource':ROLES[role][1],'seconds':len(x)/SR,'seed':seed,'peakDb':float(20*np.log10(peak))})
            html.append('<article>'+role+' / '+event+'<br><audio controls preload="metadata" src="'+name+'.ogg"></audio></article>')
            if publish:
                (PRODUCTION/(name+'.ogg')).write_bytes(ogg)
                registry[key]={'category':'hostile','sounds':[{'name':'gtsr:combat/'+name,'stream':False}]}
    # Preview role IDs and canonical production IDs have separate frozen fingerprints.
    sidecar=OUT/('sha-production-v2.json' if publish else 'sha-roles-v2.json')
    if sidecar.exists():assert json.loads(sidecar.read_text())==fingerprints
    sidecar.write_text(json.dumps(fingerprints,indent=2)+'\n')
    report={'revision':2,'assets':len(rows),'publish':publish,'canonicalMap':codes,'sourcePin':'PASS','doubleEncode':'PASS','crossRun':'PASS' if (OUT/('report-production-v2.json' if publish else 'report-roles-v2.json')).exists() else 'FIRST_RUN','decode':'PASS','subjective_audio_audition':'NOT_PERFORMED','entries':rows}
    (OUT/('report-production-v2.json' if publish else 'report-roles-v2.json')).write_text(json.dumps(report,indent=2)+'\n')
    (OUT/'preview.html').write_text(''.join(html),encoding='utf-8')
    if publish:registry_path.write_text(json.dumps(registry,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='entries'}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--codes');p.add_argument('--publish',action='store_true');a=p.parse_args();main(a.codes,a.publish)
