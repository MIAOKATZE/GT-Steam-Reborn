"""Freeze a full production UI inventory and visual plans; does not launch Minecraft."""
from pathlib import Path
import argparse, hashlib, json, re

ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).parent
G='com.miaokatze.gtsr.'

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def ref(n): return {'ref':n}
def call(t,m,*a): return {'target':t,'call':m,'args':list(a)}

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--workspace',type=Path,required=True);ap.add_argument('--revision',default='');a=ap.parse_args()
    if a.revision and not re.fullmatch(r'r[0-9]+',a.revision):raise ValueError('Use r1/r2/... immutable plan revisions')
    out=a.workspace.resolve();out.mkdir(parents=True,exist_ok=True)
    src=ROOT/'src/main/java/com/miaokatze/gtsr/common'
    enums=(src/'api/enums/MetaTileEntityID.java').read_text(encoding='utf-8').split('public final int ID')[0]
    kinds=re.findall(r'^\s*([A-Z][A-Z_]+)\((\d+)\)',enums,re.M)
    critical=[k for k,_ in kinds if k.startswith('CRITICAL_') and k!='CRITICAL_SINGULARITY_COMPRESSOR']
    nodes=['STEAM_CACHE_NODE','REINFORCED_STEAM_CACHE_NODE','OVERPRESSURE_STEAM_CACHE_NODE','WATER_CACHE_NODE','REINFORCED_WATER_CACHE_NODE','OVERPRESSURE_WATER_CACHE_NODE','SINGULARITY_STEAM_COMPARTMENT','SINGULARITY_STEAM_OUTPUT_COMPARTMENT','SINGULARITY_FLUID_INPUT_COMPARTMENT','SINGULARITY_FLUID_OUTPUT_COMPARTMENT']
    inv=[];steps=[];narrow=[]
    def add(op,**kw): steps.append({'op':op,**kw})
    def wait(t=20): add('wait',ticks=t)
    def invoke(t,m,*args): add('invoke',side='server',target=t,method=m,args=list(args))
    def enter():
        invoke(call(ref('server'),'getCommandManager|func_71187_D'),'executeCommand|func_71556_a',ref('serverPlayer'),'gtsrtest enter');wait(45)
    add('group',name='gallery-enter-TestLab');enter()
    def entry(kind,variant='native',id=None,category=None,reason=None):
        id=id or 'ui.'+kind.lower()+('.terminal' if variant=='terminal' else '')
        e=dict(id=id,title=kind.replace('_',' '),kind=kind,variant=variant,category=category or ('Critical' if kind in critical else '普通机器'),route='生产机器普通右键' if variant=='native' else '手持枢纽终端生产入口',shots=[],groups=[id])
        if reason:e['reason']=reason
        inv.append(e);return e
    def group_open(e,bound=False):
        add('group',name=e['id']);add('galleryClose');wait(8)
        add('galleryPrepare',side='server',kind=e['kind'],terminal=e['variant']=='terminal',boundFixture=bound);wait(25)
        add('galleryOpen',side='server',id=e['id']);add('galleryExpect',id=e['id'],timeoutTicks=100);wait(30)
    def shot(e,suffix='overview'):
        name=e['id']+'.'+suffix;e['shots'].append(name);add('galleryCapture',name=name)
    for kind,relative in kinds:
        reason='此存储单元仅有继承builder，无玩家开窗入口。' if kind in ('HUB_STORAGE_UNIT','REINFORCED_HUB_STORAGE_UNIT','OVERPRESSURE_HUB_STORAGE_UNIT') else None
        e=entry(kind,reason=reason)
        if reason:continue
        group_open(e);shot(e)
    for kind in critical:
        e=entry(kind,'terminal');group_open(e)
        for page,label in enumerate(('状态','配置','库存','建造需求')):
            if page:add('click',widget='critical_tab_'+str(page));wait(18)
            add('assert',side='client',name=e['id']+'.page.'+str(page),actual={'target':{'widget':'critical_pages'},'field':'currentPageIndex'},expected=page)
            shot(e,'page-'+str(page)+'-'+label)
            if page==1 and kind=='CRITICAL_PROCESSING':
                add('click',widget='critical_processing_modes');wait(15);shot(e,'processing-modes-dropdown');add('key',code=1);wait(8)
        add('galleryClose');wait(10)
    extra=[('TCDS_CONTROLLER','tcds','终端'),('STEAM_HUB_ARRAY','steam_hub','Hub'),('WATER_HUB_ARRAY','water_hub','Hub'),('SINGULARITY_DRILLING_HUB','drilling_hub','Hub'),('CLUSTER_CONTROLLER','cluster','Cluster'),('SINGULARITY_CRUST_STEAM_BORER','aggregator','聚合器')]
    for kind,id,cat in extra+[(n,'node_editor.'+n.lower(),'节点编辑器') for n in nodes]:
        e=entry(kind,'terminal','ui.terminal.'+id,cat);bound=kind in nodes
        if bound:e['fixtureNote']='已绑定状态的单控制器视觉fixture：仅设置owned节点绑定状态，无付费绑定/枢纽结构；其后使用生产右键开窗。'
        group_open(e,bound)
        if id=='aggregator':
            # Its production entry is sneaking right-click, exposed by this public factory bridge.
            invoke(ref('galleryMachine'),'openConfigGui',ref('serverPlayer'));wait(25)
        shot(e)
        if id=='cluster':
            for p in range(1,5):add('click',widget='cluster_tab_'+str(p));wait(18);shot(e,'page-'+str(p))
    for kind in ('CRITICAL_LOOM','CRITICAL_PROCESSING'):
        for variant in ('native','terminal'):
            e=next(e for e in inv if e['kind']==kind and e['variant']==variant)
            gid=e['id']+'.rich';e.setdefault('groups',[]).append(gid)
            add('group',name=gid);add('galleryClose');wait(10)
            add('galleryPrepare',side='server',kind=kind,terminal=variant=='terminal');wait(25)
            add('galleryRich',side='server');add('galleryOpen',side='server',id=gid);add('galleryExpect',id=gid,timeoutTicks=100);wait(25)
            for page in range(4 if variant=='terminal' else 1):
                if page:add('click',widget='critical_tab_'+str(page));wait(18)
                shot(e,'rich-page-'+str(page))
            e['fixtureNote']=e.get('fixtureNote','')+' rich-page为受控长状态/高位工作读数/填充背包视觉fixture；保持state=IDLE、未形成，无业务运行。'
    add('group',name='gallery-owned-single-controller-cleanup');add('galleryClose');wait(12);add('galleryCleanup',side='server');wait(12)
    # The maintained source plans supply real inventory/chest and tools/lore fixtures and cleanup.
    for fragment in ('inventory.json','tools-lore.json'):
        plan=json.loads((HERE/'plans'/fragment).read_text(encoding='utf-8'));steps.extend(plan)
    add('group',name='gallery-final-owned-fixture-cleanup');add('galleryClose');wait(12);add('galleryCleanup',side='server');wait(12)
    inv.append(dict(id='ui.terminal.unsealed_chest',title='解封宝箱',category='宝箱',kind='UNSEALED_CHEST',variant='native',route='生产UnsealedChestFactory.open；owned旧27槽NBT fixture',shots=['inventory-chest90-legacy27-loaded','inventory-chest90-reopened']))
    lore=[('tuning_wand','协调棒家族菜单','p6-wand-families-real-item-route'),('machine_debug','协调棒机器配置','p6-wand-construction-air-profile'),('prosperity_journal','繁荣手记','p6-journal-real-S2C-starter'),('prosperity_navigator','繁荣导航器','p6-navigator-real-S2C-dimension78'),('spacetime_altar_story','时空祭坛剧情','p6-altar-authenticated-reading')]
    for id,title,first in lore:
        prefix={'tuning_wand':'p6-wand-','machine_debug':'p6-wand-','prosperity_journal':'p6-journal-','prosperity_navigator':'p6-navigator-','spacetime_altar_story':'p6-altar-'}[id]
        shots=[x['name'] for x in steps if x['op']=='screenshot' and x.get('name','').startswith(prefix)]
        inv.append(dict(id='ui.client.'+id,title=title,category='工具与剧情',kind=id,variant='client',route='正式tools-lore计划中的生产物品/S2C入口；Journal local全权限项单独为视觉fixture',shots=shots,fixtureNote='手记local-unlocked/long-*使用明确的全权限视觉fixture，截图展示不等于权限授予或剧情推进验收。' if id=='prosperity_journal' else ''))
    # Critical narrow run uses the same entry inventory, with explicit prefixed shots.
    full=steps;steps=[];add('group',name='gallery-supplement-enter');enter()
    for kind in nodes:
        e=next(e for e in inv if e['id']=='ui.terminal.node_editor.'+kind.lower())
        group_open(e,True)
        for button,suffix in [('hub_rate','rate-editor')]+([] if 'OUTPUT' in kind else [('hub_capacity','capacity-editor')]):
            add('click',widget=button);wait(18)
            add('assert',side='client',name=e['id']+'.'+suffix+'.page',actual={'target':{'widget':'hub_pages'},'field':'currentPageIndex'},expected=1)
            shot(e,suffix);add('click',widget='hub_numeric_cancel');wait(12)
        if 'OUTPUT' in kind:e['fixtureNote']+=' 输出节点容量编辑按生产规则禁用，不伪造容量页。'
    for kind in ('SINGULARITY_MINER_NODE','SINGULARITY_DRILLING_NODE'):
        e=next(e for e in inv if e['kind']==kind and e['variant']=='native');group_open(e)
        name=e['id']+'.upgrade-cost-tooltip';e['shots'].append(name);add('screenshot',name=name,hover='worker_upgrade')
    e=next(e for e in inv if e['id']=='ui.terminal.tcds');group_open(e)
    name=e['id']+'.formula-tooltip';e['shots'].append(name);add('screenshot',name=name,hover='tcds_formula_help')
    for kind in ('CRITICAL_LOOM','CRITICAL_PROCESSING'):
        e=next(e for e in inv if e['kind']==kind and e['variant']=='native');group_open(e)
        add('galleryRich',side='server');wait(20);add('galleryScrollNative');wait(20);shot(e,'rich-native-long-bottom')
    add('group',name='gallery-resize-narrow');add('galleryClose');wait(12);add('resize',width=427,height=240,scale=2);wait(20)
    supplement=steps;steps=[];add('group',name='gallery-narrow-enter');enter()
    for kind in critical:
        for variant in ('native','terminal'):
            e=next(e for e in inv if e['kind']==kind and e['variant']==variant)
            group_open(e);shot(e,'narrow-overview')
            if variant=='terminal':
                add('scroll',widget='critical_terminal_viewport',notches=8,direction='DOWN');wait(20);shot(e,'narrow-bottom')
                # Put all four tab centres inside the narrow viewport before real hit-test clicks.
                add('drag',widget='critical_terminal_viewport',fromX=.08,fromY=.99,toX=.95,toY=.99);wait(18)
                add('scroll',widget='critical_terminal_viewport',notches=100,direction='UP');wait(18)
                for page in range(1,4):
                    add('click',widget='critical_tab_'+str(page));wait(18)
                    add('assert',side='client',name=e['id']+'.narrow-page.'+str(page),actual={'target':{'widget':'critical_pages'},'field':'currentPageIndex'},expected=page)
                    shot(e,'narrow-page-'+str(page))
    add('group',name='gallery-narrow-cleanup');add('galleryClose');wait(12);add('galleryCleanup',side='server')
    narrow=steps
    suffix='-'+a.revision if a.revision else ''
    files={'gallery-main-plan'+suffix+'.json':full,'gallery-narrow-plan'+suffix+'.json':narrow,'gallery-supplement-plan'+suffix+'.json':supplement+narrow,'gallery-inventory'+suffix+'.json':inv}
    for name,data in files.items():
        dest=out/name
        if dest.exists():raise FileExistsError('Do not overwrite frozen gallery input: '+str(dest))
        dest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    manifest={'status':'PREPARED_NOT_RUN','inventoryCount':len(inv),'machineCount':len(kinds),'criticalCount':len(critical),'unreachable':sum(bool(e.get('reason')) for e in inv),'plans':{n:sha(out/n) for n in files},'sourceFragments':{n:sha(HERE/'plans'/n) for n in ('inventory.json','tools-lore.json')},'scope':'Real GL visual inventory. No formed critical structure, paid upgrade or full business acceptance is claimed.'}
    (out/('gallery-plan-manifest'+suffix+'.json')).write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(manifest,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
