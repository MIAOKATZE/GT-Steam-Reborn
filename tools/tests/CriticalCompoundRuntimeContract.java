import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
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
        seed(unsafe,"Diesel","gtsr_compound_premium",2500,false);
        seed(unsafe,"Hydrogen","gtsr_compound_gas",1200,true);
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
        Item circuitItem=new Item().setHasSubtypes(true);
        gregtech.api.enums.ItemList.Circuit_Integrated.set(circuitItem);
        circuitContracts(unsafe,circuitItem);
        FakeBus bus=bus(unsafe,circuitItem,2);
        set(host,"mInputBusses",new ArrayList<>(List.of(bus)));
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
        var expected=CriticalChemicalRecipes.best(liveFuels,amounts,2);
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
        check(!state.n.hasKey("chemicalCompoundPayment")&&state.n.getInteger("chemicalBatchRule")==5
            &&state.n.getInteger("chemicalBatchCircuit")==2&&bus.mInventory[0].stackSize==1
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
        var migrationPair=CriticalChemicalRecipes.catalog(CriticalGTRecipes.chemicalFuels()).stream()
            .filter(r->r.components.size()==2).findFirst().orElseThrow();
        set(host,"mInputHatches",recipeHatches(unsafe,tile,migrationPair,volumes(migrationPair,20)));
        before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalSchema")==5
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
        seed(unsafe,"GasolineRegular","gtsr_compound_regular",2000,false);
        seed(unsafe,"GasolinePremium","gtsr_compound_high",2500,false);
        String[] rockets={"gtsr_compound_rocket0","gtsr_compound_rocket1","gtsr_compound_rocket2","gtsr_compound_rocket3"};
        for(int i=0;i<rockets.length;i++)seed(unsafe,null,rockets[i],900+i*200,false);
        gtPlusPlus.core.fluids.GTPPFluids.RP1RocketFuel=FluidRegistry.getFluid(rockets[0]);
        gtPlusPlus.core.fluids.GTPPFluids.DenseHydrazineFuelMixture=FluidRegistry.getFluid(rockets[1]);
        gtPlusPlus.core.fluids.GTPPFluids.CN3H7O3RocketFuel=FluidRegistry.getFluid(rockets[2]);
        gtPlusPlus.core.fluids.GTPPFluids.H8N4C2O4RocketFuel=FluidRegistry.getFluid(rockets[3]);
        FluidRegistry.registerFluid(new Fluid("combustionpromotor"));
        // The diverse catalogue samples the full feedstock pool, unlike the former fixed tail.
        // Seed those native identities instead of assuming a three-base subset contains a nine-fluid sample.
        String[] extraBase={"HeavyFuel","NitroFuel","Ethanol","Methanol","Methane","Benzene","Ethylene",
            "Propene","Butene","Butadiene","Ethane","Propane","Butane","Toluene"};
        for(int i=0;i<extraBase.length;i++)seed(unsafe,extraBase[i],"gtsr_compound_"+extraBase[i].toLowerCase(java.util.Locale.ROOT),100+i*20,i>=4);
        Fluid ozone=new Fluid("gtsr_compound_ozone");FluidRegistry.registerFluid(ozone);
        gregtech.api.enums.Materials.Ozone.mGas=ozone;
        var fuels=CriticalGTRecipes.chemicalFuels();
        check(CriticalChemicalRecipes.catalog(fuels).size()==1024,"migration fixture registers the complete diverse catalogue");
        var promoted=CriticalChemicalRecipes.catalogLegacyV3(fuels).stream().filter(r->r.id.equals("promoted.3")).findFirst().orElseThrow();
        var amounts=volumes(promoted,13);
        var old=CriticalChemicalRecipes.bestLegacyV2(fuels,amounts);
        var v3=CriticalChemicalRecipes.bestLegacyV3(fuels,amounts);
        check(old.recipe.peakMultiplier==100&&v3.recipe.peakMultiplier==40,"old policies keep independent peaks");
        set(host,"mInputBusses",new ArrayList<>());
        for(int policy:new int[]{2,3}) {
            var hatches=recipeHatches(unsafe,tile,promoted,amounts);
            state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",policy);
            state.n.setString("chemicalEscrowOwner",owner.toString());
            NBTTagList pending=payment(promoted,amounts);
            pending.getCompoundTagAt(0).setString("confirmedLitres","1");hatches.get(0).stock--;
            state.n.setTag("chemicalCompoundPayment",pending);
            if(policy==3)state.n.setInteger("chemicalPaymentPolicy",3);
            hatches.get(0).limit=1;set(host,"mInputHatches",hatches);
            check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.getBoolean("uncertain")
                &&state.n.getInteger("chemicalSchema")==5&&state.n.getInteger("chemicalPaymentPolicy")==policy,
                "legacy pending upgrades envelope without circuit or repricing "+policy);
            state.n=reload(state.n);hatches.get(0).limit=Integer.MAX_VALUE;
            var expected=policy==2?old:v3;
            check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalBatchRule")==policy
                &&state.n.getString("eu").equals(expected.evaluation.generatedEU.toString())&&hatches.stream().allMatch(h->h.stock==0),
                "legacy receipt pays only missing litres using frozen policy "+policy);
            BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
            state.n=reload(state.n);state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
            check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(expected.evaluation.generatedEU),
                "legacy funded batch delivers frozen output "+policy);
        }
        // A v4 debit must still use the original recipe identities, ratios and captured prices.
        var v4Recipe=CriticalChemicalRecipes.catalogLegacyV4(fuels).stream()
            .filter(r->r.id.equals("compound.2.0.1")).findFirst().orElseThrow();
        var v4Amounts=volumes(v4Recipe,37);
        var v4Expected=CriticalChemicalRecipes.matchExactLegacyV4(fuels,v4Amounts,2,v4Recipe.id);
        var v4Hatches=recipeHatches(unsafe,tile,v4Recipe,v4Amounts);
        state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",4);
        state.n.setInteger("chemicalPaymentPolicy",4);state.n.setInteger("chemicalPaymentCircuit",2);
        state.n.setString("chemicalPaymentRecipe",v4Recipe.id);state.n.setString("chemicalEscrowOwner",owner.toString());
        NBTTagList v4Payment=payment(v4Recipe,v4Amounts);
        v4Payment.getCompoundTagAt(0).setString("confirmedLitres","1");v4Hatches.get(0).stock--;
        state.n.setTag("chemicalCompoundPayment",v4Payment);v4Hatches.get(0).limit=2;
        set(host,"mInputHatches",v4Hatches);
        check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalSchema")==5
            &&state.n.getInteger("chemicalPaymentPolicy")==4
            &&state.n.getString("chemicalPaymentRecipe").equals(v4Recipe.id),
            "v4 partial debit upgrades only envelope and retains its exact old manifest without a selector");
        state.n=reload(state.n);v4Hatches.get(0).limit=Integer.MAX_VALUE;
        check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalBatchRule")==4
            &&state.n.getInteger("chemicalBatchCircuit")==2&&state.n.getString("chemicalRecipe").equals(v4Recipe.id)
            &&state.n.getString("eu").equals(v4Expected.evaluation.generatedEU.toString())
            &&v4Hatches.stream().allMatch(h->h.stock==0),
            "v4 receipt completes with its original ratio and frozen valuation after NBT reload");
        BigInteger v4Before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
        state.n=reload(state.n);state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(v4Before)
            .equals(v4Expected.evaluation.generatedEU),"v4 funded receipt settles once to the captured owner");
        Item circuitItem=gregtech.api.enums.ItemList.Circuit_Integrated.getItem();
        FakeBus bus=bus(unsafe,circuitItem,9);set(host,"mInputBusses",new ArrayList<>(List.of(bus)));
        var pair=CriticalChemicalRecipes.catalog(fuels).stream().filter(r->r.components.size()==2).findFirst().orElseThrow();
        amounts=volumes(pair,37);var hatches=recipeHatches(unsafe,tile,pair,amounts);
        set(host,"mInputHatches",hatches);state.n=new NBTTagCompound();
        check(!(boolean)start.invoke(null,state,host,state.n)&&hatches.stream().allMatch(h->h.stock>0)
            &&!state.n.hasKey("chemicalCompoundPayment"),"circuit9 cannot fall back to pair or debit");
        for(int selector:new int[]{0,1,10,-3}) {
            bus.mInventory[0]=selector==0?null:new ItemStack(circuitItem,1,selector);state.n=new NBTTagCompound();
            check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.hasKey("chemicalCompoundPayment")
                &&hatches.stream().allMatch(h->h.stock>0),"invalid selector leaves fluids untouched "+selector);
        }
        bus.mInventory[0]=new ItemStack(circuitItem,1,2);bus.mInventory[1]=new ItemStack(circuitItem,1,3);
        state.n=new NBTTagCompound();
        check(!(boolean)start.invoke(null,state,host,state.n)&&!state.n.hasKey("chemicalCompoundPayment")
            &&hatches.stream().allMatch(h->h.stock>0),"conflicting selectors reject before debit");bus.mInventory[1]=null;
        for(int size:new int[]{2,3,6,9}) {
            var recipe=CriticalChemicalRecipes.catalog(fuels).stream().filter(r->r.components.size()==size).findFirst().orElseThrow();
            amounts=volumes(recipe,37);
            var first=gregtech.api.util.GTUtility.FluidId.create(recipe.components.get(0).fuel.fluid);
            amounts.put(first,amounts.get(first).add(BigInteger.ONE));
            var expected=CriticalChemicalRecipes.best(fuels,amounts,size);bus.mInventory[0]=new ItemStack(circuitItem,1,size);
            state.n=new NBTTagCompound();state.n.setInteger("chemicalSchema",2);
            hatches=recipeHatches(unsafe,tile,recipe,amounts);hatches.get(0).limit=5;set(host,"mInputHatches",hatches);
            check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalPaymentPolicy")==5
                &&state.n.getInteger("chemicalPaymentCircuit")==size&&state.n.getString("chemicalPaymentRecipe").equals(expected.recipe.id)
                &&state.n.getTagList("chemicalCompoundPayment",10).tagCount()==size,"selector and recipe freeze before debit "+size);
            int remaining=hatches.get(0).stock;String recipeId=state.n.getString("chemicalPaymentRecipe");
            NBTTagCompound saved=reload(state.n);state.n.setString("chemicalEscrowOwner",UUID.randomUUID().toString());
            check(!(boolean)start.invoke(null,state,host,state.n)&&hatches.get(0).stock==remaining,"owner protects pending stock "+size);
            state.n=reload(saved);state.n.setString("chemicalPaymentRecipe","unknown.recipe");
            check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getBoolean("uncertain")&&hatches.get(0).stock==remaining,
                "unknown recipe locks before debit "+size);
            state.n=reload(saved);state.n.setInteger("chemicalPaymentPolicy",99);
            check(!(boolean)start.invoke(null,state,host,state.n)&&state.n.getBoolean("uncertain")&&hatches.get(0).stock==remaining,
                "unknown policy locks before debit "+size);
            state.n=reload(saved);bus.mInventory[0]=null;hatches.get(0).limit=Integer.MAX_VALUE;
            check((boolean)start.invoke(null,state,host,state.n)&&state.n.getInteger("chemicalBatchRule")==5
                &&state.n.getInteger("chemicalBatchCircuit")==size&&state.n.getString("chemicalRecipe").equals(recipeId)
                &&hatches.stream().allMatch(h->h.stock==0)&&state.n.getString("eu").equals(expected.evaluation.generatedEU.toString()),
                "removed circuit preserves frozen paid manifest "+size);
            BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(owner);
            state.n=reload(state.n);state.n.setInteger("progress",399);CriticalEnergyRuntime.tick(state,host);
            check(gregtech.common.misc.WirelessNetworkManager.getUserEU(owner).subtract(before).equals(expected.evaluation.generatedEU),
                "funded group delivers exact once-floored EU "+size);
        }
        bus.mInventory[0]=new ItemStack(circuitItem,1,2);
    }

    static NBTTagList payment(CompoundRecipe recipe,Map<gregtech.api.util.GTUtility.FluidId,BigInteger> amounts) {
        NBTTagList result=new NBTTagList();
        for(var part:recipe.components) {
            NBTTagCompound entry=part.fuel.fluid.writeToNBT(new NBTTagCompound());
            entry.setString("targetLitres",amounts.get(gregtech.api.util.GTUtility.FluidId.create(part.fuel.fluid)).toString());
            entry.setString("confirmedLitres","0");entry.setString("euPerL",part.fuel.euPerL.toString());
            entry.setInteger("spectrum",part.fuel.spectrum);result.appendTag(entry);
        }
        return result;
    }

    static FakeBus bus(sun.misc.Unsafe unsafe,Item item,int selector)throws Exception {
        FakeBus bus=(FakeBus)unsafe.allocateInstance(FakeBus.class);
        set(bus,"mInventory",new ItemStack[]{new ItemStack(item,1,selector),null,null,null});return bus;
    }
    static void circuitContracts(sun.misc.Unsafe unsafe,Item item)throws Exception {
        FakeBus bus=bus(unsafe,item,4);
        ItemStack physical=bus.mInventory[0];physical.stackSize=3;
        physical.setTagCompound(new NBTTagCompound());physical.getTagCompound().setString("marker","keep");
        NBTTagCompound before=(NBTTagCompound)physical.getTagCompound().copy();
        check(CriticalChemicalCircuit.read(List.of(bus,bus))==4&&physical.stackSize==3
            &&physical.getTagCompound().equals(before),"read preserves item size/NBT and deduplicates bus");
        bus.mInventory[1]=new ItemStack(item,1,4);
        check(CriticalChemicalCircuit.read(List.of(bus))==4,"same-number physical selectors accepted");
        bus.mInventory[1]=new ItemStack(item,1,9);
        check(CriticalChemicalCircuit.read(List.of(bus))==CriticalChemicalCircuit.CONFLICT,"different selectors conflict");
        bus.mInventory[0]=null;bus.mInventory[1]=null;bus.mInventory[3]=new ItemStack(item,0,7);
        check(CriticalChemicalCircuit.read(List.of(bus))==7,"native zero-size ghost circuit slot selects");
        bus.mInventory[0]=new ItemStack(item,1,7);
        check(CriticalChemicalCircuit.read(List.of(bus))==7,"same-number ghost and physical selectors accepted");
        bus.mInventory[0]=new ItemStack(item,1,6);
        check(CriticalChemicalCircuit.read(List.of(bus))==CriticalChemicalCircuit.CONFLICT,"ghost and physical number conflict rejects");
        bus.mInventory[3]=null;
        bus.mInventory[0]=new ItemStack(new Item(),1,5);
        check(CriticalChemicalCircuit.read(List.of(bus))==0,"other circuit-like items cannot select");
        bus.mInventory[0]=new ItemStack(item,0,5);
        check(CriticalChemicalCircuit.read(List.of(bus))==0,"zero-size stack cannot select");
        bus.mInventory[0]=new ItemStack(item,1,25);
        check(CriticalChemicalCircuit.read(List.of(bus))==CriticalChemicalCircuit.INVALID,"damaged integrated selector rejects");
        MTEHatchInputBusME me=(MTEHatchInputBusME)unsafe.allocateInstance(MTEHatchInputBusME.class);
        IGregTechTileEntity tile=(IGregTechTileEntity)Proxy.newProxyInstance(IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->{
                if(m.getName().equals("getMetaTileEntity"))return me;
                if(m.getReturnType()==boolean.class)return false;
                return null;
            });
        me.setBaseMetaTileEntity(tile);set(me,"mInventory",new ItemStack[]{null,null});
        var slots=new MTEHatchInputBusME.Slot[MTEHatchInputBusME.SLOT_COUNT];
        var config=(MTEHatchInputBusME.Slot)unsafe.allocateInstance(MTEHatchInputBusME.Slot.class);
        set(config,"config",new ItemStack(item,64,8));slots[0]=config;set(me,"slots",slots);
        check(CriticalChemicalCircuit.read(List.of(me))==0,"ME stocked filter config cannot select");
        me.mInventory[me.getCircuitSlot()]=new ItemStack(item,0,9);
        check(CriticalChemicalCircuit.read(List.of(me))==9,"ME native zero-size ghost circuit selects without network access");
        me.mInventory[me.getCircuitSlot()]=null;
        ItemStack manual=new ItemStack(item,2,6);me.mInventory[me.getManualSlot()]=manual;
        set(me,"processingRecipe",true);config.extracted=new ItemStack(item,64,8);
        check(CriticalChemicalCircuit.read(List.of(me))==6&&manual.stackSize==2&&config.extracted.stackSize==64,
            "ME reads only real manual stack even during virtual recipe processing");
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
    static class FakeBus extends MTEHatchInputBus {
        FakeBus(){super("unused",1,new String[0],null);}
        @Override public boolean isValid(){return true;}
        @Override public int getCircuitSlot(){return 3;}
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
