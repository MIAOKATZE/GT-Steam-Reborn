package com.miaokatze.gtsr.common.machine.hatch.critical;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;

/** AE powered extraction with an overflow-free long ceiling, including Long.MAX_VALUE fluids. */
final class CriticalMETransfer {

    private CriticalMETransfer() {}

    static long powerUnits(long amount, int unit) {
        return amount / unit + (amount % unit == 0 ? 0 : 1);
    }

    static <S extends IAEStack<S>> long extract(IEnergySource energy, IMEInventory<S> inventory, S request,
        BaseActionSource source, boolean commit) {
        long wanted = request.getStackSize();
        if (wanted <= 0) return 0;
        int unit = request.getAmountPerUnit();
        double power = energy.extractAEPower(powerUnits(wanted, unit), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        long powered = Math.min(wanted, (long) (power * unit + 0.9));
        if (powered < wanted) return 0;
        S simulated = inventory.extractItems(request, Actionable.SIMULATE, source);
        if (simulated == null || simulated.getStackSize() < wanted) return 0;
        if (!commit) return wanted;
        S extracted = inventory.extractItems(request, Actionable.MODULATE, source);
        long actual = extracted == null ? 0 : extracted.getStackSize();
        if (actual > 0) energy.extractAEPower(powerUnits(actual, unit), Actionable.MODULATE, PowerMultiplier.CONFIG);
        return actual;
    }

    static <S extends IAEStack<S>> long available(IEnergySource energy, IMEInventory<S> inventory, S request,
        BaseActionSource source) {
        long limit = request.getStackSize();
        if (limit <= 0) return 0;
        S simulated = inventory.extractItems(request, Actionable.SIMULATE, source);
        if (simulated == null || simulated.getStackSize() <= 0) return 0;
        long stock = Math.min(limit, simulated.getStackSize());
        int unit = request.getAmountPerUnit();
        double power = energy.extractAEPower(powerUnits(stock, unit), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Math.min(stock, (long) (power * unit + 0.9));
    }
}
