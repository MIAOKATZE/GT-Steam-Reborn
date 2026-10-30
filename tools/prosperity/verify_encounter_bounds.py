#!/usr/bin/env python3
"""Offline actual-source loot/spawner regressions. No downloads, Gradle or gameplay.

Defaults use this audit workspace's cached MC/Forge/GT/GTSR jars. See
README-encounter-bounds.md for API/name adapters and remaining hook limits.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASELINE = '57d082e9e748c8918ae3bb2c33df386c9a1cc99e'
PREFIX = 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/'
MAPPINGS = {
    'getWorldObj()': 'getWorld()', 'func_150296_c()': 'getKeySet()',
    'getItemDamage()': 'getMetadata()', 'hasCustomInventoryName()': 'isCustomInventoryName()',
    'openInventory()': 'openChest()', 'closeInventory()': 'closeChest()',
    '.func_151247_a(': '.sendToAllTrackingEntity(', '.func_147451_t(': '.updateAllLightTypes(',
    '.field_76434_w': '.healthBoost',
}
API = r'''
import java.nio.file.*; import java.util.jar.*; import org.objectweb.asm.*;
public class EncounterApiFixture implements Opcodes {
 public static void main(String[] args) throws Exception {
  try (JarFile input = new JarFile(args[0]); JarOutputStream output = new JarOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
   for (String name : new String[]{"net/minecraft/world/World", "net/minecraft/entity/Entity"}) {
    byte[] bytes = input.getInputStream(input.getJarEntry(name + ".class")).readAllBytes();
    ClassWriter writer = new ClassWriter(0);
    new ClassReader(bytes).accept(writer, 0);
    if (name.endsWith("/World")) writer.visitField(ACC_PUBLIC, "perWorldStorage", "Lnet/minecraft/world/storage/MapStorage;", null, null).visitEnd();
    else {
     MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "getEntityData", "()Lnet/minecraft/nbt/NBTTagCompound;", null, null);
     method.visitCode(); method.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException"); method.visitInsn(DUP);
     method.visitLdcInsn("Unexercised Forge Entity.getEntityData API fixture");
     method.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
     method.visitInsn(ATHROW); method.visitMaxs(3, 1); method.visitEnd();
    }
    output.putNextEntry(new JarEntry(name + ".class")); output.write(writer.toByteArray()); output.closeEntry();
   }
  }
 }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=ROOT.parents[1])
    parser.add_argument('--out', type=Path)
    parser.add_argument('--baseline', default=BASELINE)
    parser.add_argument('--current-world', action='store_true', help='Also compile current RemasterData/Site instead of baseline world support')
    args = parser.parse_args()
    w = args.workspace.resolve()
    out = (args.out or w / 'review-gtsr-encounters-r2').resolve()
    out.mkdir(parents=True, exist_ok=True)
    empty = out / 'empty'; empty.mkdir(exist_ok=True)
    jdk = next((w / 'toolchains').glob('*jdk17*/bin'))
    mc = w / 'review-monitoring/minecraft-mcp.jar'
    forge = w / 'review-gtsr-client-r1/forge-mcp.jar'
    gt = w / 'review-gtsr-hubs-r1/gt5u-mcp.jar'
    jars = [mc, forge, gt, *sorted((w / 'dependency-probe').glob('*.jar')),
            *sorted((w / '.gradle-build/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))]
    jars = [p for p in dict.fromkeys(jars) if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    cp = ':'.join(map(str, jars))
    results = {}

    def run(label, executable, arguments, fail=False):
        result = subprocess.run([str(jdk / executable), *map(str, arguments)], cwd=out, capture_output=True, text=True, timeout=90)
        log = result.stdout + result.stderr
        (out / (label + '.log')).write_text(log)
        if fail:
            if result.returncode == 0 or 'java.lang.AssertionError:' not in log:
                raise AssertionError(label + ': expected behavioral assertion\n' + log)
        elif result.returncode:
            raise AssertionError(label + '\n' + log)
        results[label] = {'exit': result.returncode, 'log': label + '.log'}
        print(label + ': ' + ('expected RED' if fail else 'PASS'))

    api = out / 'EncounterApiFixture.java'; api.write_text(API)
    asm = next((w / '.gradle-build/caches/modules-2/files-2.1/org.ow2.asm/asm/9.8').glob('*/*.jar'))
    run('api-fixture-compile', 'javac', ['-cp', asm, '-d', out, api])
    fixture = out / 'forge-api-fixture.jar'
    run('api-fixture-build', 'java', ['-cp', str(out) + ':' + str(asm), 'EncounterApiFixture', mc, fixture])
    cp = str(fixture) + ':' + cp
    (out / 'classpath.txt').write_text(cp)
    changed = ['encounter/SealedChestLoot.java', 'remaster/RemasterRuntime.java']
    support = ['remaster/' + name + '.java' for name in
               ('RemasterData', 'RemasterSite', 'RemasterRollout', 'RemasterCatalog', 'RemasterPrefab', 'FutureStructurePlanner')]
    tests = [ROOT / 'tools/prosperity' / (name + '.java') for name in
             ('SealedChestLootBoundsCheck', 'RemasterSpawnerLedgerCheck')]
    hashes = {}
    for mode in ('baseline', 'patched'):
        sources = []
        names = changed + support + (['remaster/RemasterSpawnerLedger.java'] if mode == 'patched' else [])
        for name in names:
            relative = PREFIX + name
            if (mode == 'patched' and name not in support) or (args.current_world and name in support):
                source = (ROOT / relative).read_text()
            else:
                source = subprocess.check_output(['git', 'show', args.baseline + ':' + relative], cwd=ROOT, text=True)
            if mode == 'patched' and name not in support:
                hashes[relative] = hashlib.sha256(source.encode()).hexdigest()
            for before, after in MAPPINGS.items(): source = source.replace(before, after)
            target = out / (mode + '-sources') / name
            target.parent.mkdir(parents=True, exist_ok=True); target.write_text(source); sources.append(target)
        classes = out / (mode + '-classes'); classes.mkdir(exist_ok=True)
        run(mode + '-compile', 'javac', ['-proc:none', '-encoding', 'UTF-8', '-sourcepath', empty,
                                       '-cp', cp, '-d', classes, *sources, *tests])
        runtime = str(classes) + ':' + str(ROOT / 'src/main/resources') + ':' + cp
        loot = 'com.miaokatze.gtsr.common.dimension.prosperity.encounter.SealedChestLootBoundsCheck'
        for scenario in ('normal', 'huge', 'capture') + (('candidates',) if mode == 'patched' else ()):
            run(mode + '-loot-' + scenario, 'java', ['-cp', runtime, loot, scenario], mode == 'baseline' and scenario != 'normal')
        run(mode + '-spawner', 'java', ['-cp', runtime,
            'com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSpawnerLedgerCheck'], mode == 'baseline')
    (out / 'verification.json').write_text(json.dumps({'results': results, 'production_sha256': hashes,
        'current_world_support': args.current_world, 'baseline': args.baseline,
        'limitations': 'Actual complete changed sources with mechanical MCP naming adapters; native ItemStack/NBT/MapStorage and packaged spawner metadata. Native World gains the Forge storage field only; Entity.getEntityData is an unexercised throwing API seam. No full Forge boot, custom loot plugin, native Forge generation array path, game loop or gameplay.'}, indent=2))


if __name__ == '__main__': main()
