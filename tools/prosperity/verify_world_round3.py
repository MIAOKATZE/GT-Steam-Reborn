#!/usr/bin/env python3
"""Offline R3 native NBT and player/connection lifetime regressions; no Gradle/network/game startup."""
import argparse, hashlib, json, os, shutil, subprocess, sys
from pathlib import Path
sys.dont_write_bytecode = True
from verify_world_lifecycle import ANCHOR_STUBS, TRAVEL_STUBS
from verify_world_round2 import ALTAR_FIXTURES
ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/'
BASELINE = '57d082e9e748c8918ae3bb2c33df386c9a1cc99e'
NAV_FIXTURES = dict(ANCHOR_STUBS)
NAV_FIXTURES.pop('com/miaokatze/gtsr/common/dimension/prosperity/travel/SpacetimeTravel.java')
NAV_FIXTURES.update({
 'cpw/mods/fml/common/network/simpleimpl/MessageContext.java': '''package cpw.mods.fml.common.network.simpleimpl;public class MessageContext {private final net.minecraft.network.NetHandlerPlayServer handler;public MessageContext(net.minecraft.network.NetHandlerPlayServer h){handler=h;}public net.minecraft.network.NetHandlerPlayServer getServerHandler(){return handler;}}''',
 'cpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper.java': '''package cpw.mods.fml.common.network.simpleimpl;public class SimpleNetworkWrapper {public static final java.util.List<IMessage> sent=new java.util.ArrayList<>();public static final java.util.List<net.minecraft.entity.player.EntityPlayerMP> receivers=new java.util.ArrayList<>();public void registerMessage(Class<?> a,Class<?> b,int i,cpw.mods.fml.relauncher.Side s){}public void sendToServer(IMessage m){}public void sendTo(IMessage m,net.minecraft.entity.player.EntityPlayerMP p){sent.add(m);receivers.add(p);}}''',
 'cpw/mods/fml/relauncher/SideOnly.java': 'package cpw.mods.fml.relauncher;public @interface SideOnly {Side value();}',
 'com/miaokatze/gtsr/common/dimension/prosperity/WorldProviderProsperityRuins.java': 'package com.miaokatze.gtsr.common.dimension.prosperity;public class WorldProviderProsperityRuins extends net.minecraft.world.WorldProviderSurface {public WorldProviderProsperityRuins(){dimensionId=78;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/altar/SpacetimeAltarNavigationSearch.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.altar;public class SpacetimeAltarNavigationSearch {public static int calls;public static void beginTick(){}public static void tick(){}public static void resetServerState(){calls=0;}public static void keepAlive(net.minecraft.world.World w,double x,double z){}public static int targetStatus(net.minecraft.world.World w,int x,int z){return 1;}public static int[] nearest(net.minecraft.world.World w,double x,double z){calls++;return new int[]{7,7};}}''',
 'com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterData.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.remaster;public class RemasterData {public static RemasterData get(net.minecraft.world.World w){return new RemasterData();}public java.util.List<GeneratedOwner> generatedOwners(){return java.util.Collections.emptyList();}public static class GeneratedOwner {public RemasterSite site;}}''',
 'com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterSite.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.remaster;public class RemasterSite {public long seed;public int entryX(){return 0;}public int entryZ(){return 0;}public String id(){return "fixture";}}''',
 'com/miaokatze/gtsr/main/GTSteamReborn.java': '''package com.miaokatze.gtsr.main;public class GTSteamReborn {public static final org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger("NavigationFixture");}''',
 'com/miaokatze/gtsr/client/travel/BeaconNavigationClient.java': '''package com.miaokatze.gtsr.client.travel;public class BeaconNavigationClient {public static void receive(com.miaokatze.gtsr.common.dimension.prosperity.travel.BeaconNavigationNetwork.Target t){}}''',
 'com/miaokatze/gtsr/client/lore/NavigatorClient.java': '''package com.miaokatze.gtsr.client.lore;public class NavigatorClient {public static void receive(com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork.Result r){}}''',
 'com/miaokatze/gtsr/common/dimension/prosperity/lore/LoreRegistry.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.lore;public class LoreRegistry {public static final java.util.Map<String,net.minecraft.item.Item> RELICS=new java.util.HashMap<>();static {RELICS.put("future_city_address_witness",new net.minecraft.item.Item());}}''',
 'com/miaokatze/gtsr/common/dimension/prosperity/lore/HistoryProgress.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.lore;public class HistoryProgress {public static void recordGrantedRelic(net.minecraft.entity.player.EntityPlayer p,String id){}}''',
 'com/miaokatze/gtsr/common/dimension/prosperity/lore/LoreNetwork.java': '''package com.miaokatze.gtsr.common.dimension.prosperity.lore;public class LoreNetwork {public static void send(net.minecraft.entity.player.EntityPlayerMP p,boolean b){}}''',
 'com/miaokatze/gtsr/common/commands/RuinLocateCommand.java': '''package com.miaokatze.gtsr.common.commands;public class RuinLocateCommand {public static int begins,steps;public static java.util.List<String> names(){return java.util.Arrays.asList("fallen_foundry");}public static Lookup beginNearest(long seed,String id,int x,int z,net.minecraft.world.WorldServer w){begins++;return new Lookup();}public static class Lookup {public void step(){steps++;}public boolean done(){return true;}public Location result(){return null;}}public static class Location {public int x,z;}}''',
 'net/minecraftforge/common/util/FakePlayer.java': ALTAR_FIXTURES['net/minecraftforge/common/util/FakePlayer.java'],
})

