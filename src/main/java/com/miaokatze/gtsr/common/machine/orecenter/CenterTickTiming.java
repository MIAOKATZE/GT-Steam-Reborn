// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Optional synchronous timing observation. Ordinary ticks do not allocate or read the clock for this observer. */
public final class CenterTickTiming {

    public enum Stage {
        BASE,
        STRUCTURE,
        PLANNING,
        OUTPUT,
        TOTAL
    }

    @FunctionalInterface
    public interface Observer {

        void record(IGregTechTileEntity tile, Stage stage, long nanos);
    }

    private static volatile Observer observer;

    private CenterTickTiming() {}

    /** Test observations are installed and removed by the server thread. */
    public static void observe(Observer next) {
        if (observer != null) throw new IllegalStateException("Another ore center timing observation is active");
        observer = next;
    }

    public static void stop(Observer current) {
        if (observer == current) observer = null;
    }

    static long begin() {
        return observer == null ? 0 : System.nanoTime();
    }

    static void end(IGregTechTileEntity tile, Stage stage, long start) {
        if (start == 0) return;
        Observer current = observer;
        if (current != null) current.record(tile, stage, System.nanoTime() - start);
    }
}
