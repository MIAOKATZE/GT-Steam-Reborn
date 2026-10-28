"""Read actual native Mixin exports: lightmap wrapper plus native star display-list weaving."""
from pathlib import Path
import argparse,hashlib,json,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[3]
JAVA=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javap.exe'
TARGETS={
 'net/minecraft/world/World':['MixinWorldCriticalTime','WorldCriticalNightClientMixin'],
 'net/minecraft/world/WorldProvider':['MixinWorldProviderCriticalNight','WorldProviderCriticalNightClientMixin'],
 'net/minecraft/client/renderer/RenderGlobal':['RenderGlobalCriticalNightMixin'],
 'net/minecraft/client/renderer/EntityRenderer':['EntityRendererCriticalNightMixin']}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def methods(text):
 starts=list(re.finditer(r'^  (?:public|private|protected|static).*\([^\n]*\);$',text,re.M))
 return [text[m.start():starts[i+1].start() if i+1<len(starts) else len(text)] for i,m in enumerate(starts)]
def main():
 p=argparse.ArgumentParser();p.add_argument('--run',required=True);a=p.parse_args()
 run=ROOT/'build/critical-night-v2038-client-qa/client-runs'/a.run
 launch=json.loads((run/'launch.json').read_text(encoding='utf-8'));epoch=int(next(x.split('=',1)[1] for x in launch['command'] if x.startswith('-Dgtsr.uiqa.launchEpochMillis=')))/1000
 source=ROOT/'build/client-testlab-mui2-v12138/.mixin.out/class';out=ROOT/'plan/implementation/critical-night-v2038'/('export-audit-'+a.run);out.mkdir(exist_ok=True);raw=run/'class-exports';raw.mkdir(exist_ok=True)
 results={};texts={};passed=True
 for target,mixins in TARGETS.items():
  path=source/(target+'.class');dest=raw/(target.replace('/','-')+'.class');shutil.copy2(path,dest)
  r=subprocess.run([str(JAVA),'-J-Dfile.encoding=UTF-8','-p','-c',str(path)],capture_output=True,encoding='utf-8',errors='replace',check=True);texts[target]=r.stdout;(out/(target.replace('/','-')+'.javap.txt')).write_text(r.stdout,encoding='utf-8')
  v=subprocess.run([str(JAVA),'-J-Dfile.encoding=UTF-8','-p','-v',str(path)],capture_output=True,encoding='utf-8',errors='replace',check=True)
  applied={m:('com.miaokatze.gtsr.mixin.'+m in v.stdout or 'com/miaokatze/gtsr/mixin/'+m in v.stdout) for m in mixins}
  fresh=path.stat().st_mtime>=epoch-1;passed&=fresh and all(applied.values());results[target]=dict(sha256=sha(path),rawClassBuildPath=str(dest.relative_to(ROOT)),freshForRun=fresh,appliedMixins=applied)
 entity=texts['net/minecraft/client/renderer/EntityRenderer'];blocks=methods(entity);update=next(b for b in blocks if re.search(r'(?:updateLightmap|func_78472_g)\(float\)',b.splitlines()[0]));hook=next(b for b in blocks if 'zeroSkyLight' in b.splitlines()[0]);darken='CriticalNightClient.blendNightLight' in hook;upload=bool(re.search(r'DynamicTexture\.(?:updateDynamicTexture|func_110564_a)',hook));wrapped='Operation' in hook.splitlines()[0]
 if wrapped:
  body=next(b for b in blocks if 'func_78472_g$mixinextras$wrapped$' in b.splitlines()[0] and 'CallbackInfo.isCancelled' in b);bridge=next(b for b in blocks if 'mixinextras$bridge$func_78472_g' in b.splitlines()[0]);returns=re.findall(r'^\s*\d+: return\s*$',body,re.M)
  hookName=re.search(r'void ([^ (]+)',hook.splitlines()[0]).group(1);bodyName=re.search(r'void ([^ (]+)',body.splitlines()[0]).group(1);bridgeName=re.search(r'java.lang.Void ([^ (]+)',bridge.splitlines()[0]).group(1)
  order=hook.find('Operation.call')>=0 and hook.find('Operation.call')<hook.find('CriticalNightClient.blendNightLight')<hook.find('DynamicTexture.func_110564_a')
  verbose=subprocess.run([str(JAVA),'-J-Dfile.encoding=UTF-8','-p','-v',str(source/'net/minecraft/client/renderer/EntityRenderer.class')],capture_output=True,encoding='utf-8',errors='replace',check=True).stdout;(out/'EntityRenderer.verbose.txt').write_text(verbose,encoding='utf-8')
  bootstrap=verbose.split('BootstrapMethods:',1)[1];bootstrapZero=bootstrap.split('\n  1:',1)[0];binding=bridgeName in bootstrapZero and bodyName in bridge and hookName in update and 'invokedynamic' in update
  cancel='angelica$modifyUpdateLightMap' in body and 'CallbackInfo.isCancelled' in body and len(returns)>=2
  passed&=order and binding and cancel and darken and upload
  proof=dict(mode='WRAP_METHOD',entryCallsWrapper=hookName in update,operationBridgeBindsWrappedBody=binding,operationThenNightBlendThenUpload=order,wrappedOriginalReturnCount=len(returns),angelicaHeadCancelPresent=cancel,wrappedOriginalReturns=[s.strip() for s in returns],wrapper=hook,entry=update,bridge=bridge,computeLightmapTrueAtRuntime='UNKNOWN_NOT_INSTRUMENTED_NO_RESOURCE_PACK_ADDED')
 else:
  lines=update.splitlines();returns=[]
  for i,line in enumerate(lines):
   if re.search(r'^\s*\d+: return\s*$',line):
    window=lines[max(0,i-14):i];returns.append(dict(instruction=line.strip(),skyZeroHookBeforeReturn=any('invoke' in s and 'zeroSkyLight' in s for s in window)))
  passed&=bool(returns) and all(r['skyZeroHookBeforeReturn'] for r in returns) and darken and upload
  proof=dict(mode='NORMAL_RETURN_ONLY',returns=returns,customColorBranch='NOT_PRESENT_NOT_RUN')
 starHook=next(b for b in methods(texts['net/minecraft/client/renderer/RenderGlobal']) if 'starryNight' in b.splitlines()[0]);starList='GL11.glCallList' in starHook and ('field_72772_v' in starHook or 'starGLCallList' in starHook);passed&=starList
 result=dict(starRendering=dict(nativeStarListInvoked=starList,wrapper=starHook),status='PASS' if passed else 'FAIL',candidateSHA256=launch['candidateSHA256'],run=a.run,targets=results,entityRenderer=proof,scope='Actual native exported class weaving. WrapMethod: entry invokes wrapper, Operation bridge invokes wrapped original with Angelica early return and normal return, both resume wrapper ordinary-midnight blend/upload. compute=true execution is not inferred from structural coverage.')
 (out/'receipt.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2));raise SystemExit(0 if passed else 1)
if __name__=='__main__':main()
