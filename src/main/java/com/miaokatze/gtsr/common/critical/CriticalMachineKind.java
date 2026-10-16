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

    public static CriticalMachineKind byId(int id) {
        return values()[Math.max(0, Math.min(values().length - 1, id))];
    }
}
