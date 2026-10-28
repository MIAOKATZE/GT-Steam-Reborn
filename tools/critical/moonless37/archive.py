"""Archive the existing minimal native profile, original PNG/JSON and scope."""
from pathlib import Path
import argparse,hashlib,html,json,shutil
ROOT=Path(__file__).resolve().parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--run',required=True);p.add_argument('--profile',required=True);a=p.parse_args()
 source=ROOT/'build/critical-moonless-v2037-client-qa/client-runs'/a.run;out=ROOT/'plan/implementation/critical-moonless-v2037';dest=out/'client-verification'/a.run;dest.mkdir(parents=True,exist_ok=True)
 for f in source.iterdir():
  if f.is_file() and (f.suffix in ['.json','.java'] or f.name in ['stdout.log','memory-samples.jsonl','helper.jar']):shutil.copy2(f,dest/f.name)
 receipt=json.loads((source/'receipt.json').read_text(encoding='utf-8'));checks=json.loads((source/'checks.json').read_text(encoding='utf-8'));pictures=ROOT/'plan/图片/临界机器/v2.0.37'/a.profile;pictures.mkdir(parents=True,exist_ok=True);frames=[]
 for name in ['moonless-solar','moonless-loom']:
  for suffix in ['.png','.json']:shutil.copy2(source/(name+suffix),pictures/(name+suffix))
  frames.append(dict(name=name,png=str((pictures/(name+'.png')).relative_to(ROOT)),sha256=sha(pictures/(name+'.png'))))
 candidate=ROOT/'build/client-testlab-mui2-v12138/mods/production-gtsr.jar'
 if sha(candidate)==receipt['candidateSHA256']:shutil.copy2(candidate,dest/'production-gtsr.jar')
 summary=dict(status=checks['status'],profile=a.profile,run=a.run,candidateSHA256=receipt['candidateSHA256'],checks=len(checks['checks']),passed=sum(c['pass'] for c in checks['checks']),elapsedSeconds=receipt['elapsedSeconds'],exitCode=receipt['exitCode'],forcedStop=receipt['forcedStop'],nativeScope='Actual PersonalSpace1.0.41 API-created dimension/provider, independent client/server cfg, real GT paired controller journals and WorldData region/task registration; controlled completion without physical giant formation. Sky/mutual exclusion/ON-no-fuel/OFF release, native lightmap and2 dynamic FBO views only. GUI/payment/cancellation covered separately by final jar-only contracts; not claimed native here.',observations=checks['moonlessObservations'],codeSources={k:v for k,v in checks.items() if k.startswith('codeSource.') or k=='personalSpaceProviderCodeSource'},pictures=frames,immediateClientOffTimeCaveat='Client OFF snapshot may precede the next vanilla time packet (18000 recorded); fixed-SUN provider/day/angle/light return to baseline and server raw time6000 is separately checked.',leaseExists=(ROOT.parent/'.gtsr-native-server.lease').exists())
 (out/(a.profile+'-summary.json')).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
 cards=''.join('<figure><figcaption>'+html.escape(f['name'])+'</figcaption><img src="'+f['name']+'.png"></figure>' for f in frames)
 (pictures/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>v2.0.37暗场原生动态构件</title><style>body{background:#191d22;color:#ddd;font:16px sans-serif;margin:24px}img{max-width:800px;width:100%;height:auto}</style><h1>v2.0.37 原生暗场动态构件</h1><p>真实PersonalSpace实验维度；受控成型与满尺寸proxy，不声明物理巨型结构建完。原生lightmap启用、sky0/block保留；仅动态TESR，不含静态全景。PNG保持未裁剪、未重绘。</p>'+cards,encoding='utf-8')
 print(json.dumps({k:summary[k] for k in ['status','checks','passed','elapsedSeconds','candidateSHA256']},indent=2))
if __name__=='__main__':main()
