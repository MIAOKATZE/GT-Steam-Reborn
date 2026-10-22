package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLoomStructure;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;

/** Client-only bounded identity checks. A group is never drawn before its entire proxy set is present. */
final class CriticalGroupVisibility {

    private final List<Group> groups = new ArrayList<>();
    private final Set<String> visible = new HashSet<>();
    private final String loomSlug, structureId;
    private final com.miaokatze.gtsr.common.critical.CriticalMachineKind machineKind;
    private final com.miaokatze.gtsr.common.critical.CriticalTier tier;
    private final double originX, originZ;
    private final int originY, parallelPlugins, speedPlugins, efficiencyPlugins;
    private final net.minecraftforge.common.util.ForgeDirection facing;
    private final boolean installation;
    private Set<String> snapshot = Collections.emptySet(), filteredSource, filteredSnapshot;
    private boolean filteredGeometryReady, filteredLoomReady;
    private int nextGroup;
    private long nextCheck = Long.MIN_VALUE;
    private boolean lastValidated;

    CriticalGroupVisibility(CriticalMachineState controller) {
        loomSlug = CriticalGeometry.visualLoomSlug(controller);
        structureId = controller.getStructureId();
        machineKind = controller.getMachineKind();
        tier = controller.getTier();
        facing = controller.getStructureFacing();
        originX = controller.getMeshOriginX();
        originY = controller.getOriginY();
        originZ = controller.getMeshOriginZ();
        parallelPlugins = controller.getParallelPlugins();
        speedPlugins = controller.getSpeedPlugins();
        efficiencyPlugins = controller.getEfficiencyPlugins();
        installation = hasInstallation(controller);
        lastValidated = controller.isStructureValidated();
        add(CriticalGeometry.visualLoomSlug(controller));
        if (hasInstallation(controller)) {
            add(CriticalGeometry.visualBaseSlug(controller));
            add(CriticalGeometry.visualMachineSlug(controller));
            if (controller.hasPhysicalPlugins()) {
                Map<String, List<CriticalGeometry.Voxel>> plugins = new LinkedHashMap<>();
                for (CriticalGeometry.Voxel voxel : CriticalGeometry.physicalPlugins(controller))
                    if (CriticalGeometry.isDynamic(voxel.model, voxel.group))
                        plugins.computeIfAbsent(voxel.model + ':' + voxel.group, ignored -> new ArrayList<>())
                            .add(voxel);
                for (Map.Entry<String, List<CriticalGeometry.Voxel>> entry : plugins.entrySet())
                    groups.add(new Group(entry.getKey(), entry.getValue()));
            }
        }
    }

    private static boolean hasInstallation(CriticalMachineState controller) {
        return !controller.getStructureId()
            .isEmpty() && controller.getState() != 0;
    }

    boolean matches(CriticalMachineState controller) {
        return loomSlug.equals(CriticalGeometry.visualLoomSlug(controller))
            && structureId.equals(controller.getStructureId())
            && machineKind == controller.getMachineKind()
            && tier == controller.getTier()
            && installation == hasInstallation(controller)
            && facing == controller.getStructureFacing()
            && originX == controller.getMeshOriginX()
            && originY == controller.getOriginY()
            && originZ == controller.getMeshOriginZ()
            && parallelPlugins == controller.getParallelPlugins()
            && speedPlugins == controller.getSpeedPlugins()
            && efficiencyPlugins == controller.getEfficiencyPlugins();
    }

    private void add(String slug) {
        CriticalGeometry.Model model = CriticalGeometry.getModel(slug);
        Map<String, List<CriticalGeometry.Voxel>> indexed = new LinkedHashMap<>();
        for (CriticalGeometry.Voxel voxel : model.voxels) if (CriticalGeometry.isDynamic(slug, voxel.group)) {
            indexed.computeIfAbsent(voxel.group, ignored -> new ArrayList<>())
                .add(voxel);
        }
        for (Map.Entry<String, List<CriticalGeometry.Voxel>> entry : indexed.entrySet()) {
            groups.add(new Group(slug + ":" + entry.getKey(), entry.getValue()));
        }
    }

    Set<String> update(CriticalMachineState controller) {
        long now = controller.getWorldObj()
            .getTotalWorldTime();
        if (now < nextCheck) return snapshot;
        nextCheck = now + 1;
        if (lastValidated && !controller.isStructureValidated()) {
            nextGroup = 0;
            for (Group group : groups) group.cursor = 0;
        }
        lastValidated = controller.isStructureValidated();
        boolean changed = false;
        int remaining = 512, completed = 0;
        while (remaining > 0 && completed < groups.size() && !groups.isEmpty()) {
            Group group = groups.get(nextGroup);
            CriticalGeometry.Voxel voxel = group.voxels.get(group.cursor);
            int x = controller.blockWorldX(voxel), y = controller.blockWorldY(voxel.y),
                z = controller.blockWorldZ(voxel);
            boolean present = controller.getWorldObj()
                .blockExists(x, y, z)
                && (group.loom ? CriticalLoomStructure.matchesVoxel(controller, voxel)
                    : controller.getWorldObj()
                        .getBlock(x, y, z) == voxel.block
                        && controller.getWorldObj()
                            .getBlockMetadata(x, y, z) == voxel.metadata);
            remaining--;
            if (!present) {
                changed |= visible.remove(group.key);
                group.cursor = 0;
                nextGroup = (nextGroup + 1) % groups.size();
                completed++;
            } else if (++group.cursor == group.voxels.size()) {
                changed |= visible.add(group.key);
                group.cursor = 0;
                nextGroup = (nextGroup + 1) % groups.size();
                completed++;
            }
        }
        if (changed) snapshot = Collections.unmodifiableSet(new HashSet<>(visible));
        return snapshot;
    }

    Set<String> readyGroups(Set<String> source, boolean geometryReady, boolean loomReady) {
        if (geometryReady && loomReady) return source;
        if (source == filteredSource && geometryReady == filteredGeometryReady && loomReady == filteredLoomReady)
            return filteredSnapshot;
        Set<String> allowed = new HashSet<>();
        String prefix = loomSlug + ':';
        for (String group : source) {
            boolean loomGroup = group.startsWith(prefix) || group.contains(":loom_");
            if (loomGroup ? loomReady : geometryReady) allowed.add(group);
        }
        filteredSource = source;
        filteredGeometryReady = geometryReady;
        filteredLoomReady = loomReady;
        filteredSnapshot = Collections.unmodifiableSet(allowed);
        return filteredSnapshot;
    }

    private static final class Group {

        final String key;
        final List<CriticalGeometry.Voxel> voxels;
        final boolean loom;
        int cursor;

        Group(String key, List<CriticalGeometry.Voxel> voxels) {
            this.key = key;
            this.voxels = voxels;
            this.loom = key.startsWith("loom:") || key.startsWith("v2_loom:")
                || key.startsWith("v2_v25_loom:")
                || key.startsWith("v2_v27_loom:")
                || key.startsWith("v2_v30_loom:")
                || key.startsWith("v2_legacy_loom:");
        }
    }
}
