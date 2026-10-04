"""User-directed v72 second Boss audition: physical/voice body, texture, spatial tail."""
import io, json
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy import signal
from combat_audio import canonical
from combat_audio_refine import ROOT, AUTHORED, LIB, SR, sha, freeze, bp, put, encode

OUT=ROOT/'temp/refinement-v72/audio/boss-revision2'

def voice_sources():
    sources=freeze();rows=[];folder=AUTHORED/'original'
    for name in ['male_ready.ogg','male_war_medic.ogg','male_wrong.ogg']:
        src=LIB/'voice/Audio'/name;data=src.read_bytes();dest=folder/('voice_'+name)
        if dest.exists(): assert dest.read_bytes()==data
        else:dest.write_bytes(data)
        x,rate=sf.read(io.BytesIO(data),always_2d=True)
        x=signal.resample_poly(x.mean(axis=1),SR,rate)
        # Trim one contiguous word to remove recording pre/post roll; never repeat it.
        envelope=np.convolve(np.abs(x),np.ones(320)/320,mode='same')
        active=np.where(envelope>max(envelope)*.04)[0]
        x=x[max(0,active[0]-160):min(len(x),active[-1]+320)]
        sources[name]=x/max(abs(x))
        rows.append({'file':dest.name,'originalPath':str(src),'sha256':sha(data),'license':'CC0-1.0',
            'sourcePage':'https://kenney.nl/assets/voiceover-pack','usage':'one contiguous reversed word, no repeated vocal grains'})
    license_data=(LIB/'voice/License.txt').read_bytes();(folder/'voice_License.txt').write_bytes(license_data)
    body=json.dumps({'sources':rows,'licenseSha256':sha(license_data)},indent=2)+'\n'
    p=AUTHORED/'boss-revision2-sources.json'
    if p.exists():assert p.read_text()==body
    else:p.write_text(body)
    return sources

def layer_recipe(family,event,source):
    seed=int(sha((family+'.'+event+'.boss2').encode())[:8],16)
    rng=np.random.default_rng(seed)
    duration={'idle':.8,'hurt':.43,'death':1.5,'attack':.72,'chant':1.04,'slam':.9,'summon':1.0}[event]
    n=int(duration*SR);t=np.arange(n)/SR;noise=rng.normal(0,1,n)
    gesture=np.sin(np.pi*t/duration)**.8
    if family=='king':
        name='male_war_medic.ogg' if event in ('hurt','attack') else 'male_wrong.ogg' if event=='death' else 'male_ready.ogg'
        # A whole reversed nonlaughing voiced word preserves genuine soft consonants.
        spoken=signal.resample(source[name][::-1],int(n*.78))
        core=np.zeros(n);put(core,spoken,.035,1)
        core=bp(core,110,1550)
        low=bp(core,55,290)*.9
        core=bp(core,200,1600)*.60+low
        speech_env=signal.sosfilt(signal.butter(2,22,fs=SR,output='sos'),np.abs(core))
        speech_env/=max(speech_env.max(),1e-9)
        breath=bp(noise,900,4200)*speech_env*.09
        consonants=bp(core,1200,3900)*.15
        texture=breath+consonants
        # Deliberately irregular, soft rub/fricative articulations, never regular ha-ha.
        for at,length,gain in [(.13,.065,.035),(.41,.09,.025)]:
            count=int(length*SR);chunk=bp(noise[:count],1600,5200)*np.hanning(count)
            put(texture,chunk,at,gain)
        if event=='death':core*=np.exp(-t*.9);texture*=np.exp(-t*1.5)
    elif family=='colossus':
        core=np.zeros(n);texture=np.zeros(n)
        body='engineCircular_002.ogg' if event=='idle' else 'impactPlate_heavy_001.ogg'
        physical=signal.resample_poly(source[body],5,3)
        put(core,bp(physical,35,2100),0,.65)
        bearing=bp(noise,32,190)*.19*np.exp(-t*(2.1 if event=='idle' else 7))
        core+=bearing
        put(texture,bp(source['impactMetal_001.ogg'],400,4800),.025,.26 if event=='idle' else .45)
        texture+=bp(noise,800,3700)*.028*gesture
        if event=='death':core*=np.exp(-t*1.3)
    else:
        # Low biological pressure cavity, membrane friction, airy surface; no whistle.
        cavity=bp(noise,65,420)
        core=cavity*.50*gesture
        bow=bp(noise,300,2100)*.16*gesture
        # Slow nonperiodic motion models a single membrane flex rather than a beat.
        control=np.interp(t,[0,duration*.18,duration*.46,duration*.74,duration],[0,.7,.42,.9,0])
        texture=bow*control+bp(noise,1800,4100)*.035*gesture
        if event in ('attack','summon'):put(texture,bp(source['impactWood_light_001.ogg'],350,2700),.04,.21)
        if event=='death':core*=np.exp(-t*1.1);texture*=np.exp(-t*.8)
    spatial=np.zeros(n)
    dry=core+texture
    for at,gain in [(.081,.12),(.137,.075),(.213,.045)]:
        put(spatial,bp(dry,85,1350),at,gain)
    stems={'body':core,'texture':texture,'space':spatial}
    x=sum(stems.values());x-=np.mean(x)
    scale=10**((-8 if event=='idle' else -6)/20)/max(np.max(abs(x)),1e-9)
    for k in stems:
        stems[k]*=scale
        ramp=320;stems[k][:ramp]*=np.sin(np.linspace(0,np.pi/2,ramp))**2
        stems[k][-ramp:]*=np.cos(np.linspace(0,np.pi/2,ramp))**2
        stems[k][0]=stems[k][-1]=0
    x=sum(stems.values());x=np.round(x*32767).astype(np.int16).astype(float)/32768
    return x,stems,seed

