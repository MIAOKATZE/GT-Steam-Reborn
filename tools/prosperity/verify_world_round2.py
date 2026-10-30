#!/usr/bin/env python3
"""Offline R2 world save/travel/reading regressions. No network, Gradle or gameplay.

Pass --baseline-dir to a saved source overlay (missing files fall back to the
local --baseline-ref), or omit it to compare the git baseline directly.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from verify_world_lifecycle import TRAVEL_STUBS

ROOT = Path(__file__).resolve().parents[2]
PREFIX = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/"
BASELINE = "57d082e9e748c8918ae3bb2c33df386c9a1cc99e"

ALTAR_FIXTURES = {'com/miaokatze/gtsr/common/dimension/prosperity/altar/SpacetimeAltarBlocks.java': 'package '
                                                                                   'com.miaokatze.gtsr.common.dimension.prosperity.altar; '
                                                                                   'public class '
                                                                                   'SpacetimeAltarBlocks '
                                                                                   '{public static '
                                                                                   'final '
                                                                                   'net.minecraft.block.Block '
                                                                                   'core=new '
                                                                                   'net.minecraft.block.Block(net.minecraft.block.material.Material.rock){};}',
 'com/miaokatze/gtsr/common/dimension/prosperity/altar/SpacetimeAltarIndex.java': 'package '
                                                                                  'com.miaokatze.gtsr.common.dimension.prosperity.altar; '
                                                                                  'public class '
                                                                                  'SpacetimeAltarIndex '
                                                                                  '{public static '
                                                                                  'final '
                                                                                  'SpacetimeAltarIndex '
                                                                                  'INSTANCE=new '
                                                                                  'SpacetimeAltarIndex(); '
                                                                                  'public static '
                                                                                  'SpacetimeAltarIndex '
                                                                                  'get(net.minecraft.world.World '
                                                                                  'w){return '
                                                                                  'INSTANCE;} '
                                                                                  'public Entry '
                                                                                  'find(String '
                                                                                  'id){return '
                                                                                  '"altar:0:0".equals(id)?new '
                                                                                  'Entry():null;} '
                                                                                  'public void '
                                                                                  'markRead(String '
                                                                                  'id){} public '
                                                                                  'static class '
                                                                                  'Entry{public '
                                                                                  'int '
                                                                                  'x=7,y=72,z=7;public '
                                                                                  'boolean '
                                                                                  'valid=true,storyRead=true;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/altar/SpacetimeAltarStoryNetwork.java': 'package '
                                                                                         'com.miaokatze.gtsr.common.dimension.prosperity.altar;public '
                                                                                         'class '
                                                                                         'SpacetimeAltarStoryNetwork '
                                                                                         '{public '
                                                                                         'static '
                                                                                         'java.util.UUID '
                                                                                         'nonce;public '
                                                                                         'static '
                                                                                         'void '
                                                                                         'show(net.minecraft.entity.player.EntityPlayerMP '
                                                                                         'p,TileSpacetimeAltarStory '
                                                                                         'b,java.util.UUID '
                                                                                         'n){nonce=n;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/altar/TileSpacetimeAltar.java': 'package '
                                                                                 'com.miaokatze.gtsr.common.dimension.prosperity.altar; '
                                                                                 'public class '
                                                                                 'TileSpacetimeAltar '
                                                                                 'extends '
                                                                                 'net.minecraft.tileentity.TileEntity '
                                                                                 '{public '
                                                                                 'net.minecraft.world.World '
                                                                                 'getWorldObj(){return '
                                                                                 'worldObj;} '
                                                                                 'public int '
                                                                                 'activations; '
                                                                                 'public String '
                                                                                 'instanceId(){return '
                                                                                 '"altar:0:0";} '
                                                                                 'public void '
                                                                                 'activate(net.minecraft.entity.player.EntityPlayer '
                                                                                 'p){activations++;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/altar/TileSpacetimeAltarStory.java': 'package '
                                                                                      'com.miaokatze.gtsr.common.dimension.prosperity.altar; '
                                                                                      'public '
                                                                                      'class '
                                                                                      'TileSpacetimeAltarStory '
                                                                                      'extends '
                                                                                      'net.minecraft.tileentity.TileEntity '
                                                                                      '{public '
                                                                                      'net.minecraft.world.World '
                                                                                      'getWorldObj(){return '
                                                                                      'worldObj;} '
                                                                                      'public '
                                                                                      'String '
                                                                                      'instanceId(){return '
                                                                                      '"altar:0:0";} '
                                                                                      'public void '
                                                                                      'bind(String '
                                                                                      'id){}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/encounter/ForgottenLakeEncounterRegistry.java': 'package '
                                                                                                 'com.miaokatze.gtsr.common.dimension.prosperity.encounter; '
                                                                                                 'public '
                                                                                                 'class '
                                                                                                 'ForgottenLakeEncounterRegistry '
                                                                                                 '{public '
                                                                                                 'static '
                                                                                                 'net.minecraft.block.Block '
                                                                                                 'sealedChest;}',
 'com/miaokatze/gtsr/common/dimension/prosperity/encounter/TileEntitySealedChest.java': 'package '
                                                                                        'com.miaokatze.gtsr.common.dimension.prosperity.encounter;public '
                                                                                        'class '
                                                                                        'TileEntitySealedChest '
                                                                                        'extends '
                                                                                        'net.minecraft.tileentity.TileEntity '
                                                                                        '{public '
                                                                                        'net.minecraft.world.World '
                                                                                        'getWorldObj(){return '
                                                                                        'worldObj;} '
                                                                                        'public '
                                                                                        'void '
                                                                                        'initializeAltar(String '
                                                                                        'id){} '
                                                                                        'public '
                                                                                        'String '
                                                                                        'getAltarInstance(){return '
                                                                                        '"altar:0:0";}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/lore/HistoryProgress.java': 'package '
                                                                             'com.miaokatze.gtsr.common.dimension.prosperity.lore; '
                                                                             'public class '
                                                                             'HistoryProgress '
                                                                             '{public static void '
                                                                             'spacetimeAltarEntered(net.minecraft.entity.player.EntityPlayerMP '
                                                                             'p,String id){}public '
                                                                             'static void '
                                                                             'spacetimeAltarActivated(net.minecraft.entity.player.EntityPlayerMP '
                                                                             'p,String id){}public '
                                                                             'static void '
                                                                             'spacetimeAltarRead(net.minecraft.entity.player.EntityPlayerMP '
                                                                             'p,com.miaokatze.gtsr.common.dimension.prosperity.altar.TileSpacetimeAltarStory '
                                                                             'b){}public static '
                                                                             'boolean '
                                                                             'hasSpacetimeAltarRead(net.minecraft.entity.player.EntityPlayer '
                                                                             'p,String id){return '
                                                                             'true;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java': 'package '
                                                                                'com.miaokatze.gtsr.common.dimension.prosperity.remaster; '
                                                                                'public class '
                                                                                'RemasterBlocks '
                                                                                '{private static '
                                                                                'final '
                                                                                'net.minecraft.block.Block '
                                                                                'BOARD=new '
                                                                                'net.minecraft.block.Block(net.minecraft.block.material.Material.rock){};public '
                                                                                'static '
                                                                                'net.minecraft.block.Block '
                                                                                'get(String '
                                                                                'id){return '
                                                                                'BOARD;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterRuntime.java': 'package '
                                                                                 'com.miaokatze.gtsr.common.dimension.prosperity.remaster;public '
                                                                                 'class '
                                                                                 'RemasterRuntime{public '
                                                                                 'static '
                                                                                 'net.minecraft.block.Block '
                                                                                 'resolve(String '
                                                                                 'id){return '
                                                                                 'null;}}',
 'net/minecraftforge/common/util/FakePlayer.java': 'package net.minecraftforge.common.util;public '
                                                   'class FakePlayer extends '
                                                   'net.minecraft.entity.player.EntityPlayerMP '
                                                   '{FakePlayer(){super(null,null,null,null);}}'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=ROOT.parents[1])
    parser.add_argument('--out', type=Path)
    parser.add_argument('--baseline-dir', type=Path)
    parser.add_argument('--baseline-ref', default=BASELINE)
    parser.add_argument('--classpath', help='Optional explicit local cached classpath')
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    out = (args.out or workspace / 'review-gtsr-world-r2/reproducible').resolve()
    out.mkdir(parents=True, exist_ok=True)
    jdk8 = next((workspace / 'toolchains').glob('*jdk8*/bin'))
    jdk17 = next((workspace / 'toolchains').glob('*jdk17*/bin'))
    mc = workspace / 'review-monitoring/minecraft-mcp.jar'
    release = workspace / 'dependency-probe/gtsr-v2.0.43-dev.jar'
    cache = workspace / '.gradle-build/caches/modules-2/files-2.1'
    jars = [mc, release]
    for library in ('com.google.code.gson/gson/2.2.4', 'com.google.guava/guava/17.0',
                    'org.apache.logging.log4j/log4j-api/2.0-beta9-fixed',
                    'org.apache.logging.log4j/log4j-core/2.0-beta9-fixed',
                    'io.netty/netty-all/4.0.10.Final', 'com.mojang/authlib/1.5.21',
                    'org.apache.commons/commons-lang3'):
        choices = [p for p in (cache / library).rglob('*.jar')
                   if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
        jars.append(sorted(choices)[0])
    cp = args.classpath or os.pathsep.join(map(str, jars))
    results, hashes = {}, {}
    empty = out / 'empty'; empty.mkdir(exist_ok=True)

    def source(relative, baseline):
        if not baseline:
            path = ROOT / relative
            hashes[relative] = sha(path)
            return path.read_text()
        overlay = args.baseline_dir / relative if args.baseline_dir else None
        if overlay is not None and overlay.is_file(): return overlay.read_text()
        return subprocess.check_output(['git', 'show', args.baseline_ref + ':' + relative], cwd=ROOT, text=True)

    def run(label, jdk, command, expected_failure=False):
        result = subprocess.run([str(jdk / command[0]), *map(str, command[1:])], cwd=out,
                                capture_output=True, text=True, timeout=90)
        log = result.stdout + result.stderr
        (out / (label + '.log')).write_text(log)
        if expected_failure:
            if result.returncode == 0 or 'java.lang.AssertionError:' not in log:
                raise AssertionError(label + ': expected behavioral failure, not linkage/API failure\n' + log)
        elif result.returncode:
            raise AssertionError(label + '\n' + log)
        results[label] = {'exit': result.returncode, 'log': label + '.log'}
        print(label + ': ' + ('expected behavioral RED' if expected_failure else 'PASS'))

    recall_fixtures = dict(TRAVEL_STUBS)
    recall_fixtures['ic2/api/item/ElectricItem.java'] = (
        'package ic2.api.item;public class ElectricItem {public static int calls;'
        'public static final Manager manager=new Manager();public static class Manager {'
        'public double discharge(net.minecraft.item.ItemStack s,double v,int t,boolean a,boolean b,boolean c)'
        '{calls++;return v;}}}')
    recall_fixtures['net/minecraftforge/common/DimensionManager.java'] = (
        'package net.minecraftforge.common;public class DimensionManager {'
        'public static net.minecraft.world.WorldServer world;'
        'public static boolean isDimensionRegistered(int d){return true;}'
        'public static net.minecraft.world.WorldServer getWorld(int d){return world;}'
        'public static void initDimension(int d){}}')
    suites = [
        dict(name='data', jdk=jdk8, changed=['remaster/RemasterData.java', 'remaster/RemasterSite.java'],
             support=['remaster/' + name + '.java' for name in
                      ('FutureStructurePlanner', 'RemasterRollout', 'RemasterCatalog', 'RemasterPrefab')],
             tests=['RemasterSavedStateValidationCheck', 'RemasterPersistenceCheck'],
             fixtures={'net/minecraft/world/World.java':
                       'package net.minecraft.world;public class World {'
                       'public net.minecraft.world.storage.MapStorage perWorldStorage;}'}),
        dict(name='altar', jdk=jdk17, changed=['altar/SpacetimeAltarStory.java'], support=[],
             tests=['AltarReadingLifecycleCheck'], fixtures=ALTAR_FIXTURES),
        dict(name='recall', jdk=jdk8, changed=['travel/SpacetimeTravel.java'], support=[],
             tests=['RecallDiscoveryFailureCheck'], fixtures=recall_fixtures),
    ]
    for suite in suites:
        for baseline in (True, False):
            label = suite['name'] + ('-baseline' if baseline else '-patched')
            work = out / label; classes = work / 'classes'
            classes.mkdir(parents=True, exist_ok=True)
            files = []
            for name in suite['changed'] + suite['support']:
                relative = PREFIX + name
                body = source(relative, baseline and name in suite['changed'])
                body = body.replace('func_150296_c()', 'getKeySet()')
                path = work / 'source' / name
                path.parent.mkdir(parents=True, exist_ok=True); path.write_text(body); files.append(path)
            if suite['name'] == 'altar':
                path = ROOT / 'src/main/java/com/miaokatze/gtsr/common/network/ServerPacketAuthority.java'
                hashes[str(path.relative_to(ROOT))] = sha(path); files.append(path)
            for name, body in suite['fixtures'].items():
                path = work / 'fixtures' / name
                path.parent.mkdir(parents=True, exist_ok=True); path.write_text(body); files.append(path)
            for test in suite['tests']:
                path = ROOT / 'tools/prosperity' / (test + '.java')
                hashes[str(path.relative_to(ROOT))] = sha(path); files.append(path)
            run(label + '-compile', suite['jdk'], ['javac', '-proc:none', '-encoding', 'UTF-8',
                '-sourcepath', empty, '-cp', cp, '-d', classes, *files])
            if suite['name'] == 'recall':
                # These Forge-only signatures are compile adapters. Runtime uses native MC classes;
                # successful landing/transfer never reaches the unavailable Forge method bodies.
                for name in ('net/minecraft/block/Block.class',
                             'net/minecraft/server/management/ServerConfigurationManager.class'):
                    (classes / name).unlink()
            runtime = os.pathsep.join([str(classes), str(ROOT / 'src/main/resources'), cp])
            for test in suite['tests']:
                if baseline and test == 'RemasterPersistenceCheck': continue  # R1 suite is patched-only here.
                run(label + '-' + test, suite['jdk'], ['java', '-cp', runtime, test], baseline)
    for relative, digest in hashes.items():
        if sha(ROOT / relative) != digest: raise AssertionError('Source changed during verification: ' + relative)
    report = {'baseline_ref': args.baseline_ref,
              'baseline_overlay': str(args.baseline_dir) if args.baseline_dir else None,
              'results': results, 'source_and_test_sha256': hashes,
              'native_mc_sha256': sha(mc), 'release_sha256': sha(release),
              'limitations': 'Complete changed ledger/site/reading/recall sources and native NBT/player/equality/'
              'connection classes. Data uses a storage-field compile fixture and native packaged catalog. '
              'Altar compiles actual shared authority with native server/player-list checks; world block/board/'
              'payment/network effects are explicit fixtures. Recall injects cold chunk/collision faults; '
              'IC2 and dimension registry are fixtures; compile-only Forge signatures are not loaded at runtime. '
              'No actual successful landing, teleport, energy debit, vis payment, disk world save, Forge '
              'startup, side stripping or full Gradle build.'}
    (out / 'verification.json').write_text(json.dumps(report, indent=2))
    print('PASS: three R2 suites and R1 persistence; no full game/build claim')


if __name__ == '__main__': main()
