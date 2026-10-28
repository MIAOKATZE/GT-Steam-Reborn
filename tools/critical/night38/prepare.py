"""Prepare one minimal ordinary-Overworld native audit; no launch/Gradle/git."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/critical-night-v2038-client-qa'
SRC=ROOT/'plan/implementation/critical-night-v2038/qa-src'
SANDBOX=ROOT/'build/client-testlab-mui2-v12138'
def main():
    assert not (ROOT.parent/'.gtsr-native-server.lease').exists()
    OUT.mkdir(exist_ok=True)
    for p in (ROOT/'tools/testlab/client_qa').glob('*.java'):shutil.copy2(p,SRC/p.name)
    g=(ROOT/'plan/implementation/critical-work-animation-v2036/qa-src/UiGalleryBatchAnimation36.java').read_text(encoding='utf-8')
    g=g.replace('Object clientAnchor,dummy,displayAnchor,dispatcher,renderer,framebuffer,oldFramebuffer;','Object clientAnchor,dummy,displayAnchor,dispatcher,renderer,framebuffer,oldFramebuffer; Object suppliedDisplayWorld;')
    g=g.replace('dummy=make("blockrenderer6343.client.world.TrackedDummyWorld");','dummy=suppliedDisplayWorld==null?make("blockrenderer6343.client.world.TrackedDummyWorld"):suppliedDisplayWorld;')
    (SRC/'UiGalleryBatchAnimation36.java').write_text(g,encoding='utf-8')
    p=SRC/'Mui2ClientQa.java';s=p.read_text(encoding='utf-8');before='Object listener=this;if("ticket-restart"';assert s.count(before)==1
    p.write_text(s.replace(before,'Object listener=this;if("night38".equals(System.getProperty("gtsr.uiqa.mode"))){listener=new UiGalleryBatchNight38(this);}else if("ticket-restart"'),encoding='utf-8')
    spec=json.loads((ROOT/'build/critical-work-animation-v2036-client-qa/client-launch.json').read_text(encoding='utf-8'))
    spec['command'][1:1]=['-Dmixin.debug.export=true','-Dmixin.debug.export.decompile=false']
    (OUT/'client-launch.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
    s=(ROOT/'build/critical-work-animation-v2036-client-qa/compile-qa.py').read_text(encoding='utf-8');a=s.index('    source = ');b=s.index('\n',a);s=s[:a]+'    source = Path('+repr(str(SRC/'Mui2ClientQa.java'))+')'+s[b:];s=s.replace('classpath = ";".join(str(p) for p in [','classpath = ";".join(str(p) for p in [ROOT / "build/classes/java/patchedMc",');(OUT/'compile-qa.py').write_text(s,encoding='utf-8')
    s=(ROOT/'build/critical-work-animation-v2036-client-qa/run-qa.py').read_text(encoding='utf-8').replace("'animation36']","'animation36','night38']").replace(str(ROOT/'plan/implementation/critical-work-animation-v2036/qa-src'),str(SRC))
    (OUT/'run-qa.py').write_text(s,encoding='utf-8');print(json.dumps(dict(status='PREPARED_NOT_LAUNCHED',workspace=str(OUT),sources=str(SRC)),indent=2))
if __name__=='__main__':main()
