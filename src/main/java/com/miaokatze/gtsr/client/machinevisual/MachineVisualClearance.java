// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSpace;

/** Oriented coordinates for decorative parts. Formation and operation belong to the server machine. */
final class MachineVisualClearance {

    private static final Map<MachineVisualDefinition, Map<ExtendedFacing, Plan>> PLANS = new IdentityHashMap<>();
    static long orientationBuilds;

    private MachineVisualClearance() {}

    static Plan plan(MachineVisualDefinition definition, ExtendedFacing facing) {
        return PLANS.computeIfAbsent(definition, ignored -> new HashMap<>())
            .computeIfAbsent(facing, ignored -> {
                orientationBuilds++;
                return new Plan(definition, facing);
            });
    }

    static final class Plan {

        final int[] x, y, z;
        final GroupGeometry[] groups;
        final double handedness;

        Plan(MachineVisualDefinition definition, ExtendedFacing facing) {
            x = MachineVisualSpace.transform(facing, 1, 0, 0);
            y = MachineVisualSpace.transform(facing, 0, 1, 0);
            z = MachineVisualSpace.transform(facing, 0, 0, 1);
            handedness = MachineVisualSpace.handedness(facing);
            groups = new GroupGeometry[definition.groups.size()];
            for (int i = 0; i < groups.length; i++) groups[i] = new GroupGeometry(definition.groups.get(i), facing);
        }
    }

    static final class GroupGeometry {

        final int[] members;
        final double[] pivot, axis;

        GroupGeometry(MachineVisualDefinition.Group group, ExtendedFacing facing) {
            members = new int[group.members.size() * 3];
            for (int i = 0; i < group.members.size(); i++) {
                MachineVisualDefinition.Cell cell = group.members.get(i);
                int[] p = MachineVisualSpace.transform(facing, cell.x, cell.y, cell.z);
                System.arraycopy(p, 0, members, i * 3, 3);
            }
            pivot = MachineVisualSpace.transform(facing, group.pivotX, group.pivotY, group.pivotZ);
            axis = group.kind.equals("rotate_y") ? MachineVisualSpace.transform(facing, 0., 1., 0.)
                : MachineVisualSpace.transform(
                    facing,
                    group.axis.equals("x") ? 1. : 0.,
                    group.axis.equals("y") ? 1. : 0.,
                    group.axis.equals("z") ? 1. : 0.);
        }
    }
}
