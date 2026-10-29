import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.AEFluidStack;
import appeng.util.item.FluidList;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Real jar Ports, AEFluidStack and GT hatch types; finite monitor/storage/energy seams, no live grid. */
public final class CriticalLargeFluidPortsContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalLargeFluidPortsRuntime", "5.09.54.205");
    }
}

class CriticalLargeFluidPortsRuntime {
    static final java.io.PrintStream OUT = System.out;
    static sun.misc.Unsafe unsafe;
    static int checks;
    static FluidStack steam;
    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    static BigInteger b(long n) { return BigInteger.valueOf(n); }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value); return; }
            catch (NoSuchFieldException absent) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    static void bind(MetaTileEntity hatch) throws Exception {
        Tile tile=allocate(Tile.class); set(hatch,"mBaseMetaTileEntity",tile); tile.setMetaTileEntity(hatch);
    }
    static MTECriticalMachine machine() throws Exception {
        MTECriticalMachine host=allocate(MTECriticalMachine.class);
        for(String field:List.of("mInputHatches","mDualInputHatches","mOutputHatches","mOutputBusses"))
            set(host,field,new ArrayList<>());
        return host;
    }
    static NetworkProxy network(FiniteMonitor inventory) throws Exception {
        NetworkProxy proxy=allocate(NetworkProxy.class);
        proxy.storage=(IStorageGrid)Proxy.newProxyInstance(IStorageGrid.class.getClassLoader(),
            new Class<?>[]{IStorageGrid.class},(p,m,a)->m.getName().equals("getFluidInventory")?inventory.monitor:null);
        proxy.energy=(IEnergyGrid)Proxy.newProxyInstance(IEnergyGrid.class.getClassLoader(),
            new Class<?>[]{IEnergyGrid.class},(p,m,a)-> {
                if(m.getName().equals("extractAEPower")) {
                    double amount=Math.min((double)a[0],proxy.energyRemaining);
                    if(a[1]==Actionable.MODULATE) proxy.energyRemaining-=amount;
                    return amount;
                }
                if(m.getReturnType()==boolean.class)return true;
                if(m.getReturnType()==double.class)return proxy.energyRemaining;
                return null;
            });
        // Explicitly finite energy allowance; even Long.MAX_VALUE litres needs < 1e16 AE.
        proxy.energyRemaining=1e16;
        return proxy;
    }
    static Input input(NetworkProxy proxy) throws Exception {
        Input hatch=allocate(Input.class); hatch.proxy=proxy; bind(hatch); return hatch;
    }
    static Output output(NetworkProxy proxy) throws Exception {
        Output hatch=allocate(Output.class); hatch.proxy=proxy; bind(hatch); return hatch;
    }
    static CriticalEnergyPorts.OutputPlan outputs(MTECriticalMachine host,BigInteger amount) {
        return CriticalEnergyPorts.outputs(host,List.of(new CriticalEnergyPorts.FluidAmount(steam,amount)),new ItemStack[0]);
    }
    public static void run(String version) throws Exception {
        unsafe=Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null,cpw.mods.fml.relauncher.FMLRelaunchLog.class,"side",cpw.mods.fml.relauncher.Side.SERVER);
        // Only the configuration bootstrap is detached; the AE stack/API implementation stays real.
        appeng.core.AEConfig.instance=allocate(appeng.core.AEConfig.class);
        set(appeng.core.AEConfig.instance,"featureFlags",EnumSet.noneOf(appeng.core.features.AEFeature.class));
        set(appeng.core.AEConfig.instance,"grinderOres",new String[0]);
        net.minecraft.launchwrapper.Launch.classLoader.registerTransformer("appeng.transformer.asm.ASMIntegration");
        Field controllerField=cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller=controllerField.get(cpw.mods.fml.common.Loader.instance());
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();
        metadata.modId="appliedenergistics2";metadata.name="AE detached API bootstrap";metadata.version="rv3-beta-1080-GTNH";
        var aeContainer=new cpw.mods.fml.common.DummyModContainer(metadata);
        var modObjects=com.google.common.collect.HashBiMap.create();
        modObjects.put(aeContainer,appeng.core.AppEng.instance());
        set(controller,"modObjectList",modObjects);
        for(Class<?> type:List.of(CriticalEnergyPorts.class,AEFluidStack.class,MTEHatchInputME.class,MTEHatchOutputME.class))
            OUT.println("SOURCE "+type.getSimpleName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
        steam=new FluidStack(FluidRegistry.getFluid("steam"),1);
        long boiler=CriticalSolarPolicy.steamLitres(CriticalSolarPolicy.waterHalfHeat(
            CriticalSolarPolicy.batchBudgetEU(.6D,CriticalSolarPolicy.COOLANTS.get(2),
                CriticalSolarPolicy.EXCITERS.get(5),false)),0).longValueExact();
        check(boiler>10_000_000_000_000L,"full-load actual boiler catalogue exceeds 1e13 litres");
        for(long amount:new long[]{(long)Integer.MAX_VALUE+1,boiler}) roundTrip(amount);
        longOutputBoundary();
        inputBoundary();
        ordinaryOutputCapacity();
        mixedPhysicalOutputs();
        physicalItemInputsAndOutputs();
        OUT.println("VALUE fullLoadBoilerLitres="+boiler);
        OUT.println("PASS CriticalLargeFluidPortsContract checks="+checks);
    }
    static void roundTrip(long amount) throws Exception {
        MTECriticalMachine host=machine();
        FiniteMonitor source=new FiniteMonitor(amount,amount),sink=new FiniteMonitor(0,amount);
        NetworkProxy src=network(source),dst=network(sink);
        // Duplicate native hatches and duplicate config slots still refer to one finite inventory.
        Input a=input(src),a2=input(src); Output z=output(dst),z2=output(dst);
        set(host,"mInputHatches",new ArrayList<>(List.of(a,a,a2)));
        set(host,"mOutputHatches",new ArrayList<>(List.of(z,z,z2)));
        var snapshot=CriticalEnergyPorts.snapshot(host);
        check(snapshot.size()==1&&CriticalEnergyPorts.total(snapshot,steam).equals(b(amount)),"finite ME snapshot long and identity dedup "+amount);
        var debits=CriticalEnergyPorts.take(snapshot,steam,b(amount));
        check(CriticalEnergyPorts.canDebit(debits)&&source.stock==amount,"large debit simulate preserves exact stock "+amount);
        var plan=outputs(host,b(amount));
        check(plan!=null&&sink.stock==0&&sink.injectSimulations==1,"output simulate long and monitor dedup "+amount);
        BigInteger[] journal={BigInteger.ZERO};
        var paid=CriticalEnergyPorts.debitConfirmed(debits,(d,n)->journal[0]=journal[0].add(b(n)));
        check(paid.complete&&!paid.uncertain&&paid.confirmedLitres.equals(b(amount))&&journal[0].equals(b(amount))
            &&source.stock==0&&source.extractCommits==1,"actual Platform powered extraction and confirmed long receipt "+amount);
        plan.commit();
        check(sink.stock==amount&&sink.injectCommits==1,"ME output long commit without truncation "+amount);
        // Same monitor cannot promise its capacity twice through two ME output hatches.
        check(outputs(host,b(1))==null&&sink.stock==amount&&sink.injectCommits==1,"full finite monitor refuses without commit "+amount);
    }
    static void longOutputBoundary() throws Exception {
        MTECriticalMachine host=machine(); FiniteMonitor sink=new FiniteMonitor(0,Long.MAX_VALUE);
        NetworkProxy dst=network(sink);
        set(host,"mOutputHatches",new ArrayList<>(List.of(output(dst),output(dst))));
        check(outputs(host,b(Long.MAX_VALUE).add(BigInteger.ONE))==null&&sink.stock==0&&sink.injectCommits==0,
            "one duplicated monitor cannot cover Long.MAX_VALUE+1 or silently wrap");
        var plan=outputs(host,b(Long.MAX_VALUE));
        check(plan!=null&&sink.stock==0,"Long.MAX_VALUE output simulates exactly");
        plan.commit();
        check(sink.stock==Long.MAX_VALUE,"Long.MAX_VALUE output commits exactly");
        sink.stock=0;sink.refuse=true;
        check(outputs(host,b((long)Integer.MAX_VALUE+1))==null&&sink.injectCommits==1,"explicit monitor refusal never commits");
        sink.refuse=false; plan=outputs(host,b((long)Integer.MAX_VALUE+1)); sink.refuse=true;
        boolean rejected=false;
        try { plan.commit(); } catch(IllegalStateException changed) { rejected=true; }
        check(rejected&&sink.stock==0,"changed monitor rejection is explicit, never a successful silent truncation");
    }
    static void inputBoundary() throws Exception {
        MTECriticalMachine host=machine(); FiniteMonitor source=new FiniteMonitor(Long.MAX_VALUE,Long.MAX_VALUE);
        NetworkProxy src=network(source); set(host,"mInputHatches",new ArrayList<>(List.of(input(src))));
        var snapshot=CriticalEnergyPorts.snapshot(host);
        check(CriticalEnergyPorts.total(snapshot,steam).equals(b(Long.MAX_VALUE)),"input snapshot retains Long.MAX_VALUE");
        var debits=CriticalEnergyPorts.take(snapshot,steam,b(Long.MAX_VALUE));
        check(CriticalEnergyPorts.canDebit(debits),"Long.MAX_VALUE raw monitor simulation retains long");
        var paid=CriticalEnergyPorts.debitConfirmed(debits,(d,n)->{throw new AssertionError("no confirmed extraction expected");});
        check(!paid.complete&&paid.uncertain&&paid.confirmedLitres.signum()==0&&source.stock==Long.MAX_VALUE
            &&source.extractCommits==0,"AE1080 ceilDiv overflow is contained as uncertain, zero debit, no false long success");
        check(appeng.util.Platform.ceilDiv(Long.MAX_VALUE-1000,1000)>0,"upstream energy ceilDiv safe arithmetic boundary");
    }
    static void ordinaryOutputCapacity() throws Exception {
        MTECriticalMachine host=machine(); Standard a=allocate(Standard.class),z=allocate(Standard.class);
        bind(a);bind(z);set(host,"mOutputHatches",new ArrayList<>(List.of(a,z)));
        long amount=(long)Integer.MAX_VALUE+1;
        var plan=outputs(host,b(amount));
        check(plan!=null&&a.getFillableStack()==null&&z.getFillableStack()==null,"real standard GT transaction splits int chunks without early commit");
        plan.commit();
        check(a.getFillableStack().amount==Integer.MAX_VALUE&&z.getFillableStack().amount==1,"two physical hatches receive exact int-max plus one");
        a.setFillableStack(null);z.setFillableStack(null);
        set(host,"mOutputHatches",new ArrayList<>(List.of(a)));
        FiniteMonitor source=new FiniteMonitor(amount,amount);NetworkProxy src=network(source);
        set(host,"mInputHatches",new ArrayList<>(List.of(input(src))));
        check(outputs(host,b(amount))==null&&a.getFillableStack()==null&&source.stock==amount&&source.extractCommits==0,
            "insufficient physical output returns null without transaction commit or input debit");
    }
    static void mixedPhysicalOutputs() throws Exception {
        MTECriticalMachine host=machine();Standard a=allocate(Standard.class),z=allocate(Standard.class);
        bind(a);bind(z);a.acceptAll=true;z.acceptAll=true;
        Fluid other=new Fluid("gtsr_cross_hatch_other");FluidRegistry.registerFluid(other);
        List<CriticalEnergyPorts.FluidAmount> fluids=List.of(new CriticalEnergyPorts.FluidAmount(steam,b(1)),
            new CriticalEnergyPorts.FluidAmount(new FluidStack(other,1),b(1)));
        set(host,"mOutputHatches",new ArrayList<>(List.of(a,a)));
        check(CriticalEnergyPorts.outputs(host,fluids,new ItemStack[0])==null&&a.getFillableStack()==null,
            "two fluid identities cannot reserve the same empty physical tank twice through duplicate discovery");
        set(host,"mOutputHatches",new ArrayList<>(List.of(a,z,a)));
        var plan=CriticalEnergyPorts.outputs(host,fluids,new ItemStack[0]);
        check(plan!=null&&a.getFillableStack()==null&&z.getFillableStack()==null,
            "multiple fluid identities reserve separate tanks without early mutation");
        plan.commit();
        check(a.getFillableStack().isFluidEqual(steam)&&a.getFillableStack().amount==1
            &&z.getFillableStack().getFluid()==other&&z.getFillableStack().amount==1,
            "native multi-fluid transactions commit each identity to its own physical tank");
    }
    static void physicalItemInputsAndOutputs()throws Exception {
        MTECriticalMachine host=machine();
        PhysicalInputBus a=allocate(PhysicalInputBus.class),z=allocate(PhysicalInputBus.class);bind(a);bind(z);
        set(a,"mInventory",new ItemStack[]{new ItemStack(net.minecraft.init.Items.stick,3)});
        set(z,"mInventory",new ItemStack[]{new ItemStack(net.minecraft.init.Items.stick,5)});
        var inputs=new ArrayList<gregtech.api.metatileentity.implementations.MTEHatchInputBus>(List.of(a,z,a));
        set(host,"mInputBusses",inputs);
        ItemStack[] snapshot=host.criticalPhysicalItemsSnapshot();
        check(Arrays.stream(snapshot).mapToInt(stack->stack.stackSize).sum()==8,
            "physical item inputs combine all bus stacks with duplicate bus identity removed");
        var result=CriticalHatchFunding.fund(host,inputs,List.of(),new ItemStack[]{new ItemStack(net.minecraft.init.Items.stick,8)},new FluidStack[0]);
        check(result==CriticalNativeMEFunding.Result.SUCCESS&&a.getStackInSlot(0)==null&&z.getStackInSlot(0)==null,
            "one item requirement actually drains3+5 items across two native input buses");
        StandardBus first=allocate(StandardBus.class),second=allocate(StandardBus.class);bind(first);bind(second);
        set(first,"mInventory",new ItemStack[1]);set(second,"mInventory",new ItemStack[1]);
        set(host,"mOutputBusses",new ArrayList<>(List.of(first,first)));
        ItemStack[] wanted={new ItemStack(net.minecraft.init.Items.stick,80)};
        check(CriticalEnergyPorts.outputs(host,List.of(),wanted)==null&&first.getStackInSlot(0)==null,
            "duplicate item output bus cannot promise a single64-item slot twice");
        set(host,"mOutputBusses",new ArrayList<>(List.of(first,second,first)));
        var plan=CriticalEnergyPorts.outputs(host,List.of(),wanted);
        check(plan!=null&&first.getStackInSlot(0)==null&&second.getStackInSlot(0)==null,
            "80 items reserve across two64-item native bus slots without mutation");
        plan.commit();
        check(first.getStackInSlot(0).stackSize==64&&second.getStackInSlot(0).stackSize==16,
            "native output bus transactions actually split80 items into64+16");
        set(first,"mInventory",new ItemStack[1]);set(second,"mInventory",new ItemStack[1]);
        ItemStack[] mixed={new ItemStack(net.minecraft.init.Items.stick,64),new ItemStack(net.minecraft.init.Items.coal,64),
            new ItemStack(net.minecraft.init.Items.diamond,1)};
        check(CriticalEnergyPorts.outputs(host,List.of(),mixed)==null&&first.getStackInSlot(0)==null&&second.getStackInSlot(0)==null,
            "different output item identities share one transaction ledger; third item cannot reuse two reserved slots");
    }
    static final class FiniteMonitor implements InvocationHandler {
        long stock; final long capacity;
        int injectSimulations,injectCommits,extractCommits; boolean refuse;
        final IMEMonitor<IAEFluidStack> monitor;
        @SuppressWarnings("unchecked") FiniteMonitor(long stock,long capacity) {
            this.stock=stock;this.capacity=capacity;
            monitor=(IMEMonitor<IAEFluidStack>)Proxy.newProxyInstance(IMEMonitor.class.getClassLoader(),new Class<?>[]{IMEMonitor.class},this);
        }
        IAEFluidStack stack(long amount) { IAEFluidStack out=AEFluidStack.create(steam);out.setStackSize(amount);return out; }
        public Object invoke(Object p,Method method,Object[] args) {
            switch(method.getName()) {
                case "getStorageList": { FluidList list=new FluidList();if(stock>0)list.add(stack(stock));return list; }
                case "extractItems": {
                    IAEFluidStack request=(IAEFluidStack)args[0];long n=Math.min(stock,request.getStackSize());
                    if(args[1]==Actionable.MODULATE){stock-=n;extractCommits++;}
                    return n==0?null:stack(n);
                }
                case "injectItems": {
                    IAEFluidStack request=(IAEFluidStack)args[0];long n=refuse?0:Math.min(capacity-stock,request.getStackSize());
                    if(args[1]==Actionable.MODULATE){stock+=n;injectCommits++;}else injectSimulations++;
                    long left=request.getStackSize()-n;return left==0?null:stack(left);
                }
                case "toString":return "finite-fluid-monitor";
                case "hashCode":return System.identityHashCode(p);
                case "equals":return p==args[0];
                default:throw new UnsupportedOperationException(method.getName());
            }
        }
    }
    static final class Tile extends BaseMetaTileEntity { @Override public void markDirty() {} }
    static final class NetworkProxy extends AENetworkProxy {
        IStorageGrid storage;IEnergyGrid energy;double energyRemaining;
        private NetworkProxy(){super(null,"detached",null,false);}
        @Override public IStorageGrid getStorage(){return storage;}
        @Override public IEnergyGrid getEnergy(){return energy;}
    }
    static final class Input extends MTEHatchInputME {
        NetworkProxy proxy;
        private Input(){super("detached",false,9,new String[0],new ITexture[0][][]);}
        @Override public boolean isValid(){return true;}
        @Override public boolean isAllowedToWork(){return true;}
        @Override public AENetworkProxy getProxy(){return proxy;}
        @Override public FluidStack getSlotConfig(int slot){return slot<2?steam.copy():null;}
    }
    static final class Output extends MTEHatchOutputME {
        NetworkProxy proxy;
        private Output(){super("detached",9,new String[0],new ITexture[0][][]);}
        @Override public boolean isValid(){return true;}
        @Override public AENetworkProxy getProxy(){return proxy;}
        @Override public boolean canStoreFluid(FluidStack fluid){return fluid.isFluidEqual(steam);}
    }
    static final class PhysicalInputBus extends gregtech.api.metatileentity.implementations.MTEHatchInputBus {
        private PhysicalInputBus(){super("detached",1,new String[0],new ITexture[0][][]);}
        @Override public boolean isValid(){return true;}
        @Override public int getCircuitSlot(){return -1;}
    }
    static final class StandardBus extends gregtech.api.metatileentity.implementations.MTEHatchOutputBus {
        private StandardBus(){super("detached",1,new String[0],new ITexture[0][][]);}
        @Override public boolean isValid(){return true;}
        @Override public void markDirty(){}
    }
    static final class Standard extends MTEHatchOutput {
        boolean acceptAll;
        private Standard(){super("detached",9,new String[0],new ITexture[0][][]);}
        @Override public boolean isValid(){return true;}
        @Override public int getCapacity(){return Integer.MAX_VALUE;}
        @Override public boolean canStoreFluid(FluidStack fluid){return acceptAll||fluid.isFluidEqual(steam);}
        @Override public void markDirty(){}
        @Override public void onEmptyingContainerWhenEmpty(){}
    }
}
