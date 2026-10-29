from pathlib import Path
import json
R=Path('E:/CodeGT/GT-Steam-Reborn'); W=R/'build/critical-visual-tooltips-v2039-client-qa'; S=R/'plan/implementation/critical-visual-tooltips-v2039/qa-src'
assert not (R.parent/'.gtsr-native-server.lease').exists(), 'Owned native lease is active'
p=S/'Mui2ClientQa.java';s=(R/'plan/implementation/critical-night-v2038/qa-src/Mui2ClientQa.java').read_text(encoding='utf8');s=s.replace('if("night38".equals(System.getProperty("gtsr.uiqa.mode")))','if("visual39".equals(System.getProperty("gtsr.uiqa.mode"))){listener=new UiGalleryBatchVisual39(this);}else if("night38".equals(System.getProperty("gtsr.uiqa.mode")))');p.write_text(s,encoding='utf8')
for n in ['compile-qa.py','run-qa.py','client-launch.json']:
 s=(R/'build/critical-night-v2038-client-qa'/n).read_text(encoding='utf8').replace('critical-night-v2038','critical-visual-tooltips-v2039').replace('critical-work-animation-v2036\\\\qa-src','critical-visual-tooltips-v2039\\\\qa-src')
 if n=='run-qa.py':s=s.replace("'night38']","'night38','visual39']")
 (W/n).write_text(s,encoding='utf8')
print('PREPARED_NOT_LAUNCHED')
