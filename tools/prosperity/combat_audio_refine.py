"""v72 first six-family listening samples. Does not change production or v71 sources."""
from pathlib import Path
import hashlib, io, json, shutil
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical

ROOT = Path(__file__).resolve().parents[2]
AUTHORED = ROOT/'plan/model/authored/audio/refinement-v72'
OUT = ROOT/'temp/refinement-v72/audio'
SR = 32000
LIB = Path('D:/CodeMIAO/MIAOSound/backend/data/library')
SOURCES = [('scifi','engineCircular_002.ogg'),('scifi','impactMetal_001.ogg'),
           ('impact','impactWood_light_001.ogg'),('impact','impactPlate_heavy_001.ogg')]

def sha(b): return hashlib.sha256(b).hexdigest()

def bp(x, lo, hi):
    return signal.sosfilt(signal.butter(3,[lo,hi],fs=SR,btype='bandpass',output='sos'),x)

def put(dest, x, at=0, gain=1):
    start=int(at*SR); count=min(len(x),len(dest)-start)
    if count>0: dest[start:start+count]+=x[:count]*gain

def freeze():
    folder=AUTHORED/'original'; folder.mkdir(parents=True,exist_ok=True)
    rows=[]; decoded={}
    for pack,name in SOURCES:
        source=LIB/pack/'Audio'/name; data=source.read_bytes(); target=folder/(pack+'_'+name)
        if target.exists(): assert target.read_bytes()==data,'Pinned source changed'
        else: target.write_bytes(data)
        license_data=(LIB/pack/'License.txt').read_bytes(); (folder/(pack+'_License.txt')).write_bytes(license_data)
        rows.append({'pack':pack,'file':target.name,'originalPath':str(source),'sha256':sha(data),
             'license':'CC0-1.0','author':'Kenney','licenseSha256':sha(license_data),
             'sourcePage':'https://kenney.nl/assets/'+('sci-fi-sounds' if pack=='scifi' else 'impact-sounds')})
        x,rate=sf.read(io.BytesIO(data),always_2d=True)
        x=signal.resample_poly(x.mean(axis=1),SR,rate);x/=max(np.max(np.abs(x)),1e-9)
        decoded[name]=x
    manifest={'verified':'2026-10-04: official Kenney work pages CC0 + local license', 'sources':rows,
        'syntheticVoice':'original deterministic glottal/formant continuous vowel; no human vocal recording',
        'rejectedV71Sources':'all eight creature mouth recordings excluded; no granular syllabic restart'}
    path=AUTHORED/'sources.json'; text=json.dumps(manifest,indent=2)+'\n'
    if path.exists(): assert path.read_text()==text
    else: path.write_text(text)
    return decoded

def modal(t, frequencies, damping, weights):
    return sum(w*np.sin(2*np.pi*f*t)*np.exp(-d*t) for f,d,w in zip(frequencies,damping,weights))

def formant(x, centers):
    f=np.fft.rfftfreq(len(x),1/SR)
    curve=sum(g*np.exp(-.5*((f-c)/width)**2) for c,width,g in centers)
    return np.fft.irfft(np.fft.rfft(x)*curve,n=len(x))

