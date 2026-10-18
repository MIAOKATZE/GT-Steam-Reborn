package com.miaokatze.gtsr.common.critical;

public enum CriticalMachineKind {

    SOLAR("solar"),
    TURBINE("turbine"),
    PROCESSING("processing"),
    ENTANGLER("entangler"),
    SUN("sun"),
    DIMENSION("dimension"),
    BATTERY("battery"),
    ASSEMBLY("assembly"),
    ACCELERATOR("accelerator");

    public final String key;

    CriticalMachineKind(String key) {
        this.key = key;
    }

    /** Keep enum ordinals stable: frozen controllers remain valid in existing saves. */
    public boolean isReleased() {
        return this == SOLAR || this == TURBINE || this == SUN || this == BATTERY;
    }

    public static CriticalMachineKind[] releasedValues() {
        return new CriticalMachineKind[] { SOLAR, TURBINE, SUN, BATTERY };
    }

    public CriticalMachineKind nextReleased() {
        CriticalMachineKind[] kinds = values();
        for (int offset = 1; offset <= kinds.length; offset++) {
            CriticalMachineKind next = kinds[(ordinal() + offset) % kinds.length];
            if (next.isReleased()) return next;
        }
        return SUN;
    }

    public static CriticalMachineKind byId(int id) {
        return values()[Math.max(0, Math.min(values().length - 1, id))];
    }
}
