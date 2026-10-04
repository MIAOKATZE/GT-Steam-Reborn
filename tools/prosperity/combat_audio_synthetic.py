"""v73 production: deterministic mathematical synthesis, zero sampled audio inputs.

Only oscillators, inharmonic FM, seeded noise filters, modal resonances and finite
delays generate audio. No human/animal recordings, speech/formant/glottal models,
or historical audio recipes are imported. Historical v71/v72 sources are archives.
Run twice; the second run verifies all encoded SHA256s before publishing.
"""
from pathlib import Path
import hashlib, io, json, re, struct
import numpy as np
import soundfile as sf
from scipy import signal

ROOT = Path(__file__).resolve().parents[2]
SR = 32000
ASSETS = ROOT / 'src/main/resources/assets/gtsr'
AUTHORED = ROOT / 'plan/model/authored/audio/synthetic-v73'
EVIDENCE = ROOT / 'temp/synthetic-audio-v73'
MEDIA = ROOT / 'plan/prosperity/media/audio/synthetic-v73'

# Explicit body design, resonance and relative scale: identities are never hashed.
PROFILES = {
 'dr-01':('stone',172,.85), 'dr-02':('shell',1260,.62),
 'dr-03':('crystal',740,.9), 'dr-04':('fiber',1510,.55),
 'dr-05':('shell',820,.72), 'dr-06':('fiber',1450,.6),
 'dr-07':('shell',1040,.7), 'dr-08':('metal',211,.88),
 'dr-09':('oathplate',335,.92), 'dr-10':('fiber',297,.96),
 'dr-11':('rune',224,.95), 'dr-12':('cinder',291,.9),
 'dr-13':('membrane',188,1.05), 'dr-14':('membrane',481,.8),
 'dr-15':('air',1170,.68), 'dr-16':('stone',318,.86),
 'dr-17':('wood',408,.84), 'dr-18':('rotor',208,.91),
 'dr-19':('air',590,.98), 'dr-20':('bell',174,1.08),
 'di-01':('pressure',91,1.12), 'di-02':('stone',113,1.2),
 'di-03':('metal',542,.88), 'di-04':('rift',1370,.69),
 'di-05':('fiber',426,1.0), 'di-06':('wood',264,1.05),
 'di-07':('magnetic',78,1.25), 'di-08':('string',810,.85),
 'di-09':('metal',236,1.1), 'di-10':('armor',377,1.05),
 'di-11':('electric',655,.84), 'di-12':('air',317,1.1),
 'di-13':('bell',109,1.23), 'di-14':('wood',318,.96),
 'di-15':('string',568,.89), 'do-02':('pressure',68,1.3),
 'dc-10':('rune',83,1.4), 'dc-02':('machine',64,1.4),
 'dc-08':('membrane',97,1.35),
}
DESCRIPTIONS = {
 'rune':'低沉电子符文、非谐波共振与软噪擦动',
 'rift':'短促尖锐电子空间撕裂与瞬态滤噪',
 'magnetic':'低腔厚重磁性共振、缓慢场弯曲',
 'machine':'机械冲击、装甲非谐波共振与轴承噪声',
 'membrane':'陌生噪声膜腔、宽带压力弯曲',
 'pressure':'低频压强腔与压缩释放', 'stone':'颗粒冲击与低频矿石共振',
 'shell':'短壳体击发与细碎电子纹理', 'fiber':'纤维滤噪擦动与稀疏共振',
 'crystal':'晶体非整数模态碰撞', 'metal':'金属非整数模态与噪声击发',
 'armor':'装甲闩击与沉重共振', 'cinder':'灰烬颗粒滤噪与低频裂解',
 'oathplate':'残誓兵短甲片/甲盾/兵器击发，固定失谐阻尼共振与窄带噪声；死亡最多两次短装甲落定，无滑音或持续喉音',
 'air':'空间气流滤噪与相位扫掠', 'wood':'木质失谐模态与短促摩擦',
 'rotor':'转子相位加速与轴承滤噪', 'bell':'非谐波钟体短击',
 'string':'张弦失谐共振与锐利电子拉擦', 'electric':'非谐波FM与电弧滤噪',
 'return':'回流反向包络与空间回折', 'weft':'织纹双模态短拨',
 'tide':'单次压潮弯曲与宽带涌流', 'parallax':'视差失谐FM与空间裂纹',
 'counterbeat':'单次低钟撞击（游戏负责两次触发）',
 'route':'路径切换的双短手势', 'anchor':'低频锚定撞击与束缚场',
}

