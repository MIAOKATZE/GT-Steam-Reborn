"""Compare frozen old/new native miner ticket evidence without rewriting raw receipts."""
from pathlib import Path
import argparse, hashlib, json, re

def read(path):return json.loads(path.read_text(encoding='utf-8'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def native(run):
    entry=None
    for line in (run/'runner.log').read_text(encoding='utf-8').splitlines():
        try:value=json.loads(line)
        except json.JSONDecodeError:continue
        if isinstance(value,dict) and 'receipt' in value:entry=Path(value['receipt'])
    if entry is None:raise ValueError('No completed native runner receipt: '+str(run))
    receipt=read(entry);console=entry.with_name('console.log')
    markers=[line for line in console.read_text(encoding='utf-8',errors='replace').splitlines() if re.search(r'java\.lang\.(?:NullPointerException|StackOverflowError)|Exception ticking world|Exception stopping the server|Encountered an unexpected exception',line)]
    return {'receipt':str(entry),'status':receipt['status'],'exitCode':receipt['exit_code'],'forcedStop':receipt['forced_stop'],'consoleSHA256':sha(console),'nativeCrashMarkers':markers}
def main():
    p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--isolated',type=Path,required=True);p.add_argument('--mixed',type=Path);p.add_argument('--critical',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    before,after=read(a.baseline/'result.json'),read(a.candidate/'result.json');ib,ia=read(a.baseline/'binding.json'),read(a.candidate/'binding.json');isolated=read(a.isolated/'result.json')
    checks={
      'sameFrozenHelper':ib['helperSHA256']==ia['helperSHA256']==sha(a.baseline/'helper.jar')==sha(a.candidate/'helper.jar'),
      'baselineExpectedFailure':before['status']=='FAIL' and len(before['tiers'])==5 and all(row['worldIdentityLive'] for row in before['tiers']) and before['tiers'][0]['selfForced'] and all(not row['selfForced'] and row['selfFlag'] and row['workFlag'] for row in before['tiers'][1:]) and all(not row['hubForced'] for row in before['tiers']),
      'candidateEveryTierCoverage':after['status']=='PASS' and len(after['tiers'])==5 and all(row[key] for row in after['tiers'] for key in ['worldIdentityLive','selfForced','hubForced','fullWorkFootprint','fullHubFootprint','stopRetainsSelfOnly','stopRetainsHub','removalClearsOwnAndHub']) and [row['footprintCount'] for row in after['tiers']]==[25,49,81,169,361],
      'candidateCanonicalHubGeometry':after.get('hubGeometryStatus')=='PASS' and after.get('hubGeometryAllCanonicalCells') is True and after.get('hubGeometryCases',0)>0,
      'isolatedNativeCalls':isolated['status']=='PASS' and all(row['pass'] for row in isolated['checks']),
    }
    runtime={'baseline':native(a.baseline),'candidate':native(a.candidate)}
    mixed=read(a.mixed/'result.json') if a.mixed else after
    checks['candidateMixedNativeLifecycle']=mixed.get('mixedHeldTicks',0)>=120 and all(mixed.get(key) is True for key in ['mixedBothTicketsHeldBeforeStop','mixedUnloadClearsNativeAndLocal','mixedServerStoppedAllMapsClear','mixedCriticalNativeIdentityStable120Ticks','mixedWorkerNativeIdentityStable120Ticks','mixedWorldIdentityLive120Ticks'])
    if a.mixed:runtime['mixedStableHost']=native(a.mixed)
    if a.critical:
        critical=read(a.critical/'result.json');checks['criticalShutdownRegression']=critical['status']=='PASS' and len(critical['checks'])==12 and all(row['pass'] for row in critical['checks'])
    checks['nativeCleanStop']=all(r['status']=='PASS' and r['exitCode']==0 and not r['forcedStop'] and not r['nativeCrashMarkers'] for r in runtime.values())
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'runtime':runtime,'helperSHA256':ia['helperSHA256'],'baselineJarSHA256':ib['jarSHA256'],'candidateJarSHA256':ia['jarSHA256'],'isolatedCheckCount':len(isolated['checks']),'hubGeometry':{key:value for key,value in after.items() if key.startswith('hubGeometry')},'boundaries':['Explicit server-thread production onPostTick seam with QA anchor outside fixtures; no natural offline mining/output or formed-hub/critical-loom operation claimed.','Native restore and cold-hub world identity/quota paths additionally use sparse World/WorldServer+minimal FML container fixtures; no worker disk-save restart claim.']}
    result['historicalMixedAttempt']={'raw':str(a.candidate/'result.json'),'bothHeldBeforeStop':after.get('mixedBothTicketsHeldBeforeStop'),'interpretation':'Unformed actual critical_loom releases its owner ticket by design; this attempt is not counted as successful 120-tick mixed hold.'}
    a.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');print(json.dumps(result,indent=2,ensure_ascii=False))
    if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
