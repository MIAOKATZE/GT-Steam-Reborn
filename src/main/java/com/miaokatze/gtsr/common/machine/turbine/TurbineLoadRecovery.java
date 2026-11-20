// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.turbine;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/** Transient grace for a warm turbine whose structure or fuel source is still reconnecting. */
public final class TurbineLoadRecovery {

    private static final int GRACE_TICKS = 100;
    private int remaining;

    public void load(int savedEfficiency) {
        remaining = savedEfficiency > 0 ? GRACE_TICKS : 0;
    }

    public boolean waiting() {
        return remaining > 0;
    }

    public void finish() {
        remaining = 0;
    }

    public void beforeTick(IGregTechTileEntity tile, MTEMultiBlockBase machine, long tick) {
        if (!waiting()) return;
        if (!tile.isAllowedToWork()) finish();
        else if (!machine.mMachine && (tick == 1 || tick % 20 == 0)) machine.checkStructure(true, tile);
    }

    public void afterTick(IGregTechTileEntity tile, MTEMultiBlockBase machine) {
        if (!waiting()) return;
        if (!machine.mMachine) tile.setActive(false);
        if (machine.getmStartUpCheck() < 0) remaining--;
    }
}
