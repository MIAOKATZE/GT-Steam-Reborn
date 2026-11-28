// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Client-world chunk ownership index. No world scan, chunk loading or per-voxel tile entity. */
public final class CriticalLoomProxyVisibility {

    private static final Map<World, Map<Long, Map<Long, Entry>>> WORLDS = Collections
        .synchronizedMap(new WeakHashMap<>());
    private static final Map<TileEntityCriticalRenderAnchor, Registration> ANCHORS = new WeakHashMap<>();
    private static final Map<String, List<CriticalGeometry.Voxel>> PROXIES = new java.util.HashMap<>();

    private CriticalLoomProxyVisibility() {}

    public static boolean animated(World world, int x, int y, int z, Block block, int metadata) {
        if (world == null || !world.isRemote) return false;
        Map<Long, Map<Long, Entry>> chunks = WORLDS.get(world);
        Map<Long, Entry> positions = chunks == null ? null : chunks.get(chunkKey(x, z));
        Entry entry = positions == null ? null : positions.get(positionKey(x, y, z));
        TileEntityCriticalRenderAnchor anchor = entry == null ? null : entry.anchor.get();
        return anchor != null && !anchor.isInvalid()
            && anchor.isLoomVisible()
            && entry.registration.activeGroups.contains(entry.group)
            && (CriticalLoomStructure.isLoomVoxel(entry.voxel)
                ? CriticalMaterials.matchesLoomVoxel(block, metadata, entry.voxel)
                : block == entry.voxel.block && metadata == entry.voxel.metadata);
    }

    /** Caller publishes an immutable snapshot of complete groups it draws; workers may retain it. */
    public static void publishVisibleGroups(TileEntityCriticalRenderAnchor anchor, java.util.Set<String> activeGroups) {
        Registration registration = ANCHORS.get(anchor);
        if (registration == null) return;
        java.util.Set<String> previous = registration.activeGroups;
        if (previous == activeGroups || previous.equals(activeGroups)) return;
        java.util.Set<String> next = activeGroups;
        registration.activeGroups = next;
        java.util.Set<String> changed = new java.util.HashSet<>(previous);
        changed.addAll(next);
        java.util.Set<String> same = new java.util.HashSet<>(previous);
        same.retainAll(next);
        changed.removeAll(same);
        registration.redrawGroups(changed);
    }

    public static void refresh(TileEntityCriticalRenderAnchor anchor) {
        World world = anchor.getWorldObj();
        if (world == null || !world.isRemote) return;
        CriticalMachineState state = anchor.getVisualState();
        boolean visible = !anchor.isInvalid() && anchor.isLoomVisible()
            && anchor.xCoord == CriticalRenderAnchors.getX(state)
            && anchor.yCoord == CriticalRenderAnchors.getY(state)
            && anchor.zCoord == CriticalRenderAnchors.getZ(state);
        Registration old = ANCHORS.get(anchor);
        if (old != null && visible
            && old.world.get() == world
            && old.x == state.getOriginX()
            && old.y == state.getOriginY()
            && old.z == state.getOriginZ()
            && old.frame == state.getStructureFacing()
            && old.slug.equals(slugs(state))) return;
        remove(anchor);
        if (!visible) return;
        Registration registration = new Registration(
            world,
            state.getOriginX(),
            state.getOriginY(),
            state.getOriginZ(),
            state.blockWorldX(0, 0),
            state.blockWorldZ(0, 0),
            state.getStructureFacing(),
            slugs(state),
            proxies(state));
        ANCHORS.put(anchor, registration);
        Map<Long, Map<Long, Entry>> chunks = WORLDS.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        for (CriticalGeometry.Voxel voxel : registration.voxels) {
            int x = registration.worldX(voxel), y = registration.y + voxel.y, z = registration.worldZ(voxel);
            registration.record(voxel.model + ':' + voxel.group, x, y, z);
            chunks.computeIfAbsent(chunkKey(x, z), ignored -> new ConcurrentHashMap<>())
                .put(positionKey(x, y, z), new Entry(anchor, voxel, registration));
        }
        registration.redraw();
    }

    public static void remove(TileEntityCriticalRenderAnchor anchor) {
        Registration registration = ANCHORS.remove(anchor);
        if (registration == null) return;
        registration.activeGroups = java.util.Collections.emptySet();
        World world = registration.world.get();
        Map<Long, Map<Long, Entry>> chunks = world == null ? null : WORLDS.get(world);
        if (chunks != null) for (CriticalGeometry.Voxel voxel : registration.voxels) {
            int x = registration.worldX(voxel), y = registration.y + voxel.y, z = registration.worldZ(voxel);
            long chunk = chunkKey(x, z), position = positionKey(x, y, z);
            Map<Long, Entry> positions = chunks.get(chunk);
            if (positions == null) continue;
            Entry entry = positions.get(position);
            if (entry != null && entry.anchor.get() == anchor) positions.remove(position);
            if (positions.isEmpty()) chunks.remove(chunk);
        }
        registration.redraw();
    }

    private static String slugs(CriticalMachineState state) {
        return modelSlugs(state) + "#plugins="
            + state.getParallelPlugins()
            + ','
            + state.getSpeedPlugins()
            + ','
            + state.getEfficiencyPlugins()
            + "#virtual="
            + state.getVirtualConstructionVersion()
            // A paid construction increment cannot change the physical index. During old-save migration,
            // the mask does remove formerly physical cells, so only that compatibility phase uses it.
            + (state.getVirtualConstructionVersion() == 0 ? "#migration=" + state.getVirtualVisualRevision() : "");
    }

