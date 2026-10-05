package com.miaokatze.gtsr.client.encounter;

/** Maps real windups and repeated hits onto the imported articulated rigs, rather than an idle silhouette. */
public final class BossRigMotion {

    private BossRigMotion() {}

    public static String clip(boolean foundry, int skill) {
        if (foundry) return skill == 2 || skill == 5 ? "gravity_salvo" : skill == 4 ? "fault_line" : "seismic_impact";
        return skill == 1 || skill == 5 ? "summon_brood" : skill == 2 ? "command_pulse" : "brood_mortar";
    }

    public static double clock(boolean foundry, int skill, double tick) {
        if (foundry) {
            if (skill == 3) {
                double cycle = tick < 40 ? tick : 40 + (tick - 40) % 16;
                return tick < 40 ? tick * .55 : 22 + (cycle - 40) * 2.5;
            }
            if (skill == 2) return tick < 30 ? tick * 22 / 30 : Math.min(80, 22 + (tick - 30) * 1.25);
            if (skill == 5) return tick < 40 ? tick * 32 / 40 : Math.min(80, 32 + (tick - 40) * 2);
            if (skill == 4) return tick < 40 ? tick * 28 / 40 : Math.min(70, 28 + (tick - 40) * 2);
            return tick < 40 ? tick * 22 / 40 : Math.min(62, 22 + (tick - 40) * 2);
        }
        return skill == 1 || skill == 5 ? Math.min(84, tick * 1.3) : Math.min(76, tick * 1.25);
    }

    /** Whole-body motion supplements bone articulation without changing server collision or position. */
    public static double[] pose(boolean foundry, int skill, double tick) {
        if (!foundry) return new double[] { 0, 0, Math.sin(Math.min(1, tick / 40) * Math.PI) * .09 };
        double raise = tick < 40 ? Math.sin(Math.min(1, tick / 40) * Math.PI) * .18 : 0;
        if (skill == 2) return new double[] { 0, tick < 30 ? -Math.sin(tick / 30 * Math.PI) * 5 : -12, raise };
        if (skill == 3) {
            double impact = tick >= 40 ? (tick - 40) % 16 : 16;
            return new double[] { 0, 0, impact < 5 ? -.18 * (1 - impact / 5) : raise };
        }
        if (skill == 4) return new double[] { Math.sin(Math.min(1, tick / 40) * Math.PI) * 7, 0, raise };
        return new double[] { 0, Math.sin(Math.min(1, tick / 40) * Math.PI) * -6, raise };
    }
}
