package com.miaokatze.gtsr.common.critical;
import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.util.*;import java.nio.file.*;
import net.minecraft.item.*;import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.util.*;import gregtech.api.recipe.*;
import gregtech.api.metatileentity.implementations.*;
import com.google.gson.*;
public class CoreProbe {
 static Item a,b,c,stick;
 static Item item(int id,String name){Item i=new Item().setUnlocalizedName(name);Item.itemRegistry.addObject(id,"probe:"+name,i);return i;}
 static GTRecipe recipe(int eut,int duration){return new GTRecipe(false,new ItemStack[]{new ItemStack(a,2)},new ItemStack[]{new ItemStack(b)},null,null,null,null,null,new FluidStack[0],new FluidStack[0],duration,eut,0);}
 static int count(ItemStack[] v){int n=0;for(ItemStack i:v)if(i!=null)n+=i.stackSize;return n;}
 static void prepare()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.PROCESSING);GTRecipe r=recipe(128,7);ItemStack[] in={new ItemStack(a,1),new ItemStack(a,3)};
  RecipeMatch m=CriticalGTRecipes.prepare(s,r,in,new FluidStack[0],128,1000,"native");
  check(m!=null&&count(m.itemInputs)==4&&count(m.itemOutputs)==2,"native ParallelHelper merges split same-item stacks into two parallels");
  check(in[0].stackSize==1&&in[1].stackSize==3,"matching leaves live snapshots unchanged");
  check(m.euPerTick==3&&m.durationTicks==400,"0.01 EU multiplier with ceil rounding and fixed 400t; native duration7 ignored");
  check(CriticalGTRecipes.prepare(s,r,in,new FluidStack[0],127,100000,"native")==null,"raw recipe voltage128 rejects hatch voltage127 before discount");
  m=CriticalGTRecipes.prepare(s,r,in,new FluidStack[0],128,2,"native");check(m!=null&&count(m.itemOutputs)==1&&m.euPerTick==2,"power budget chooses one parallel without overclock");
  ItemStack[] inventory=(ItemStack[])get(s,"inventory");for(int i=18;i<36;i++)inventory[i]=new ItemStack(c,64);
  check(CriticalGTRecipes.prepare(s,r,in,new FluidStack[0],128,100000,"native")==null,"blocked output rejects batch before funding");
 }
 static MTEHatchOutputBus output()throws Exception {MTEHatchOutputBus b=(MTEHatchOutputBus)U.allocateInstance(MTEHatchOutputBus.class);attach(b);set(b,"mInventory",new ItemStack[16]);return b;}
 static MTECriticalMachine recipeHost(CriticalMachineState s)throws Exception {
  MTECriticalMachine m=host(s);add(m,"mEnergyHatches",energy(2,1000000));set(m.getBaseMetaTileEntity(),"mStoredEnergy",1000000L);add(m,"mOutputBusses",output());return m;
 }
 static void matching()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.ASSEMBLY);set(s,"processingMode",RecipeMaps.assemblerRecipes.unlocalizedName);MTECriticalMachine h=recipeHost(s);
  add(h,"mInputBusses",bus(new ItemStack(a,1),new ItemStack(a,3)));
  GTRecipe r=recipe(128,7);check(RecipeMaps.assemblerRecipes.addRecipe(r,false,false,false)!=null,"register test recipe in real GT assembler RecipeMap");
  RecipeMatch m=CriticalGTRecipes.match(s,h);check(m!=null&&count(m.itemOutputs)==2,"production matcher queries real RecipeMap and real native hatch snapshots");
  check(h.criticalRecipeVoltage()==128,"host exposes native energy hatch raw voltage");
  set(s,"processingMode",CriticalProcessingModes.ASSEMBLY_LINE);check(CriticalGTRecipes.match(s,h)==null,"assembly line rejects unresearched recipe without data-access hatch");
  GTRecipe.RecipeAssemblyLine al=new GTRecipe.RecipeAssemblyLine(new ItemStack(c),100,128,new ItemStack[]{new ItemStack(a,2)},new FluidStack[0],new ItemStack(b),900,128,new ItemStack[1][]);al.setPersistentHash(330001);
  GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.add(al);gregtech.api.enums.ItemList.Tool_DataStick.set(stick);
  ItemStack flash=new ItemStack(stick);NBTTagCompound n=new NBTTagCompound();n.setTag("output",new ItemStack(b).writeToNBT(new NBTTagCompound()));flash.setTagCompound(n);
  MTEHatchDataAccess data=(MTEHatchDataAccess)U.allocateInstance(MTEHatchDataAccess.class);attach(data);set(data,"mInventory",new ItemStack[]{flash});add(h,"criticalDataHatches",data);
  check(data.getAssemblyLineRecipes().contains(al),"real GT data-access resolves researched flash NBT via AssemblyLineUtils");
  m=CriticalGTRecipes.match(s,h);check(m!=null&&m.durationTicks==400&&count(m.itemOutputs)==2,"production AL matcher executes flash recipe at fixed400t with two parallels");
  NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);CriticalMachineState restored=new CriticalMachineState();restored.readFromNBT(saved);check(restored.getProcessingMode().equals(CriticalProcessingModes.ASSEMBLY_LINE),"selected processing mode survives NBT");
 }
 static void journal()throws Exception {
  NBTTagCompound legacyConfig=new NBTTagCompound();legacyConfig.setInteger("tier",0);legacyConfig.setInteger("kind",CriticalMachineKind.PROCESSING.ordinal());legacyConfig.setInteger("parallel",2);legacyConfig.setInteger("speed",3);legacyConfig.setInteger("economy",1);CriticalConfiguration config=CriticalConfiguration.read(legacyConfig);check(config.tier==CriticalTier.T1&&config.parallel==2&&config.speed==3&&config.economy==1&&config.write().equals(legacyConfig),"paid legacy construction tier/plugin geometry snapshot survives NBT unchanged");
  config=new CriticalConfiguration(CriticalTier.T1,CriticalMachineKind.PROCESSING,2,3,1);check(config.tier==CriticalTier.T3&&config.parallel==8&&config.speed==0&&config.economy==0,"new configuration uses fixed T3 decorative eight-module construction");
  CriticalMachineState s=state(CriticalMachineKind.PROCESSING);int[] amounts={200,256,Integer.MAX_VALUE};ItemStack[] v={new ItemStack(a,amounts[0]),new ItemStack(b,amounts[1]),new ItemStack(c,amounts[2])};
  set(s,"escrow",v);set(s,"pendingItems",v);NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);CriticalMachineState r=new CriticalMachineState();r.readFromNBT(n);
  for(String key:new String[]{"escrow","pendingItems"}){ItemStack[] restored=(ItemStack[])get(r,key);for(int i=0;i<amounts.length;i++)check(restored[i].stackSize==amounts[i],"NBT "+key+" exact item count "+amounts[i]);}
  CriticalMachineState old=state(CriticalMachineKind.TURBINE);set(old,"batchId","legacy-paid-v32");set(old,"cycleDuration",400);set(old,"cycleTicks",399);set(old,"batchEu",0L);set(old,"batchGenerated",1000L);set(old,"pendingItems",new ItemStack[]{new ItemStack(b,200)});
  call(old,"runBatch");check(((String)get(old,"batchId")).isEmpty()&&((Long)get(old,"pendingGenerated"))==1000L,"v32 paid energy journal settles through original path before new backend");
  ItemStack[] inventory=(ItemStack[])get(old,"inventory");int total=0;for(int i=18;i<36;i++)if(inventory[i]!=null)total+=inventory[i].stackSize;check(total==200,"v32 pending item journal delivered exactly once");
 }
 static void advanced()throws Exception {
  Item catalyst=item(31995,"qftcatalyst");GTRecipe original=recipe(128,7);original.mOutputs=new ItemStack[]{new ItemStack(b),new ItemStack(c)};original.mOutputChances=new int[]{10000,10000};gregtech.api.recipe.metadata.RecipeMetadataStorage metadata=new gregtech.api.recipe.metadata.RecipeMetadataStorage();metadata.store(GTRecipeConstants.QFT_CATALYST,new ItemStack(catalyst));set(original,"metadataStorage",metadata);
  GTRecipe adjusted=CriticalAdvancedRecipes.qftBase(original);check(adjusted!=null&&adjusted.mInputs.length==2&&adjusted.mInputs[1].stackSize==0,"QFT adapter encodes native nonconsumed catalyst requirement");check(adjusted.mOutputChances[0]==5000&&adjusted.mOutputChances[1]==5000&&original.mOutputChances[0]==10000&&original.mInputs.length==1,"QFT default output probability and reusable original arrays preserved");
  CriticalMachineState s=state(CriticalMachineKind.PROCESSING);set(s,"processingMode",RecipeMaps.quantumForceTransformerRecipes.unlocalizedName);MTECriticalMachine h=recipeHost(s);add(h,"mInputBusses",bus(new ItemStack(a,8)));check(RecipeMaps.quantumForceTransformerRecipes.addRecipe(original,false,false,false)!=null,"register recipe in real native QFT map");check(CriticalGTRecipes.match(s,h)==null,"QFT production matcher rejects missing catalyst");
  add(h,"mInputBusses",bus(new ItemStack(catalyst,3)));RecipeMatch m=CriticalGTRecipes.match(s,h);check(m!=null&&count(m.itemInputs)==6&&m.euPerTick==4,"QFT catalyst stock3 limits native recipe to three parallels");for(ItemStack cost:m.itemInputs)check(cost.getItem()!=catalyst,"QFT cost journal excludes nonconsumed catalyst");check(s.fundCriticalInputs(m.itemInputs,m.fluidInputs),"QFT real GT bus funding succeeds");int remainingCatalyst=0;for(ItemStack st:h.criticalPhysicalItemsSnapshot())if(st.getItem()==catalyst)remainingCatalyst+=st.stackSize;check(remainingCatalyst==3,"QFT actual hatch debit preserves all catalyst items");
 }
 static void execution()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.ASSEMBLY);set(s,"processingMode",RecipeMaps.assemblerRecipes.unlocalizedName);MTECriticalMachine h=recipeHost(s);add(h,"mInputBusses",bus(new ItemStack(a,4)));
  for(int i=0;i<200;i++){set(h,"energySpentThisTick",0L);((Map<?,?>)get(h,"hatchEnergySpent")).clear();call(s,"runBatch");}
  check(s.getBatchProgress()==200&&CoreProbe.count(h.criticalPhysicalItemsSnapshot())==0,"production runBatch debits real GT stock once and progresses200 ticks");
  NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);CriticalMachineState r=new CriticalMachineState();r.readFromNBT(n);MTECriticalMachine restored=recipeHost(r);MTEHatchOutputBus out=((List<MTEHatchOutputBus>)get(restored,"mOutputBusses")).get(0);
  for(int i=0;i<199;i++){set(restored,"energySpentThisTick",0L);((Map<?,?>)get(restored,"hatchEnergySpent")).clear();call(r,"runBatch");}
  check(r.getBatchProgress()==399&&CoreProbe.count((ItemStack[])get(out,"mInventory"))==0,"saved batch resumes199ticks without input re-debit or early output");
  ItemStack[] inventory=(ItemStack[])get(out,"mInventory");for(int i=0;i<inventory.length;i++)inventory[i]=new ItemStack(c,64);set(restored,"energySpentThisTick",0L);call(r,"runBatch");
  check(r.getBatchProgress()==400&&!((String)get(r,"batchId")).isEmpty(),"400th tick blocked real output preserves paid batch journal");
  Arrays.fill(inventory,null);set(restored,"energySpentThisTick",0L);call(r,"runBatch");check(CoreProbe.count(inventory)==2&&((String)get(r,"batchId")).isEmpty(),"unblock settles exactly two parallel products into real GT output bus");
  set(restored,"energySpentThisTick",0L);call(r,"runBatch");check(CoreProbe.count(inventory)==2,"settled recipe cannot replay without fresh inputs");
 }
 public static void main(String[] args)throws Exception {
  bootstrap();a=item(31990,"a");b=item(31991,"b");c=item(31992,"c");stick=item(31993,"flash");gregtech.api.enums.ItemList.Paper_Printed_Pages.set(c);
  prepare();matching();execution();advanced();journal();
  JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("checks",checks);result.addProperty("GT",source(GTRecipe.class));result.addProperty("product",source(CriticalGTRecipes.class));result.addProperty("boundary","Real GT recipe map/helper/data-access/hatches; sparse world and FML/StructureLib gates, no full server lifecycle");Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
 }
}
