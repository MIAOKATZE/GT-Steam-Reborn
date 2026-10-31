package com.miaokatze.gtsr.compat.gtnl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.IAdditionalSteamSourceHolder;
import com.miaokatze.gtsr.compat.gtnl.GTNLSteamPayment.Fuel;
import com.miaokatze.gtsr.compat.gtnl.GTNLSteamPayment.Receipt;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;

/** Actual payment helper with controlled local tank balances; no wireless network or game is booted. */
public final class GtnlPaymentContract {

    private GtnlPaymentContract() {}

    public static void main(String[] args) throws Exception {
        if (!GtnlNativeBootstrap.enter(GtnlPaymentContract.class.getName(), args)) return;
        Fluid ordinary = fluid("steam"), hot = fluid("ic2superheatedsteam");
        Fluid dense = fluid("densesupercriticalsteam"), compressed = fluid("gtnl_contract_compressed");
        List<Fuel> fuels = java.util.Arrays.asList(
            new Fuel(new FluidStack(ordinary, 1), 1),
            new Fuel(new FluidStack(hot, 1), 10),
            new Fuel(new FluidStack(dense, 1), 50),
            new Fuel(new FluidStack(compressed, 1), 1000));
        var holder = new GtnlCoolingContract.Holder();
        var pressure = new GtnlCoolingContract.Tank(10000);
        holder.pressure.add(pressure);

        Tanks mixed = new Tanks().with(ordinary, 10000)
            .with(hot, 1);
        require(!GTNLSteamPayment.canBoost(fuels, 1000, mixed), "1L hot sample must not qualify for acceleration");
        equal(0, mixed.realCalls, "boost eligibility is simulation only");
        Receipt paid = GTNLSteamPayment.pay(fuels, 1000, false, mixed);
        require(
            paid != null && paid.fluid()
                .getFluid() == ordinary,
            "native ordinary-first fuel order retained");
        equal(1000, paid.amount(), "ordinary physical request retains GTNL native energy result");
        GTNLSteamPayment.cool(holder, paid);
        equal(0, pressure.steam, "ordinary payment with a hot sample returns no pressure steam");
        equal(1000, holder.accumulated, "ordinary physical volume follows the 160:1 cooling ledger");
        equal(1, mixed.amount(hot), "unpaid hot sample is untouched");

        Tanks insufficient = new Tanks().with(ordinary, 999)
            .with(hot, 99);
        require(
            GTNLSteamPayment.pay(fuels, 1000, false, insufficient) == null,
            "insufficient fuels reject the payment");
        equal(0, insufficient.realCalls, "failed simulations do not partially pay either fuel");
        equal(999, insufficient.amount(ordinary), "insufficient ordinary balance remains intact");
        equal(99, insufficient.amount(hot), "insufficient hot balance remains intact");

        Tanks boosted = new Tanks().with(ordinary, 10000)
            .with(hot, 500);
        require(GTNLSteamPayment.canBoost(fuels, 1000, boosted), "full 4A hot payment qualifies");
        paid = GTNLSteamPayment.pay(fuels, 1000, true, boosted);
        require(
            paid != null && paid.fluid()
                .getFluid() == hot,
            "boosted mode pays only selected hot fuel");
        equal(400, paid.amount(), "native energy 1000 times four divided by native factor ten");
        equal(100, boosted.amount(hot), "boost makes one real physical payment");
        equal(10000, boosted.amount(ordinary), "boost never pays ordinary as a second charge");
        equal(1, boosted.realCalls, "one selected fuel produces one actual local-tank drain");
        GTNLSteamPayment.cool(holder, paid);
        equal(400, pressure.steam, "400L actual hot consumption returns exactly 400L ordinary steam");

        require(
            GTNLSteamPayment.pay(fuels, 1000, true, boosted) == null,
            "locked boost stops when hot becomes inadequate");
        equal(10000, boosted.amount(ordinary), "locked boost cannot fall back to abundant ordinary fuel");
        equal(1, boosted.realCalls, "failed locked payment has no extra real drain");
        equal(400, pressure.steam, "failed payment has no cooling credit");

        Tanks nativeDense = new Tanks().with(dense, 20);
        paid = GTNLSteamPayment.pay(fuels, 1000, false, nativeDense);
        require(paid != null, "GTNL dense fuel remains payable");
        equal(20, paid.amount(), "dense native energy factor fifty retained");
        GTNLSteamPayment.cool(holder, paid);
        equal(400, pressure.steam, "other GTNL fuel types gain no invented pressure cooling");
        equal(1000, holder.accumulated, "other GTNL fuel types gain no invented water cooling");
        Tanks tiny = new Tanks().with(hot, 1);
        paid = GTNLSteamPayment.pay(fuels, 1, true, tiny);
        require(paid != null, "sub-liter energy conversion still requires a physical liter");
        equal(1, paid.amount(), "native minimum physical payment preserved");

        Tanks boundary = new Tanks().with(hot, Integer.MAX_VALUE);
        require(
            GTNLSteamPayment.pay(fuels, Long.MAX_VALUE, true, boundary) == null,
            "unrepresentable request fails safely");
        equal(0, boundary.realCalls, "overflow cannot issue a negative or truncated real drain");
        nativePipeless(ordinary, hot);
        System.out.println(
            "PASS production GTNL payment: native conversion, sample exploit, simulation, lock and cooling receipts");
    }

