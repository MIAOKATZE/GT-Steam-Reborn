"""v72 per-shape combat families, generated/published one group at a time.

Original 36 profiles are assigned by authored anatomy, never hashed to a voice.
Each short event has one gesture with body/texture/finite-space components.
"""
import argparse, io, json
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical
from combat_audio_refine import ROOT, AUTHORED, LIB, SR, sha, bp, put, encode, modal
from combat_audio_boss_layers import voice_sources, layer_recipe

OUT=ROOT/'temp/refinement-v72/audio/families'
PRODUCTION=ROOT/'src/main/resources/assets/gtsr/sounds/combat'

# code: material family, exact body recording, size pitch, characteristic resonance Hz
PROFILES={
 'dr-01':('stone','impactMining_001.ogg',.85,172),
 'dr-02':('shell','impactWood_light_002.ogg',1.45,1260),
 'dr-03':('crystal','impactGlass_light_001.ogg',.88,740),
 'dr-04':('shell','clothBelt.ogg',1.3,1510),
 'dr-05':('shell','impactWood_light_001.ogg',1.1,820),
 'dr-06':('fiber','clothBelt2.ogg',1.55,1450),
 'dr-07':('shell','impactPlate_light_001.ogg',1.35,1040),
 'dr-08':('metal','metalPot2.ogg',.92,211),
 'dr-09':('armor','metalLatch.ogg',.90,335),
 'dr-10':('fiber','clothBelt.ogg',.84,297),
 'dr-11':('prayer','male_wrong.ogg',1.03,224),
 'dr-12':('cinder','impactMining_003.ogg',1.15,291),
 'dr-13':('membrane','impactSoft_medium_001.ogg',.78,188),
 'dr-14':('membrane','impactGlass_light_002.ogg',1.28,481),
 'dr-15':('air','forceField_000.ogg',1.34,1170),
 'dr-16':('stone','impactMining_002.ogg',1.18,318),
 'dr-17':('wood','creak1.ogg',1.2,408),
 'dr-18':('rotor','engineCircular_001.ogg',1.1,208),
 'dr-19':('air','clothBelt2.ogg',.75,590),
 'dr-20':('bell','metalPot1.ogg',.85,174),
 'di-01':('pressure','forceField_003.ogg',.82,91),
 'di-02':('stone','impactMining_004.ogg',.68,113),
 'di-03':('metal','metalPot3.ogg',1.15,542),
 'di-04':('beast','male_war_medic.ogg',.9,183),
 'di-05':('fiber','clothBelt2.ogg',.72,426),
 'di-06':('wood','creak2.ogg',.79,264),
 'di-07':('beast','male_wrong.ogg',.65,134),
 'di-08':('string','drawKnife1.ogg',1.08,810),
 'di-09':('metal','metalLatch.ogg',.74,236),
 'di-10':('armor','impactMetal_medium_002.ogg',.86,377),
 'di-11':('electric','forceField_001.ogg',1.14,655),
 'di-12':('air','clothBelt.ogg',.68,317),
 'di-13':('bell','metalPot3.ogg',.64,109),
 'di-14':('wood','creak3.ogg',.93,318),
 'di-15':('string','creak1.ogg',1.3,568),
 'do-02':('pressure','forceField_004.ogg',.69,68),
}

EXTRA_SOURCES={
 'impact':['impactWood_light_002.ogg','impactPlate_light_001.ogg','impactSoft_medium_001.ogg',
   'impactMining_001.ogg','impactMining_002.ogg','impactMining_003.ogg','impactMining_004.ogg',
   'impactGlass_light_001.ogg','impactGlass_light_002.ogg','impactMetal_medium_002.ogg'],
 'rpg':['clothBelt.ogg','clothBelt2.ogg','metalLatch.ogg','metalPot1.ogg','metalPot2.ogg','metalPot3.ogg',
        'creak1.ogg','creak2.ogg','creak3.ogg','drawKnife1.ogg','knifeSlice.ogg'],
 'scifi':['forceField_000.ogg','forceField_001.ogg','forceField_003.ogg','forceField_004.ogg','engineCircular_001.ogg'],
}

