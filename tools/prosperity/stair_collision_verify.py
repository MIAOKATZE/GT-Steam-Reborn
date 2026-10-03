from native_terrain_fixture import native_sources, isolated_sourcepath
"""Compile latest stair-owning production classes and run real MC collision fixture."""
import re, subprocess, tempfile, sys, json, hashlib
from pathlib import Path
from remaster_convert import ROOT

def main():
    jars=list((Path.home()/'.gradle/caches/modules-2/files-2.1').rglob('*.jar'))
    cp=[str(ROOT/'build/classes/java/main'),str(ROOT/'build/classes/java/patchedMc')]
    for n in ('guava-17.0.jar','commons-lang3-3.3.2.jar','log4j-api-2.0-beta9-fixed.jar','log4j-core-2.0-beta9-fixed.jar','gson-2.2.4.jar'):
        cp += [str(j) for j in jars if j.name==n][:1]
    cp += [str(ROOT/'src/main/resources'),str(ROOT/'build/resources/patchedMc')]+[str(j) for j in jars if 'jabel' not in j.name and 'byte-buddy' not in j.name]
    with tempfile.TemporaryDirectory(prefix='gtsr-stair-check-') as folder:
        tmp=Path(folder);sources=[]
        for n in ('RuinsArchitecture','RoyalArchitecture'):
            p=ROOT/f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/architecture/{n}.java'
            s=re.sub(r'^\s*(GameRegistry\.registerBlock|CreativeTabManager\.addItemToTab)\([^\n]+\);\s*$','',p.read_text('utf8'),flags=re.M)
            q=tmp/f'{n}.java';q.write_text(s,'utf8');sources.append(q)
        fixture=(ROOT/'tools/prosperity/RemasterRuntimeCheck.java').read_text('utf8').split(' static class P extends')[0]+'}\n'
        fixture=fixture.replace('P player;', 'EntityPlayerMP player;')
        adapter=tmp/'RemasterRuntimeCheck.java';adapter.write_text(fixture,'utf8');sources += [adapter,ROOT/'tools/prosperity/StairCollisionCheck.java',ROOT/'tools/prosperity/RoyalStairGeometryCheck.java']
        sources += [ROOT/'tools/prosperity/StairNaturalProfileCapture.java']
        sources += [ROOT/f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{p}.java' for p in ('remaster/RemasterBlock','echo/RuinsBlueprint','encounter/ForgottenLakeEncounterStructure','architecture/BlockZenithLog')]
        if '--natural-capture' in sys.argv:
            sources += [ROOT/f'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/{p}.java' for p in ('remaster/RemasterPlanner','remaster/RemasterTerrain','remaster/RemasterWorldgen','remaster/RemasterRuntime','remaster/RemasterData','remaster/RemasterOriginalContract','ProsperityTerrainProfile')]
        sources += native_sources(ROOT)
        args=tmp/'javac.args';classpath=';'.join(cp).replace('\\','/')
        args.write_text('-encoding UTF-8\n-sourcepath "' + isolated_sourcepath(tmp) + '"\n-cp "'+classpath+'"\n-d "'+str(tmp).replace('\\','/')+'"\n'+'\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),'utf8')
        subprocess.run(['javac','@'+str(args)],check=True)
        args.write_text('-cp "'+str(tmp).replace('\\','/')+';'+classpath+'"\n'+('RoyalStairGeometryCheck '+(sys.argv[sys.argv.index('--height')+1] if '--height' in sys.argv else '139')+' '+(sys.argv[sys.argv.index('--layout')+1] if '--layout' in sys.argv else '3') if '--royal' in sys.argv else 'StairCollisionCheck')+(' '+str(ROOT/(sys.argv[sys.argv.index('--fixture')+1] if '--fixture' in sys.argv else 'temp/stair-walk-fixtures.json')).replace('\\','/')+(' '+str(ROOT/sys.argv[sys.argv.index('--report')+1]).replace('\\','/') if '--report' in sys.argv else '') if '--matrix' in sys.argv else ''),'utf8')
        if '--natural-capture' in sys.argv:
            args.write_text('-cp "'+str(tmp).replace('\\','/')+';'+classpath+'"\nStairNaturalProfileCapture','utf8')
            subprocess.run(['java','@'+str(args)],check=True)
            return
        fixture_path=ROOT/(sys.argv[sys.argv.index('--fixture')+1] if '--fixture' in sys.argv else 'temp/stair-walk-fixtures.json')
        matrix_hash=hashlib.sha256(fixture_path.read_bytes()).hexdigest() if '--matrix' in sys.argv else None
        execution=subprocess.run(['java','@'+str(args)])
        if matrix_hash:
            report=ROOT/(sys.argv[sys.argv.index('--report')+1] if '--report' in sys.argv else 'temp/stair-walk-collision.json'); data=json.loads(report.read_text('utf8')); data['fixtureSHA256']=matrix_hash; report.write_text(json.dumps(data,indent=2),'utf8')
            if matrix_hash!=hashlib.sha256(fixture_path.read_bytes()).hexdigest(): raise RuntimeError('Fixture changed while Minecraft entity proof was running')
        if '--royal' in sys.argv:
            height=int(sys.argv[sys.argv.index('--height')+1]) if '--height' in sys.argv else 139; layout=int(sys.argv[sys.argv.index('--layout')+1]) if '--layout' in sys.argv else 3
            exported=ROOT/('temp/royal-stair-geometry.json' if layout==3 and height==139 else f'temp/royal-stair-geometry-layout{layout}-h{height}.json'); data=json.loads(exported.read_text('utf8')); source=ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/encounter/ForgottenLakeEncounterStructure.java'; data['sourceSHA256']=hashlib.sha256(source.read_bytes()).hexdigest(); data['source']=str(source.relative_to(ROOT)); exported.write_text(json.dumps(data,separators=(',',':')),'utf8')
        execution.check_returncode()
if __name__=='__main__':main()
