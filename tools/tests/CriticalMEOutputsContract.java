import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;

/** Actual narrow accessor transformation and real GT205/AE1080 quantity/cache codecs. */
public final class CriticalMEOutputsContract {
    public static void main(String[] args) throws Exception {
        ArrayList<URL> urls = new ArrayList<>();
        for (String item : System.getProperty("java.class.path").split(File.pathSeparator))
            urls.add(new File(item).toURI().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        Launch.minecraftHome = new File(".");
        for (String prefix : new String[] {"org.spongepowered.", "com.llamalad7.mixinextras.", "CriticalMEOutputsContract"})
            Launch.classLoader.addClassLoaderExclusion(prefix);
        Field platform = MixinBootstrap.class.getDeclaredField("platform");
        platform.setAccessible(true);
        platform.set(null, new org.spongepowered.asm.launch.platform.MixinPlatformManager() {
            @Override public void init() {}
            @Override public Collection<String> getPhaseProviderClasses() { return Collections.emptyList(); }
        });
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("mixins.gtsr.critical-output-probe.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData", Object[].class)
            .invoke(null, (Object) new Object[] {"7", "10", "99", "99", "1.7.10", "9.05", new File("."), new ArrayList<>()});
        Method run = Launch.classLoader.loadClass("CriticalMEOutputsRuntime").getMethod("run", String.class);
        run.setAccessible(true);
        run.invoke(null, "5.09.54.205");
    }
}

class CriticalMEOutputsRuntime {
    static int checks;
    static sun.misc.Unsafe unsafe;
    static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    static void set(Object target, String field, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(field); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException absent) {}
        }
        throw new NoSuchFieldException(field);
    }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    public static void run(String version) throws Exception {
        unsafe = Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", cpw.mods.fml.relauncher.Side.SERVER);
        appeng.core.AEConfig.instance = allocate(appeng.core.AEConfig.class);
        set(appeng.core.AEConfig.instance, "featureFlags", EnumSet.noneOf(appeng.core.features.AEFeature.class));
        set(appeng.core.AEConfig.instance, "grinderOres", new String[0]);
        Launch.classLoader.registerTransformer("appeng.transformer.asm.ASMIntegration");
        Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = "appliedenergistics2"; metadata.name = "AE detached API bootstrap"; metadata.version = "rv3-beta-1080-GTNH";
        var objects = com.google.common.collect.HashBiMap.create();
        objects.put(new cpw.mods.fml.common.DummyModContainer(metadata), appeng.core.AppEng.instance());
        set(controller, "modObjectList", objects);
        for (long amount : new long[] {(long) Integer.MAX_VALUE + 1, 100_000_000_000L, Long.MAX_VALUE}) {
            fluids(amount);
            items(amount);
        }
        ordinaryAndLegacy();
        partialAndNativeTransactions();
        cellTransferBoundary();
        System.out.println("PASS CriticalMEOutputsContract checks=" + checks);
    }

    static com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputME fluidHatch() throws Exception {
        var hatch = new FluidHatch("fixture-fluid", 4, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
        bind(hatch);
        proxy(hatch.getProvider(), false, 0);
        return hatch;
    }
    static com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputBusME itemBus() throws Exception {
        var bus = new ItemBus("fixture-item", 4, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
        bind(bus);
        proxy(bus.getProvider(), false, 0);
        return bus;
    }
    static void bind(gregtech.api.metatileentity.MetaTileEntity hatch) throws Exception {
        var tile = allocate(Tile.class);
        set(hatch, "mBaseMetaTileEntity", tile);
        tile.setMetaTileEntity(hatch);
    }
    static FakeProxy proxy(Object provider, boolean active, long capacity) throws Exception {
        FakeProxy proxy = allocate(FakeProxy.class);
        proxy.active = active;
        proxy.capacity = capacity;
        proxy.storage = (appeng.api.networking.storage.IStorageGrid) Proxy.newProxyInstance(
            appeng.api.networking.storage.IStorageGrid.class.getClassLoader(),
            new Class<?>[] {appeng.api.networking.storage.IStorageGrid.class}, (p, m, a) -> {
                if (m.getName().equals("getFluidInventory") || m.getName().equals("getItemInventory")) return proxy.monitor;
                return null;
            });
        proxy.monitor = (appeng.api.storage.IMEMonitor) Proxy.newProxyInstance(
            appeng.api.storage.IMEMonitor.class.getClassLoader(), new Class<?>[] {appeng.api.storage.IMEMonitor.class},
            (p, m, a) -> {
                if (m.getName().equals("injectItems")) {
                    appeng.api.storage.data.IAEStack input = (appeng.api.storage.data.IAEStack) a[0];
                    long accepted = Math.min(input.getStackSize(), proxy.capacity - proxy.stored);
                    if (a[1] == appeng.api.config.Actionable.MODULATE) { proxy.stored += accepted; proxy.commits++; }
                    return accepted == input.getStackSize() ? null : input.copy().setStackSize(input.getStackSize() - accepted);
                }
                return null;
            });
        proxy.energy = (appeng.api.networking.energy.IEnergyGrid) Proxy.newProxyInstance(
            appeng.api.networking.energy.IEnergyGrid.class.getClassLoader(),
            new Class<?>[] {appeng.api.networking.energy.IEnergyGrid.class}, (p, m, a) -> {
                if (m.getName().equals("extractAEPower")) return Math.min((double) a[0], 1e20);
                if (m.getReturnType() == boolean.class) return true;
                if (m.getReturnType() == double.class) return 1e20;
                return null;
            });
        set(provider, "proxy", proxy);
        return proxy;
    }
    static void fluids(long amount) throws Exception {
        var hatch = fluidHatch();
        FluidStack fluid = new FluidStack(FluidRegistry.getFluid("steam"), 1);
        var batch = hatch.beginLongOutput();
        check(batch.insert(fluid, amount) == amount && hatch.getProvider().getCachedAmount() == 0, "fluid simulate " + amount);
        batch.commit();
        check(hatch.getProvider().getCachedAmount() == amount && hatch.getProvider().getCacheList().get(0).getStackSize() == amount,
            "fluid real long contents " + amount);
        NBTTagCompound display = new NBTTagCompound();
        hatch.getProvider().getWailaNBTData(null, null, display, null, 0, 0, 0);
        check(display.getLong("cacheCapacity") == Long.MAX_VALUE && display.getTagList("stacks", 10).getCompoundTagAt(0).getLong("Amount") == amount,
            "fluid native display payload remains long " + amount);
        try { batch.commit(); throw new AssertionError("repeated fluid commit"); } catch (IllegalStateException expected) { checks++; }
        var second = hatch.beginLongOutput();
        check(second.insert(fluid, Long.MAX_VALUE) == Long.MAX_VALUE - amount, "fluid partial reservation " + amount);
        NBTTagCompound tag = new NBTTagCompound();
        hatch.getProvider().saveNBTData(tag);
        var restored = fluidHatch();
        restored.getProvider().loadNBTData(tag);
        restored.getProvider().loadNBTData(tag);
        check(restored.getProvider().getCachedAmount() == amount, "fluid native NBT repeated load no duplication " + amount);
        var network = proxy(restored.getProvider(), false, amount);
        restored.getProvider().flushCachedStack();
        check(network.stored == 0 && restored.getProvider().getCachedAmount() == amount, "fluid offline preservation " + amount);
        network.active = true;
        restored.getProvider().flushCachedStack();
        check(network.stored > 0 && network.stored + restored.getProvider().getCachedAmount() == amount,
            "fluid reconnect native powered flush and long conservation " + amount);
        long before = restored.getProvider().getCachedAmount();
        FluidStack normal = fluid.copy(); normal.amount = 12;
        int simulated = restored.fill(normal, false);
        int inserted = restored.fill(normal, true);
        check(simulated == inserted && restored.getProvider().getCachedAmount() == before + inserted, "fluid normal int API " + amount);
    }
    static void items(long amount) throws Exception {
        var bus = itemBus();
        ItemStack item = new ItemStack(net.minecraft.init.Items.iron_ingot, 1);
        var batch = bus.beginLongOutput();
        check(batch.insert(item, amount) == amount && bus.getProvider().getCachedAmount() == 0, "item simulate " + amount);
        batch.commit();
        check(bus.getProvider().getCachedAmount() == amount && bus.getProvider().getCacheList().get(0).getStackSize() == amount,
            "item real long contents " + amount);
        NBTTagCompound display = new NBTTagCompound();
        bus.getProvider().getWailaNBTData(null, null, display, null, 0, 0, 0);
        check(display.getLong("cacheCapacity") == Long.MAX_VALUE && display.getTagList("stacks", 10).getCompoundTagAt(0).getLong("Amount") == amount,
            "item native display payload remains long " + amount);
        try { batch.commit(); throw new AssertionError("repeated item commit"); } catch (IllegalStateException expected) { checks++; }
        NBTTagCompound tag = new NBTTagCompound();
        bus.getProvider().saveNBTData(tag);
        var restored = itemBus();
        restored.getProvider().loadNBTData(tag);
        restored.getProvider().loadNBTData(tag);
        check(restored.getProvider().getCachedAmount() == amount, "item native NBT repeated load no duplication " + amount);
        var network = proxy(restored.getProvider(), false, amount);
        restored.getProvider().flushCachedStack();
        check(network.stored == 0 && restored.getProvider().getCachedAmount() == amount, "item offline preservation " + amount);
        network.active = true;
        restored.getProvider().flushCachedStack();
        check(network.stored > 0 && network.stored + restored.getProvider().getCachedAmount() == amount,
            "item reconnect native powered flush and long conservation " + amount);
        long before = restored.getProvider().getCachedAmount();
        ItemStack normal = item.copy(); normal.stackSize = 12;
        ItemStack simulated = normal.copy();
        restored.storePartial(simulated, true);
        restored.storePartial(normal, false);
        check(normal.stackSize == simulated.stackSize && restored.getProvider().getCachedAmount() == before + 12 - normal.stackSize,
            "item normal int API " + amount);
    }
    static void ordinaryAndLegacy() throws Exception {
        var nativeFluid = new gregtech.common.tileentities.machines.outputme.MTEHatchOutputME(
            "ordinary-fluid", 4, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
        var nativeItem = new gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME(
            "ordinary-item", 4, new String[0], new gregtech.api.interfaces.ITexture[0][][]);
        check(nativeFluid.getProvider().getCacheCapacity() == 128_000 && nativeItem.getProvider().getCacheCapacity() == 1_600,
            "ordinary native instances retain their providers and capacities");
        var bus = itemBus();
        var ae = appeng.util.item.AEItemStack.create(new ItemStack(net.minecraft.init.Items.iron_ingot, 1));
        NBTTagCompound valid = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        entries.appendTag(bus.saveStackToNBT(ae.copy().setStackSize(Long.MAX_VALUE)));
        entries.appendTag(bus.saveStackToNBT(ae.copy().setStackSize(1)));
        valid.setTag("cache", entries);
        try { bus.getProvider().loadNBTData(valid); throw new AssertionError("oversized NBT"); }
        catch (IllegalStateException expected) { checks++; }
        var fluid = fluidHatch();
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluid.saveNBTData(fluidTag);
        net.minecraft.nbt.NBTTagList oldFluids = new net.minecraft.nbt.NBTTagList();
        oldFluids.appendTag(fluid.saveStackToNBT(appeng.util.item.AEFluidStack.create(
            new FluidStack(FluidRegistry.getFluid("steam"), 1)).setStackSize(100_000_000_000L)));
        fluidTag.setTag("cachedFluids", oldFluids);
        fluid.loadNBTData(fluidTag);
        fluid.loadNBTData(fluidTag);
        check(fluid.getProvider().getCachedAmount() == 100_000_000_000L, "legacy native fluid NBT migrates once");
        NBTTagCompound itemTag = new NBTTagCompound();
        bus.saveNBTData(itemTag);
        net.minecraft.nbt.NBTTagList oldItems = new net.minecraft.nbt.NBTTagList();
        oldItems.appendTag(bus.saveStackToNBT(ae.copy().setStackSize(100_000_000_000L)));
        itemTag.setTag("cachedItems", oldItems);
        bus.loadNBTData(itemTag);
        bus.loadNBTData(itemTag);
        check(bus.getProvider().getCachedAmount() == 100_000_000_000L, "legacy native item NBT migrates once");
        NBTTagCompound rewritten = new NBTTagCompound();
        bus.saveNBTData(rewritten);
        check(!rewritten.hasKey("cachedItems") && rewritten.getTagList("cache", 10).getCompoundTagAt(0).getLong("size") == 100_000_000_000L,
            "save uses one native cache entry with long amount");
    }
    static void partialAndNativeTransactions() throws Exception {
        var hatch = fluidHatch();
        FluidStack steam = new FluidStack(FluidRegistry.getFluid("steam"), 1);
        var fill = hatch.beginLongOutput(); fill.insert(steam, Long.MAX_VALUE - 7); fill.commit();
        var mixed = hatch.beginLongOutput();
        check(mixed.insert(steam, 5) == 5 && mixed.insert(new FluidStack(FluidRegistry.getFluid("ic2superheatedsteam"), 1), 5) == 2,
            "multi-fluid batch shares exact remaining capacity");
        mixed.commit();
        check(hatch.getProvider().getCachedAmount() == Long.MAX_VALUE && hatch.fill(steam, true) == 0, "full cache refuses without overflow");
        var bus = itemBus();
        var initial = bus.beginLongOutput();
        initial.insert(new ItemStack(net.minecraft.init.Items.iron_ingot, 1), Long.MAX_VALUE - 2); initial.commit();
        var tx = bus.createTransaction();
        ItemStack stack = new ItemStack(net.minecraft.init.Items.gold_ingot, 5);
        check(tx.storePartial(gregtech.api.util.GTUtility.ItemId.create(stack), stack, 1, 1) && stack.stackSize == 3,
            "ordinary native transaction partially reserves exact long capacity");
        check(bus.getProvider().getCachedAmount() == Long.MAX_VALUE - 2, "native transaction has zero early writes");
        tx.commit();
        check(bus.getProvider().getCachedAmount() == Long.MAX_VALUE, "native transaction commits once at capacity");
        try { tx.commit(); throw new AssertionError("native transaction repeated commit"); }
        catch (IllegalStateException expected) { checks++; }
        var transientHatch = fluidHatch();
        var pending = transientHatch.beginLongOutput(); pending.insert(steam, 20);
        transientHatch.getProvider().addToCache(appeng.util.item.AEFluidStack.create(steam).setStackSize(Long.MAX_VALUE - 10));
        try { pending.commit(); throw new AssertionError("stale batch"); }
        catch (IllegalStateException expected) { checks++; }
        check(transientHatch.getProvider().getCachedAmount() == Long.MAX_VALUE - 10, "stale batch refuses before all writes");
        var partial = fluidHatch();
        var pendingFluid = partial.beginLongOutput(); pendingFluid.insert(steam, 10); pendingFluid.commit();
        var network = proxy(partial.getProvider(), true, 2);
        partial.getProvider().flushCachedStack();
        check(network.stored == 2 && partial.getProvider().getCachedAmount() == 8, "native network partial flush preserves remainder");
        network.capacity = 10;
        partial.getProvider().flushCachedStack();
        check(network.stored == 10 && partial.getProvider().getCachedAmount() == 0, "native network reconnect retry never replays inserted contents");
    }
    static void cellTransferBoundary() throws Exception {
        var bus = itemBus();
        var iron = appeng.util.item.AEItemStack.create(new ItemStack(net.minecraft.init.Items.iron_ingot, 1));
        bus.getProvider().addToCache(iron.copy().setStackSize(Long.MAX_VALUE - 3));
        long[] cellContents = {100};
        appeng.api.storage.IMEInventoryHandler inventory = (appeng.api.storage.IMEInventoryHandler) Proxy.newProxyInstance(
            appeng.api.storage.IMEInventoryHandler.class.getClassLoader(), new Class<?>[] {appeng.api.storage.IMEInventoryHandler.class},
            (p, m, a) -> {
                switch (m.getName()) {
                    case "getStackType": return iron.getStackType();
                    case "getAvailableItems":
                        appeng.api.storage.data.IItemList list = (appeng.api.storage.data.IItemList) a[0];
                        list.add(iron.copy().setStackSize(cellContents[0])); return list;
                    case "extractItems":
                        long extracted = Math.min(((appeng.api.storage.data.IAEStack) a[0]).getStackSize(), cellContents[0]);
                        if (a[1] == appeng.api.config.Actionable.MODULATE) cellContents[0] -= extracted;
                        return iron.copy().setStackSize(extracted);
                    default: return null;
                }
            });
        appeng.api.storage.ICellHandler handler = (appeng.api.storage.ICellHandler) Proxy.newProxyInstance(
            appeng.api.storage.ICellHandler.class.getClassLoader(), new Class<?>[] {appeng.api.storage.ICellHandler.class},
            (p, m, a) -> {
                if (m.getName().equals("isCell")) return ((ItemStack) a[0]).getItem() == net.minecraft.init.Items.golden_carrot;
                if (m.getName().equals("getCellInventory")) return inventory;
                if (m.getReturnType() == boolean.class) return false;
                if (m.getReturnType() == int.class) return 0;
                return null;
            });
        appeng.api.AEApi.instance().registries().cell().addCellHandler(handler);
        Field slots = gregtech.api.metatileentity.CommonMetaTileEntity.class.getDeclaredField("mInventory");
        slots.setAccessible(true);
        ((ItemStack[]) slots.get(bus))[0] = new ItemStack(net.minecraft.init.Items.golden_carrot, 1);
        bus.getProvider().cellToCacheTransfer();
        check(cellContents[0] == 97 && bus.getProvider().getCachedAmount() == Long.MAX_VALUE,
            "cell transfer extracts only remaining capacity and preserves the untransferred cell contents");
        bus.getProvider().cellToCacheTransfer();
        check(cellContents[0] == 97 && bus.getProvider().getCacheCapacity() == Long.MAX_VALUE,
            "full output never extracts another cell item or lowers intrinsic capacity");
    }
    public static class FluidHatch extends com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputME {
        public FluidHatch(String n, int t, String[] d, gregtech.api.interfaces.ITexture[][][] x) { super(n,t,d,x); }
        @Override public void dispatchMarkDirty() {}
    }
    public static class ItemBus extends com.miaokatze.gtsr.common.machine.hatch.critical.MTEHatchCriticalOutputBusME {
        public ItemBus(String n, int t, String[] d, gregtech.api.interfaces.ITexture[][][] x) { super(n,t,d,x); }
        @Override public void dispatchMarkDirty() {}
    }
    public static class Tile extends gregtech.api.metatileentity.BaseMetaTileEntity {
        @Override public void markDirty() {}
    }
    public static class FakeProxy extends appeng.me.helpers.AENetworkProxy {
        boolean active;
        long capacity, stored;
        int commits;
        appeng.api.networking.storage.IStorageGrid storage;
        appeng.api.storage.IMEMonitor monitor;
        appeng.api.networking.energy.IEnergyGrid energy;
        public FakeProxy() { super(null,"unused",null,true); }
        @Override public boolean isActive() { return active; }
        @Override public appeng.api.networking.storage.IStorageGrid getStorage() { return storage; }
        @Override public appeng.api.networking.energy.IEnergyGrid getEnergy() { return energy; }
        @Override public void writeToNBT(NBTTagCompound tag) {}
        @Override public void readFromNBT(NBTTagCompound tag) {}
        @Override public appeng.api.networking.IGridNode getNode() { return null; }
    }
}
