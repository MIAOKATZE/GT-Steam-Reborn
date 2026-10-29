"""Archive only source, JSON/log evidence and untouched PNGs; never compiled helpers/jars."""
from pathlib import Path
import argparse,hashlib,json,shutil,html
from PIL import Image
ROOT=Path(__file__).resolve().parents[3]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--run',required=True);a=ap.parse_args()
 run=ROOT/'build/critical-visual-tooltips-v2039-client-qa/client-runs'/a.run;out=ROOT/'plan/implementation/critical-visual-tooltips-v2039';dest=out/'client-verification'/a.run;dest.mkdir(parents=True,exist_ok=True)
 pictures=ROOT/'plan/图片/临界机器/v2.0.39'/a.run;pictures.mkdir(parents=True,exist_ok=True)
 for p in run.iterdir():
  if p.is_file() and (p.suffix in ['.json','.java'] or p.name in ['stdout.log','memory-samples.jsonl']):shutil.copy2(p,dest/p.name)
 receipt=json.loads((run/'receipt.json').read_text(encoding='utf8'));cp=run/'checks.json';checks=json.loads(cp.read_text(encoding='utf8')) if cp.exists() else {};images=[]
 for p in sorted(run.glob('*.png')):
  shutil.copy2(p,pictures/p.name)
  if p.with_suffix('.json').exists():shutil.copy2(p.with_suffix('.json'),pictures/p.with_suffix('.json').name)
  im=Image.open(p).convert('RGB');pixels=list(im.getdata());fg=[v for v in pixels if v[0]>15 or v[1]>18 or v[2]>25];white=sum(min(v)>=240 for v in pixels)
  images.append(dict(file=str((pictures/p.name).relative_to(ROOT)),sha256=sha(p),size=im.size,foregroundPixels=len(fg),foregroundColors=len(set(fg)),nearWhitePixels=white,nearWhiteForegroundRatio=white/max(1,len(fg))))
 rows=checks.get('checks',[]);summary=dict(status=receipt['status'],run=a.run,candidateSHA256=receipt['candidateSHA256'],helperSHA256=receipt['helperSHA256'],coreSHA256=sha(ROOT/'build/client-testlab-mui2-v12138/mods/production-miaokatzecore.jar'),checks=len(rows),passed=sum(bool(c['pass']) for c in rows),failed=[c for c in rows if not c['pass']],elapsedSeconds=receipt.get('elapsedSeconds'),exitCode=receipt.get('exitCode'),forcedStop=receipt.get('forcedStop'),hiddenDesktop=receipt.get('hiddenDesktop'),heapGiB=receipt.get('heapGiB'),runtimeCrashFindings=receipt.get('runtimeCrashFindings'),scope=checks.get('animationScope'),tooltipRows=len(checks.get('actualItemTooltips',[])),models=checks.get('visualModels'),pictures=images,leaseExists=(ROOT.parent/'.gtsr-native-server.lease').exists())
 (out/('native-summary-'+a.run+'.json')).write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf8')
 cards=''.join('<figure><figcaption>'+html.escape(Path(f['file']).stem)+'</figcaption><img src="'+html.escape(Path(f['file']).name)+'"></figure>' for f in images)
 (pictures/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>v2.0.39 Native</title><style>body{background:#191d22;color:#ddd;font:16px sans-serif;margin:24px}img{max-width:800px;width:100%}figure{display:inline-block;width:45%;vertical-align:top}</style><h1>v2.0.39 Native 动态层</h1><p>受控已建成state2视觉fixture；真实注册控制器/Anchor/S35/TESR/FBO。仅动态层，未宣称物理巨构成型或真实付费业务。PNG原件未裁剪、未重绘。</p>'+cards,encoding='utf8')
 print(json.dumps({k:summary[k] for k in ['status','checks','passed','failed','elapsedSeconds','tooltipRows']},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
