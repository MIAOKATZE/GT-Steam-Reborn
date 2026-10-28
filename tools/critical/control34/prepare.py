"""Prepare isolated terminal-control QA; never launches Minecraft."""
from pathlib import Path
import argparse, hashlib, json, shutil

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / 'plan/implementation/terminal-controls-v2034'
OUT = ROOT / 'build/terminal-controls-v2034-client-qa'
SOURCE = EVIDENCE / 'qa-src'
SANDBOX = ROOT / 'build/client-testlab-mui2-v12138'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--final-ui-only',action='store_true');args=parser.parse_args()
    OUT.mkdir(exist_ok=True)
    for path in (ROOT / 'tools/testlab/client_qa').glob('*.java'):
        if path.name != 'UiGalleryBatchV2034.java':
            shutil.copy2(path, SOURCE / path.name)
    p = SOURCE / 'UiGalleryBatch.java'
    text = p.read_text(encoding='utf-8')
    changes = {
      'final Mui2ClientQa q;': 'final Mui2ClientQa q;\n    final UiGalleryBatchV2034 v2034=new UiGalleryBatchV2034(this);',
      'if(v209.client())return;': 'if(v2034.client())return;\n            if(v209.client())return;',
      'v2014.configure();': 'v2014.configure();v2034.configure();',
      'v2014.prepareFixture();': 'v2014.prepareFixture();v2034.prepareFixture();',
      'v2014.beforeCapture();': 'v2014.beforeCapture();v2034.beforeCapture();',
      'v2014.afterCapture(event);': 'v2014.afterCapture(event);v2034.afterCapture(event);',
      'void afterNavigation()throws Exception {': 'void afterNavigation()throws Exception {\n        if(v2034.afterNavigation())return;',
      'if(request==1){Object configuration=': 'if(request>=340&&request<=349){v2034.server();return;}\n            if(request==1){Object configuration=',
      'else if(request==9){call(q.serverPlayer,': 'else if(request==9){v2034.cleanup();call(q.serverPlayer,',
      'openServer();Object container=': 'v2034.prepareFixture();openServer();Object container=',
      'Object tab=q.find(q.panel,isEntanglement(row)?': 'if(str(row,"kind","").startsWith("CRITICAL_")&&!str(row,"kind","").equals("CRITICAL_LOOM"))prefix="critical_main_tab_";Object tab=q.find(q.panel,isEntanglement(row)?',
    }
    for before, after in changes.items():
        assert text.count(before)==1, (before, text.count(before))
        text = text.replace(before,after)
    p.write_text(text,encoding='utf-8')
    spec=json.loads((ROOT/'build/plasma-v2033-client-qa/client-launch.json').read_text(encoding='utf-8'))
    assert Path(spec['command'][spec['command'].index('--gameDir')+1]).resolve()==SANDBOX
    (OUT/'client-launch.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
    for name, original in [('compile-qa.py',ROOT/'build/plasma-v2033-client-qa/compile-qa.py'),('run-qa.py',ROOT/'tools/testlab/client_qa/run.py')]:
        text=original.read_text(encoding='utf-8').replace("ROOT=Path(__file__).resolve().parents[3]",'ROOT=Path('+repr(str(ROOT))+')')
        if name=='compile-qa.py':
            start=text.index('    source = '); end=text.index('\n',start)
            text=text[:start]+'    source = Path('+repr(str(SOURCE/'Mui2ClientQa.java'))+')'+text[end:]
        (OUT/name).write_text(text,encoding='utf-8')
    rows=[]
    for kind in ['SOLAR','TURBINE','SUN','BATTERY','LOOM']:
        for page in ([0,1,2] if kind=='LOOM' else [1,2]):
            row=dict(id='control34.'+kind.lower()+'.'+str(page),name='control34.'+kind.lower()+'.'+str(page),v2034=True,kind='CRITICAL_'+kind,variant='terminal',page=page,width=1280,height=720,scale=2,minimumFrames=3,timeoutSeconds=45,hover='critical_action_power',note='Controlled display state on native controller for actual button/S2C; not physical formation')
            if page==1 and kind!='LOOM':row['toggleProbe']=True
            rows.append(row)
    for status in ['power_off','solar.missing_working_fluid','solar.missing_coolant','solar.missing_exciter','working','blocked']:
        rows.append(dict(id='control34.solar.'+status,name='control34.solar.'+status,v2034=True,visualStatus=status,kind='CRITICAL_SOLAR',variant='terminal',page=1,width=1280,height=720,scale=2,minimumFrames=3,timeoutSeconds=45,note='CONTROLLED UI STATE: inert owned genuine GT controller with state2/typed blocker fixture; no formation or real production claim'))
    rows.append(dict(id='control34.actual-solar',name='control34.actual-solar',v2034=True,actualSolar=True,kind='CRITICAL_SOLAR',variant='terminal',page=1,width=1280,height=720,scale=2,minimumFrames=3,timeoutSeconds=420,note='Actual TestLab construction, native GT hatches, production400tick with pause/resume; no fake formation'))
    if args.final_ui_only:
        rows=[r for r in rows if r.get('toggleProbe') or r.get('visualStatus')]
        template=dict(v2034=True,variant='terminal',page=1,width=1280,height=720,scale=2,minimumFrames=3,timeoutSeconds=45,note='CONTROLLED UI STATE: frozen owned native GT tile; production snapshot assembly/S2C genuine; no physical construction or paid stellar session claim')
        for kind,status in [('SOLAR','not_built'),('SUN','sun.igniting'),('SUN','sun.production_paused'),('SUN','sun.waiting_recipe')]:
            rows.append(dict(template,id='control34.final.'+status,name='control34.final.'+status,kind='CRITICAL_'+kind,visualStatus=status))
        for page in [0,1]:
            rows.append(dict(template,id='control34.final.loom_ready.'+str(page),name='control34.final.loom_ready.'+str(page),kind='CRITICAL_LOOM',visualStatus='loom_ready',page=page))
    plan=OUT/'control34.json';plan.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    plan.with_suffix('.manifest.json').write_text(json.dumps(dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(),captures=len(rows),status='PREPARED_NOT_LAUNCHED',heapGiB=3,hiddenDesktopRequired=True),indent=2),encoding='utf-8')
    print(json.dumps(dict(workspace=str(OUT),sandbox=str(SANDBOX),plan=str(plan),ownershipLeaseExists=(ROOT.parent/'.gtsr-native-server.lease').exists()),indent=2))

if __name__=='__main__':main()
