"""Reuse the protection Mixin fixture with GT .265; no Minecraft/FML startup."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JAVA = Path('C:/Program Files/Java/jdk-26.0.2.1/bin')
MIXINS = ['MixinBlockCriticalProtection', 'MixinEntityCriticalProtection', 'BlockMachinesCriticalProtectionMixin',
          'MixinWorldCriticalClearance']


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, help='Use this built dev JAR without a production source overlay.')
    parser.add_argument('--phase', default='candidate')
    parser.add_argument('--java-home', type=Path, default=JAVA.parent)
    args = parser.parse_args()
    out = ROOT / 'build/critical-v216-protection' / args.phase
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    gt = next(CACHE.glob('com.github.GTNewHorizons/GT5-Unofficial/5.09.54.265/*/*-dev.jar'))
    unimixins = next(CACHE.glob('io.github.legacymoddingmc/unimixins/0.3.1/*/*-dev.jar'))
    core = next(CACHE.glob('com.miaokatze.miaokatzecore/miaokatzecore/0.2.4/*/*-dev.jar'))
    mui2 = next(CACHE.glob('com.github.GTNewHorizons/ModularUI2/2.3.94-1.7.10/*/*-dev.jar'))
    jars = [unimixins, gt, core, mui2] + [path for path in CACHE.glob('*/*/*/*/*.jar')
        if not path.name.startswith(('unimixins-', 'GT5-Unofficial-', 'miaokatzecore-', 'ModularUI2-'))
        and not path.name.endswith(('-sources.jar', '-javadoc.jar'))]
    jars = [path for path in jars if not compiler_plugin(path)]
    product = args.jar.resolve() if args.jar else ROOT / 'build/classes/java/main'
    cp = ';'.join(map(str, [classes, product, ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))
    sources = [ROOT / 'tools/critical' / name for name in
        ['CriticalV31MixinLauncher.java', 'CriticalV31ProtectionMixinRuntime.java', 'CriticalProtectionContract.java',
         'CriticalProtectionNativeProbe.java']]
    sources += [ROOT / 'tools/tests/structurelib-gates' / name for name in ['FMLCommonHandler.java', 'StructureLibAPI.java']]
    if not args.jar:
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/mixin' / (name + '.java') for name in MIXINS]
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java') for name in
                    ['CriticalBlockProtection', 'CriticalWorldData', 'CriticalMachineState', 'CriticalStructureMonitor']]
    binding = {str(path.relative_to(ROOT)): sha(path) for path in sources}
    (classes / 'mixins.gtsr.probe.v31.json').write_text(json.dumps(dict(required=True, minVersion='0.8.5',
        package='com.miaokatze.gtsr.mixin', compatibilityLevel='JAVA_8', mixins=MIXINS)), encoding='utf-8')

    def call(exe, values, stem):
        argfile = out / (stem + '.args')
        argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in values), encoding='utf-8')
        result = subprocess.run([str(args.java_home / 'bin' / exe), '@' + str(argfile)], cwd=out,
            capture_output=True, text=True, encoding='utf-8', errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
        (out / (stem + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(stem + ' failed\n' + (result.stdout + result.stderr)[-7000:])

    empty = out / 'empty-sourcepath'
    empty.mkdir(exist_ok=True)
    call('javac.exe', ['-encoding', 'UTF-8', '-proc:none', '--release', '17', '-sourcepath', empty,
                      '-cp', cp, '-d', classes, *sources], 'compile')
    call('java.exe', ['-Djava.awt.headless=true', '-Dmixin.debug.export=true', '-cp', cp,
        'CriticalV31MixinLauncher', out / 'runtime.json'], 'run')
    assert binding == {str(path.relative_to(ROOT)): sha(path) for path in sources}, 'Fixture sources changed during run'
    receipt = dict(status='PASS', product=str(product), productionSourceOverlay=not bool(args.jar),
        GT265SHA256=sha(gt), Core024SHA256=sha(core), MUI294SHA256=sha(mui2), sourceSHA256=binding,
        javaHome=str(args.java_home), fullMinecraftStarted=False, FMLStarted=False)
    if args.jar:
        receipt['productSHA256'] = sha(product)
    (out / 'binding.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    runtime = json.loads((out / 'runtime.json').read_text(encoding='utf-8'))
    print(json.dumps(dict(status='PASS', checks=len(runtime['runtime']['checks']), receipt=str(out / 'binding.json'))))


def compiler_plugin(path):
    with zipfile.ZipFile(path) as archive:
        return 'META-INF/services/com.sun.source.util.Plugin' in archive.namelist()


if __name__ == '__main__':
    main()
