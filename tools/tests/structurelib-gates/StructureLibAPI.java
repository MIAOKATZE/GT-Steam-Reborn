package com.gtnewhorizon.structurelib;

/** Detached traversal diagnostic gates only. Prevents StructureLib's FML networking bootstrap. */
public final class StructureLibAPI {
    private StructureLibAPI() {}
    public static boolean isDebugEnabled() { return false; }
    public static boolean isInstrumentEnabled() { return false; }
}
