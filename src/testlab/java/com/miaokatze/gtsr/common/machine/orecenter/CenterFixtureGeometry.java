// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Derives the North-facing test fixture's journal footprint from the shipped layout. */
public final class CenterFixtureGeometry {

    private CenterFixtureGeometry() {}

    public static List<int[]> oneSector() {
        List<int[]> positions = new ArrayList<>();
        add(positions, "tower-0", 0, -2, 0, 0);
        add(positions, "outer-0", 0, -2, 0, 0);
        addModule(positions, 1, "crusher");
        addModule(positions, 3, "logistics");
        return positions;
    }

    private static void addModule(List<int[]> positions, int slot, String module) {
        int[] at = CenterStructure.SLOTS[slot];
        add(positions, "module-" + slot + "-" + module + "-1", at[0], at[1] - 66, at[2], at[3]);
    }

    private static void add(List<int[]> positions, String resource, int x, int y, int z, int direction) {
        Shape shape = CenterStructure.optionalShape(resource);
        if (shape == null) throw new IllegalStateException("Missing fixture layout " + resource);
        for (int i = 0; i < shape.count(); i++) {
            int px = x + CenterStructure.rotateX(shape.x[i], shape.z[i], direction);
            int pz = z + CenterStructure.rotateZ(shape.x[i], shape.z[i], direction) + 19;
            positions.add(new int[] { px, y + Byte.toUnsignedInt(shape.y[i]), pz });
        }
    }

    /** Leaves terrain regeneration to the fixture journal and removes only claims created by this fixture. */
    public static void release(IGregTechTileEntity controller, int slot) {
        Frame frame = new Frame(controller);
        CenterClaims.get(frame.world).release(frame, slot);
    }
}