    private static void nativePipeless(Fluid ordinary, Fluid hot) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe allocator = (sun.misc.Unsafe) field.get(null);
        Class<?> nativeType = PipelessSteamSupport.hatchClass();
        MTEHatch nativeHatch = tank(allocator, nativeType, hot, 1000, 0);
        Sources controller = new Sources();
        require(
            PipelessSteamSupport.add(controller, nativeHatch.getBaseMetaTileEntity(), 0),
            "real GTNL tier-0 hatch accepted");
        require(
            PipelessSteamSupport.add(controller, nativeHatch.getBaseMetaTileEntity(), 0),
            "same real hatch can be rediscovered");
        equal(1, PipelessSteamSupport.count(controller), "structure registration deduplicates the same hatch");
        require(
            PipelessSteamSupport
                .matches(nativeHatch, java.util.Collections.singletonList(PipelessSteamSupport.hatchClass())),
            "real tier-0 hatch passes structure candidate matching");
        equal(
            1000,
            PipelessSteamSupport.available(controller, new FluidStack(hot, 1)),
            "native tank balance discovered");
        equal(1000, nativeHatch.getFluidAmount(), "availability uses native simulation without payment");
        List<FluidStack> snapshot = PipelessSteamSupport.getStored(controller);
        equal(1, snapshot.size(), "one native hatch exposes one stock entry");
        snapshot.get(0).amount = 1;
        equal(1000, nativeHatch.getFluidAmount(), "stock snapshot does not expose mutable local tank storage");
        equal(
            400,
            PipelessSteamSupport.drain(controller, new FluidStack(hot, 1), 400, true),
            "native simulation returns requested volume");
        equal(1000, nativeHatch.getFluidAmount(), "native simulated drain leaves local storage untouched");
        equal(
            0,
            PipelessSteamSupport.drain(controller, new FluidStack(ordinary, 1), 400, false),
            "other fuel identity cannot drain selected native tank");
        equal(
            400,
            PipelessSteamSupport.drain(controller, new FluidStack(hot, 1), 400, false),
            "real public native drain pays physical volume");
        equal(600, nativeHatch.getFluidAmount(), "native local tank charged once without a network refill tick");
        equal(
            600,
            PipelessSteamSupport.drain(controller, new FluidStack(hot, 1), 1000, false),
            "depletion returns actual remaining physical volume");
        equal(0, nativeHatch.getFluidAmount(), "native tank can be exhausted");

        MTEHatch higherTier = tank(allocator, nativeType, hot, 1000, 1);
        require(
            !PipelessSteamSupport.add(controller, higherTier.getBaseMetaTileEntity(), 0),
            "higher-tier wireless hatch is outside pipeless contract");
        require(
            !PipelessSteamSupport
                .matches(higherTier, java.util.Collections.singletonList(PipelessSteamSupport.hatchClass())),
            "candidate matching also rejects higher tiers");
        MTEHatch ordinaryInput = tank(allocator, MTEHatchInput.class, ordinary, 1000, 0);
        require(
            !PipelessSteamSupport.add(controller, ordinaryInput.getBaseMetaTileEntity(), 0),
            "ordinary input hatch is not registered as an extra wireless source");
        equal(1, PipelessSteamSupport.count(controller), "rejected hatches do not change required-source count");

