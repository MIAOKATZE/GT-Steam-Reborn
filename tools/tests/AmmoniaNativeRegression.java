import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;

import gregtech.api.enums.VoidingMode;
import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Calls the production machine and GT205 transactions. World/AE transport is not booted. */
public final class AmmoniaNativeRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("AmmoniaNativeRuntime", args[0]);
    }
}

class AmmoniaNativeRuntime {
    private static final List<String> failures = new ArrayList<>();

    static void check(boolean ok, String label) {
        System.out.println("RESULT " + label + " " + (ok ? "PASS" : "FAIL"));
        if (!ok) failures.add(label);
    }

    static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> cls = object.getClass(); cls != null; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    static long pending(MTEAmmoniaPlant machine) throws Exception {
        Field field = MTEAmmoniaPlant.class.getDeclaredField("gtsr$pendingSuperheatedSteam");
        field.setAccessible(true);
        return field.getLong(machine);
    }

    static void push(MTEAmmoniaPlant machine, int amount) throws Exception {
        Method method = MTEAmmoniaPlant.class.getDeclaredMethod("pushSuperheatedSteam", int.class);
        method.setAccessible(true);
        method.invoke(machine, amount);
    }

    static MTEAmmoniaPlant machine(MTEHatchOutput... outputs) throws Exception {
        MTEAmmoniaPlant machine = new MTEAmmoniaPlant("ammonia-probe");
        for (MTEHatchOutput output : outputs) attach(output);
        attach(machine);
        machine.mOutputHatches.addAll(List.of(outputs));
        set(machine, "voidingMode", VoidingMode.VOID_NONE);
        return machine;
    }

