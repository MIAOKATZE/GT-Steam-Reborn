package com.miaokatze.gtsr.common.critical;
import static com.miaokatze.gtsr.common.critical.ProbeSupport.*;
import java.util.*;import java.math.*;import java.nio.file.*;
import net.minecraft.nbt.*;import net.minecraft.item.*;import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.enums.*;import gregtech.api.util.*;import gregtech.api.metatileentity.implementations.*;
import gregtech.common.misc.*;import gregtech.common.misc.spaceprojects.*;
import com.google.gson.*;
public class EnergyProbe {
 static final UUID OWNER=UUID.fromString("33333333-0000-0000-0000-000000000001"),LEADER=UUID.fromString("33333333-0000-0000-0000-000000000002");
 static Fluid fluid(String n){Fluid f=FluidRegistry.getFluid(n);if(f==null){f=new Fluid(n);FluidRegistry.registerFluid(f);}return f;}
 static void fluids(){Materials.Steam.mGas=fluid("steam");fluid("ic2superheatedsteam");fluid("supercriticalsteam");Materials.DenseSteam.mGas=fluid("densesteam");Materials.DenseSuperheatedSteam.mGas=fluid("densesuperheatedsteam");Materials.DenseSupercriticalSteam.mGas=fluid("densesupercriticalsteam");Materials.Water.mFluid=fluid("water");fluid("ic2distilledwater");}
 static MTEHatchInput input(FluidStack f)throws Exception {MTEHatchInput h=(MTEHatchInput)U.allocateInstance(MTEHatchInput.class);attach(h);set(h,"mTier",(byte)18);set(h,"mInventory",new ItemStack[4]);h.mFluid=f;return h;}
 static MTEHatchOutput output()throws Exception {MTEHatchOutput h=(MTEHatchOutput)U.allocateInstance(MTEHatchOutput.class);attach(h);set(h,"mTier",(byte)17);set(h,"mInventory",new ItemStack[4]);return h;}
 static MTECriticalMachine hostOwned(CriticalMachineState s)throws Exception {MTECriticalMachine h=host(s);h.getBaseMetaTileEntity().setOwnerUuid(OWNER);return h;}
 static void ticks(CriticalMachineState s,MTECriticalMachine h,int n){for(int i=0;i<n;i++)CriticalEnergyRuntime.tick(s,h);}
 static void math(){
  check(CriticalEnergyMath.logarithmic(1000000L,1,1000000,1000000)==693147,"water logarithmic floor F=1 Q=1M");
  check(CriticalEnergyMath.logarithmic(1000000L,2,1000000,1000000)==1000000,"dimension factor clamp to available stock");
  check(CriticalEnergyMath.logarithmic(1000000L,0.5,1000000,1000000)==346573,"dimension factor0.5 scales logarithmic throughput");
  check(CriticalEnergyMath.logarithmic(0L,1,1,1)==0,"zero water has zero debit");
  BigInteger[] v=new BigInteger[6];Arrays.fill(v,BigInteger.valueOf(Long.MAX_VALUE));CriticalEnergyMath.TurbineBatch m=CriticalEnergyMath.turbine(v,159);
  BigInteger expected=BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(144144));
  check(m.eu.equals(expected)&&m.eu.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0,"six steam grades accumulate EU exactly beyond long range");
  long[] one={1,0,0,0,0,0};check(CriticalEnergyMath.turbine(one,159).water.equals(BigInteger.ONE)&&CriticalEnergyMath.turbine(one,159).remainder==0,"condensation remainder160 carry exact");
 }
 static void turbine()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.TURBINE);MTECriticalMachine h=hostOwned(s);MTEHatchOutput out=output();add(h,"mOutputHatches",out);
  long[] amounts={159,1,160,1,2,3};List<MTEHatchInput> inputs=new ArrayList<>();for(int i=0;i<6;i++){FluidStack f=CriticalEnergyRuntime.steam(i);f.amount=(int)amounts[i];MTEHatchInput in=input(f);inputs.add(in);add(h,"mInputHatches",in);}add(h,"mInputHatches",inputs.get(0));
  BigInteger base=BigInteger.ONE.shiftLeft(100);WirelessNetworkManager.setUserEU(OWNER,base);
  CriticalEnergyRuntime.tick(s,h);check(inputs.stream().allMatch(x->x.mFluid==null),"all six real GT hatch inventories debited once despite duplicate hatch reference");
  check(s.energyRuntimeData().getInteger("progress")==1&&out.mFluid==null,"funded turbine progresses1 with no early output");
  ticks(s,h,198);check(s.energyRuntimeData().getInteger("progress")==199,"native runtime reaches tick199 before save");
  NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);CriticalMachineState r=new CriticalMachineState();r.readFromNBT(saved);MTECriticalMachine restored=hostOwned(r);add(restored,"mOutputHatches",out);
  check(r.energyRuntimeData().getBoolean("funded")&&r.energyRuntimeData().getInteger("progress")==199,"real controller NBT preserves funded turbine/progress");
  ticks(r,restored,200);check(out.mFluid==null&&WirelessNetworkManager.getUserEU(LEADER).equals(base),"tick399 emits neither water nor wireless EU");
  CriticalEnergyRuntime.tick(r,restored);check(out.mFluid!=null&&out.mFluid.amount==39&&r.energyRuntimeData().getInteger("waterRemainder")==80,"tick400 emits exact39 water and preserves80 remainder");
  check(WirelessNetworkManager.getUserEU(LEADER).equals(base.add(BigInteger.valueOf(367392))),"native WirelessNetworkManager credits owner team BigInteger once");
  CriticalEnergyRuntime.tick(r,restored);check(out.mFluid.amount==39&&WirelessNetworkManager.getUserEU(LEADER).equals(base.add(BigInteger.valueOf(367392))),"empty next tick does not replay settled output or wireless energy");
  MTEHatchInput next=input(new FluidStack(CriticalEnergyRuntime.steam(0).getFluid(),80));add(restored,"mInputHatches",next);ticks(r,restored,400);
  check(out.mFluid.amount==40&&r.energyRuntimeData().getInteger("waterRemainder")==0,"persisted condensation80 plus next80 yields one whole water");
 }
 static void hugeAndBlocked()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.TURBINE);set(s,"config",new CriticalConfiguration(CriticalTier.T1,CriticalMachineKind.TURBINE,0,0,0));MTECriticalMachine h=hostOwned(s);
  MTEHatchInput a=input(new FluidStack(CriticalEnergyRuntime.steam(2).getFluid(),Integer.MAX_VALUE)),b=input(new FluidStack(CriticalEnergyRuntime.steam(2).getFluid(),Integer.MAX_VALUE));add(h,"mInputHatches",a);add(h,"mInputHatches",b);
  CriticalEnergyRuntime.tick(s,h);check(a.mFluid.amount==Integer.MAX_VALUE&&b.mFluid.amount==Integer.MAX_VALUE&&!s.energyRuntimeData().getBoolean("funded"),"blocked distilled-water output consumes no steam");
  MTEHatchOutput out=output();add(h,"mOutputHatches",out);CriticalEnergyRuntime.tick(s,h);
  check(a.mFluid==null&&b.mFluid==null&&new BigInteger(s.energyRuntimeData().getString("eu")).equals(BigInteger.valueOf(343597383520L)),"multiple real hatches above INT_MAX volume fund uncapped steam batch without energy hatch");
  out.mFluid=new FluidStack(fluid("blocked"),1);ticks(s,h,399);check(s.energyRuntimeData().getBoolean("funded")&&s.energyRuntimeData().getInteger("progress")==400,"mid-batch blocked output retains paid journal at400");
  out.mFluid=null;CriticalEnergyRuntime.tick(s,h);check(out.mFluid.amount==26843545&&s.energyRuntimeData().getInteger("waterRemainder")==94,"unblock commits exact large condensation and remainder94");
 }
 static void bigWirelessJournal()throws Exception {
  CriticalMachineState s=state(CriticalMachineKind.TURBINE);NBTTagCompound data=s.energyRuntimeData();BigInteger generated=BigInteger.ONE.shiftLeft(100);data.setBoolean("funded",true);data.setInteger("progress",399);data.setString("eu",generated.toString());data.setString("owner",OWNER.toString());data.setTag("outputs",new NBTTagList());data.setTag("items",new NBTTagList());
  NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);CriticalMachineState r=new CriticalMachineState();r.readFromNBT(saved);MTECriticalMachine h=hostOwned(r);WirelessNetworkManager.setUserEU(OWNER,BigInteger.ZERO);CriticalEnergyRuntime.tick(r,h);check(WirelessNetworkManager.getUserEU(LEADER).equals(generated),"restored funded journal settles generated2^100 EU through real team wireless API without long cap");check(r.energyRuntimeData().getString("lastGeneratedEU").equals(generated.toString()),"exact generated2^100 receipt persists as decimal NBT");
 }
 public static void main(String[] args)throws Exception {
  bootstrap();fluids();GlobalEnergyWorldSavedData.INSTANCE=new GlobalEnergyWorldSavedData();SpaceProjectManager.checkOrCreateTeam(OWNER);SpaceProjectManager.checkOrCreateTeam(LEADER);SpaceProjectManager.putInTeam(OWNER,LEADER);
  math();turbine();hugeAndBlocked();bigWirelessJournal();
  JsonObject o=new JsonObject();o.addProperty("status","PASS");o.addProperty("checks",checks);o.addProperty("GT",source(WirelessNetworkManager.class));o.addProperty("product",source(CriticalEnergyRuntime.class));o.addProperty("boundary","Real GT native hatches/output transactions/team wireless manager + controller NBT; registry seed and sparse world, not full server");Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(o).getBytes("UTF-8"));
 }
}
