"""One requested native launch with owned custom-colors config; always restore exact original bytes."""
from pathlib import Path
import hashlib,json,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[3]
def main():
 out=ROOT/'plan/implementation/critical-night-v2038';cfg=ROOT/'build/client-testlab-mui2-v12138/config/mcpatcherforge.cfg';original=cfg.read_bytes();(out/'owned-mcpatcherforge-before-custom.cfg').write_bytes(original)
 text=original.decode('utf-8');m=re.search(r'(?ms)(^custom_colors\s*\{)(.*?)(^\})',text);assert m
 body=m.group(2);changed,n=re.subn(r'(?m)^(\s*B:(?:enabled|lightmaps)=)(?:true|false)\s*$',lambda x:x.group(1)+'true',body);assert n==2
 updated=(text[:m.start(2)]+changed+text[m.end(2):]).encode('utf-8');temp=cfg.with_suffix('.night38.tmp');temp.write_bytes(updated);temp.replace(cfg)
 receipt=dict(originalSHA256=hashlib.sha256(original).hexdigest(),customSHA256=hashlib.sha256(updated).hexdigest(),customColors=True,lightmaps=True,resourcePacksAdded=False,restored=False)
 try:
  cmd=[sys.executable,str(ROOT/'build/critical-night-v2038-client-qa/run-qa.py'),'--workspace',str(ROOT/'build/critical-night-v2038-client-qa'),'--client-sandbox',str(ROOT/'build/client-testlab-mui2-v12138'),'--jar',str(ROOT/'build/v2038-release/candidate-runtime.jar'),'--mode','night38','--run','night-v2038-r4','--language','zh_CN','--hidden-desktop','--heap-gib','3','--reserve-gib','2','--render-distance','3','--deadline','900']
  code=subprocess.call(cmd,cwd=ROOT);receipt['runnerExitCode']=code
 finally:
  temp.write_bytes(original);temp.replace(cfg);receipt['restored']=cfg.read_bytes()==original;(out/'owned-custom-config-lifecycle.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
 print(json.dumps(receipt,indent=2));raise SystemExit(receipt.get('runnerExitCode',1))
if __name__=='__main__':main()