ALTAR_R3_FIXTURES = dict(NAV_FIXTURES)
ALTAR_R3_FIXTURES.update(ALTAR_FIXTURES)
ALTAR_R3_FIXTURES.pop('com/miaokatze/gtsr/common/dimension/prosperity/altar/SpacetimeAltarStoryNetwork.java')
ALTAR_R3_FIXTURES['com/miaokatze/gtsr/client/lore/GuiSpacetimeAltarStory.java'] = 'package com.miaokatze.gtsr.client.lore;public class GuiSpacetimeAltarStory {public static void receive(com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarStoryNetwork.Reading r){}}'
ALTAR_R3_FIXTURES['cpw/mods/fml/common/network/ByteBufUtils.java'] = 'package cpw.mods.fml.common.network;public class ByteBufUtils {public static void writeUTF8String(io.netty.buffer.ByteBuf b,String s){throw new UnsupportedOperationException("serialization not exercised");}public static String readUTF8String(io.netty.buffer.ByteBuf b){throw new UnsupportedOperationException("serialization not exercised");}}'

PORTAL_FIXTURES = {
 'com/miaokatze/gtsr/common/machine/MTESpacetimeCalibration.java': 'package com.miaokatze.gtsr.common.machine;public class MTESpacetimeCalibration {private final Base base;public MTESpacetimeCalibration(net.minecraft.world.World world){base=new Base(world);}public boolean isPortalActive(){return true;}public boolean validatePortalForTravel(){return true;}public Base getBaseMetaTileEntity(){return base;}public net.minecraft.util.Vec3 worldToLocal(double x,double y,double z){return net.minecraft.util.Vec3.createVectorHelper(x,y,z);}public net.minecraft.util.Vec3 localToWorld(double x,double y,double z){return net.minecraft.util.Vec3.createVectorHelper(x,y,z);}public static class Base{private final net.minecraft.world.World world;Base(net.minecraft.world.World world){this.world=world;}public net.minecraft.world.World getWorld(){return world;}}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/travel/SpacetimeTravel.java': 'package com.miaokatze.gtsr.common.dimension.prosperity.travel;public class SpacetimeTravel {public static int attempts;public static boolean eligible(net.minecraft.entity.player.EntityPlayerMP p){return p!=null&&!p.isDead;}public static boolean enterProsperity(net.minecraft.entity.player.EntityPlayerMP p,boolean message){attempts++;return false;}}',
 'com/miaokatze/gtsr/common/dimension/prosperity/portal/PortalEntityData.java': 'package com.miaokatze.gtsr.common.dimension.prosperity.portal;public class PortalEntityData {private static final java.util.Map<net.minecraft.entity.Entity,net.minecraft.nbt.NBTTagCompound> data=new java.util.IdentityHashMap<>();public static net.minecraft.nbt.NBTTagCompound get(net.minecraft.entity.Entity entity){return data.computeIfAbsent(entity,k->new net.minecraft.nbt.NBTTagCompound());}}',
}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--workspace',type=Path,default=ROOT.parents[1]);p.add_argument('--out',type=Path)
    p.add_argument('--baseline-dir',type=Path);p.add_argument('--baseline-ref',default=BASELINE)
    p.add_argument('--suite',choices=['all','data','navigation','altar','anchor','stop','search','portal'],default='all');p.add_argument('--baseline-only',action='store_true')
    args=p.parse_args();workspace=args.workspace.resolve();out=(args.out or workspace/'review-gtsr-world-r3/reproducible').resolve();out.mkdir(parents=True,exist_ok=True)
    jdk=next((workspace/'toolchains').glob('*jdk17*/bin'));cache=workspace/'.gradle-build/caches/modules-2/files-2.1'
    jars=[workspace/'review-monitoring/minecraft-mcp.jar',workspace/'dependency-probe/gtsr-v2.0.43-dev.jar']
    for lib in ('com.google.code.gson/gson/2.2.4','com.google.guava/guava/17.0','org.apache.logging.log4j/log4j-api/2.0-beta9-fixed','org.apache.logging.log4j/log4j-core/2.0-beta9-fixed','io.netty/netty-all/4.0.10.Final','com.mojang/authlib/1.5.21','org.apache.commons/commons-lang3'):
        jars.append(sorted(x for x in (cache/lib).rglob('*.jar') if not x.name.endswith(('-sources.jar','-javadoc.jar')))[0])
    cp=os.pathsep.join(map(str,jars));empty=out/'empty';empty.mkdir(exist_ok=True);results={};hashes={};baseline_hashes={}
    for script in ('verify_world_round3.py','verify_world_round2.py','verify_world_lifecycle.py'):
        path=ROOT/'tools/prosperity'/script;hashes[str(path.relative_to(ROOT))]=hashlib.sha256(path.read_bytes()).hexdigest()
    def source(relative,baseline):
        if not baseline:
            raw=(ROOT/relative).read_bytes();hashes[relative]=hashlib.sha256(raw).hexdigest();return raw.decode()
        overlay=args.baseline_dir/relative if args.baseline_dir else None
        body=overlay.read_text() if overlay and overlay.is_file() else subprocess.check_output(['git','show',args.baseline_ref+':'+relative],cwd=ROOT,text=True)
        baseline_hashes[relative]=hashlib.sha256(body.encode()).hexdigest();return body
    def run(label,command,red=False):
        r=subprocess.run([str(jdk/command[0]),*map(str,command[1:])],cwd=out,capture_output=True,text=True,timeout=90)
        log=r.stdout+r.stderr;(out/(label+'.log')).write_text(log);results[label]={'exit':r.returncode,'log':label+'.log'}
        if red:
            if not r.returncode or 'java.lang.AssertionError:' not in log:raise AssertionError('Expected behavioral RED, not API/linkage failure: '+label+'\n'+log)
        elif r.returncode:raise AssertionError(label+'\n'+log)
        print(label+(': expected behavioral RED' if red else ': PASS'))
    suites=[dict(name='data',changed=['remaster/RemasterData.java'],support=['remaster/'+n+'.java' for n in ('RemasterSite','RemasterRollout','RemasterCatalog','RemasterPrefab','FutureStructurePlanner')],tests=['RemasterOpaqueStateCheck'],fixtures={'net/minecraft/world/World.java':'package net.minecraft.world;public class World {public net.minecraft.world.storage.MapStorage perWorldStorage;}'}),dict(name='navigation',changed=['travel/BeaconNavigationNetwork.java','lore/NavigatorNetwork.java'],support=['travel/BeaconTargetLock.java'],tests=['NavigationLifetimeCheck'],fixtures=NAV_FIXTURES)]
    suites += [dict(name='altar',changed=['altar/SpacetimeAltarStory.java','altar/SpacetimeAltarStoryNetwork.java'],support=[],tests=['AltarTransientLifetimeCheck'],extra_tests=['AltarReadingLifecycleCheck'],fixtures=ALTAR_R3_FIXTURES),
               dict(name='anchor',changed=['travel/AnchorIntent.java'],support=[],tests=['AnchorIntentLifecycleCheck'],fixtures=ANCHOR_STUBS),
               dict(name='stop',changed=['travel/SpacetimeTravel.java'],support=[],tests=['TravelServerLifecycleCheck'],fixtures=TRAVEL_STUBS),
               dict(name='search',changed=['altar/SpacetimeAltarNavigationSearch.java'],support=[],tests=['AltarSearchLifecycleCheck'],fixtures={'cpw/mods/fml/common/IWorldGenerator.java':'package cpw.mods.fml.common;public interface IWorldGenerator {}'}),
               dict(name='portal',changed=['portal/SpacetimeAttraction.java'],support=['portal/CoreTransferGate.java'],tests=['PortalContactLifetimeCheck'],fixtures=PORTAL_FIXTURES)]
    for suite in suites:
        if args.suite!='all' and suite['name']!=args.suite:continue
        for baseline in (True,) if args.baseline_only else (True,False):
            label=suite['name']+('-baseline' if baseline else '-patched');work=out/label;classes=work/'classes'
            if classes.exists():shutil.rmtree(classes)
            classes.mkdir(parents=True);files=[]
            for name in suite['changed']+suite['support']:
                body=source(PREFIX+name,baseline and name in suite['changed']).replace('func_150296_c()','getKeySet()').replace('func_145797_a(', 'setOwner(').replace('func_145799_b(', 'setThrower(')
                if suite['name']=='portal':
                    # Vanilla MCP bytecode lacks Forge's attached entity NBT. Keep native equality and geometry.
                    body=body.replace('entity.getEntityData()', 'PortalEntityData.get(entity)').replace('player.getEntityData()', 'PortalEntityData.get(player)')
                path=work/'source'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
            if suite['name'] in ('navigation','altar'):
                authority=ROOT/'src/main/java/com/miaokatze/gtsr/common/network/ServerPacketAuthority.java'
                hashes[str(authority.relative_to(ROOT))]=hashlib.sha256(authority.read_bytes()).hexdigest();files.append(authority)
            for name,body in suite['fixtures'].items():
                path=work/'fixtures'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);files.append(path)
            for test in suite['tests']+suite.get('extra_tests',[]):
                path=ROOT/'tools/prosperity'/(test+'.java');hashes[str(path.relative_to(ROOT))]=hashlib.sha256(path.read_bytes()).hexdigest();files.append(path)
            run(label+'-compile',['javac','-proc:none','-encoding','UTF-8','-sourcepath',empty,'-cp',cp,'-d',classes,*files])
            if suite['name']=='stop':
                for name in ('net/minecraft/block/Block.class','net/minecraft/server/management/ServerConfigurationManager.class'):(classes/name).unlink()
            for test in suite['tests']:
                main=('com.miaokatze.gtsr.common.dimension.prosperity.travel.' if suite['name']=='anchor' else '')+test
                run(label+'-'+test,['java','-cp',os.pathsep.join([str(classes),str(ROOT/'src/main/resources'),cp]),main],baseline)
    for path,digest in hashes.items():
        if hashlib.sha256((ROOT/path).read_bytes()).hexdigest()!=digest:raise AssertionError('Source changed during check: '+path)
    report={'baseline_ref':args.baseline_ref,'baseline_overlay':str(args.baseline_dir),'results':results,'source_and_test_sha256':hashes,'baseline_source_sha256':baseline_hashes,'jar_sha256':{str(j):hashlib.sha256(j.read_bytes()).hexdigest() for j in jars},'limitations':'Complete changed sources; native Minecraft NBT/player/equality/NetworkManager/server/configuration list. Mechanical MCP getter/item-owner renames; portal-only Forge Entity.getEntityData extension is supplied by an explicit identity-keyed NBT sink. Portal machine/world/failed-transfer calls are fixtures while Entity.equals and geometry are native. FML packet transport, target lookup, item registration, world/block/board/payment and reset-fanout effect sinks are explicit fixtures. Altar search tests compile complete service against cached own-dev/native jars and an IWorldGenerator marker, but exercise reset only. Weak-reference field/retirement checks are deterministic graph/behavior checks, not GC timing proof. Forge-only recall compile signatures are removed before runtime. No real packets, client/server, disk saves, successful transfer, energy/vis payment, side stripping or full Gradle build.'}
    (out/'verification.json').write_text(json.dumps(report,indent=2));print('PASS: focused R3 native boundary suites only')
if __name__=='__main__':main()
