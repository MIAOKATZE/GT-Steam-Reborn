package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSpace;

/** Fixed continuous sweeps are compiled once; only admitted and already-published groups query the world. */
final class MachineVisualClearance {

    private static final double EPS = 1e-7;
    private static final Map<MachineVisualDefinition, int[][]> SWEEPS = new IdentityHashMap<>();
    private static final Map<MachineVisualDefinition, Map<ExtendedFacing, Plan>> PLANS = new IdentityHashMap<>();
    static long sweepBuilds, orientationBuilds;

    private MachineVisualClearance() {}

    static Plan plan(MachineVisualDefinition definition, ExtendedFacing facing) {
        return PLANS.computeIfAbsent(definition, ignored -> new HashMap<>())
            .computeIfAbsent(facing, ignored -> {
                int[][] sweeps = SWEEPS.computeIfAbsent(definition, source -> {
                    sweepBuilds++;
                    int[][] compiled = new int[source.groups.size()][];
                    for (int i = 0; i < compiled.length; i++) compiled[i] = sweep(source.groups.get(i));
                    return compiled;
                });
                orientationBuilds++;
                return new Plan(definition, facing, sweeps);
            });
    }

    static int safeGroups(MachineVisualClient.Entry entry, int wanted) {
        World world = entry.world;
        int mask = 0;
        for (int index = 0; index < entry.geometry.groups.length; index++) {
            int bit = 1 << index;
            if ((wanted & bit) == 0) continue;
            GroupGeometry geometry = entry.geometry.groups[index];
            MachineVisualDefinition.Group group = entry.definition.groups.get(index);
            boolean valid = true;
            for (int i = 0; i < geometry.members.length; i += 3) {
                int x = entry.originX + geometry.members[i], y = entry.originY + geometry.members[i + 1],
                    z = entry.originZ + geometry.members[i + 2];
                MachineVisualClient.clearanceMemberChecks++;
                MachineVisualClient.clearanceWorldQueries++;
                if (!world.blockExists(x, y, z)) {
                    valid = false;
                    break;
                }
                MachineVisualClient.clearanceWorldQueries++;
                if (world.getTileEntity(x, y, z) != null) {
                    valid = false;
                    break;
                }
                MachineVisualClient.clearanceWorldQueries += 2;
                if (!group.members.get(i / 3)
                    .matches(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z))) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;
            for (int i = 0; i < geometry.air.length; i += 3) {
                int x = entry.originX + geometry.air[i], y = entry.originY + geometry.air[i + 1],
                    z = entry.originZ + geometry.air[i + 2];
                MachineVisualClient.clearanceAirChecks++;
                MachineVisualClient.clearanceWorldQueries++;
                if (!world.blockExists(x, y, z)) {
                    valid = false;
                    break;
                }
                MachineVisualClient.clearanceWorldQueries++;
                if (!world.isAirBlock(x, y, z)) {
                    valid = false;
                    break;
                }
            }
            if (valid) mask |= bit;
        }
        return mask;
    }

