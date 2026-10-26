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
    static final BigInteger FLUID_STOCK=b(Integer.MAX_VALUE).multiply(b(100));
    static BigInteger routeEU(double factor,int coolant,int exciter,boolean salt) {
        var cooling=CriticalSolarPolicy.COOLANTS.get(coolant);
        var activation=CriticalSolarPolicy.EXCITERS.get(exciter);
        return salt?CriticalSolarPolicy.batchHotSaltLitres(FLUID_STOCK,factor,cooling,activation).multiply(b(30000))
            :CriticalSolarPolicy.batchDenseSupercriticalLitres(FLUID_STOCK,factor,cooling,activation).multiply(b(50000));
    }
    static BigInteger expectedCost(CriticalSolarPolicy.Entry entry,BigInteger actualEU) {
        BigInteger numerator=actualEU.multiply(b((long)entry.litresPerSecond));
        BigInteger denominator=b(CriticalSolarPolicy.REFERENCE_WATER_HEAT_EUT).multiply(b(20*400));
        return numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator).multiply(b(20));
    }
    static int routeCost(boolean coolantCharge,double factor,int coolant,int exciter,boolean salt) {
        return expectedCost(coolantCharge?CriticalSolarPolicy.COOLANTS.get(coolant):CriticalSolarPolicy.EXCITERS.get(exciter),
            routeEU(factor,coolant,exciter,salt)).intValueExact();
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
        bulkMath();
        seed("SuperCoolant", "gtsr_solar_supercoolant", "mFluid");
        seed("Time", "gtsr_solar_tachyon", "mStandardMoltenFluid");
        seed("Space", "gtsr_solar_space", "mStandardMoltenFluid");
        seed("Helium", "gtsr_solar_he", "mPlasma");
        seed("Helium", "gtsr_solar_he_gas", "mGas");
        seed("Iron", "gtsr_solar_fe", "mPlasma");
        seed("Americium", "gtsr_solar_am", "mPlasma");
        seed("Steam", "steam", "mGas");
        FluidRegistry.registerFluid(new Fluid("ic2superheatedsteam"));
        FluidRegistry.registerFluid(new Fluid("supercriticalsteam"));
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
        int cryoFull=routeCost(true,.6D,1,1,false),heliumFull=routeCost(false,.6D,1,1,false);
        int gasFull=routeCost(false,.6D,1,0,false),baseFull=routeCost(true,.6D,0,0,false);
        int baseGas=routeCost(false,.6D,0,0,false);
        var cryo = CriticalCompoundRuntime.hatch(unsafe, tile, new FluidStack(FluidRegistry.getFluid("cryotheum"),1), cryoFull);
        var helium = CriticalCompoundRuntime.hatch(unsafe, tile, Materials.Helium.getPlasma(1), heliumFull);
        var heliumGas = CriticalCompoundRuntime.hatch(unsafe, tile, Materials.Helium.getGas(1), gasFull);
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
        check(field(selection,"coolantLitres").equals(b(cryoFull)) && field(selection,"exciterLitres").equals(b(heliumFull)),
            "inventory-saturated private cryo/He prepays both actual-heat-scaled20second medium costs");
        BigInteger chosenHeat=routeEU(.3D,1,1,false);
        for(int steamMode=0;steamMode<6;steamMode++) {
            Object sameStock=select.invoke(null,snapshot,.3D,steamMode,false,false);
            BigInteger volume=(BigInteger)field(sameStock,"outputLitres");
            check(field(sameStock,"equivalentEU").equals(chosenHeat)
                &&volume.multiply(CriticalEnergyMath.steamHeatPerL(steamMode)).multiply(b(10)).equals(chosenHeat)
                &&field(sameStock,"inputLitres").equals(CriticalSolarPolicy.waterLitres(volume,steamMode)),
                "real planner shares actual native heat and debits only corresponding water for steam"+steamMode);
        }
        for(int steamMode=0;steamMode<6;steamMode++) {
            Object irregular=select.invoke(null,snapshot,.123456789D,steamMode,false,false);
            check(field(irregular,"equivalentEU").equals(b(2274286242L).multiply(b(50000))),
                "runtime retains exact decimal dimension factor until final3L quantisation for steam"+steamMode);
        }
        cryo.stock=Integer.MAX_VALUE;helium.stock=Integer.MAX_VALUE;
        List<CriticalCompoundRuntime.FakeHatch> largeStock=new ArrayList<>(hatches);
        for(int i=0;i<900;i++) largeStock.add(CriticalCompoundRuntime.hatch(unsafe,tile,
            gregtech.api.util.GTModHandler.getDistilledWater(1),Integer.MAX_VALUE));
        CriticalCompoundRuntime.set(host,"mInputHatches",largeStock);
        BigInteger previousUncapped=BigInteger.ZERO;
        for(double highDimension:new double[]{7D,8D}) {
            BigInteger actualEU=CriticalSolarPolicy.batchDenseSupercriticalLitres(FLUID_STOCK.multiply(b(10)),highDimension,
                CriticalSolarPolicy.COOLANTS.get(1),CriticalSolarPolicy.EXCITERS.get(1)).multiply(b(50000));
            check(actualEU.compareTo(previousUncapped)>0,"fixed-stock DSC output increases above former global cap:"+highDimension);
            Object uncapped=select.invoke(null,CriticalEnergyPorts.snapshot(host),highDimension,5,false,false);
            check(field(uncapped,"equivalentEU").equals(actualEU)
                &&field(uncapped,"coolantLitres").equals(expectedCost(CriticalSolarPolicy.COOLANTS.get(1),actualEU))
                &&field(uncapped,"exciterLitres").equals(expectedCost(CriticalSolarPolicy.EXCITERS.get(1),actualEU)),
                "real planner preserves uncapped output when water and both media fund a complete batch");
            check(select.invoke(null,CriticalEnergyPorts.snapshot(host),highDimension,0,false,false)==null,
                "ordinary-steam full batch cannot overdraw water, reduce output or throw SnapshotShortage");
            Object irradiation=select.invoke(null,CriticalEnergyPorts.snapshot(host),highDimension,0,false,true);
            check(irradiation!=null&&field(irradiation,"inputLitres").equals(BigInteger.ZERO)
                &&field(irradiation,"outputLitres").equals(BigInteger.ZERO)
                &&field(irradiation,"equivalentEU").equals(BigInteger.ZERO),
                "insufficient water preserves independent dust-only operation without consuming water");
            previousUncapped=actualEU;
        }
        CriticalCompoundRuntime.set(host,"mInputHatches",hatches);
        cryo.stock=cryoFull;helium.stock=heliumFull;
        List<CriticalEnergyPorts.Debit> debits = new ArrayList<>();
        var allocation = new CriticalEnergyPorts.Allocation(snapshot);
        debits.addAll(allocation.take((FluidStack)field(selection,"input"),(BigInteger)field(selection,"inputLitres")));
        debits.addAll(allocation.take(cryo.fluid,(BigInteger)field(selection,"coolantLitres")));
        debits.addAll(allocation.take(helium.fluid,(BigInteger)field(selection,"exciterLitres")));
        check(CriticalEnergyPorts.debit(debits) && cryo.stock == 0 && helium.stock == 0 && water.stock < Integer.MAX_VALUE,
            "native hatch drain receipts actually pay work fluid, coolant and exciter");
        cryo.stock = cryoFull; helium.stock = heliumFull-1; for(var hatch:waters) hatch.stock=Integer.MAX_VALUE;
        selection = select.invoke(null, CriticalEnergyPorts.snapshot(host), 0.6D, 5, false, false);
        check(selection != null && ((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium_gas")
            && helium.stock == heliumFull-1, "one litre short of helium plasma falls back to fully payable helium gas");
        heliumGas.stock=0;helium.stock=0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false)==null,
            "water and full coolant cannot run without any mandatory activator");
        var supercoolant=CriticalCompoundRuntime.hatch(unsafe,tile,Materials.SuperCoolant.getFluid(1),baseFull);
        List<CriticalCompoundRuntime.FakeHatch> baseInputs=new ArrayList<>(List.of(supercoolant,heliumGas,helium));baseInputs.addAll(waters);
        CriticalCompoundRuntime.set(host,"mInputHatches",baseInputs);
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false)==null,
            "supercoolant alone cannot bypass the separate activator condition");
        heliumGas.stock=baseGas;
        snapshot=CriticalEnergyPorts.snapshot(host);
        selection=select.invoke(null,snapshot,.6D,5,false,false);
        check(selection!=null&&((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium_gas")
            &&field(selection,"exciterLitres").equals(b(baseGas))&&field(selection,"coolantLitres").equals(b(baseFull)),
            "baseline supercoolant plus helium gas has two separately paid actual-heat requirements");
        debits=new ArrayList<>();allocation=new CriticalEnergyPorts.Allocation(snapshot);
        debits.addAll(allocation.take((FluidStack)field(selection,"input"),(BigInteger)field(selection,"inputLitres")));
        debits.addAll(allocation.take(supercoolant.fluid,(BigInteger)field(selection,"coolantLitres")));
        debits.addAll(allocation.take(heliumGas.fluid,(BigInteger)field(selection,"exciterLitres")));
        check(CriticalEnergyPorts.debit(debits)&&supercoolant.stock==0&&heliumGas.stock==0&&helium.stock==0,
            "native hatch receipts debit helium gas once, never substitute or double-charge plasma");
        CriticalCompoundRuntime.set(host,"mInputHatches",hatches);heliumGas.stock=gasFull;helium.stock=heliumFull-1;
        cryo.stock = 0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),0.6D,5,false,true) == null,
            "neither water nor dust-only work bypasses mandatory coolant");
        BigInteger dustGasWork=CriticalSolarPolicy.irradiationBudgetEU(CriticalSolarPolicy.batchBudgetEU(.6D,
            CriticalSolarPolicy.COOLANTS.get(1),CriticalSolarPolicy.EXCITERS.get(0),false));
        int dustGasCost=expectedCost(CriticalSolarPolicy.COOLANTS.get(1),dustGasWork).intValueExact();
        int dustGasActivationCost=expectedCost(CriticalSolarPolicy.EXCITERS.get(0),dustGasWork).intValueExact();
        cryo.stock = dustGasCost; for(var hatch:waters) hatch.stock=0;
        selection = select.invoke(null,CriticalEnergyPorts.snapshot(host),0.6D,5,false,true);
        check(selection != null && field(selection,"coolantLitres").equals(b(dustGasCost))
            && field(selection,"exciterLitres").equals(b(dustGasActivationCost))&&field(selection,"equivalentEU").equals(BigInteger.ZERO),
            "dust-only pays both media against its explicit1/256 power-equivalent work, no imaginary energy");
        heliumGas.stock=0;helium.stock=0;
        check(select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,true)==null,
            "dust-only cannot bypass the mandatory activator when coolant is present");
        BigInteger dustPlasmaWork=CriticalSolarPolicy.irradiationBudgetEU(CriticalSolarPolicy.batchBudgetEU(.6D,
            CriticalSolarPolicy.COOLANTS.get(1),CriticalSolarPolicy.EXCITERS.get(1),false));
        int dustPlasmaCost=expectedCost(CriticalSolarPolicy.EXCITERS.get(1),dustPlasmaWork).intValueExact();
        int dustCryoCost=expectedCost(CriticalSolarPolicy.COOLANTS.get(1),dustPlasmaWork).intValueExact();
        helium.stock=dustPlasmaCost;cryo.stock=dustCryoCost;
        selection=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,true);
        check(selection!=null&&((CriticalSolarPolicy.Entry)field(selection,"exciter")).id.equals("helium")
            &&field(selection,"exciterLitres").equals(b(dustPlasmaCost)),"dust-only accepts and pays helium plasma without requiring extra helium gas");
        cryo.stock = dustCryoCost-1;
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
        cryo.stock=routeCost(true,.6D,1,1,true);helium.stock=routeCost(false,.6D,1,1,true);
        List<CriticalCompoundRuntime.FakeHatch> all=new ArrayList<>(List.of(cryo,helium));all.addAll(waters);all.addAll(salts);
        CriticalCompoundRuntime.set(host,"mInputHatches",all);
        Object salt=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,true,false);
        Object water=select.invoke(null,CriticalEnergyPorts.snapshot(host),.6D,5,false,false);
        check(field(salt,"equivalentEU").equals(routeEU(.6D,1,1,true))
            &&field(water,"equivalentEU").equals(routeEU(.6D,1,1,false))
            &&field(salt,"coolantLitres").equals(b(cryo.stock))&&field(salt,"exciterLitres").equals(b(helium.stock)),
            "water and salt planners use actual independent inventory saturation and pay exact actual heat");
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
        state.n.setBoolean("solarSaltMode",false);state.n.setInteger("steamMode",0);
        int cryoBefore=cryo.stock,heliumBefore=helium.stock;
        check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.getBoolean("funded")
            &&cryo.stock==cryoBefore&&helium.stock==heliumBefore
            &&waters.stream().allMatch(hatch->hatch.stock==Integer.MAX_VALUE),
            "actual unaffordable water start returns without debit, clipping, or SnapshotShortage");
        state.n.setBoolean("solarSaltMode",true);state.n.setInteger("steamMode",5);
        cryo.stock=routeCost(true,1D,1,1,true);helium.stock=routeCost(false,1D,1,1,true);
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("solarPolicy")==7
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
        for(int oldPolicy:new int[]{0,2,3,4,5,6}){
            state.n=(NBTTagCompound)frozenManifest.copy();
            if(oldPolicy==0)state.n.removeTag("solarPolicy");else state.n.setInteger("solarPolicy",oldPolicy);
            state.n.setInteger("progress",398);state.n.setBoolean("solarSaltMode",false);state.n.setInteger("steamMode",0);
            String frozenInputs=state.n.getTagList("inputs",10).toString();
            String frozenOutputs=state.n.getTagList("outputs",10).toString();
            String frozenEU=state.n.getString("eu");
            CriticalEnergyRuntime.tick(state,host);
            check(state.n.getInteger("progress")==399&&state.n.getString("eu").equals(frozenEU)
                &&state.n.getTagList("inputs",10).toString().equals(frozenInputs)
                &&state.n.getTagList("outputs",10).toString().equals(frozenOutputs),
                "legacy paid solar policy"+oldPolicy+" keeps exact inputs, outputs and EU frozen during work");
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
    static void bulkMath() {
        var cryo=CriticalSolarPolicy.COOLANTS.get(1);var he=CriticalSolarPolicy.EXCITERS.get(1);
        BigInteger stock=b(100000000000L);
        BigInteger dsc=CriticalSolarPolicy.batchDenseSupercriticalLitres(stock,.6D,cryo,he);
        BigInteger salt=CriticalSolarPolicy.batchHotSaltLitres(stock,.6D,cryo,he);
        check(CriticalSolarPolicy.VERSION==7&&dsc.equals(b(8100000000L))&&salt.equals(b(27000000000L)),
            "policy7 private1.35 multiplier at100G stock yields8.1G DSC or27G hot salt per20s batch");
        check(CriticalSolarPolicy.waterLitres(dsc,5).equals(b(50625000000L))
            &&dsc.multiply(b(2500)).equals(b(20250000000000L))
            &&salt.multiply(b(1500)).equals(b(40500000000000L)),
            "100G example pays50.625G water and carries20.25T/40.5T native EU");
        check(CriticalSolarPolicy.costLitres(cryo,dsc.multiply(b(50000))).equals(b(214300))
            &&CriticalSolarPolicy.costLitres(he,dsc.multiply(b(50000))).equals(b(8580)),
            "unchanged integer-second fee charges Cryo214300 and helium plasma8580 per reference example");
        BigInteger halfHeat=dsc.multiply(b(5000));
        check(CriticalSolarPolicy.waterLitres(CriticalSolarPolicy.steamLitres(halfHeat,0),0).equals(b(253125000000L)),
            "ordinary steam at the same heat needs253.125G water and cannot run on100G stock");
        BigInteger fullStock=b(1000000000000L);
        halfHeat=CriticalSolarPolicy.batchDenseSupercriticalLitres(fullStock,.6D,cryo,he).multiply(b(5000));
        for(int mode=0;mode<6;mode++){
            BigInteger volume=CriticalSolarPolicy.steamLitres(halfHeat,mode);
            check(volume.multiply(CriticalEnergyMath.steamHeatPerL(mode)).equals(halfHeat)
                &&CriticalSolarPolicy.waterLitres(volume,mode).compareTo(fullStock)<=0,
                "six fully payable steam choices preserve identical native heat:"+mode);
        }
        for(double invalid:new double[]{0D,-1D,Double.NaN,Double.POSITIVE_INFINITY})
            check(CriticalSolarPolicy.batchDenseSupercriticalLitres(stock,invalid,cryo,he).signum()==0
                &&CriticalSolarPolicy.batchHotSaltLitres(stock,invalid,cryo,he).signum()==0,
                "invalid dimension rejects new batch output:"+invalid);
        BigInteger previous=BigInteger.ZERO;
        for(BigInteger x:List.of(BigInteger.ZERO,BigInteger.ONE,b(100),stock,b(Long.MAX_VALUE).add(BigInteger.ONE),BigInteger.TEN.pow(1000))){
            BigInteger volume=CriticalSolarPolicy.batchDenseSupercriticalLitres(x,.6D,cryo,he);
            BigInteger hot=CriticalSolarPolicy.batchHotSaltLitres(x,.6D,cryo,he);
            check(volume.compareTo(previous)>=0&&volume.mod(b(3)).signum()==0
                &&volume.compareTo(b(16200000000L))<0&&hot.compareTo(b(54000000000L))<0,
                "bulk curve remains monotone, quantised and exact for zero, tiny and beyond-long inventory");
            previous=volume;
        }
        check(CriticalSolarPolicy.batchDenseSupercriticalLitres(stock,.123456789D,cryo,he).equals(b(1666666650L))
            &&CriticalSolarPolicy.batchHotSaltLitres(stock,.123456789D,cryo,he).equals(b(5555555505L)),
            "odd dimension uses exact decimal factors without rounding rated power first");
        var tachyon=CriticalSolarPolicy.COOLANTS.get(2);var spatial=CriticalSolarPolicy.EXCITERS.get(5);
        check(CriticalSolarPolicy.batchDenseSupercriticalLitres(stock,8D,tachyon,spatial)
                .compareTo(CriticalSolarPolicy.batchDenseSupercriticalLitres(stock,7D,tachyon,spatial))>0
            &&CriticalSolarPolicy.batchHotSaltLitres(BigInteger.TEN.pow(1000),Double.MAX_VALUE,tachyon,spatial)
                .compareTo(b(Long.MAX_VALUE))>0,
            "new production has no former global heat cap or long storage cap");
    }
    static void pureMath() {
        var cryo=CriticalSolarPolicy.COOLANTS.get(1); var he=CriticalSolarPolicy.EXCITERS.get(1);
        BigInteger target=b(CriticalSolarPolicy.REFERENCE_WATER_HEAT_EUT).multiply(b(20));
        check(CriticalSolarPolicy.ratedWaterEUPerTick(.6D,cryo,he).equals(target), "private cryo/helium has94500000000 native heat EU/t");
        check(CriticalSolarPolicy.MAX_VOLTAGE==gregtech.api.enums.GTValues.V[14],"MAX voltage agrees with loaded authoritative GT205");
        var tachyon=CriticalSolarPolicy.COOLANTS.get(2); var spatial=CriticalSolarPolicy.EXCITERS.get(5);
        check(CriticalSolarPolicy.ratedWaterHeatEUPerTick(1D,tachyon,spatial).equals(b(1400000000000L))
            &&CriticalSolarPolicy.ratedWaterHeatEUPerTick(2D,tachyon,spatial).equals(b(2800000000000L)),
            "dimension1 and2 strongest media keep growing at1400G and2800G beyond former440G cap");
        check(CriticalSolarPolicy.ratedWaterHeatEUPerTick(7D,CriticalSolarPolicy.COOLANTS.get(0),CriticalSolarPolicy.EXCITERS.get(0))
            .equals(b(490000000000L))&&CriticalSolarPolicy.ratedWaterHeatEUPerTick(8D,CriticalSolarPolicy.COOLANTS.get(0),CriticalSolarPolicy.EXCITERS.get(0))
            .equals(b(560000000000L)),"unboosted dimensions7 and8 also grow beyond the old cap without a plateau");
        check(CriticalSolarPolicy.ratedWaterHeatEUPerTick(Double.MAX_VALUE,tachyon,spatial).compareTo(b(Long.MAX_VALUE))>0,
            "finite dimension uses BigDecimal and BigInteger even beyond long instead of a safety cap");
        for(double invalid:new double[]{-1D,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            check(CriticalSolarPolicy.ratedWaterHeatEUPerTick(invalid,tachyon,spatial).signum()==0,
                "only nonpositive or nonfinite dimension is refused:"+invalid);
        BigInteger budget=CriticalSolarPolicy.batchBudgetEU(.6D,cryo,he,false);
        check(CriticalSolarPolicy.batchBudgetEU(.6D,cryo,he,true).equals(budget.multiply(b(2))), "salt route gets exactly2x shared mode budget");
        check(CriticalSolarPolicy.COOLANTS.get(0).referenceEUPerTick==199468339200L
            &&CriticalSolarPolicy.COOLANTS.get(1).referenceEUPerTick==365691955200L
            &&CriticalSolarPolicy.COOLANTS.get(2).referenceEUPerTick==664894464000L,
            "each coolant uses its own exact best native MkVI LNR power reference");
        List<CriticalSolarPolicy.Entry> catalogue=new ArrayList<>(CriticalSolarPolicy.COOLANTS);catalogue.addAll(CriticalSolarPolicy.EXCITERS);
        BigInteger referencePower=b(CriticalSolarPolicy.REFERENCE_WATER_HEAT_EUT).multiply(b(20));
        BigInteger referenceEU=referencePower.multiply(b(400));
        long[] integerRates={40000,20000,200,30000,800,800,800,800,200};
        check(CriticalSolarPolicy.REFERENCE_WATER_HEAT_EUT==94500000000L,"all medium fees share94500000000 native heat EU/t reference");
        int rateIndex=0;
        for(var entry:catalogue){
            BigInteger rate=b(integerRates[rateIndex++]);
            BigInteger expected=rate.multiply(b(20));
            check(entry.litresPerSecond==rate.longValueExact()&&CriticalSolarPolicy.costLitres(entry,referenceEU).equals(expected)
                &&CriticalSolarPolicy.costLitres(entry,referenceEU.multiply(b(2))).equals(expected.multiply(b(2))),
                "shared864A reference and double output use the exact user integer-second fee:"+entry.id);
            check(CriticalSolarPolicy.costLitres(entry,BigInteger.ONE).equals(b(20))
                &&CriticalSolarPolicy.costLitres(entry,referenceEU.divide(b(3))).mod(b(20)).signum()==0,
                "positive tiny work pays at least1L/s and all scaled bills are whole20second multiples:"+entry.id);
            BigInteger nativeDenominator=b((long)entry.nativeLitresPerSecond).multiply(referencePower);
            BigInteger ratioNumerator=rate.multiply(b(entry.referenceEUPerTick));
            check(ratioNumerator.multiply(b(2)).compareTo(nativeDenominator.multiply(b(7)))>0
                &&ratioNumerator.multiply(b(3)).compareTo(nativeDenominator.multiply(b(10)))>0,
                "rounded fee stays above3.5x native per-output cost and above2x after external5/3 gain:"+entry.id);
        }
        BigInteger fee=CriticalSolarPolicy.costLitres(he,budget);
        BigInteger doubleFee=CriticalSolarPolicy.costLitres(he,budget.multiply(b(2)));
        check(fee.equals(expectedCost(he,budget))&&doubleFee.equals(expectedCost(he,budget.multiply(b(2))))
            &&doubleFee.compareTo(fee.multiply(b(2)))<=0&&doubleFee.compareTo(fee.multiply(b(2)).subtract(b(20)))>=0,
            "actual output doubles medium cost within one whole litre-per-second ceiling step");
        check(CriticalSolarPolicy.paidLitres(he,budget,BigInteger.ONE).equals(fee)
            &&CriticalSolarPolicy.paidLitres(he,budget,budget.multiply(b(100))).equals(fee),
            "legacy ratedBudget argument cannot discount high dimensions or boosters");
        check(CriticalSolarPolicy.costLitres(cryo,budget).equals(b(400000))
            &&CriticalSolarPolicy.costLitres(he,budget).equals(b(16000))
            &&CriticalSolarPolicy.costLitres(cryo,budget.multiply(b(2))).equals(b(800000))
            &&CriticalSolarPolicy.costLitres(he,budget.multiply(b(2))).equals(b(32000)),
            "reference native heat pays Cryo20000/He800Lps and doubled salt heat pays twice");
        check(CriticalSolarPolicy.costLitres(he,referenceEU.divide(b(3))).equals(b(5340)),
            "one-third output rounds266.66Lps up to267Lps before20second prepayment");
        check(CriticalSolarPolicy.EXCITERS.get(0).nativeLitresPerSecond==2400D
            &&CriticalSolarPolicy.EXCITERS.get(0).referenceEUPerTick==664894464000L
            &&he.referenceEUPerTick==166223616000L&&he.nativeLitresPerSecond==20D,
            "helium gas uses explicit air-maintenance proxy while plasma uses the atomic-excitation reference");
        // Arbitrary dimensions must use the exact unrounded ceiling; quantise only the final actual heat.
        for(double dimension:new double[]{0D,1e-12D,.123456789D,.6D,7D,8D,100D,1e100D,Double.MAX_VALUE}) {
            BigInteger power=CriticalSolarPolicy.ratedWaterHeatEUPerTick(dimension,cryo,he);
            BigInteger ceiling=CriticalSolarPolicy.rawWaterHalfHeat(CriticalSolarPolicy.batchBudgetEU(dimension,cryo,he,false));
            check(ceiling.equals(power.multiply(b(800)))
                &&CriticalSolarPolicy.waterHalfSaturationStock(ceiling).equals(power.multiply(b(5))),
                "unrounded ceiling gives K=5P exactly for dimension"+dimension);
            for(BigInteger stock:List.of(BigInteger.ZERO,BigInteger.ONE,b(94),b(100),b(1000000),power.multiply(b(5)),
                power.multiply(b(45)),BigInteger.TEN.pow(1000))) {
                BigInteger denominator=stock.add(power.multiply(b(5))).multiply(b(7500));
                BigInteger expected=denominator.signum()==0?BigInteger.ZERO
                    :power.multiply(b(400)).multiply(stock).divide(denominator).multiply(b(15000));
                BigInteger actual=CriticalSolarPolicy.saturatedWaterHalfHeat(stock,ceiling);
                check(actual.equals(expected),"actual half-EU equals public native-EU formula for dimension"+dimension+" stock"+stock);
                for(int mode=0;mode<6;mode++) {
                    BigInteger volume=CriticalSolarPolicy.steamLitres(actual,mode);
                    check(volume.multiply(CriticalEnergyMath.steamHeatPerL(mode)).equals(expected)
                        &&CriticalSolarPolicy.waterLitres(volume,mode).compareTo(stock)<=0,
                        "arbitrary dimension formula preserves six-steam heat and physical water bounds:"+mode);
                }
            }
        }
        // Retain the public policy6 arithmetic API for callers holding legacy quotes.
        for(double dimension:new double[]{.123456789D,.6D,1D,2D,7D,100D,1e100D,Double.MAX_VALUE}) {
            BigInteger design=CriticalSolarPolicy.batchBudgetEU(dimension,tachyon,spatial,false);
            BigInteger nativePower=CriticalSolarPolicy.ratedWaterHeatEUPerTick(dimension,tachyon,spatial);
            BigInteger kStock=nativePower.multiply(b(5));
            BigInteger saltMax=kStock.multiply(b(8)).divide(b(75));
            check(CriticalSolarPolicy.saltLitres(design.multiply(b(2))).equals(saltMax),
                "direct salt M=floor8K/75 equals native twofold salt budget:"+dimension);
            for(BigInteger stock:List.of(BigInteger.ZERO,BigInteger.ONE,b(100),kStock,kStock.multiply(b(9)),
                b(Long.MAX_VALUE).add(BigInteger.ONE),BigInteger.TEN.pow(1000))) {
                BigInteger directDSC=kStock.multiply(stock).multiply(b(4)).divide(stock.add(kStock).multiply(b(375))).multiply(b(3));
                BigInteger halfHeat=CriticalSolarPolicy.saturatedWaterHalfHeat(stock,CriticalSolarPolicy.rawWaterHalfHeat(design));
                check(CriticalSolarPolicy.steamLitres(halfHeat,5).equals(directDSC)
                    &&CriticalSolarPolicy.waterLitres(directDSC,5).equals(directDSC.multiply(b(25)).add(b(3)).divide(b(4)))
                    &&halfHeat.equals(directDSC.multiply(b(5000))),
                    "direct DSC production, ceil25V/4 water and2500V native EU match runtime math:"+dimension);
                BigInteger directSalt=saltMax.multiply(stock).divide(stock.add(saltMax));
                check(CriticalSolarPolicy.saturatedSaltLitres(stock,saltMax).equals(directSalt)
                    &&CriticalSolarPolicy.costLitres(tachyon,directDSC.multiply(b(50000))).equals(expectedCost(tachyon,directDSC.multiply(b(50000))))
                    &&CriticalSolarPolicy.costLitres(spatial,directSalt.multiply(b(30000))).equals(expectedCost(spatial,directSalt.multiply(b(30000)))),
                    "uncapped stock and native water/salt heat keep exact existing medium fees:"+dimension);
            }
        }
        BigInteger maxHalfHeat=budget.divide(b(10));
        BigInteger k=CriticalSolarPolicy.waterHalfSaturationStock(maxHalfHeat);
        check(k.equals(b(472500000000L)),"private cryo/helium common half-saturation inventory is472500000000L");
        BigInteger common=CriticalSolarPolicy.saturatedWaterHalfHeat(k,maxHalfHeat);
        check(common.equals(b(37800000000000L)),"half-saturation batch contains18900000000000 native EU");
        check(CriticalSolarPolicy.costLitres(cryo,common.multiply(b(10))).equals(b(200000))
            &&CriticalSolarPolicy.costLitres(he,common.multiply(b(10))).equals(b(8000)),
            "worked half-saturation example prepays Cryo200000L and He8000L");
        BigInteger previous=BigInteger.ZERO;
        for(BigInteger stock:List.of(BigInteger.ZERO,BigInteger.ONE,b(93),b(94),b(95),b(100),b(1000000),k,k.multiply(b(9)),k.multiply(b(99)),BigInteger.TEN.pow(1000))) {
            BigInteger heat=CriticalSolarPolicy.saturatedWaterHalfHeat(stock,maxHalfHeat);
            check(heat.compareTo(previous)>=0&&heat.compareTo(maxHalfHeat)<0,"integer saturation is monotone and strictly bounded:"+stock);
            for(int mode=0;mode<6;mode++) {
                BigInteger volume=CriticalSolarPolicy.steamLitres(heat,mode);
                BigInteger water=CriticalSolarPolicy.waterLitres(volume,mode);
                check(volume.multiply(CriticalEnergyMath.steamHeatPerL(mode)).equals(heat)
                    &&water.compareTo(stock)<=0,
                    "all steam identities carry equal native heat without overdrawing inventory:"+mode);
            }
            previous=heat;
        }
        check(CriticalSolarPolicy.saturatedWaterHalfHeat(k.multiply(b(9)),maxHalfHeat).equals(maxHalfHeat.multiply(b(9)).divide(b(10)))
            &&CriticalSolarPolicy.saturatedWaterHalfHeat(k.multiply(b(99)),maxHalfHeat).equals(maxHalfHeat.multiply(b(99)).divide(b(100))),
            "nine and99 times half-saturation inventory give90 and99percent native heat");
        BigInteger tinySteam=CriticalSolarPolicy.steamLitres(CriticalSolarPolicy.saturatedWaterHalfHeat(b(94),maxHalfHeat),0);
        check(tinySteam.equals(b(15000))&&CriticalSolarPolicy.waterLitres(tinySteam,0).equals(b(94)),
            "94L tiny batch pays ceil93.75L honestly; no hidden leave-one-litre rule");
        BigInteger saltMaximum=CriticalSolarPolicy.saltLitres(budget.multiply(b(2)));
        check(saltMaximum.multiply(b(1500)).equals(budget.divide(b(10))),"salt native heat ceiling is twice water heat ceiling");
        check(CriticalSolarPolicy.saturatedSaltLitres(saltMaximum,saltMaximum).equals(saltMaximum.divide(b(2)))
            &&CriticalSolarPolicy.saturatedSaltLitres(BigInteger.TEN.pow(1000),saltMaximum).compareTo(saltMaximum)<0,
            "salt rational saturation converts one-for-one and never reaches its finite ceiling");
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
        check(salt.multiply(b(30000)).equals(maxHalfHeat.multiply(b(20))),
            "salt ceiling carries twice the water ceiling native heat");
        check(salt.multiply(b(1000)).multiply(CriticalEnergyMath.steamHeatPerL(1)).multiply(b(10))
            .equals(salt.multiply(b(30000))), "real GT205 hot-salt->1000SH steam chain preserves accounting");
        check(CriticalEnergyMath.TURBINE_WARMUP_TICKS==400&&CriticalEnergyMath.runningEfficiency(0)==100
            && CriticalEnergyMath.runningEfficiency(400)==2000,
            "visible1x to20x over one400paid-tick20second cycle");
        check(CriticalEnergyMath.targetWarmupTicks(BigInteger.ONE)==0
            &&CriticalEnergyMath.targetWarmupTicks(fullTurbineHeat().divide(b(2)))==200
            &&CriticalEnergyMath.targetWarmupTicks(fullTurbineHeat())==400,
            "one litre normal steam cannot preheat; half/full absolute16A MAX base-EU targets are200/400");
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
            &&state.n.getInteger("turbinePolicy")==4&&state.n.getInteger("turbineTargetWarmupTicks")==400,
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
            &&state.n.getInteger("efficiency")==2000
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
        state.n=new NBTTagCompound();state.n.setInteger("turbinePolicy",3);
        state.n.setInteger("turbineRunTicks",1800);state.n.setInteger("turbineTargetWarmupTicks",3600);
        state.n.setInteger("efficiency",1050);state.n.setBoolean("funded",true);state.n.setInteger("progress",398);
        state.n.setString("owner",owner.toString());state.n.setString("eu","9876543210123456789");
        net.minecraft.nbt.NBTTagList oldInputs=new net.minecraft.nbt.NBTTagList();
        NBTTagCompound oldReceipt=new NBTTagCompound();oldReceipt.setString("legacyReceipt","paid-at-v2.0.19");oldInputs.appendTag(oldReceipt);
        state.n.setTag("inputs",oldInputs);
        Method migrate=CriticalEnergyRuntime.class.getDeclaredMethod("migrateTurbineWarmth",CriticalMachineState.class,NBTTagCompound.class);
        migrate.setAccessible(true);migrate.invoke(null,state,state.n);
        check(state.n.getInteger("turbineRunTicks")==200&&state.n.getInteger("turbineTargetWarmupTicks")==400
            &&state.n.getInteger("efficiency")==1050&&state.n.getInteger("turbinePolicy")==3,
            "legacy half-warm1800/3600 migrates to200/400 at the same10.5x fraction without changing paid policy");
        migrate.invoke(null,state,state.n);
        check(state.n.getInteger("turbineRunTicks")==200&&state.n.getInteger("turbineWarmthScale")==4
            &&state.n.getString("eu").equals("9876543210123456789")&&state.n.getTagList("inputs",10).equals(oldInputs),
            "warmth scaling is idempotent and does not reprice old EU or debit receipts");
        before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        for(int i=0;i<2;i++){((EnergyClock)state.worldObj).clock++;CriticalEnergyRuntime.tick(state,host);}
        check(!state.n.getBoolean("funded")&&state.n.getInteger("turbineRunTicks")==202
            &&gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(new BigInteger("9876543210123456789")),
            "old paid policy3 finishes exactly its frozen bill while progressing on the new400tick warmth scale");
        int migratedEfficiency=CriticalEnergyMath.runningEfficiency(202);
        fund.invoke(null,state,state.n,new ArrayList<>(),new ArrayList<>(),fullHeat,0);
        check(state.n.getInteger("turbinePolicy")==4&&state.n.getInteger("turbineRunTicks")==202
            &&new BigInteger(state.n.getString("eu")).equals(fullHeat.multiply(b(migratedEfficiency)).divide(b(200))),
            "next fresh policy4 payment preserves migrated warmth fraction and freezes only its newly quoted EU");
        EnergyState steady=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        EnergyState changing=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        for(EnergyState s:List.of(steady,changing)){
            s.n=new NBTTagCompound();s.n.setString("owner",owner.toString());
            s.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        }
        for(int batch=0;batch<2;batch++){
            fund.invoke(null,steady,steady.n,new ArrayList<>(),new ArrayList<>(),fullHeat.multiply(b(10)),0);
            fund.invoke(null,changing,changing.n,new ArrayList<>(),new ArrayList<>(),fullHeat.multiply(b(batch%2==0?10:9)),0);
            for(int tick=0;tick<400;tick++) for(EnergyState s:List.of(steady,changing)){
                ((EnergyClock)s.worldObj).clock++;CriticalEnergyRuntime.tick(s,host);
            }
            check(steady.n.getInteger("turbineRunTicks")==changing.n.getInteger("turbineRunTicks")
                &&steady.n.getInteger("efficiency")==changing.n.getInteger("efficiency"),
                "actual steady and100/90 oscillating funded batches have identical rotor warmth after batch"+batch);
        }
        check(steady.n.getInteger("turbineRunTicks")==400&&steady.n.getInteger("efficiency")==2000,
            "actual400tick funded batch reaches20x in20seconds, no oscillation advantage");
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
        check(steady.n.getInteger("turbineRunTicks")==0,
            "1L steam cannot maintain full rotor warmth and loses400ticks in one paid batch");
        EnergyState cheap=(EnergyState)unsafe.allocateInstance(EnergyState.class);
        cheap.n=new NBTTagCompound();cheap.n.setString("owner",owner.toString());
        cheap.worldObj=(EnergyClock)unsafe.allocateInstance(EnergyClock.class);
        for(int batch=0;batch<2;batch++){
            fund.invoke(null,cheap,cheap.n,new ArrayList<>(),new ArrayList<>(),BigInteger.ONE,0);
            for(int tick=0;tick<400;tick++){((EnergyClock)cheap.worldObj).clock++;CriticalEnergyRuntime.tick(cheap,host);}
        }
        check(cheap.n.getInteger("turbineRunTicks")==0&&cheap.n.getInteger("efficiency")==100,
            "paid1L ordinary-steam batches cannot preheat a cold rotor or carry20x into giant steam");
        for(EnergyState s:List.of(steady,changing)){
            s.n=new NBTTagCompound();s.n.setString("owner",owner.toString());
        }
        for(int batch=0;batch<2;batch++){
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
        check(steady.n.getInteger("turbineRunTicks")==200&&changing.n.getInteger("turbineRunTicks")<=200,
            "sub-threshold stable load converges to its200tick absolute target and cannot mint20x");
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
