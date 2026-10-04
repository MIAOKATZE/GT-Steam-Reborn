"""Publish v72 Boss skills, mechanical spawner and three separated ecology sound sets."""
import io,json
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical
from combat_audio_refine import ROOT,AUTHORED,LIB,SR,sha,bp,put,encode,modal
from combat_audio_boss_layers import layer_recipe
from combat_audio_families import freeze_family_sources,family_recipe

OUT=ROOT/'temp/refinement-v72/audio/boss-production'
PRODUCTION=ROOT/'src/main/resources/assets/gtsr/sounds/combat'

def source_set():
    s=freeze_family_sources();name='bird_chirp.wav';p=LIB/'nature/Audio'/name;raw=p.read_bytes();dest=AUTHORED/'original/nature_bird_chirp.wav'
    if dest.exists():assert dest.read_bytes()==raw
    else:dest.write_bytes(raw)
    license_data=(LIB/'nature/License.txt').read_bytes();(AUTHORED/'original/nature_License.txt').write_bytes(license_data)
    manifest={'file':dest.name,'originalPath':str(p),'sha256':sha(raw),'license':'CC0-1.0','author':'syncopika','sourcePage':'https://opengameart.org/content/bird-chirping-sounds','licenseSha256':sha(license_data)}
    text=json.dumps(manifest,indent=2)+'\n';pin=AUTHORED/'bird-source.json'
    if pin.exists():assert pin.read_text()==text
    else:pin.write_text(text)
    x,rate=sf.read(io.BytesIO(raw),always_2d=True);x=signal.resample_poly(x.mean(axis=1),SR,rate)
    # Take one contiguous 0.27s energetic bird gesture, not the full ambient recording.
    window=int(.27*SR);energy=np.convolve(x*x,np.ones(window)/window,mode='same');at=max(0,int(energy.argmax())-window//2)
    s[name]=x[at:at+window]/max(abs(x[at:at+window]));return s

def finish(x,event):
    x-=x.mean();ramp=min(320,len(x)//8);x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2;x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    x*=10**((-11 if event=='idle' else -7)/20)/max(np.max(abs(x)),1e-9);x[0]=x[-1]=0
    return np.round(x*32767).astype(np.int16).astype(float)/32768

def effect(key,s):
    family,event=key.split('.',1);seed=int(sha((key+'.skill-v72').encode())[:8],16);rng=np.random.default_rng(seed)
    if key=='entity.do-02.birth':
        return family_recipe('do-02','idle',s)[0]
    duration=1.25 if event in ('phase','meteor','unlock','recharge') else .70 if event not in ('idle','hurt') else .48
    n=int(duration*SR);t=np.arange(n)/SR;x=np.zeros(n);noise=rng.normal(0,1,n)
    if family=='king' and event in ('idle','hurt','death','attack','chant','warning','phase','silence'):
        use=event if event in ('idle','hurt','death','attack','chant') else 'chant'
        return layer_recipe('king',use,s)[0]
    if family in ('colossus','hive') and event in ('idle','hurt','death','attack','slam','summon'):
        return layer_recipe(family,event,s)[0]
    if family=='creature' and event.startswith('gear_pigeon.'):
        e=event.rsplit('.',1)[1];x=np.zeros(int((.4 if e!='death' else .65)*SR))
        bird=signal.resample_poly(s['bird_chirp.wav'],5,4)
        put(x,bp(bird,250,4200),0,.55)
        put(x,bp(s['impactMetal_001.ogg'],2200,6500),.14,.04)
        if e=='death':x*=np.exp(-np.arange(len(x))/SR*2)
        return finish(x,e)
    if family=='creature' and event.startswith('steam_firefly.'):
        e=event.rsplit('.',1)[1];n=int(.30*SR);t=np.arange(n)/SR
        x=bp(rng.normal(0,1,n),2200,6500)*np.sin(np.pi*t/.3)**2
        return finish(x,e)*.32
    if family=='creature' and event.startswith('slag_ridge_hunter.'):
        return family_recipe('dr-01',event.rsplit('.',1)[1],s)[0]
    if family in ('oathguard','creature'):
        return family_recipe('dr-09',event,s)[0]
    if event in ('impact','explode','slam','stomp','crush') or key=='echo.projectile.explode':
        put(x,s['impactPlate_heavy_001.ogg'],0,.4)
        put(x,bp(s['impactMining_002.ogg'],250,7000),.025,.5)
        x+=bp(noise,35,240)*np.exp(-t*7)*.27
        x+=bp(noise,800,6500)*np.exp(-t*15)*.14
    elif family=='spawner':
        hz=210 if event=='spawn' else 430 if event=='unlock' else 320
        x=modal(t,[hz,hz*1.48,hz*2.41],[5,9,14],[.26,.12,.055])
        put(x,bp(s['metalLatch.ogg'],250,5200),0,.22)
        x+=bp(noise,400,2800)*.025*np.sin(np.pi*t/duration)
    elif event in ('charge','launch','pull','meteor','domain'):
        envelope=np.sin(np.pi*t/duration)**1.3
        x=bp(noise,55,2500)*envelope*.18
        put(x,bp(s['engineCircular_002.ogg'],45,900),.03,.2)
        if event in ('launch','domain'):put(x,bp(s['impactWood_light_001.ogg'],90,3700),.045,.22)
    else:
        put(x,bp(s['metalLatch.ogg'],75,3500),.035,.3)
        x+=bp(noise,70,650)*.10*np.sin(np.pi*t/duration)
        x+=modal(t,[109,188,317],[5,9,14],[.12,.06,.03])
    return finish(x,event)

def main():
    s=source_set();OUT.mkdir(parents=True,exist_ok=True)
    regpath=ROOT/'src/main/resources/assets/gtsr/sounds.json';registry=json.loads(regpath.read_text());keys=[]
    for family,events in {'king':'idle hurt death attack chant warning phase crush pull silence domain meteor impact','colossus':'idle hurt death attack phase slam charge stomp','hive':'idle hurt death attack phase summon launch impact','spawner':'spawn unlock recharge','oathguard':'idle hurt death attack','creature':'idle hurt death attack'}.items():
        keys.extend(family+'.'+e for e in events.split())
    keys.append('echo.projectile.explode')
    keys.append('entity.do-02.birth')
    for species in ['gear_pigeon','steam_firefly','slag_ridge_hunter']:
        keys.extend('creature.'+species+'.'+e for e in ['idle','hurt','death']+(['attack'] if species=='slag_ridge_hunter' else []))
    hashes={};rows=[];html=['<!doctype html><meta charset="utf-8"><style>body{font:16px sans-serif;max-width:950px;margin:30px auto}article{padding:12px;border-bottom:1px solid #ccc}</style><h1>v72 Boss技能与分离生态声族</h1>']
    for key in keys:
        x=effect(key,s);ogg=canonical(encode(x,'OGG','VORBIS'));wav=encode(x,'WAV','PCM_16')
        assert ogg==canonical(encode(effect(key,s),'OGG','VORBIS'))
        decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and len(decoded)==len(x)
        peak=max(max(abs(signal.resample_poly(decoded,p,q))) for p,q in [(2,1),(1,1),(1,2)])
        assert peak<10**(-1/20)
        filename=key.replace('.','_');(OUT/(filename+'.ogg')).write_bytes(ogg);(OUT/(filename+'.wav')).write_bytes(wav)
        (PRODUCTION/(filename+'.ogg')).write_bytes(ogg)
        registry[key]={'category':'ambient' if key.startswith('creature.gear') or key.startswith('creature.steam') else 'hostile','sounds':[{'name':'gtsr:combat/'+filename,'stream':False}]}
        hashes[key]={'ogg':sha(ogg),'wav':sha(wav)};rows.append({'key':key,'seconds':len(x)/SR,'peakDb':float(20*np.log10(peak))})
        html.append('<article>'+key+'<br><audio controls preload="metadata" src="'+filename+'.ogg"></audio></article>')
    p=OUT/'sha.json'
    if p.exists():assert json.loads(p.read_text())==hashes
    p.write_text(json.dumps(hashes,indent=2)+'\n');registry_text=json.dumps(registry,indent=2)+'\n';regpath.write_text(registry_text)
    result={'assets':len(rows),'sourcePin':'PASS','doubleEncode':'PASS','crossRun':'PASS' if (OUT/'report.json').exists() else 'FIRST_RUN','actualDecode':'PASS','subjective_audio_audition':'NOT_PERFORMED','entries':rows}
    (OUT/'report.json').write_text(json.dumps(result,indent=2)+'\n');(OUT/'preview.html').write_text(''.join(html),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='entries'}))

if __name__=='__main__':main()
