"""Verify canonical append-only entity ids and the actual published OBJ/rig resources."""
import hashlib,json,re
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
OLD=['dr-01','dr-03','dr-05','dr-07','dr-10','dr-11','dr-12','dr-15','dr-20','di-02','di-05','di-08',
     'di-10','di-13','dc-02','dc-08','do-01','di-01','di-03','di-04','di-06','di-07','di-09','di-11',
     'di-12','di-14','di-15','do-02','dr-02','dr-04','dr-06','dr-08','dr-09','dr-13','dr-14','dr-16','dr-17','dr-18','dr-19']
enum=(ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java').read_text(encoding='utf8')
codes=re.findall(r'^    \w+\("([a-z0-9-]+)"',enum,re.M)
assert codes[:39]==OLD
assert codes[39:]==[f'di-{i}' for i in range(16,26)]
reports=[]
for code in codes[39:]:
    folder=ROOT/'src/main/resources/assets/gtsr/liminal'/code
    rig=json.loads((folder/'models/rig_animation.json').read_text(encoding='utf8'))
    groups={g['name']:g for g in rig['groups']}
    for name in groups:
        chain=set();current=name
        while current:
            assert current not in chain;chain.add(current);current=groups[current]['parent']
    vs=[];uvs=[];triangles=0;group=None;faces=[]
    obj=(folder/'models'/f'{code}.obj').read_text(encoding='utf8')
    for row in obj.splitlines():
        p=row.split()
        if not p:continue
        if p[0]=='v':vs.append(list(map(float,p[1:])))
        if p[0]=='vt':uvs.append(list(map(float,p[1:])))
        if p[0]=='g':group=p[1];assert group in groups
        if p[0]=='f':
            assert group in groups
            face_uv=[]
            for f in p[1:]:
                v,u=map(int,f.split('/')[:2]);assert 1<=v<=len(vs);assert 1<=u<=len(uvs)
                face_uv.append(u-1)
            faces.append(face_uv)
            triangles+=len(p)-3
    assert rig['canonical_code']==code
    assert (folder/'textures/model'/f'{code}.png').is_file()
    image=Image.open(folder/'textures/model'/f'{code}.png').convert('RGBA')
    samples=0;sample_colors=set();base_samples=0;glow_samples=0
    for face in faces:
        # Mirror RigAsset's OBJ parser, then sample actual fan-triangulated interiors,
        # their vertices and edge midpoints against the actual production PNG.
        for j in range(1,len(face)-1):
            inds=[face[0],face[j],face[j+1]]
            coords=[(uvs[q][0],1-uvs[q][1]) for q in inds]
            assert all(q<4 for q in inds) or all(q>=4 for q in inds)
            luminous=inds[0]>=4
            for weights in [(1,0,0),(0,1,0),(0,0,1),(1/3,1/3,1/3),(.5,.5,0),(0,.5,.5),(.5,0,.5)]:
                u=sum(weights[k]*coords[k][0] for k in range(3));v=sum(weights[k]*coords[k][1] for k in range(3))
                px=min(image.width-1,int(u*image.width));py=min(image.height-1,int(v*image.height))
                color=image.getpixel((px,py));assert color[3]==255
                assert color!=(36,42,45,255),f'{code}: face samples unpainted atlas background at {px},{py}'
                if luminous:
                    assert px>=64 and py>=64;glow_samples+=1
                else:
                    assert px<64 and py<64;base_samples+=1
                sample_colors.add(color);samples+=1
    assert base_samples>0 and glow_samples>0 and len(sample_colors)>2
    assert all(c in rig['animations'] for c in ['spawn','idle','walk','death'])
    skill=re.search(r'\("'+re.escape(code)+r'".*?, "([a-z_]+)"\)',enum).group(1)
    assert skill in rig['animations']
    for name,clip in rig['animations'].items():
        for t in clip['tracks']:
            assert t['group'] in groups
            keys=t['keys'];assert all(keys[j][0]<keys[j+1][0] for j in range(len(keys)-1))
            assert 0<=keys[0][0]<=keys[-1][0]<=clip['frames']
    assert rig['animations']['death']['frames']==140
    reports.append(dict(code=code,wireId=codes.index(code),triangles=triangles,vertices=len(vs),groups=len(groups),
                        loaderPathsValid=True,skill=skill,meshSHA=hashlib.sha256(obj.encode()).hexdigest(),
                        textureUVSemanticValid=True,uvContract='RigAsset: PNG row = 1 - OBJ v',
                        paintedTextureSamples=samples,baseSamples=base_samples,glowSamples=glow_samples,
                        sampledColors=len(sample_colors)))
assert len({r['meshSHA'] for r in reports})==10
module=(ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/MiniBossCombat.java').read_text(encoding='utf8')
assert not re.search(r'\.setBlock|\.setBlockMetadata|\.createExplosion|\.newExplosion',module)
result=dict(passed=True,oldWireIdsPreserved=39,newWireIds=list(range(39,49)),
            actualPublishedResources=30,terrainMutationCalls=0,reports=reports)
dest=ROOT/'temp/refinement-v72/miniboss/source-asset-verification.json'
dest.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in result.items() if k!='reports'}))
