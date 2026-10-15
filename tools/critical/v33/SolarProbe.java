package com.miaokatze.gtsr.common.critical;
import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.util.*;import java.math.*;import java.nio.file.*;import java.lang.reflect.*;
import net.minecraft.nbt.*;import net.minecraft.item.*;import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import gregtech.api.enums.*;import gregtech.api.util.*;import gregtech.api.metatileentity.implementations.*;
import gtPlusPlus.core.material.MaterialMisc;
import com.google.gson.*;
public class SolarProbe {
 static boolean start(CriticalMachineState s,MTECriticalMachine h)throws Exception {Method m=CriticalEnergyRuntime.class.getDeclaredMethod("startSolar",CriticalMachineState.class,MTECriticalMachine.class,NBTTagCompound.class);m.setAccessible(true);return (Boolean)m.invoke(null,s,h,s.energyRuntimeData());}
 static BigInteger amount(NBTTagCompound data,String fluid){NBTTagList list=data.getTagList("outputs",10);for(int i=0;i<list.tagCount();i++){NBTTagCompound n=list.getCompoundTagAt(i);if(FluidStack.loadFluidStackFromNBT(n).getFluid().getName().equals(fluid))return new BigInteger(n.getString("exactAmount"));}return BigInteger.ZERO;}
 static void modes()throws Exception {
  Fluid cold=EnergyProbe.fluid("probe.salt.cold"),hot=EnergyProbe.fluid("probe.salt.hot");set(MaterialMisc.SOLAR_SALT_COLD,"mFluid",cold);set(MaterialMisc.SOLAR_SALT_HOT,"mFluid",hot);
  for(int mode=0;mode<6;mode++){
   CriticalMachineState s=state(CriticalMachineKind.SOLAR);MTECriticalMachine h=host(s);s.energyRuntimeData().setInteger("steamMode",mode);
   MTEHatchInput water=EnergyProbe.input(new FluidStack(EnergyProbe.fluid("ic2distilledwater"),1000000)),salt=EnergyProbe.input(new FluidStack(cold,1000000));add(h,"mInputHatches",water);add(h,"mInputHatches",salt);add(h,"mOutputHatches",EnergyProbe.output());add(h,"mOutputHatches",EnergyProbe.output());
   check(start(s,h),"solar mode"+mode+" funds from real GT water/salt hatches");int q=mode<3?693147:693125;
   check(water.mFluid.amount==1000000-q&&salt.mFluid.amount==306853,"solar mode"+mode+" logarithmic water/salt exact debit");
   BigInteger steam=BigInteger.valueOf(mode<3?q*160L:q/25L*4L);check(amount(s.energyRuntimeData(),CriticalEnergyRuntime.steam(mode).getFluid().getName()).equals(steam)&&amount(s.energyRuntimeData(),hot.getName()).intValueExact()==693147,"solar mode"+mode+" exact steam grade ratio and1:1 hot salt");
   check(s.energyRuntimeData().getString("eu").equals("0"),"solar mode"+mode+" generates no EU");
  }
 }
 static void irradiation()throws Exception {
  Item dust=CoreProbe.item(31980,"stonedust"),irr=CoreProbe.item(31981,"irradiatedstone"),moon=CoreProbe.item(31982,"irradiatedmoon");((Map<String,ItemStack>)get(GTOreDictUnificator.class,"sName2StackMap")).put("dustStone",new ItemStack(dust));GTSRItemList.IrradiatedStoneDust.set(irr);GTSRItemList.IrradiatedMoonDust.set(moon);
  CriticalMachineState s=state(CriticalMachineKind.SOLAR);MTECriticalMachine h=host(s);MTEHatchInputBus input=bus(new ItemStack(dust,64),new ItemStack(dust,64),new ItemStack(dust,64),new ItemStack(dust,64),new ItemStack(dust,64));add(h,"mInputBusses",input);add(h,"mOutputBusses",CoreProbe.output());
  check(start(s,h),"irradiation independently funds actual stone-dust GT hatch stock");int remaining=CoreProbe.count(h.criticalPhysicalItemsSnapshot());check(remaining==64,"irradiation max parallel256 reserves exactly256 of320 dust");
  NBTTagList list=s.energyRuntimeData().getTagList("items",10);check(list.tagCount()==1&&list.getCompoundTagAt(0).getInteger("exactCount")==256,"irradiation product journal records256 without64 clamp");
  NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);CriticalMachineState restored=new CriticalMachineState();restored.readFromNBT(saved);check(restored.energyRuntimeData().getTagList("items",10).getCompoundTagAt(0).getInteger("exactCount")==256,"irradiation256 product survives real controller NBT");
 }
 static void ownership()throws Exception {
  SparseWorld w1=world(),w2=world();w2.provider.dimensionId=42;CriticalWorldData a=CriticalWorldData.get(w1),b=CriticalWorldData.get(w2);
  check(a.claimNight("first")&&!a.claimNight("second"),"one persistent night claim per dimension");check(b.claimNight("other-dimension"),"different dimension has independent night claimant");
  NBTTagCompound n=new NBTTagCompound();a.writeToNBT(n);CriticalWorldData restored=new CriticalWorldData();restored.readFromNBT(n);check(restored.holdsNight("first")&&!restored.claimNight("second"),"night owner survives world-data NBT");check(!restored.validateNightOwner(w1)&&!restored.hasNightOwner(),"restart without live validated owner revokes stale night claim");
 }
 public static void main(String[] args)throws Exception {
  bootstrap();EnergyProbe.fluids();modes();irradiation();ownership();JsonObject o=new JsonObject();o.addProperty("status","PASS");o.addProperty("checks",checks);o.addProperty("GT",source(MTEHatchInput.class));o.addProperty("product",source(CriticalEnergyRuntime.class));o.addProperty("boundary","Private production startSolar seam with real GT hatches and output transactions; night ownership world-data NBT checked separately; no full Forge time/network lifecycle");Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes("UTF-8"));
 }
}
