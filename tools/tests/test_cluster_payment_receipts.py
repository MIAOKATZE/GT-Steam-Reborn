#!/usr/bin/env python3
"""Offline payment regression: compile whole production executor/economy with narrow world fixtures.

The logistics ME-close method is copied verbatim from production into its fixture. Minecraft
Item/ItemStack are native (MCP-remapped jar); GT machine/network, recipe, Forge fluid fixtures
model explicit success/partial-failure receipts. This is not a running Minecraft/AE network.
Pass --source-dir for the three baseline files. Needs --javac and --classpath (native MC + libs).
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

p=argparse.ArgumentParser()
p.add_argument('--javac', required=True)
cp=p.add_mutually_exclusive_group(required=True)
cp.add_argument('--classpath')
cp.add_argument('--classpath-file', type=Path)
p.add_argument('--source-dir', type=Path)
p.add_argument('--case', choices=('receipts', 'rounds', 'batch', 'all'), default='receipts')
a=p.parse_args()
classpath=a.classpath or a.classpath_file.read_text(encoding='utf-8-sig').strip()
repo=Path(__file__).resolve().parents[2]
entries=[]
for entry in classpath.split(os.pathsep):
    path=Path(entry)
    if path.suffix == '.jar' and path.exists():
        with zipfile.ZipFile(path) as jar:
            if 'META-INF/services/com.sun.source.util.Plugin' in jar.namelist():
                continue
    entries.append(entry)
classpath=os.pathsep.join([str(repo/'build/classes/java/patchedMc'),*entries])
src=a.source_dir or repo/'src/main/java/com/miaokatze/gtsr/common/machine/cluster'
cluster='com.miaokatze.gtsr.common.machine.cluster'

def method(text, marker):
    start=text.index(marker); brace=text.index('{', start); depth=1; i=brace+1
    while depth:
        depth += (text[i]=='{')-(text[i]=='}'); i+=1
    return text[start:i]

unit=(src/'MTEBasicLogisticsUnit.java').read_text(encoding='utf-8')
close=method(unit, 'public '+('boolean' if 'public boolean endMEBusProcessing' in unit else 'void')+' endMEBusProcessing')
fixtures={
'net.minecraftforge.common.util.ForgeDirection': 'public enum ForgeDirection {UNKNOWN}',
'net.minecraftforge.fluids.Fluid': 'public class Fluid {private final String name; public Fluid(String s){name=s;}public String getName(){return name;}}',
'net.minecraftforge.fluids.FluidRegistry': 'public class FluidRegistry {public static final Fluid WATER=new Fluid("water");}',
'net.minecraftforge.fluids.FluidStack': 'public class FluidStack {public int amount;private final Fluid f;public FluidStack(Fluid f,int n){this.f=f;amount=n;}public Fluid getFluid(){return f;}public FluidStack copy(){return new FluidStack(f,amount);}}',
'net.minecraftforge.fluids.FluidTankInfo': 'public class FluidTankInfo {public FluidStack fluid;}',
'gregtech.api.metatileentity.implementations.MTEHatch': 'public class MTEHatch {}',
'gregtech.api.metatileentity.implementations.MTEHatchInput': 'public class MTEHatchInput extends MTEHatch {public net.minecraftforge.fluids.FluidTankInfo[] getTankInfo(net.minecraftforge.common.util.ForgeDirection d){return null;}}',
'gregtech.api.metatileentity.implementations.MTEHatchInputBus': 'public class MTEHatchInputBus {public net.minecraft.item.ItemStack stack;public int getSizeInventory(){return 1;}public net.minecraft.item.ItemStack getStackInSlot(int i){return stack;}}',
'gregtech.api.metatileentity.implementations.MTEHatchOutputBus': 'public class MTEHatchOutputBus {public int capacity=Integer.MAX_VALUE;public net.minecraft.item.ItemStack stack;public int getSizeInventory(){return 1;}public net.minecraft.item.ItemStack getStackInSlot(int i){return stack;}public void setInventorySlotContents(int i,net.minecraft.item.ItemStack s){stack=s;}}',
'gregtech.api.recipe.check.CheckRecipeResult': 'public class CheckRecipeResult {public boolean paid;public CheckRecipeResult(boolean p){paid=p;}public boolean wasSuccessful(){return paid;}}',
'gregtech.common.tileentities.machines.MTEHatchInputBusME': '''public class MTEHatchInputBusME extends gregtech.api.metatileentity.implementations.MTEHatchInputBus {public boolean paid=true;public int closes;public gregtech.api.recipe.check.CheckRecipeResult endRecipeProcessing(com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster c){closes++;if(!paid)c.stopped=true;return new gregtech.api.recipe.check.CheckRecipeResult(paid);}}''',
'gregtech.api.interfaces.IOutputTransaction': 'public interface IOutputTransaction {interface IRecipeCheckAware {void setRecipeCheck(boolean b);}}',
'gregtech.api.interfaces.IOutputBusTransaction': 'public interface IOutputBusTransaction {void storePartial(gregtech.api.util.GTUtility.ItemId i,net.minecraft.item.ItemStack s,long a,long b);void commit();}',
'gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME': 'public class MTEHatchOutputBusME extends gregtech.api.metatileentity.implementations.MTEHatchOutputBus {public gregtech.api.interfaces.IOutputBusTransaction createTransaction(){return null;}}',
'gregtech.api.util.shutdown.ShutDownReasonRegistry': 'public class ShutDownReasonRegistry {public static final Object CRITICAL_NONE=new Object();}',
'gregtech.api.objects.XSTR': 'public class XSTR extends java.util.Random {public static int draws;public static boolean fixed;public static double gaussian;@Override public double nextGaussian(){draws++;return fixed?gaussian:super.nextGaussian();}@Override public double nextDouble(){draws++;return fixed?.5:super.nextDouble();}}',
'gregtech.api.util.GTUtility': '''public class GTUtility {private static final java.util.Map<Integer,net.minecraft.item.ItemStack> stacks=new java.util.HashMap<>();public static <T> java.util.List<T> validMTEList(java.util.List<T> l){return l;}public static boolean isStackInvalid(net.minecraft.item.ItemStack s){return s==null||s.stackSize<=0;}public static net.minecraft.item.ItemStack copyAmountUnsafe(int n,net.minecraft.item.ItemStack s){net.minecraft.item.ItemStack c=s.copy();c.stackSize=n;return c;}public static net.minecraft.item.ItemStack copyOrNull(net.minecraft.item.ItemStack s){return s.copy();}public static int stackToInt(net.minecraft.item.ItemStack s){stacks.put(1,s);return 1;}public static net.minecraft.item.ItemStack intToStack(int i){return stacks.get(i);}public static class ItemId{public static ItemId createNoCopy(net.minecraft.item.ItemStack s){return new ItemId();}}}''',
'gregtech.api.util.GTModHandler': 'public class GTModHandler {public static net.minecraftforge.fluids.FluidStack getDistilledWater(int n){return null;}}',
'gregtech.api.util.GTRecipe': 'public class GTRecipe {public static int defaultChance=10000;public int chance=defaultChance;public net.minecraft.item.ItemStack[] mOutputs;public net.minecraftforge.fluids.FluidStack fluid;public net.minecraftforge.fluids.FluidStack getRepresentativeFluidInput(int n){return fluid;}public net.minecraft.item.ItemStack getOutput(int n){return mOutputs[n];}public int getOutputChance(int n){return chance;}}',
'gregtech.api.recipe.RecipeMap': 'public class RecipeMap<T> {public gregtech.api.util.GTRecipe recipe;public RecipeMap<T> findRecipeQuery(){return this;}public RecipeMap<T> items(net.minecraft.item.ItemStack s){return this;}public RecipeMap<T> fluids(net.minecraftforge.fluids.FluidStack s){return this;}public gregtech.api.util.GTRecipe find(){return recipe;}}',
'gregtech.api.enums.Materials': 'public class Materials {public static final Materials Water=new Materials(net.minecraftforge.fluids.FluidRegistry.WATER),Lubricant=new Materials(new net.minecraftforge.fluids.Fluid("lube"));public net.minecraftforge.fluids.Fluid mFluid;public Materials(net.minecraftforge.fluids.Fluid f){mFluid=f;}public net.minecraftforge.fluids.FluidStack getFluid(int n){return new net.minecraftforge.fluids.FluidStack(mFluid,n);}}',
'com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess': '''public class GTSRHatchFluidAccess {public static int steamPaid=Integer.MAX_VALUE,lubePaid=Integer.MAX_VALUE,waterPaid=Integer.MAX_VALUE;public static int drains,waterStock=Integer.MAX_VALUE,waterDrained;public static boolean hasEnoughAcross(java.util.List<?> h,net.minecraftforge.fluids.FluidStack s){return !s.getFluid().getName().equals("water")||s.amount<=waterStock;}public static int depleteFluidAcross(java.util.List<?> h,net.minecraftforge.fluids.FluidStack s){drains++;int cap=s.getFluid().getName().equals("steam")?steamPaid:s.getFluid().getName().equals("lube")?lubePaid:waterPaid;int n=Math.min(cap,s.amount);if(s.getFluid().getName().equals("water")){waterDrained+=n;waterStock-=n;}return n;}}''',
'com.miaokatze.gtsr.common.util.GTSROutputBusCompat': '''public class GTSROutputBusCompat {public static boolean storePartial(gregtech.api.metatileentity.implementations.MTEHatchOutputBus b,net.minecraft.item.ItemStack s,boolean sim){int accepted=Math.min(s.stackSize,b.capacity-(b.stack==null?0:b.stack.stackSize));if(!sim&&accepted>0){if(b.stack==null){b.stack=s.copy();b.stack.stackSize=accepted;}else b.stack.stackSize+=accepted;}s.stackSize-=accepted;return s.stackSize<=0;}}''',
cluster+'.ChainLink': 'public enum ChainLink {CRUSH,HAMMER,ORE_WASH,CHEM_BATH,SIMPLE_WASH,CENTRIFUGE,THERMOCENTRIFUGE,SIFTER,MAGNETIC_SEPARATOR,FURNACE,FURNACE_PRIMARY;public static gregtech.api.recipe.RecipeMap<?> map;public gregtech.api.recipe.RecipeMap<?> getRecipeMap(){return map;}}',
cluster+'.LogisticsChain': 'public class LogisticsChain {public ChainLink link=ChainLink.CRUSH;public boolean isEmpty(){return false;}public boolean isExecutable(ClusterTopology t){return true;}public java.util.List<ChainLink> getLinks(){return java.util.Collections.singletonList(link);}public int getPeakIndex(){return 0;}}',
cluster+'.ClusterItemForms': 'public class ClusterItemForms {public enum OreForm{OTHER,ORE,CRUSHED,CRUSHED_PURIFIED,CRUSHED_CENTRIFUGED,DUST_IMPURE,DUST_PURE,DUST,INGOT}public static OreForm classify(net.minecraft.item.ItemStack s){return OreForm.CRUSHED;}}',
cluster+'.BoosterState': 'public class BoosterState {private java.util.List<MTEBasicAmplifierUnit> units;public static BoosterState aggregate(java.util.List<MTEBasicAmplifierUnit> u){BoosterState b=new BoosterState();b.units=u.stream().filter(x->x.precheckOk).toList();return b;}public double getPrimaryBonus(){return 0;}public double getSecondaryBonus(){return 0;}public java.util.List<MTEBasicAmplifierUnit> getActiveUnits(){return units;}}',
cluster+'.MTEBasicAmplifierUnit': 'public class MTEBasicAmplifierUnit {public boolean paid=true,precheckOk=true;public int prechecked,charged,maxRounds=Integer.MAX_VALUE,parallelBonus;public Object getCluster(){return this;}public boolean isTierValidForConnection(){return true;}public boolean isFluidAvailable(){return true;}public boolean canPayForBatch(int n){prechecked=n;return n<=maxRounds;}public void markBatchPrecheckResult(boolean b){precheckOk=b;}public boolean tryConsumeForBatch(int n){charged=n;return paid;}}',
cluster+'.ClusterTopology': 'public class ClusterTopology {public java.util.List<MTEBasicAmplifierUnit> boosters=new java.util.ArrayList<>();public java.util.List<MTEBasicAmplifierUnit> getBoosterUnits(){return boosters;}public java.util.List<MTEBasicLogisticsUnit> getLogisticsUnits(){return java.util.Collections.emptyList();}public int getExtensionCount(){return 0;}}',
cluster+'.ExecutionPlan': 'public class ExecutionPlan {public static int parallel=1;public static double seconds=1;public static int effectiveParallel(int t,BoosterState b){return parallel+b.getActiveUnits().stream().mapToInt(x->x.parallelBonus).sum();}public static double itemTimeSec(java.util.List<ChainLink> l,int t,ClusterTopology top,BoosterState b){return seconds;}}',
cluster+'.ClusterBatchHost': 'public interface ClusterBatchHost {double heatFraction();void addRealBatchThroughput(int n);}',
cluster+'.ClusterPreheatController': 'public class ClusterPreheatController {public boolean isReady(){return true;}public double getProgress(){return 1;}}',
cluster+'.ClusterParams': '''public class ClusterParams {public static final int BATCH_FAIL_BACKOFF_TICKS=20,SIMPLE_WASH_RECIPE_PROBE_MB=1000,TIER_COUNT=4,PREHEAT_SECONDS=30;public static final double CRUSH_BYPRODUCT_MULT_HIGH_TIER=1,CRUSH_BYPRODUCT_MULT_STEEL=.5,CRUSH_BYPRODUCT_MULT_NORMAL=.1;public static final int[] CLUSTER_LUBRICANT_LPS={10,10,10,10},LOGISTICS_UNIT_LUBRICANT_LPS={10,10,10,10};public enum ClusterTier{BASE;public static ClusterTier get(int i){return BASE;}}public enum SteamGrade{DENSE_SUPERCRITICAL_STEAM,DENSE_SUPERHEATED_STEAM,DENSE_STEAM,SUPERCRITICAL_STEAM,SUPERHEATED_STEAM,STEAM;private static final net.minecraftforge.fluids.Fluid fluid=new net.minecraftforge.fluids.Fluid("steam");public int getDivisor(){return 1;}public boolean isAcceptedBy(ClusterTier t){return this==STEAM;}public net.minecraftforge.fluids.Fluid resolveFluid(){return fluid;}}}''',
cluster+'.MTESteamMineralLogisticsCluster': '''public class MTESteamMineralLogisticsCluster implements ClusterBatchHost {public boolean stopped;public int throughput;public ClusterTopology topology=new ClusterTopology();public int getStructureTierIndex(){return 0;}public ClusterTopology getTopology(){return topology;}public boolean isMachineEnabled(){return !stopped;}public void addStatInput(net.minecraft.item.ItemStack s,int n){}public void addStatOutput(net.minecraft.item.ItemStack s,int n){}public void addStatBonusEntries(java.util.Map<Long,Long> m){}public void recordBatchLinks(java.util.EnumSet<ChainLink> s){}public void addProcessedOre(int n){}public double heatFraction(){return 1;}public void addRealBatchThroughput(int n){throughput+=n;}public void stopMachine(Object r){stopped=true;}public double runFixedSteamLps(){return 100;}public ClusterPreheatController getPreheat(){return new ClusterPreheatController();}public java.util.List<gregtech.api.metatileentity.implementations.MTEHatch> getClusterFluidInputHatches(){return java.util.Collections.emptyList();}}''',
cluster+'.MTEBasicLogisticsUnit': '''import java.util.*;import net.minecraft.item.ItemStack;import gregtech.api.metatileentity.implementations.*;import gregtech.common.tileentities.machines.MTEHatchInputBusME;public class MTEBasicLogisticsUnit {public List<MTEHatchInputBus> mInputBusses=new ArrayList<>();public List<MTEHatchOutputBus> outs=new ArrayList<>();public List<ItemStack> pending;public boolean aborted,batchMode,queue;public long cooldown;public int started,lastParallel,lastSize;public boolean isBatchModeEnabled(){return batchMode;}public int getMaxBatchSize(){return 128;}public LogisticsChain chain=new LogisticsChain();public LogisticsChain getChain(){return chain;}public boolean isModuleEnabled(){return true;}public boolean isPowerAllowed(){return true;}public long getChainCooldownTicks(){return 0;}public boolean hasPendingOutputs(){return pending!=null;}public boolean beginMEBusProcessingIfDue(){return true;}public void armMeProbeThrottle(){}public boolean isQueueMode(){return queue;}public int getLastEffectivePeak(){return 0;}public void setLastEffectivePeak(int i){}public void setChainCooldownTicks(long t){cooldown=t;}public void markDirty(){}public void setLastBatchFluidSummary(List<String> s){}public void recordBatchSize(int p,int n){lastParallel=p;lastSize=n;}public void onBatchProcessed(int n){started++;}public void stashPendingOutputs(List<ItemStack> s){pending=s;}public List<MTEHatchInputBus> getLogisticsInputBusses(){return mInputBusses;}public List<MTEHatchInput> getLogisticsInputHatches(){return Collections.emptyList();}public List<MTEHatchOutputBus> getLogisticsOutputBusses(){return outs;}public MTESteamMineralLogisticsCluster getCluster(){return null;}public void abortPendingRun(){pending=null;aborted=true;}public boolean isWorkInProgress(){return false;}public int getUnitStructureTier(){return 0;}'''+close+'}',
}
main='''package com.miaokatze.gtsr.common.machine.cluster;
import net.minecraft.item.*;import java.util.*;import gregtech.api.metatileentity.implementations.*;import gregtech.common.tileentities.machines.MTEHatchInputBusME;import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;import net.minecraftforge.fluids.*;
public class PaymentReceiptsTest {
 static int failures;static void check(boolean b,String s){if(!b){failures++;System.err.println("FAIL: "+s);}else System.out.println("PASS: "+s);}
 static MTEBasicLogisticsUnit unit(boolean paid){MTEBasicLogisticsUnit u=new MTEBasicLogisticsUnit();MTEHatchInputBusME b=new MTEHatchInputBusME();b.paid=paid;b.stack=new ItemStack(new Item(),1);u.mInputBusses.add(b);u.outs.add(new MTEHatchOutputBus());return u;}
 static void execute(boolean paid,boolean boosterPaid,boolean wash){GTSRHatchFluidAccess.drains=0;MTESteamMineralLogisticsCluster c=new MTESteamMineralLogisticsCluster();MTEBasicLogisticsUnit u=unit(paid);if(!boosterPaid){MTEBasicAmplifierUnit b=new MTEBasicAmplifierUnit();b.paid=false;c.topology.boosters.add(b);}if(wash){u.chain.link=ChainLink.ORE_WASH;gregtech.api.recipe.RecipeMap<Object> map=new gregtech.api.recipe.RecipeMap<>();gregtech.api.util.GTRecipe r=new gregtech.api.util.GTRecipe();r.fluid=new FluidStack(FluidRegistry.WATER,100);r.mOutputs=new ItemStack[]{new ItemStack(new Item(),1)};map.recipe=r;ChainLink.map=map;}else ChainLink.map=null;
 int result=ClusterChainExecutor.executeBatch(c,u,c);boolean expect=paid&&boosterPaid&&!wash;check((result==1)==expect,"batch result "+paid+"/"+boosterPaid+"/"+wash);check((u.pending!=null)==expect,"pending products require actual payment "+paid+"/"+boosterPaid+"/"+wash);check((u.started==1)==expect,"virtual recipe not restarted after failure "+paid+"/"+boosterPaid+"/"+wash);check((c.throughput==1)==expect,"throughput only after full payment "+paid+"/"+boosterPaid+"/"+wash);if(!paid)check(GTSRHatchFluidAccess.drains==0,"failed ME extraction does not charge fluids"); }
 static void rounds(){ExecutionPlan.parallel=64;MTESteamMineralLogisticsCluster c=new MTESteamMineralLogisticsCluster();MTEBasicLogisticsUnit u=unit(true);u.mInputBusses.get(0).stack.stackSize=64;MTEBasicAmplifierUnit booster=new MTEBasicAmplifierUnit();c.topology.boosters.add(booster);check(ClusterChainExecutor.executeBatch(c,u,c)==64,"one parallel round processes 64 inputs");check(booster.prechecked==1&&booster.charged==1,"64 parallel inputs charge one amplifier base amount");ExecutionPlan.parallel=1;}
 static void batchCase(String name,int inputs,int ticks,int capacity,int roundsAvailable,int water,int expected,int duration,int charged,int width){
  ExecutionPlan.parallel=64;ExecutionPlan.seconds=ticks/20D;
  GTSRHatchFluidAccess.waterStock=water<0?Integer.MAX_VALUE:water;GTSRHatchFluidAccess.waterPaid=Integer.MAX_VALUE;GTSRHatchFluidAccess.waterDrained=0;
  MTESteamMineralLogisticsCluster c=new MTESteamMineralLogisticsCluster();MTEBasicLogisticsUnit u=unit(true);u.batchMode=true;u.mInputBusses.get(0).stack.stackSize=inputs;u.outs.get(0).capacity=capacity;
  MTEBasicAmplifierUnit booster=new MTEBasicAmplifierUnit();booster.maxRounds=roundsAvailable;c.topology.boosters.add(booster);ChainLink.map=null;
  if(water>=0){u.chain.link=ChainLink.ORE_WASH;gregtech.api.recipe.RecipeMap<Object> map=new gregtech.api.recipe.RecipeMap<>();gregtech.api.util.GTRecipe recipe=new gregtech.api.util.GTRecipe();recipe.fluid=new FluidStack(FluidRegistry.WATER,100);recipe.mOutputs=new ItemStack[]{u.mInputBusses.get(0).stack.copy()};recipe.mOutputs[0].stackSize=1;map.recipe=recipe;ChainLink.map=map;}
  int result=ClusterChainExecutor.executeBatch(c,u,c);
  check(result==expected,name+" processed count");check(booster.charged==charged,name+" billed rounds");check(u.lastParallel==width,name+" actual parallel width");
  check(u.mInputBusses.get(0).stack.stackSize==inputs-expected,name+" input conservation");
  check(u.outs.get(0).stack==null,name+" output reservation rolls back before commit");
  check((u.pending==null?0:u.pending.stream().mapToInt(s->s.stackSize).sum())==expected,name+" output conservation");
  if(expected>0)check(u.cooldown==duration,name+" duration");
  if(water>=0)check(GTSRHatchFluidAccess.waterDrained==100*expected,name+" actual recipe fluid receipt");
  if(expected>0)check(ClusterChainExecutor.executeBatch(c,u,c)==0,name+" in-flight batch cannot replay");
  check(((MTEHatchInputBusME)u.mInputBusses.get(0)).closes==1,name+" ME input window closes exactly once");
 }
 static void batch(){
  batchCase("ten rounds",640,10,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,640,100,10,64);
  batchCase("fractional tail",96,10,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,96,15,2,64);
  batchCase("partial first round",32,10,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,32,10,1,32);
  batchCase("128 tick limit",1000,10,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,819,127,13,64);
  batchCase("long recipe",1000,128,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,64,128,1,64);
  batchCase("128 multiplier limit",10000,1,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,8192,128,128,64);
  batchCase("amplifier stock limit",640,10,Integer.MAX_VALUE,3,-1,192,30,3,64);
  batchCase("output capacity tail",640,10,160,Integer.MAX_VALUE,-1,160,25,3,64);
  batchCase("output limits first round",640,10,32,Integer.MAX_VALUE,-1,32,10,1,32);
  batchCase("output blocked",640,10,0,Integer.MAX_VALUE,-1,0,0,0,0);
  batchCase("recipe fluid tail",640,10,Integer.MAX_VALUE,Integer.MAX_VALUE,9600,96,15,2,64);
  batchCase("empty input",0,10,Integer.MAX_VALUE,Integer.MAX_VALUE,-1,0,0,0,0);
  gregtech.api.util.GTRecipe.defaultChance=5000;gregtech.api.objects.XSTR.fixed=true;gregtech.api.objects.XSTR.gaussian=0;gregtech.api.objects.XSTR.draws=0;
  batchCase("probability output reservation",640,10,160,Integer.MAX_VALUE,64000,160,25,3,64);
  check(gregtech.api.objects.XSTR.draws==160,"capacity planning uses no random draws; only selected inputs roll once");
  gregtech.api.objects.XSTR.gaussian=3;gregtech.api.objects.XSTR.draws=0;
  batchCase("legacy gaussian outlier rejects once",640,10,160,Integer.MAX_VALUE,64000,0,0,0,0);
  check(gregtech.api.objects.XSTR.draws==160,"final output rejection does not reroll a smaller batch");
  gregtech.api.util.GTRecipe.defaultChance=10000;gregtech.api.objects.XSTR.fixed=false;
  ExecutionPlan.parallel=1;ExecutionPlan.seconds=1;GTSRHatchFluidAccess.waterStock=Integer.MAX_VALUE;ChainLink.map=null;
 }
 public static void main(String[] args){if(args.length>0&&(args[0].equals("rounds")||args[0].equals("batch"))){if(args[0].equals("rounds"))rounds();else batch();if(failures>0)throw new AssertionError(failures+" round failures");return;}execute(true,true,false);execute(false,true,false);execute(true,false,false);GTSRHatchFluidAccess.waterPaid=35;execute(true,true,true);MTEBasicLogisticsUnit u=unit(false);MTEHatchInputBusME second=new MTEHatchInputBusME();u.mInputBusses.add(second);u.endMEBusProcessing(new MTESteamMineralLogisticsCluster());check(second.closes==1,"all ME windows close after an earlier failure");
 for(int which=0;which<3;which++){GTSRHatchFluidAccess.steamPaid=which==1?50:Integer.MAX_VALUE;GTSRHatchFluidAccess.lubePaid=which==2?5:Integer.MAX_VALUE;GTSRHatchFluidAccess.drains=0;ClusterSteamEconomy e=new ClusterSteamEconomy();var result=e.settleRunFull(new MTESteamMineralLogisticsCluster(),100,0);check(result.ok==(which==0),"steam economy actual receipts case "+which);if(which==1)check(GTSRHatchFluidAccess.drains==1,"steam shortage does not charge lubricant");}
 if(args.length>0&&args[0].equals("all")){rounds();batch();}
 if(failures>0)throw new AssertionError(failures+" receipt failures");System.out.println("All payment receipt cases passed");}
}'''
with tempfile.TemporaryDirectory(prefix='gtsr-cluster-payment-') as td:
    td=Path(td); files=[]
    for name,body in fixtures.items():
        package,classname=name.rsplit('.',1); f=td/(classname+'.java'); f.write_text('package '+package+';'+body,encoding='utf-8'); files.append(f)
    f=td/'PaymentReceiptsTest.java';f.write_text(main,encoding='utf-8');files.append(f)
    files.extend(src/n for n in ['ClusterChainExecutor.java','ClusterSteamEconomy.java'])
    classes=td/'classes';classes.mkdir()
    result=subprocess.run([a.javac,'-encoding','UTF-8','-proc:none','-cp',classpath,'-d',str(classes)]+[str(f) for f in files])
    if result.returncode: raise SystemExit(result.returncode)
    result=subprocess.run([str(Path(a.javac).with_name('java')),'-cp',str(classes)+os.pathsep+classpath,cluster+'.PaymentReceiptsTest',a.case])
    raise SystemExit(result.returncode)
