package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergyDebug;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoTunnel;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel;

/** Real GT205 type admission and physical funding paths; no network/server bootstrap. */
public final class CriticalFoundationPortsContract {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static void check(boolean result, String text) {
        if (!result) throw new AssertionError(text);
        checks++;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return;
            } catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    private static Host bind(MetaTileEntity hatch) throws Exception {
        Host host = allocate(Host.class);
        field(hatch, "mBaseMetaTileEntity", host);
        host.setMetaTileEntity(hatch);
        return host;
    }
    public static void main(String[] args) throws Exception {
        CriticalFoundationContract.source(CriticalHatchFunding.class, args);
        CriticalFoundationContract.source(CriticalNativeMEFunding.class, args);
        CriticalFoundationContract.source(com.miaokatze.gtsr.common.machine.MTECriticalMachine.class, args);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        unsafe = (sun.misc.Unsafe) access.get(null);
        MTEHatchEnergyTunnel laser = allocate(MTEHatchEnergyTunnel.class);
        String gtSource = MTEHatchEnergyTunnel.class.getProtectionDomain().getCodeSource().getLocation().toString();
        check(gtSource.contains("5.09.54.205"), "GT205 code source is bound: " + gtSource);
        check(laser.getHatchType() == 2 && CriticalMachineState.isSupportedLoomHatchMeta(laser), "Laser type two is a real supported loom input");
        check(CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchEnergyMulti.class)), "Multiamp energy remains accepted");
        check(!CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchDynamoTunnel.class)), "Laser dynamo remains excluded");
        check(CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchEnergyDebug.class)), "Actual GT debug energy subclass is accepted");
        check(CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchInputDebug.class)), "Actual GT debug fluid subclass is accepted");
        check(CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchInputBusDebug.class)), "Actual GT debug bus subclass is accepted");
        check(CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchInputBusME.class))
            && CriticalMachineState.isSupportedLoomHatchMeta(allocate(MTEHatchInputME.class)), "Native stocking item and fluid inputs remain accepted");
        Machine machine = allocate(Machine.class);
        field(machine, "mExoticEnergyHatches", new ArrayList<>());
        field(machine, "mEnergyHatches", new ArrayList<>());
        field(machine, "mInputBusses", new ArrayList<>());
        field(machine, "mInputHatches", new ArrayList<>());
        check(machine.addExoticEnergyInputToMachineList(bind(laser), 0), "Real GT exotic adder accepts laser");
        check(machine.getExoticAndNormalEnergyHatchList().contains(laser), "Native combined energy list includes the laser");
        Item critical = new Item(), ordinary = new Item();
        Bus bus = allocate(Bus.class);
        bus.items = new ItemStack[] { new ItemStack(critical, 9), new ItemStack(ordinary, 17) };
        bind(bus);
        CriticalNativeMEFunding.ItemConsumption consumed = CriticalHatchFunding.consumeAllItem(machine,
            Collections.singletonList(bus), new ItemStack(critical));
        check(consumed.result == CriticalNativeMEFunding.Result.SUCCESS && consumed.amount == 9, "All matching physical singularities are debited");
        check(bus.items[0] == null && bus.items[1].stackSize == 17, "Ordinary singularity type remains untouched");
        DebugBus debug = allocate(DebugBus.class);
        debug.items = new ItemStack[] { new ItemStack(critical, 512), new ItemStack(ordinary, 32) };
        bind(debug);
        ItemStack[] snapshots = CriticalHatchFunding.snapshotItems(Collections.singletonList(debug));
        check(snapshots.length == 2 && snapshots[0].stackSize == 512, "Debug slot zero is funding, last slot is circuit");
        check(CriticalHatchFunding.fund(machine, Collections.singletonList(debug), Collections.emptyList(),
            new ItemStack[] { new ItemStack(critical, 512), new ItemStack(ordinary, 32) }, new net.minecraftforge.fluids.FluidStack[0])
            == CriticalNativeMEFunding.Result.SUCCESS, "Debug phantom copies fund without reference identity errors");
        check(debug.items[0].stackSize == 512, "GT debug inputs retain their phantom configuration");
        check(CriticalHatchFunding.fund(machine, Collections.singletonList(debug), Collections.emptyList(),
            new ItemStack[] { new ItemStack(critical, 300), new ItemStack(critical, 300) }, new net.minecraftforge.fluids.FluidStack[0])
            == CriticalNativeMEFunding.Result.SHORTAGE, "Debug finite allowance cannot be reserved twice");
        OfflineBus offline = allocate(OfflineBus.class);
        offline.proxy = allocate(OfflineProxy.class);
        offline.proxy.offline = allocate(appeng.me.GridAccessException.class);
        bind(offline);
        consumed = CriticalNativeMEFunding.consumeAllItem(machine, Collections.singletonList(offline), new ItemStack(critical));
        check(consumed.result == CriticalNativeMEFunding.Result.SUCCESS && consumed.amount == 0,
            "A disconnected ME bus offers no singularities without creating an uncertain debit");
        System.out.println("PASS CriticalFoundationPortsContract checks=" + checks);
    }
    private static final class Host extends BaseMetaTileEntity { @Override public void markDirty() {} }
    private static final class Machine extends MTEMultiBlockBase {
        private Machine() { super("detached"); }
        @Override public IMetaTileEntity newMetaEntity(IGregTechTileEntity host) { return null; }
        @Override public ItemStack getMachineCraftingIcon() { return null; }
        @Override public String[] getDescription() { return new String[0]; }
        @Override public ITexture[] getTexture(IGregTechTileEntity host, ForgeDirection side, ForgeDirection front,
            int color, boolean active, boolean redstone) { return new ITexture[0]; }
    }
    private static final class Bus extends MTEHatchInputBus {
        private ItemStack[] items;
        private Bus() { super("detached", 9, 2, new String[0], new ITexture[0][][]); }
        @Override public int getSizeInventory() { return items.length; }
        @Override public int getCircuitSlot() { return -1; }
        @Override public ItemStack getStackInSlot(int slot) { return items[slot]; }
        @Override public ItemStack decrStackSize(int slot, int count) {
            ItemStack removed = items[slot].copy(); removed.stackSize = count;
            items[slot].stackSize -= count; if (items[slot].stackSize == 0) items[slot] = null; return removed;
        }
    }
    private static final class DebugBus extends MTEHatchInputBusDebug {
        private ItemStack[] items;
        private DebugBus() { super("detached", (byte) 9, new String[0], new ITexture[0][][]); }
        @Override public int getSizeInventory() { return items.length + 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot < items.length ? items[slot].copy() : null; }
    }
    private static final class OfflineBus extends MTEHatchInputBusME {
        private OfflineProxy proxy;
        private OfflineBus() { super("offline", false, 9, new String[0], new ITexture[0][][]); }
        @Override public boolean isAllowedToWork() { return true; }
        @Override public appeng.me.helpers.AENetworkProxy getProxy() { return proxy; }
    }
    private static final class OfflineProxy extends appeng.me.helpers.AENetworkProxy {
        private appeng.me.GridAccessException offline;
        private OfflineProxy() { super(null, "offline", null, false); }
        @Override public appeng.api.networking.storage.IStorageGrid getStorage() throws appeng.me.GridAccessException {
            throw offline;
        }
    }
}
