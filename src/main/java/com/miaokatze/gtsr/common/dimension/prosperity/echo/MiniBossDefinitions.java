package com.miaokatze.gtsr.common.dimension.prosperity.echo;

/** DI16-DI25 independent profiles, retaining their original structure-design identities. */
final class MiniBossDefinitions {

    static final String[] CODES = { "di-16", "di-17", "di-18", "di-19", "di-20", "di-21", "di-22", "di-23", "di-24",
        "di-25" };
    static final String[] DESIGN_CODES = { "mb-pressure-auditor", "mb-return-overseer", "mb-weft-curator",
        "mb-tide-collector", "mb-parallax-master", "mb-oath-captain", "mb-ash-keeper", "mb-counterbeat",
        "mb-route-warden", "mb-anchor-surveyor" };
    static final String[] CLIPS = { "pressure_seal", "return_draw", "weft_cross", "tide_levy", "parallax_split",
        "oath_cut", "ash_names", "counterbeat", "route_bar", "anchor_boundary" };
    static final int[] WINDUPS = { 36, 40, 38, 42, 34, 30, 40, 32, 38, 44 };
    static final int[] COOLDOWNS = { 100, 110, 96, 120, 90, 84, 110, 108, 96, 120 };
    static final float[] DAMAGE = { 8, 7, 6, 8, 7, 10, 6, 7, 8, 9 };

    static int index(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return -1;
    }

    static String canonicalCode(String designCode) {
        for (int i = 0; i < DESIGN_CODES.length; i++) if (DESIGN_CODES[i].equals(designCode)) return CODES[i];
        return designCode;
    }
}