def recipe(family,event,source):
    seed=int(sha((family+'.'+event).encode())[:8],16); rng=np.random.default_rng(seed)
    seconds={'idle':.72,'hurt':.38,'death':1.25,'attack':.60,'chant':1.12,'summon':.90,'slam':.78}[event]
    if family=='king': seconds={'idle':.90,'hurt':.55,'death':1.65,'attack':.82,'chant':1.12}[event]
    if family=='dr-05': seconds={'idle':.35,'hurt':.29,'death':.72,'attack':.43}[event]
    if family=='dr-18': seconds={'idle':.45,'hurt':.27,'death':.70,'attack':.45}[event]
    n=int(seconds*SR);t=np.arange(n)/SR;noise=rng.normal(0,1,n); x=np.zeros(n)
    if family=='king':
        # One sustained exhalation, continuous u->o vowel; zero repeated syllable onsets.
        f0=125+3*np.sin(2*np.pi*.65*t)+(5 if event=='hurt' else -9)*t/seconds
        phase=np.cumsum(f0)*2*np.pi/SR
        glottal=sum(np.sin(h*phase)/h**1.6 for h in range(1,28))+.018*bp(noise,300,2800)
        dark=formant(glottal,[(260,100,1),(690,170,.40),(1500,300,.06)])
        open_vowel=formant(glottal,[(390,130,1),(850,220,.32),(1600,350,.05)])
        cross=np.clip(t/seconds,0,1)
        x=(dark*(1-cross)+open_vowel*cross)*np.sin(np.pi*t/seconds)**.65
        if event=='attack': x+=.06*bp(noise,45,190)*np.exp(-t*8)
        if event=='death': x*=np.exp(-t*1.0)
        if event=='hurt': x*=np.exp(-t*2.5)
    elif family=='colossus':
        # Weight and metal, never a mouth-like syllable train.
        name='engineCircular_002.ogg' if event=='idle' else 'impactPlate_heavy_001.ogg'
        physical=signal.resample_poly(source[name],5,3)
        put(x,bp(physical,45,2200),0,.7)
        x+=modal(t,[53,91,167,281],[4,6,10,13],[.48,.32,.17,.08])
        if event in ('attack','slam'):put(x,source['impactMetal_001.ogg'],.018,.35)
        if event=='death':x*=np.exp(-t*1.5)
    elif family=='hive':
        # Single dry membrane vowel/air compression; no wet, belch or laughter samples.
        air=bp(noise,370,2400)
        membrane=modal(t,[182,317,503],[2.2,3.5,6],[.22,.12,.035])
        envelope=np.sin(np.pi*t/seconds)**1.5
        x=air*.11*envelope+membrane*envelope
        x+=.018*np.sin(2*np.pi*(1050*t-220*t*t))*envelope
        if event in ('summon','attack'):put(x,bp(source['impactWood_light_001.ogg'],550,3600),.035,.13)
        if event=='death':x*=np.exp(-t*1.6)
    elif family=='dr-05':
        # Single shell scrape and one dry contact, rather than a human creature grunt.
        put(x,bp(source['impactWood_light_001.ogg'],250,6800),0,.7)
        x+=bp(noise,1600,6200)*.075*np.sin(np.pi*t/seconds)**2
        if event in ('hurt','attack'):put(x,source['impactMetal_001.ogg'],.025,.15)
        if event=='death': x*=np.exp(-t*2)
    elif family=='dr-18':
        frequency=210+120*t/seconds if event=='attack' else 230-170*t/seconds if event=='death' else 190+12*np.sin(np.pi*t/seconds)
        phase=2*np.pi*np.cumsum(frequency)/SR
        rotor=sum(np.sin(phase*h)/h**1.25 for h in (1,2,3,5,7))
        x=rotor*.24*np.sin(np.pi*t/seconds)**.8+bp(noise,1000,4500)*.035*np.sin(np.pi*t/seconds)
        if event=='hurt':put(x,bp(source['impactMetal_001.ogg'],1500,6500),0,.15)
    else:
        # Cracked bell shell: unequal resonances, one impulse, no musical melody.
        frequencies=[172,257,391,613,907] if event!='hurt' else [214,331,499,727,1081]
        x=modal(t,frequencies,[4,6,9,13,20],[.65,.28,.14,.07,.03])
        x+=bp(noise,700,5100)*.09*np.exp(-t*55)
        if event=='attack':x+=modal(t,[89,139],[7,11],[.25,.12])
        if event=='death':x*=np.exp(-t*1.5)
    # One low-level finite acoustic tail, no looping and no repeated audible voices.
    if family in ('king','dr-20'):
        put(x,bp(x.copy(),80,1400),.091,.08)
    x-=x.mean(); ramp=min(320,n//8)
    x[:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2
    x[-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
    peak_db=-8 if event=='idle' else -6
    x*=10**(peak_db/20)/max(np.max(np.abs(x)),1e-9);x[0]=x[-1]=0
    x=np.round(x*32767).astype(np.int16).astype(float)/32768
    return x,seed

def encode(x,fmt,sub):
    memory=io.BytesIO();sf.write(memory,x,SR,format=fmt,subtype=sub);return memory.getvalue()

def main():
    source=freeze(); OUT.mkdir(parents=True,exist_ok=True); samples=OUT/'samples';samples.mkdir(exist_ok=True)
    fingerprints={};report=[];html=['<!doctype html><meta charset="utf-8"><title>v72 六族短音试听</title><style>body{max-width:980px;margin:35px auto;font:16px sans-serif;background:#f4f2ec;color:#282722}article{padding:12px;border-bottom:1px solid #ccc}audio{width:70%}</style><h1>v72 六族短音小样</h1><p>不自动播放。每类完整四事件；先听三Boss，再听甲壳、机械工蜂、裂铃。人工听感尚未验收。</p>']
    for family in ['king','colossus','hive','dr-05','dr-18','dr-20']:
        events=['idle','hurt','death','attack']+(['chant'] if family=='king' else ['slam'] if family=='colossus' else ['summon'] if family=='hive' else [])
        for event in events:
            key=family+'.'+event;x,seed=recipe(family,event,source);wav=encode(x,'WAV','PCM_16');ogg=canonical(encode(x,'OGG','VORBIS'))
            assert ogg==canonical(encode(recipe(family,event,source)[0],'OGG','VORBIS'))
            decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and len(decoded)==len(x)
            peaks=[np.max(np.abs(signal.resample_poly(decoded,p,q))) for p,q in [(2,1),(1,1),(1,2)]]
            assert max(peaks)<10**(-1/20) and x[0]==x[-1]==0 and np.isfinite(decoded).all()
            name=key.replace('.','_');(samples/(name+'.wav')).write_bytes(wav);(samples/(name+'.ogg')).write_bytes(ogg)
            fingerprints[key]={'wav':sha(wav),'ogg':sha(ogg)}
            report.append({'key':key,'seconds':len(x)/SR,'seed':seed,'syllabicGestures':1,'v71MouthSourcesUsed':False,'decodedPeakDb':float(20*np.log10(max(peaks))),'format':'PCM16/mono/32000'})
            html.append('<article><b>'+key+'</b><p>单声势 · '+str(round(len(x)/SR,2))+' 秒</p><audio controls preload="metadata" src="samples/'+name+'.ogg"></audio></article>')
            if family=='king':
                for label,ratio in [('game_normal',.6),('game_enraged',.85)]:
                    played=signal.resample_poly(x,100,round(ratio*100));path=name+'_'+label+'.wav'
                    (samples/path).write_bytes(encode(played,'WAV','PCM_16'))
                    html.append('<article>'+key+' · '+label+'（实际调用pitch '+str(ratio)+'）<br><audio controls preload="metadata" src="samples/'+path+'"></audio></article>')
    sidecar=OUT/'sample-sha.json'
    if sidecar.exists():assert json.loads(sidecar.read_text())==fingerprints,'Cross-run sample mismatch'
    sidecar.write_text(json.dumps(fingerprints,indent=2)+'\n')
    (OUT/'preview.html').write_text(''.join(html),encoding='utf-8')
    result={'samples':len(report),'source_hash':'PASS','double_encode':'PASS','cross_run':'PASS' if (OUT/'sample-report.json').exists() else 'FIRST_RUN','actual_decode':'PASS','subjective_audio_audition':'NOT_PERFORMED','productionChanged':False,'entries':report}
    (OUT/'sample-report.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='entries'}))

if __name__=='__main__':main()
