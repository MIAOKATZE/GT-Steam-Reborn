"""Fresh production bridge/source overlay and real vanilla MC movement; --compile-only prepares."""
from native_terrain_fixture import native_sources, isolated_sourcepath
import hashlib, json, re, subprocess, tempfile, sys
from pathlib import Path
from remaster_convert import ROOT

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    jars = list((Path.home()/'.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp = [ROOT/'build/classes/java/main', ROOT/'build/classes/java/patchedMc']
    for name in ('guava-17.0.jar','commons-lang3-3.3.2.jar','log4j-api-2.0-beta9-fixed.jar','log4j-core-2.0-beta9-fixed.jar','gson-2.2.4.jar'):
        cp += [p for p in jars if p.name == name][:1]
    cp += [ROOT/'src/main/resources', ROOT/'build/resources/patchedMc'] + [p for p in jars if 'jabel' not in p.name and 'byte-buddy' not in p.name]
    sources = [ROOT/'tools/prosperity/CityRoadCollisionCheck.java', ROOT/'tools/prosperity/RemasterRuntimeCheck.java']
    base = ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity'
    sources += [base/f'remaster/{n}.java' for n in ('RemasterRuntime','RemasterOriginalContract','RemasterEngineering','RemasterWitness','RemasterLoot','TileRemasterNode','RemasterSpawn','RemasterBlock','RemasterBlocks','RemasterCatalog','RemasterPrefab','RemasterWorldgen','RemasterTerrain','RemasterPlanner','RemasterSite','RemasterData')]
    sources += [base/(n+'.java') for n in ('encounter/TileEntitySealedChest','encounter/BlockSealedChest','echo/EchoKind','echo/EntityOldEcho','encounter/ForgottenLakeEncounterStructure','architecture/BlockZenithLog','ruins/ProsperityDecorPlacer','lore/HistoryProgress','architecture/RuinsArchitecture','architecture/RoyalArchitecture')]
    sources += native_sources(ROOT)
    sources = list(dict.fromkeys(sources))
    pins = sources + [Path(__file__), ROOT/'tools/prosperity/native_terrain_fixture.py', ROOT/'src/main/resources/assets/gtsr/remaster/catalog.json']
    before = {p.relative_to(ROOT).as_posix():sha(p) for p in pins}
    receipt = {'sourceSHA256Before':before, 'compileOnly':'--compile-only' in sys.argv, 'MCExecuted':False}
    exit_code = 0
    report = ROOT/'temp/city-road-collision.json'
    previous_report_stamp = report.stat().st_mtime_ns if report.exists() else None
    with tempfile.TemporaryDirectory(prefix='gtsr-city-road-final-') as folder:
        tmp = Path(folder); copies = []
        for source in sources:
            text = source.read_text('utf8')
            if source.name == 'RemasterRuntimeCheck.java':
                text = (text.split(' static class P extends')[0]+'}\n').replace('P player;','EntityPlayerMP player;')
            if source.name in ('RuinsArchitecture.java','RoyalArchitecture.java'):
                text = re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$', '', text, flags=re.M)
            target = tmp/source.name; target.write_text(text,'utf8'); copies.append(target)
        classpath = ';'.join(p.as_posix() for p in cp); args = tmp/'javac.args'
        args.write_text('-encoding UTF-8\n-sourcepath "'+isolated_sourcepath(tmp)+'"\n-cp "'+classpath+'"\n-d "'+tmp.as_posix()+'"\n'+'\n'.join('"'+p.as_posix()+'"' for p in copies),'utf8')
        compile_run = subprocess.run(['javac','@'+str(args)],capture_output=True,text=True)
        (ROOT/'temp/city-road-final-compile.log').write_text(compile_run.stdout+compile_run.stderr,'utf8')
        receipt['compileExit'] = compile_run.returncode; exit_code = compile_run.returncode
        if exit_code == 0 and not receipt['compileOnly']:
            args.write_text('-Xmx3G\n-cp "'+tmp.as_posix()+';'+classpath+'"\nCityRoadCollisionCheck','utf8')
            flags = [a for a in sys.argv[1:] if a != '--compile-only']
            receipt['MCExecuted'] = True
            run = subprocess.run(['java','@'+str(args)]+flags,capture_output=True,text=True)
            (ROOT/'temp/city-road-final-mc.log').write_text(run.stdout+run.stderr,'utf8')
            receipt['MCExit'] = run.returncode; exit_code = run.returncode
            print(run.stdout[-3000:]); print(run.stderr[-3000:])
    after = {p.relative_to(ROOT).as_posix():sha(p) for p in pins}
    receipt['sourceSHA256After'] = after
    receipt['sourceChanged'] = [p for p in before if before[p] != after[p]]
    if receipt['sourceChanged']:
        exit_code = exit_code or 1
    receipt['exit'] = exit_code
    receipt['independentTerrainPlacementCheck'] = 'Separate matrix owned by root; old radius6/cap6 expectations are not v1 bridge assertions.'
    if receipt['MCExecuted'] and report.exists() and report.stat().st_mtime_ns != previous_report_stamp:
        receipt['reportSHA256'] = sha(report)
        receipt['reportFresh'] = True
    elif receipt['MCExecuted']:
        receipt['reportFresh'] = False
        receipt['missingReport'] = 'MC stopped before writing a current report; previous artifacts are not evidence.'
    (ROOT/'temp/city-road-final-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n','utf8')
    print('city-road '+('COMPILE_READY' if receipt['compileOnly'] else 'MC_FINISHED')+' exit='+str(exit_code)+' sourceChanged='+str(receipt['sourceChanged']))
    if exit_code:
        raise SystemExit(exit_code)

if __name__ == '__main__':
    main()