def main():
    sources=voice_sources();OUT.mkdir(parents=True,exist_ok=True);samples=OUT/'samples';samples.mkdir(exist_ok=True)
    html=['<!doctype html><meta charset="utf-8"><title>三Boss 分层修订2</title><style>body{max-width:1050px;margin:30px auto;font:16px sans-serif;background:#f4f1ea}article{padding:15px;border-bottom:1px solid #ccc}audio{width:70%}</style><h1>三Boss 分层修订2</h1><p>根据用户实际试听意见修改：声音增加厚度与层次；缄王改为真实非笑语音的一次倒序低声嘟囔，呼吸/软辅音与轻空间三层。该意见不代表本修订已获听感验收；代理未自行试听。</p>']
    rows=[];hashes={}
    for family,extra in [('king','chant'),('colossus','slam'),('hive','summon')]:
        for event in ['idle','hurt','death','attack',extra]:
            key=family+'.'+event;x,stems,seed=layer_recipe(family,event,sources)
            ogg=canonical(encode(x,'OGG','VORBIS'));wav=encode(x,'WAV','PCM_16')
            assert ogg==canonical(encode(layer_recipe(family,event,sources)[0],'OGG','VORBIS'))
            decoded,rate=sf.read(io.BytesIO(ogg));assert rate==SR and len(x)==len(decoded)
            peaks=[float(max(abs(signal.resample_poly(decoded,p,q)))) for p,q in [(2,1),(1,1),(1,2)]]
            assert max(peaks)<10**(-1/20)
            filename=key.replace('.','_');(samples/(filename+'.ogg')).write_bytes(ogg);(samples/(filename+'.wav')).write_bytes(wav)
            hashes[key]={'ogg':sha(ogg),'wav':sha(wav)}
            rows.append({'key':key,'seconds':len(x)/SR,'seed':seed,'layers':['body','texture','space'],'maxResampledDb':20*np.log10(max(peaks))})
            html.append('<article><h2>'+key+'</h2><audio controls preload="metadata" src="samples/'+filename+'.ogg"></audio>')
            if family=='king':
                for label,ratio in [('normal',.6),('enraged',.85)]:
                    name=filename+'_'+label+'.wav';played=signal.resample_poly(x,100,round(ratio*100));(samples/name).write_bytes(encode(played,'WAV','PCM_16'))
                    html.append('<p>实际游戏pitch '+str(ratio)+'</p><audio controls preload="metadata" src="samples/'+name+'"></audio>')
            if event==extra:
                html.append('<p>以下为隔离声部，同实际配层增益，空间层有意较轻：</p>')
                for k,stem in stems.items():
                    name=filename+'_'+k+'.wav';(samples/name).write_bytes(encode(stem,'WAV','PCM_16'))
                    html.append('<p>'+k+'</p><audio controls preload="metadata" src="samples/'+name+'"></audio>')
            html.append('</article>')
    sidecar=OUT/'sha.json'
    if sidecar.exists():assert json.loads(sidecar.read_text())==hashes
    sidecar.write_text(json.dumps(hashes,indent=2)+'\n')
    (OUT/'preview.html').write_text(''.join(html),encoding='utf-8')
    result={'samples':len(rows),'sourcePin':'PASS','doubleEncode':'PASS','crossRun':'PASS' if (OUT/'report.json').exists() else 'FIRST_RUN','actualDecode':'PASS','userFeedback':'v72 first-sample direction feedback: more layers/weight, king should rustle/mutter like mage; adopted into revision2, not acceptance','subjective_audio_audition':'NOT_PERFORMED','productionChanged':False,'entries':rows}
    (OUT/'report.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='entries'}))

if __name__=='__main__':main()
