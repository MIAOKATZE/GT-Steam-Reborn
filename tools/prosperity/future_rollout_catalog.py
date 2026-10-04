"""Synchronize the reviewed future catalog with the actual rollout, preserving city design-only."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
path=ROOT/'plan/prosperity/future-design/catalog.js'
catalog=json.loads(path.read_text(encoding='utf8').split('=',1)[1].strip().rstrip(';'))
city=json.loads((ROOT/'temp/refinement-v72/city/catalog-replacement.json').read_text(encoding='utf8'))
bosses=['mb-pressure-auditor','mb-return-overseer','mb-weft-curator','mb-tide-collector','mb-parallax-master','mb-oath-captain','mb-ash-keeper','mb-counterbeat','mb-route-warden','mb-anchor-surveyor']
for i,e in enumerate(catalog['structures']):
    if e['id']=='abandoned_city':
        catalog['structures'][i]=city|{'designOnly':True,'implemented':False}
        continue
    e.update(designOnly=False,implemented=True,status='implemented',geometryStatus='production-prefab-source-exact',
             productionSource='plan/prosperity/structures/future/'+e['id']+'.json',
             runtimeLayout='natural-prefab',runtimeVariant=0)
    if e.get('encounter'):
        e['encounter']['runtimeEnabled']=True
        b=e['encounter'].get('boss')
        if b and b['code'] in bosses:
            b['originalDesignCode']=b['code'];b['code']='di-'+str(16+bosses.index(b['code']))
    if e.get('relic'):
        e['relic']['status']='registered-runtime-key'
        e['relic']['runtimeItemId']='gtsr:ProsperityRelic_'+e['relic']['id']
    e['achievementIds']=[a for a in e.get('achievementIds',[]) if not a.startswith('defeat.mb-')]
    if e.get('chests'):e['chests']['tier']='1–5：普通箱按难度持久随机；小Boss奖励5；三大Boss奖励5'
catalog.update(status='runtime-rollout-v72',scope='3既有场景+61独立精致结构实装；废弃城市仍为设计候选')
path.write_text('/* Reviewed rollout catalog; abandoned_city remains design-only. */\nwindow.PROSPERITY_FUTURE_DESIGN = '+json.dumps(catalog,ensure_ascii=False,indent=2)+';\n',encoding='utf8')
adapter=ROOT/'plan/prosperity/future-adapters.js'
text=adapter.read_text(encoding='utf8')
text=text.replace('future:true,designOnly:true,implemented:false,description:', 'future:true,designOnly:e.designOnly!==false,implemented:e.implemented===true,description:')
text=text.replace("function get(id){if(cache.has(id))", "function get(id){const entry=(w.PROSPERITY_FUTURE_DESIGN?.structures||[]).find(e=>e.id===id)||{};if(cache.has(id))")
text=text.replace('future:true,designOnly:true,implemented:false,playerReviewViews:', 'future:true,designOnly:entry.designOnly!==false,implemented:entry.implemented===true,playerReviewViews:')
text=text.replace('未来设计，尚未接入游戏注册与生成。',"'+(e.implemented?'已实装自然地形结构；源模型与生产切片逐格一致。':'设计候选，未接入自然生成。')+'")
adapter.write_text(text,encoding='utf8')
print('61 implemented entries, city excluded')
index=ROOT/'plan/prosperity/index.html'
text=index.read_text(encoding='utf8')
text=text.replace('本轮收束为三个战斗剧情场景：120 × 120 多厂房铸造战场、浅埋地下工厂与巨树王庭。场景正在打磨，尚未验收；其他结构仅留设计介绍，后续大幅简化减量。',
    '三个既有战斗剧情场景继续保留，另新增61个精致结构：中型10、小型20、废墟30及虚构扩大工程。实际体素模型转为Minecraft切片，稀疏分布在自然地形；废弃城市仍为设计候选。')
text=text.replace('62 项未来结构为独立 design-only 模型目录，未接入游戏；','61 项新结构已接入真实生成，废弃城市1项保持 design-only；')
text=text.replace('FUTURE / DESIGN ONLY','AUTHORED / ROLLOUT')
index.write_text(text,encoding='utf8')
plan=ROOT/'plan/prosperity/future-design/catalog-plan.md'
plan.write_text('# 精致结构实装目录（v1.20.72）\n\n'
    '原有战场、工厂、巨树王庭保留。61个独立作者模型已编译为生产prefab与owner-chunk切片：中型10、小型20、废墟30、虚构扩大工程1；废弃城市仍为design-only，生产目录排除。\n\n'
    '结构保留既定内饰、开放装置、 courtyard与体素风格。箱位重新分散至少4格，条件箱抬至实体支撑台，保留显眼关键路口刷怪笼与原有主通路。没有额外背景地板；自然布局只为已有低位结构地板加窄支撑。\n\n'
    '生产source为`plan/prosperity/structures/future/<id>.json`；`future_rollout_compile.cjs --refine`复建作者模型，`future_rollout_publish.py`生成真实切片，`future_rollout_verify.py`核对所有849684个作者cell及门禁绑定。真实Minecraft布局、碰撞、交互与定位证据由本轮native receipt记录，网页预览不代替游戏核验。\n\n'
    '10个中型首领使用DI16–25，保留原mb设计身份；各自碰撞箱取实际Enum尺寸。普通箱按结构难度持久一次随机1–4档，Boss战箱5档；单枚基础掉落与唯一信物来自实际服务端交互。\n\n'
    '虚构扩大工程保留多段悬浮圆环与中央剧情阅读；15个证物展示台只核实实际取得的物品，不直接发放。集齐并在真实中心读取/提交后触发紫色天空、DO02演示出生/待机/消散、天空复蓝；不要求击杀DO02。两枚城市信物暂由测试/give获取，城市尚不自然生成。\n\n'
    '城市候选为76×24×68的六座小残构，参照原版村落错落排列，无实体、无Boss、无笼，不制造全城地板。\n\n'
    '## 实装稳定ID\n\n'+ '\n'.join('- `'+e['id']+'` — '+e['name'] for e in catalog['structures'] if e.get('implemented'))+'\n',encoding='utf8')
