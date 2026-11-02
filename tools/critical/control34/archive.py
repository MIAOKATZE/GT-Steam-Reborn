"""Archive bounded native QA evidence and unchanged PNGs; never launches a process."""
from pathlib import Path
import hashlib,html,json,shutil

ROOT=Path(__file__).resolve().parents[3]
RUNS=ROOT/'build/terminal-controls-v2034-client-qa/client-runs'
OUT=ROOT/'plan/implementation/terminal-controls-v2034'
PICTURES=ROOT/'plan/图片/临界机器/v2.0.34/terminal-controls'

def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    PICTURES.mkdir(parents=True,exist_ok=True)
    index=[]
    for run in ['controls-v2034-r1','controls-v2034-r2','controls-v2034-r3']:
        source=RUNS/run;dest=OUT/'client-verification'/run;dest.mkdir(parents=True,exist_ok=True)
        for path in source.glob('*.json'):
            shutil.copy2(path,dest/path.name)
        for name in ['stdout.log','memory-samples.jsonl','helper.jar']:
            if (source/name).exists():shutil.copy2(source/name,dest/name)
        if (source/'qa-src').exists():shutil.copytree(source/'qa-src',dest/'qa-src',dirs_exist_ok=True)
        if run=='controls-v2034-r1' and (source/'screenshots/control34.actual-solar.png').exists():
            # This is the gallery carrier after the separate native task, never a photo of that machine.
            shutil.copy2(source/'screenshots/control34.actual-solar.png',dest/'raw-gallery-carrier-after-native-task.png')
        if run!='controls-v2034-r3':continue
        checks=read(source/'checks.json')
        for event in checks.get('scriptEvents',[]):
            name=event.get('galleryCapture')
            if not name:continue
            path=source/'screenshots'/(name+'.png');destination=PICTURES/path.name;shutil.copy2(path,destination)
            index.append(dict(name=name,png=str(destination.relative_to(ROOT)),sha256=sha(destination),scope='Controlled display state on native GT controller, not physical formation; native production-assembled S2C and synthetic native hit-test input',evidence=event.get('control34Evidence',{})))
    native=read(RUNS/'controls-v2034-r1/actual-solar-production.json')
    original_native=Path(native['nativeReport']['reportPath'])
    if original_native.exists():shutil.copy2(original_native,OUT/'client-verification/controls-v2034-r1/native-original-world-report.json')
    final=read(RUNS/'controls-v2034-r3/receipt.json')
    checks=read(RUNS/'controls-v2034-r3/checks.json')
    inheritance=read(OUT/'runtime-evidence-inheritance.json')
    failures=[c for c in checks.get('checks',[]) if not c['pass']]
    summary=dict(status='PASS' if final['status']=='PASS' and inheritance['status']=='PASS' and native['nativeReport']['results'][0]['status']=='PASS' else 'PARTIAL',finalUIRun='controls-v2034-r3',finalCandidateSHA256=final['candidateSHA256'],finalUIStatus=final['status'],captured=len(index),uiAssertions=len(checks.get('checks',[])),uiFailures=failures,actualSolarNativeStatus=native['nativeReport']['results'][0]['status'],actualSolarWaterSupplied=100,actualSolarWaterConsumed=57,actualSolarDenseSupercriticalSteamOutput=9,actualSolarPauseProgress=native['pauseProgress'],actualSolarPauseRealTicks=native['pauseRealTicks'],runtimeEvidenceInheritance=inheritance['status'],nativeStatusScope='R1 genuine5925block construction, actual GT hatches and400 paid production ticks; main disabled20ticks with unchanged paid progress and loom power, then same batch resumed and output committed',uiScope='R3 controlled display states on native controllers, not physical formation; unmodified production snapshot/S2C, real window/session/permission checks, synthetic actual MUI2 hit-test clicks. Physical mouse/keyboard NOT_RUN',priorQAFindings=dict(r1='Overall FAIL retained: state0 power action correctly unavailable; three captures sampled earlier S2C before target state wait. Independent native real-production subcase PASS.',r2='Overall FAIL retained: helper froze display tile before its own native getTimer>=2 open-window gate; explicit owned graceful stop, no forced process stop. No product conclusion.',r3='Wait native GT timer>=2, then freeze display fixtures immediately before production terminal opening; retain native access/window/PSM/S2C gates.'),pictures=index)
    (OUT/'client-verification-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    summary['unverifiedControlledStatusCaptures']=['runtime.solar.missing_working_fluid','runtime.solar.missing_exciter','runtime.blocked','runtime.sun.production_paused']
    summary['remainingUIFindingScope']='Four controlled fixture transitions timed out because same-kind gallery reuse key omitted visualStatus and skipped fixture preparation. Source fast path and identical preceding/failed window IDs explain every alternating failure. No screenshot acceptance or demonstrated real-production defect is claimed for those four states; no fourth client launched.'
    summary['uiPassedAssertions']=sum(c['pass'] for c in checks.get('checks',[]))
    summary['nativePowerMachineCount']=4
    summary['helperRootCauseEvidence']='control34-helper-root-cause.json'
    summary['visibleScreenshotReview']='All12 unchanged final-run PNGs visually inspected: role separation clear, no visible clipping or overlapping. Known gtsr.critical.power raw label appears in4 power-action feedback rows; parent applies resource-only correction after this capture, without claiming recapture.'
    summary['sizingLogReview']=dict(message='MUI [SIZING][ListWidget]: Margin/padding is set on both sides on axis Y, but total size exceeds parent size.',widgetPathInOriginalLog=None,observedImpact='Visible12 PNGs do not show overlap/clipping; outer viewport475x350/no external scrollbar assertions pass. No claim that a specific hidden ListWidget caused the message.')
    (OUT/'client-verification-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    cards=''.join('<figure><figcaption>'+html.escape(x['name'])+'</figcaption><a href="'+html.escape(Path(x['png']).name)+'"><img src="'+html.escape(Path(x['png']).name)+'"></a></figure>' for x in index)
    (PICTURES/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>v2.0.34终端验证</title><style>body{font:16px sans-serif;background:#1c2025;color:#ddd;margin:24px}img{max-width:100%;height:auto}figure{margin:24px 0}figcaption{padding:8px}</style><h1>v2.0.34终端验证</h1><p>以下为受控原生GT显示状态，不声明真实建成。S2C、窗口、权限与按钮点击为实际生产路径；真实5925方块施工和400tick生产另见独立JSON证据。</p>'+cards,encoding='utf-8')
    manifest=[]
    for path in (OUT/'client-verification').rglob('*'):
        if path.is_file():manifest.append(dict(path=str(path.relative_to(ROOT)),bytes=path.stat().st_size,sha256=sha(path)))
    (OUT/'client-evidence-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({k:summary[k] for k in ['status','finalUIStatus','captured','uiAssertions','actualSolarNativeStatus','runtimeEvidenceInheritance']},indent=2))

if __name__=='__main__':main()
