#!/usr/bin/env python3
"""R2 conservation regression. Exact production methods; real Forge fluids/MC item/NBT.

Native GT/AE/world endpoints are controlled fixtures, not a live game/grid. The fixture
parent ticks model GT205's bytecode (unbounded native hatch push, digital-tank push).
The fluid access fixture distinguishes virtual native drain from actual powered debit.
Requires --classpath with Forge/MC, Forge resources, LaunchWrapper, and cached libraries.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

def extract(s, marker):
    start=s.index(marker); brace=s.index('{', start); end=brace+1; depth=1
    while depth:
        depth += (s[end]=='{')-(s[end]=='}'); end+=1
    return s[start:end]

def generate(root):
    machine=root/'src/main/java/com/miaokatze/gtsr/common/machine'
    def source(name): return (machine/(name+'.java')).read_text()
    def methods(name,markers):
        s=source(name)
        return '\n'.join(extract(s,m) for m in markers if m in s)
    chunks=[r'''
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import gregtech.api.metatileentity.implementations.MTEHatch;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
public class MachineConservationRegression {
 static int checks,failures; static Fluid STEAM;
 static void check(boolean ok,String name){checks++;if(!ok){failures++;System.err.println("FAIL: "+name);}else System.out.println("PASS: "+name);}
 static boolean close(double a,double b){return Math.abs(a-b)<1e-7;}
 static class IGregTechTileEntity {
  boolean server=true,allowed=true,failParent; int parentTicks; Receiver target=new Receiver();
  boolean isServerSide(){return server;} boolean isAllowedToWork(){return allowed;}
  ForgeDirection getFrontFacing(){return ForgeDirection.NORTH;} ForgeDirection getBackFacing(){return ForgeDirection.SOUTH;}
  IFluidHandler getITankContainerAtSide(ForgeDirection d){return target;}
  void issueTileUpdate(){} void markDirty(){}
 }
 static class Receiver implements IFluidHandler {
  int actual=Integer.MAX_VALUE,received,calls; boolean negative,mutate,fail;
  public int fill(ForgeDirection d,FluidStack f,boolean execute){
   if(!execute)return f.amount; calls++; if(fail)throw new IllegalStateException("no side effect fill failure"); if(negative)return -1;
   int n=Math.min(f.amount,actual);received+=n;if(mutate)f.amount=0;return n;
  }
  public FluidStack drain(ForgeDirection d,FluidStack f,boolean b){return null;}
  public FluidStack drain(ForgeDirection d,int n,boolean b){return null;}
  public boolean canFill(ForgeDirection d,Fluid f){return true;} public boolean canDrain(ForgeDirection d,Fluid f){return false;}
  public FluidTankInfo[] getTankInfo(ForgeDirection d){return new FluidTankInfo[0];}
 }
 static class LocalTank {
  FluidStack mFluid; boolean mOutputFluid=true;int notifications,lastNotifiedStock;void detectInventoryChange(){notifications++;lastNotifiedStock=stock();}
  FluidStack getDrainableStack(){return mFluid;}
  FluidStack drain(int n,boolean execute){if(mFluid==null||n<=0)return null;FluidStack result=mFluid.copy();result.amount=Math.min(n,mFluid.amount);if(execute){mFluid.amount-=result.amount;if(mFluid.amount<=0)mFluid=null;}return result;}
  int stock(){return mFluid==null?0:mFluid.amount;}
  void setStock(int n){mFluid=new FluidStack(STEAM,n);}
  void unsafePush(IGregTechTileEntity b,int max){FluidStack sim=drain(max,false);if(sim==null)return;int n=b.target.fill(b.getBackFacing(),sim,false);if(n>0)b.target.fill(b.getBackFacing(),drain(n,true),true);}
  void onPostTick(IGregTechTileEntity b,long tick){}
 }
 static class NativeOutputParent extends LocalTank {
  public void onPostTick(IGregTechTileEntity b,long tick){b.parentTicks++;if(b.failParent)throw new IllegalStateException("parent fixture");if(b.server&&b.allowed&&mFluid!=null)unsafePush(b,Integer.MAX_VALUE);if(b.server)detectInventoryChange();}
 }
 static class DigitalParent extends LocalTank {
  public void onPostTick(IGregTechTileEntity b,long tick){b.parentTicks++;if(b.failParent)throw new IllegalStateException("parent fixture");if(b.server&&mOutputFluid&&mFluid!=null&&tick%20==0)unsafePush(b,2000);}
 }
 static class MTEFilteredCacheNode extends DigitalParent {
  boolean mBound,mRegistered,mIsOutputMode=true;long mNextRegistrationTick;String mMemoryFluidName="",mLastSyncKey;
  boolean registerWithHub(IGregTechTileEntity b){return true;} int getBaseHubTransferRate(){return 400;}
  String getStoredFluidName(){return mFluid==null?"":mFluid.getFluid().getName();}
  String getDisplayedWindowFluidName(){return getStoredFluidName();}
''',methods('base/MTEFilteredCacheNode',['public final void onPostTick']),'}']
    classes=[('MTESteamOutputHatch','NativeOutputParent','OUTPUT_PER_TICK',6400),('MTESteamOutputHatchGeneric','NativeOutputParent','OUTPUT_PER_TICK',800),('MTESteamCoolingHatch','NativeOutputParent','OUTPUT_PER_TICK',3200),('MTEPressureSteamOutputHatch','MTESteamOutputHatch','PRESSURE_OUTPUT_PER_TICK',51200),('MTEPressureSteamCoolingHatch','MTESteamCoolingHatch','OUTPUT_PER_TICK',51200)]
    for name,parent,constant,value in classes:
        chunks += [f'static class {name} extends {parent} {{ static final int {constant}={value};',methods('base/'+name,['protected int getOutputPerTick','public void onPostTick']),'}']
    chunks += [r'''
 static class GTUtility {
  static <T> List<T> validMTEList(List<T> l){return l;}
  static boolean isStackInvalid(ItemStack s){return s==null||s.stackSize<=0;}
  static ItemStack copyAmountUnsafe(int n,ItemStack s){ItemStack c=s.copy();c.stackSize=n;return c;}
  static ItemStack copyOrNull(ItemStack s){return s==null?null:s.copy();}
  static int stackToInt(ItemStack s){return System.identityHashCode(s.getItem())+s.getMetadata();}
  static class ItemId {static ItemId createNoCopy(ItemStack s){return new ItemId();}}
 }
 static class MTEHatchOutputBus {
  ItemStack[] slots=new ItemStack[2]; int getSizeInventory(){return slots.length;}
  ItemStack getStackInSlot(int i){return slots[i];} void setInventorySlotContents(int i,ItemStack s){slots[i]=s;}
 }
 interface IOutputTransaction {interface IRecipeCheckAware {void setRecipeCheck(boolean b);}}
 interface IOutputBusTransaction {void storePartial(GTUtility.ItemId id,ItemStack s,long a,long b);void commit();}
 static class MTEHatchOutputBusME extends MTEHatchOutputBus {IOutputBusTransaction createTransaction(){throw new AssertionError("ME not used by this rollback fixture");}}
 static class MTEBasicLogisticsUnit {List<MTEHatchOutputBus> buses=new ArrayList<>();List<MTEHatchOutputBus> getLogisticsOutputBusses(){return buses;}}
 static class GTSROutputBusCompat {
  static boolean storePartial(MTEHatchOutputBus b,ItemStack offered,boolean simulate){
   for(int i=0;i<b.slots.length&&offered.stackSize>0;i++){
    ItemStack current=b.slots[i];
    if(current!=null&&(!current.isItemEqual(offered)||!ItemStack.areItemStackTagsEqual(current,offered)))continue;
    int accepted=Math.min(offered.stackSize,current==null?64:64-current.stackSize);
    if(!simulate&&accepted>0){if(current==null)b.slots[i]=GTUtility.copyAmountUnsafe(accepted,offered);else current.stackSize+=accepted;}
    offered.stackSize-=accepted;
   }return offered.stackSize==0;
  }
 }
 static class Executor {
''',methods('cluster/ClusterChainExecutor',['private static final class OutputLedger','private static List<OutputLedger> tryEmitOutputs','private static void rollbackOutputs']),r'''
 }
 static class CheckRecipeResult {final boolean success;CheckRecipeResult(boolean b){success=b;}}
 static class CheckRecipeResultRegistry {static final CheckRecipeResult NO_RECIPE=new CheckRecipeResult(false),SUCCESSFUL=new CheckRecipeResult(true),FLUID_OUTPUT_FULL=new CheckRecipeResult(false);}
 static class DenseBase {
  static final String[] NORMAL_FLUID_NAMES={"steam","ic2superheatedsteam","supercriticalsteam"},DENSE_FLUID_NAMES={"densesteam","densesuperheatedsteam","densesupercriticalsteam"};
  static final double[] GRADE_COEF={.5,1,2};
  List<MTEHatch> hatches=new ArrayList<>();int grade=1;int mSingularityMode=2,mAccumGrade=-1,cycles;double mAccum;
  List<MTEHatch> getSteamInputHatches(){return hatches;} boolean isDenseSteamOnly(){return false;}
  int findHighestGrade(boolean b){return grade;} boolean probeGrade(int g,boolean b,boolean dense){return g==grade;}
  void startCycle(){cycles++;} boolean enterSingularityModeIfPossible(){return true;} int fillOutput(FluidStack s){return 0;}
''',methods('base/MTESingularityMachineBase',['private FluidStack[] gradeProbeStacks','protected final long sumGrade','protected final void drainGrade','protected final long drainGradePaid']),r'''
 }
 static class Dense extends DenseBase {static final long DENSE_COMPRESSION_RATIO=1000;
''',methods('MTEDenseStateManipulator',['private CheckRecipeResult processCompressionCycle','private CheckRecipeResult processDecompressionCycle','private double getSingularityOutputCoefficient','private double getGradeRatio','private int findHighestDenseGrade','private long sumDenseGrade','private void drainDenseGrade','private long drainDenseGrade']),r'''
 }
 static class VoidProtectionHelper {VoidProtectionHelper setMachine(Object o){return this;}VoidProtectionHelper setFluidOutputs(FluidStack[] s){return this;}VoidProtectionHelper build(){return this;}boolean isFluidFull(){return false;}}
 interface ICoolingHatchHolder {}
 static class SteamCoolingSupport {static int produced;static void pushCoolingProducts(ICoolingHatchHolder h,int n,boolean b){produced+=n;}}
 static class Drill implements ICoolingHatchHolder {
  int mMaxProgresstime,mEfficiencyIncrease,mEfficiency,consumed;FluidStack[] mOutputFluids;boolean debit=true;
  static final int PROGRESSION_TIME_TICKS=20;int calculateFinalWaterOutput(){return 100;}FluidStack[] getOutputFluid(int n){return new FluidStack[]{new FluidStack(STEAM,n)};}
  int getTotalSteamStored(){return 1000;}int getSteamPerSecond(){return 100;}int getEfficiencyIncrease(){return 10000;}
  boolean hasSuperheatedSteamInHatch(){return false;}boolean tryConsumeSteam(int n){if(debit)consumed+=n;return debit;}void updateSlots(){}
''',methods('MTESteamFluidDrill',['public CheckRecipeResult checkProcessing']),r'''
 }
 static void outputs(){
  String[] names={"steam","generic","cooling","pressure steam","pressure cooling","cache"};int[] limits={6400,800,3200,51200,51200,400};
  for(int kind=0;kind<names.length;kind++){
   for(int receipt:new int[]{37,0,Integer.MAX_VALUE}){
    LocalTank t=tank(kind);t.setStock(100000);IGregTechTileEntity b=new IGregTechTileEntity();b.target.actual=receipt;
    t.onPostTick(b,20);int expected=Math.min(receipt,limits[kind]);
    check(t.stock()==100000-expected&&b.target.received==expected,names[kind]+" actual receipt "+receipt);
    check(kind==5?b.parentTicks==1:t.notifications==1,names[kind]+" preserves native tick obligations");
    if(kind<5)check(t.lastNotifiedStock==100000-expected,names[kind]+" watchers see actual tank");
   }
   LocalTank t=tank(kind);t.setStock(100000);IGregTechTileEntity b=new IGregTechTileEntity();b.server=false;t.onPostTick(b,20);check(t.stock()==100000&&b.target.received==0,names[kind]+" client does not transfer");
   t=tank(kind);t.setStock(100000);b=new IGregTechTileEntity();b.target.fail=true;try{t.onPostTick(b,20);}catch(IllegalStateException expected){}
   check(t.stock()==100000&&t.mOutputFluid,names[kind]+" known no-side-effect fill failure preserves source");
   t=tank(kind);t.setStock(100000);b=new IGregTechTileEntity();b.target.actual=37;b.target.mutate=true;t.onPostTick(b,20);check(t.stock()==99963,names[kind]+" receiver cannot mutate offered accounting");
   if(kind<5){t=tank(kind);t.setStock(100000);b=new IGregTechTileEntity();b.allowed=false;t.onPostTick(b,20);check(t.stock()==100000&&b.target.received==0,names[kind]+" disabled hatch does not transfer");}
  }
  LocalTank t=tank(5);t.setStock(100000);t.mOutputFluid=false;IGregTechTileEntity b=new IGregTechTileEntity();t.onPostTick(b,20);check(t.stock()==100000,"disabled cache auto-output stays disabled");
 }
 static LocalTank tank(int n){switch(n){case 0:return new MTESteamOutputHatch();case 1:return new MTESteamOutputHatchGeneric();case 2:return new MTESteamCoolingHatch();case 3:return new MTEPressureSteamOutputHatch();case 4:return new MTEPressureSteamCoolingHatch();default:return new MTEFilteredCacheNode();}}
 static void rollback(){
  Item item=new Item(),other=new Item();
  for(boolean full:new boolean[]{false,true}){
   MTEBasicLogisticsUnit u=new MTEBasicLogisticsUnit();MTEHatchOutputBus b=new MTEHatchOutputBus();u.buses.add(b);
   ItemStack tagged=new ItemStack(item,7);NBTTagCompound tag=new NBTTagCompound();tag.setString("owner","original");tagged.setTagCompound(tag);b.slots[0]=tagged;
   List<ItemStack> out=new ArrayList<>();out.add(new ItemStack(item,2));if(!full)out.add(new ItemStack(other,1));
   List<Executor.OutputLedger> ledger=Executor.tryEmitOutputs(u,out,false);if(ledger!=null)Executor.rollbackOutputs(ledger);
   check(b.slots[0]!=null&&b.slots[0].stackSize==7&&"original".equals(b.slots[0].getTagCompound().getString("owner")),"rollback preserves preexisting tagged stack "+full);
   check(b.slots[1]==null,"rollback removes only inserted untagged stack "+full);
   check((ledger!=null)==full,"rollback capacity control "+full);
  }
 }
 static Dense dense(boolean dense,int amount,int actual,boolean shared){
  Dense d=new Dense();int[] bank={amount};Fluid fluid=FluidRegistry.getFluid(dense?"densesuperheatedsteam":"ic2superheatedsteam");
  d.hatches.add(new MTEHatch(bank,fluid,actual));if(shared)d.hatches.add(new MTEHatch(bank,fluid,actual));return d;
 }
 static void dense(){
  for(boolean decompress:new boolean[]{false,true}){
   for(int actual:new int[]{0,50,Integer.MAX_VALUE}){
    Dense d=dense(decompress,200,actual,false);CheckRecipeResult result=decompress?d.processDecompressionCycle():d.processCompressionCycle();
    int paid=Math.min(200,actual);double expected=decompress?paid*1000d:paid/1000d;
    check(close(d.mAccum,expected)&&d.hatches.get(0).bank[0]==200-paid,"dense actual powered debit "+decompress+"/"+actual);
    check(result.success==(paid>0),"dense zero payment does not start "+decompress+"/"+actual);
   }
   Dense d=dense(decompress,200,Integer.MAX_VALUE,true);if(decompress)d.processDecompressionCycle();else d.processCompressionCycle();
   check(close(d.mAccum,decompress?200000d:.2d)&&d.hatches.get(0).bank[0]==0,"shared ME network charged once "+decompress);
   d=dense(decompress,1,Integer.MAX_VALUE,false);d.mAccum=.8;d.mAccumGrade=0;if(decompress)d.processDecompressionCycle();else d.processCompressionCycle();
   check(close(d.mAccum,decompress?1000.4:.401),"grade carry conserves energy "+decompress);
  }
 }
 static void drill(){
  for(boolean paid:new boolean[]{false,true}){Drill d=new Drill();d.debit=paid;SteamCoolingSupport.produced=0;CheckRecipeResult result=d.checkProcessing();
   check(result.success==paid,"drill return follows debit "+paid);
   check((d.mOutputFluids!=null)==paid&&((d.mMaxProgresstime>0)==paid),"drill recipe products require payment "+paid);
   check(SteamCoolingSupport.produced==(paid?100:0),"drill cooling requires payment "+paid);
  }
 }
 public static void main(String[] args) throws Exception {
  if(!nativeBootstrap(args))return;
  outputs();rollback();dense();drill();
  System.out.println("R2 machine conservation: "+checks+" checks; failures="+failures+"; real MC/Forge storage, controlled GT/AE/world fixtures");
  if(failures>0)throw new AssertionError(failures+" machine conservation failures");
 }
''']
    # Reuse the proven FML bootstrap source, not gameplay logic.
    helper=(ROOT/'tools/tests/test_hub_transfer_contracts.py').read_text()
    start=helper.index('    static boolean nativeBootstrap(');end=helper.index("    '''",start)
    bootstrap=helper[start:end].replace('HubTransferContract','MachineConservationRegression')
    bootstrap=bootstrap.replace('return true;', '''for(String name:new String[]{"ic2superheatedsteam","supercriticalsteam","densesteam","densesuperheatedsteam","densesupercriticalsteam"})FluidRegistry.registerFluid(new Fluid(name));
        return true;''')
    return '\n'.join(chunks)+bootstrap+'\n}'

HATCH=r'''package gregtech.api.metatileentity.implementations;
import net.minecraftforge.fluids.*;import net.minecraftforge.common.util.ForgeDirection;
public class MTEHatch {
 public final int[] bank;public final Fluid fluid;public final int actual;
 public MTEHatch(int[] b,Fluid f,int a){bank=b;fluid=f;actual=a;}
 public FluidTankInfo[] getTankInfo(ForgeDirection d){return new FluidTankInfo[]{new FluidTankInfo(new FluidStack(fluid,bank[0]),Integer.MAX_VALUE)};}
 // GT205 recipe-window drain returns a virtual reservation, not a powered extraction receipt.
 public FluidStack drain(ForgeDirection d,FluidStack request,boolean execute){return new FluidStack(fluid,Math.min(bank[0],request.amount));}
}'''
ACCESS=r'''package com.miaokatze.gtsr.api.compat;
import gregtech.api.metatileentity.implementations.MTEHatch;import net.minecraftforge.fluids.FluidStack;
public class GTSRHatchFluidAccess {public static int drainFluidExact(MTEHatch h,FluidStack request){if(h.fluid!=request.getFluid())return 0;int n=Math.min(request.amount,Math.min(h.bank[0],h.actual));h.bank[0]-=n;return n;}}'''

def main():
    p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--classpath',required=True);p.add_argument('--javac',required=True);a=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='gtsr-machine-r2-') as name:
        work=Path(name);files=[]
        for file,content in [('MachineConservationRegression.java',generate(a.source_root)),('MTEHatch.java',HATCH),('GTSRHatchFluidAccess.java',ACCESS)]:
            path=work/file;path.write_text(content);files.append(str(path))
        subprocess.run([a.javac,'--release','17','-proc:none','-cp',a.classpath,'-d',str(work),*files],check=True)
        subprocess.run([str(Path(a.javac).with_name('java')),'-cp',str(work)+os.pathsep+a.classpath,'MachineConservationRegression'],check=True)
if __name__=='__main__':main()
