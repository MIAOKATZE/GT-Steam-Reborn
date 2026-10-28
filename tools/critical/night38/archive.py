"""Archive one owned ordinary-world native run; preserve original screenshots and raw history."""
from pathlib import Path
import argparse,hashlib,json,shutil,html
from PIL import Image,ImageStat
ROOT=Path(__file__).resolve().parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--run',required=True);a=ap.parse_args();run=ROOT/'build/critical-night-v2038-client-qa/client-runs'/a.run;out=ROOT/'plan/implementation/critical-night-v2038';dest=out/'client-verification'/a.run;dest.mkdir(parents=True,exist_ok=True);pictures=ROOT/'plan/图片/临界机器/v2.0.38/night-transition';pictures.mkdir(parents=True,exist_ok=True)
 for f in run.iterdir():
  if f.is_file() and (f.suffix in ['.json','.java'] or f.name in ['stdout.log','memory-samples.jsonl']):shutil.copy2(f,dest/f.name)
 receipt=json.loads((run/'receipt.json').read_text(encoding='utf-8'));checks=json.loads((run/'checks.json').read_text(encoding='utf-8'));frames=[]
 for name in ['night-sky','moonless-solar','moonless-loom']:
  if not (run/(name+'.png')).exists():continue
  for ext in ['.png','.json']:
   if (run/(name+ext)).exists():shutil.copy2(run/(name+ext),pictures/(name+ext))
  frames.append(dict(name=name,path=str((pictures/(name+'.png')).relative_to(ROOT)),sha256=sha(pictures/(name+'.png'))))
 comparison={}
 for name in ['moonless-solar','moonless-loom']:
  previous=ROOT/'plan/图片/临界机器/v2.0.37/custom-colors-profile'/(name+'.png');current=run/(name+'.png')
  if not(previous.exists() and current.exists()):continue
  stats={}
  for label,path in [('v37',previous),('v38',current)]:
   im=Image.open(path).convert('RGB');pixels=list(im.getdata());visible=[p for p in pixels if max(p)>8];stats[label]=dict(path=str(path.relative_to(ROOT)),sha256=sha(path),size=im.size,meanRGB=ImageStat.Stat(im).mean,visiblePixels=len(visible),visibleMeanLuma=sum(.2126*p[0]+.7152*p[1]+.0722*p[2] for p in visible)/max(1,len(visible)),visibleUniqueColors=len(set(visible)),nearWhitePixels=sum(min(p)>245 for p in pixels))
  comparison[name]=stats
 summary=dict(imageComparison=comparison,imageComparisonCaveat='Same reused native cameras and scale; both production albedo emission and intended ambient night palette changed, so image luma is visual evidence rather than an isolated material-only measurement.',status=checks['status'],run=a.run,candidateSHA256=receipt['candidateSHA256'],checks=len(checks['checks']),passed=sum(c['pass'] for c in checks['checks']),elapsedSeconds=receipt['elapsedSeconds'],exitCode=receipt['exitCode'],forcedStop=receipt['forcedStop'],scope='Actual owned ordinary Overworld, natural noon baseline, real GT main power and registered region/task owner, two-second visual ON/OFF transitions with native lightmap+sky color samples, actual native star display list and uncropped main framebuffer sky screenshot, two direct TESR dynamic views. Controlled state2/proxy is not physical giant formation. PersonalSpace native validation is historical v37, not rerun here; no native payment/cancellation claim.',fades=checks.get('fadeSamples'),observations=checks.get('moonlessObservations'),codeSources={k:v for k,v in checks.items() if k.startswith('codeSource.') or k=='actualWorldProviderCodeSource'},pictures=frames,leaseExists=(ROOT.parent/'.gtsr-native-server.lease').exists())
 (out/'native-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8');cards=''.join('<figure><figcaption>'+html.escape(f['name'])+'</figcaption><img src="'+f['name']+'.png"></figure>' for f in frames);(pictures/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>v2.0.38 Native</title><style>body{background:#191d22;color:#ddd;font:16px sans-serif;margin:24px}img{max-width:100%;height:auto}</style><h1>v2.0.38 原生采图</h1><p>普通主世界；受控已建成状态及动态proxy，不声明物理巨构建成。night-sky为原生主framebuffer真实天空；其他仅直接TESR动态层。PNG原件未裁剪、未重绘。</p>'+cards,encoding='utf-8');print(json.dumps({k:summary[k] for k in ['status','checks','passed','elapsedSeconds','candidateSHA256']},indent=2))
if __name__=='__main__':main()
