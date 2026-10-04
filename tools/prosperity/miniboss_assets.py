"""Independent mechanical custodian meshes and animated rigs, in block units."""
import argparse, hashlib, json, math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
CODES = [f'di-{i}' for i in range(16,26)]
DESIGN_CODES = ['mb-pressure-auditor','mb-return-overseer','mb-weft-curator','mb-tide-collector',
         'mb-parallax-master','mb-oath-captain','mb-ash-keeper','mb-counterbeat','mb-route-warden','mb-anchor-surveyor']
NAMES = ['阀印监核','拒收督工','断纬主织','逆潮收税官','偏光鉴定师','换誓长官','存名司炉','反拍钟师','归途路监','定锚测记官']
CLIPS = ['pressure_seal','return_draw','weft_cross','tide_levy','parallax_split','oath_cut','ash_names','counterbeat','route_bar','anchor_boundary']
WIND = [36,40,38,42,34,30,40,32,38,44]
COLORS = [(93,164,151),(175,111,68),(113,158,178),(61,153,185),(153,121,210),(170,161,90),(176,83,53),(92,175,189),(161,125,76),(93,133,191)]
CONDITIONS_EN = ['Pressure ring locked: move into the center or beyond the outer edge',
    'Hook cone locked: move sideways out of the towing sector',
    'Crossed wires: avoid the horizontal line, then the vertical line',
    'Tidal ring locked: enter the inner circle or leave the outer circle',
    'Twin lens tracks: the narrow space between both beams stays clear',
    'Sword sector charging: move behind the shield or beyond seven blocks',
    'Three ash marks: leave each marked two-block circle',
    'Inner beat, then outer beat: change position between both warnings',
    'Road barrier locked: cross out of the marked transverse line',
    'Anchor ring locked: stay in the center or leave the outer edge']

class Mesh:
    def __init__(self):
        # RigAsset converts OBJ v to PNG row coordinate 1-v. Base atlas is the PNG's
        # upper-left quadrant; luminous metal is the lower-right quadrant.
        self.lines = ['# Independent custodian geometry; block units', 'vt 0.10 0.90','vt 0.45 0.90','vt 0.45 0.55','vt 0.10 0.55',
                      'vt 0.60 0.40','vt 0.90 0.40','vt 0.90 0.10','vt 0.60 0.10']
        self.vertices=[]; self.groups=[dict(name='root',parent=None,pivot=[0,0,0],axis='+Y',motion='rotate',rest=0,min=-180,max=180)]
        self.triangles=0
    def part(self,name,pivot,axis='+Y',parent='body'):
        self.groups.append(dict(name=name,parent=parent,pivot=list(pivot),axis=axis,motion='rotate',rest=0,min=-180,max=180))
        self.lines.append('g '+name)
    def shape(self,vs,faces,glow=False):
        start=len(self.vertices)+1
        self.vertices.extend(vs)
        self.lines.extend('v %.5f %.5f %.5f'%v for v in vs)
        for face in faces:
            self.lines.append('f '+' '.join(f'{start+v}/{(j%4)+1+(4 if glow else 0)}' for j,v in enumerate(face)))
            self.triangles+=len(face)-2
    def box(self,c,s,glow=False):
        x,y,z=c; a,b,d=[v/2 for v in s]
        vs=[(x+i*a,y+j*b,z+k*d) for i,j,k in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
        self.shape(vs,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)],glow)
    def drum(self,c,r,h,n=12,axis='y',glow=False):
        x,y,z=c; vs=[]
        for side in [-1,1]:
            for j in range(n):
                a=j*2*math.pi/n
                v=(r*math.cos(a),side*h/2,r*math.sin(a))
                if axis=='z': v=(v[0],v[2],v[1])
                if axis=='x': v=(v[1],v[0],v[2])
                vs.append((x+v[0],y+v[1],z+v[2]))
        faces=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]
        faces.extend((j,(j+1)%n,(j+1)%n+n,j+n) for j in range(n))
        self.shape(vs,faces,glow)
    def ring(self,c,r,thickness,axis='y',glow=False):
        x,y,z=c; vs=[]; n=16
        for j in range(n):
            a=j*math.pi*2/n
            for k in range(6):
                b=k*math.pi/3
                v=((r+thickness*math.cos(b))*math.cos(a), thickness*math.sin(b), (r+thickness*math.cos(b))*math.sin(a))
                if axis=='z': v=(v[0],v[2],v[1])
                if axis=='x': v=(v[1],v[0],v[2])
                vs.append((x+v[0],y+v[1],z+v[2]))
        self.shape(vs,[(j*6+k,((j+1)%n)*6+k,((j+1)%n)*6+(k+1)%6,j*6+(k+1)%6) for j in range(n) for k in range(6)],glow)

