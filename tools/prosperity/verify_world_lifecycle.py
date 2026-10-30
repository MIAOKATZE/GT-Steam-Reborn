"""Offline source-level world/lifecycle regressions. No downloads, Forge startup, or gameplay.

Provide cached dependency jars via --classpath: MCP-named Minecraft, source-matching
GTSR dev jar, Gson 2.2.4, Guava, log4j API/core, Netty, authlib and commons-lang3.
Use Java 8. Optional --baseline-ref runs the same assertions against that git source
and requires the known failures before checking the working tree. See
README-world-lifecycle.md for fixture and mapping-adapter boundaries.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = "src/main/java/com/miaokatze/gtsr/common/dimension/"

ANCHOR_STUBS = {'com/miaokatze/gtsr/common/dimension/prosperity/travel/SpacetimeTravel.java': 'package '
                                                                               'com.miaokatze.gtsr.common.dimension.prosperity.travel;public '
                                                                               'class SpacetimeTravel { '
                                                                               'public static int '
                                                                               'marks;public static boolean '
                                                                               'eligible(net.minecraft.entity.player.EntityPlayerMP '
                                                                               'p){return '
                                                                               'p!=null&&!p.isDead;}public '
                                                                               'static void '
                                                                               'mark(net.minecraft.entity.player.EntityPlayerMP '
                                                                               'p,net.minecraft.item.ItemStack '
                                                                               's){marks++;}}',
 'com/miaokatze/gtsr/common/items/SpacetimeAnchorBeacon.java': 'package '
                                                               'com.miaokatze.gtsr.common.items;public class '
                                                               'SpacetimeAnchorBeacon extends '
                                                               'net.minecraft.item.Item {}',
 'cpw/mods/fml/common/FMLCommonHandler.java': 'package cpw.mods.fml.common; public class FMLCommonHandler '
                                              '{public static FMLCommonHandler instance(){return new '
                                              'FMLCommonHandler();} public Bus bus(){return new Bus();} '
                                              'public static class Bus{public void register(Object o){}}}',
 'cpw/mods/fml/common/eventhandler/SubscribeEvent.java': 'package cpw.mods.fml.common.eventhandler; public '
                                                         '@interface SubscribeEvent {}',
 'cpw/mods/fml/common/gameevent/TickEvent.java': 'package cpw.mods.fml.common.gameevent; public class '
                                                 'TickEvent {public enum Phase {START, END} public static '
                                                 'class ServerTickEvent {public Phase phase;public '
                                                 'ServerTickEvent(Phase p){phase=p;}}}',
 'cpw/mods/fml/common/network/NetworkRegistry.java': 'package cpw.mods.fml.common.network; public class '
                                                     'NetworkRegistry {public static final NetworkRegistry '
                                                     'INSTANCE=new NetworkRegistry();public '
                                                     'cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper '
                                                     'newSimpleChannel(String s){return new '
                                                     'cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper();}}',
 'cpw/mods/fml/common/network/simpleimpl/IMessage.java': 'package '
                                                         'cpw.mods.fml.common.network.simpleimpl;public '
                                                         'interface IMessage {void '
                                                         'toBytes(io.netty.buffer.ByteBuf b);void '
                                                         'fromBytes(io.netty.buffer.ByteBuf b);}',
 'cpw/mods/fml/common/network/simpleimpl/IMessageHandler.java': 'package '
                                                                'cpw.mods.fml.common.network.simpleimpl;public '
                                                                'interface IMessageHandler<A,B> {B '
                                                                'onMessage(A m,MessageContext c);}',
 'cpw/mods/fml/common/network/simpleimpl/MessageContext.java': 'package '
                                                               'cpw.mods.fml.common.network.simpleimpl;public '
                                                               'class MessageContext {public '
                                                               'net.minecraft.network.NetHandlerPlayServer '
                                                               'getServerHandler(){return null;}}',
 'cpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper.java': 'package '
                                                                     'cpw.mods.fml.common.network.simpleimpl; '
                                                                     'public class SimpleNetworkWrapper '
                                                                     '{public void registerMessage(Class<?> '
                                                                     'a,Class<?> b,int '
                                                                     'i,cpw.mods.fml.relauncher.Side s){} '
                                                                     'public void sendToServer(IMessage '
                                                                     'm){}}',
 'cpw/mods/fml/relauncher/Side.java': 'package cpw.mods.fml.relauncher; public enum Side {SERVER,CLIENT}'}

TRAVEL_STUBS = {'com/miaokatze/gtsr/common/dimension/prosperity/travel/AnchorIntent.java': 'package '
                                                                            'com.miaokatze.gtsr.common.dimension.prosperity.travel;public '
                                                                            'class AnchorIntent {public '
                                                                            'static int resets;public static void resetServerState(){resets++;}public static void init(){}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/travel/SpacetimeEffects.java': 'package '
                                                                                'com.miaokatze.gtsr.common.dimension.prosperity.travel;public '
                                                                                'class SpacetimeEffects '
                                                                                '{public static void '
                                                                                'init(){}public static void '
                                                                                'burst(net.minecraft.world.WorldServer '
                                                                                'w,double x,double y,double '
                                                                                'z){}}',
 'com/miaokatze/gtsr/common/items/SpacetimeAnchorBeacon.java': 'package '
                                                               'com.miaokatze.gtsr.common.items;public class '
                                                               'SpacetimeAnchorBeacon {public static void '
                                                               'clampCharge(net.minecraft.item.ItemStack '
                                                               's){}}',
 'com/miaokatze/gtsr/config/Config.java': 'package com.miaokatze.gtsr.config; public class Config {public '
                                          'static int prosperityDimId=78;}',
 'ic2/api/item/ElectricItem.java': 'package ic2.api.item; public class ElectricItem {public static final '
                                   'Manager manager=new Manager();public static class Manager {public double '
                                   'discharge(net.minecraft.item.ItemStack s,double v,int t,boolean '
                                   'a,boolean b,boolean c){return 0;}}}',
 'net/minecraft/block/Block.java': 'package net.minecraft.block;public class Block {public '
                                   'net.minecraft.block.material.Material getMaterial(){return null;}public '
                                   'boolean isSideSolid(net.minecraft.world.World w,int x,int y,int '
                                   'z,net.minecraftforge.common.util.ForgeDirection d){return false;}public '
                                   'String getUnlocalizedName(){return "";}public void '
                                   'addCollisionBoxesToList(net.minecraft.world.World w,int x,int y,int '
                                   'z,net.minecraft.util.AxisAlignedBB b,java.util.List '
                                   'list,net.minecraft.entity.Entity e){}}',
 'net/minecraft/server/management/ServerConfigurationManager.java': 'package '
                                                                    'net.minecraft.server.management; public '
                                                                    'class ServerConfigurationManager { '
                                                                    'public void '
                                                                    'transferPlayerToDimension(net.minecraft.entity.player.EntityPlayerMP '
                                                                    'p,int d,net.minecraft.world.Teleporter '
                                                                    't){} }',
 'net/minecraftforge/common/DimensionManager.java': 'package net.minecraftforge.common;public class '
                                                    'DimensionManager {public static boolean '
                                                    'isDimensionRegistered(int d){return false;} public '
                                                    'static net.minecraft.world.WorldServer getWorld(int '
                                                    'd){return null;} public static void initDimension(int '
                                                    'd){}}',
 'net/minecraftforge/common/util/ForgeDirection.java': 'package net.minecraftforge.common.util;public enum '
                                                       'ForgeDirection {UP}'}


# Complete SpacetimeTravel source now fans out its stop hook. These explicit effect sinks
# test the fan-out without pretending to run the network/altar implementations in R1.
for _relative in ("travel/BeaconNavigationNetwork", "lore/NavigatorNetwork", "altar/SpacetimeAltarStory",
                  "altar/SpacetimeAltarStoryNetwork", "altar/SpacetimeAltarNavigationSearch"):
    _package, _name = _relative.split("/")
    TRAVEL_STUBS["com/miaokatze/gtsr/common/dimension/prosperity/" + _relative + ".java"] = (
        "package com.miaokatze.gtsr.common.dimension.prosperity." + _package + ";public class " + _name
        + " {public static int resets;public static void resetServerState(){resets++;}}")

SUITES = [
    dict(name="persistence", main="RemasterPersistenceCheck",
         changed="prosperity/remaster/RemasterData.java",
         sources=["prosperity/remaster/" + name + ".java" for name in
                  ("RemasterSite", "RemasterRollout", "RemasterCatalog", "RemasterPrefab", "FutureStructurePlanner")],
         test="tools/prosperity/RemasterPersistenceCheck.java",
         stubs={"net/minecraft/world/World.java":
                "package net.minecraft.world; public class World { public net.minecraft.world.storage.MapStorage perWorldStorage; }"}),
    dict(name="anchor", main="com.miaokatze.gtsr.common.dimension.prosperity.travel.AnchorIntentLifecycleCheck",
         changed="prosperity/travel/AnchorIntent.java", sources=[],
         test="tools/prosperity/AnchorIntentLifecycleCheck.java", stubs=ANCHOR_STUBS),
    dict(name="biome", main="BiomeViabilityWindowCheck", changed="framework/GTSRWorldChunkManager.java",
         sources=["framework/GTSRDimensionDef.java", "framework/genlayer/GTSRGenLayerChain.java",
                  "framework/genlayer/GTSRGenLayerSelector.java"],
         test="tools/dim1/BiomeViabilityWindowCheck.java",
         stubs={"com/miaokatze/gtsr/main/GTSteamReborn.java":
                'package com.miaokatze.gtsr.main; public class GTSteamReborn { public static final org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger("GTSR.BiomeFixture"); }'}),
    dict(name="travel", main="TravelServerLifecycleCheck", changed="prosperity/travel/SpacetimeTravel.java",
         sources=[], test="tools/prosperity/TravelServerLifecycleCheck.java", stubs=TRAVEL_STUBS),
]


def run_suite(args, output, suite, baseline):
    mode = "baseline" if baseline else "patched"
    work = output / (suite["name"] + "-" + mode)
    classes = work / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    relative = JAVA + suite["changed"]
    source = (subprocess.check_output(["git", "show", args.baseline_ref + ":" + relative], cwd=ROOT).decode("utf-8")
              if baseline else (ROOT / relative).read_text(encoding="utf-8"))
    # Cached remapped vanilla bytecode has MCP names/raw signatures; source uses Forge's signatures.
    if suite["name"] == "persistence":
        source = source.replace("func_150296_c()", "getKeySet()")
    if suite["name"] == "biome":
        source = source.replace("List<BiomeGenBase> biomes,", "List biomes,")
        source = source.replace("List<BiomeGenBase> biomes)", "List biomes)")
    changed = work / Path(relative).name
    changed.write_text(source, encoding="utf-8")
    files = [changed, ROOT / suite["test"]] + [ROOT / (JAVA + name) for name in suite["sources"]]
    if suite["name"] == "biome":
        if baseline:
            # R2 chain snapshots own their logical window, which would hide the R1 manager bug.
            # Preserve the original pooled-array contract for the baseline viability regression.
            chain_path = JAVA + "framework/genlayer/GTSRGenLayerChain.java"
            chain = work / "GTSRGenLayerChain.java"
            chain.write_text(subprocess.check_output(
                ["git", "show", args.baseline_ref + ":" + chain_path], cwd=ROOT).decode("utf-8"), encoding="utf-8")
            files = [chain if p == ROOT / chain_path else p for p in files]
        else:
            files += [ROOT / (JAVA + name) for name in
                      ("framework/GTSRBiomeAuthority.java", "framework/genlayer/GTSRGenLayerRosterFace.java")]
    for name, body in suite["stubs"].items():
        file = work / "fixtures" / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(body, encoding="utf-8")
        files.append(file)
    compile_result = subprocess.run([args.javac, "-encoding", "UTF-8", "-cp", args.classpath,
                                     "-d", str(classes)] + [str(p) for p in files],
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (work / "compile.log").write_text(compile_result.stdout, encoding="utf-8")
    if compile_result.returncode:
        raise RuntimeError("Compilation failed (not a regression result): " + str(work / "compile.log"))
    cp = os.pathsep.join([str(classes), str(ROOT / "src/main/resources"), args.classpath])
    result = subprocess.run([args.java, "-cp", cp, suite["main"]], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True)
    (work / "test.log").write_text(result.stdout, encoding="utf-8")
    print(mode + "/" + suite["name"] + ": " + result.stdout.strip())
    if baseline:
        if result.returncode == 0 or "AssertionError" not in result.stdout:
            raise RuntimeError("Baseline did not produce the expected assertion failure: " + str(work))
    elif result.returncode:
        raise RuntimeError("Patched regression failed: " + str(work))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True, help="Existing cached jars, using platform path separators")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    parser.add_argument("--output", type=Path, help="Keep sources, fixtures and logs here (default: temporary)")
    parser.add_argument("--baseline-ref", help="Local original git revision; no fetch is performed")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="gtsr-world-lifecycle-") as temporary:
        output = args.output.resolve() if args.output else Path(temporary)
        for suite in SUITES:
            if args.baseline_ref:
                run_suite(args, output, suite, True)
            run_suite(args, output, suite, False)
    print("PASS: four focused source-level suites; not a Forge/gameplay or full-build result")


if __name__ == "__main__":
    main()