        Sources large = new Sources();
        MTEHatch first = tank(allocator, nativeType, hot, Integer.MAX_VALUE, 0);
        MTEHatch second = tank(allocator, nativeType, hot, 9, 0);
        PipelessSteamSupport.add(large, first.getBaseMetaTileEntity(), 0);
        PipelessSteamSupport.add(large, second.getBaseMetaTileEntity(), 0);
        long demand = Integer.MAX_VALUE + 9L;
        equal(
            demand,
            PipelessSteamSupport.drain(large, new FluidStack(hot, 1), demand, true),
            "long demand is simulated across int-sized native tanks");
        equal(
            demand,
            PipelessSteamSupport.drain(large, new FluidStack(hot, 1), demand, false),
            "long demand pays across native tanks without truncation");
        equal(0, first.getFluidAmount() + second.getFluidAmount(), "large payment exhausts both native tanks exactly");
        System.out.println(
            "PASS actual GTNL hatch: candidate/adder/count, native simulation/drain, local storage and long demand");
    }

    private static MTEHatch tank(sun.misc.Unsafe allocator, Class<?> type, Fluid fluid, int amount, int tier)
        throws Exception {
        MTEHatch hatch = (MTEHatch) allocator.allocateInstance(type);
        Field tierField = hatch.getClass()
            .getField("mTier");
        tierField.setAccessible(true);
        tierField.set(hatch, (byte) tier);
        hatch.mFluid = new FluidStack(fluid, amount);
        IGregTechTileEntity tile = (IGregTechTileEntity) Proxy.newProxyInstance(
            GtnlPaymentContract.class.getClassLoader(),
            new Class<?>[] { IGregTechTileEntity.class },
            (proxy, method, values) -> {
                if (method.getName()
                    .equals("getMetaTileEntity")) return hatch;
                if (method.getName()
                    .equals("getFrontFacing")) return net.minecraftforge.common.util.ForgeDirection.NORTH;
                if (method.getName()
                    .equals("isServerSide")) return true;
                Class<?> returned = method.getReturnType();
                if (returned == boolean.class) return false;
                if (returned == int.class) return 0;
                if (returned == long.class) return 0L;
                if (returned == byte.class) return (byte) 0;
                if (returned == short.class) return (short) 0;
                if (returned == float.class) return 0F;
                if (returned == double.class) return 0D;
                return null;
            });
        hatch.setBaseMetaTileEntity(tile);
        return hatch;
    }

    private static final class Sources implements IAdditionalSteamSourceHolder {

        private final List<MTEHatch> hatches = new ArrayList<>();

        @Override
        public List<MTEHatch> gtsr$getAdditionalSteamSources() {
            return hatches;
        }
    }

    private static Fluid fluid(String name) {
        if (FluidRegistry.getFluid(name) == null) FluidRegistry.registerFluid(new Fluid(name));
        return FluidRegistry.getFluid(name);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static void equal(long expected, long actual, String label) {
        require(expected == actual, label + ": expected=" + expected + ", actual=" + actual);
    }

    private static final class Tanks implements GTNLSteamPayment.Drain {

        private final Map<Fluid, Integer> amounts = new HashMap<>();
        private int realCalls;

        private Tanks with(Fluid fluid, int amount) {
            amounts.put(fluid, amount);
            return this;
        }

        private int amount(Fluid fluid) {
            return amounts.getOrDefault(fluid, 0);
        }

        @Override
        public int drain(FluidStack request, boolean simulate) {
            int paid = Math.min(request.amount, amount(request.getFluid()));
            if (!simulate) {
                realCalls++;
                amounts.put(request.getFluid(), amount(request.getFluid()) - paid);
            }
            return paid;
        }
    }
}