    private static String modelSlugs(CriticalMachineState state) {
        String loom = CriticalGeometry.visualLoomSlug(state);
        return state.getStructureId()
            .isEmpty() || state.getState() == 0 ? loom
                : loom + "|" + CriticalGeometry.visualBaseSlug(state) + "|" + CriticalGeometry.visualMachineSlug(state);
    }

    private static List<CriticalGeometry.Voxel> proxies(CriticalMachineState state) {
        String slug = modelSlugs(state);
        String layout = slug + "#virtual=" + state.getVirtualConstructionVersion();
        List<CriticalGeometry.Voxel> models = PROXIES.computeIfAbsent(layout, ignored -> {
            List<CriticalGeometry.Voxel> result = new ArrayList<>();
            for (String modelSlug : slug.split("\\|"))
                for (CriticalGeometry.Voxel voxel : CriticalGeometry.getModel(modelSlug).voxels)
                    if (CriticalGeometry.isDynamic(modelSlug, voxel.group) && !state.isVirtualConstructionVoxel(voxel))
                        result.add(voxel);
            return Collections.unmodifiableList(result);
        });
        // Plugin templates have no authored dynamic groups: the installed, integer-sampled cells
        // inherit the slot's base carrier. Registration owns these exact cells for both add/remove.
        List<CriticalGeometry.Voxel> result = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : models) if (isPhysicalProxy(state, voxel)) result.add(voxel);
        if (!state.getStructureId()
            .isEmpty() && state.getState() != 0 && state.hasPhysicalPlugins())
            for (CriticalGeometry.Voxel voxel : CriticalGeometry.physicalPlugins(state))
                if (CriticalGeometry.isDynamic(voxel.model, voxel.group) && isPhysicalProxy(state, voxel))
                    result.add(voxel);
        return Collections.unmodifiableList(result);
    }

    /** Same admission rule for registration and its immutable removal journal. */
    public static boolean isPhysicalProxy(CriticalMachineState state, CriticalGeometry.Voxel voxel) {
        return !state.isVirtualConstructionVoxel(voxel)
            && (state.getVirtualConstructionVersion() != 0 || !state.isVirtualVoxelInstalled(voxel));
    }

    private static long chunkKey(int x, int z) {
        return (long) (x >> 4) << 32 | (z >> 4) & 0xffffffffL;
    }

    private static long sectionKey(int x, int y, int z) {
        return ((long) (x >> 4) & 0x3ffffffL) << 38 | ((long) (z >> 4) & 0x3ffffffL) << 12 | (y >> 4) & 0xfffL;
    }

    private static long positionKey(int x, int y, int z) {
        return (long) y << 8 | (x & 15) << 4 | z & 15;
    }

    private static final class Entry {

        final WeakReference<TileEntityCriticalRenderAnchor> anchor;
        final CriticalGeometry.Voxel voxel;
        final String group;
        final Registration registration;

        Entry(TileEntityCriticalRenderAnchor anchor, CriticalGeometry.Voxel voxel, Registration registration) {
            this.anchor = new WeakReference<>(anchor);
            this.voxel = voxel;
            this.group = voxel.model + ':' + voxel.group;
            this.registration = registration;
        }
    }

    private static final class Registration {

        final WeakReference<World> world;
        final int x, y, z;
        final int blockOriginX, blockOriginZ;
        final ForgeDirection frame;
        final String slug;
        final List<CriticalGeometry.Voxel> voxels;
        volatile java.util.Set<String> activeGroups = java.util.Collections.emptySet();
        final Map<String, Map<Long, int[]>> groupChunks = new java.util.HashMap<>();

        Registration(World world, int x, int y, int z, int blockOriginX, int blockOriginZ, ForgeDirection frame,
            String slug, List<CriticalGeometry.Voxel> voxels) {
            this.world = new WeakReference<>(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockOriginX = blockOriginX;
            this.blockOriginZ = blockOriginZ;
            this.frame = frame;
            this.slug = slug;
            this.voxels = voxels;
        }

        int worldX(CriticalGeometry.Voxel voxel) {
            return blockOriginX + CriticalStructureAlignment.rotateX(voxel.x, voxel.z, frame);
        }

        int worldZ(CriticalGeometry.Voxel voxel) {
            return blockOriginZ + CriticalStructureAlignment.rotateZ(voxel.x, voxel.z, frame);
        }

        void record(String group, int x, int y, int z) {
            int[] bounds = groupChunks.computeIfAbsent(group, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(sectionKey(x, y, z), ignored -> new int[] { x, y, z, x, y, z });
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        }

        void redraw() {
            redrawGroups(groupChunks.keySet());
        }

        void redrawGroups(java.util.Set<String> groups) {
            World current = world.get();
            if (current == null) return;
            Map<Long, int[]> sections = new java.util.HashMap<>();
            for (String group : groups) {
                Map<Long, int[]> chunks = groupChunks.get(group);
                if (chunks == null) continue;
                for (Map.Entry<Long, int[]> section : chunks.entrySet()) {
                    int[] incoming = section.getValue();
                    int[] merged = sections.get(section.getKey());
                    if (merged == null) sections.put(section.getKey(), incoming.clone());
                    else for (int axis = 0; axis < 3; axis++) {
                        merged[axis] = Math.min(merged[axis], incoming[axis]);
                        merged[axis + 3] = Math.max(merged[axis + 3], incoming[axis + 3]);
                    }
                }
            }
            for (int[] b : sections.values()) current.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
        }
    }
}
