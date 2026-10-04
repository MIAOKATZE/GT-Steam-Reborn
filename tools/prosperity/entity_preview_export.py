"""Export only implemented entity assets and production sound bytes for the local gallery."""
from pathlib import Path
import base64, hashlib, json, math, re, shutil, struct

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/gtsr'
PAGE = ROOT / 'plan/prosperity'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def obj(path):
    vertices, uvs, meshes = [], [], {}
    group = 'root'
    for line in path.read_text(encoding='utf-8').splitlines():
        w = line.split('#')[0].split()
        if not w: continue
        if w[0] == 'v': vertices.append(list(map(float, w[1:4])))
        elif w[0] == 'vt': uvs.append([float(w[1]), 1-float(w[2])])
        elif w[0] == 'g': group = w[1]
        elif w[0] == 'f':
            out = meshes.setdefault(group, [])
            def vertex(token):
                p = token.split('/')
                def ix(s, n): return int(s)-1 if int(s)>0 else n+int(s)
                return vertices[ix(p[0],len(vertices))], uvs[ix(p[1],len(uvs))]
            for i in range(2,len(w)-1):
                tri = [vertex(w[k]) for k in [1,i,i+1]]
                a,b = [[tri[j][0][k]-tri[0][0][k] for k in range(3)] for j in [1,2]]
                normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                norm=math.hypot(*normal)
                if norm<1e-12: raise ValueError(f'Degenerate {path}')
                for v,uv in tri: out.extend(v+[n/norm for n in normal]+uv)
    return [{'name':name,'vertices':len(v)//8,'data':base64.b64encode(struct.pack('<'+'f'*len(v),*v)).decode()} for name,v in meshes.items()], [[min(v[k] for v in vertices) for k in range(3)],[max(v[k] for v in vertices) for k in range(3)]]

def run():
    source=ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java'
    roster=re.findall(r'\w+\("((?:dr|di|dc|do)-\d+)",\s*"([^"]+)"', source.read_text(encoding='utf-8'))
    roster=[(c,n) for c,n in roster if c!='do-01']+[('dc-10','缄王')]
    result={}
    for code,name in roster:
        folder=ASSETS/'liminal'/code
        paths={'obj':folder/'models'/f'{code}.obj','rig':folder/'models/rig_animation.json','texture':folder/'textures/model'/f'{code}.png'}
        rig=json.loads(paths['rig'].read_text(encoding='utf-8'))
        if code=='do-02':
            death='death_a' if 'death_a' in rig['animations'] else 'death'
            rig['animations']={k:v for k,v in rig['animations'].items() if k in ['spawn','idle',death]}
        meshes,bounds=obj(paths['obj'])
        if any(m['name'] not in {g['name'] for g in rig['groups']} for m in meshes): raise ValueError(f'Missing group {code}')
        packaged=[]
        for kind,path in paths.items():
            dest=PAGE/'media/entities'/code/path.name
            dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
            packaged.append({'kind':kind,'source':path.relative_to(ROOT).as_posix(),'sourceSHA256':sha(path),'packagedURL':dest.relative_to(PAGE).as_posix()})
        result[code]={'meta':{'code':code,'name':name},'rig':rig,'meshes':meshes,'bounds':bounds,'triangles':sum(m['vertices']//3 for m in meshes),'textures':[{'data':'data:image/png;base64,'+base64.b64encode(paths['texture'].read_bytes()).decode(),'packagedURL':packaged[-1]['packagedURL']}],'sources':packaged}
    outdir=PAGE/'media/entities'; outdir.mkdir(parents=True,exist_ok=True)
    for code,model in result.items():
        (outdir/f'{code}.js').write_text('globalThis.PROSPERITY_ENTITY_MODELS=globalThis.PROSPERITY_ENTITY_MODELS||{};globalThis.PROSPERITY_ENTITY_MODELS['+json.dumps(code)+']='+json.dumps(model,ensure_ascii=False,separators=(',',':'))+';',encoding='utf-8')
        legacy=outdir/f'{code}.json'
        if legacy.exists() and legacy.resolve().parent==outdir.resolve(): legacy.unlink()
    index={c:{'name':m['meta']['name'],'url':f'media/entities/{c}.js','clips':list(m['rig']['animations']),'sources':m['sources']} for c,m in result.items()}
    definitions=ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/MiniBossDefinitions.java'
    definitionText=definitions.read_text(encoding='utf-8')
    def values(name):
        block=re.search(r'\b'+name+r'\s*=\s*\{([^}]+)\}',definitionText,re.S).group(1)
        return re.findall(r'"([^"]+)"',block) if '"' in block else [float(x.strip()) for x in block.split(',') if x.strip()]
    for i,code in enumerate(values('CODES')):
        index[code]['combat']={'clip':values('CLIPS')[i],'damage':values('DAMAGE')[i],'windupTicks':values('WINDUPS')[i],'cooldownTicks':values('COOLDOWNS')[i],'state':'独立精英 / 小 Boss；沿用旧日虚影 COMBAT 状态机','source':definitions.relative_to(ROOT).as_posix(),'sourceSHA256':sha(definitions)}
    glyph=ASSETS/'textures/fx/rune_glyphs.png';glyphDest=PAGE/'media/entities/fx/rune_glyphs.png';glyphDest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(glyph,glyphDest)
    fx={'glyphURL':glyphDest.relative_to(PAGE).as_posix(),'glyphData':'data:image/png;base64,'+base64.b64encode(glyph.read_bytes()).decode(),'source':glyph.relative_to(ROOT).as_posix(),'sourceSHA256':sha(glyph)}
    (PAGE/'entity-preview-data.js').write_text('globalThis.PROSPERITY_ENTITY_ASSETS='+json.dumps(index,ensure_ascii=False,separators=(',',':'))+';globalThis.PROSPERITY_ENTITY_FX='+json.dumps(fx,separators=(',',':'))+';\n',encoding='utf-8')
    design=ROOT/'plan/model/authored/audio/refinement-v72/DESIGN.md'
    briefs={}
    for line in design.read_text(encoding='utf-8').splitlines():
        cols=[c.strip() for c in line.strip('|').split('|')]
        if len(cols)!=5: continue
        match=re.search(r'((?:dr|di|do|dc)-\d+)',cols[0],re.I)
        code=match.group(1).lower() if match else {'缄王':'dc-10','巢识 dc-08':'dc-08','崩垣 dc-02':'dc-02'}.get(cols[0])
        if code: briefs[code]={'identity':cols[1],'tone':cols[2],'events':cols[3],'length':cols[4]}
    sounds=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8')); events=[]
    for event,data in sounds.items():
        code=event.split('.')[1] if event.startswith(('entity.','mini.')) else {'king':'dc-10','colossus':'dc-02','hive':'dc-08'}.get(event.split('.')[0])
        if code not in result or data.get('category')=='music': continue
        if code=='do-02' and event.split('.')[-1] not in ['birth','idle','death','spawn']: continue
        variants=[]
        for sound in data['sounds']:
            sound={'name':sound} if isinstance(sound,str) else sound
            if sound.get('type')=='event': continue
            namespace,_,name=sound['name'].partition(':')
            if not name: namespace,name='gtsr',namespace
            if namespace!='gtsr': raise ValueError(f'External sound needs licensed local export: {event} {namespace}:{name}')
            src=ASSETS/'sounds'/f'{name}.ogg';dst=PAGE/'media/audio/refinement-v72'/f'{name}.ogg'
            dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst)
            variants.append({'url':dst.relative_to(PAGE).as_posix(),'packagedURL':dst.relative_to(PAGE).as_posix(),'source':src.relative_to(ROOT).as_posix(),'sourceSHA256':sha(src),'volume':sound.get('volume',1),'pitch':sound.get('pitch',1)})
        if variants: events.append({'event':event,'code':code,'category':data.get('category','hostile'),'variants':variants})
    layers={'dc-10':'连续倒序低频闭口声 / 真实软辅音与轻呼吸 / 81、137、213ms 低通厅堂尾音；正常 pitch 0.6，怒态 0.85。','dc-02':'重金属板与承重电机 / 一次金属接触和关节摩擦 / 同材质低通扩散余振。','dc-08':'65–420Hz 空气生物腔体 / 单次膜拉伸与孵化门响 / 轻腔体扩散。'}
    mapping=json.loads((ROOT/'plan/model/authored/audio/refinement-v72/mini-canonical-map.json').read_text(encoding='utf-8'))
    for line in (ROOT/'plan/model/authored/audio/refinement-v72/MINI_BOSS_LAYERS.md').read_text(encoding='utf-8').splitlines():
        cols=[c.strip() for c in line.strip('|').split('|')]
        if len(cols)==5 and cols[0] in mapping:
            code=mapping[cols[0]];briefs[code]={'identity':cols[1],'tone':cols[1],'events':cols[4]+'；预警：'+cols[2]+'；释放：'+cols[3],'length':'各事件独立一至两声势'}
    briefs={code:brief for code,brief in briefs.items() if code in result}
    audio={'status':'生产源快照；尚未获本轮听感验收','soundsJSONSourceSHA256':sha(ASSETS/'sounds.json'),'license':'原素材与加工说明：plan/model/authored/audio/refinement-v72；Kenney 与 OpenGameArt 音源 CC0，逐项许可随生产设计记录。','briefs':briefs,'layers':layers,'events':events}
    (PAGE/'sound-preview-data.js').write_text('globalThis.PROSPERITY_SOUND_ASSETS='+json.dumps(audio,ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
    print(json.dumps({'entities':len(result),'clips':sum(len(m['rig']['animations']) for m in result.values()),'soundEvents':len(events)},ensure_ascii=False))

if __name__=='__main__': run()
