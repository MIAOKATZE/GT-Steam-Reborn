import java.lang.reflect.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalRuntimeSnapshot;
import com.miaokatze.gtsr.common.machine.AmmoniaThermalPayment;

public final class GtsrFollowupContracts {

    static int checks;
    static sun.misc.Unsafe unsafe;

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("GtsrFollowupContracts", "5.09.54.265");
    }

    static class Port implements AmmoniaThermalPayment.Port {

        int stock, calls, limit = Integer.MAX_VALUE;
        boolean throwsAfterDebit;

        Port(int stock) {
            this.stock = stock;
        }

        public boolean available(int amount) {
            return stock >= amount;
        }

        public void debit(int amount, java.util.function.IntConsumer confirmed) {
            calls++;
            int paid = Math.min(stock, Math.min(limit, amount));
            stock -= paid;
            if (throwsAfterDebit) throw new IllegalStateException("unknown external receipt");
            confirmed.accept(paid);
        }
    }

    static NBTTagCompound save(AmmoniaThermalPayment p) {
        NBTTagCompound n = new NBTTagCompound();
        p.write(n);
        return n;
    }

    static void payment() {
        int[] commits = { 0 };
        Runnable mark = () -> commits[0]++;
        Port gas = new Port(10), steam = new Port(9);
        AmmoniaThermalPayment p = new AmmoniaThermalPayment();
        check(
            !p.pay(10, 20, gas, steam, mark) && gas.stock == 10 && steam.stock == 9 && gas.calls == 0,
            "joint prepare prevents either debit");
        steam.stock = 100;
        steam.limit = 5;
        check(
            !p.pay(10, 20, gas, steam, mark) && p.gasCredit() == 10 && p.steamCredit() == 5,
            "second-port short pay retains both receipts");
        check(gas.calls == 1 && steam.calls == 1, "each external side is called once");
        NBTTagCompound saved = save(p);
        AmmoniaThermalPayment restored = new AmmoniaThermalPayment();
        restored.read(saved);
        steam.limit = 100;
        check(
            restored.pay(10, 20, gas, steam, mark) && gas.calls == 1 && steam.stock == 80,
            "reload pays only missing steam");
        check(
            restored.gasCredit() == 0 && restored.steamCredit() == 0 && restored.status() == 0,
            "pair retires together exactly once");
        check(p.gasCredit() == 10 && p.steamCredit() == 5, "controller instances cannot share credits");
        gas = new Port(100);
        steam = new Port(100);
        gas.limit = 3;
        p = new AmmoniaThermalPayment();
        check(
            !p.pay(10, 20, gas, steam, mark) && p.gasCredit() == 3 && steam.calls == 0,
            "first-port short pay stops before second external debit");
        gas.limit = 100;
        check(
            p.pay(2, 4, gas, steam, mark) && p.gasCredit() == 1 && gas.calls == 1 && steam.stock == 96,
            "new thermal target consumes only current pair and retains surplus");
        gas = new Port(100);
        steam = new Port(100);
        steam.throwsAfterDebit = true;
        p = new AmmoniaThermalPayment();
        check(
            !p.pay(10, 20, gas, steam, mark) && p.status() == 2 && p.gasCredit() == 10,
            "unknown debit freezes with known first-side receipt");
        saved = save(p);
        check(
            saved.getCompoundTag("gtsrAmmoniaThermalPayment")
                .getString("debitResource")
                .equals("steam")
                && saved.getCompoundTag("gtsrAmmoniaThermalPayment")
                    .getInteger("debitAmount") == 20,
            "unknown side and requested amount remain traceable");
        restored = new AmmoniaThermalPayment();
        restored.read(saved);
        int calls = steam.calls;
        check(
            !restored.pay(10, 20, gas, steam, mark) && steam.calls == calls,
            "unknown receipt survives restart without automatic replay");
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("gtsrAmmoniaThermalPayment", "raw-future-data");
        restored.read(malformed);
        check(
            restored.status() == 2 && save(restored).equals(malformed),
            "unknown record type remains byte-equivalent and locked");
        p = new AmmoniaThermalPayment();
        p.read(new NBTTagCompound());
        check(p.status() == 0 && p.gasCredit() == 0, "legacy absent payment key restores empty ledger");
    }

    static NBTTagCompound save(CriticalMachineState s) {
        NBTTagCompound n = new NBTTagCompound();
        s.writeToNBT(n);
        return n;
    }

    static void state() {
        NBTTagCompound n = new NBTTagCompound(), energy = new NBTTagCompound();
        energy.setString("eu", "123");
        energy.setBoolean("uncertain", true);
        n.setTag("criticalEnergyRuntime", energy);
        CriticalMachineState state = new CriticalMachineState();
        state.readFromNBT(n);
        energy.setString("eu", "999");
        check(
            state.runtimeSnapshot()
                .energy()
                .getString("eu")
                .equals("123"),
            "load detaches runtime from caller NBT");
        CriticalRuntimeSnapshot snapshot = state.runtimeSnapshot();
        NBTTagCompound copy = snapshot.energy();
        copy.setString("eu", "456");
        check(
            snapshot.energy()
                .getString("eu")
                .equals("123")
                && state.runtimeSnapshot()
                    .energy()
                    .getString("eu")
                    .equals("123"),
            "snapshot and caller tags cannot mutate live state");
        copy = state.energyRuntimeData();
        copy.setString("eu", "456");
        check(
            state.runtimeSnapshot()
                .energy()
                .getString("eu")
                .equals("123"),
            "legacy public accessor now returns detached data");
        n = save(state);
        n.getCompoundTag("criticalEnergyRuntime")
            .setString("eu", "888");
        check(
            state.runtimeSnapshot()
                .energy()
                .getString("eu")
                .equals("123"),
            "save payload does not borrow live runtime");
        n = new NBTTagCompound();
        energy = new NBTTagCompound();
        energy.setString("eu", "invalid-bigint");
        n.setTag("criticalEnergyRuntime", energy);
        state.readFromNBT(n);
        check(
            !state.canRemove() && save(state).getCompoundTag("resourceNbtQuarantine")
                .equals(n),
            "invalid exact amount freezes and preserves original state");
        n = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound();
        item.setShort("id", (short) 32000);
        item.setByte("Count", (byte) 1);
        item.setInteger("slot", 0);
        list.appendTag(item);
        n.setTag("escrow", list);
        state.readFromNBT(n);
        check(
            !state.canRemove() && save(state).getCompoundTag("resourceNbtQuarantine")
                .equals(n),
            "unknown item registry identity is quarantined instead of discarded");
        n = new NBTTagCompound();
        list = new NBTTagList();
        item = new NBTTagCompound();
        item.setString("FluidName", "missing:fixture");
        item.setInteger("Amount", 12);
        item.setInteger("slot", 0);
        list.appendTag(item);
        n.setTag("pendingFluids", list);
        state.readFromNBT(n);
        check(
            !state.canRemove() && save(state).getCompoundTag("resourceNbtQuarantine")
                .equals(n),
            "unknown fluid registry identity is quarantined instead of discarded");
        n = new NBTTagCompound();
        list = new NBTTagList();
        item = new NBTTagCompound();
        new ItemStack(Items.iron_ingot, 7).writeToNBT(item);
        item.setInteger("slot", 0);
        item.setInteger("fullCount", 7);
        list.appendTag(item);
        n.setTag("inventory", list);
        state.readFromNBT(n);
        check(
            state.getStackInSlot(0).stackSize == 7 && !save(state).hasKey("resourceNbtQuarantine"),
            "valid legacy sparse resource journal still restores");
        n = new NBTTagCompound();
        energy = new NBTTagCompound();
        list = new NBTTagList();
        item = new FluidStack(FluidRegistry.WATER, 1).writeToNBT(new NBTTagCompound());
        NBTTagCompound opaque = new NBTTagCompound();
        opaque.setInteger("eu", 7);
        opaque.setString("items", "custom-fluid-payload");
        item.setTag("Tag", opaque);
        item.setString("exactAmount", "12");
        list.appendTag(item);
        energy.setTag("outputs", list);
        n.setTag("criticalEnergyRuntime", energy);
        state.readFromNBT(n);
        check(
            !save(state).hasKey("resourceNbtQuarantine"),
            "arbitrary fluid payload tags are not interpreted as machine financial schema");
    }

    static void longAE() throws Exception {
        Issue37Runtime
            .set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", cpw.mods.fml.relauncher.Side.SERVER);
        net.minecraft.launchwrapper.Launch.classLoader.registerTransformer("appeng.transformer.asm.ASMIntegration");
        appeng.core.AEConfig.instance = (appeng.core.AEConfig) unsafe.allocateInstance(appeng.core.AEConfig.class);
        for (String key : new String[] { "featureFlags", "grinderOres" }) {
            Field f = appeng.core.AEConfig.class.getDeclaredField(key);
            f.setAccessible(true);
            f.set(
                appeng.core.AEConfig.instance,
                key.equals("featureFlags") ? java.util.EnumSet.noneOf(appeng.core.features.AEFeature.class)
                    : new String[0]);
        }
        Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = "appliedenergistics2";
        metadata.name = "AE focused bootstrap";
        metadata.version = "rv3-beta-1095-GTNH";
        var objects = com.google.common.collect.HashBiMap.create();
        objects.put(new cpw.mods.fml.common.DummyModContainer(metadata), appeng.core.AppEng.instance());
        Issue37Runtime.set(controller, controller.getClass(), "modObjectList", objects);
        var hub = new com.miaokatze.gtsr.common.machine.MTESteamHubArray("ae-long-contract");
        for (String key : new String[] { "mSteamStored", "mStoredFluidType" }) {
            Field f = hub.getClass()
                .getDeclaredField(key);
            f.setAccessible(true);
            f.set(
                hub,
                key.equals("mSteamStored") ? Long.valueOf((long) Integer.MAX_VALUE + 17)
                    : FluidRegistry.getFluidStack("steam", 1));
        }
        Class<?> inventory = Class.forName("com.miaokatze.gtsr.common.crossmod.ae2.HubArrayMEInventory");
        Method read = inventory
            .getDeclaredMethod("readStoredAEStack", com.miaokatze.gtsr.common.machine.base.IHubArray.class);
        read.setAccessible(true);
        appeng.api.storage.data.IAEFluidStack stack = (appeng.api.storage.data.IAEFluidStack) read.invoke(null, hub);
        check(stack.getStackSize() == (long) Integer.MAX_VALUE + 17, "AE reports full long native balance");
        check(
            hub.getStoredFluidStack().amount == Integer.MAX_VALUE,
            "legacy FluidStack display and IO remain bounded int");
        var base = new gregtech.api.metatileentity.BaseMetaTileEntity();
        hub.setBaseMetaTileEntity(base);
        base.setMetaTileEntity(hub);
        Constructor<?> ctor = inventory.getDeclaredConstructor(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        ctor.setAccessible(true);
        Object monitor = ctor.newInstance(base);
        java.util.List<Long> diffs = new java.util.ArrayList<>();
        appeng.api.storage.IMEMonitorHandlerReceiver<appeng.api.storage.data.IAEFluidStack> receiver = new appeng.api.storage.IMEMonitorHandlerReceiver<>() {

            public boolean isValid(Object token) {
                return true;
            }

            public void onListUpdate() {}

            public void postChange(
                appeng.api.networking.storage.IBaseMonitor<appeng.api.storage.data.IAEFluidStack> source,
                Iterable<appeng.api.storage.data.IAEFluidStack> change,
                appeng.api.networking.security.BaseActionSource action) {
                for (var value : change) diffs.add(value.getStackSize());
            }
        };
        inventory.getMethod("addListener", appeng.api.storage.IMEMonitorHandlerReceiver.class, Object.class)
            .invoke(monitor, receiver, new Object());
        inventory.getMethod("onTick")
            .invoke(monitor);
        check(
            diffs.equals(java.util.List.of((long) Integer.MAX_VALUE + 17)),
            "AE first long report contains entire stock");
        diffs.clear();
        hub.mSteamStored = Long.MAX_VALUE;
        inventory.getMethod("onTick")
            .invoke(monitor);
        check(
            diffs.size() == 1 && diffs.get(0) == Long.MAX_VALUE - ((long) Integer.MAX_VALUE + 17),
            "AE difference reaches Long.MAX_VALUE without int clipping or overflow");
        diffs.clear();
        hub.mSteamStored = 2;
        inventory.getMethod("onTick")
            .invoke(monitor);
        check(
            diffs.size() == 1 && diffs.get(0) == 2 - Long.MAX_VALUE,
            "AE negative long delta conserves the former full balance");
    }

    public static void run(String version) throws Exception {
        unsafe = Issue37Runtime.bootstrap(version);
        payment();
        state();
        longAE();
        System.out.println("PASS followup native contracts checks=" + checks);
    }
}
