"""Small isolated upstream-runtime probes for issues 37/60. No Minecraft client or server."""
from pathlib import Path
import argparse
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True)
    parser.add_argument('--probe', default='Issue37RegistrationRegression')
    args = parser.parse_args()
    assert args.label.replace('-', '').replace('_', '').isalnum()
    out = ROOT / 'build/issue-regressions-v12147' / args.label
    out.mkdir(parents=True, exist_ok=False)
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    gt205 = next(cache.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    gt594 = ROOT / 'build/critical-v2-runtime-v12130/nei/GT5-Unofficial-5.09.52.594-dev.jar'
    dependencies = [p for p in (ROOT/'build/critical-v2-runtime-v12129/probe/classpath.txt').read_text().split(';')
                    if Path(p).suffix == '.jar' and '/GT5-Unofficial/' not in p.replace('\\','/')]
    dependencies = [p for p in dependencies if
                    ('/StructureLib/' not in p.replace('\\','/') or '/1.4.45/' in p.replace('\\','/')) and
                    ('/GTNHLib/' not in p.replace('\\','/') or '/0.11.52/' in p.replace('\\','/'))]
    sources = [Path(__file__).with_name('Issue37RegistrationRegression.java'),
               Path(__file__).with_name(args.probe+'.java')]
    if args.probe == 'NEIItemOrderingRegression':
        dependencies = [p for p in dependencies if '/NotEnoughItems/' not in p.replace('\\','/')]
        dependencies.insert(0, next(cache.glob('com.github.GTNewHorizons/NotEnoughItems/2.8.145-GTNH/*/*-dev.jar')))
        sources += [ROOT/'src/main/java/com/miaokatze/gtsr'/path for path in
                    ('loader/ItemLoader.java', 'register/GTSRItemOrderCatalog.java',
                     'register/CreativeTabManager.java')]
        sources.append(ROOT.parent/'MIAOKATZE-Core/src/main/java/com/miaokatze/miaokatzecore/api/items/ItemOrderCatalog.java')
        sources.append(Path(__file__).parent/'item-ordering/squeek/applecore/api/food/IEdible.java')
    classes = out/'classes'; classes.mkdir()
    production = [ROOT/'build/classes/java/main', ROOT/'build/classes/java/patchedMc',
                  ROOT/'build/resources/patchedMc', ROOT/'src/main/resources']
    def run(name, tool, values):
        argfile = out/(name+'.args')
        argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values), encoding='utf-8')
        result = subprocess.run([str(JAVA/(tool+'.exe')),'@'+str(argfile)],cwd=out,
                                capture_output=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
        (out/(name+'.log')).write_text(result.stdout+result.stderr, encoding='utf-8')
        return result
    compile_cp = ';'.join(map(str,[gt205,*production,*dependencies]))
    result = run('compile','javac',['-encoding','UTF-8','-proc:none','-cp',compile_cp,'-d',classes,
                                  *dict.fromkeys(sources)])
    if result.returncode:
        print(result.stdout+result.stderr); raise SystemExit(result.returncode)
    results = {}
    versions = [('beta1','5.09.52.594',gt594),('rc2','5.09.54.205',gt205)] if args.probe.startswith('Issue37') else [('rc2','5.09.54.205',gt205)]
    for label, version, jar in versions:
        cp = ';'.join(map(str,[classes,jar,*production,*dependencies]))
        result = run(label,'java',['-Xmx1G','-Dfile.encoding=UTF-8','-cp',cp,args.probe,version])
        results[label] = {'exitCode':result.returncode,'version':version,'gtJar':str(jar),
                          'result':[s for s in result.stdout.splitlines() if s.startswith(('RESULT','SOURCE'))]}
        print(label,result.returncode)
        print((result.stdout+result.stderr)[-5500:])
    (out/'receipt.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    if any(v['exitCode'] for v in results.values()): raise SystemExit(1)

if __name__ == '__main__': main()
