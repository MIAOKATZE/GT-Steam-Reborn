"""Apply production redstone mixins to real GT205 API with a <=512 MiB JVM."""
from pathlib import Path
import argparse, hashlib, json, subprocess

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/gtsr-v1.21.43-dev.jar')
    parser.add_argument('--candidate', action='store_true')
    parser.add_argument('--jar-only', action='store_true', help='test a built candidate jar without source overlay')
    parser.add_argument('--gt-version', default='5.09.54.205')
    args = parser.parse_args()
    phase = 'candidate' if args.candidate else 'baseline'
    if args.jar_only: phase += '-jar-only'
    if args.gt_version != '5.09.54.205': phase += '-gt' + args.gt_version
    out = ROOT / 'build/structure-audit-v12143/redstone' / phase
    classes = out / 'classes'; classes.mkdir(parents=True, exist_ok=True)
    unimixins = next(CACHE.glob('io.github.legacymoddingmc/unimixins/0.3.1/*/*-dev.jar'))
    gt_compile = next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205/*/*-dev.jar'))
    gt_runtime = next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/' + args.gt_version + '/*/*-dev.jar'))
    jars = [p for p in CACHE.glob('*/*/*/*/*.jar') if not p.name.startswith(('unimixins-', 'GT5-Unofficial-', 'gtsr-'))]
    cp = [str(classes), str(args.jar.resolve()), str(unimixins), str(gt_compile)] + list(map(str, jars)) + [str(ROOT/'build/classes/java/patchedMc')]
    sources = [ROOT/'tools/tests/RedstoneLifecycleProbe.java', ROOT/'tools/tests/structurelib-gates/FMLCommonHandler.java']
    mixins = ['StructureCheckerMixin']
    if args.candidate and not args.jar_only:
        sources += [ROOT / 'src/main/java' / p for p in [
            'com/miaokatze/gtsr/mixin/StructureCheckerMixin.java',
            'com/miaokatze/gtsr/mixin/MTEMultiBlockRedstoneLifecycleMixin.java',
            'com/miaokatze/gtsr/common/structure/GTSRRedstoneHatchCheck.java',
            'com/miaokatze/gtsr/common/api/compat/IGTSRRedstoneHatchCheck.java']]
    if args.candidate: mixins += ['MTEMultiBlockRedstoneLifecycleMixin']
    (classes/'mixins.gtsr.redstone-probe.json').write_text(json.dumps(dict(required=True,
        minVersion='0.8.5', package='com.miaokatze.gtsr.mixin', compatibilityLevel='JAVA_8', mixins=mixins)))
    def run(exe, values, name):
        argfile = out/(name+'.args')
        argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values))
        with (out/(name+'.log')).open('w', encoding='utf-8') as log:
            result = subprocess.run([str(JAVA/exe), '@'+str(argfile)], cwd=out, stdout=log,
                stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode:
            print((out/(name+'.log')).read_text(errors='replace')[-10000:])
            raise RuntimeError(name+' failed')
    run('javac.exe', ['-encoding','UTF-8','-proc:none','-cp',';'.join(cp),'-d',classes]+sources, 'compile')
    cp = [str(gt_runtime) if p == str(gt_compile) else p for p in cp]
    run('java.exe', ['-Xmx512m','-Djava.awt.headless=true','-Dmixin.debug.export=true','-cp',';'.join(cp),
        'RedstoneLifecycleProbe', out/'result.json'], 'run')
    result = json.loads((out/'result.json').read_text())
    result['jarSha256'] = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    result['compiledSources'] = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    result['gtRuntime'] = dict(version=args.gt_version, sha256=hashlib.sha256(gt_runtime.read_bytes()).hexdigest())
    result['sourceOverlay'] = args.candidate and not args.jar_only
    result['transformedClasses'] = {str(p.relative_to(out)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (out/'.mixin.out/class').rglob('*.class')}
    (out/'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    if args.candidate and not result['pass']: raise RuntimeError('candidate assertions failed')

if __name__ == '__main__': main()
