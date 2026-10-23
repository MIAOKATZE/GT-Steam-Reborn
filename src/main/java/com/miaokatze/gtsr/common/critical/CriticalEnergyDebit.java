package com.miaokatze.gtsr.common.critical;

/** Confirmed prefix of a real debit. An uncertain tail must never be automatically retried. */
public final class CriticalEnergyDebit {

    public final long requested, confirmed;
    public final boolean uncertain;

    public CriticalEnergyDebit(long requested, long confirmed, boolean uncertain) {
        if (requested < 0 || confirmed < 0 || confirmed > requested) throw new IllegalArgumentException("debit");
        this.requested = requested;
        this.confirmed = confirmed;
        this.uncertain = uncertain;
    }

    public boolean complete() {
        return !uncertain && confirmed == requested;
    }
}
