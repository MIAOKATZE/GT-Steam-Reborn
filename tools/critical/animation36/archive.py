"""Archive native animation checks and original PNGs; GIF uses captured frames only."""
from pathlib import Path
import argparse,hashlib,html,json,shutil

ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'build/critical-work-animation-v2036-client-qa/client-runs'
OUT=ROOT/'plan/implementation/critical-work-animation-v2036'
PICTURES=ROOT/'plan/图片/临界机器/v2.0.36/working-animation'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text(encoding='utf-8'))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--final-run',required=True);args=parser.parse_args()
    runs=list(dict.fromkeys(['animation-v2036-r1','animation-v2036-r2','animation-v2036-r3','animation-v2036-r4',args.final_run]))
    for run in runs:
        source=BASE/run;dest=OUT/'client-verification'/run;dest.mkdir(parents=True,exist_ok=True)
        for p in source.glob('*.json'):shutil.copy2(p,dest/p.name)
        for name in ['UiGalleryBatchAnimation36.java','Mui2ClientQa.java','helper.jar','memory-samples.jsonl']:
            if (source/name).exists():shutil.copy2(source/name,dest/name)
        log=source/'stdout.log'
        if run not in ['animation-v2036-r1','animation-v2036-r2']:shutil.copy2(log,dest/'stdout.log')
        else:
            lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
            selected=[dict(line=i+1,text=line) for i,line in enumerate(lines) if 'java.lang.IllegalAccessError:' in line][:3]
            (dest/'raw-log-findings.json').write_text(json.dumps(dict(originalPath=str(log),bytes=log.stat().st_size,sha256=sha(log),findings=selected,manualOwnedStop=True,ownedPID=32232 if run.endswith('r1') else 23468),indent=2),encoding='utf-8')
            if (source/'owned-thread-sample.txt').exists():shutil.copy2(source/'owned-thread-sample.txt',dest/'owned-thread-sample.txt')
    run=BASE/args.final_run;receipt=read(run/'receipt.json');checks=read(run/'checks.json')
    PICTURES.mkdir(parents=True,exist_ok=True);pictures=[]
    for frame in checks.get('animationFrames',[]):
        p=run/(frame['name']+'.png');target=PICTURES/p.name;shutil.copy2(p,target)
        pictures.append(dict(name=frame['name'],phase=frame['phase'],cycleTicks=frame['cycleTicks'],sha256=sha(target),path=str(target.relative_to(ROOT))))
    work=[PICTURES/('solar-working-'+str(t)+'.png') for t in [20,100,220,320]]
    if all(p.exists() for p in work):
        from PIL import Image
        images=[Image.open(p).convert('RGB') for p in work]
        images[0].save(PICTURES/'solar-working-captured-frames.gif',save_all=True,append_images=images[1:],duration=650,loop=0)
    summary=dict(status=checks['status'],finalRun=args.final_run,candidateSHA256=receipt['candidateSHA256'],exitCode=receipt.get('exitCode'),checks=len(checks.get('checks',[])),passed=sum(c['pass'] for c in checks.get('checks',[])),frames=pictures,scope=checks.get('animationScope'),manualOwnedStopR1=True,manualOwnedStopR2=True,previousFailures='R1 listener visibility; R2 unnecessary static scene layer; R3 missing initial authoritative descriptor gate; R4 wrong frozen model slug assertion. All original FAIL receipts retained; none is counted as a passing native animation run.',finalRunForcedStop=receipt.get('forcedStop'),genericRunnerInputTypeCaveat='The reused generic runner inputType text mentions ModularScreen input; this task executes no GUI or physical input. Actual scope and assertions are in animationScope/animationFrames/nativeServerSamples.',noUserWorlds=True,heapGiB=3,hiddenDesktop=receipt.get('hiddenDesktop'))
    (OUT/'native-animation-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    cards=''.join('<figure><figcaption>'+html.escape(p['name'])+' — 实际描述符进度 '+str(p['cycleTicks'])+'/400</figcaption><img src="'+Path(p['path']).name+'"></figure>' for p in pictures)
    gif='<p><a href="solar-working-captured-frames.gif">4张工作关键帧组成的GIF预览（非实时录像，不表示实际帧率或批次用时）</a></p>' if all(p.exists() for p in work) else ''
    (PICTURES/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>v2.0.36锅炉工作动画</title><style>body{background:#1c2025;color:#ddd;font:16px sans-serif;margin:24px}img{max-width:800px;width:100%;height:auto}figure{margin:24px 0}</style><h1>v2.0.36锅炉工作动画</h1><p>受控成型与满尺寸proxy展示，不声明巨型结构真实建完。工作进度来自真实GT工质/冷却剂/激活剂扣料后的生产EnergyRuntime.tick，经原生Anchor/S35实际客户端接收，再使用同一描述符由原生TESR绘制。全部原始PNG保持未裁剪、未重绘。</p>'+gif+cards,encoding='utf-8')
    manifest=[dict(path=str(p.relative_to(ROOT)),bytes=p.stat().st_size,sha256=sha(p)) for p in (OUT/'client-verification').rglob('*') if p.is_file()]
    (OUT/'client-evidence-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({k:summary[k] for k in ['status','checks','passed','exitCode','candidateSHA256']},indent=2))

if __name__=='__main__':main()
