package com.miaokatze.gtsr.client.encounter;

import java.util.Locale;

/** Actual synchronized HP, independent of the decorative hundred-HP layer count. */
final class BossHealthReadout {

    private BossHealthReadout() {}

    static String format(float health, float maxHealth) {
        float maximum = Float.isFinite(maxHealth) ? Math.max(0, maxHealth) : 0;
        float current = Float.isFinite(health) ? Math.max(0, Math.min(maximum, health)) : 0;
        return number(current) + " / " + number(maximum);
    }

    private static String number(float value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value)
            : String.format(Locale.ROOT, "%.1f", value);
    }
}
