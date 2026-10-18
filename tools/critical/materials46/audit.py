"""Audit canonical palette/geometry/languages/sprite metadata; emit the 65-material Wiki inventory."""
import argparse
import collections
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ASSET = 'assets/gtsr/'
FACES = ['ny', 'py', 'nz', 'pz', 'nx', 'px']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--jar', type=Path, help='Audit resources from this product jar instead of the source tree')
    args = parser.parse_args()
    jar = zipfile.ZipFile(args.jar) if args.jar else None
    base = ROOT/'src/main/resources'
    def read(name): return jar.read(ASSET+name) if jar else (base/ASSET/name).read_bytes()
    def exists(name): return ASSET+name in jar.namelist() if jar else (base/ASSET/name).is_file()
    def data(name): return json.loads(read(name))
    palette = data('critical/materials.json')
    ids = list(dict.fromkeys(v['id'] for v in palette.values()))
    languages = {}
    failures = []
    for lang in ['zh_CN', 'en_US']:
        rows = collections.defaultdict(list)
        for line in read('lang/'+lang+'.lang').decode('utf-8').splitlines():
            if line and not line.startswith('#') and '=' in line:
                k,v = line.split('=',1); rows[k].append(v)
        languages[lang] = {k:v[-1] for k,v in rows.items()}
        failures += [f'duplicate key {lang}:{k}' for k,v in rows.items()
                     if len(v)>1 and (k.startswith('tile.critical_') or k.startswith('tile.gtsr.critical_v2.'))]
        for kind in ['solar', 'turbine', 'processing', 'entangler', 'sun', 'dimension', 'battery', 'assembly', 'accelerator']:
            registered = languages[lang].get(f'gt.blockmachines.gtsr.critical.{kind}.name')
            terminal = languages[lang].get(f'gtsr.critical.kind.{kind}')
            if not registered or not terminal or registered != terminal:
                failures.append(f'inconsistent machine name {lang}:{kind}: registered={registered!r}, terminal={terminal!r}')
    filenames = [n[len(ASSET):] for n in jar.namelist() if n.startswith(ASSET+'critical/models/') and n.endswith('.geometry.json')] if jar else [str(p.relative_to(base/ASSET)).replace('\\','/') for p in (base/ASSET/'critical/models').glob('*.geometry.json')]
    usage = collections.defaultdict(set); loom_forms = set(); current_forms = set()
    current = set()
    for filename in filenames:
        slug = Path(filename).name.removesuffix('.geometry.json'); model = data(filename)
        for row in model['blocks']:
            if row[3] not in palette: failures.append(f'unknown {row[3]} in {slug}'); continue
            identifier = palette[row[3]]['id']; dynamic = row[4] in model['animationGroups']
            usage[identifier].add(slug)
            if slug.startswith('v2_') and not slug.startswith(('v2_v','v2_legacy')): current.add(identifier)
            if slug in ['loom','v2_loom','v2_v25_loom','v2_v27_loom','v2_v30_loom','v2_legacy_loom']:
                loom_forms.add((identifier,dynamic))
            if slug == 'v2_loom': current_forms.add((identifier,dynamic))
    sprites = set(); inventory = []
    def collect(obj):
        if isinstance(obj,dict):
            for k,v in obj.items():
                if k in ['albedo','emission','frozenAlbedo','frozenEmission'] and v: sprites.add(v)
                else: collect(v)
        elif isinstance(obj,list):
            for v in obj: collect(v)
    for identifier in ids:
        p = palette[identifier]
        aliases = [a for a,v in palette.items() if v['id']==identifier]
        properties = ['alphaMode','emissionStrength','animated','lightValue','lightOpacity','sprites','ctm','faceSprites']
        for alias in aliases:
            if any(p.get(key)!=palette[alias].get(key) for key in properties): failures.append(f'conflicting alias {alias}')
        forms = [('critical_'+identifier,False,False),('critical_proxy_'+identifier,False,True)]
        forms += [('critical_loom_'+('proxy_' if dynamic else '')+identifier,True,dynamic) for dynamic in [False,True] if (identifier,dynamic) in loom_forms]
        for reg,loom,dynamic in forms:
            for lang,names in languages.items():
                key = 'tile.'+reg+'.name'
                value = names.get(key)
                if not value: failures.append(f'missing {lang}:{key}')
                elif '\ufffd' in value or '??' in value: failures.append(f'corrupt name {lang}:{key}')
        for key in p.get('tooltipKeys',[]):
            for lang,names in languages.items():
                if not names.get(key): failures.append(f'missing {lang}:{key}')
        faces = {}
        if identifier.startswith('v2_'):
            collect(p)
            for side,face in enumerate(FACES):
                if 'ctm' in p:
                    ctm = p['ctm']; group = ctm['faceLookup'][face]; pair = ctm['sprites'][group]['0']
                    allowed = ctm['faceAllowedMask'][face]
                    for mask in range(16):
                        if str(mask & allowed) not in ctm['sprites'][group]: failures.append(f'missing CTM {identifier}/{face}/{mask & allowed}')
                elif 'faceSprites' in p:
                    link=p['faceSprites'][face]; pair=palette['v2_'+link['material']]['sprites'][link['group']]
                else: pair=p['sprites']['top' if side<2 else 'side']
                faces[face]=pair
        else:
            for side,face in enumerate(FACES):
                sprite='gtsr:critical/'+identifier+('__top' if side<2 else '')
                faces[face]={'albedo':sprite,'emission':'gtsr:critical/'+identifier+'_emission' if side>=2 and p['emissionStrength']>0 else None}
                collect(faces[face])
        inventory.append({'id':identifier,'aliases':aliases,'names':{lang:names['tile.critical_'+identifier+'.name'] for lang,names in languages.items()},
            'currentGeometryUsed':identifier in current,'models':sorted(usage[identifier]),
            'currentHandPlaceableLoomForms':[{'dynamic':dynamic,'registry':'critical_loom_'+('proxy_' if dynamic else '')+identifier,
                'names':{lang:names['tile.critical_loom_'+('proxy_' if dynamic else '')+identifier+'.name'] for lang,names in languages.items()}} for dynamic in [False,True] if (identifier,dynamic) in current_forms],
            'constructionBlocksInternal':True,'faces':faces,'alphaMode':p['alphaMode'],'emissive':p['emissionStrength']>0})
    for lang,names in languages.items():
        visible = [names['tile.critical_loom_'+('proxy_' if dynamic else '')+identifier+'.name'] for identifier,dynamic in current_forms]
        if len(visible) != len(set(visible)): failures.append('ambiguous visible loom names '+lang)
    animated = 0
    for sprite in sorted(sprites):
        domain,path=sprite.split(':',1)
        if domain!='gtsr': failures.append('foreign sprite '+sprite); continue
        filename='textures/blocks/'+path+'.png'
        if not exists(filename): failures.append('missing sprite '+sprite); continue
        image=Image.open(io.BytesIO(read(filename))); w,h=image.size
        if w<=0 or h%w: failures.append('invalid sprite dimensions '+sprite); continue
        if h>w:
            animated+=1
            if not exists(filename+'.mcmeta'): failures.append('missing animation metadata '+sprite); continue
            animation=data(filename+'.mcmeta')['animation']
            for frame in animation.get('frames',[]):
                index=frame['index'] if isinstance(frame,dict) else frame
                if not 0<=index<h//w: failures.append('invalid frame '+sprite)
    result={'status':'FAIL' if failures else 'PASS','resourceSource':str(args.jar.resolve()) if args.jar else 'source tree',
        'resourceJarSHA256':hashlib.sha256(args.jar.read_bytes()).hexdigest() if args.jar else None,
        'counts':{'aliases':len(palette),'entities':len(ids),'legacy':sum(not i.startswith('v2_') for i in ids),
                  'v2':sum(i.startswith('v2_') for i in ids),'registryIdentities':2*len(ids)+len(loom_forms),
                  'currentLoomForms':len(current_forms),'sprites':len(sprites),'animatedSprites':animated},
        'controllerCasings':[{'textureId':15872,'registry':'critical_loom_alloy_frame','material':'alloy_frame'},
                             {'textureId':15873,'registry':'critical_v2_reinforced_panel','material':'v2_reinforced_panel'}],
        'materials':inventory,'failures':failures,'boundary':'Resource declarations, palette identity, geometry, all registered names and sprite file/animation metadata only; no client visual/GL claim.'}
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'status':result['status'],'counts':result['counts'],'failures':len(failures)}))
    if failures: raise SystemExit(1)


if __name__=='__main__': main()
