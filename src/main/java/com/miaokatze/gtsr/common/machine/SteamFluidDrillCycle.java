// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.machine;

/** One drill cycle uses one steam type. Real partial withdrawals remain prepaid until a cycle can finish paying. */
public final class SteamFluidDrillCycle {

    public static final int TICKS = 20;
    private int prepaid;
    private boolean superheated;
    private boolean uncertain;
    private int unresolvedRequest;

    public int prepaid() {
        return prepaid;
    }

    public boolean superheated() {
        return superheated;
    }

    public boolean isUncertain() {
        return uncertain;
    }

    public int unresolvedRequest() {
        return unresolvedRequest;
    }

    public void restore(int amount, boolean heated) {
        restore(amount, heated, false);
    }

    public void restore(int amount, boolean heated, boolean unresolved) {
        restore(amount, heated, unresolved, 0);
    }

    public void restore(int amount, boolean heated, boolean unresolved, int request) {
        prepaid = Math.max(0, Math.min(4_000, amount));
        unresolvedRequest = Math.max(0, Math.min(4_000, request));
        uncertain = unresolved || request != 0 || amount < 0 || amount > 4_000;
        superheated = heated && (prepaid > 0 || uncertain);
    }

    /** An external source may mutate before throwing. Never replay that unknown withdrawal, including after reload. */
    public boolean attemptPayment(int requested, boolean heated, Runnable payment) {
        if (uncertain) return false;
        if (requested <= 0 || requested > 4_000 || prepaid > 0 && heated != superheated)
            throw new IllegalArgumentException("Invalid drill payment request");
        superheated = heated;
        unresolvedRequest = requested;
        try {
            payment.run();
            unresolvedRequest = 0;
            return true;
        } catch (RuntimeException failure) {
            uncertain = true;
            return false;
        }
    }

    /** 0 = unavailable, 1 = ordinary, 2 = superheated. Never complete a prepaid cycle with another fuel. */
    public int select(int required, int ordinaryAvailable, int heatedAvailable) {
        if (uncertain) return 0;
        if (prepaid > 0)
            return prepaid + (superheated ? heatedAvailable : ordinaryAvailable) >= required ? (superheated ? 2 : 1)
                : 0;
        if (heatedAvailable >= required) return 2;
        return ordinaryAvailable >= required ? 1 : 0;
    }

    public void paid(int amount, boolean heated) {
        if (uncertain || amount < 0 || prepaid > 0 && heated != superheated)
            throw new IllegalArgumentException("Drill payment must retain its steam identity");
        if (amount == 0) return;
        prepaid += amount;
        unresolvedRequest = Math.max(0, unresolvedRequest - amount);
        superheated = heated;
    }

    public void spend(int required) {
        if (uncertain || required <= 0 || prepaid < required) throw new IllegalStateException("Unpaid drill cycle");
        prepaid -= required;
        if (prepaid == 0) superheated = false;
    }

    public static int output(int tier, int efficiency, int mode, boolean nether, boolean heated) {
        int maximum = tier == 1 ? 2_000 : 8_000;
        int output = (int) (200 + Math.max(0, Math.min(10_000, efficiency)) / 10_000F * (maximum - 200));
        if (mode == 1) output = (int) (output * .20);
        else if (mode == 2) output = (int) (output * .10);
        else if (mode == 3) output = (int) (output * (nether ? .05 : .005));
        return output * (heated ? 2 : 1);
    }
}
