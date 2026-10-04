"""Add the ten real independent DI profiles to the field archive catalog."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
path=ROOT/'plan/prosperity/data/catalog.js'
data=json.loads(path.read_text(encoding='utf8').split('=',1)[1].strip().rstrip(';'))
assets=json.loads((ROOT/'plan/prosperity/entity-preview-data.js').read_text(encoding='utf8').split('=',1)[1].split(';globalThis.',1)[0])
future=json.loads((ROOT/'plan/prosperity/future-design/catalog.js').read_text(encoding='utf8').split('=',1)[1].strip().rstrip(';'))
enum=ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java'
for match in re.finditer(r'DI(1[6-9]|2[0-5])\("(di-\d+)", "([^"]+)", (\d+), ([\d.]+)F, ([\d.]+)F, BattleStyle.MINIBOSS, "([^"]+)"',enum.read_text(encoding='utf8')):
    num,code,name,hp,width,height,clip=match.groups()
    structure=next(e for e in future['structures'] if e.get('encounter',{}).get('boss',{}).get('code')==code)
    asset=assets[code]
    entry=dict(id=code.replace('-','_'),code=code,name=name,nameTag='(旧日虚影)'+name,
        category='entity',group='old_echo',battleStyle='MINIBOSS',hp=int(hp),width=width,height=height,
        bossBar=True,implemented=True,designOnly=False,originalDesignCode=structure['encounter']['boss']['originalDesignCode'],
        spawn={'where':[structure['name']+'（'+structure['id']+'）真实战斗大厅'],
               'conditions':['自然地形结构固定出生，独立DI身份；无召唤替身，不进入地表随机夜刷池']},
        drops=[],facts=[f'最大生命{hp} / 碰撞{width}×{height} / 独立MINIBOSS技能{clip}'],
        description=[structure['story']['text'],'击败后解锁5档Boss奖励箱，首箱含唯一来源信物'],
        assetSource=f'liminal/{code}/textures/model/{code}.png',
        model=f'media/entities/{code}.js',rig=f'media/entities/{code}/rig_animation.json',
        combat=asset['combat'],sources=[{'file':str(enum.relative_to(ROOT)).replace('\\','/'),'label':'实际Enum注册/生命/尺寸/技能'},
            {'file':'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/MiniBossDefinitions.java','label':'独立动作/前摇/冷却/伤害'},
            {'file':'plan/prosperity/structures/future/'+structure['id']+'.json','label':'真实结构出生与Boss战门禁'}]+[{'file':s['source'],'label':s['kind']} for s in asset['sources']])
    data['entities']=[e for e in data['entities'] if e.get('code')!=code]+[entry]
data['meta']['counts']['entities']=len(data['entities']);data['meta']['sourceVersion']='1.20.72'
path.write_text('/* Source catalog with v72 real structure and entity rollout. */\nwindow.PROSPERITY_CATALOG = '+json.dumps(data,ensure_ascii=False,indent=2)+';\n',encoding='utf8')
print('DI16–25 added; catalog entities='+str(len(data['entities'])))
