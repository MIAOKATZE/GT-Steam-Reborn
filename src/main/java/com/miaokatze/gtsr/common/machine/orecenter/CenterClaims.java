// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

/** Persistent no-drop ownership of exact generated voxels, indexed only in occupied chunks. */
public final class CenterClaims extends WorldSavedData {

    private static final String KEY = "gtsrOreCenterClaims";
    private static final Map<World, CenterClaims> WORLDS = new WeakHashMap<>();
    private final Map<Owner, Claim> owners = new HashMap<>();
    private final Map<Long, List<Claim>> chunks = new HashMap<>();
    private final Map<Owner, WeakReference<CenterStructure>> centers = new HashMap<>();
    private final java.util.LinkedHashMap<CenterStructure, Long> waitingScans = new java.util.LinkedHashMap<>();
    private long scanTick = Long.MIN_VALUE, scanNanos;
    private int internalWrites;
    private final ArrayDeque<int[]> writePositions = new ArrayDeque<>();

    public CenterClaims(String name) {
        super(name);
    }

    static CenterClaims get(World world) {
        return WORLDS.computeIfAbsent(world, w -> {
            CenterClaims loaded = (CenterClaims) w.perWorldStorage.loadData(CenterClaims.class, KEY);
            if (loaded != null) return loaded;
            CenterClaims fresh = new CenterClaims(KEY);
            w.perWorldStorage.setData(KEY, fresh);
            return fresh;
        });
    }

    boolean allowsScan(CenterStructure center, long tick) {
        if (scanTick != tick) {
            scanTick = tick;
            scanNanos = 0;
        }
        waitingScans.put(center, tick);
        java.util.Iterator<Map.Entry<CenterStructure, Long>> waiters = waitingScans.entrySet()
            .iterator();
        while (waiters.hasNext()) {
            Map.Entry<CenterStructure, Long> next = waiters.next();
            if (tick - next.getValue() > 2) {
                waiters.remove();
                continue;
            }
            if (next.getKey() != center || scanNanos >= 2_000_000L) return false;
            waiters.remove();
            return true;
        }
        return false;
    }

    void countScan(long nanos) {
        scanNanos += nanos;
    }

    void beginInternalWrite() {
        internalWrites++;
    }

    void endInternalWrite() {
        internalWrites--;
    }

    void beginVoxelWrite(int x, int y, int z) {
        writePositions.push(new int[] { x, y, z });
    }

    void endVoxelWrite() {
        writePositions.pop();
    }

    private boolean isInternalWrite(int x, int y, int z) {
        int[] at = writePositions.peek();
        return internalWrites > 0 && at != null && at[0] == x && at[1] == y && at[2] == z;
    }

    public static void load(World world) {
        if (!world.isRemote) get(world);
    }

    private static long chunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static Owner owner(Frame frame, int slot) {
        return new Owner(frame.x, frame.y, frame.z, frame.facing, slot);
    }

    void claim(Frame frame, int slot, String resource, Shape shape) {
        Owner owner = owner(frame, slot);
        drop(owner);
        put(new Claim(owner, resource, shape));
        markDirty();
    }

    /** A refused rollback must not turn unpaid generated remnants into harvestable materials. */
    void retainAfterFailedWrite(Frame frame, int slot, String resource, Shape shape) {
        Claim claim = new Claim(owner(frame, slot), resource, shape);
        for (int i = 0; i < shape.count(); i++) {
            int[] at = CenterStructure.SLOTS[slot];
            int rx = claim.ox + CenterStructure.rotateX(shape.x[i], shape.z[i], at[3]);
            int rz = claim.oz + CenterStructure.rotateZ(shape.x[i], shape.z[i], at[3]);
            int x = frame.x(rx, rz), y = frame.y + claim.oy + shape.y[i], z = frame.z(rx, rz);
            if (!frame.loaded(x, z) || frame.world.getBlock(x, y, z) != shape.block(i)
                || frame.world.getBlockMetadata(x, y, z) != shape.metadata(i)) claim.removed.set(i);
        }
        drop(claim.owner);
        put(claim);
        markDirty();
    }

    void release(Frame frame, int slot) {
        if (drop(owner(frame, slot))) markDirty();
    }

    void attach(Frame frame, int slot, CenterStructure structure) {
        Claim claim = owners.get(owner(frame, slot));
        if (claim != null && (claim.source == null || claim.source.get() != structure)) {
            claim.source = new WeakReference<>(structure);
            if (!claim.removed.isEmpty()) structure.invalidateModule(slot);
        }
    }

    void track(Frame frame, CenterStructure structure) {
        centers.entrySet()
            .removeIf(
                entry -> entry.getValue()
                    .get() == null
                    || entry.getValue()
                        .get() == structure);
        centers.put(owner(frame, -1), new WeakReference<>(structure));
    }