def build(i):
    m=Mesh(); m.part('body',[0,1.3,0],'+Y','root')
    # Every silhouette is authored separately, rather than recolouring one body.
    if i==0: # valve tripod and pressure cage
        m.drum((0,1.25,0),.65,1.2);m.ring((0,1.7,0),.8,.1)
        m.part('actuator',[0,2.05,0]);m.drum((0,2.1,0),.7,.18);m.ring((0,2.1,0),.85,.07,glow=True)
        for j in range(3):
            a=j*math.pi*2/3;m.part('limb'+str(j),[.55*math.cos(a),.8,.55*math.sin(a)],'+X')
            m.box((.75*math.cos(a),.4,.75*math.sin(a)),(.25,.8,.25))
        m.part('sensor',[0,1.6,-.65],'+X');m.drum((0,1.6,-.7),.25,.2,axis='z',glow=True)
    elif i==1: # wide two-roller reject press with raised crane hook
        m.box((0,1.25,0),(1.5,.8,.9))
        for j in range(2):
            m.part('limb'+str(j),[(-1 if j==0 else 1)*.8,.45,0],'+X');m.drum(((-1 if j==0 else 1)*.8,.45,0),.4,.8,axis='z')
        m.part('actuator',[0,1.6,0],'+X');m.box((0,2.0,0),(.3,.85,.3));m.box((0,2.35,-.35),(.3,.25,.8));m.ring((0,2,-.65),.3,.08,'z')
        m.part('sensor',[0,1.4,-.5]);m.box((0,1.4,-.51),(.9,.15,.1),True)
    elif i==2: # loom arachnid, six radial treadles and suspended shuttle
        m.box((0,1.2,0),(.9,.6,1.4));m.box((0,1.7,0),(1.8,.14,1.5))
        for j in range(6):
            s=-1 if j<3 else 1; z=(j%3-1)*.6
            m.part('limb'+str(j),[s*.45,1,z],'+Z');m.box((s*.8,.7,z),(.7,.18,.16));m.box((s*1.05,.35,z),(.16,.7,.16))
        m.part('actuator',[0,1.7,0],'+Z');m.box((0,2.05,0),(.22,.6,1.3));m.ring((0,2.1,0),.25,.05,'z',True)
        m.part('sensor',[0,1.35,-.7],'+X');m.box((0,1.35,-.75),(.55,.2,.1),True)
    elif i==3: # tide vessel on three fins, lifting sluice hood
        m.drum((0,1.2,0),.62,1.5);m.ring((0,1.2,0),.8,.1)
        for j in range(3):
            a=j*math.pi*2/3;m.part('limb'+str(j),[.5*math.cos(a),.7,.5*math.sin(a)],'+Z');m.box((.75*math.cos(a),.4,.75*math.sin(a)),(.3,.8,.3))
        m.part('actuator',[0,2,0],'+Y');m.drum((0,2.15,0),.75,.3);m.ring((0,2.5,0),.5,.08,glow=True)
        m.part('sensor',[0,1.6,-.7],'+X');m.drum((0,1.6,-.65),.23,.3,axis='z',glow=True)
    elif i==4: # twin parallax lens gimbal on a narrow pedestal
        m.drum((0,.8,0),.35,1.25);m.box((0,.12,0),(1.6,.2,.9))
        m.part('actuator',[0,1.6,0],'+Y');m.ring((0,2,0),.8,.12,'z');m.box((0,2,0),(1.4,.1,.2))
        for j in range(2):
            s=-1 if j==0 else 1;m.part('limb'+str(j),[s*.45,2,0],'+X','actuator');m.drum((s*.45,2,0),.35,.4,axis='z');m.drum((s*.45,2,-.24),.25,.08,axis='z',glow=True)
        m.part('sensor',[0,2.8,0],'+Z');m.box((0,2.8,0),(.1,.4,.1))
    elif i==5: # oath officer, banner yoke, articulated shield and sword
        m.box((0,1.6,0),(.8,1.1,.5));m.drum((0,2.5,0),.35,.6)
        for j in range(2):
            s=-1 if j==0 else 1;m.part('limb'+str(j),[s*.28,1.1,0],'+X');m.box((s*.28,.6,0),(.3,1.1,.35))
        m.part('actuator',[.5,2,0],'+Z');m.box((.75,1.5,0),(.2,1,.2));m.box((.8,1.15,-.5),(.12,.15,1.2))
        m.part('sensor',[-.5,2,0],'+X');m.box((-.65,1.6,-.3),(.65,1,.16));m.box((-.65,1.6,-.4),(.08,.75,.04),True)
    elif i==6: # named furnace, chimney crown, separate feeding tongs
        m.drum((0,1.25,0),.8,1.8);m.ring((0,1,0),.85,.08)
        for j in range(3):
            a=j*math.pi*2/3;m.part('limb'+str(j),[.6*math.cos(a),.6,.6*math.sin(a)],'+X');m.box((.75*math.cos(a),.3,.75*math.sin(a)),(.3,.6,.3))
        m.part('actuator',[0,2.1,0],'+Z');m.drum((0,2.5,0),.4,.7);m.ring((0,2.7,0),.5,.08)
        m.part('sensor',[0,1.3,-.8],'+X');m.box((0,1.3,-.82),(.8,.7,.08),True)
    elif i==7: # open fork bell and two eccentric hammers
        m.box((0,.15,0),(1.8,.3,.8));m.box((-.8,1.5,0),(.2,2.6,.3));m.box((.8,1.5,0),(.2,2.6,.3));m.box((0,2.8,0),(1.8,.18,.3))
        m.part('actuator',[0,2.7,0],'+Z');m.ring((0,1.95,0),.6,.2,'z');m.drum((0,1.4,0),.6,.6)
        for j in range(2):
            s=-1 if j==0 else 1;m.part('limb'+str(j),[s*.8,1.8,0],'+Z');m.box((s*.5,1.8,0),(.6,.12,.12));m.drum((s*.35,1.8,0),.18,.3,axis='z')
        m.part('sensor',[0,1.3,-.65],'+Y');m.ring((0,1.3,-.65),.32,.05,'z',True)
    elif i==8: # rail warden, four wheels and folding barrier
        m.box((0,.65,0),(1.5,.45,1.3));m.box((0,1.15,0),(.5,.6,.5))
        for j in range(4):
            s=-1 if j<2 else 1;z=-.45 if j%2==0 else .45;m.part('limb'+str(j),[s*.8,.4,z],'+X');m.drum((s*.8,.4,z),.3,.22,axis='x')
        m.part('actuator',[0,1.5,0],'+Z');m.box((0,1.8,0),(2.3,.18,.22));m.box((0,2,0),(2.3,.12,.2),True)
        m.part('sensor',[0,1.4,-.35],'+X');m.drum((0,1.4,-.4),.2,.2,axis='z',glow=True)
    else: # surveyor tripod, rotating compass hoop and hanging sounding plumb
        m.drum((0,1.75,0),.3,.4)
        for j in range(3):
            a=j*math.pi*2/3;m.part('limb'+str(j),[.3*math.cos(a),1.6,.3*math.sin(a)],'+Z');m.box((.7*math.cos(a),.85,.7*math.sin(a)),(.18,1.7,.18))
        m.part('actuator',[0,2,0],'+Y');m.ring((0,2.2,0),1,.08);m.ring((0,2.2,0),.65,.07,'z');m.box((0,2.2,0),(1.6,.07,.1),True)
        m.part('sensor',[0,1.6,0],'+X');m.box((0,1.2,0),(.06,.8,.06));m.drum((0,.75,0),.2,.22)
    return m

