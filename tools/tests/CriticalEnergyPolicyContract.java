import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;

import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Production boiler catalogue/planner, pulse arithmetic, real native hatch debit and frozen NBT. */
public final class CriticalEnergyPolicyContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalEnergyPolicyRuntime", "5.09.54.205");
    }
}

class CriticalEnergyPolicyRuntime {
    static final java.io.PrintStream OUT=System.out;
    static int checks;
    static sun.misc.Unsafe unsafe;
    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    static BigInteger b(long n) { return BigInteger.valueOf(n); }
    static BigInteger fullTurbineHeat() {
        return b(CriticalSolarPolicy.MAX_VOLTAGE).multiply(b(2L*400*CriticalEnergyMath.TURBINE_FULL_WARMUP_BASE_AMPS_MAX));
    }
    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static void seed(String material, String name, String property) throws Exception {
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) { fluid = new Fluid(name); FluidRegistry.registerFluid(fluid); }
        Materials definition = (Materials) Materials.class.getField(material).get(null);
        if (definition == null) {
            definition = (Materials) unsafe.allocateInstance(Materials.class);
            definition.mMaterialList = new ArrayList<>();
        }
        Materials.class.getField(property).set(definition, fluid);
        Materials.class.getField(material).set(null, definition);
    }
    public static void run(String version) throws Exception {
        unsafe = Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null,cpw.mods.fml.relauncher.FMLRelaunchLog.class,"side",cpw.mods.fml.relauncher.Side.SERVER);
        for (Class<?> type : List.of(CriticalEnergyMath.class, CriticalSolarPolicy.class, CriticalEnergyRuntime.class,
            CriticalEnergyPorts.class)) System.out.println("SOURCE " + type.getSimpleName() + " "
                + type.getProtectionDomain().getCodeSource().getLocation());
        pureMath();
        seed("SuperCoolant", "gtsr_solar_supercoolant", "mFluid");
        seed("Time", "gtsr_solar_tachyon", "mStandardMoltenFluid");
        seed("Space", "gtsr_solar_space", "mStandardMoltenFluid");
        seed("Helium", "gtsr_solar_he", "mPlasma");
        seed("Helium", "gtsr_solar_he_gas", "mGas");
        seed("Iron", "gtsr_solar_fe", "mPlasma");
        seed("Americium", "gtsr_solar_am", "mPlasma");
        seed("DenseSteam", "densesteam", "mGas");
        seed("DenseSuperheatedSteam", "densesuperheatedsteam", "mGas");
        seed("DenseSupercriticalSteam", "densesupercriticalsteam", "mGas");
        FluidRegistry.registerFluid(new Fluid("cryotheum"));
        FluidRegistry.registerFluid(new Fluid("ic2distilledwater"));
        Fluid atomic = new Fluid("gtsr_solar_atomic"); FluidRegistry.registerFluid(atomic);
        bartworks.system.material.WerkstoffLoader.molten.put(goodgenerator.items.GGMaterial.atomicSeparationCatalyst, atomic);
        UUID owner = UUID.fromString("22222222-3333-4444-5555-666666666666");
        MTECriticalMachine host = (MTECriticalMachine) unsafe.allocateInstance(MTECriticalMachine.class);
        EnergyClock hatchWorld=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);hatchWorld.clock=1;
        IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[] {IGregTechTileEntity.class}, (p,m,a) -> {
                if (m.getName().equals("getOwnerUuid")) return owner;
                if (m.getName().equals("getMetaTileEntity")) return host;
                if (m.getName().equals("getWorld")) return hatchWorld;
                if (m.getReturnType() == boolean.class) return false;
                return null;
            });
        host.setBaseMetaTileEntity(tile);
        CriticalCompoundRuntime.set(host, "mDualInputHatches", new ArrayList<>());
        CriticalCompoundRuntime.set(host, "mOutputHatches", new ArrayList<>());
        CriticalCompoundRuntime.set(host, "mOutputBusses", new ArrayList<>());
        var cryo = CriticalCompoundRuntime.hatch(unsafe, tile, new FluidStack(FluidRegistry.getFluid("cryotheum"),1), 20000);
        var helium = CriticalCompoundRuntime.hatch(unsafe, tile, Materials.Helium.getPlasma(1), 180);
        var heliumGas = CriticalCompoundRuntime.hatch(unsafe, tile, Materials.Helium.getGas(1), 20000);
        List<CriticalCompoundRuntime.FakeHatch> waters = new ArrayList<>();
        for (int i=0;i<100;i++) waters.add(CriticalCompoundRuntime.hatch(unsafe,tile,
            gregtech.api.util.GTModHandler.getDistilledWater(1),Integer.MAX_VALUE));
        var water=waters.get(0);
        List<CriticalCompoundRuntime.FakeHatch> hatches=new ArrayList<>(List.of(cryo,helium,heliumGas)); hatches.addAll(waters);
        CriticalCompoundRuntime.set(host, "mInputHatches", hatches);
        List<CriticalEnergyPorts.Input> snapshot = CriticalEnergyPorts.snapshot(host);
        Method select = CriticalEnergyRuntime.class.getDeclaredMethod("selectSolar", List.class, double.class,
            int.class, boolean.class, boolean.class); select.setAccessible(true);
        Object selection = select.invoke(null, snapshot, 0.6D, 5, false, false);
        check(selection != null && ((CriticalSolarPolicy.Entry)field(selection,"coolant")).id.equals("cryotheum")
            && ((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium"), "highest fully paid He/cryo route");
        check(field(selection,"coolantLitres").equals(b(20000)) && field(selection,"exciterLitres").equals(b(180)),
            "full private cryo/He batch prepays real 20 seconds: coolant20000L and helium180L");
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        var allocation = new CriticalEnergyPorts.Allocation(snapshot);
        debits.addAll(allocation.take((FluidStack)field(selection,"input"),(BigInteger)field(selection,"inputLitres")));
        debits.addAll(allocation.take(cryo.fluid,(BigInteger)field(selection,"coolantLitres")));
        debits.addAll(allocation.take(helium.fluid,(BigInteger)field(selection,"exciterLitres")));
        check(CriticalEnergyPorts.debit(debits) && cryo.stock == 0 && helium.stock == 0 && water.stock < Integer.MAX_VALUE,
            "native hatch drain receipts actually pay work fluid, coolant and exciter");
        cryo.stock = 20000; helium.stock = 179; for(var hatch:waters) hatch.stock=Integer.MAX_VALUE;
        selection = select.invoke(null, CriticalEnergyPorts.snapshot(host), 0.6D, 5, false, false);
        check(selection != null && ((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium_gas")
            && helium.stock == 179, "one litre short of helium plasma falls back to fully payable helium gas");
        heliumGas.stock=0;helium.stock=0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false)==null,
            "water and full coolant cannot run without any mandatory activator");
        var supercoolant=CriticalCompoundRuntime.hatch(unsafe,tile,Materials.SuperCoolant.getFluid(1),32000);
        List<CriticalCompoundRuntime.FakeHatch> baseInputs=new ArrayList<>(List.of(supercoolant,heliumGas,helium));baseInputs.addAll(waters);
        CriticalCompoundRuntime.set(host,"mInputHatches",baseInputs);
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false)==null,
            "supercoolant alone cannot bypass the separate activator condition");
        heliumGas.stock=20000;
        snapshot=CriticalEnergyPorts.snapshot(host);
        selection=select.invoke(null,snapshot,.6D,5,false,false);
        check(selection!=null&&((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium_gas")
            &&field(selection,"exciterLitres").equals(b(20000))&&field(selection,"coolantLitres").equals(b(32000)),
            "baseline supercoolant plus helium gas has two separately paid full-batch requirements");
        debits=new ArrayList<>();allocation=new CriticalEnergyPorts.Allocation(snapshot);
        debits.addAll(allocation.take((FluidStack)field(selection,"input"),(BigInteger)field(selection,"inputLitres")));
        debits.addAll(allocation.take(supercoolant.fluid,(BigInteger)field(selection,"coolantLitres")));
        debits.addAll(allocation.take(heliumGas.fluid,(BigInteger)field(selection,"exciterLitres")));
        check(CriticalEnergyPorts.debit(debits)&&supercoolant.stock==0&&heliumGas.stock==0&&helium.stock==0,
            "native hatch receipts debit helium gas once, never substitute or double-charge plasma");
        CriticalCompoundRuntime.set(host,"mInputHatches",hatches);heliumGas.stock=20000;helium.stock=179;
        cryo.stock = 0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),0.6D,5,false,true) == null,
            "neither water nor dust-only work bypasses mandatory coolant");
        cryo.stock = 79; for(var hatch:waters) hatch.stock=0;
        selection = select.invoke(null,CriticalEnergyPorts.snapshot(host),0.6D,5,false,true);
        check(selection != null && field(selection,"coolantLitres").equals(b(79))
            && field(selection,"exciterLitres").equals(b(79))&&field(selection,"equivalentEU").equals(BigInteger.ZERO),
            "dust-only pays both ceil(20000/256)L coolant and helium gas, no imaginary energy");
        heliumGas.stock=0;helium.stock=0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,true)==null,
            "dust-only cannot bypass the mandatory activator when coolant is present");
        helium.stock=1;
        selection=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,true);
        check(selection!=null&&((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium")
            &&field(selection,"exciterLitres").equals(b(1)),"dust-only accepts and pays helium plasma without requiring extra helium gas");
        cryo.stock = 78;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),0.6D,5,false,true) == null,
            "dust-only underfunded minimum load cannot run");
        solarBatch(host,tile,cryo,helium,waters,select);
        frozenPulse(host, owner);
        OUT.println("PASS CriticalEnergyPolicyContract checks=" + checks);
    }
    static void solarBatch(MTECriticalMachine host,IGregTechTileEntity tile,
        CriticalCompoundRuntime.FakeHatch cryo,CriticalCompoundRuntime.FakeHatch helium,
        List<CriticalCompoundRuntime.FakeHatch> waters,Method select) throws Exception {
        Fluid cold=new Fluid("molten.solarsaltcold"),hot=new Fluid("molten.solarsalthot");
        FluidRegistry.registerFluid(cold);FluidRegistry.registerFluid(hot);
        gtPlusPlus.core.material.MaterialMisc.SOLAR_SALT_COLD.setFluid(cold);
        gtPlusPlus.core.material.MaterialMisc.SOLAR_SALT_HOT.setFluid(hot);
        List<CriticalCompoundRuntime.FakeHatch> salts=new ArrayList<>();
        for(int i=0;i<100;i++)salts.add(CriticalCompoundRuntime.hatch(unsafe,tile,new FluidStack(cold,1),Integer.MAX_VALUE));
        for(var hatch:waters)hatch.stock=Integer.MAX_VALUE;
        cryo.stock=40000;helium.stock=360;
        List<CriticalCompoundRuntime.FakeHatch> all=new ArrayList<>(List.of(cryo,helium));all.addAll(waters);all.addAll(salts);
        CriticalCompoundRuntime.set(host,"mInputHatches",all);
        Object salt=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,true,false);
        Object water=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false);
        check(((BigInteger)field(salt,"equivalentEU")).equals(((BigInteger)field(water,"equivalentEU")).multiply(b(2)))
            &&field(salt,"coolantLitres").equals(b(40000))&&field(salt,"exciterLitres").equals(b(360)),
            "real hot-salt planner gives exactly2x paid water route with2x coolant/He charge");
        Output output=(Output)unsafe.allocateInstance(Output.class);output.stored=BigInteger.ZERO;
        output.setBaseMetaTileEntity(tile);
        CriticalCompoundRuntime.set(host,"mOutputHatches",new ArrayList<>(List.of(output)));
        CriticalCompoundRuntime.set(host,"mInputBusses",new ArrayList<>());
        CriticalCompoundRuntime.set(host,"meSnapshotTick",Long.MIN_VALUE);
        EnergyState state=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        state.n=new NBTTagCompound();state.n.setInteger("steamMode",5);state.n.setBoolean("solarSaltMode",true);
        state.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        CriticalCompoundRuntime.set(state.worldObj,"provider",unsafe.allocateInstance(net.minecraft.world.WorldProviderSurface.class));
        Method start=CriticalEnergyRuntime.class.getDeclaredMethod("startSolar",CriticalMachineState.class,
            MTECriticalMachine.class,NBTTagCompound.class);start.setAccessible(true);
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("solarPolicy")==3
            &&state.n.getBoolean("solarBatchSaltMode")&&cryo.stock==0&&helium.stock==0,
            "actual startSolar freezes selected salt mode and pays full400ticks of modifiers");
        check(waters.stream().allMatch(hatch->hatch.stock==Integer.MAX_VALUE)
            &&salts.stream().anyMatch(hatch->hatch.stock<Integer.MAX_VALUE),
            "salt mode debits cold salt only even when full water budget is present");
        BigInteger frozen=new BigInteger(state.n.getTagList("outputs",10).getCompoundTagAt(0).getString("exactAmount"));
        NBTTagCompound frozenManifest=(NBTTagCompound)state.n.copy();
        state.n=(NBTTagCompound)state.n.copy();state.n.setBoolean("solarSaltMode",false);state.n.setInteger("steamMode",0);
        state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        check(!state.n.getBoolean("funded")&&output.stored.equals(frozen)&&output.fluid.isFluidEqual(new FluidStack(hot,1)),
            "mode switch and saved-NBT recovery deliver original paid hot-salt output without repricing");
        for(int oldPolicy:new int[]{0,2}){
            state.n=(NBTTagCompound)frozenManifest.copy();
            if(oldPolicy==0)state.n.removeTag("solarPolicy");else state.n.setInteger("solarPolicy",oldPolicy);
            state.n.setInteger("progress",399);state.n.setBoolean("solarSaltMode",false);state.n.setInteger("steamMode",0);
            BigInteger storedBefore=output.stored;
            CriticalEnergyRuntime.tick(state,host);
            check(!state.n.getBoolean("funded")&&state.n.getInteger("solarPolicy")==oldPolicy
                &&output.stored.subtract(storedBefore).equals(frozen)&&cryo.stock==0&&helium.stock==0,
                "already-paid legacy solar policy"+oldPolicy+" delivers original frozen output without buying new activators or migration");
        }
        // A native GT205 registry anchors the mature LHE route to the production upstream ratio.
        gregtech.api.registries.LHECoolantRegistry.registerBaseCoolants();
        var nativeSalt=gregtech.api.registries.LHECoolantRegistry.getCoolant("molten.solarsalthot");
        check(nativeSalt.steamMultiplier==2.5D&&nativeSalt.superheatedThreshold==1D/25D,
            "GT205 real LHE registry retains2.5 steam multiplier and1/25SH threshold");
        CriticalCompoundRuntime.set(host,"mOutputHatches",new ArrayList<>());
    }
    static void pureMath() {
        var cryo=CriticalSolarPolicy.COOLANTS.get(1); var he=CriticalSolarPolicy.EXCITERS.get(1);
        BigInteger target=b(CriticalSolarPolicy.MAX_VOLTAGE).multiply(b(864));
        check(CriticalSolarPolicy.ratedWaterEUPerTick(.6D,cryo,he).equals(target), "private cryo/helium reaches864A MAX");
        check(CriticalSolarPolicy.MAX_VOLTAGE==gregtech.api.enums.GTValues.V[14],"MAX voltage agrees with loaded authoritative GT205");
        check(CriticalSolarPolicy.ratedWaterEUPerTick(100D,CriticalSolarPolicy.COOLANTS.get(2),
            CriticalSolarPolicy.EXCITERS.get(5)).equals(b(CriticalSolarPolicy.MAX_VOLTAGE).multiply(b(4096))),
            "absolute4096A cap is after dimension and modifier factors");
        BigInteger budget=CriticalSolarPolicy.batchBudgetEU(.6D,cryo,he,false);
        check(CriticalSolarPolicy.batchBudgetEU(.6D,cryo,he,true).equals(budget.multiply(b(2))), "salt route gets exactly2x shared mode budget");
        check(CriticalSolarPolicy.paidLitres(he,budget,budget).equals(b(180))
            && CriticalSolarPolicy.paidLitres(he,budget.multiply(b(2)),budget).equals(b(360)), "water9L/s and salt18L/s helium");
        BigInteger common=CriticalSolarPolicy.waterHalfHeat(budget);
        for(int mode=0;mode<6;mode++) {
            BigInteger volume=CriticalSolarPolicy.steamLitres(common,mode);
            BigInteger[] inputs=new BigInteger[]{BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO};
            inputs[mode]=volume;
            var batch=CriticalEnergyMath.turbine(inputs,0);
            check(batch.rawHeat.equals(common), "same heat for steam mode"+mode);
            check(CriticalEnergyMath.targetWarmupTicks(batch.rawHeat)==CriticalEnergyMath.targetWarmupTicks(common),
                "absolute EU load target is identical for steam mode"+mode);
            check(batch.eu.multiply(b(20)).equals(common.multiply(b(10))), "full overlimit energy for steam mode"+mode);
            BigInteger expected=volume.multiply(b(mode<3?1:1000)).divide(b(160));
            check(batch.water.equals(expected), "complete cascade condensation for steam mode"+mode);
        }
        BigInteger salt=CriticalSolarPolicy.saltLitres(budget.multiply(b(2)));
        check(salt.multiply(b(30000)).equals(common.multiply(b(20))),
            "full salt output is exactly twice actual water output after common quantum");
        check(salt.multiply(b(1000)).multiply(CriticalEnergyMath.steamHeatPerL(1)).multiply(b(10))
            .equals(salt.multiply(b(30000))), "real GT205 hot-salt->1000SH steam chain preserves accounting");
        check(CriticalEnergyMath.runningEfficiency(0)==100 && CriticalEnergyMath.runningEfficiency(3600)==2000,
            "visible1x to20x at3600 paid work ticks");
        check(CriticalEnergyMath.targetWarmupTicks(BigInteger.ONE)==0
            &&CriticalEnergyMath.targetWarmupTicks(fullTurbineHeat().divide(b(2)))==1800
            &&CriticalEnergyMath.targetWarmupTicks(fullTurbineHeat())==3600,
            "one litre normal steam cannot preheat; half/full absolute16A MAX base-EU targets are1800/3600");
        int current=100;
        for(int i=0;i<1000;i++) current=CriticalEnergyMath.trendEfficiency(current,b(i%2==0?100:90),b(i%2==0?90:100));
        check(current==100,"100/90 oscillation cannot mint rotor efficiency");
    }
    static void frozenPulse(MTECriticalMachine host, UUID owner) throws Exception {
        EnergyState state=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        state.n=new NBTTagCompound();state.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        ((EnergyClock)state.worldObj).clock=10;
        state.n.setString("owner",owner.toString()); state.n.setInteger("efficiency",1900);
        Method fund=CriticalEnergyRuntime.class.getDeclaredMethod("fundAdaptive",CriticalMachineState.class,
            NBTTagCompound.class,List.class,List.class,BigInteger.class,int.class);fund.setAccessible(true);
        BigInteger fullHeat=fullTurbineHeat();
        check((boolean)fund.invoke(null,state,state.n,new ArrayList<>(),new ArrayList<>(),fullHeat,0),"fresh paid turbine batch starts");
        check(new BigInteger(state.n.getString("eu")).equals(fullHeat.divide(b(2))) && state.n.getInteger("efficiency")==100
            &&state.n.getInteger("turbinePolicy")==3&&state.n.getInteger("turbineTargetWarmupTicks")==3600,
            "new policy resets unearned legacy trend warmth only when funding new batch");
        String frozen=state.n.getString("eu");
        for(int i=0;i<399;i++){((EnergyClock)state.worldObj).clock++;CriticalEnergyRuntime.tick(state,host);}
        check(state.n.getString("eu").equals(frozen) && state.n.getInteger("progress")==399
            && state.n.getInteger("turbineRunTicks")==399 && state.n.getInteger("efficiency")>100,
            "399 paid work ticks visibly warm rotor without repricing frozen batch EU");
        NBTTagCompound saved=(NBTTagCompound)state.n.copy();state.n=saved;
        gregtech.common.misc.GlobalEnergyWorldSavedData.INSTANCE=new gregtech.common.misc.GlobalEnergyWorldSavedData();
        gregtech.common.misc.WirelessNetworkManager.strongCheckOrAddUser(owner);
        ((EnergyClock)state.worldObj).clock++;CriticalEnergyRuntime.tick(state,host);
        check(!state.n.getBoolean("funded") && state.n.getInteger("turbineRunTicks")==400
            && gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).equals(fullHeat.divide(b(2))),
            "400th tick settles saved frozen batch at original1x while rotor reached current warmth");
        ((EnergyClock)state.worldObj).clock+=2;CriticalEnergyRuntime.idleTick(state);
        check(state.n.getInteger("turbineRunTicks")==399,"missing paid work decays rotor warmth");
        state.n=new NBTTagCompound();state.n.setBoolean("funded",true);state.n.setInteger("progress",399);
        state.n.setString("owner",owner.toString());state.n.setString("eu","12345678901234567890");
        state.n.setInteger("efficiency",1900);((EnergyClock)state.worldObj).clock++;
        BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before)
            .equals(new BigInteger("12345678901234567890")) && !state.n.hasKey("turbinePolicy"),
            "legacy already funded batch settles original BigInteger EU without policy migration");
        EnergyState steady=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        EnergyState changing=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        for(EnergyState s:List.of(steady,changing)){
            s.n=new NBTTagCompound();s.n.setString("owner",owner.toString());
            s.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        }
        for(int batch=0;batch<9;batch++){
            fund.invoke(null,steady,steady.n,new ArrayList<>(),new ArrayList<>(),fullHeat.multiply(b(10)),0);
            fund.invoke(null,changing,changing.n,new ArrayList<>(),new ArrayList<>(),fullHeat.multiply(b(batch%2==0?10:9)),0);
            for(int tick=0;tick<400;tick++) for(EnergyState s:List.of(steady,changing)){
                ((EnergyClock)s.worldObj).clock++;CriticalEnergyRuntime.tick(s,host);
            }
            check(steady.n.getInteger("turbineRunTicks")==changing.n.getInteger("turbineRunTicks")
                &&steady.n.getInteger("efficiency")==changing.n.getInteger("efficiency"),
                "actual steady and100/90 oscillating funded batches have identical rotor warmth after batch"+batch);
        }
        check(steady.n.getInteger("turbineRunTicks")==3600&&steady.n.getInteger("efficiency")==2000,
            "actual nine400tick funded batches reach20x in180seconds, no oscillation advantage");
        fund.invoke(null,steady,steady.n,new ArrayList<>(),new ArrayList<>(),BigInteger.ONE,0);
        String frozenLow=steady.n.getString("eu");
        check(steady.n.getInteger("turbineTargetWarmupTicks")==0&&steady.n.getInteger("efficiency")==2000,
            "switching a hot rotor to1L normal steam freezes current bill but sets absolute warmth target0");
        boolean lowBillUnchanged=true;
        for(int tick=0;tick<400;tick++){
            lowBillUnchanged &= steady.n.getString("eu").equals(frozenLow);
            ((EnergyClock)steady.worldObj).clock++;CriticalEnergyRuntime.tick(steady,host);
        }
        check(lowBillUnchanged,"all400 low-load decay ticks preserve the exact prepaid EU bill");
        check(steady.n.getInteger("turbineRunTicks")==3200,
            "1L steam cannot maintain full rotor warmth and loses400ticks in one paid batch");
        EnergyState cheap=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        cheap.n=new NBTTagCompound();cheap.n.setString("owner",owner.toString());
        cheap.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        for(int batch=0;batch<10;batch++){
            fund.invoke(null,cheap,cheap.n,new ArrayList<>(),new ArrayList<>(),BigInteger.ONE,0);
            for(int tick=0;tick<400;tick++){((EnergyClock)cheap.worldObj).clock++;CriticalEnergyRuntime.tick(cheap,host);}
        }
        check(cheap.n.getInteger("turbineRunTicks")==0&&cheap.n.getInteger("efficiency")==100,
            "ten paid1L ordinary-steam batches cannot preheat a cold rotor or carry20x into giant steam");
        for(EnergyState s:List.of(steady,changing)){
            s.n=new NBTTagCompound();s.n.setString("owner",owner.toString());
        }
        for(int batch=0;batch<10;batch++){
            fund.invoke(null,steady,steady.n,new ArrayList<>(),new ArrayList<>(),fullHeat.divide(b(2)),0);
            fund.invoke(null,changing,changing.n,new ArrayList<>(),new ArrayList<>(),
                fullHeat.multiply(b(batch%2==0?50:45)).divide(b(100)),0);
            boolean neverBetter=true;
            for(int tick=0;tick<400;tick++){
                for(EnergyState s:List.of(steady,changing)){((EnergyClock)s.worldObj).clock++;CriticalEnergyRuntime.tick(s,host);}
                neverBetter &= changing.n.getInteger("turbineRunTicks")<=steady.n.getInteger("turbineRunTicks");
            }
            check(neverBetter,"below-threshold100/90 heat oscillation never outwarms continuous higher heat, batch"+batch);
        }
        check(steady.n.getInteger("turbineRunTicks")==1800&&changing.n.getInteger("turbineRunTicks")<=1800,
            "sub-threshold stable load converges to its1800tick absolute target and cannot mint20x");
    }
    static class EnergyState extends CriticalCompoundRuntime.FakeState {
        @Override public CriticalMachineKind getMachineKind(){return CriticalMachineKind.TURBINE;}
    }
    static class EnergyClock extends CriticalCompoundRuntime.ClockWorld {
        long clock;
        @Override public long getTotalWorldTime(){return clock;}
    }
    static class Output extends gregtech.api.metatileentity.implementations.MTEHatchOutput {
        BigInteger stored;FluidStack fluid;
        Output(){super("unused",1,new String[0],null);}
        @Override public boolean isValid(){return true;}
        @Override public gregtech.api.interfaces.IOutputHatchTransaction createTransaction(){
            return new gregtech.api.interfaces.IOutputHatchTransaction(){
                BigInteger pending=BigInteger.ZERO;FluidStack type;
                public gregtech.api.interfaces.IOutputHatch getHatch(){return Output.this;}
                public boolean isFiltered(){return false;}
                public boolean isFilteredTo(gregtech.api.util.GTUtility.FluidId id){return false;}
                public boolean hasAvailableSpace(){return true;}
                public boolean storePartial(gregtech.api.util.GTUtility.FluidId id,FluidStack stack,long total,long parallel){
                    type=stack.copy();pending=pending.add(b(stack.amount));stack.amount=0;return true;}
                public void complete(gregtech.api.util.GTUtility.FluidId id){}
                public void commit(){stored=stored.add(pending);fluid=type;}
            };
        }
    }
}
