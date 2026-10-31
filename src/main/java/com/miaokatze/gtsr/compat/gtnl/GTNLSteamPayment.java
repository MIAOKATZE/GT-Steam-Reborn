package com.miaokatze.gtsr.compat.gtnl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;

/** One selected, fully simulated upstream fuel payment and its physical cooling receipt. */
public final class GTNLSteamPayment {

    private GTNLSteamPayment() {}

    @FunctionalInterface
    public interface Drain {

        int drain(FluidStack request, boolean simulate);
    }

    public static final class Fuel {

        private final FluidStack fluid;
        private final int factor;

        public Fuel(FluidStack fluid, int factor) {
            this.fluid = fluid;
            this.factor = factor;
        }

        public FluidStack fluid() {
            return fluid;
        }

        public int factor() {
            return factor;
        }
    }

    public static final class Receipt {

        private final FluidStack fluid;
        private final int amount;

        public Receipt(FluidStack fluid, int amount) {
            this.fluid = fluid;
            this.amount = amount;
        }

        public FluidStack fluid() {
            return fluid;
        }

        public int amount() {
            return amount;
        }
    }

    public static List<Fuel> nativeFuels() {
        return NativeFuels.VALUES;
    }

    private static final class NativeFuels {

        private static final List<Fuel> VALUES = load();

        private static List<Fuel> load() {
            try {
                Class<?> types = Class.forName("com.science.gtnl.utils.enums.SteamTypes");
                List<Fuel> result = new ArrayList<>();
                for (Object value : types.getEnumConstants()) {
                    GTNLSteamType type = (GTNLSteamType) value;
                    result.add(
                        new Fuel(
                            type.gtsr$getFluid()
                                .getFluidStack(1),
                            type.gtsr$getEfficiencyFactor()));
                }
                return Collections.unmodifiableList(result);
            } catch (ClassNotFoundException missing) {
                throw new IllegalStateException("GTNL enhancement loaded without SteamTypes", missing);
            }
        }
    }

    public static boolean canBoost(List<Fuel> fuels, long energy, Drain drain) {
        if (energy < 0 || energy > Long.MAX_VALUE / 4) return false;
        for (Fuel fuel : fuels) {
            if (!superheated(fuel.fluid())) continue;
            FluidStack request = request(fuel, energy * 4);
            return request != null && drain.drain(request.copy(), true) >= request.amount;
        }
        return false;
    }

    public static Receipt pay(List<Fuel> fuels, long energy, boolean boosted, Drain drain) {
        if (energy < 0 || (boosted && energy > Long.MAX_VALUE / 4)) return null;
        long requestedEnergy = boosted ? energy * 4 : energy;
        for (Fuel fuel : fuels) {
            if (boosted && !superheated(fuel.fluid())) continue;
            FluidStack request = request(fuel, requestedEnergy);
            if (request == null || drain.drain(request.copy(), true) < request.amount) continue;
            int paid = drain.drain(request.copy(), false);
            if (paid != request.amount)
                throw new IllegalStateException("GTNL tank changed between simulation and payment");
            return new Receipt(request, paid);
        }
        return null;
    }

    private static FluidStack request(Fuel fuel, long energy) {
        long amount = Math.max(1, energy / fuel.factor());
        if (amount > Integer.MAX_VALUE) return null;
        FluidStack request = fuel.fluid()
            .copy();
        request.amount = (int) amount;
        return request;
    }

    private static boolean superheated(FluidStack fluid) {
        return "ic2superheatedsteam".equals(
            fluid.getFluid()
                .getName());
    }

    public static void cool(ICoolingHatchHolder holder, Receipt receipt) {
        String name = receipt.fluid()
            .getFluid()
            .getName();
        if (superheated(receipt.fluid())) SteamCoolingSupport.pushCoolingProducts(holder, receipt.amount(), true);
        else if ("steam".equals(name) || "ic2steam".equals(name)) {
            SteamCoolingSupport.pushCoolingProducts(holder, receipt.amount(), false);
        }
    }
}
