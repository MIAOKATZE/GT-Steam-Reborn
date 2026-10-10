package com.miaokatze.gtsr.common.critical;

import gregtech.api.enums.GTValues;

public enum CriticalTier {

    T1(7, 8),
    T2(10, 11),
    T3(12, 14);

    public final int materialVoltageTier;
    public final int recipeVoltageTier;

    CriticalTier(int materialVoltageTier, int recipeVoltageTier) {
        this.materialVoltageTier = materialVoltageTier;
        this.recipeVoltageTier = recipeVoltageTier;
    }

    public long recipeVoltage() {
        return GTValues.V[recipeVoltageTier];
    }

    public long constructionVoltage() {
        return GTValues.V[materialVoltageTier];
    }

    public static CriticalTier byId(int id) {
        return values()[Math.max(0, Math.min(2, id))];
    }
}
