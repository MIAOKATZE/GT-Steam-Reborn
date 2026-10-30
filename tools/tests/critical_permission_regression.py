#!/usr/bin/env python3
"""Exact construction permission/mutation code with explicit finite world/player API fixtures.

No Gradle, downloads, Forge boot, or third-party claim-integration result is implied.
"""
from pathlib import Path
import argparse, subprocess, json, hashlib
R = Path(__file__).resolve().parents[2]
W = R.parents[1]
P = 'com/miaokatze/gtsr/common/critical/'

def method(source, signature):
    start = source.index(signature); opening = source.index('{', start); depth=1; i=opening+1
    while depth:
        depth += (source[i]=='{') - (source[i]=='}'); i += 1
    return source[start:i]

def write(root, name, contents):
    target=root/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(contents);return target

STUBS = {
'net/minecraft/item/ItemStack.java': 'package net.minecraft.item; public class ItemStack {}',
'net/minecraftforge/fluids/FluidStack.java': 'package net.minecraftforge.fluids; public class FluidStack {}',
'gregtech/api/enums/GTValues.java': 'package gregtech.api.enums; public class GTValues {public static final long[] V=new long[10];}',
'com/mojang/authlib/GameProfile.java': '''package com.mojang.authlib; import java.util.UUID; public class GameProfile { public final UUID id; public final String name; public GameProfile(UUID id,String name){this.id=id;this.name=name;} }''',
'net/minecraft/entity/player/EntityPlayer.java': '''package net.minecraft.entity.player; import java.util.UUID; import net.minecraft.world.World; public class EntityPlayer { public World worldObj; private final UUID id; public EntityPlayer(World world,UUID id){worldObj=world;this.id=id;} public UUID getUniqueID(){return id;} }''',
'net/minecraft/world/World.java': '''package net.minecraft.world; import java.util.*; import net.minecraft.entity.player.EntityPlayer; public class World { public boolean isRemote, allow=true, air=true; public final List<Object> playerEntities=new ArrayList<>(); public int queries,writes,lastX,lastY,lastZ; public EntityPlayer lastActor; public boolean canMineBlock(EntityPlayer player,int x,int y,int z){queries++;lastActor=player;lastX=x;lastY=y;lastZ=z;return allow;} public boolean isAirBlock(int x,int y,int z){return air;} public boolean blockExists(int x,int y,int z){return true;} public boolean setBlock(int x,int y,int z,Object block,int meta,int flags){writes++;air=false;return true;} public boolean setBlockToAir(int x,int y,int z){writes++;air=true;return true;} }''',
'net/minecraft/world/WorldServer.java': '''package net.minecraft.world; public class WorldServer extends World {}''',
'net/minecraftforge/common/util/FakePlayer.java': '''package net.minecraftforge.common.util; import com.mojang.authlib.GameProfile; import net.minecraft.world.WorldServer; import net.minecraft.entity.player.EntityPlayer; public class FakePlayer extends EntityPlayer { public static int allocations; public FakePlayer(WorldServer world,GameProfile profile){super(world,profile.id);allocations++;} }''',
}
FIXTURE=r'''
package com.miaokatze.gtsr.common.critical;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
class CriticalConstructionLedger { static class Round {int end=1,next=1,voxelEnd=1,advance=1;ItemStack[] items(){return new ItemStack[]{new ItemStack()};}FluidStack[] fluids(){return new FluidStack[0];}}}
class CriticalConstructionCosts {static ItemStack[] forStage(Object config,int stage,int count){return new ItemStack[0];}static FluidStack[] forStageFluids(Object config,int stage,int count){return new FluidStack[0];}}
class CriticalGeometry {
 static class Voxel { int x=37,y=71,z=-53,metadata=2; Block block=new Block(); }
 static class Block { int getLightValue(World w,int x,int y,int z){return 0;} }
}
class CriticalStructures {
 static boolean isController(CriticalGeometry.Voxel v){return false;}
 static class ControllerElement { ControllerElement(boolean b,Object kind,Object facing){} boolean placeController(Object host,World w,int x,int y,int z){return w.setBlock(x,y,z,new Object(),0,2);} }
}
class CriticalLayoutDefinition { static boolean isSupplySupport(CriticalGeometry.Voxel v){return false;} }
class PermissionState {
 World worldObj; String owner; final CriticalConstructionAccess constructionAccess=new CriticalConstructionAccess();
 int blocksChangedThisTick,lightsChangedThisTick,cursor,stage,constructionTickBudget=1,constructionLastAdvance,fundingCalls;long blockBudgetStarted;
 boolean debugIgnoreEnergy=true,nativeFundingUncertain,constructionAdvancing;
 boolean protectionDirty;String status="";
 BitSet placed=new BitSet();List<CriticalGeometry.Voxel> geometry=new ArrayList<>();
 Map<CriticalGeometry.Voxel,Integer> voxelIndices=new IdentityHashMap<>();
 static class Tier {long constructionVoltage(){return 1;}} static class Config {Object kind;Tier tier=new Tier();} Config config=new Config();
 static class Ledger { int installed,damaged,payments,elapsed,completed;boolean funded;
 boolean isPhysicalJob(){return false;}boolean hasStageSnapshot(){return true;}boolean funded(){return funded;}int economyVersion(){return 6;}
 void snapshotPhysicalStage(List<CriticalGeometry.Voxel> v){}void snapshotStage(ItemStack[] items,FluidStack[] fluids){}
 CriticalConstructionLedger.Round planPhysicalRound(int budget,List<CriticalGeometry.Voxel> v){return planRound(budget,v.size());}
 CriticalConstructionLedger.Round planRound(int budget,int size){var r=new CriticalConstructionLedger.Round();r.advance=1-elapsed;return r;}
 void recordRoundPayment(ItemStack[] items,FluidStack[] fluids){funded=true;payments++;}void advanceElapsed(int next){elapsed=next;}int stageDuration(){return 1;}void completeRound(){completed++;}
 void markPhysicalInstalled(CriticalGeometry.Voxel v){installed++;}void markPhysicalDamaged(CriticalGeometry.Voxel v){damaged++;} }
 Ledger constructionLedger=new Ledger();
 Object gtMachine(){return null;} Object getStructureFacing(){return null;}
 int wx(CriticalGeometry.Voxel v){return v.x;}int wy(CriticalGeometry.Voxel v){return v.y;}int wz(CriticalGeometry.Voxel v){return v.z;}
 boolean fail(String reason){status=reason;return false;}
 boolean hasBlockBudget(CriticalGeometry.Voxel v){return true;} boolean matchesMaterial(CriticalGeometry.Voxel v){return true;}
 boolean matches(CriticalGeometry.Voxel v){return !worldObj.air;} boolean isUserBuilt(CriticalGeometry.Voxel v){return false;}
 List<CriticalGeometry.Voxel> paidStage(List<CriticalGeometry.Voxel> v){return v;}void resetConstructionStage(){}void changed(){}
 int loadedWindowEnd(List<CriticalGeometry.Voxel> v,int cursor,int end){return end;}boolean isLoomJournalOwner(){return false;}
 boolean ignoresConstructionItems(){return false;}boolean ignoresConstructionFluids(){return false;}boolean canConsumeConstructionEnergy(long eu){return true;}
 boolean fundCriticalInputs(ItemStack[] items,FluidStack[] fluids){fundingCalls++;return true;}long getEnergy(){return Long.MAX_VALUE;}boolean consumeEnergy(long eu){return true;}
 void advance(){advanceConstruction(geometry,false);}
 void markDirty(){} int previousActivePaidIndex(int from){return placed.previousSetBit(from);}
 PermissionState(World world,String owner,CriticalGeometry.Voxel voxel){this.worldObj=world;this.owner=owner;geometry.add(voxel);voxelIndices.put(voxel,0);}
 boolean place(CriticalGeometry.Voxel v){return placeOwnedBlock(v);} boolean supply(CriticalGeometry.Voxel v){return canEditSupply(v);}
 boolean remove(CriticalGeometry.Voxel v){return removeTerraceVoxel(v);}
 __METHODS__
 void dismantleMutationLoop(){List<CriticalGeometry.Voxel> voxels=geometry;int next=previousActivePaidIndex(voxels.size()-1-cursor);
 __LOOP__
 }
}
public class CriticalPermissionRegression {
 static int checks,failures; static final UUID OWNER=UUID.fromString("11111111-2222-3333-4444-555555555555");
 static final UUID OTHER=UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 interface Run {void run();} static void scenario(String name,Run run){try{run.run();System.out.println("PASS "+name);}catch(AssertionError error){failures++;System.out.println("FAIL "+name+": "+error.getMessage());}}
 static PermissionState state(World world,CriticalGeometry.Voxel v){return new PermissionState(world,OWNER.toString(),v);}
 static void target(World world,CriticalGeometry.Voxel v){check(world.lastActor!=null && world.lastActor.worldObj==world && OWNER.equals(world.lastActor.getUniqueID()),"exact target world and owner UUID");check(world.lastX==v.x && world.lastY==v.y && world.lastZ==v.z,"exact target coordinates");}
 public static void main(String[] args){
 scenario("online-denied",()->{WorldServer w=new WorldServer();w.allow=false;EntityPlayer p=new EntityPlayer(w,OWNER);w.playerEntities.add(p);var v=new CriticalGeometry.Voxel();var s=state(w,v);check(!s.place(v)&&w.writes==0&&s.placed.isEmpty()&&s.constructionLedger.installed==0,"online denial preserves block and ledger");check(w.lastActor==p,"uses real online owner");target(w,v);});
 scenario("offline-denied",()->{WorldServer w=new WorldServer();w.allow=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);check(!s.place(v)&&w.writes==0&&s.placed.isEmpty()&&s.constructionLedger.installed==0,"offline denial preserves block and ledger");target(w,v);});
 scenario("other-dimension-denied",()->{WorldServer w=new WorldServer(),other=new WorldServer();other.playerEntities.add(new EntityPlayer(other,OWNER));w.allow=false;w.playerEntities.add(new EntityPlayer(w,OTHER));var v=new CriticalGeometry.Voxel();var s=state(w,v);check(!s.place(v)&&w.writes==0,"owner in other dimension cannot bypass denial");target(w,v);});
 scenario("offline-allowed-and-cached",()->{WorldServer w=new WorldServer();var v=new CriticalGeometry.Voxel();var s=state(w,v);int before=FakePlayer.allocations;check(s.place(v)&&w.writes==1&&s.placed.get(0)&&s.constructionLedger.installed==1,"permitted offline placement journals exactly once");target(w,v);Object actor=w.lastActor;for(int i=0;i<1000;i++)check(s.supply(v),"permitted repeated edit");check(w.lastActor==actor&&FakePlayer.allocations==before+1,"offline actor reused rather than allocated per voxel");});
 scenario("world-owner-change",()->{WorldServer w=new WorldServer(),other=new WorldServer();var v=new CriticalGeometry.Voxel();var s=state(w,v);check(s.supply(v),"first world permission");Object actor=w.lastActor;s.worldObj=other;check(s.supply(v)&&other.lastActor!=actor,"new world gets distinct actor");target(other,v);s.owner=OTHER.toString();Object second=other.lastActor;check(s.supply(v)&&other.lastActor!=second&&OTHER.equals(other.lastActor.getUniqueID())&&other.lastActor.worldObj==other,"new owner gets exact owner actor");});
 scenario("online-arrival-departure",()->{WorldServer w=new WorldServer();var v=new CriticalGeometry.Voxel();var s=state(w,v);check(s.supply(v),"offline first");Object offline=w.lastActor;EntityPlayer p=new EntityPlayer(w,OWNER);w.playerEntities.add(p);check(s.supply(v)&&w.lastActor==p,"online actor replaces cached offline actor");w.playerEntities.clear();check(s.supply(v)&&w.lastActor==offline,"offline actor resumes safely");});
 scenario("unknown-owner-client",()->{WorldServer w=new WorldServer();var v=new CriticalGeometry.Voxel();var s=state(w,v);for(String owner:new String[]{"","LegacyName","1-1-1-1-1",null}){s.owner=owner;check(!s.supply(v)&&w.writes==0,"unknown owner fails closed");}s.owner=OWNER.toString();w.isRemote=true;check(!s.supply(v)&&w.queries==0,"client cannot authorize mutation");});
 scenario("crossworld-online-list",()->{WorldServer w=new WorldServer(),other=new WorldServer();EntityPlayer p=new EntityPlayer(other,OWNER);w.playerEntities.add(p);var v=new CriticalGeometry.Voxel();var s=state(w,v);check(s.supply(v)&&w.lastActor!=p,"wrong-world actor cannot be borrowed from player list");target(w,v);});
 scenario("supply-denied",()->{WorldServer w=new WorldServer();w.allow=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);check(!s.supply(v)&&w.writes==0,"offline supply upgrade honors permission");target(w,v);});
 scenario("terrace-removal-denied",()->{WorldServer w=new WorldServer();w.air=false;w.allow=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);check(!s.remove(v)&&w.writes==0&&!w.air,"denied terrace removal preserves block");target(w,v);});
 scenario("dismantle-denied",()->{WorldServer w=new WorldServer();w.air=false;w.allow=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);s.placed.set(0);s.dismantleMutationLoop();check(w.writes==0&&!w.air&&s.cursor==0&&s.placed.get(0),"denied dismantle preserves block and cursor");target(w,v);});
 scenario("paid-permission-retry",()->{WorldServer w=new WorldServer();w.allow=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);s.advance();check(w.writes==0&&s.cursor==0&&s.constructionLedger.funded&&s.constructionLedger.payments==1&&s.fundingCalls==1&&s.constructionLedger.elapsed==1,"denied placement keeps paid checkpoint");s.advance();check(w.writes==0&&s.fundingCalls==1&&s.constructionLedger.payments==1,"denied retry does not pay again");w.allow=true;s.advance();check(w.writes==1&&s.placed.get(0)&&s.constructionLedger.installed==1&&s.fundingCalls==1&&s.constructionLedger.completed==1&&s.stage==1,"permission restoration installs paid work exactly once");});
 scenario("controller-local-actors",()->{WorldServer w=new WorldServer();var v=new CriticalGeometry.Voxel();var first=state(w,v);var second=state(w,v);check(first.supply(v),"first controller access");Object actor=w.lastActor;check(second.supply(v)&&w.lastActor!=actor,"controllers do not share global profile-only actor");});
 scenario("permitted-removals",()->{WorldServer w=new WorldServer();w.air=false;var v=new CriticalGeometry.Voxel();var s=state(w,v);check(s.remove(v)&&w.air&&w.writes==1,"allowed terrace removal preserved");w.air=false;s.placed.set(0);s.dismantleMutationLoop();check(w.air&&w.writes==2&&s.cursor==1,"allowed dismantle progression preserved");});
 System.out.println("CriticalPermissionRegression checks="+checks+" failures="+failures);if(failures!=0)System.exit(1);
 }
}
'''

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--source-dir',type=Path,default=R/'src/main/java');ap.add_argument('--helper-dir',type=Path);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--java-home',type=Path,default=W/'toolchains/zulu17.70.113-ca-jdk17.0.21.0.3-linux_x64');args=ap.parse_args()
    state=(args.source_dir/P/'CriticalMachineState.java').read_text();helper=( (args.helper_dir or args.source_dir)/P/'CriticalConstructionAccess.java').read_text()
    methods='\n'.join(method(state,sig) for sig in ['private boolean placeOwnedBlock(', 'private boolean canEditSupply(', 'private boolean removeTerraceVoxel(', 'private void advanceConstruction('])
    dismantle=method(state,'private void dismantle(');start=dismantle.index('        for (int i = 0; i < 128 && next >= 0; i++) {');end=dismantle.index('        if (next < 0) cursor = voxels.size();');loop=dismantle[start:end]
    out=args.out;src=out/'src';src.mkdir(parents=True,exist_ok=True)
    files=[write(src,n,c) for n,c in STUBS.items()];files.append(write(src,P+'CriticalConstructionAccess.java',helper));files.append(write(src,P+'CriticalPermissionRegression.java',FIXTURE.replace('__METHODS__',methods).replace('__LOOP__',loop)))
    cmd=[str(args.java_home/'bin/javac'),'-encoding','UTF-8','-d',str(out/'classes'),*map(str,files)];result=subprocess.run(cmd,capture_output=True,text=True);(out/'compile.log').write_text(result.stdout+result.stderr)
    if result.returncode:print(result.stdout+result.stderr);return result.returncode
    result=subprocess.run([str(args.java_home/'bin/java'),'-cp',str(out/'classes'),'com.miaokatze.gtsr.common.critical.CriticalPermissionRegression'],capture_output=True,text=True);(out/'test.log').write_text(result.stdout+result.stderr);print(result.stdout+result.stderr)
    (out/'receipt.json').write_text(json.dumps({'exit':result.returncode,'sourceSHA256':hashlib.sha256(state.encode()).hexdigest(),'helperSHA256':hashlib.sha256(helper.encode()).hexdigest(),'boundary':'Exact complete advanceConstruction/placeOwnedBlock/canEditSupply/removeTerraceVoxel methods and complete dismantle mutation loop; complete production access helper. MC/Forge world/player/fakeplayer APIs and construction ledger/payment endpoints here are explicit finite fixtures. Separate native full-source compilation is required; no native gameplay or third-party claims certified.'},indent=2));return result.returncode
if __name__=='__main__':raise SystemExit(main())