    static void attach(Object machine) throws Exception {
        Object tile = java.lang.reflect.Proxy.newProxyInstance(AmmoniaNativeRuntime.class.getClassLoader(),
            new Class<?>[] { gregtech.api.interfaces.tileentity.IGregTechTileEntity.class }, (p, m, a) -> {
                if (m.getName().equals("getMetaTileEntity")) return machine;
                if (m.getName().equals("getFrontFacing")) return net.minecraftforge.common.util.ForgeDirection.NORTH;
                if (m.getName().equals("isServerSide") || m.getName().equals("isAllowedToWork")) return true;
                Class<?> type = m.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == byte.class) return (byte) 0;
                if (type == short.class) return (short) 0;
                return null;
            });
        set(machine, "mBaseMetaTileEntity", tile);
    }

    static class Ordinary extends MTEHatchOutput {
        Ordinary() { super("ordinary", 4, new String[0], null); }
        @Override public boolean isValid() { return true; }
    }

    static class SteamOnly extends MTESteamOutputHatch {
        SteamOnly() { super("steam", 1, new String[0], null); }
        @Override public boolean isValid() { return true; }
    }

    static class Pressure extends MTEPressureSteamOutputHatch {
        Pressure() { super("pressure", 2, new String[0], null); }
        @Override public boolean isValid() { return true; }
    }

    // Only AE network availability/filter metadata is controlled here; routing uses the real machine methods.
    static class ME extends MTEHatchOutputME {
        boolean filtered;
        boolean checks;
        boolean space = true;
        ME(boolean filtered) { super("me", 4, new String[0], null); this.filtered = filtered; }
        @Override public boolean isValid() { return true; }
        @Override public boolean isFiltered() { return filtered; }
        @Override public boolean isFilteredToFluid(GTUtility.FluidId id) { return true; }
        @Override public boolean hasPhysicalSpace() { return space; }
        @Override public boolean getCheckMode() { return checks; }
    }

    static class Input extends MTEHatchInput {
        int realLimit = Integer.MAX_VALUE;
        Input(FluidStack fluid) throws Exception {
            super("input", 10, new String[0], null);
            attach(this);
            setFillableStack(fluid);
        }
        @Override public boolean isValid() { return true; }
        @Override public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side,
            FluidStack request, boolean real) {
            FluidStack capped = request.copy();
            if (real) capped.amount = Math.min(capped.amount, realLimit);
            return super.drain(side, capped, real);
        }
    }

    static MTEAmmoniaPlant thermal(Input gas, Input steam) throws Exception {
        MTEAmmoniaPlant machine = machine();
        machine.mInputHatches.add(gas);
        machine.mInputHatches.add(steam);
        machine.mMachine = true;
        // Keep inherited structure/recipe startup checks outside this thermal-only fixture.
        Issue37Runtime.set(machine, MTEMultiBlockBase.class, "mStartUpCheck", 100000);
        Issue37Runtime.set(machine, MTEMultiBlockBase.class, "mUpdate", 100000);
        return machine;
    }

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("ammonia"));
        FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("refinerygas"));
        Materials.Steam.mGas = FluidRegistry.getFluid("steam");
        Materials.Gas.mGas = FluidRegistry.getFluid("refinerygas");
        System.out.println("SOURCE ammonia " + MTEAmmoniaPlant.class.getProtectionDomain().getCodeSource().getLocation());
        var steamOnly = new SteamOnly();
        var steamMachine = machine(steamOnly);
        push(steamMachine, 400);
        check(steamOnly.getFluidAmount() == 0 && pending(steamMachine) == 400, "AM02-steam-only-rejects-superheat");

        var ordinary = new Ordinary();
        var pressure = new Pressure();
        pressure.setFillableStack(FluidRegistry.getFluidStack("ic2superheatedsteam", pressure.getCapacity()));
        var split = machine(ordinary, pressure);
        push(split, 400);
        check(ordinary.getFluidAmount() == 0 && pending(split) == 400, "AM01-backpressure-keeps-ammonia-port-empty");
        check(split.getOutputHatches().contains(ordinary) && !split.getOutputHatches().contains(pressure),
            "AM01-primary-precheck-excludes-steam-port");

        var balance = machine();
        long original = (long) Integer.MAX_VALUE + 1000;
        set(balance, "gtsr$pendingSuperheatedSteam", original);
        push(balance, 400);
        check(pending(balance) == original + 400, "AM07-long-balance-above-int-retained");

        var locked = new Ordinary();
        locked.setLockedFluid(FluidRegistry.getFluid("ic2superheatedsteam"));
        locked.lockFluid(true);
        var lockMachine = machine(locked);
        push(lockMachine, 400);
        check(locked.getFluidAmount() == 400 && pending(lockMachine) == 0,
            "AM01-explicit-superheat-lock-still-receives");

        split.addOutput(FluidRegistry.getFluidStack("ammonia", 1000));
        check(ordinary.getFluidAmount() == 1000 && ordinary.getFluid().getFluid() == FluidRegistry.getFluid("ammonia"),
            "AM01-native-ammonia-delivery-after-side-backpressure");

        var mixedME = new ME(true);
        var meMachine = machine(mixedME);
        var ammoniaId = GTUtility.FluidId.create(FluidRegistry.getFluidStack("ammonia", 1));
        check(meMachine.getOutputHatches().isEmpty() && !meMachine.canDumpFluidToME(List.of(ammoniaId)),
            "AM01-ME-mixed-filter-side-port-cannot-bypass-primary-precheck");
        var primaryME = new ME(false);
        meMachine = machine(primaryME);
        check(meMachine.getOutputHatches().contains(primaryME) && meMachine.canDumpFluidToME(List.of(ammoniaId)),
            "AM01-unfiltered-ME-remains-primary-port");
        primaryME.checks = true;
        check(!meMachine.canDumpFluidToME(List.of(ammoniaId)), "AM01-ME-check-mode-keeps-native-capacity-check");

        var fractional = machine(new Pressure());
        set(fractional, "gtsr$pendingSuperheatedSteam", original);
        push(fractional, 400);
        check(pending(fractional) == original + 400 - 1024000, "AM07-subtract-actual-delivery-from-full-long");
        set(fractional, "voidingMode", VoidingMode.VOID_FLUID);
        push(fractional, 400);
        check(pending(fractional) == 0, "AM07-explicit-void-mode-discards-backlog");

        Input gas = new Input(Materials.Gas.getGas(200));
        Input steam = new Input(Materials.Steam.getGas(8000));
        var heating = thermal(gas, steam);
        for (long tick = 1; tick <= 20; tick++) heating.onPostTick(heating.getBaseMetaTileEntity(), tick);
        check(heating.mHeatLevel == 10 && gas.getFluidAmount() == 0 && steam.getFluidAmount() == 0,
            "AM03-twenty-paid-ticks-are-ten-heat");

        gas = new Input(Materials.Gas.getGas(0));
        steam = new Input(Materials.Steam.getGas(0));
        var pulse = thermal(gas, steam);
        for (long tick = 1; tick <= 20; tick++) {
            if (tick == 20) {
                gas.setFillableStack(Materials.Gas.getGas(10));
                steam.setFillableStack(Materials.Steam.getGas(400));
            }
            pulse.onPostTick(pulse.getBaseMetaTileEntity(), tick);
        }
        check(pulse.mHeatLevel == 0 && pending(pulse) == 400, "AM03-boundary-pulse-cannot-buy-whole-second-heat");

        gas = new Input(Materials.Gas.getGas(100));
        steam = new Input(Materials.Steam.getGas(700));
        var shortTick = thermal(gas, steam);
        shortTick.mHeatLevel = 10000;
        set(shortTick, "mMaxProgresstime", 1280);
        check(!shortTick.onRunningTick(null) && gas.getFluidAmount() == 100 && steam.getFluidAmount() == 700,
            "AM05-running-tick-shortage-does-not-partially-pay");

        gas = new Input(Materials.Gas.getGas(100));
        steam = new Input(Materials.Steam.getGas(5000));
        var paidTick = thermal(gas, steam);
        paidTick.mHeatLevel = 10000;
        set(paidTick, "mMaxProgresstime", 1280);
        check(paidTick.onRunningTick(null) && steam.getFluidAmount() == 3600 && gas.getFluidAmount() == 90
            && pending(paidTick) == 1400, "AM05-whole-tick-conserves-paid-steam");
        paidTick.mParallelCount = 256;
        set(paidTick, "mReactionTimeSec", 1);
        check(paidTick.onRunningTick(null) && steam.getFluidAmount() == 2200,
            "AM04-live-catalyst-change-does-not-change-batch-charge");

        gas = new Input(Materials.Gas.getGas(100));
        steam = new Input(Materials.Steam.getGas(5000));
        steam.realLimit = 100;
        var partial = thermal(gas, steam);
        partial.mHeatLevel = 10000;
        set(partial, "mMaxProgresstime", 1280);
        check(!partial.onRunningTick(null) && pending(partial) == 100 && steam.getFluidAmount() == 4900,
            "AM05-partial-real-extraction-converts-only-receipt-and-stops");

        var saved = new net.minecraft.nbt.NBTTagCompound();
        paidTick.saveNBTData(saved);
        var restored = thermal(new Input(Materials.Gas.getGas(100)), new Input(Materials.Steam.getGas(5000)));
        restored.loadNBTData(saved);
        restored.mParallelCount = 256;
        set(restored, "mReactionTimeSec", 1);
        check(restored.onRunningTick(null) && pending(restored) == 1400,
            "AM04-NBT-restores-frozen-charge-without-restoring-old-pending");

        FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("nitrogen"));
        var recipeMachine = thermal(new Input(Materials.Gas.getGas(1100)), new Input(Materials.Steam.getGas(5000)));
        recipeMachine.mInputHatches.add(new Input(FluidRegistry.getFluidStack("nitrogen", 1000)));
        var recipeOutput = new Ordinary();
        attach(recipeOutput);
        recipeMachine.mOutputHatches.add(recipeOutput);
        recipeMachine.mHeatLevel = 10000;
        gregtech.api.util.GTRecipeBuilder.builder()
            .fluidInputs(Materials.Gas.getGas(1000), FluidRegistry.getFluidStack("nitrogen", 1000))
            .fluidOutputs(FluidRegistry.getFluidStack("ammonia", 1000)).duration(1280).eut(0)
            .addTo(recipeMachine.getRecipeMap());
        check(recipeMachine.checkProcessing().wasSuccessful(), "AM04-native-ProcessingLogic-starts-real-one-parallel-recipe");
        recipeMachine.mParallelCount = 256;
        set(recipeMachine, "mReactionTimeSec", 1);
        check(recipeMachine.onRunningTick(null) && pending(recipeMachine) == 1400,
            "AM04-successful-start-freezes-fixed-load-charge-even-at-one-actual-parallel");
        if (!failures.isEmpty()) throw new AssertionError("Failed: " + failures);
    }
}
