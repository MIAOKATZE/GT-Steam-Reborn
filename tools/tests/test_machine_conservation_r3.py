#!/usr/bin/env python3
"""R3 exact-method behavior harness. Real MC ItemStack/NBT; GT, AE, world boundaries are explicit fixtures."""
import argparse, os, subprocess, tempfile, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def extract(s, marker):
 a=s.index(marker);b=s.index('{',a);i=b+1;depth=1
 while depth: depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
def generate(root):
 m=root/'src/main/java/com/miaokatze/gtsr/common/machine'
 def methods(name, markers):
  s=(m/(name+'.java')).read_text();return '\n'.join(extract(s,k) for k in markers if k in s)
 chunks=[r'''
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;
public class MachineConservationR3 {
 static int checks,failures;static Fluid STEAM;static Item FUEL,ORE;
 static void check(boolean ok,String name){checks++;if(!ok){failures++;System.err.println("FAIL: "+name);}else System.out.println("PASS: "+name);}
 static class Pair<A,B>{A a;B b;static <A,B> Pair<A,B> of(A a,B b){Pair<A,B> p=new Pair<>();p.a=a;p.b=b;return p;}A getLeft(){return a;}B getRight(){return b;}}
 static class GTUtility{
  static <T> List<T> validMTEList(List<T> x){return x;}static boolean isStackInvalid(ItemStack s){return s==null||s.stackSize<=0;}
  static boolean areUnificationsEqual(ItemStack a,ItemStack b,boolean n){return a!=null&&b!=null&&a.isItemEqual(b)&&ItemStack.areItemStackTagsEqual(a,b);}
  static ItemStack copyOrNull(ItemStack s){return s==null?null:s.copy();}
  static class ItemId {ItemStack s;ItemId(ItemStack s){this.s=s;}ItemStack getItemStack(){return s.copy();}}
 }
 interface IActionHost{}
 static class MachineSource{MachineSource(IActionHost h){}}
 static class IGregTechTileEntity implements IActionHost{boolean client,allowed=true;int dirty;boolean isClientSide(){return client;}boolean isAllowedToWork(){return allowed;}void markDirty(){dirty++;}}
 static class GridAccessException extends Exception{}
 static class IAEItemStack{ItemStack stack;IAEItemStack(ItemStack s){stack=s;}ItemStack getItemStack(){return stack;}}
 static class AEItemStack{static IAEItemStack create(ItemStack s){return new IAEItemStack(s);}}
 static class AENetworkProxy{MTEHatchInputBusME bus;AENetworkProxy(MTEHatchInputBusME b){bus=b;}AENetworkProxy getEnergy()throws GridAccessException{if(bus.offline)throw new GridAccessException();return this;}AENetworkProxy getStorage(){return this;}AENetworkProxy getItemInventory(){return this;}}
 static class Platform{static IAEItemStack poweredExtraction(AENetworkProxy e,AENetworkProxy inventory,IAEItemStack request,MachineSource source){MTEHatchInputBusME b=inventory.bus;int n=Math.min(request.stack.stackSize,Math.min(b.bank,b.actual));b.bank-=n;b.paid+=n;if(n<=0)return null;ItemStack r=request.stack.copy();r.stackSize=n;return new IAEItemStack(r);}}
 static class MTEHatchInputBus{
  ItemStack local;int paid;MTEHatchInputBus(ItemStack s){local=s;}int getSizeInventory(){return 1;}ItemStack getStackInSlot(int n){return local;}
  ItemStack decrStackSize(int slot,int n){ItemStack ref=getStackInSlot(slot);if(ref==null)return null;ItemStack out=ref.copy();out.stackSize=Math.min(n,ref.stackSize);ref.stackSize-=out.stackSize;paid+=out.stackSize;return out;}
  void updateSlots(){}
 }
 static class MTEHatchInputBusME extends MTEHatchInputBus{
  static final int SLOT_COUNT=16;int bank,actual=Integer.MAX_VALUE,closes;boolean offline,allowed=true;ItemStack view,manual,template;IGregTechTileEntity tile=new IGregTechTileEntity();
  MTEHatchInputBusME(ItemStack s){super(null);bank=s.stackSize;template=s.copy();}
  void start(){view=template.copy();view.stackSize=bank;}
  void end(){closes++;if(view!=null){int amount=Math.max(0,templateAmount-view.stackSize);int n=Math.min(bank,Math.min(actual,amount));bank-=n;paid+=n;}}
  int templateAmount;void open(){start();templateAmount=bank;}
  int getSizeInventory(){return 18;}ItemStack getStackInSlot(int n){return n==0?view:n==17?manual:null;}
  ItemStack decrStackSize(int slot,int n){ItemStack ref=getStackInSlot(slot);if(ref==null)return null;ItemStack out=ref.copy();out.stackSize=Math.min(n,ref.stackSize);ref.stackSize-=out.stackSize;return out;}
  boolean isAllowedToWork(){return allowed;}AENetworkProxy getProxy(){return new AENetworkProxy(this);}IGregTechTileEntity getBaseMetaTileEntity(){return tile;}
 }
 static class Parent{void onPostTick(IGregTechTileEntity b,long t){}void saveNBTData(NBTTagCompound n){}void loadNBTData(NBTTagCompound n){}}
 static class Mode extends Parent{
  int mSingularityMode,mSingularityModeTicks,mSingularityCheckCooldown,mMaxProgresstime=1,expired;
  List<MTEHatchInputBus> mInputBusses=new ArrayList<>();IGregTechTileEntity tile=new IGregTechTileEntity();
  IGregTechTileEntity getBaseMetaTileEntity(){return tile;}int getSingularityDurationTicks(){return 4000;}
  boolean canConsumeSingularity(){return tile.allowed&&mMaxProgresstime>0;}
  ItemStack getSingularityItemForMode(int mode){return new ItemStack(FUEL,1,mode);}
  void onSingularityModeExpired(){expired++;}void startRecipeProcessing(){for(MTEHatchInputBus b:mInputBusses)if(b instanceof MTEHatchInputBusME)((MTEHatchInputBusME)b).open();}
  void endRecipeProcessing(){for(MTEHatchInputBus b:mInputBusses)if(b instanceof MTEHatchInputBusME)((MTEHatchInputBusME)b).end();}
''',methods('base/MTESingularityModeMachineBase',['protected void checkSingularityMode','public void onPostTick','protected boolean consumeSingularityFromInputBuses','private ItemStack consumeSingularityStack','public void saveNBTData','public void loadNBTData']),r'''
 }
 static class CheckRecipeResult{final boolean ok;CheckRecipeResult(boolean b){ok=b;}}
 static class CheckRecipeResultRegistry{static final CheckRecipeResult INTERNAL_ERROR=new CheckRecipeResult(false),NO_FUEL_FOUND=new CheckRecipeResult(false),GENERATING=new CheckRecipeResult(true),ITEM_OUTPUT_FULL=new CheckRecipeResult(false),SUCCESSFUL=new CheckRecipeResult(true);}
 static class Gear extends Parent{
  static final long[] STEAM_CONSUMPTION_PER_SEC={0,6400,25600},SUPERHEATED_OUTPUT_PER_SEC={0,1600,6400},DISTILLED_WATER_OUTPUT_PER_SEC={0,30,120};
  int tier=1,mWaterOutputRemainder,mCasingTier,mPipeTier,mGearTier,mFrameTier,mSyncedEffectiveTier,mMaxProgresstime,mEUt;long mSteamConsumedLastTick,mSuperheatedOutputLastTick,mWaterOutputLastTick,water;boolean debit=true;
  int getEffectiveTier(){return tier;}boolean depleteSteam(long n){return debit;}void outputSuperheatedSteam(long n){}void outputDistilledWater(long n){water+=n;}
''',methods('MTEGearSteamCompressor',['public CheckRecipeResult checkProcessing','public void saveNBTData','public void loadNBTData']),r'''
 }
 static class MTEHatchOutputBus{int actual=Integer.MAX_VALUE,total;boolean fits=true;ItemStack last;}
 static class GTSROutputBusCompat{static boolean storePartial(MTEHatchOutputBus b,ItemStack stack,boolean simulate){int n=simulate?(b.fits?stack.stackSize:0):Math.min(stack.stackSize,b.actual);if(!simulate){b.total+=n;b.last=stack.copy();b.last.stackSize=n;}stack.stackSize-=n;return stack.stackSize<=0;}}
 static class SimpleCheckRecipeResult{static CheckRecipeResult ofFailure(String s){return new CheckRecipeResult(false);}}
 static class CMA{
  ItemStack mPendingOreOutput;int dirty;List<MTEHatchOutputBus> mOutputBusses=new ArrayList<>();void markDirty(){dirty++;}
  List<Object> mPool=Arrays.asList(new Object());boolean dropMapValid=true,mDirectionalMode,mActiveDense;int mActiveGrade=0,mSingularityMode,mMaxProgresstime,mEfficiency,mEfficiencyIncrease,rebuilt;
  double mOreAccumulator;String mLastOreName;ItemStack[] mOutputItems;static final ItemStack[] emptyItemStackArray=new ItemStack[0];
  static final double ORES_PER_HEAT_UNIT=10;static final double[] GRADE_COEF={.5,1,2},SINGULARITY_OUTPUT_COEF={1,2,4};static final int DENSE_CYCLE_TICKS=5,NORMAL_CYCLE_TICKS=20;
  void updateSlots(){}void rebuildPoolIfNeeded(){rebuilt++;}boolean hasUsableDimension(){return true;}float getDirectionalWeightSum(){return 1;}int getUUMatterTotal(){return 1;}int findGrade(){return 0;}boolean probeGrade(int a,boolean b,boolean c){return false;}
  GTUtility.ItemId extractNextOre(){return new GTUtility.ItemId(new ItemStack(ORE,1));}boolean outputOre(ItemStack s){ItemStack out=s.copy();out.stackSize=5;return tryOutputOre(out);}
''',methods('MTECrustMatterAggregator',['private boolean tryOutputOre','private boolean flushPendingOreOutput','public CheckRecipeResult checkProcessing','protected void outputAfterRecipe']),r'''
  boolean flush(){
''',('return flushPendingOreOutput();' if 'private boolean flushPendingOreOutput' in (m/'MTECrustMatterAggregator.java').read_text() else 'return true;'),r'''
  }
  void savePending(NBTTagCompound aNBT){
''']
 c=(m/'MTECrustMatterAggregator.java').read_text()
 # Exact new persistence statements; baseline has no pending ownership at all.
 save=extract(c,'public void saveNBTData');load=extract(c,'public void loadNBTData')
 if 'if (mPendingOreOutput != null)' in save:
  a=save.index('        if (mPendingOreOutput != null)');b=save.index('        aNBT.setString("lastDimAbbr"',a);chunks.append(save[a:b])
 chunks += ['}\nvoid loadPending(NBTTagCompound aNBT){']
 if 'NBTTagCompound pending =' in load:
  a=load.index('        NBTTagCompound pending =');b=load.index('        lastDimAbbr =',a);chunks.append(load[a:b])
 chunks += [r'''}
 }
 static class HubParent {boolean nativeCleared;public void clearHatches(){nativeCleared=true;}void onUnload(){clearHatches();}void onRemoval(){clearHatches();}}
 static abstract class Hub extends HubParent {
  abstract void detachHatchControllers();
''',methods('base/MTEHubArrayBase',['public void clearHatches']),r'''
 }
 static class MTESteamHubInputHatch{Object mController;}static class MTESteamHubOutputHatch{Object mController;}
 static class MTEWaterHubInputHatch{Object mController;}static class MTEWaterHubOutputHatch{Object mController;}
 static class SteamHub extends Hub {List<MTESteamHubInputHatch> mSteamInputHatches=new ArrayList<>();List<MTESteamHubOutputHatch> mSteamOutputHatches=new ArrayList<>();
''',methods('MTESteamHubArray',['protected void detachHatchControllers']),r'''
 }
 static class WaterHub extends Hub {List<MTEWaterHubInputHatch> mWaterInputHatches=new ArrayList<>();List<MTEWaterHubOutputHatch> mWaterOutputHatches=new ArrayList<>();
''',methods('MTEWaterHubArray',['protected void detachHatchControllers']),r'''
 }
 static void hubs(){
  for(boolean removal:new boolean[]{false,true}){
   SteamHub s=new SteamHub();MTESteamHubInputHatch si=new MTESteamHubInputHatch();MTESteamHubOutputHatch so=new MTESteamHubOutputHatch();si.mController=s;so.mController=s;s.mSteamInputHatches.add(si);s.mSteamOutputHatches.add(so);if(removal)s.onRemoval();else s.onUnload();check(si.mController==null&&so.mController==null&&s.nativeCleared,"steam native lifecycle detaches custom references "+removal);
   WaterHub w=new WaterHub();MTEWaterHubInputHatch wi=new MTEWaterHubInputHatch();MTEWaterHubOutputHatch wo=new MTEWaterHubOutputHatch();wi.mController=w;wo.mController=w;w.mWaterInputHatches.add(wi);w.mWaterOutputHatches.add(wo);if(removal)w.onRemoval();else w.onUnload();check(wi.mController==null&&wo.mController==null&&w.nativeCleared,"water native lifecycle detaches custom references "+removal);
  }
  SteamHub s=new SteamHub();MTESteamHubInputHatch si=new MTESteamHubInputHatch();MTESteamHubOutputHatch so=new MTESteamHubOutputHatch();Object newOwner=new Object();si.mController=newOwner;so.mController=newOwner;s.mSteamInputHatches.add(si);s.mSteamOutputHatches.add(so);s.detachHatchControllers();check(si.mController==newOwner&&so.mController==newOwner,"steam old controller cannot detach reassigned hatch");
  WaterHub w=new WaterHub();MTEWaterHubInputHatch wi=new MTEWaterHubInputHatch();MTEWaterHubOutputHatch wo=new MTEWaterHubOutputHatch();wi.mController=newOwner;wo.mController=newOwner;w.mWaterInputHatches.add(wi);w.mWaterOutputHatches.add(wo);w.detachHatchControllers();check(wi.mController==newOwner&&wo.mController==newOwner,"water old controller cannot detach reassigned hatch");
 }
 static void modes(){
  for(int paid:new int[]{0,1,Integer.MAX_VALUE}){
   Mode m=new Mode();MTEHatchInputBusME me=new MTEHatchInputBusME(new ItemStack(FUEL,2,2));me.actual=paid;m.mInputBusses.add(me);m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);
   check(m.mSingularityMode==(paid==0?0:2),"ME mode requires actual payment "+paid);
   check(me.bank==(paid==0?2:1)&&me.paid==(paid==0?0:1),"ME exactly one debit including parent close "+paid);
  }
  Mode m=new Mode();MTEHatchInputBusME me=new MTEHatchInputBusME(new ItemStack(ORE,0));me.manual=new ItemStack(FUEL,2,1);m.mInputBusses.add(me);m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);
  check(m.mSingularityMode==1&&me.manual.stackSize==1&&me.bank==0,"native manual fuel is physical, not a network request");
  m=new Mode();m.mInputBusses.add(new MTEHatchInputBus(new ItemStack(FUEL,2,2)));m.checkSingularityMode();check(m.mSingularityMode==2&&m.mInputBusses.get(0).paid==1,"physical bus consumes exactly one");
  m=new Mode();check(!m.consumeSingularityFromInputBuses(-1,new ItemStack(FUEL,1,1)),"negative requested fuel does not succeed");
  for(int mode:new int[]{-1,3,Integer.MAX_VALUE}){m=new Mode();NBTTagCompound n=new NBTTagCompound();n.setInteger("mSingularityMode",mode);n.setInteger("mSingularityModeTicks",Integer.MAX_VALUE);m.loadNBTData(n);check(m.mSingularityMode==0&&m.mSingularityModeTicks==0,"invalid NBT mode safe "+mode);}
  for(int ticks:new int[]{-1,4001,Integer.MAX_VALUE}){m=new Mode();NBTTagCompound n=new NBTTagCompound();n.setInteger("mSingularityMode",2);n.setInteger("mSingularityModeTicks",ticks);m.loadNBTData(n);check(m.mSingularityModeTicks==Math.max(0,Math.min(4000,ticks)),"NBT mode TTL bounded "+ticks);}
  m=new Mode();me=new MTEHatchInputBusME(new ItemStack(FUEL,2,2));me.offline=true;m.mInputBusses.add(me);m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);check(m.mSingularityMode==0&&me.bank==2&&me.closes==1,"offline native extraction grants no mode and still closes reading window");
  m=new Mode();me=new MTEHatchInputBusME(new ItemStack(FUEL,2,2));me.actual=0;m.mInputBusses.add(me);m.mSingularityMode=2;m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);check(m.mSingularityMode==0&&m.expired==1,"failed native renewal expires rather than granting unpaid TTL");
  m=new Mode();me=new MTEHatchInputBusME(new ItemStack(FUEL,2,2));me.allowed=false;m.mInputBusses.add(me);m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);check(m.mSingularityMode==0&&me.bank==2&&me.closes==1,"disabled stocking bus cannot grant mode");
  m=new Mode();me=new MTEHatchInputBusME(new ItemStack(FUEL,2,2)){};m.mInputBusses.add(me);m.mSingularityCheckCooldown=19;m.onPostTick(m.tile,20);check(m.mSingularityMode==0&&me.bank==2&&me.closes==1,"unknown stocking subclass fails closed");
 }
 static void gear(){
  for(int tier:new int[]{1,2}){Gear g=new Gear();g.tier=tier;for(int n=0;n<20;n++)g.checkProcessing();check(g.water==(tier==1?30:120),"gear declared full-second water output tier "+tier);}
  Gear g=new Gear();g.checkProcessing();NBTTagCompound n=new NBTTagCompound();g.saveNBTData(n);Gear loaded=new Gear();loaded.loadNBTData(n);loaded.checkProcessing();check(g.water+loaded.water==3,"half-litre remainder survives reload");
  g=new Gear();g.checkProcessing();g.debit=false;for(int i=0;i<20;i++)g.checkProcessing();g.debit=true;g.checkProcessing();check(g.water==3,"failed steam debit cannot advance fractional water");
  n=new NBTTagCompound();n.setInteger("mWaterOutputRemainder",Integer.MAX_VALUE);g=new Gear();g.loadNBTData(n);g.checkProcessing();check(g.water<=2,"invalid remainder cannot manufacture unbounded water");
  g=new Gear();g.checkProcessing();g.tier=2;g.checkProcessing();g.tier=1;g.checkProcessing();check(g.water==9,"fractional litre retained across tier changes");
 }
 static void outputs(){
  CMA cycle=new CMA();MTEHatchOutputBus sink=new MTEHatchOutputBus();sink.actual=2;cycle.mOutputBusses.add(sink);cycle.outputAfterRecipe();
  check(sink.total==2&&cycle.mPendingOreOutput!=null&&cycle.mPendingOreOutput.stackSize==3&&cycle.mOreAccumulator==4,"CMA production owns one partial group and stops selection");
  sink.actual=0;check(!cycle.checkProcessing().ok&&cycle.rebuilt==0,"CMA real cycle start blocked behind pending output");
  sink.actual=Integer.MAX_VALUE;check(cycle.checkProcessing().ok&&sink.total==5&&cycle.mPendingOreOutput==null,"CMA flush precedes subsequent real cycle start");
  for(int actual:new int[]{0,2,5}){CMA c=new CMA();MTEHatchOutputBus b=new MTEHatchOutputBus();b.actual=actual;c.mOutputBusses.add(b);ItemStack output=new ItemStack(ORE,5);NBTTagCompound identity=new NBTTagCompound();identity.setString("identity","paid");output.setTagCompound(identity);
   check(c.tryOutputOre(output),"CMA claims exactly one group once admitted "+actual);
   check(b.total+(c.mPendingOreOutput==null?0:c.mPendingOreOutput.stackSize)==5,"CMA output plus remainder conserved "+actual);
   check(output.stackSize==5,"CMA caller output template unchanged "+actual);
   b.actual=0;check(c.flush()==(actual==5),"CMA pending prevents fresh production "+actual);
   NBTTagCompound tag=new NBTTagCompound();c.savePending(tag);CMA reload=new CMA();reload.loadPending(tag);reload.mOutputBusses.add(b);b.actual=Integer.MAX_VALUE;check(reload.flush()&&b.total==5,"CMA reload retries remainder without duplicating prefix "+actual);
   check(b.last==null||"paid".equals(b.last.getTagCompound().getString("identity")),"CMA remainder keeps item identity "+actual);
  }
  CMA c=new CMA();MTEHatchOutputBus b=new MTEHatchOutputBus();b.actual=1;c.mOutputBusses.add(b);c.tryOutputOre(new ItemStack(ORE,300));NBTTagCompound tag=new NBTTagCompound();c.savePending(tag);CMA loaded=new CMA();loaded.loadPending(tag);check(loaded.mPendingOreOutput!=null&&loaded.mPendingOreOutput.stackSize==299,"CMA pending quantities survive vanilla byte Count boundary");
  c=new CMA();b=new MTEHatchOutputBus();b.fits=false;c.mOutputBusses.add(b);check(!c.tryOutputOre(new ItemStack(ORE,5))&&c.mPendingOreOutput==null&&b.total==0,"CMA failed preflight does not claim output ownership");
  c=new CMA();b=new MTEHatchOutputBus();b.actual=0;c.mOutputBusses.add(b);c.tryOutputOre(new ItemStack(ORE,5));MTEHatchOutputBus second=new MTEHatchOutputBus();second.actual=2;c.mOutputBusses.add(second);check(!c.flush()&&second.total==2&&c.mPendingOreOutput.stackSize==3,"CMA partial flush retains exact remaining group");second.actual=3;check(c.flush()&&second.total==5&&c.mPendingOreOutput==null,"CMA subsequent flush cannot replay accepted prefix");
 }
 public static void main(String[] args)throws Exception{if(!nativeBootstrap(args))return;FUEL=new Item();ORE=Item.getItemById(1);modes();gear();outputs();hubs();System.out.println("R3 machine conservation: "+checks+" checks; failures="+failures+"; real MC item/NBT, controlled GT/AE endpoints");if(failures>0)throw new AssertionError(failures+" failures");}
''']
 helper=(ROOT/'tools/tests/test_hub_transfer_contracts.py').read_text();a=helper.index('    static boolean nativeBootstrap(');b=helper.index("    '''",a)
 return '\n'.join(chunks)+helper[a:b].replace('HubTransferContract','MachineConservationR3')+'\n}'
def main():
 p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--classpath',required=True);p.add_argument('--javac',required=True);a=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='gtsr-r3-machine-')as tmp:
  d=Path(tmp);s=d/'MachineConservationR3.java';s.write_text(generate(a.source_root));subprocess.run([a.javac,'--release','17','-proc:none','-cp',a.classpath,'-d',str(d),str(s)],check=True);subprocess.run([str(Path(a.javac).with_name('java')),'-cp',str(d)+os.pathsep+a.classpath,'MachineConservationR3'],check=True)
if __name__=='__main__':main()
