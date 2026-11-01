package com.miaokatze.gtsr.client.machinevisual;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualHost;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualSpace;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Exact translated sweeps and the full continuous rotary disk, checked against loaded world cells. */
final class MachineVisualClearance {

    private static final double EPS = 1e-7;

    private MachineVisualClearance() {}

    static int safeGroups(MachineVisualHost host) {
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        World world = tile.getWorld();
        MachineVisualDefinition definition = host.visualDefinition();
        int mask = 0;
        for (int groupIndex = 0; groupIndex < definition.groups.size(); groupIndex++) {
            MachineVisualDefinition.Group group = definition.groups.get(groupIndex);
            Set<String> members = new HashSet<>();
            boolean valid = true;
            for (MachineVisualDefinition.Cell cell : group.members) {
                members.add(key(cell.x, cell.y, cell.z));
                int[] p = at(host, cell.x, cell.y, cell.z);
                if (!world.blockExists(p[0], p[1], p[2]) || world.getTileEntity(p[0], p[1], p[2]) != null
                    || !cell.matches(world.getBlock(p[0], p[1], p[2]), world.getBlockMetadata(p[0], p[1], p[2]))) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;
            Set<String> sweep = new HashSet<>();
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
            } else {
                for (MachineVisualDefinition.Cell cell : group.members) {
                    double dx = group.axis.equals("x") ? group.travel : 0;
                    double dy = group.axis.equals("y") ? group.travel : 0;
                    double dz = group.axis.equals("z") ? group.travel : 0;
                    for (int x = first(cell.x - .5 + Math.min(0, dx)); x <= last(cell.x + .5 + Math.max(0, dx)); x++)
                        for (int y = first(cell.y - .5 + Math.min(0, dy)); y
                            <= last(cell.y + .5 + Math.max(0, dy)); y++)
                            for (int z = first(cell.z - .5 + Math.min(0, dz)); z
                                <= last(cell.z + .5 + Math.max(0, dz)); z++) sweep.add(key(x, y, z));
                }
            }
            sweep.removeAll(members);
            for (String coordinate : sweep) {
                String[] p = coordinate.split(":");
                int[] xyz = at(host, Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (!world.blockExists(xyz[0], xyz[1], xyz[2]) || !world.isAirBlock(xyz[0], xyz[1], xyz[2])) {
                    valid = false;
                    break;
                }
            }
            if (valid) mask |= 1 << groupIndex;
        }
        return mask;
    }

    static int[] at(MachineVisualHost host, int x, int y, int z) {
        int[] p = MachineVisualSpace.transform(host.getExtendedFacing(), x, y, z);
        IGregTechTileEntity tile = host.getBaseMetaTileEntity();
        p[0] += tile.getXCoord();
        p[1] += tile.getYCoord();
        p[2] += tile.getZCoord();
        return p;
    }

    private static int first(double min) {
        return (int) Math.floor(min + .5 + EPS);
    }

    private static int last(double max) {
        return (int) Math.ceil(max + .5 - EPS) - 1;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
