"""Prepare the owned v218 scene HUD helper and private-desktop runner; never launch Minecraft."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, zipfile

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/prosperity-v218-native-qa'
FIXTURE=ROOT/'build/client-testlab-task03-gt265'
SOURCE=OUT/'qa-src'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def once(text,old,new):
    assert text.count(old)==1,old
    return text.replace(old,new)
def main():
    p=argparse.ArgumentParser();p.add_argument('--compile',action='store_true');a=p.parse_args()
    if (ROOT.parent/'.gtsr-native-server.lease').exists():raise RuntimeError('Native lease exists; do not replace owned helper')
    OUT.mkdir(exist_ok=True);SOURCE.mkdir(exist_ok=True)
    spec=json.loads((ROOT/'build/critical-repair-v216/client-launch.json').read_text(encoding='utf8'))
    assert Path(spec['command'][spec['command'].index('--gameDir')+1]).resolve()==FIXTURE
    required_mods=['gregtech-5.09.54.265.jar','GTNewHorizonsCoreMod-2.9.99.jar','modularui2-2.3.94-1.7.10.jar','miaokatzecore-v0.2.4.jar']
    assert all((FIXTURE/'mods'/name).is_file() for name in required_mods)
    (OUT/'client-launch.json').write_text(json.dumps(spec,indent=2),encoding='utf8')
    for path in (ROOT/'tools/testlab/client_qa').glob('*.java'):shutil.copy2(path,SOURCE/path.name)
    shutil.copy2(Path(__file__).with_name('SceneBossNativeQa.java'),SOURCE/'SceneBossNativeQa.java')
    shutil.copy2(ROOT/'tools/prosperity/SceneBossHudProbe.java',SOURCE/'SceneBossHudProbe.java')
    base=SOURCE/'Mui2ClientQa.java';text=base.read_text(encoding='utf8')
    text=once(text,'    UiGalleryBatch galleryBatch;','    UiGalleryBatch galleryBatch;\n    SceneBossNativeQa prosperity218;')
    text=once(text,'Object listener=this;if("ticket-restart"','Object listener=this;if("prosperity218".equals(System.getProperty("gtsr.uiqa.mode"))){prosperity218=new SceneBossNativeQa(this);listener=prosperity218;}else if("ticket-restart"')
    for name,event in [('stopping','Stopping'),('stopped','Stopped')]:
        marker=f'public void {name}(FMLServer{event}Event event) {{'
        text=once(text,marker,marker+f'\n        if(prosperity218!=null){{prosperity218.{name}();return;}}')
    base.write_text(text,encoding='utf8')
    wire=SOURCE/'UiQaWireCounter.java';text=wire.read_text(encoding='utf8')
    text=once(text,'        owner.receipt.addProperty("packetMeasurementScope"','        owner.receipt.addProperty("nativeNetworkChannelClass",channel.getClass().getName());\n        owner.receipt.addProperty("packetMeasurementScope"')
    text=once(text,'            if(direction.equals("S2C")&&channel.equals("modularui2"))observeMuiOpen(payload);','            if(direction.equals("S2C")&&channel.equals("modularui2"))observeMuiOpen(payload);\n            if(owner.prosperity218!=null&&direction.equals("S2C")&&channel.equals("gtsr_encounter"))owner.prosperity218.observePacket(payload);')
    wire.write_text(text,encoding='utf8')
    runner=(ROOT/'tools/testlab/client_qa/run.py').read_text(encoding='utf8')
    runner=once(runner,'ROOT=Path(__file__).resolve().parents[3]','ROOT=Path('+repr(str(ROOT))+')')
    runner=once(runner,"choices=['ui','gallery','gallery-batch','ticket-restart']","choices=['ui','gallery','gallery-batch','ticket-restart','prosperity218']")
    runner=once(runner,"p.add_argument('--keep-open'","p.add_argument('--expect-stable-pause',action='store_true');p.add_argument('--keep-open'")
    runner=once(runner,"p.add_argument('--keep-open'","p.add_argument('--reuse-owned-world');p.add_argument('--keep-open'")
    runner=once(runner,"p.add_argument('--keep-open'","p.add_argument('--probe-legacy-chest',action='store_true');p.add_argument('--keep-open'")
    runner=once(runner,"    available=free_memory();required=", "    if a.reuse_owned_world:\n        if not re.fullmatch(r'Mui2Audit-baseline217-hud-[a-zA-Z0-9_-]+',a.reuse_owned_world) or not (FIXTURE/'saves'/a.reuse_owned_world/'level.dat').is_file():raise ValueError('Only a known existing owned baseline217 HUD world may be reused')\n    available=free_memory();required=")
    runner=once(runner,"'-Dgtsr.uiqa.world=Mui2Audit-'+a.run","'-Dgtsr.uiqa.world='+(a.reuse_owned_world or 'Mui2Audit-'+a.run)")
    runner=once(runner,"cmd[1:1]=['-Dgtsr.uiqa.mode='","cmd[1:1]=['-Dgtsr.native218.expectStablePause='+str(a.expect_stable_pause).lower(),'-Dgtsr.uiqa.mode='")
    runner=once(runner,"cmd[1:1]=['-Dgtsr.native218.expectStablePause='","cmd[1:1]=['-Dgtsr.native218.probeLegacyChest='+str(a.probe_legacy_chest).lower(),'-Dgtsr.native218.expectStablePause='")
    runner=once(runner,"    if a.heap_gib>5:","    if not a.hidden_desktop:raise ValueError('v218 owned scene QA requires --hidden-desktop')\n    if a.heap_gib>5:")
    runner=once(runner,'destination.chmod(0o666);shutil.copy2(a.jar,destination)','\n        if destination.exists():destination.chmod(0o666)\n        shutil.copy2(a.jar,destination)')
    runner=once(runner,"for source in Path(__file__).parent.glob('*.java'):","for source in Path("+repr(str(SOURCE))+").glob('*.java'):")
    runner=once(runner,'        destination=FIXTURE/\'mods/production-gtsr.jar\';',"        receipt['replacedOwnedProductionJar']=dict(path=str(FIXTURE/'mods/production-gtsr.jar'),beforeSHA256=sha(FIXTURE/'mods/production-gtsr.jar') if (FIXTURE/'mods/production-gtsr.jar').exists() else None)\n        destination=FIXTURE/'mods/production-gtsr.jar';")
    runner=once(runner,"inputType='Synthetic actual ModularScreen hit-test/key/drag dispatch, lwjgl3ify injectTextEvent, legacy GuiScreen.keyTyped and native PlayerController.windowClick; physical mouse/keyboard/IME NOT_RUN'","inputType='Owned synthetic native scene placement/teleports; actual LocalChannel production packets and GL HUD; physical input NOT_RUN'")
    (OUT/'run-qa.py').write_text(runner,encoding='utf8');compile(runner,str(OUT/'run-qa.py'),'exec')
    previous_helper=FIXTURE/'mods/gtsr-mui2-qa-owned-only.jar'
    previous_hash=sha(previous_helper)
    backup=OUT/('helper-before-'+previous_hash+'.jar')
    if not backup.exists():shutil.copy2(previous_helper,backup)
    receipt=dict(status='PREPARED_NOT_RUN',sandbox=str(FIXTURE),hiddenDesktopRequired=True,sources={p.name:sha(p) for p in sorted(SOURCE.glob('*.java'))},replacedHelperBeforeSHA256=previous_hash,previousHelperBackup=str(backup),runtimeMods={name:sha(FIXTURE/'mods'/name) for name in required_mods})
    with (OUT/'helper-replacements.jsonl').open('a',encoding='utf8') as history:history.write(json.dumps(dict(beforeSHA256=receipt['replacedHelperBeforeSHA256'],sources=receipt['sources']))+'\n')
    if a.compile:
        classes=OUT/('qa-classes-'+hashlib.sha256(json.dumps(receipt['sources'],sort_keys=True).encode()).hexdigest()[:12]);classes.mkdir(exist_ok=True)
        cp=spec['command'][spec['command'].index('-cp')+1]+';'+';'.join(str(p) for p in sorted((FIXTURE/'mods').glob('*.jar')))
        options=['-encoding','UTF-8','-proc:none','--release','8','-cp',cp,'-d',str(classes),*map(str,sorted(SOURCE.glob('*.java')))];argfile=OUT/'qa-compile.args'
        argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in options),encoding='utf8')
        javac=Path(spec['command'][0]).with_name('javac.exe');r=subprocess.run([str(javac),'-J-Duser.language=en','@'+str(argfile)],capture_output=True,text=True,encoding='utf8',errors='replace');(OUT/'qa-compile.log').write_text(r.stdout+r.stderr,encoding='utf8')
        if r.returncode:raise RuntimeError(r.stderr)
        helper=FIXTURE/'mods/gtsr-mui2-qa-owned-only.jar'
        with zipfile.ZipFile(helper,'w') as z:
            for path in sorted(classes.rglob('*.class')):z.writestr(zipfile.ZipInfo(path.relative_to(classes).as_posix(),date_time=(1980,1,1,0,0,0)),path.read_bytes())
        receipt.update(status='COMPILED_NOT_RUN',jarSHA256=sha(helper),classCount=len(list(classes.rglob('*.class'))));(OUT/'qa-compile-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf8')
    (OUT/'prepare-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf8');print(json.dumps({k:receipt[k] for k in ['status','sandbox','hiddenDesktopRequired']},indent=2))
if __name__=='__main__':main()