    private static int[] sweep(MachineVisualDefinition.Group group) {
        Set<Long> members = new LinkedHashSet<>(), sweep = new LinkedHashSet<>();
        for (MachineVisualDefinition.Cell cell : group.members) members.add(key(cell.x, cell.y, cell.z));
        if (group.kind.equals("rotate_y")) {
            double radius2 = 0, minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (MachineVisualDefinition.Cell cell : group.members) {
                double x = Math.abs(cell.x - group.pivotX) + .5, z = Math.abs(cell.z - group.pivotZ) + .5;
                radius2 = Math.max(radius2, x * x + z * z);
                minY = Math.min(minY, cell.y - .5);
                maxY = Math.max(maxY, cell.y + .5);
            }
            double radius = Math.sqrt(radius2);
            for (int x = first(group.pivotX - radius); x <= last(group.pivotX + radius); x++)
                for (int z = first(group.pivotZ - radius); z <= last(group.pivotZ + radius); z++) {
                    double dx = Math.max(0, Math.abs(x - group.pivotX) - .5);
                    double dz = Math.max(0, Math.abs(z - group.pivotZ) - .5);
                    if (dx * dx + dz * dz >= radius2 - EPS) continue;
                    for (int y = first(minY); y <= last(maxY); y++) sweep.add(key(x, y, z));
                }
        } else if (group.kind.equals("rotate_z")) {
            // The turbine tapers along Z. A global maximum radius would falsely intersect its narrower rear shells.
            Map<Integer, Double> slices = new java.util.LinkedHashMap<>();
            for (MachineVisualDefinition.Cell cell : group.members) {
                double x = Math.abs(cell.x - group.pivotX) + .5;
                double y = Math.abs(cell.y - group.pivotY) + .5;
                slices.merge(cell.z, x * x + y * y, Math::max);
            }
            for (Map.Entry<Integer, Double> slice : slices.entrySet()) {
                double radius2 = slice.getValue(), radius = Math.sqrt(radius2);
                for (int x = first(group.pivotX - radius); x <= last(group.pivotX + radius); x++)
                    for (int y = first(group.pivotY - radius); y <= last(group.pivotY + radius); y++) {
                        double dx = Math.max(0, Math.abs(x - group.pivotX) - .5);
                        double dy = Math.max(0, Math.abs(y - group.pivotY) - .5);
                        if (dx * dx + dy * dy < radius2 - EPS) sweep.add(key(x, y, slice.getKey()));
                    }
            }
        } else {
            double dx = group.axis.equals("x") ? group.travel : 0;
            double dy = group.axis.equals("y") ? group.travel : 0;
            double dz = group.axis.equals("z") ? group.travel : 0;
            for (MachineVisualDefinition.Cell cell : group.members) for (int x = first(cell.x - .5 + Math.min(0, dx)); x
                <= last(cell.x + .5 + Math.max(0, dx)); x++)
                for (int y = first(cell.y - .5 + Math.min(0, dy)); y <= last(cell.y + .5 + Math.max(0, dy)); y++)
                    for (int z = first(cell.z - .5 + Math.min(0, dz)); z <= last(cell.z + .5 + Math.max(0, dz)); z++)
                        sweep.add(key(x, y, z));
        }
        sweep.removeAll(members);
        int[] cells = new int[sweep.size() * 3];
        int i = 0;
        for (long cell : sweep) {
            cells[i++] = (int) (cell << 1 >> 43);
            cells[i++] = (int) (cell << 22 >> 43);
            cells[i++] = (int) (cell << 43 >> 43);
        }
        return cells;
    }

    private static int first(double min) {
        return (int) Math.floor(min + .5 + EPS);
    }

    private static int last(double max) {
        return (int) Math.ceil(max + .5 - EPS) - 1;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x1fffffL) << 42 | ((long) y & 0x1fffffL) << 21 | (long) z & 0x1fffffL;
    }

    static final class Plan {

        final int[] x, y, z;
        final GroupGeometry[] groups;
        final double handedness;

        Plan(MachineVisualDefinition definition, ExtendedFacing facing, int[][] sweeps) {
            x = MachineVisualSpace.transform(facing, 1, 0, 0);
            y = MachineVisualSpace.transform(facing, 0, 1, 0);
            z = MachineVisualSpace.transform(facing, 0, 0, 1);
            handedness = MachineVisualSpace.handedness(facing);
            groups = new GroupGeometry[definition.groups.size()];
            for (int i = 0; i < groups.length; i++)
                groups[i] = new GroupGeometry(definition.groups.get(i), facing, sweeps[i]);
        }
    }

    static final class GroupGeometry {

        final int[] members, air;
        final double[] pivot, axis;

        GroupGeometry(MachineVisualDefinition.Group group, ExtendedFacing facing, int[] sweep) {
            members = new int[group.members.size() * 3];
            for (int i = 0; i < group.members.size(); i++) {
                MachineVisualDefinition.Cell cell = group.members.get(i);
                int[] p = MachineVisualSpace.transform(facing, cell.x, cell.y, cell.z);
                System.arraycopy(p, 0, members, i * 3, 3);
            }
            air = new int[sweep.length];
            for (int i = 0; i < sweep.length; i += 3) {
                int[] p = MachineVisualSpace.transform(facing, sweep[i], sweep[i + 1], sweep[i + 2]);
                System.arraycopy(p, 0, air, i, 3);
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
