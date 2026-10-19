package com.miaokatze.gtsr.common.terminal.entanglement;

/** Percent-point calculations shared by production and the terminal's one-second forecast. */
public final class EntanglementMath {

    private EntanglementMath() {}

    public static double pyrotheum(long amount) {
        return 5.0d * amount / (amount + 5000.0d);
    }

    public static double cryotheum(long amount) {
        return amount / (amount + 1000.0d);
    }

    public static double decayOverlimit(double value) {
        return Math.max(0, value - (value < 0.01d ? 0.01d : value / 2000.0d));
    }

    public static int grade(long[] amounts) {
        for (int grade = 2; grade >= 0; grade--) if (amounts[grade] > 0) return grade;
        return -1;
    }

    public static final class Forecast {

        public double heat, overlimit, instability, tear, heatGain, multiplier;
        public boolean chill, solidify, overlimitExplosion, collapse, tearExplosion, output;
        public int grade = -1;
    }

    public static Forecast predict(boolean critical, boolean formed, boolean allowed, boolean cycling,
        boolean outputSpace, double heat, double overlimit, double instability, double tear, long[] requested) {
        Forecast result = new Forecast();
        result.heat = heat;
        result.overlimit = overlimit;
        result.instability = instability;
        result.tear = critical ? tear : 0;
        result.multiplier = (1 + overlimit / 100) * (critical ? 1 + tear / 100 : 1);
        boolean fluids = formed && allowed;
        result.grade = fluids ? grade(requested) : -1;
        if (result.grade >= 0) {
            long amount = requested[result.grade];
            result.heatGain = 0.5d * new double[] { 0.5d, 1, 2 }[result.grade]
                * amount
                / (amount + (critical ? 5000.0d : 200000.0d))
                * result.multiplier;
            result.heat += result.heatGain;
            if (result.heat >= 100 && outputSpace) {
                result.output = true;
                result.heat = 0;
            }
        }
        if (fluids) {
            result.overlimit += pyrotheum(requested[3]);
            double cooling = cryotheum(requested[4]);
            result.chill = requested[4] > 0 && cooling > result.instability;
            result.instability = Math.max(0, result.instability - cooling);
            if (critical) {
                double reduction = 0.001d * requested[5];
                result.solidify = requested[5] > 0 && reduction > result.tear;
                result.tear = Math.max(0, result.tear - reduction);
            }
        }
        result.overlimit = decayOverlimit(result.overlimit);
        boolean working = fluids && !result.chill && !result.solidify && (cycling || result.grade >= 0);
        if (working) {
            if (result.overlimit > 200) result.instability += result.overlimit / 1000;
            if (critical) {
                if (result.instability > 50 && result.overlimit > 800)
                    result.tear += result.instability / 100 + result.overlimit / 1000;
                if (result.tear > 0 && requested[5] == 0) result.tear += 0.1d;
            }
        } else {
            result.instability = Math.max(0, result.instability - 0.1d);
            result.tear = Math.max(0, result.tear - 0.1d);
            result.heat = Math.max(0, result.heat - 1);
        }
        result.overlimitExplosion = critical ? result.overlimit >= 1000 : result.overlimit > 500;
        result.collapse = !result.overlimitExplosion && result.instability > 100;
        result.tearExplosion = critical && !result.overlimitExplosion && !result.collapse && result.tear > 100;
        return result;
    }
}
