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
            && CriticalMaterials.matchesLoomVoxel(block, metadata, entry.voxel);
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
            && old.slug.equals(CriticalGeometry.visualLoomSlug(state))) return;
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
            CriticalGeometry.visualLoomSlug(state));
        ANCHORS.put(anchor, registration);
        Map<Long, Map<Long, Entry>> chunks = WORLDS.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        for (CriticalGeometry.Voxel voxel : proxies(registration.slug)) {
            int x = registration.worldX(voxel), y = registration.y + voxel.y, z = registration.worldZ(voxel);
            chunks.computeIfAbsent(chunkKey(x, z), ignored -> new ConcurrentHashMap<>())
                .put(positionKey(x, y, z), new Entry(anchor, voxel));
        }
        registration.redraw();
    }

    public static void remove(TileEntityCriticalRenderAnchor anchor) {
        Registration registration = ANCHORS.remove(anchor);
        if (registration == null) return;
        World world = registration.world.get();
        Map<Long, Map<Long, Entry>> chunks = world == null ? null : WORLDS.get(world);
        if (chunks != null) for (CriticalGeometry.Voxel voxel : proxies(registration.slug)) {
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

    private static List<CriticalGeometry.Voxel> proxies(String slug) {
        return PROXIES.computeIfAbsent(slug, ignored -> {
            List<CriticalGeometry.Voxel> result = new ArrayList<>();
            CriticalGeometry.Model model = CriticalGeometry.getModel(slug);
            for (CriticalGeometry.Voxel voxel : model.voxels) if (model.animations.has(voxel.group)) result.add(voxel);
            return result;
        });
    }

    private static long chunkKey(int x, int z) {
        return (long) (x >> 4) << 32 | (z >> 4) & 0xffffffffL;
    }

    private static long positionKey(int x, int y, int z) {
        return (long) y << 8 | (x & 15) << 4 | z & 15;
    }

    private static final class Entry {

        final WeakReference<TileEntityCriticalRenderAnchor> anchor;
        final CriticalGeometry.Voxel voxel;

        Entry(TileEntityCriticalRenderAnchor anchor, CriticalGeometry.Voxel voxel) {
            this.anchor = new WeakReference<>(anchor);
            this.voxel = voxel;
        }
    }

    private static final class Registration {

        final WeakReference<World> world;
        final int x, y, z;
        final int blockOriginX, blockOriginZ;
        final ForgeDirection frame;
        final String slug;

        Registration(World world, int x, int y, int z, int blockOriginX, int blockOriginZ, ForgeDirection frame,
            String slug) {
            this.world = new WeakReference<>(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockOriginX = blockOriginX;
            this.blockOriginZ = blockOriginZ;
            this.frame = frame;
            this.slug = slug;
        }

        int worldX(CriticalGeometry.Voxel voxel) {
            return blockOriginX + CriticalStructureAlignment.rotateX(voxel.x, voxel.z, frame);
        }

        int worldZ(CriticalGeometry.Voxel voxel) {
            return blockOriginZ + CriticalStructureAlignment.rotateZ(voxel.x, voxel.z, frame);
        }

        void redraw() {
            World current = world.get();
            if (current != null) current.markBlockRangeForRenderUpdate(
                x,
                y,
                z,
                x + (slug.startsWith("v2_") ? 111 : 79),
                y + 45,
                z + (slug.startsWith("v2_") ? 111 : 79));
        }
    }
}