def freeze_family_sources():
    sources=voice_sources();rows=[];folder=AUTHORED/'original'
    for pack,names in EXTRA_SOURCES.items():
        for name in names:
            src=LIB/pack/'Audio'/name;data=src.read_bytes();p=folder/(pack+'_'+name)
            if p.exists():assert p.read_bytes()==data,'Pinned family source changed'
            else:p.write_bytes(data)
            x,rate=sf.read(io.BytesIO(data),always_2d=True)
            x=signal.resample_poly(x.mean(axis=1),SR,rate);x/=max(np.max(np.abs(x)),1e-9);sources[name]=x
            rows.append({'file':p.name,'originalPath':str(src),'sha256':sha(data),'license':'CC0-1.0',
                'sourcePage':'https://kenney.nl/assets/'+{'rpg':'rpg-audio','impact':'impact-sounds','scifi':'sci-fi-sounds'}[pack]})
        (folder/(pack+'_License.txt')).write_bytes((LIB/pack/'License.txt').read_bytes())
    p=AUTHORED/'family-sources.json';body=json.dumps({'sources':rows},indent=2)+'\n'
    if p.exists():assert p.read_text()==body
    else:p.write_text(body)
    return sources

def family_recipe(code,event,source):
    material,name,pitch,hz=PROFILES[code]
    seed=int(sha((code+'.'+event+'.v72').encode())[:8],16);rng=np.random.default_rng(seed)
    size=.85 if code.startswith('dr-') else 1.2
    duration={'idle':.50,'hurt':.32,'death':.90,'attack':.55}[event]*size
    if material in ('shell','fiber'):duration*=.82
    if material in ('bell','pressure'):duration*=1.22
    n=int(duration*SR);t=np.arange(n)/SR;noise=rng.normal(0,1,n);x=np.zeros(n)
    physical=signal.resample_poly(source[name],100,round(pitch*100))
    if event=='idle':physical=physical[:int(.27*SR)]
    if material in ('prayer','beast'):
        # One contiguous reversed word, shaded into a short breath/nasal body.
        physical=signal.resample(physical[::-1],int(n*.75))
        put(x,bp(physical,60,900 if material=='beast' else 1750),.02,.55)
        env=signal.sosfilt(signal.butter(2,25,fs=SR,output='sos'),np.abs(x));env/=max(env.max(),1e-9)
        x+=bp(noise,900,3000)*env*.035
        if material=='beast':x+=bp(noise,60,240)*env*.13
    elif material=='rotor':
        speed=hz*(1+.65*t/duration) if event=='attack' else hz*(1-.7*t/duration) if event=='death' else hz*(1+.06*np.sin(np.pi*t/duration))
        phase=2*np.pi*np.cumsum(speed)/SR
        x+=sum(np.sin(phase*h)/h**1.2 for h in (1,2,3,5))*.18*np.sin(np.pi*t/duration)**.75
        x+=bp(noise,1400,5700)*.024*np.sin(np.pi*t/duration)
        put(x,bp(physical,180,3900),0,.12)
    elif material in ('air','pressure','membrane'):
        shape=np.sin(np.pi*t/duration)**1.2
        x+=bp(noise,max(35,hz*.35),min(3500,hz*4))*shape*(.17 if material=='pressure' else .1)
        x+=bp(noise,1200,4800)*shape*.015
        put(x,bp(physical,130,3000),.03,.13 if event=='idle' else .22)
        if material=='membrane':x+=modal(t,[hz,hz*1.67],[6,10],[.1,.05])*shape
    elif material=='bell':
        x+=modal(t,[hz,hz*1.48,hz*2.27,hz*3.6],[4,6,10,16],[.43,.22,.085,.026])
        put(x,bp(physical,500,4800),0,.07)
        x+=bp(noise,900,5500)*.025*np.exp(-t*50)
    elif material=='electric':
        put(x,bp(physical,100,4100),0,.28)
        x+=bp(noise,1900,7500)*.1*np.sin(np.pi*t/duration)**1.5
        x+=bp(noise,50,240)*.10*np.sin(np.pi*t/duration)
    elif material=='string':
        put(x,bp(physical,180,4800),0,.45)
        x+=modal(t,[hz,hz*2,hz*3.9],[10,18,25],[.17,.06,.03])
        x+=bp(noise,1300,6500)*.03*np.exp(-t*25)
    else:
        put(x,bp(physical,100 if material in ('shell','fiber') else 40,6000),0,.55)
        x+=bp(noise,1500,6200)*.03*np.sin(np.pi*t/duration)**2
        if material in ('metal','armor','stone','wood'):
            x+=modal(t,[hz,hz*1.6,hz*2.41],[6,11,18],[.11,.04,.015])
        if material=='cinder':x+=bp(noise,600,5500)*.10*np.exp(-t*5)
        if material=='fiber':x+=bp(noise,550,3800)*.055*np.sin(np.pi*t/duration)**.9
    if event=='attack':
        if material in ('armor','string','beast'):put(x,bp(source['knifeSlice.ogg'],600,6500),.04,.34)
        elif material not in ('rotor','bell'):x+=bp(noise,100,700)*.05*np.exp(-t*10)
    if event=='hurt':x*=np.exp(-t*3)
    if event=='death':x*=np.exp(-t*1.2)
    dry=x.copy();put(x,bp(dry,80,1700),.077,.065 if code.startswith('dr') else .11)
    x-=x.mean();ramp=min(240,n//8);x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    target=-10 if event=='idle' else -7
    x*=10**(target/20)/max(np.max(np.abs(x)),1e-9);x[0]=x[-1]=0
    return np.round(x*32767).astype(np.int16).astype(float)/32768,seed

def run(group,publish):
    source=freeze_family_sources();OUT.mkdir(parents=True,exist_ok=True)
    names=[c for c,p in PROFILES.items() if group=='all' or p[0]==group]
    assert names,'Empty material group'
    folder=OUT/group;folder.mkdir(exist_ok=True)
    sha_rows={};rows=[]
    registration=json.loads((ROOT/'src/main/resources/assets/gtsr/sounds.json').read_text())
    html=['<!doctype html><meta charset="utf-8"><style>body{font:16px sans-serif;max-width:900px;margin:30px auto}article{padding:10px;border-bottom:1px solid #ccc}</style><h1>'+group+' 材质组</h1><p>角色按具体rig指定材质与原始录音；短单声势，保留受伤/死亡/攻击区别。代理未自行试听。</p>']
    for code in names:
        material,body,pitch,hz=PROFILES[code]
        for event in ('idle','hurt','death','attack'):
            key='entity.'+code+'.'+event;x,seed=family_recipe(code,event,source)
            ogg=canonical(encode(x,'OGG','VORBIS'));wav=encode(x,'WAV','PCM_16')
            assert ogg==canonical(encode(family_recipe(code,event,source)[0],'OGG','VORBIS'))
            decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and len(decoded)==len(x)
            peaks=[max(abs(signal.resample_poly(decoded,p,q))) for p,q in [(2,1),(1,1),(1,2)]]
            assert max(peaks)<10**(-1/20) and x[0]==x[-1]==0
            filename=key.replace('.','_');(folder/(filename+'.ogg')).write_bytes(ogg);(folder/(filename+'.wav')).write_bytes(wav)
            sha_rows[key]={'wav':sha(wav),'ogg':sha(ogg)}
            rows.append({'key':key,'material':material,'recording':body,'bodyPitch':pitch,'resonance':hz,'seed':seed,'seconds':len(x)/SR,'peakDb':float(20*np.log10(max(peaks)))})
            html.append('<article>'+key+' · '+body+'<br><audio controls preload="metadata" src="'+filename+'.ogg"></audio></article>')
            if publish:
                (PRODUCTION/(filename+'.ogg')).write_bytes(ogg)
                registration[key]={'category':'hostile','sounds':[{'name':'gtsr:combat/'+filename,'stream':False}]}
    sidecar=folder/'sha.json'
    if sidecar.exists():assert json.loads(sidecar.read_text())==sha_rows
    sidecar.write_text(json.dumps(sha_rows,indent=2)+'\n')
    result={'group':group,'profiles':len(names),'assets':len(rows),'publish':publish,'sourcePin':'PASS','doubleEncode':'PASS','crossRun':'PASS' if (folder/'report.json').exists() else 'FIRST_RUN','decode':'PASS','subjective_audio_audition':'NOT_PERFORMED','entries':rows}
    (folder/'report.json').write_text(json.dumps(result,indent=2)+'\n');(folder/'preview.html').write_text(''.join(html),encoding='utf-8')
    if publish:(ROOT/'src/main/resources/assets/gtsr/sounds.json').write_text(json.dumps(registration,indent=2)+'\n')
    (AUTHORED/'profiles.json').write_text(json.dumps({k:{'material':v[0],'recording':v[1],'pitch':v[2],'resonanceHz':v[3]} for k,v in PROFILES.items()},indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='entries'}))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--group',required=True);parser.add_argument('--publish',action='store_true');args=parser.parse_args();run(args.group,args.publish)
