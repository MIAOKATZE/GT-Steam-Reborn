"""Export only implemented entity assets and production sound bytes for the local gallery."""
from pathlib import Path
import base64, hashlib, json, math, re, shutil, struct, sys

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
    glyph=ASSETS/'textures/fx/rune_glyphs.png';glyphDest=PAGE/'media/entities/fx/rune_glyphs.png';glyphDest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(glyph,glyphDest)
    fx={'glyphURL':glyphDest.relative_to(PAGE).as_posix(),'glyphData':'data:image/png;base64,'+base64.b64encode(glyph.read_bytes()).decode(),'source':glyph.relative_to(ROOT).as_posix(),'sourceSHA256':sha(glyph)}
    (PAGE/'entity-preview-data.js').write_text('globalThis.PROSPERITY_ENTITY_ASSETS='+json.dumps(index,ensure_ascii=False,separators=(',',':'))+';globalThis.PROSPERITY_ENTITY_FX='+json.dumps(fx,separators=(',',':'))+';\n',encoding='utf-8')
    print(json.dumps({'entities':len(result),'clips':sum(len(m['rig']['animations']) for m in result.values())}))
    if '--entities-only' not in sys.argv:
        from combat_audio_synthetic import export
        export()

if __name__=='__main__': run()
