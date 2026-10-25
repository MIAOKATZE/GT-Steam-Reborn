import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Actual source runtime, Minecraft NBT, drain receipts and GT wireless API; no live AE grid. */
public final class CriticalCompoundRuntimeContract {
    public static void main(String[] args)throws Exception {
        Issue37RegistrationRegression.launch("CriticalCompoundRuntime", "5.09.54.205");
    }
}

class CriticalCompoundRuntime {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static BigInteger b(long n){return BigInteger.valueOf(n);}
    static void set(Object object,String name,Object value)throws Exception{
        Class<?> type=object.getClass();
        while(type!=null){try{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;}
            catch(NoSuchFieldException missing){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static void seed(sun.misc.Unsafe unsafe,String material,String name,int price,boolean gas)throws Exception {
        Fluid fluid=new Fluid(name);FluidRegistry.registerFluid(fluid);
        var map=gas?gregtech.api.recipe.RecipeMaps.gasTurbineFuels:gregtech.api.recipe.RecipeMaps.dieselFuels;
        map.getBackend().compileRecipe(gregtech.api.util.GTRecipeBuilder.builder()
            .fluidInputs(new FluidStack(fluid,1)).duration(20).eut(0).specialValue(price).build().orElseThrow());
        if(material!=null){
            var definition=(gregtech.api.enums.Materials)unsafe.allocateInstance(gregtech.api.enums.Materials.class);
            definition.mFluid=fluid;gregtech.api.enums.Materials.class.getField(material).set(null,definition);
        }
    }
    public static void run(String version)throws Exception {
        sun.misc.Unsafe unsafe=Issue37Runtime.bootstrap(version);
        for(Class<?> type:List.of(CriticalChemicalMath.class,CriticalChemicalRecipes.class,CriticalEnergyRuntime.class,
            CriticalEnergyPorts.class))System.out.println("SOURCE "+type.getSimpleName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
        seed(unsafe,"GasolinePremium","gtsr_compound_premium",2500,false);
        seed(unsafe,"NefariousGas","gtsr_compound_gas",1200,true);
        seed(unsafe,null,"gtsr_compound_extra",100,false);
        var liveFuels=CriticalGTRecipes.chemicalFuels();
        var premium=liveFuels.stream().filter(f->f.fluid.getFluid().getName().equals("gtsr_compound_premium")).findFirst().orElseThrow();
        var gas=liveFuels.stream().filter(f->f.fluid.getFluid().getName().equals("gtsr_compound_gas")).findFirst().orElseThrow();
        var extra=liveFuels.stream().filter(f->f.fluid.getFluid().getName().equals("gtsr_compound_extra")).findFirst().orElseThrow();
        Fluid unknown=new Fluid("gtsr_compound_unknown");FluidRegistry.registerFluid(unknown);
        UUID owner=UUID.fromString("11111111-2222-3333-4444-555555555555");
        gregtech.common.misc.GlobalEnergyWorldSavedData.INSTANCE=new gregtech.common.misc.GlobalEnergyWorldSavedData();
        gregtech.common.misc.WirelessNetworkManager.strongCheckOrAddUser(owner);
        FakeState state=(FakeState)unsafe.allocateInstance(FakeState.class);
        state.n=new NBTTagCompound();state.worldObj=(ClockWorld)unsafe.allocateInstance(ClockWorld.class);
        MTECriticalMachine host=(MTECriticalMachine)unsafe.allocateInstance(MTECriticalMachine.class);
        IGregTechTileEntity tile=(IGregTechTileEntity)Proxy.newProxyInstance(IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->{
                if(m.getName().equals("getOwnerUuid"))return owner;
                if(m.getName().equals("getMetaTileEntity"))return host;
                if(m.getReturnType()==boolean.class)return false;
                return null;
            });
        host.setBaseMetaTileEntity(tile);
        set(host,"mDualInputHatches",new ArrayList<>());
        set(host,"mOutputHatches",new ArrayList<>());
        set(host,"mOutputBusses",new ArrayList<>());
        FakeHatch liquid=hatch(unsafe,tile,premium.fluid,40),gaseous=hatch(unsafe,tile,gas.fluid,60);
        FakeHatch diluent=hatch(unsafe,tile,new FluidStack(unknown,1),100),extraFuel=hatch(unsafe,tile,extra.fluid,10);
        Method start=CriticalEnergyRuntime.class.getDeclaredMethod("startChemical",CriticalMachineState.class,
            MTECriticalMachine.class,NBTTagCompound.class);start.setAccessible(true);
        set(host,"mInputHatches",new ArrayList<>(List.of(liquid)));
        check(!(boolean)start.invoke(null,state,host,state.n)&&liquid.stock==40&&!state.n.getBoolean("funded"),
            "single-fluid input never starts or charges");
        set(host,"mInputHatches",new ArrayList<>(List.of(liquid,diluent)));
        check(!(boolean)start.invoke(null,state,host,state.n)&&liquid.stock==40&&diluent.stock==100,
            "two identities without registered compound never charge");
        set(host,"mInputHatches",new ArrayList<>(List.of(liquid,gaseous,diluent,extraFuel)));
        Map<gregtech.api.util.GTUtility.FluidId,BigInteger> amounts=new LinkedHashMap<>();
        for(FakeHatch input:List.of(liquid,gaseous,diluent,extraFuel))
            amounts.put(gregtech.api.util.GTUtility.FluidId.create(input.fluid),b(input.stock));
        var expected=CriticalChemicalRecipes.best(liveFuels,amounts);
        check(expected!=null&&expected.evaluation.eligible,"native-identity compound available");
        liquid.limit=7;
        check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.getBoolean("funded")
            &&!state.n.getBoolean("uncertain")&&liquid.stock==33&&gaseous.stock==60,
            "short charge retains receipts and cannot fund partial composition");
        NBTTagList journal=state.n.getTagList("chemicalCompoundPayment",10);
        check(journal.getCompoundTagAt(0).getString("confirmedLitres").equals("7")
            &&journal.getCompoundTagAt(0).getString("targetLitres").equals("40"),
            "whole-snapshot target and confirmed payment are independent");
        state.n=(NBTTagCompound)state.n.copy();liquid.limit=Integer.MAX_VALUE;
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getBoolean("funded")
            &&liquid.stock==0&&gaseous.stock==0&&diluent.stock==0&&extraFuel.stock==0,
            "NBT-restored receipt takes only missing litres and consumes all diluents/extra fuel");
        check(state.n.getString("chemicalBatchHeat").equals("173000")
            &&state.n.getString("chemicalBatchLitres").equals("210")
            &&new BigInteger(state.n.getString("eu")).equals(expected.evaluation.generatedEU),
            "all recognized native heat uses exact whole-snapshot proportion");
        check(!state.n.hasKey("chemicalCompoundPayment")&&state.n.getInteger("chemicalBatchRule")==3
            &&state.n.getTagList("chemicalBatchCredits",10).tagCount()==4,
            "fully paid manifest becomes frozen batch receipts");
        BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        check(!state.n.getBoolean("funded")&&gregtech.common.misc.WirelessNetworkManager.getUserEU(owner)
            .subtract(before).equals(expected.evaluation.generatedEU),"real GT wireless account receives actual paid EU");
        check(!state.n.hasKey("chemicalEUNumerator")&&!state.n.hasKey("chemicalEUDenominator"),
            "final settlement floors once without an unbounded persistent rational denominator");
        state.n=new NBTTagCompound();liquid.stock=4000000;gaseous.stock=6000000;
        set(host,"mInputHatches",new ArrayList<>(List.of(liquid,gaseous)));
        check((boolean)start.invoke(null,state,host,state.n)&&liquid.stock==0&&gaseous.stock==0
            &&new BigInteger(state.n.getString("chemicalBatchHeat")).equals(b(17200000000L)),
            "stock above former heat cap is fully paid without cap");
        state.n=new NBTTagCompound();liquid.stock=40;gaseous.stock=60;gaseous.throwDrain=true;
        check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getBoolean("uncertain")
            &&liquid.stock==0&&gaseous.stock==60&&state.n.hasKey("chemicalDebitIntent"),
            "later source exception locks confirmed prefix and debit intent");
        state.n=(NBTTagCompound)state.n.copy();gaseous.throwDrain=false;CriticalEnergyRuntime.tick(state,host);
        check(gaseous.stock==60&&!state.n.getBoolean("funded"),"uncertain payment never retries after reload");
        BigInteger huge=BigInteger.TEN.pow(60);before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",2);state.n.setInteger("chemicalBatchRule",2);
        state.n.setBoolean("funded",true);state.n.setInteger("progress",399);
        state.n.setString("eu",huge.toString());state.n.setString("owner",owner.toString());CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(huge),
            "old schema2 funded EU stays frozen and GT wireless preserves 10^60 EU");
        migrationPolicies(unsafe,state,host,tile,start,owner);
        state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",1);
        state.n.setString("chemicalEscrowOwner",owner.toString());
        NBTTagCompound legacy=premium.fluid.writeToNBT(new NBTTagCompound());
        legacy.setInteger("ruleVersion",1);legacy.setInteger("spectrum",0);legacy.setString("euPerL","480");
        legacy.setString("paidLitres","3");legacy.setString("remainingHeat","439");
        NBTTagList credits=new NBTTagList();credits.appendTag(legacy);state.n.setTag("chemicalCredits",credits);
        liquid.stock=40;gaseous.stock=60;set(host,"mInputHatches",new ArrayList<>(List.of(liquid,gaseous)));
        before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalSchema")==3
            &&!state.n.hasKey("chemicalCredits")&&gregtech.common.misc.WirelessNetworkManager.getUserEU(owner)
                .subtract(before).equals(b(439)),"v1 residual migrates at frozen base heat before new compound starts");
        state.n=(NBTTagCompound)state.n.copy();state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        BigInteger afterMigration=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).equals(afterMigration),
            "finished legacy closeout never pays twice on NBT reload or empty next start");
        System.out.println("PASS CriticalCompoundRuntimeContract checks="+checks);
    }

    static void migrationPolicies(sun.misc.Unsafe unsafe,FakeState state,MTECriticalMachine host,
        IGregTechTileEntity tile,Method start,UUID owner)throws Exception {
        seed(unsafe,"Diesel","gtsr_compound_diesel",480,false);
        seed(unsafe,"LightFuel","gtsr_compound_light",360,false);
        seed(unsafe,"Hydrogen","gtsr_compound_hydrogen",160,true);
        seed(unsafe,null,"gtsr_compound_rocket0",900,false);
        seed(unsafe,null,"gtsr_compound_rocket3",1600,false);
        gtPlusPlus.core.fluids.GTPPFluids.RP1RocketFuel=FluidRegistry.getFluid("gtsr_compound_rocket0");
        gtPlusPlus.core.fluids.GTPPFluids.H8N4C2O4RocketFuel=FluidRegistry.getFluid("gtsr_compound_rocket3");
        FluidRegistry.registerFluid(new Fluid("combustionpromotor"));
        Fluid ozone=new Fluid("gtsr_compound_ozone");FluidRegistry.registerFluid(ozone);
        var ozoneMaterial=(gregtech.api.enums.Materials)unsafe.allocateInstance(gregtech.api.enums.Materials.class);
        ozoneMaterial.mFluid=ozone;gregtech.api.enums.Materials.Ozone=ozoneMaterial;
        var fuels=CriticalGTRecipes.chemicalFuels();
        var promoted=CriticalChemicalRecipes.catalog(fuels).stream().filter(r->r.id.equals("promoted.3")).findFirst().orElseThrow();
        var amounts=volumes(promoted,13);
        var old=CriticalChemicalRecipes.bestLegacyV2(fuels,amounts);
        var fresh=CriticalChemicalRecipes.best(fuels,amounts);
        check(old.recipe.id.equals("promoted.3")&&old.recipe.peakMultiplier==100
            &&fresh.recipe.id.equals("promoted.3")&&fresh.recipe.peakMultiplier==40,
            "same promoted3 identity has old100 and new three-component40 policy");
        ArrayList<FakeHatch> hatches=recipeHatches(unsafe,tile,promoted,amounts);
        state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",2);
        state.n.setString("chemicalEscrowOwner",owner.toString());
        NBTTagList pending=new NBTTagList();
        for(var part:promoted.components){
            NBTTagCompound entry=part.fuel.fluid.writeToNBT(new NBTTagCompound());
            entry.setString("targetLitres",amounts.get(gregtech.api.util.GTUtility.FluidId.create(part.fuel.fluid)).toString());
            entry.setString("confirmedLitres","0");entry.setString("euPerL",part.fuel.euPerL.toString());
            entry.setInteger("spectrum",part.fuel.spectrum);pending.appendTag(entry);
        }
        pending.getCompoundTagAt(0).setString("confirmedLitres","1");hatches.get(0).stock--;
        state.n.setTag("chemicalCompoundPayment",pending);hatches.get(0).limit=1;
        set(host,"mInputHatches",hatches);
        check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.getBoolean("uncertain")
            &&state.n.getInteger("chemicalSchema")==3&&state.n.getInteger("chemicalPaymentPolicy")==2,
            "v2 pending binds legacy policy before envelope upgrade and short receipt persists");
        state.n=reload(state.n);hatches.get(0).limit=Integer.MAX_VALUE;
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalBatchRule")==2
            &&state.n.getInteger("chemicalEfficiencyHundredths")==10000
            &&state.n.getString("eu").equals(old.evaluation.generatedEU.toString())
            &&hatches.stream().allMatch(h->h.stock==0),
            "serialized old pending resumes only missing litres and retains old100 final EU");
        BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        state.n=reload(state.n);state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(old.evaluation.generatedEU),
            "legacy batch pays frozen old100 to actual wireless owner");
        state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",2);
        hatches=recipeHatches(unsafe,tile,promoted,amounts);hatches.get(0).limit=1;set(host,"mInputHatches",hatches);
        check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalPaymentPolicy")==3,
            "idle schema2 without pending captures new512 policy instead of staying legacy");
        state.n=reload(state.n);hatches.get(0).limit=Integer.MAX_VALUE;
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalBatchRule")==3
            &&state.n.getInteger("chemicalEfficiencyHundredths")==4000
            &&state.n.getString("eu").equals(fresh.evaluation.generatedEU.toString()),
            "new same-id pending keeps three-component40 across NBT reload");
        var senary=CriticalChemicalRecipes.catalog(fuels).stream().filter(r->r.id.equals("senary.0.0.0")).findFirst().orElseThrow();
        amounts=volumes(senary,37);
        var first=gregtech.api.util.GTUtility.FluidId.create(senary.components.get(0).fuel.fluid);
        amounts.put(first,amounts.get(first).add(BigInteger.ONE));
        var expected=CriticalChemicalRecipes.best(fuels,amounts);
        check(expected.recipe.components.size()==6&&expected.evaluation.generatedNumerator
            .remainder(expected.evaluation.generatedDenominator).signum()!=0,
            "six-component off-ratio fixture has nonintegral exact final output");
        state.n=new NBTTagCompound();hatches=recipeHatches(unsafe,tile,senary,amounts);
        hatches.get(0).limit=5;set(host,"mInputHatches",hatches);
        check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalPaymentPolicy")==3
            &&state.n.getTagList("chemicalCompoundPayment",10).tagCount()==6,
            "new six-fluid manifest freezes all identities before actual short drain");
        state.n=reload(state.n);hatches.get(0).limit=Integer.MAX_VALUE;
        check((boolean)start.invoke(null,state,host,state.n)&&hatches.stream().allMatch(h->h.stock==0)
            &&state.n.getTagList("chemicalBatchCredits",10).tagCount()==6
            &&state.n.getString("eu").equals(expected.evaluation.generatedEU.toString()),
            "serialized six-fluid receipts fully fund and floor exact output once");
        before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        state.n=reload(state.n);state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(expected.evaluation.generatedEU),
            "six-fluid serialized funded batch delivers exact output to real wireless map");
        state.n=new NBTTagCompound();hatches=recipeHatches(unsafe,tile,senary,amounts);
        hatches.get(0).limit=1;set(host,"mInputHatches",hatches);start.invoke(null,state,host,state.n);
        state.n.setInteger("chemicalPaymentPolicy",4);int stock=hatches.get(0).stock;
        state.n=reload(state.n);hatches.get(0).limit=Integer.MAX_VALUE;
        check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getBoolean("uncertain")
            &&hatches.get(0).stock==stock,"unknown manifest policy locks before any further debit");
    }

    static NBTTagCompound reload(NBTTagCompound data)throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.CompressedStreamTools.writeCompressed(data,bytes);
        return net.minecraft.nbt.CompressedStreamTools.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()));
    }
    static Map<gregtech.api.util.GTUtility.FluidId,BigInteger> volumes(CompoundRecipe recipe,int scale){
        Map<gregtech.api.util.GTUtility.FluidId,BigInteger> result=new LinkedHashMap<>();
        for(var part:recipe.components)result.put(gregtech.api.util.GTUtility.FluidId.create(part.fuel.fluid),b((long)part.ratio*scale));
        return result;
    }
    static ArrayList<FakeHatch> recipeHatches(sun.misc.Unsafe unsafe,IGregTechTileEntity tile,CompoundRecipe recipe,
        Map<gregtech.api.util.GTUtility.FluidId,BigInteger> amounts)throws Exception{
        ArrayList<FakeHatch> result=new ArrayList<>();
        for(var part:recipe.components)result.add(hatch(unsafe,tile,part.fuel.fluid,
            amounts.get(gregtech.api.util.GTUtility.FluidId.create(part.fuel.fluid)).intValueExact()));
        return result;
    }

    static FakeHatch hatch(sun.misc.Unsafe unsafe,IGregTechTileEntity tile,FluidStack fluid,int amount)throws Exception{
        FakeHatch hatch=(FakeHatch)unsafe.allocateInstance(FakeHatch.class);
        hatch.fluid=fluid.copy();hatch.stock=amount;hatch.limit=Integer.MAX_VALUE;hatch.setBaseMetaTileEntity(tile);return hatch;
    }
    static class FakeHatch extends MTEHatchInput {
        FluidStack fluid;int stock,limit;boolean throwDrain;
        FakeHatch(){super("unused",1,new String[0],null);}
        @Override public boolean isValid(){return true;}
        @Override public FluidTankInfo[] getTankInfo(ForgeDirection side){return new FluidTankInfo[]{
            new FluidTankInfo(new FluidStack(fluid,stock),Integer.MAX_VALUE)};}
        @Override public FluidStack drain(ForgeDirection side,FluidStack request,boolean commit){
            if(throwDrain)throw new IllegalStateException("injected uncertain drain");
            int amount=Math.min(request.amount,Math.min(stock,limit));if(commit)stock-=amount;
            return new FluidStack(fluid,amount);
        }
    }
    static class FakeState extends CriticalMachineState {
        NBTTagCompound n;
        @Override public CriticalMachineKind getMachineKind(){return CriticalMachineKind.BATTERY;}
        @Override public NBTTagCompound energyRuntimeData(){return n;}
        @Override public void markDirty(){}
        @Override public boolean fail(String message){System.out.println("STATE "+message);return false;}
        @Override public void setRuntimeBatch(int p,int d,long e,String message){}
    }
    static class ClockWorld extends World {
        ClockWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public long getTotalWorldTime(){return 1;}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
