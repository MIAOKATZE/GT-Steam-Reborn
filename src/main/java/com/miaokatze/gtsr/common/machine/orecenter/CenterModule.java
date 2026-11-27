package com.miaokatze.gtsr.common.machine.orecenter;

/** Stable package and assembly identities. */
public enum CenterModule {

    CRUSHER("crusher", 0, 1, 2000, 0, 10, 50, 3, 10),
    WASHER("washer", 0, 1, 500, 0, 10, 50, 3, 10),
    SIFTER("sifter", 0, 1, 1000, 0, 10, 50, 3, 10),
    CENTRIFUGE("centrifuge", 0, 1, 3000, 0),
    SMELTING("smelting", 0, 1, 2000, 0),
    THERMAL_CENTRIFUGE("thermalCentrifuge", 1, 1, 1000, 16),
    SEPARATOR("separator", 1, 1, 300, 12),
    ELECTROLYZER("electrolyzer", 1, 1, 1000, 24),
    EXTRACTOR("extractor", 1, 1, 600, 12),
    REACTOR("reactor", 2, 1, 2400, 32, 75, 75, 9, 72),
    FURNACE("furnace", 2, 1, 4000, 64, 75, 75, 9, 72),
    DRYER("dryer", 3, 1, 4000, 64),
    SPEED("speed", 0, 0, 0, 0),
    PARALLEL("parallel", 0, 0, 0, 0),
    YIELD("yield", 0, 0, 0, 0),
    SECONDARY("secondary", 0, 0, 0, 0),
    LOGISTICS("logistics", 0, 2, 0, 0);

    public final String key;
    public final int tier;
    public final int kind;
    /** Remaining percentages of native recipe duration and total processing energy. */
    public final int recipeTimePercent, recipeEnergyPercent;
    private final int speedII, speedIII;
    final int steamRate;
    final int standbyEU;

    CenterModule(String key, int tier, int kind, int steamRate, int standbyEU) {
        this(key, tier, kind, steamRate, standbyEU, 50, 10, 6, 48);
    }

    CenterModule(String key, int tier, int kind, int steamRate, int standbyEU, int recipeTimePercent,
        int recipeEnergyPercent, int speedII, int speedIII) {
        this.key = key;
        this.tier = tier;
        this.kind = kind;
        this.steamRate = steamRate;
        this.standbyEU = standbyEU;
        this.recipeTimePercent = recipeTimePercent;
        this.recipeEnergyPercent = recipeEnergyPercent;
        this.speedII = speedII;
        this.speedIII = speedIII;
    }

    public int gradeSpeed(int grade) {
        return switch (grade) {
            case 1 -> 1;
            case 2 -> speedII;
            case 3 -> speedIII;
            default -> throw new IllegalArgumentException("Invalid processing package grade: " + grade);
        };
    }

    public double timeMultiplier(int grade) {
        return recipeTimePercent / (100.0 * gradeSpeed(grade));
    }

    public double energyMultiplier() {
        return recipeEnergyPercent / 100.0;
    }

    public boolean acceptsGrade(int grade, int centerTier) {
        return centerTier >= tier && grade >= 1 && grade <= (centerTier >= 3 ? 3 : centerTier >= 2 ? 2 : 1);
    }
}
