"""Prepare one owned native animation audit; never launch Minecraft or Gradle."""
from pathlib import Path
import json, shutil

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'build/critical-work-animation-v2036-client-qa'
SOURCE=ROOT/'plan/implementation/critical-work-animation-v2036/qa-src'
SANDBOX=ROOT/'build/client-testlab-mui2-v12138'

def main():
    if (ROOT.parent/'.gtsr-native-server.lease').exists():raise RuntimeError('Owned client/server lease exists; do not replace helper')
    assert 'public final class UiGalleryBatchAnimation36' in (SOURCE/'UiGalleryBatchAnimation36.java').read_text(encoding='utf-8'), 'FML ASM subscriber must be public across its event classloader'
    OUT.mkdir(exist_ok=True)
    for path in (ROOT/'tools/testlab/client_qa').glob('*.java'):shutil.copy2(path,SOURCE/path.name)
    p=SOURCE/'Mui2ClientQa.java';body=p.read_text(encoding='utf-8')
    before='Object listener=this;if("ticket-restart"'
    assert body.count(before)==1
    body=body.replace(before,'Object listener=this;if("animation36".equals(System.getProperty("gtsr.uiqa.mode"))){listener=new UiGalleryBatchAnimation36(this);}else if("ticket-restart"')
    p.write_text(body,encoding='utf-8')
    spec=json.loads((ROOT/'build/terminal-controls-v2034-client-qa/client-launch.json').read_text(encoding='utf-8'))
    assert Path(spec['command'][spec['command'].index('--gameDir')+1]).resolve()==SANDBOX
    (OUT/'client-launch.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
    body=(ROOT/'build/terminal-controls-v2034-client-qa/compile-qa.py').read_text(encoding='utf-8')
    start=body.index('    source = ');end=body.index('\n',start)
    body=body[:start]+'    source = Path('+repr(str(SOURCE/'Mui2ClientQa.java'))+')'+body[end:]
    (OUT/'compile-qa.py').write_text(body,encoding='utf-8')
    body=(ROOT/'tools/testlab/client_qa/run.py').read_text(encoding='utf-8').replace('ROOT=Path(__file__).resolve().parents[3]','ROOT=Path('+repr(str(ROOT))+')')
    body=body.replace("choices=['ui','gallery','gallery-batch','ticket-restart']","choices=['ui','gallery','gallery-batch','ticket-restart','animation36']")
    body=body.replace("f'fullscreen:false\\npauseOnLostFocus:false", "f'particles:0\\nfboEnable:true\\nfullscreen:false\\npauseOnLostFocus:false")
    body=body.replace("for source in Path(__file__).parent.glob('*.java'):","for source in Path("+repr(str(SOURCE))+").glob('*.java'):")
    (OUT/'run-qa.py').write_text(body,encoding='utf-8')
    print(json.dumps(dict(status='PREPARED_NOT_LAUNCHED',workspace=str(OUT),sandbox=str(SANDBOX),sources=str(SOURCE),heapGiB=3,hiddenDesktopRequired=True,guiReuse=False),indent=2))

if __name__=='__main__':main()
