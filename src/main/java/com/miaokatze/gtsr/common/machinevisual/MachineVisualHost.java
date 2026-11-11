// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machinevisual;

import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.render.IMTERenderer;

/** Opt-in seam; the common interface never resolves a client renderer or GL class. */
public interface MachineVisualHost extends IMetaTileEntity, IMTERenderer {

    MachineVisualState visualState();

    MachineVisualDefinition visualDefinition();

    ExtendedFacing getExtendedFacing();

    @Override
    default void renderTESR(double x, double y, double z, float partialTick) {
        GTSteamReborn.proxy.renderMachineVisual(this, x, y, z, partialTick);
    }

    @Override
    default AxisAlignedBB getRenderBoundingBox(int x, int y, int z) {
        MachineVisualDefinition d = visualDefinition();
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int a = 0; a < 8; a++) {
            double[] transformed = MachineVisualSpace.transform(
                getExtendedFacing(),
                (a & 1) == 0 ? d.minX - .5 : d.maxX - .5,
                (a & 2) == 0 ? d.minY - .5 : d.maxY - .5,
                (a & 4) == 0 ? d.minZ - .5 : d.maxZ - .5);
            minX = Math.min(minX, transformed[0]);
            minY = Math.min(minY, transformed[1]);
            minZ = Math.min(minZ, transformed[2]);
            maxX = Math.max(maxX, transformed[0]);
            maxY = Math.max(maxY, transformed[1]);
            maxZ = Math.max(maxZ, transformed[2]);
        }
        return AxisAlignedBB
            .getBoundingBox(x + .5 + minX, y + .5 + minY, z + .5 + minZ, x + .5 + maxX, y + .5 + maxY, z + .5 + maxZ);
    }
}