    public static void changed(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        CenterClaims data = WORLDS.get(world);
        if (data == null || data.isInternalWrite(x, y, z)) return;
        List<Claim> claims = data.chunks.get(chunk(x >> 4, z >> 4));
        if (claims != null) for (Claim claim : claims) {
            int index = claim.index(x, y, z);
            if (index < 0 || claim.removed.get(index)) continue;
            if (claim.shape.block(index) == world.getBlock(x, y, z)
                && claim.shape.metadata(index) == world.getBlockMetadata(x, y, z)) continue;
            claim.removed.set(index);
            CenterStructure source = claim.source == null ? null : claim.source.get();
            if (source != null) source.invalidateModule(claim.owner.slot);
            data.markDirty();
        }
        java.util.Iterator<Map.Entry<Owner, WeakReference<CenterStructure>>> iterator = data.centers.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<Owner, WeakReference<CenterStructure>> entry = iterator.next();
            CenterStructure center = entry.getValue()
                .get();
            Owner at = entry.getKey();
            if (center == null) {
                iterator.remove();
                continue;
            }
            int dx = x - at.x, dz = z - at.z, dy = y - at.y + 2;
            if (dx < -150 || dx > 150 || dz < -150 || dz > 150 || dy < 0 || dy > 64) continue;
            if (!world.getChunkProvider()
                .chunkExists(at.x >> 4, at.z >> 4)) continue;
            if (!liveController(world, at)) {
                iterator.remove();
                continue;
            }
            int localX = (int) CriticalStructureAlignment.unrotateX(dx, dz, at.facing);
            int localZ = (int) CriticalStructureAlignment.unrotateZ(dx, dz, at.facing) - 19;
            center.changed(
                new Frame(
                    (gregtech.api.interfaces.tileentity.IGregTechTileEntity) world.getTileEntity(at.x, at.y, at.z)),
                localX,
                dy,
                localZ);
        }
    }

    private void put(Claim claim) {
        owners.put(claim.owner, claim);
        for (long key : claim.chunks) chunks.computeIfAbsent(key, k -> new ArrayList<>())
            .add(claim);
    }

    private boolean drop(Owner owner) {
        Claim old = owners.remove(owner);
        if (old == null) return false;
        for (long key : old.chunks) {
            List<Claim> rows = chunks.get(key);
            rows.remove(old);
            if (rows.isEmpty()) chunks.remove(key);
        }
        return true;
    }

    public static boolean protects(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return false;
        CenterClaims data = WORLDS.get(world);
        // Existing claims are loaded by the controlling machines, so normal block checks allocate nothing.
        if (data == null || data.chunks.isEmpty() || !data.chunks.containsKey(chunk(x >> 4, z >> 4))) return false;
        Claim claim = data.find(x, y, z, world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));
        if (claim == null) return false;
        Owner owner = claim.owner;
        if (world.getChunkProvider()
            .chunkExists(owner.x >> 4, owner.z >> 4) && !liveController(world, owner)) return false;
        CenterStructure source = claim.source == null ? null : claim.source.get();
        if (source == null) return true;
        return !source.permitsExternalRemoval(claim.owner.slot);
    }

    private static boolean liveController(World world, Owner at) {
        if (!world.getChunkProvider()
            .chunkExists(at.x >> 4, at.z >> 4)) return false;
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(at.x, at.y, at.z);
        return tile instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof MTEMineralIntegrationCenter;
    }

    public static boolean suppressDrops(World world, int x, int y, int z, Block block, int meta) {
        if (world == null || world.isRemote) return false;
        CenterClaims data = WORLDS.get(world);
        if (data == null) return false;
        // Generated voxels removed by our transaction never mutate ownership halfway through rollback.
        if (data.isInternalWrite(x, y, z)) return true;
        Claim claim = data.find(x, y, z, block, meta);
        if (claim == null) return false;
        CenterStructure source = claim.source == null ? null : claim.source.get();
        if (source != null) source.invalidateModule(claim.owner.slot);
        claim.removed.set(claim.index(x, y, z));
        data.markDirty();
        return true;
    }

    boolean owns(Frame frame, int slot, int x, int y, int z) {
        Claim claim = owners.get(owner(frame, slot));
        return claim != null
            && claim.match(x, y, z, frame.world.getBlock(x, y, z), frame.world.getBlockMetadata(x, y, z));
    }

    String moduleResource(Frame frame, int slot) {
        Claim claim = owners.get(owner(frame, slot));
        return claim == null ? null : claim.resource;
    }

    boolean hasRemainingDifferent(Frame frame, int slot, String resource) {
        Claim claim = owners.get(owner(frame, slot));
        if (claim == null || resource.equals(claim.resource)) return false;
        int dir = CenterStructure.SLOTS[slot][3];
        for (int i = 0; i < claim.shape.count(); i++) if (!claim.removed.get(i)) {
            int rx = claim.ox + CenterStructure.rotateX(claim.shape.x[i], claim.shape.z[i], dir);
            int rz = claim.oz + CenterStructure.rotateZ(claim.shape.x[i], claim.shape.z[i], dir);
            int x = frame.x(rx, rz), y = frame.y + claim.oy + claim.shape.y[i], z = frame.z(rx, rz);
            if (!frame.loaded(x, z) || frame.world.getBlock(x, y, z) == claim.shape.block(i)
                && frame.world.getBlockMetadata(x, y, z) == claim.shape.metadata(i)) return true;
        }
        return false;
    }

    private Claim find(int x, int y, int z, Block block, int meta) {
        List<Claim> candidates = chunks.get(chunk(x >> 4, z >> 4));
        if (candidates == null) return null;
        for (Claim claim : candidates) if (claim.match(x, y, z, block, meta)) return claim;
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        owners.clear();
        chunks.clear();
        NBTTagList entries = root.getTagList("modules", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= 36) continue;
            String resource = entry.getString("shape");
            if (!resource.matches("module-" + slot % 6 + "-[a-z]+-[123]")) continue;
            Shape shape = CenterStructure.optionalShape(resource);
            if (shape == null) continue;
            Owner owner = new Owner(
                entry.getInteger("x"),
                entry.getInteger("y"),
                entry.getInteger("z"),
                CriticalStructureAlignment.horizontal(ForgeDirection.getOrientation(entry.getInteger("facing"))),
                slot);
            Claim claim = new Claim(owner, resource, shape);
            claim.removed.or(BitSet.valueOf(entry.getByteArray("removed")));
            put(claim);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound root) {
        NBTTagList entries = new NBTTagList();
        for (Claim claim : owners.values()) {
            Owner owner = claim.owner;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("x", owner.x);
            entry.setInteger("y", owner.y);
            entry.setInteger("z", owner.z);
            entry.setInteger("facing", owner.facing.ordinal());
            entry.setInteger("slot", owner.slot);
            entry.setString("shape", claim.resource);
            if (!claim.removed.isEmpty()) entry.setByteArray("removed", claim.removed.toByteArray());
            entries.appendTag(entry);
        }
        root.setTag("modules", entries);
    }

    private static final class Owner {

        final int x, y, z, slot;
        final ForgeDirection facing;

        Owner(int x, int y, int z, ForgeDirection facing, int slot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Owner owner)) return false;
            return x == owner.x && y == owner.y && z == owner.z && facing == owner.facing && slot == owner.slot;
        }

        @Override
        public int hashCode() {
            return ((((x * 31 + y) * 31 + z) * 31 + facing.ordinal()) * 31 + slot);
        }
    }

    private static final class Claim {

        final Owner owner;
        final String resource;
        final Shape shape;
        final BitSet removed = new BitSet();
        final long[] chunks;
        WeakReference<CenterStructure> source;
        final int ox, oy, oz;

        Claim(Owner owner, String resource, Shape shape) {
            this.owner = owner;
            this.resource = resource;
            this.shape = shape;
            shape.prepareLookup();
            int[] at = CenterStructure.SLOTS[owner.slot];
            ox = at[0];
            oy = at[1] - 2 - 64;
            oz = at[2] + 19;
            java.util.Set<Long> touched = new java.util.HashSet<>();
            for (int i = 0; i < shape.count(); i++) {
                int x = ox + CenterStructure.rotateX(shape.x[i], shape.z[i], at[3]);
                int z = oz + CenterStructure.rotateZ(shape.x[i], shape.z[i], at[3]);
                int wx = owner.x + CriticalStructureAlignment.rotateX(x, z, owner.facing);
                int wz = owner.z + CriticalStructureAlignment.rotateZ(x, z, owner.facing);
                touched.add(chunk(wx >> 4, wz >> 4));
            }
            chunks = touched.stream()
                .mapToLong(Long::longValue)
                .toArray();
        }

        boolean match(int x, int y, int z, Block block, int meta) {
            int index = index(x, y, z);
            return index >= 0 && !removed.get(index) && shape.block(index) == block && shape.metadata(index) == meta;
        }

        int index(int x, int y, int z) {
            int dx = x - owner.x, dz = z - owner.z, dy = y - owner.y;
            if (dx < -160 || dx > 160 || dz < -160 || dz > 160 || dy < -4 || dy > 100) return -1;
            int lx = (int) CriticalStructureAlignment.unrotateX(dx, dz, owner.facing);
            int lz = (int) CriticalStructureAlignment.unrotateZ(dx, dz, owner.facing);
            int dir = (4 - CenterStructure.SLOTS[owner.slot][3]) & 3;
            int rx = CenterStructure.rotateX(lx - ox, lz - oz, dir),
                rz = CenterStructure.rotateZ(lx - ox, lz - oz, dir);
            return shape.lookup(rx, dy - oy, rz);
        }
    }
}
