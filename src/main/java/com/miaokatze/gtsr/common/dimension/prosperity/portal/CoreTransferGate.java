package com.miaokatze.gtsr.common.dimension.prosperity.portal;

import java.util.function.BooleanSupplier;

/** A failed transfer keeps its contact lease; retries consume at most one attempt per second. */
public final class CoreTransferGate {

    private long nextAttempt = Long.MIN_VALUE;
    private long nextMessage = Long.MIN_VALUE;

    public boolean attempt(long tick, BooleanSupplier transfer, Runnable completed) {
        if (tick < nextAttempt) return false;
        nextAttempt = tick + 20;
        if (!transfer.getAsBoolean()) return false;
        completed.run();
        return true;
    }

    public boolean reportFailure(long tick) {
        if (tick < nextMessage) return false;
        nextMessage = tick + 100;
        return true;
    }
}
