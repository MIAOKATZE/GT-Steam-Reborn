import java.lang.reflect.*;
import java.util.*;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.*;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.item.*;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.hatch.critical.*;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.*;
import gregtech.common.tileentities.machines.*;

/** Real input implementation and GT native lifecycle; finite AE inventory/energy seams, no live grid or GL. */
public final class CriticalMEInputContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalMEInputRuntime", "5.09.54.205");
    }
}

class CriticalMEInputRuntime {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static FluidStack fluid;
    static ItemStack item;
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static long displayStock(MetaTileEntity input, int slot) {
        try {
            Method method=input.getClass().getSuperclass().getDeclaredMethod("longStock",int.class);
            method.setAccessible(true);return (long)method.invoke(input,slot);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot invoke production stock display query",failure);
        }
    }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    static void set(Object target, String field, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field f=type.getDeclaredField(field); f.setAccessible(true); f.set(target,value); return; }
            catch (NoSuchFieldException absent) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(field);
    }
    static Object settings() throws Exception {
        Constructor<?> ctor=Class.forName("com.miaokatze.gtsr.common.machine.hatch.critical.CriticalMEInputSettings")
            .getDeclaredConstructor(); ctor.setAccessible(true); return ctor.newInstance();
    }
    static void bind(MetaTileEntity input) throws Exception {
        Tile tile=allocate(Tile.class);set(input,"mBaseMetaTileEntity",tile);tile.setMetaTileEntity(input);
        set(input,"longSettings",settings());
    }
    static InputFluid inputFluid(Network network) throws Exception {
        InputFluid input=allocate(InputFluid.class);input.network=network;input.online=true;bind(input);
        set(input,"slots",new MTEHatchInputME.Slot[16]);input.setSlotConfig(0,fluid);return input;
    }
    static InputItem inputItem(Network network) throws Exception {
        InputItem input=allocate(InputItem.class);input.network=network;input.online=true;bind(input);
        set(input,"slots",new MTEHatchInputBusME.Slot[16]);input.setSlotConfig(0,item);return input;
    }
    static Network network(long stock) throws Exception {
        Network n=allocate(Network.class);n.stock=stock;n.power=1e20;
        n.fluids=(IMEMonitor<IAEFluidStack>)Proxy.newProxyInstance(IMEMonitor.class.getClassLoader(),new Class[]{IMEMonitor.class},
            (proxy,method,args)->n.inventory(method,args,true));
        n.items=(IMEMonitor<IAEItemStack>)Proxy.newProxyInstance(IMEMonitor.class.getClassLoader(),new Class[]{IMEMonitor.class},
            (proxy,method,args)->n.inventory(method,args,false));
        n.storage=(IStorageGrid)Proxy.newProxyInstance(IStorageGrid.class.getClassLoader(),new Class[]{IStorageGrid.class},
            (proxy,method,args)->method.getName().equals("getFluidInventory")?n.fluids:n.items);
        n.energy=(IEnergyGrid)Proxy.newProxyInstance(IEnergyGrid.class.getClassLoader(),new Class[]{IEnergyGrid.class},
            (proxy,method,args)->{
                if(method.getName().equals("extractAEPower")) {
                    double actual=Math.min((double)args[0],n.power);
                    if(args[1]==Actionable.MODULATE){n.power-=actual;n.energyCommits++;}
                    return actual;
                }
                if(method.getReturnType()==boolean.class)return true;
                if(method.getReturnType()==double.class)return n.power;
                return null;
            });
        return n;
    }
    public static void run(String version) throws Exception {
        unsafe=Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null,cpw.mods.fml.relauncher.FMLRelaunchLog.class,"side",cpw.mods.fml.relauncher.Side.SERVER);
        appeng.core.AEConfig.instance=allocate(appeng.core.AEConfig.class);
        set(appeng.core.AEConfig.instance,"featureFlags",EnumSet.noneOf(appeng.core.features.AEFeature.class));
        set(appeng.core.AEConfig.instance,"grinderOres",new String[0]);
        net.minecraft.launchwrapper.Launch.classLoader.registerTransformer("appeng.transformer.asm.ASMIntegration");
        Field control=cpw.mods.fml.common.Loader.class.getDeclaredField("modController");control.setAccessible(true);
        Object controller=control.get(cpw.mods.fml.common.Loader.instance());
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="appliedenergistics2";
        metadata.name="AE detached input contract";metadata.version="rv3-beta-1080-GTNH";
        var modObjects=com.google.common.collect.HashBiMap.create();
        modObjects.put(new cpw.mods.fml.common.DummyModContainer(metadata),appeng.core.AppEng.instance());
        set(controller,"modObjectList",modObjects);
        fluid=new FluidStack(FluidRegistry.getFluid("steam"),1);item=new ItemStack(net.minecraft.init.Items.stick,1);
        for(Class<?> type:List.of(MTEHatchCriticalInputME.class,MTEHatchCriticalInputBusME.class,CriticalNativeMEFunding.class))
            System.out.println("SOURCE "+type.getSimpleName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
        for(long amount:new long[]{(long)Integer.MAX_VALUE+1,100_000_000_000L,Long.MAX_VALUE}) {
            Network fn=network(amount);InputFluid f=inputFluid(fn);var fs=f.getLongInputSource(0);
            check(fs.available()==amount,"fluid long amount "+amount);
            check(fs.extract(amount,false)==amount&&fn.commits==0&&fn.stock==amount,"fluid simulate unchanged "+amount);
            check(fs.extract(amount,true)==amount&&fn.stock==0&&fn.commits==1,"fluid exact single debit "+amount);
            check(fs.available()==0,"fluid rereads real stock after payment "+amount);
            Network in=network(amount);InputItem i=inputItem(in);var is=i.getLongInputSource(0);
            check(is.available()==amount,"item long amount "+amount);
            check(is.extract(amount,true)==amount&&in.stock==0&&in.commits==1,"item exact single debit "+amount);
        }
        capAndAvailability();nativeLifecycle();settingsRoundTrip();consumeAllItems();mainUiStock();
        System.out.println("PASS CriticalMEInputContract checks="+checks);
    }
    static void capAndAvailability() throws Exception {
        Network n=network(100_000_000_000L);InputFluid f=inputFluid(n);f.setLongSlotLimit(0,73);
        var source=f.getLongInputSource(0);
        check(source.available()==73&&source.extract(74,true)==0&&n.commits==0,"configured long cap is actual source limit");
        f.setLongSlotLimit(0,Long.MAX_VALUE);n.power=0;
        check(source.available()==0&&source.extract(1,true)==0&&n.stock==100_000_000_000L,"no energy means zero debit");
        n.power=1e20;n.stock=12;
        check(source.extract(13,true)==0&&n.commits==0&&n.stock==12,"insufficient complete request means zero debit");
        f.online=false;
        check(source.available()==0&&source.extract(1,true)==0,"disconnected retained source cannot debit");
        f.online=true;f.setSlotConfig(0,null);
        check(source.available()==0&&source.extract(1,true)==0,"removed marking invalidates retained source");
        f.setSlotConfig(0,fluid);f.network=network(100);
        check(source.available()==0&&source.extract(1,true)==0,"network change invalidates retained source");
        Network partial=network(100);partial.partial=7;InputItem i=inputItem(partial);
        check(i.getLongInputSource(0).extract(50,true)==7&&partial.stock==93,"unexpected modulate partial returns actual debit");
    }
    static void nativeLifecycle() throws Exception {
        Network n=network(100_000_000_000L);InputItem i=inputItem(n);
        i.startRecipeProcessing();
        check(i.getStackInSlot(0).stackSize==Integer.MAX_VALUE,"ordinary item machine sees native int maximum");
        i.getStackInSlot(0).stackSize-=17;
        check(i.endRecipeProcessing(null).wasSuccessful()&&n.stock==100_000_000_000L-17&&n.commits==1,"ordinary item native end commits once");
        i.setLongSlotLimit(0,23);i.startRecipeProcessing();
        check(i.getStackInSlot(0).stackSize==23,"native item view respects configured cap");
        i.endRecipeProcessing(null);check(n.commits==1,"unchanged native item view causes no extra debit");
        Network fn=network(100_000_000_000L);InputFluid f=inputFluid(fn);f.setLongSlotLimit(0,31);
        f.startRecipeProcessing();
        check(f.getStoredFluids()[0].amount==31,"ordinary fluid machine sees min int and long cap");
        f.getStoredFluids()[0].amount-=11;
        check(f.endRecipeProcessing(null).wasSuccessful()&&fn.stock==100_000_000_000L-11&&fn.commits==1,"ordinary fluid native end commits once");
        f.setLongSlotLimit(0,Long.MAX_VALUE);f.startRecipeProcessing();
        check(f.getLongInputSource(0).extract(100_000_000_000L-11,true)==100_000_000_000L-11,"long path can pay while fake view is open");
        f.endRecipeProcessing(null);check(fn.commits==2&&fn.stock==0,"long payment does not mutate fake view or double debit native end");
    }
    static void settingsRoundTrip() throws Exception {
        Object a=settings(),b=settings();Class<?> type=a.getClass();
        Method set=type.getDeclaredMethod("set",int.class,long.class),get=type.getDeclaredMethod("get",int.class);
        Method save=type.getDeclaredMethod("save",NBTTagCompound.class),load=type.getDeclaredMethod("load",NBTTagCompound.class);
        for(Method m:List.of(set,get,save,load))m.setAccessible(true);
        for(int slot=0;slot<16;slot++)set.invoke(a,slot,Long.MAX_VALUE-slot);
        NBTTagCompound tag=new NBTTagCompound();save.invoke(a,tag);load.invoke(b,tag);
        for(int slot=0;slot<16;slot++)check((long)get.invoke(b,slot)==Long.MAX_VALUE-slot,"NBT exact long slot "+slot);
        load.invoke(b,new NBTTagCompound());check((long)get.invoke(b,0)==Long.MAX_VALUE,"old/missing long NBT retains unlimited marked view");
        var sync=new com.cleanroommc.modularui.value.sync.LongSyncValue(()->Long.MAX_VALUE-1);
        var packet=new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());sync.write(packet);
        check(packet.readLong()==Long.MAX_VALUE-1,"main UI stock sync packet retains all 63 bits");
    }
    static void consumeAllItems() throws Exception {
        Network n=network(100_000_000_000L);InputItem a=inputItem(n),b=inputItem(n);b.setLongSlotLimit(0,19);
        var paid=CriticalNativeMEFunding.consumeAllItem(null,List.of(a,b,a),item);
        check(paid.result==CriticalNativeMEFunding.Result.SUCCESS&&paid.amount==100_000_000_000L&&n.stock==0&&n.commits==1,
            "100G consumeAllItem uses real long source and same-network/duplicate bus dedup");
    }
    static void mainUiStock() throws Exception {
        Network n=network(100_000_000_000L);InputFluid f=inputFluid(n);
        Field slots=MTEHatchInputME.class.getDeclaredField("slots");slots.setAccessible(true);
        var gui=new gregtech.common.gui.modularui.hatch.MTEHatchInputMEGui(f,(MTEHatchInputME.Slot[])slots.get(f));
        var manager=new com.cleanroommc.modularui.value.sync.PanelSyncManager(
            new com.cleanroommc.modularui.value.sync.ModularSyncManager(false),true);
        Method create=gui.getClass().getDeclaredMethod("createContentSection",
            com.cleanroommc.modularui.screen.ModularPanel.class,com.cleanroommc.modularui.value.sync.PanelSyncManager.class);
        create.setAccessible(true);
        var content=(com.cleanroommc.modularui.widget.ParentWidget<?>)create.invoke(gui,
            new com.cleanroommc.modularui.screen.ModularPanel("input-test"),manager);
        var row=(com.cleanroommc.modularui.widget.ParentWidget<?>)content.getChildren().get(0);
        Object filter=row.getChildren().get(0),middle=row.getChildren().get(1);
        Class<?> display=Class.forName("com.miaokatze.gtsr.common.machine.hatch.critical.CriticalMEInputDisplay");
        Method replace=display.getDeclaredMethod("fluids",com.cleanroommc.modularui.widget.ParentWidget.class,
            com.cleanroommc.modularui.value.sync.PanelSyncManager.class,java.util.function.IntFunction.class,
            java.util.function.IntToLongFunction.class);replace.setAccessible(true);
        java.util.function.IntToLongFunction available=index->displayStock(f,index);
        replace.invoke(null,content,manager,(java.util.function.IntFunction<FluidStack>)f::getSlotConfig,available);
        check(row.getChildren().get(0)==filter&&row.getChildren().get(1)==middle,"main UI keeps original type marking and centre operations");
        check(row.getChildren().get(2).getChildren().size()==16,"main UI read-only stock grid keeps all sixteen slots");
        var synced=(com.cleanroommc.modularui.value.sync.LongSyncValue)manager.getSyncHandler("criticalLongStock0:0");
        check(synced!=null&&synced.getLongValue()==100_000_000_000L,"main UI stock receives actual 100G instead of int display");
        n.power=1_600_000;
        synced.updateCacheFromSource(false);
        check(synced.getLongValue()==100_000_000_000L,"fluid main UI retains100G stock despite1.6M AE energy cache");
        check(f.getLongInputSource(0).available()<100_000_000_000L&&f.getLongInputSource(0).extract(100_000_000_000L,true)==0
            &&n.commits==0&&n.energyCommits==0,"fluid payment remains energy limited with zero debit for100G request");
        f.setLongSlotLimit(0,19);synced.updateCacheFromSource(false);
        check(synced.getLongValue()==19,"fluid visible stock respects slot cap independent of energy");
        n.power=1e20;
        f.setLongSlotLimit(0,Long.MAX_VALUE);n.stock=Long.MAX_VALUE;synced.updateCacheFromSource(false);
        check(synced.getLongValue()==Long.MAX_VALUE,"main UI stock receives exact Long.MAX_VALUE");
        InputItem i=inputItem(network(100_000_000_000L));
        set(i,"inventoryHandler",new com.cleanroommc.modularui.utils.item.ItemStackHandler(2));
        set(i,"mInventory",new ItemStack[2]);
        slots=MTEHatchInputBusME.class.getDeclaredField("slots");slots.setAccessible(true);
        var busGui=new gregtech.common.gui.modularui.hatch.MTEHatchInputBusMEGui(i,(MTEHatchInputBusME.Slot[])slots.get(i));
        manager=new com.cleanroommc.modularui.value.sync.PanelSyncManager(
            new com.cleanroommc.modularui.value.sync.ModularSyncManager(false),true);
        create=busGui.getClass().getDeclaredMethod("createContentSection",
            com.cleanroommc.modularui.screen.ModularPanel.class,com.cleanroommc.modularui.value.sync.PanelSyncManager.class);
        create.setAccessible(true);
        content=(com.cleanroommc.modularui.widget.ParentWidget<?>)create.invoke(busGui,
            new com.cleanroommc.modularui.screen.ModularPanel("input-bus-test"),manager);
        row=(com.cleanroommc.modularui.widget.ParentWidget<?>)content.getChildren().get(0);
        filter=row.getChildren().get(0);middle=row.getChildren().get(1);
        replace=display.getDeclaredMethod("items",com.cleanroommc.modularui.widget.ParentWidget.class,
            com.cleanroommc.modularui.value.sync.PanelSyncManager.class,java.util.function.IntFunction.class,
            java.util.function.IntToLongFunction.class);replace.setAccessible(true);
        available=index->displayStock(i,index);
        replace.invoke(null,content,manager,(java.util.function.IntFunction<ItemStack>)i::getSlotConfig,available);
        check(row.getChildren().get(0)==filter&&row.getChildren().get(1)==middle,"item main UI keeps original marking/manual/circuit operations");
        check(row.getChildren().get(2).getChildren().size()==16,"item main UI stock grid keeps sixteen slots");
        synced=(com.cleanroommc.modularui.value.sync.LongSyncValue)manager.getSyncHandler("criticalLongStock0:0");
        check(synced.getLongValue()==100_000_000_000L,"item main UI sync exposes100G");
        i.network.power=1_600_000;synced.updateCacheFromSource(false);
        check(synced.getLongValue()==100_000_000_000L,"item main UI retains100G stock despite1.6M AE energy cache");
        check(i.getLongInputSource(0).available()<100_000_000_000L&&i.getLongInputSource(0).extract(100_000_000_000L,true)==0
            &&i.network.commits==0&&i.network.energyCommits==0,"item payment remains energy limited with zero debit for100G request");
        i.setLongSlotLimit(0,29);synced.updateCacheFromSource(false);
        check(synced.getLongValue()==29,"item visible stock respects slot cap independent of energy");
        i.online=false;synced.updateCacheFromSource(false);
        check(synced.getLongValue()==0,"disabled input clears displayed stock");
    }
    static class Tile extends BaseMetaTileEntity {
        @Override public void markDirty() {}
        @Override public boolean isAllowedToWork() { return true; }
    }
    static class Network extends AENetworkProxy {
        long stock;double power;int commits,energyCommits;long partial;
        IMEMonitor<IAEFluidStack> fluids;IMEMonitor<IAEItemStack> items;IStorageGrid storage;IEnergyGrid energy;
        private Network(){super(null,"detached",null,false);}
        public IStorageGrid getStorage(){return storage;}
        public IEnergyGrid getEnergy(){return energy;}
        public boolean isReady(){return true;}
        public boolean isActive(){return true;}
        Object inventory(Method method,Object[] args,boolean isFluid) {
            if(method.getName().equals("extractItems")) {
                long amount=Math.min(stock,((IAEStack)args[0]).getStackSize());
                if(args[1]==Actionable.MODULATE) {if(partial>0)amount=Math.min(amount,partial);stock-=amount;commits++;}
                IAEStack result=isFluid?AEFluidStack.create(fluid):AEItemStack.create(item);result.setStackSize(amount);
                return amount==0?null:result;
            }
            if(method.getName().equals("toString"))return "finite-input-monitor";
            if(method.getName().equals("hashCode"))return System.identityHashCode(this);
            throw new UnsupportedOperationException(method.getName());
        }
    }
    static class InputFluid extends MTEHatchCriticalInputME {
        Network network;boolean online;
        private InputFluid(){super("detached",9,new String[0],new ITexture[0][][]);}
        public boolean isAllowedToWork(){return online;}
        public boolean isValid(){return true;}
        public AENetworkProxy getProxy(){return network;}
    }
    static class InputItem extends MTEHatchCriticalInputBusME {
        Network network;boolean online;
        private InputItem(){super("detached",9,new String[0],new ITexture[0][][]);}
        public boolean isAllowedToWork(){return online;}
        public boolean isValid(){return true;}
        public AENetworkProxy getProxy(){return network;}
    }
}
