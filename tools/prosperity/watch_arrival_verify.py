from native_terrain_fixture import native_sources, isolated_sourcepath
"""Isolated new-watch arrival proof; never writes the global stair or Royal fixtures."""
import hashlib, json, subprocess, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    script = """const fs=require('fs'),vm=require('vm'),a=JSON.parse(fs.readFileSync('tools/prosperity/profiles/stair-route-baseline.json')),out=[];for(const v of [1,2]){const e=a.rows.find(r=>r.id==='city_watch_tower'&&r.variant===v),w={window:{}};vm.createContext(w);vm.runInContext(fs.readFileSync(e.file,'utf8'),w);const m=Object.values(w.window.REMASTER_PREFAB7_DATA)[0].metadata;if(m.surfaceEntrance?.x!==18||m.surfaceEntrance?.z!==0||m.surfaceEntrance?.topY!==0)throw Error('entry metadata mismatch');for(const r of [...m.structureDesignReview.stairExceptionRoutes,...m.structureDesignReview.stairFunctionalAccessRoutes])out.push({variant:v,route:r.originalRouteKey,points:r.actualPoints});}fs.writeFileSync('temp/watch-arrival-inputs.json',JSON.stringify(out));"""
    subprocess.run(['node','-e',script], cwd=ROOT, check=True)
    jars=list((Path.home()/'.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp=[ROOT/'build/classes/java/main',ROOT/'build/classes/java/patchedMc']
    for name in ('guava-17.0.jar','commons-lang3-3.3.2.jar','log4j-api-2.0-beta9-fixed.jar','log4j-core-2.0-beta9-fixed.jar','gson-2.2.4.jar'):
        cp.extend([p for p in jars if p.name==name][:1])
    cp.extend([ROOT/'src/main/resources',ROOT/'build/resources/patchedMc'])
    cp.extend(p for p in jars if 'jabel' not in p.name and 'byte-buddy' not in p.name)
    source_names=('remaster/RemasterPlanner','remaster/RemasterSite','remaster/RemasterTerrain','remaster/RemasterWorldgen','remaster/RemasterRuntime','remaster/RemasterData','remaster/RemasterOriginalContract','ProsperityTerrainProfile')
    source_files=[ROOT/f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{p}.java' for p in source_names]
    source_files.append(ROOT/'tools/prosperity/WatchArrivalProfileCapture.java')
    source_files += native_sources(ROOT)
    receipt={'compiledProductionSources':{str(p.relative_to(ROOT)):sha(p) for p in source_files}}
    with tempfile.TemporaryDirectory(prefix='gtsr-watch-arrival-') as folder:
        temp=Path(folder);copies=[]
        for p in source_files:
            copy=temp/p.name;copy.write_bytes(p.read_bytes());copies.append(copy)
        classpath=';'.join(p.as_posix() for p in cp)
        args=temp/'javac.args'
        args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(temp) + '"\n-cp "'+classpath+'"\n-d "'+temp.as_posix()+'"\n'+'\n'.join('"'+p.as_posix()+'"' for p in copies),'utf8')
        subprocess.run(['javac','@'+str(args)],cwd=ROOT,check=True)
        args.write_text('-cp "'+temp.as_posix()+';'+classpath+'"\nWatchArrivalProfileCapture','utf8')
        subprocess.run(['java','@'+str(args)],cwd=ROOT,check=True)
    receipt['fixtureSHA256']=sha(ROOT/'temp/watch-arrival-fixtures.json')
    subprocess.run(['python','tools/prosperity/stair_collision_verify.py','--matrix','--fixture','temp/watch-arrival-fixtures.json','--report','temp/watch-arrival-collision.json'],cwd=ROOT,check=True)
    receipt['report']=json.loads((ROOT/'temp/watch-arrival-collision.json').read_text('utf8'))
    receipt['report'].pop('exceptions',None)
    receipt['currentProductionSources']={str(p.relative_to(ROOT)):sha(p) for p in source_files}
    (ROOT/'temp/watch-arrival-receipt.json').write_text(json.dumps(receipt,indent=2),'utf8')
    print(json.dumps(receipt['report']))

if __name__=='__main__':
    main()