def sha(raw): return hashlib.sha256(raw).hexdigest()

def canonical(raw):
    table=[]
    for v in range(256):
        crc=v << 24
        for _ in range(8): crc=((crc<<1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xffffffff
        table.append(crc)
    data=bytearray(raw);at=0
    while at<len(data):
        assert data[at:at+4]==b'OggS'
        count=data[at+26];size=27+count+sum(data[at+27:at+27+count])
        struct.pack_into('<I',data,at+14,0x474F544F);data[at+22:at+26]=b'\0'*4;crc=0
        for value in data[at:at+size]: crc=((crc<<8)&0xffffffff)^table[((crc>>24)^value)&255]
        struct.pack_into('<I',data,at+22,crc);at+=size
    assert at==len(data)
    return bytes(data)

def encode(x,format,subtype):
    # libsndfile 1.2.2 on this Windows host exits inside a single 1M-frame OGG
    # write. Bounded block writes keep the Vorbis encoder stable for 32s music.
    memory=io.BytesIO()
    with sf.SoundFile(memory,mode='w',samplerate=SR,channels=1,format=format,subtype=subtype) as output:
        for at in range(0,len(x),8192):output.write(x[at:at+8192])
    return memory.getvalue()

def bp(x,lo,hi):
    return signal.sosfilt(signal.butter(3,[max(22,lo),min(10500,hi)],btype='bandpass',fs=SR,output='sos'),x)

def add(x,y,at,gain):
    start=int(at*SR);count=min(len(y),len(x)-start)
    if count>0:x[start:start+count]+=y[:count]*gain

def identity(key):
    parts=key.split('.');family=parts[0];event=parts[-1]
    if family in ('entity','mini'):code=parts[1]
    else:code={'king':'dc-10','colossus':'dc-02','hive':'dc-08','oathguard':'dr-09'}.get(family)
    if family=='creature':
        code='dr-01' if 'slag_ridge_hunter' in key else None
        if 'gear_pigeon' in key:return 'gear_pigeon','rotor',690,.6,event
        if 'steam_firefly' in key:return 'steam_firefly','electric',2430,.43,event
    if code:return code,*PROFILES[code],event
    return family,{'spawner':'machine','echo':'electric','creature':'membrane'}.get(family,'armor'),{'spawner':230,'echo':310,'creature':181}.get(family,335),.95,event

def synth_oathplate(key,event):
    """Finite fixed-frequency armor contacts: no chirp/FM/pulse/vocal tail."""
    seconds={'idle':.18,'hurt':.24,'attack':.34,'death':.59}.get(event,.34)
    n=int(seconds*SR);x=np.zeros(n)
    seed=int(sha((key+'.oathplate-r2').encode())[:16],16);rng=np.random.default_rng(seed)
    # Death is two separate armor settles, not a syllabic or pulsed oscillator.
    contacts=[(0,.22,1),(.245,.19,.58)] if event=='death' else [(0,seconds,1)]
    modes={'idle':[183,347,619],'hurt':[112,271,503,941],
           'attack':[169,433,971,1733],'death':[137,319,683,1129]}.get(event,[169,433,971,1733])
    for at,duration,gain in contacts:
        t=np.arange(int(duration*SR))/SR;attack=1-np.exp(-t*900)
        body=np.zeros(len(t))
        for i,hz in enumerate(modes):
            body+=np.sin(2*np.pi*hz*t+.17*i)*np.exp(-t*(24+i*15))*attack*.23/(i+1)
        noise=rng.normal(size=len(t))
        texture=bp(noise,270 if event!='attack' else 900,2300 if event!='attack' else 4100)*attack*np.exp(-t*(65 if event=='idle' else 38))*.18
        chunk=body+texture
        chunk[-160:]*=np.cos(np.linspace(0,np.pi/2,160))**2
        add(x,chunk,at,gain)
    # One quiet reflection is short and damped; no rhythmic or long voiced tail.
    add(x,bp(x.copy(),200,1300),.039,.025)
    x-=x.mean();ramp=80
    x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-160:]*=np.cos(np.linspace(0,np.pi/2,160))**2
    target=-16 if event=='idle' else -10 if event=='death' else -9
    x*=10**(target/20)/max(abs(x));x[0]=x[-1]=0
    x=np.round(x*32767).astype(np.int16).astype(float)/32768
    return x,{'identity':'dr-09','material':'oathplate','resonanceHz':335,'scale':.92,'seed':seed,'event':event,'seconds':seconds,'gestures':len(contacts),'recipeBranch':'oathplate-r2','fixedModalHz':modes,'contacts':contacts,'pitchMotion':'none','fm':'none','normalRandomPitchRange':[.8,1.2],'peakTargetDb':target}

def synth(key):
    code,kind,hz,size,event=identity(key)
    if kind=='oathplate':return synth_oathplate(key,event)
    seconds={'idle':.43,'hurt':.29,'death':.88,'attack':.48,'windup':.64,'release':.66,'chant':.76,'warning':.54,'phase':.84,'silence':.72,'domain':.81,'meteor':.83,'pull':.63,'summon':.74,'slam':.62,'stomp':.5,'crush':.63,'charge':.69,'launch':.47,'impact':.45,'explode':.57,'birth':.65,'spawn':.5,'unlock':.7,'recharge':.64}.get(event,.55)*size
    n=int(SR*seconds);t=np.arange(n)/SR;u=t/seconds
    seed=int(sha((key+'.synthetic-v73').encode())[:16],16);rng=np.random.default_rng(seed)
    noise=rng.normal(size=n);env=np.sin(np.pi*u)**1.35
    percussive=event in ('hurt','attack','release','slam','stomp','crush','impact','explode','spawn')
    gesture=(1-np.exp(-t*180))*np.exp(-t*(8 if event=='hurt' else 5)/size) if percussive else env
    # Continuous, inharmonic phase bend; no vocal pulse train or vowel filters.
    bend={'death':-.43,'windup':.35,'charge':.41,'release':-.31,'attack':-.22,'hurt':.21,'phase':.23,'warning':.16}.get(event,-.08)
    phase=2*np.pi*np.cumsum(hz*(1+bend*u))/SR
    body=np.zeros(n);texture=np.zeros(n)
    ratios=[1,1.481,2.317,3.723]
    if kind in ('stone','shell','crystal','metal','armor','wood','bell','string','counterbeat','anchor','machine'):
        # Mechanical and material responses use independent, nonharmonic modes.
        ratios={'crystal':[1,1.917,2.831,4.113],'wood':[1,1.37,2.63,3.11],'string':[1,2.013,3.977,5.171],'machine':[1,1.63,2.71,4.13],'counterbeat':[1,1.819,3.014,5.458]}.get(kind,ratios)
        for i,r in enumerate(ratios):body+=np.sin(phase*r+.14*i)*np.exp(-t*(3.1+i*3)/size)*(.37/(1+i)**1.4)
        body*=gesture if not percussive else 1-np.exp(-t*240)
        texture=bp(noise,400 if kind!='crystal' else 1600,6300)*.14*(np.exp(-t*30) if percussive else env)
        body+=bp(noise,35,hz*2.2)*.17*gesture
    elif kind in ('rune','magnetic'):
        ratios=[1,1.397,2.173,3.061] if kind=='rune' else [1,1.219,1.733,2.491]
        for i,r in enumerate(ratios):body+=np.sin(phase*r+.21*np.sin(phase*.417+i))*gesture*(.28/(1+i)**1.5)
        body+=bp(noise,29,270)*.18*gesture
        texture=bp(noise,540,2800)*.11*env*(.7+.3*np.sin(np.pi*u*.73))
    elif kind in ('membrane','pressure','tide','air','fiber','cinder'):
        lo=37 if kind in ('membrane','pressure','tide') else 130
        hi={'membrane':440,'pressure':270,'tide':560,'air':1900,'fiber':2900,'cinder':980}[kind]
        body=bp(noise,lo,hi)*(.5 if kind in ('membrane','tide') else .35)*gesture
        body+=np.sin(phase*1.431+.42*np.sin(phase*.613))*gesture*.09
        texture=bp(noise,hi+170,6400)*.1*env
        if kind=='cinder':texture*=np.maximum(0,np.sin(phase*.047))**3
    elif kind=='rotor':
        body=(np.sin(phase)+.3*np.sin(phase*2.037+.3*np.sin(phase*.19)))*gesture*.29
        texture=bp(noise,1000,5100)*gesture*.09
    else:
        # Electrical tears/FM, broad transient texture, never plain UI beeps.
        fm={'rift':4.3,'electric':2.8,'return':1.4,'weft':.65,'parallax':3.4,'route':1.0}.get(kind,1.9)
        body=np.sin(phase+fm*env*np.sin(phase*1.713))*gesture*.27
        body+=np.sin(phase*1.479+.6*np.sin(phase*.319))*gesture*.11
        texture=bp(noise,1300 if kind=='rift' else 680,7400)*gesture*.2
        body+=bp(noise,70,410)*gesture*.12
    if event in ('windup','charge','pull','recharge'):body*=u**.55;texture*=u**.7
    if event=='death':body*=np.exp(-t*.9);texture*=np.exp(-t*1.4)
    dry=body+texture
    if kind in ('weft','route') and event=='release':add(dry,dry.copy(),.145,.48)
    space=np.zeros(n)
    for delay,gain in [(.067+.012*size,.11),(.131+.015*size,.055),(.211,.025)]:add(space,bp(dry,80,1800),delay,gain)
    x=dry+space;x-=x.mean();ramp=min(320,n//8)
    x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    target=-12 if code=='steam_firefly' else -11 if event=='idle' else -8
    x*=10**(target/20)/max(abs(x));x[0]=x[-1]=0
    x=np.round(x*32767).astype(np.int16).astype(float)/32768
    return x,{'identity':code,'material':kind,'resonanceHz':hz,'scale':size,'seed':seed,'event':event,'seconds':len(x)/SR,'gestures':2 if kind in ('weft','route') and event=='release' else 1}

def synth_music(key):
    seconds=32.0 if key=='silent_king.battle' else 3.464
    n=int(SR*seconds);t=np.arange(n)/SR;u=t/seconds
    seed=int(sha((key+'.synthetic-v73').encode())[:16],16);rng=np.random.default_rng(seed)
    noise=rng.normal(size=n)
    env=np.sin(np.pi*u)**.65
    if key=='scene.entry':
        phase=2*np.pi*np.cumsum(59*(1+.19*np.sin(np.pi*u)))/SR
        x=sum(np.sin(phase*r+.2*i)*np.exp(-t*(.6+.3*i))*.22/(i+1) for i,r in enumerate([1,1.413,2.219,3.719]))
        x+=bp(noise,35,450)*np.exp(-t*.95)*.23+bp(noise,900,3900)*env*.045
    else:
        # Continuous instrumental ritual bed: no voices, chants, vowel filters or
        # vocal articulations. Slow nonharmonic field movement, distant metal modes.
        phase=2*np.pi*43*t
        x=sum(np.sin(phase*r+.43*np.sin(2*np.pi*t/(9+i*3)))*.12/(i+1) for i,r in enumerate([1,1.419,2.137,3.731]))*env
        x+=bp(noise,32,310)*env*.19+bp(noise,650,2900)*env*.024
        for at,hz in [(1.8,137),(9.1,173),(17.6,119),(25.2,151)]:
            z=np.arange(int(SR*4.7))/SR
            mode=sum(np.sin(2*np.pi*hz*r*z)*np.exp(-z*(.9+i*.4))*.07/(i+1) for i,r in enumerate([1,1.617,2.719]))
            mode[:320]*=np.sin(np.linspace(0,np.pi/2,320))**2
            add(x,mode,at,1)
    add(x,bp(x.copy(),70,1300),.183,.12);x-=x.mean()
    ramp=1280;x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    x*=10**(-12/20)/max(abs(x));x[0]=x[-1]=0
    return np.round(x*32767).astype(np.int16).astype(float)/32768,{'identity':key,'material':'instrumental electronic ritual' if seconds==32 else 'electronic entrance','seed':seed,'seconds':seconds,'gestures':'continuous music' if seconds==32 else 1}

def main():
    for directory in [AUTHORED/'source',EVIDENCE,MEDIA/'combat']:directory.mkdir(parents=True,exist_ok=True)
    regpath=ASSETS/'sounds.json';registry=json.loads(regpath.read_text(encoding='utf-8'));registry_hash=sha(regpath.read_bytes())
    # Withdraw the ten unapproved mini identities and all their current audio.
    removed={k for k in registry if k.startswith('mini.')}
    for key in removed:
        for entry in registry[key]['sounds']:
            name=entry if isinstance(entry,str) else entry['name'];target=ASSETS/'sounds'/(name[5:]+'.ogg')
            assert target.resolve().is_relative_to((ASSETS/'sounds/combat').resolve())
            if target.exists():target.unlink()
        del registry[key]
    if removed:
        regpath.write_text(json.dumps(registry,indent=2)+'\n',encoding='utf-8');registry_hash=sha(regpath.read_bytes())
    uses={}
    for key,item in registry.items():
        for entry in item['sounds']:
            name=entry if isinstance(entry,str) else entry['name']
            uses.setdefault(name,[]).append(key)
    production=set((ASSETS/'sounds').rglob('*.ogg'))
    assert {ASSETS/'sounds'/(name[5:]+'.ogg') for name in uses}==production
    assert len(PROFILES)==39 and not any(k.startswith('mini.') for k in registry)
    previous=json.loads((AUTHORED/'manifest.json').read_text(encoding='utf-8')) if (AUTHORED/'manifest.json').exists() else None
    rows=[];by_name={};peak=-100
    for name,keys in sorted(uses.items()):
        key=keys[0];producer=synth_music if name.startswith('gtsr:music/') else synth
        x,detail=producer(key);ogg=canonical(encode(x,'OGG','VORBIS'));wav=encode(x,'WAV','PCM_16')
        x2,_=producer(key);assert np.array_equal(x,x2) and ogg==canonical(encode(x2,'OGG','VORBIS'))
        decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and decoded.ndim==1 and np.isfinite(decoded).all() and len(decoded)==len(x)
        pitches=[.5,.6,.75,.8,.85,.9,1,1.1,1.18,1.2,1.5,2]
        maximum=max(float(np.max(abs(signal.resample_poly(decoded,100,round(p*100))))) for p in pitches)
        assert .02<maximum<10**(-1/20)
        db=float(20*np.log10(maximum));peak=max(peak,db)
        relative=name[5:]+'.ogg';file=Path(relative).name
        row={**detail,'keys':keys,'path':relative,'oggSHA256':sha(ogg),'wavSHA256':sha(wav),'worstResampledPeakDb':db}
        if previous:
            oldrow=next(r for r in previous['assets'] if r['path']==relative)
            if detail.get('recipeBranch')=='oathplate-r2' and oldrow.get('recipeBranch')!='oathplate-r2':
                assert key.startswith(('entity.dr-09.','oathguard.')),'Unexpected recipe migration'
            else:assert oldrow['oggSHA256']==row['oggSHA256']
        (ASSETS/'sounds'/relative).write_bytes(ogg);(AUTHORED/'source'/file).write_bytes(ogg)
        (AUTHORED/'source'/Path(file).with_suffix('.wav')).write_bytes(wav);(MEDIA/relative).parent.mkdir(parents=True,exist_ok=True);(MEDIA/relative).write_bytes(ogg)
        rows.append(row);by_name[name]=row
    previewpath=ROOT/'plan/prosperity/sound-preview-data.js';text=previewpath.read_text(encoding='utf-8')
    prefix='globalThis.PROSPERITY_SOUND_ASSETS=';data=json.loads(text[len(prefix):].strip().removesuffix(';'))
    variants=0
    old={entry['event']:entry for entry in data['events'] if entry['event'] in registry}
    data['events']=[]
    for key,item in registry.items():
        code=identity(key)[0] if item['category']!='music' else 'dc-10' if key=='silent_king.battle' else 'scene'
        entry=old.get(key,{'event':key,'code':code,'category':item['category'],'variants':[]})
        if not entry['variants']:
            for sound in item['sounds']:
                name=sound if isinstance(sound,str) else sound['name']
                entry['variants'].append({'source':'src/main/resources/assets/gtsr/sounds/'+name[5:]+'.ogg','volume':1,'pitch':1})
        data['events'].append(entry)
    for entry in data['events']:
        for variant in entry['variants']:
            src=ROOT/variant['source'];relative=src.relative_to(ASSETS/'sounds').as_posix()
            row=by_name['gtsr:'+relative.removesuffix('.ogg')]
            variant.update(url='media/audio/synthetic-v73/'+relative,packagedURL='media/audio/synthetic-v73/'+relative,sourceSHA256=row['oggSHA256'])
            assert src.read_bytes()==(MEDIA/relative).read_bytes()==(AUTHORED/'source'/src.name).read_bytes();variants+=1
    data['status']='纯电子数学合成 v73；零真人/动物/音效采样输入；尚待用户试听验收'
    data['license']='自主数学合成；配方与SHA清单：plan/model/authored/audio/synthetic-v73；历史素材仅归档，不参与当前生成'
    data['soundsJSONSourceSHA256']=registry_hash
    data['briefs']={code:brief for code,brief in data.get('briefs',{}).items() if code in PROFILES}
    for code,brief in data['briefs'].items():
        brief.update(tone=DESCRIPTIONS[PROFILES[code][0]],events='待机、受击、消散及攻击的独立纯电子手势；主体/纹理/空间三层',length='1–2 次短音色手势；无多音节与笑声')
        if code=='dr-09':brief.update(events='一次低柔甲片接触 / 一次厚实甲盾冲击 / 一次兵器击发 / 最多两次短装甲落定',length='待机0.18s / 受击0.24s / 攻击0.34s / 死亡0.59s；无连续滑音、FM啸叫或脉冲列')
        if code=='do-02':brief['events']='出生、待机、死亡三类纯电子手势；主体/纹理/空间三层'
    data['layers']={code:DESCRIPTIONS[PROFILES[code][0]]+'；主体/滤噪纹理/有限空间延迟，无真人或拟人发声' for code in ('dc-10','dc-02','dc-08')}
    previewpath.write_text(prefix+json.dumps(data,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
    manifest={'revision':'synthetic-v73','producer':'tools/prosperity/combat_audio_synthetic.py','producerSHA256':sha(Path(__file__).read_bytes()),'sampleInputs':[],'humanVoiceInputs':0,'animalVoiceInputs':0,'sampledSoundInputs':0,'speechModels':False,'glottalModels':False,'formantModels':False,'sampleRate':SR,'channels':1,'codec':'Vorbis','identities':len(PROFILES),'profiles':{k:{'material':v[0],'resonanceHz':v[1],'scale':v[2],'description':DESCRIPTIONS[v[0]]} for k,v in PROFILES.items()},'assets':rows}
    (AUTHORED/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    recipe={'generation':'Independent seeded mathematical oscillator/FM/noise/modal/delay producer; no audio file read in synth or generation inputs','history':'combat-v71 and refinement-v72 are immutable historical references only; their sampled sources and old recipes are not imported','gesturePolicy':'one continuous gesture, no syllables/laughter or human-like articulations','layerPolicy':'body + texture + finite-space, entity-specific materials/frequencies/scales and event-dependent envelopes','music':'Entry 3.464s / king instrumental ritual bed 32s also replaced mathematically; streaming registrations retained','audition':'User listening acceptance pending; no subjective agent hearing claim'}
    (AUTHORED/'recipe.json').write_text(json.dumps(recipe,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    assert sha(regpath.read_bytes())==registry_hash
    result={'result':'PASS','productionAssets':len(rows),'combatAssets':sum(r['path'].startswith('combat/') for r in rows),'registeredKeys':len(registry),'previewVariants':variants,'identities':len(PROFILES),'sampleInputs':0,'doubleGenerationSHA':'PASS','crossRunSHA':'PASS' if previous else 'FIRST_RUN','decode':'PASS','sourceProductionPreviewSHA':'PASS','soundsJSONClosure':'PASS','orphans':0,'worstResampledPeakDb':peak,'pitchRatios':pitches,'subjectiveAudition':'NOT_PERFORMED','userAcceptance':'PENDING'}
    (EVIDENCE/'audit.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result))

def export():
    """Public default exporter hook; idempotently regenerate/publish v73 assets."""
    main()

if __name__=='__main__':main()
