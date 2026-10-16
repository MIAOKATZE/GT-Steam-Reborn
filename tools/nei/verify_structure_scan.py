"""Execute real BlockRenderer scanner constructor and GTSR cluster prototype, without game startup."""
from pathlib import Path
import hashlib, json, subprocess, urllib.request

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/critical-v2-runtime-v12130/nei'
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
JAVA=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def run(name,tool,args):
    file=OUT/(name+'.args');file.write_text('\n'.join('"'+str(x).replace('\\','/')+'"' for x in args),encoding='utf-8')
    result=subprocess.run([str(JAVA/(tool+'.exe')),'@'+str(file)],capture_output=True,encoding='utf-8',errors='replace',cwd=OUT,creationflags=subprocess.CREATE_NO_WINDOW)
    (OUT/(name+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
    return result

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    legacy=OUT/'GT5-Unofficial-5.09.52.594-dev.jar'
    if not legacy.exists():
        url='https://nexus.gtnewhorizons.com/repository/releases/com/github/GTNewHorizons/GT5-Unofficial/5.09.52.594/'+legacy.name
        opener=urllib.request.build_opener(urllib.request.ProxyHandler({'https':'http://127.0.0.1:7890'}))
        with opener.open(url,timeout=45) as response:legacy.write_bytes(response.read())
    current=next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    renderer=ROOT/'build/critical-machines-v12119/structure-client/red-v18-01/fixture/mods/blockrenderer6343-1.4.24.jar'
    dependencies=(ROOT/'build/critical-v2-runtime-v12129/probe/classpath.txt').read_text().split(';')
    dependencies=[p for p in dependencies if Path(p).suffix=='.jar' and '/GT5-Unofficial/' not in p.replace('\\','/')]
    dependencies=[p for p in dependencies if ('/StructureLib/' not in p.replace('\\','/') or '/1.4.45/' in p.replace('\\','/')) and ('/GTNHLib/' not in p.replace('\\','/') or '/0.11.52/' in p.replace('\\','/'))]
    source=Path(__file__).with_name('NeiStructureProbe.java');compat=ROOT/'src/main/java/com/miaokatze/gtsr/api/compat/GTVersionCompat.java'
    historical=OUT/'v1.21.14/GTVersionCompat.java';historical.parent.mkdir(exist_ok=True)
    old=subprocess.run(['git','show','v1.21.14:'+compat.relative_to(ROOT).as_posix()],cwd=ROOT,capture_output=True,check=True)
    historical.write_bytes(old.stdout)
    results={}
    for label,version,gt in [('beta1','5.09.52.594',legacy),('rc2','5.09.54.205',current)]:
        for phase,code in [('red',historical),('green',compat)]:
            name=label+'-'+phase;classes=OUT/name;classes.mkdir(exist_ok=True)
            cp=';'.join(map(str,[classes,gt,ROOT/'build/classes/java/main',ROOT/'build/classes/java/patchedMc',ROOT/'build/resources/patchedMc',ROOT/'src/main/resources',renderer,*dependencies]))
            compiled=run(name+'-compile','javac',['-encoding','UTF-8','-proc:none','-sourcepath',OUT/'empty','-cp',str(current)+';'+cp,'-d',classes,source,code])
            if compiled.returncode:raise RuntimeError(compiled.stderr)
            executed=run(name+'-scan','java',['-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',cp,'NeiStructureProbe',version])
            expected=(executed.returncode!=0 and 'glass=null cause=java.lang.IllegalArgumentException' in executed.stdout and 'GTConstructableScan.getStructurePairs' in executed.stderr) if phase=='red' else executed.returncode==0 and 'entries=1 glassIdentity=true' in executed.stdout
            results[name]={'expectedResultObserved':expected,'version':version,'gtJar':str(gt),'gtJarSHA256':digest(gt),'compatSourceSHA256':digest(code),'exitCode':executed.returncode,'result':[s for s in executed.stdout.splitlines() if s.startswith('RESULT')],'actualClassSources':[s for s in executed.stdout.splitlines() if s.startswith('SOURCE')],'executionLogSHA256':digest(OUT/(name+'-scan.log'))}
    exercised=[ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/cluster/ClusterStructureDef.java',ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/cluster/MTESteamMineralLogisticsCluster.java']
    receipt={'status':'PASS' if all(r['expectedResultObserved'] for r in results.values()) else 'FAIL','results':results,'blockRendererSHA256':digest(renderer),'historicalCompatTag':'v1.21.14','historicalCompatSHA256':digest(historical),'sources':{p.relative_to(ROOT).as_posix():digest(p) for p in [source,compat,Path(__file__)]},'exercisedReadOnlySourceSHA256':{p.relative_to(ROOT).as_posix():digest(p) for p in exercised},'boundary':'Actual GT API jars 594/205, StructureLib 1.4.45 and unmodified BlockRenderer 1.4.24 GTConstructableScan constructor, current actual GTSR cluster prototype getStructureDefinition. Historical v1.21.14 compatibility source versus current source, each compiled against GT205 and executed separately against 594/205. Early compatibility initialization followed by late glass registration. No full game or FML lifecycle.','limitations':['The early initialization order is a controlled fixture, not a replay of the reporter\'s complete GTNH beta1 instance.','Current cluster class/definition is exercised; this is not the complete v1.21.14 jar or beta1 StructureLib/BlockRenderer distribution.','Scanner constructor/definition discovery only; no scan thread, DummyWorld construction, render, or final NEI GUI.'],'fixtureOverrides':['FML indexed mod map has the corresponding GT version. Server thread/sided handler and vanilla bootstrap only.','GT casing/frame instances allocated without constructors to avoid mod startup. Cluster prototype allocated without constructor.','IC2 glass is inserted through the real FML registry addObjectRaw seam; GT glass uses the real API field. Both are populated after initial compatibility lookup.'],'repeatCommand':'python -X utf8 tools/nei/verify_structure_scan.py','prerequisite':'gradlew compileJava must first produce current production classes; cached probe classpath and BlockRenderer fixture from existing v19/v29 work.'}
    (OUT/'checks.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'owned-paths.json').write_text(json.dumps({'owner':'issue37_v30','paths':[p.relative_to(ROOT).as_posix() for p in [compat,source,Path(__file__)]]},indent=2),encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))
    if receipt['status']!='PASS':raise SystemExit(1)

if __name__=='__main__':main()
