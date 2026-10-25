import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Actual source arithmetic, NBT and extraction receipts; isolated from a full Forge server. */
public final class CriticalChemicalContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalChemicalRuntime", "5.09.54.205");
    }
}

class CriticalChemicalRuntime {
    static int checks;
    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
    static BigInteger b(long value) { return BigInteger.valueOf(value); }
    static void set(Object object,String name,Object value)throws Exception{
        Class<?> type=object.getClass();
        while(type!=null){try{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;}
            catch(NoSuchFieldException missing){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static void seed(gregtech.api.recipe.RecipeMap<?> map,String name,int heat){
        Fluid fluid=FluidRegistry.getFluid(name);
        if(fluid==null){fluid=new Fluid(name);FluidRegistry.registerFluid(fluid);}
        map.getBackend().compileRecipe(gregtech.api.util.GTRecipeBuilder.builder()
            .fluidInputs(new FluidStack(fluid,1)).duration(20).eut(0).specialValue(heat).build().orElseThrow());
    }
    public static void run(String version) throws Exception {
        sun.misc.Unsafe unsafe = Issue37Runtime.bootstrap(version);
        for(Class<?> type:List.of(CriticalChemicalMath.class,CriticalEnergyRuntime.class,CriticalEnergyPorts.class))
            System.out.println("SOURCE "+type.getSimpleName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
        seed(gregtech.api.recipe.RecipeMaps.dieselFuels,"gtsr_rc2_diesel",100);
        seed(gregtech.api.recipe.RecipeMaps.gasTurbineFuels,"gtsr_rc2_gas",200);
        seed(gregtech.api.recipe.RecipeMaps.denseLiquidFuels,"gtsr_rc2_dense",256);
        seed(gregtech.api.recipe.RecipeMaps.denseLiquidFuels,"gtsr_rc2_diesel",1000);
        var rocket=com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap("rocketFuels");
        seed(rocket,"gtsr_rc2_rocket",300);seed(rocket,"gtsr_rc2_diesel",999);
        Map<String,CriticalGTRecipes.ChemicalFuel> fuels=new HashMap<>();
        for(var entry:CriticalGTRecipes.chemicalFuels())fuels.put(entry.fluid.getFluid().getName(),entry);
        check(fuels.get("gtsr_rc2_diesel").spectrum==0&&fuels.get("gtsr_rc2_diesel").euPerL.equals(b(100)),
            "duplicate FluidId uses diesel first without rocket bonus");
        check(fuels.get("gtsr_rc2_gas").spectrum==1&&fuels.get("gtsr_rc2_gas").euPerL.equals(b(200)),
            "native gas-map spectrum");
        check(fuels.get("gtsr_rc2_rocket").spectrum==2&&fuels.get("gtsr_rc2_rocket").euPerL.equals(b(900)),
            "rocket heat normalized exactly once");
        check(fuels.get("gtsr_rc2_dense").spectrum==0&&fuels.get("gtsr_rc2_dense").euPerL.equals(b(256)),
            "TCDS dense-liquid fuels enter liquid spectrum without changing old diesel precedence");
        // New compound examples are verified by CriticalCompoundContract.

        Fluid fluid = new Fluid("gtsr_rc2_contract_fuel");
        FluidRegistry.registerFluid(fluid);
        FluidStack fuel = new FluidStack(fluid,1);
        fuel.tag = new NBTTagCompound(); fuel.tag.setString("batch", "A");
        NBTTagCompound ledger = new NBTTagCompound();
        var credits = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound oldCredit = fuel.writeToNBT(new NBTTagCompound());
        oldCredit.setInteger("spectrum",0); oldCredit.setInteger("ruleVersion",1);
        oldCredit.setString("euPerL","480"); oldCredit.setString("paidLitres","3");
        oldCredit.setString("remainingHeat","1440"); credits.appendTag(oldCredit);
        ledger.setTag("chemicalCredits",credits);
        check(CriticalEnergyRuntime.chemicalPaidHeat(ledger)[0].equals(b(1440)), "legacy paid base heat read exactly");
        oldCredit.setString("remainingHeat","439"); ledger=(NBTTagCompound)ledger.copy();
        check(CriticalEnergyRuntime.chemicalPaidHeat(ledger)[0].equals(b(439)), "fractional litre legacy heat survives NBT");
        NBTTagCompound repriced = fuel.writeToNBT(new NBTTagCompound());
        repriced.setInteger("spectrum",0); repriced.setInteger("ruleVersion",1);
        repriced.setString("euPerL","900"); repriced.setString("paidLitres","1");
        repriced.setString("remainingHeat","900");
        ledger.getTagList("chemicalCredits",10).appendTag(repriced);
        check(CriticalEnergyRuntime.chemicalPaidHeat(ledger)[0].equals(b(1339)), "fuel repricing does not revalue escrow");
        check(ledger.getTagList("chemicalCredits",10).tagCount()==2, "different historical heat prices separated");
        check(FluidStack.loadFluidStackFromNBT(ledger.getTagList("chemicalCredits",10).getCompoundTagAt(0))
            .tag.getString("batch").equals("A"), "escrow preserves fluid NBT");

        Constructor<CriticalEnergyPorts.Input> sourceConstructor = CriticalEnergyPorts.Input.class
            .getDeclaredConstructor(MTEHatchInput.class,FluidStack.class,long.class,appeng.api.storage.IMEMonitor.class);
        sourceConstructor.setAccessible(true);
        FakeHatch hatch=(FakeHatch)unsafe.allocateInstance(FakeHatch.class);
        hatch.fluid=fuel.copy(); hatch.stock=100; hatch.limit=7;
        IGregTechTileEntity tile=(IGregTechTileEntity)Proxy.newProxyInstance(IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->null);
        hatch.setBaseMetaTileEntity(tile);
        CriticalEnergyPorts.Input source=sourceConstructor.newInstance(hatch,fuel,100L,null);
        CriticalEnergyPorts.Allocation allocation=new CriticalEnergyPorts.Allocation(List.of(source));
        List<CriticalEnergyPorts.Debit> requested=allocation.take(fuel,b(60));
        try { allocation.take(fuel,b(41)); throw new AssertionError("snapshot double spend"); }
        catch (IllegalArgumentException expected) { checks++; }
        List<Long> paid=new ArrayList<>();
        CriticalEnergyPorts.DebitResult result=CriticalEnergyPorts.debitConfirmed(requested,(d,n)->paid.add(n));
        check(!result.complete&&!result.uncertain&&result.confirmedLitres.equals(b(7)), "short real drain confirmed partial");
        check(hatch.stock==93&&paid.equals(List.of(7L)), "only actual receipt enters journal");
        check(hatch.requestTag.equals("A"), "debit preserves requested fluid NBT");
        hatch.limit=7;
        check(CriticalEnergyPorts.debit(CriticalEnergyPorts.take(List.of(source),fuel,b(20)))&&hatch.stock==73,
            "legacy steam debit still traverses partial physical tanks to full amount");
        hatch.limit=100; hatch.throwDrain=true;
        result=CriticalEnergyPorts.debitConfirmed(requested,(d,n)->paid.add(n));
        check(result.uncertain&&!result.complete, "throwing source quarantined");
        FakeHatch prefix=(FakeHatch)unsafe.allocateInstance(FakeHatch.class);
        prefix.fluid=fuel.copy();prefix.stock=5;prefix.limit=5;prefix.setBaseMetaTileEntity(tile);
        CriticalEnergyPorts.Input prefixSource=sourceConstructor.newInstance(prefix,fuel,5L,null);
        List<Long> confirmedPrefix=new ArrayList<>();
        result=CriticalEnergyPorts.debitConfirmed(CriticalEnergyPorts.take(List.of(prefixSource,source),fuel,b(10)),
            (d,n)->confirmedPrefix.add(n));
        check(result.uncertain&&result.confirmedLitres.equals(b(5))&&confirmedPrefix.equals(List.of(5L)),
            "later failure preserves confirmed earlier source payment");

        FakeState state=(FakeState)unsafe.allocateInstance(FakeState.class); state.n=new NBTTagCompound();
        state.worldObj=(ClockWorld)unsafe.allocateInstance(ClockWorld.class);
        MTECriticalMachine host=(MTECriticalMachine)unsafe.allocateInstance(MTECriticalMachine.class);
        var owner=(gregtech.common.tileentities.machines.MTEHatchCraftingInputME)unsafe.allocateInstance(
            gregtech.common.tileentities.machines.MTEHatchCraftingInputME.class);
        int[] dirty={0};
        IGregTechTileEntity ownerTile=(IGregTechTileEntity)Proxy.newProxyInstance(IGregTechTileEntity.class.getClassLoader(),
            new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->{
                if(m.getName().equals("getMetaTileEntity"))return owner;
                if(m.getName().equals("markDirty")){dirty[0]++;return null;}
                if(m.getReturnType()==boolean.class)return false;
                return null;
            });
        owner.setBaseMetaTileEntity(ownerTile);
        var pattern=(gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot<?>)unsafe.allocateInstance(
            gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot.class);
        FluidStack stored=new FluidStack(fuel,100);
        set(pattern,"itemInventory",new ArrayList<>());
        set(pattern,"fluidInventory",new ArrayList<>(List.of(stored,stored)));
        set(owner,"internalInventory",new gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot[]{pattern,pattern});
        var slave=(gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave)unsafe.allocateInstance(
            gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave.class);
        set(slave,"master",owner);
        set(host,"mInputHatches",new ArrayList<>());
        set(host,"mDualInputHatches",new ArrayList<>(List.of(slave,owner,slave)));
        List<CriticalEnergyPorts.Input> nativeSnapshot=CriticalEnergyPorts.snapshot(host);
        check(nativeSnapshot.size()==1&&CriticalEnergyPorts.total(nativeSnapshot,fuel).equals(b(100)),
            "native master/slave/inventory/fluid aliases deduplicated");
        List<Long> nativePaid=new ArrayList<>();
        result=CriticalEnergyPorts.debitConfirmed(CriticalEnergyPorts.take(nativeSnapshot,fuel,b(30)),
            (d,n)->nativePaid.add(n));
        check(result.complete&&stored.amount==70&&nativePaid.equals(List.of(30L)),"native PatternSlot real persistent decrement");
        check(dirty[0]==1,"real native master marked dirty once");
        state.n.setBoolean("funded",true); state.n.setInteger("progress",17); state.n.setString("eu","12345678901234567890");
        state.n.setInteger("generationRemainder",199); state.n.setInteger("waterRemainder",159);
        state.n.setInteger("efficiency",1700); state.n.setInteger("idleTicks",399);
        CriticalEnergyRuntime.idleTick(state);
        check(state.n.getInteger("efficiency")==1700&&state.n.getInteger("idleTicks")==399,"chemical no idle decay");
        CriticalEnergyRuntime.tick(state,host);
        check(state.n.getInteger("progress")==18&&state.n.getString("eu").equals("12345678901234567890"),
            "paid legacy batch advances its frozen bill without replanning");
        check(!state.n.hasKey("chemicalSchema")&&state.n.getInteger("generationRemainder")==199
            &&state.n.getInteger("waterRemainder")==159,"legacy remainder/schema remain intact until completion");
        state.n.setBoolean("uncertain",true);
        CriticalEnergyRuntime.tick(state,host);
        check(state.n.getInteger("progress")==18,"uncertain legacy batch never auto-replayed");
        UUID wirelessOwner=UUID.fromString("11111111-2222-3333-4444-555555555555");
        gregtech.common.misc.GlobalEnergyWorldSavedData.INSTANCE=new gregtech.common.misc.GlobalEnergyWorldSavedData();
        gregtech.common.misc.WirelessNetworkManager.strongCheckOrAddUser(wirelessOwner);
        BigInteger before=gregtech.common.misc.WirelessNetworkManager.getUserEU(wirelessOwner);
        state.n=(NBTTagCompound)ledger.copy();state.n.setInteger("chemicalSchema",1);
        state.n.setString("chemicalEscrowOwner",wirelessOwner.toString());
        check(CriticalEnergyRuntime.settleChemicalEscrow(state,host),"dismantle residual heat closes successfully");
        check(gregtech.common.misc.WirelessNetworkManager.getUserEU(wirelessOwner).subtract(before).equals(b(1339)),
            "residual paid heat gets exactly 1x in original real GT wireless account");
        check(CriticalEnergyRuntime.settleChemicalEscrow(state,host)
            &&gregtech.common.misc.WirelessNetworkManager.getUserEU(wirelessOwner).subtract(before).equals(b(1339)),
            "completed chemical closeout does not replay");
        state.n=(NBTTagCompound)ledger.copy();state.n.setInteger("chemicalSchema",1);
        state.n.setString("chemicalEscrowOwner",wirelessOwner.toString());state.n.setBoolean("uncertain",true);
        check(!CriticalEnergyRuntime.settleChemicalEscrow(state,host)&&CriticalEnergyRuntime.hasChemicalEscrow(state.n),
            "uncertain closeout retains escrow without replay");
        state.n.setBoolean("uncertain",false);
        state.n.getTagList("chemicalCredits",10).getCompoundTagAt(0).setString("remainingHeat","invalid");
        check(!CriticalEnergyRuntime.settleChemicalEscrow(state,host)&&state.n.getBoolean("uncertain"),
            "malformed closeout safely locks instead of throwing or discarding credits");
        System.out.println("PASS CriticalChemicalContract checks="+checks);
    }
    static class FakeHatch extends MTEHatchInput {
        FluidStack fluid; int stock,limit; boolean throwDrain; String requestTag;
        FakeHatch(){super("unused",1,new String[0],null);}
        @Override public boolean isValid(){return true;}
        @Override public FluidTankInfo[] getTankInfo(ForgeDirection side){return new FluidTankInfo[]{new FluidTankInfo(
            new FluidStack(fluid,stock),1000)};}
        @Override public FluidStack drain(ForgeDirection side,FluidStack request,boolean commit){
            if(throwDrain)throw new IllegalStateException("injected uncertain drain");
            requestTag=request.tag.getString("batch");
            int amount=Math.min(request.amount,Math.min(stock,limit));
            if(commit)stock-=amount;
            return new FluidStack(fluid,amount);
        }
    }
    static class FakeState extends CriticalMachineState {
        NBTTagCompound n;
        @Override public CriticalMachineKind getMachineKind(){return CriticalMachineKind.BATTERY;}
        @Override public NBTTagCompound energyRuntimeData(){return n;}
        @Override public void markDirty(){}
        @Override public boolean fail(String message){return false;}
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