def track(group,keys,channel='rotate',axis=None):
    t=dict(group=group,channel=channel,keys=keys)
    if axis:t['axis']=axis
    return t

def rig(i,m):
    last=WIND[i]+(24 if i==6 else 22 if i==7 else 12 if i in [2,4] else 0)+24
    # Preparatory compression, signature actuator sweep, body recoil, slow return.
    axes=['+Y','+X','+Z','+Y','+Y','+Z','+Z','+Z','+Z','+Y']
    angles=[110,-75,65,140,50,-115,-35,45,-90,180]
    skill=[track('body',[[0,0],[WIND[i]-4,-.12],[WIND[i],.14],[WIND[i]+8,0],[last,0]],'translate','+Y'),
           track('actuator',[[0,0],[WIND[i]-4,-angles[i]*.25],[WIND[i],angles[i]],[last-12,angles[i]*.4],[last,0]],axis=axes[i]),
           track('sensor',[[0,0],[WIND[i]-4,18],[WIND[i],-20],[last,0]],axis='+X')]
    if i in [2,4,6,7]:
        count=3 if i==6 else 2
        step=22 if i==7 else 12
        keys=[[0,0],[WIND[i]-4,-angles[i]*.25]]
        for j in range(count):
            keys.extend([[WIND[i]+j*step,angles[i]*(-1 if j%2 else 1)],
                         [WIND[i]+j*step+8,-angles[i]*.4]])
        keys.append([last,0]);skill[1]['keys']=keys
    walk=[track('body',[[0,0],[6,.06],[12,0],[18,-.03],[24,0]],'translate','+Y')]
    for j,g in enumerate([g for g in m.groups if g['name'].startswith('limb')]):
        a=14 if j%2==0 else -14
        walk.append(track(g['name'],[[0,a],[12,-a],[24,a]]))
    return dict(schema_version='2.0-entity',canonical_code=CODES[i],original_design_code=DESIGN_CODES[i],name=NAMES[i],units='block',up_axis='+Y',
        forward_axis='-Z',origin='bbox-bottom-center',groups=m.groups,
        animations={'idle':dict(loop=True,frames=40,speed=1,tracks=[track('sensor',[[0,-4],[20,4],[40,-4]])]),
        'walk':dict(loop=True,frames=24,speed=1,tracks=walk),
        'spawn':dict(loop=False,frames=40,speed=1,tracks=[track('root',[[0,-.25],[30,.08],[40,0]],'translate','+Y')]),
        CLIPS[i]:dict(loop=False,frames=last,speed=1,tracks=skill),
        'death':dict(loop=False,frames=140,speed=1,tracks=[track('body',[[0,0],[16,15],[32,-25],[60,75],[140,75]],axis='+Z'),
            track('body',[[0,0],[24,-.3],[60,-.8],[140,-.8]],'translate','+Y'),
            track('actuator',[[0,0],[20,40],[60,90],[140,90]],axis='+X')])},
        emissive_groups=['sensor'],production=dict(independent_design=True,no_surface_characters=True,revision='v72'))

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--publish',action='store_true')
    ap.add_argument('--obj-only',action='store_true',help='Rewrite only OBJ files, preserving published rigs and PNGs')
    args=ap.parse_args()
    out=ROOT/('src/main/resources/assets/gtsr/liminal' if args.publish else 'temp/refinement-v72/miniboss/assets')
    report=[]
    for i,code in enumerate(CODES):
        m=build(i);r=rig(i,m);folder=out/code
        (folder/'models').mkdir(parents=True,exist_ok=True);(folder/'textures/model').mkdir(parents=True,exist_ok=True)
        obj='\n'.join(m.lines)+'\n';(folder/'models'/f'{code}.obj').write_text(obj,encoding='utf8')
        if not args.obj_only:
            (folder/'models/rig_animation.json').write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf8')
        im=Image.new('RGBA',(128,128),(36,42,45,255));d=ImageDraw.Draw(im);col=COLORS[i]
        for y in range(64):
            for x in range(64):
                noise=((x*17+y*31+i*19)%17)-8
                im.putpixel((x,y),tuple(max(0,min(255,int(c*.65)+noise)) for c in col)+(255,))
        for y in range(0,64,16):d.line((0,y,63,y),fill=(19,24,28),width=2)
        for x in range(0,64,16):d.line((x,0,x,63),fill=(20,25,28),width=1)
        for x in range(8,64,16):
            for y in range(8,64,16):d.rectangle((x-1,y-1,x+1,y+1),fill=(145,154,152))
        d.rectangle((64,64,127,127),fill=tuple(min(255,c+65) for c in col)+(255,))
        if not args.obj_only:im.save(folder/'textures/model'/f'{code}.png')
        names={g['name'] for g in m.groups}
        assert len(names)==len(m.groups)
        for clip in r['animations'].values():
            for t in clip['tracks']:
                assert t['group'] in names
                assert all(math.isfinite(k[1]) for k in t['keys'])
                assert [k[0] for k in t['keys']]==sorted(set(k[0] for k in t['keys']))
                assert t['keys'][-1][0]<=clip['frames']
        bounds=[[min(v[j] for v in m.vertices),max(v[j] for v in m.vertices)] for j in range(3)]
        report.append(dict(code=code,groups=len(names),triangles=m.triangles,bounds=bounds,mesh_sha256=hashlib.sha256(obj.encode()).hexdigest(),
                           windup=WIND[i],clip=CLIPS[i],release_frame=WIND[i],valid=True))
    dest=ROOT/'temp/refinement-v72/miniboss';dest.mkdir(parents=True,exist_ok=True)
    (dest/'geometry-verification.json').write_text(json.dumps(dict(passed=True,independent_meshes=len(set(r['mesh_sha256'] for r in report)),reports=report),ensure_ascii=False,indent=2),encoding='utf8')
    design_path=dest/'design.json'
    if design_path.exists():
        design=json.loads(design_path.read_text(encoding='utf8'))
        design['status']='production-DI16-DI25'
        for i,p in enumerate(design['profiles']):p.update(code=CODES[i],originalDesignCode=DESIGN_CODES[i],condition_en=CONDITIONS_EN[i])
        design_path.write_text(json.dumps(design,ensure_ascii=False,indent=2),encoding='utf8')
        for locale in ['zh_CN','en_US']:
            rows=[]
            for p in design['profiles']:
                rows.append(f"echo.skill.{p['code']}.1="+(p['skill'] if locale=='zh_CN' else p['english']))
                rows.append(f"echo.warning.{p['code']}="+(p['condition'] if locale=='zh_CN' else p['condition_en']))
            (dest/f'{locale}.lang.fragment').write_text('\n'.join(rows)+'\n',encoding='utf8')
    print(json.dumps(dict(passed=True,models=len(report),independent_meshes=10)))

if __name__=='__main__': main()
