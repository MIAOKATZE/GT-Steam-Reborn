import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.recipe.check.CheckRecipeResult;

public class SteamProbe {
 static Unsafe u;
 static int checks;
 static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);System.out.println("PASS "+text);}
 static void set(Object o,Class<?> c,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Method method(Class<?> c,String n,Class<?>...t)throws Exception{for(Class<?> p=c;p!=null;p=p.getSuperclass())try{Method m=p.getDeclaredMethod(n,t);m.setAccessible(true);return m;}catch(NoSuchMethodException e){}throw new NoSuchMethodException(n);}
 static void setAny(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{set(o,c,n,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static gregtech.api.interfaces.tileentity.IGregTechTileEntity tile(){return (gregtech.api.interfaces.tileentity.IGregTechTileEntity)Proxy.newProxyInstance(SteamProbe.class.getClassLoader(),new Class<?>[]{gregtech.api.interfaces.tileentity.IGregTechTileEntity.class},(p,m,a)->{Class<?> t=m.getReturnType();if(t==boolean.class)return m.getName().equals("isServerSide");if(t==int.class)return 0;if(t==long.class)return 0L;if(t==byte.class)return (byte)0;if(t==short.class)return (short)0;if(t==double.class)return 0d;if(t==float.class)return 0f;return null;});}
 static void init()throws Exception{
  Thread.currentThread().setName("Server thread"); Field uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);u=(Unsafe)uf.get(null);
  net.minecraft.client.Minecraft mc=(net.minecraft.client.Minecraft)u.allocateInstance(net.minecraft.client.Minecraft.class);
  set(null,net.minecraft.client.Minecraft.class,"theMinecraft",mc);
  set(mc,net.minecraft.client.Minecraft.class,"mcResourceManager",new net.minecraft.client.resources.SimpleReloadableResourceManager(new net.minecraft.client.resources.data.IMetadataSerializer()));
  Object sided=Proxy.newProxyInstance(SteamProbe.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.IFMLSidedHandler.class},(p,m,v)->m.getName().equals("getSide")?cpw.mods.fml.relauncher.Side.SERVER:null);
  try{set(cpw.mods.fml.common.FMLCommonHandler.instance(),cpw.mods.fml.common.FMLCommonHandler.class,"sidedDelegate",sided);}catch(NoSuchFieldException e){}
  set(null,cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome",new java.io.File("build/critical-functional-v33/steam/fixture-home"));
  Object loader=u.allocateInstance(cpw.mods.fml.common.Loader.class);set(loader,cpw.mods.fml.common.Loader.class,"namedMods",new HashMap<>());set(loader,cpw.mods.fml.common.Loader.class,"mods",new ArrayList<>());set(null,cpw.mods.fml.common.Loader.class,"instance",loader);
  Field proxy=gregtech.GTMod.class.getField("proxy");if(proxy.get(null)==null)proxy.set(null,u.allocateInstance(proxy.getType()));
  Class.forName("net.minecraft.init.Items");Field feather=net.minecraft.init.Items.class.getField("feather");if(feather.get(null)==null)u.putObject(u.staticFieldBase(feather),u.staticFieldOffset(feather),new net.minecraft.item.Item(){});
 }
 public static void main(String[] args)throws Exception{
  init();
  boolean fixed=args.length>0&&args[0].equals("fixed");
  System.out.println("GT ProcessingLogic="+ProcessingLogic.class.getProtectionDomain().getCodeSource().getLocation());
  for(String name:new String[]{"MTEReinforcedBrickBlastFurnace","MTELargeCokeOven","MTESiemensMartinFurnace"}){
   Class<?> c=Class.forName("com.miaokatze.gtsr.common.machine."+name); Object machine=u.allocateInstance(c);
   System.out.println("MACHINE "+name+"="+c.getProtectionDomain().getCodeSource().getLocation());
   for(Class<?> p=c;p!=null;p=p.getSuperclass())for(Field f:p.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&f.getType()==ArrayList.class){f.setAccessible(true);f.set(machine,new ArrayList<>());}
   set(machine,MTEMultiBlockBase.class,"voidingMode",gregtech.api.enums.VoidingMode.VOID_ALL);
   setAny(machine,"mInventory",new net.minecraft.item.ItemStack[2]);setAny(machine,"mBaseMetaTileEntity",tile());
   ((MTEMultiBlockBase)machine).fixAllIssues();
   gregtech.api.metatileentity.implementations.MTEHatchInputBus input=(gregtech.api.metatileentity.implementations.MTEHatchInputBus)u.allocateInstance(gregtech.api.metatileentity.implementations.MTEHatchInputBus.class);
   gregtech.api.metatileentity.BaseMetaTileEntity inputTile=(gregtech.api.metatileentity.BaseMetaTileEntity)u.allocateInstance(gregtech.api.metatileentity.BaseMetaTileEntity.class);
   setAny(input,"mBaseMetaTileEntity",inputTile);setAny(input,"mInventory",new net.minecraft.item.ItemStack[2]);setAny(input,"watchers",new ArrayList<>());inputTile.setMetaTileEntity(input);
   check((boolean)method(c,"addInputBusToMachineList",gregtech.api.interfaces.tileentity.IGregTechTileEntity.class,int.class).invoke(machine,inputTile,1),name+" real input bus accepted");
   method(MTEMultiBlockBase.class,"shouldCheckRecipeThisTick",long.class).invoke(machine,1L);
   input.setInventorySlotContents(0,new net.minecraft.item.ItemStack(new net.minecraft.item.Item(){},1));
   check(inputTile.hasInventoryBeenModified(),name+" real hatch inventory insertion sets native tile flag");
   input.onPostTick(inputTile,2L);
   check((boolean)method(MTEMultiBlockBase.class,"shouldCheckRecipeThisTick",long.class).invoke(machine,2L)==fixed,name+" inserted input schedules immediate recipe check="+fixed);
   if(name.equals("MTESiemensMartinFurnace"))set(machine,c,"mFurnaceTemperature",1d);
   for(int recipeEUt:new int[]{0,1,30}) {
   ProcessingLogic logic=(ProcessingLogic)method(c,"createProcessingLogic").invoke(machine);
   method(c,"setProcessingLogicPower",ProcessingLogic.class).invoke(machine,logic);
   logic.setMachine((MTEMultiBlockBase)machine).setVoidProtection(false,false);
   logic.process();
   GTRecipe recipe=(GTRecipe)u.allocateInstance(GTRecipe.class);recipe.mEUt=recipeEUt;recipe.mDuration=600;recipe.mInputs=new net.minecraft.item.ItemStack[0];recipe.mOutputs=new net.minecraft.item.ItemStack[0];recipe.mFluidInputs=new net.minecraftforge.fluids.FluidStack[0];recipe.mFluidOutputs=new net.minecraftforge.fluids.FluidStack[0];
   Object result=method(ProcessingLogic.class,"validateAndCalculateRecipe",GTRecipe.class).invoke(logic,recipe);
   Field consumed=result.getClass().getDeclaredField("successfullyConsumedInputs");consumed.setAccessible(true);check((boolean)consumed.get(result)==(fixed||recipeEUt==0),name+" expected power outcome EUt="+recipeEUt);
   if(fixed){method(c,"setEnergyUsage",ProcessingLogic.class).invoke(machine,logic);check(((MTEMultiBlockBase)machine).mEUt==0,name+" actual energy usage zero EUt="+recipeEUt);check(logic.getCurrentParallels()==((MTEMultiBlockBase)machine).getMaxParallelRecipes(),name+" full parallel preserved EUt="+recipeEUt);}
   System.out.println(name+" recipeEUt="+recipeEUt);
   for(Field f:result.getClass().getDeclaredFields()){f.setAccessible(true);Object v=f.get(result);System.out.println("  "+f.getName()+"="+(v instanceof CheckRecipeResult?((CheckRecipeResult)v).getID()+" success="+((CheckRecipeResult)v).wasSuccessful():v));}
   System.out.println("duration="+logic.getDuration()+" EUt="+logic.getCalculatedEut());
   }
   MTEMultiBlockBase base=(MTEMultiBlockBase)machine; base.mEUt=0;base.mMaxProgresstime=600;base.mProgresstime=0;
   for(int tick=1;tick<=600;tick++){
    method(MTEMultiBlockBase.class,"runMachine",gregtech.api.interfaces.tileentity.IGregTechTileEntity.class,long.class).invoke(machine,base.getBaseMetaTileEntity(),(long)tick);
    if(tick<600&&base.mProgresstime!=tick)throw new AssertionError(name+" progress "+tick+" actual="+base.mProgresstime);
   }
   if(base.mMaxProgresstime!=0||base.mProgresstime!=0)throw new AssertionError(name+" not finished after600");
   System.out.println("PASS "+name+" actual native runMachine 600 calls completes600t exactly");
   if(name.equals("MTELargeCokeOven")){
    com.miaokatze.gtsr.common.machine.MTELargeCokeOven oven=(com.miaokatze.gtsr.common.machine.MTELargeCokeOven)machine;
    for(int tier:new int[]{1,2})for(double heat:new double[]{0,.5,1}){
     oven.mTier=tier;oven.setHeat(heat);
     ProcessingLogic l=(ProcessingLogic)method(c,"createProcessingLogic").invoke(machine);method(c,"setProcessingLogicPower",ProcessingLogic.class).invoke(machine,l);l.setMachine(base).setVoidProtection(false,false);l.process();
     GTRecipe r=(GTRecipe)u.allocateInstance(GTRecipe.class);r.mDuration=1200;r.mInputs=new net.minecraft.item.ItemStack[0];r.mOutputs=new net.minecraft.item.ItemStack[0];r.mFluidInputs=new net.minecraftforge.fluids.FluidStack[0];r.mFluidOutputs=new net.minecraftforge.fluids.FluidStack[0];
     method(ProcessingLogic.class,"validateAndCalculateRecipe",GTRecipe.class).invoke(l,r);
     int expected=(int)Math.ceil(1200*(1-Math.min(.75,(tier==2?.25:0)+.5*heat)));
     check(l.getDuration()==expected,"LCO actual calculation tier="+tier+" heat="+heat+" duration="+expected);
     oven.mMaxProgresstime=l.getDuration();oven.mProgresstime=0;oven.mRuntime=0;oven.mMachine=true;setAny(oven,"mUpdate",-1);setAny(oven,"mStartUpCheck",-1);setAny(oven,"mCoils",new it.unimi.dsi.fastutil.longs.LongArrayList());
     for(int tick=1;tick<=expected;tick++){
      oven.onPostTick(base.getBaseMetaTileEntity(),tick);
      if(tick<expected&&oven.mProgresstime!=tick)throw new AssertionError("LCO native onPostTick stalled at "+tick+" actual="+oven.mProgresstime);
     }
     check(oven.mMaxProgresstime==0&&oven.mProgresstime==0,"LCO native parent+child onPostTick completes exactly="+expected+" sampled heat="+heat+" finalHeat="+oven.getHeat());
     set(machine,MTEMultiBlockBase.class,"voidingMode",gregtech.api.enums.VoidingMode.VOID_NONE);
     l=(ProcessingLogic)method(c,"createProcessingLogic").invoke(machine);method(c,"setProcessingLogicPower",ProcessingLogic.class).invoke(machine,l);l.setMachine(base).setVoidProtection(true,true);l.process();
     r.mOutputs=new net.minecraft.item.ItemStack[]{new net.minecraft.item.ItemStack(new net.minecraft.item.Item(){},1)};
     Object result=method(ProcessingLogic.class,"validateAndCalculateRecipe",GTRecipe.class).invoke(l,r);Field resultField=result.getClass().getDeclaredField("checkRecipeResult");resultField.setAccessible(true);CheckRecipeResult outResult=(CheckRecipeResult)resultField.get(result);
     check(!outResult.wasSuccessful(),"LCO no output bus denies output before start id="+outResult.getID());
     Field outputConsumed=result.getClass().getDeclaredField("successfullyConsumedInputs");outputConsumed.setAccessible(true);check(!(boolean)outputConsumed.get(result),"LCO output protection refuses before consumption");
     set(machine,MTEMultiBlockBase.class,"voidingMode",gregtech.api.enums.VoidingMode.VOID_ALL);
    }
   }
   gregtech.api.metatileentity.implementations.MTEHatchOutputBus output=(gregtech.api.metatileentity.implementations.MTEHatchOutputBus)u.allocateInstance(gregtech.api.metatileentity.implementations.MTEHatchOutputBus.class);
   gregtech.api.metatileentity.BaseMetaTileEntity outputTile=(gregtech.api.metatileentity.BaseMetaTileEntity)u.allocateInstance(gregtech.api.metatileentity.BaseMetaTileEntity.class);
   setAny(output,"mBaseMetaTileEntity",outputTile);setAny(output,"mInventory",new net.minecraft.item.ItemStack[2]);setAny(output,"watchers",new ArrayList<>());outputTile.setMetaTileEntity(output);
   check((boolean)method(c,"addOutputBusToMachineList",gregtech.api.interfaces.tileentity.IGregTechTileEntity.class,int.class).invoke(machine,outputTile,1),name+" real output bus accepted");
   method(MTEMultiBlockBase.class,"shouldCheckRecipeThisTick",long.class).invoke(machine,3L);
   output.setInventorySlotContents(0,null);output.onPostTick(outputTile,3L);
   check((boolean)method(MTEMultiBlockBase.class,"shouldCheckRecipeThisTick",long.class).invoke(machine,3L)==fixed,name+" drained output schedules immediate recipe check="+fixed);
  }
  System.out.println("RESULT checks="+checks+" failures=0 boundary=actual GT power/calculation/runMachine, LCO parent-child onPostTick, real input/output bus insertion/drain events with native BaseMetaTileEntity flags; Unsafe allocated machines/hatches/tiles and FML platform fixture; no recipe-map lookup, physical world/structure IO, wallclock or TPS");
 }
}
