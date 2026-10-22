"""Compare immutable real-client outputs; native world screenshots remain contextual evidence."""
from pathlib import Path
import argparse,json
from PIL import Image,ImageChops,ImageStat

ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'build/critical-v205/render-acceptance'
def main():
    ap=argparse.ArgumentParser();ap.add_argument('baseline');ap.add_argument('candidate');a=ap.parse_args()
    left=BASE/a.baseline;right=BASE/a.candidate
    x=json.loads((left/'checks.json').read_text(encoding='utf-8'));y=json.loads((right/'checks.json').read_text(encoding='utf-8'))
    geom=[]
    old={r['slug']:r for r in x['geometry']};new={r['slug']:r for r in y['geometry']}
    for name in sorted(set(old)|set(new)):
        geom.append(dict(slug=name,baseline=old.get(name),candidate=new.get(name),equal=old.get(name)==new.get(name)))
    frames=[];before={r['name']:r for r in x['frames']};after={r['name']:r for r in y['frames']}
    for name in sorted(set(before)|set(after)):
        r=dict(name=name,strict=name.startswith(('nei-','world-model-','actual-anchor-')) and name in before and name in after,paired=name in before and name in after,baseline=before.get(name),candidate=after.get(name))
        p=left/(name+'.png');q=right/(name+'.png')
        if p.exists() and q.exists():
            im=Image.open(p).convert('RGB');other=Image.open(q).convert('RGB');diff=ImageChops.difference(im,other)
            histogram=diff.convert('L').histogram();pixels=im.width*im.height
            r.update(exact=diff.getbbox() is None,changedPixels=sum(1 for v in diff.getdata() if v!=(0,0,0)),meanAbsChannelError=sum(ImageStat.Stat(diff).mean)/3)
            r['rawRGBAExact']=before[name]['sha256']==after[name]['sha256']
            if not r['exact']:diff.save(right/(name+'-diff.png'))
        else:r.update(exact=False,missing=True)
        frames.append(r)
    perf=[];old={r['kind']:r for r in x['performance']};new={r['kind']:r for r in y['performance']}
    for kind in sorted(set(old)|set(new)):
        b=old.get(kind,{});c=new.get(kind,{});row=dict(kind=kind,baseline=b,candidate=c,cacheRetained=c.get('retainedInts',0)>0)
        if b.get('warmCpuMedianMs') and c.get('warmCpuMedianMs'):row['cpuSpeedup']=b['warmCpuMedianMs']/c['warmCpuMedianMs']
        if b.get('warmGpuSyncMedianMs') and c.get('warmGpuSyncMedianMs'):row['syncSpeedup']=b['warmGpuSyncMedianMs']/c['warmGpuSyncMedianMs']
        perf.append(row)
    strict=[r for r in frames if r['strict']]
    exact=all(r['exact'] and r.get('rawRGBAExact',False) for r in strict)
    report=dict(baseline=a.baseline,candidate=a.candidate,baselineStatus=x['status'],candidateStatus=y['status'],geometry=geom,frames=frames,performance=perf,strictPixelCases=len(strict),strictPixelExact=sum(r['exact'] for r in strict),strictRGBAExact=sum(r.get('rawRGBAExact',False) for r in strict),geometryExact=sum(r['equal'] for r in geom),unpairedCases=[r['name'] for r in frames if not r['paired']],contextualWorldCases=[r for r in frames if not r['strict']],baselineFailedChecks=[c for c in x['checks'] if not c['pass']],candidateFailedChecks=[c for c in y['checks'] if not c['pass']],pairedPixelComparison='EXACT_MATCH' if exact else 'DIFFERENCES',status='PASS' if x['status']==y['status']=='PASS' and all(r['equal'] for r in geom) and exact else 'REVIEW_REQUIRED')
    p=left/'screenshots/native-world-solar.png';q=right/'screenshots/native-world-solar.png'
    if p.exists() and q.exists():
        diff=ImageChops.difference(Image.open(p).convert('RGB'),Image.open(q).convert('RGB'))
        report['nativeWorldContext']=dict(strict=False,exact=diff.getbbox() is None,changedPixels=sum(v!=(0,0,0) for v in diff.getdata()),meanAbsChannelError=sum(ImageStat.Stat(diff).mean)/3,scope='Full native static chunks + actual anchor rendering. Camera/FOV/day/rain/atlas/motion fixed. Source Angelica cloud planes still visible and not fully controlled. Contextual evidence, not a claim of full unobscured world pixel fidelity.')
    path=BASE/(a.baseline+'-vs-'+a.candidate+'.json');path.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','strictPixelCases','strictPixelExact','geometryExact','baselineFailedChecks','candidateFailedChecks')},indent=2));print(path)
if __name__=='__main__':main()
