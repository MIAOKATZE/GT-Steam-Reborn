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
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

/** Legacy-only generated-voxel migration and chunk-indexed real-structure notifications. */
public final class CenterClaims extends WorldSavedData {

    private static final String KEY = "gtsrOreCenterClaims";
    private static final Map<World, CenterClaims> WORLDS = new WeakHashMap<>();
    private final Map<Owner, Claim> owners = new HashMap<>();
    private final Map<Long, List<Claim>> chunks = new HashMap<>();
    private final Map<Owner, WeakReference<CenterStructure>> centers = new HashMap<>();
    private final Map<Long, List<Owner>> centerChunks = new HashMap<>();
    private final Map<Owner, long[]> centerFootprints = new HashMap<>();
    private final Map<String, List<Claim>> legacyControllers = new HashMap<>();
    private final java.util.LinkedHashMap<WeakReference<CenterStructure>, Long> waitingScans = new java.util.LinkedHashMap<>();
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
        waitingScans.put(center.scanQueueReference, tick);
        java.util.Iterator<Map.Entry<WeakReference<CenterStructure>, Long>> waiters = waitingScans.entrySet()
            .iterator();
        while (waiters.hasNext()) {
            Map.Entry<WeakReference<CenterStructure>, Long> next = waiters.next();
            CenterStructure waiting = next.getKey()
                .get();
            if (waiting == null || tick - next.getValue() > 2) {
                waiters.remove();
                continue;
            }
            if (waiting != center || scanNanos >= 2_000_000L) return false;
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

    /** Runtime indices are disposable; the WorldSavedData and its unsaved claims remain owned by storage. */
    public static void unload(World world) {
        CenterClaims data = WORLDS.remove(world);
        if (data == null) return;
        for (WeakReference<CenterStructure> reference : data.waitingScans.keySet()) {
            CenterStructure center = reference.get();
            if (center != null) center.clearRuntimeReferences();
        }
        for (WeakReference<CenterStructure> reference : data.centers.values()) {
            CenterStructure center = reference.get();
            if (center != null) center.clearRuntimeReferences();
        }
        for (Claim claim : data.owners.values()) {
            CenterStructure center = claim.source == null ? null : claim.source.get();
            if (center != null) center.clearRuntimeReferences();
            claim.source = null;
        }
        data.waitingScans.clear();
        data.centers.clear();
        data.centerChunks.clear();
        data.centerFootprints.clear();
        data.writePositions.clear();
        data.internalWrites = 0;
        data.scanTick = Long.MIN_VALUE;
        data.scanNanos = 0;
    }

    static void detach(World world, CenterStructure structure) {
        CenterClaims data = WORLDS.get(world);
        if (data == null) return;
        data.waitingScans.remove(structure.scanQueueReference);
        java.util.List<Owner> detached = new ArrayList<>();
        data.centers.forEach(
            (owner, reference) -> {
                if (reference.get() == null || reference.get() == structure) detached.add(owner);
            });
        for (Owner owner : detached) data.untrack(owner);
        for (Claim claim : data.owners.values())
            if (claim.source != null && claim.source.get() == structure) claim.source = null;
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
        List<Owner> old = new ArrayList<>();
        centers.forEach(
            (owner, reference) -> { if (reference.get() == null || reference.get() == structure) old.add(owner); });
        for (Owner owner : old) untrack(owner);
        Owner owner = owner(frame, -1);
        centers.put(owner, new WeakReference<>(structure));
        long[] touched = structure.realStructureChunks(frame);
        centerFootprints.put(owner, touched);
        for (long key : touched) centerChunks.computeIfAbsent(key, k -> new ArrayList<>())
            .add(owner);
    }

    private void untrack(Owner owner) {
        centers.remove(owner);
        long[] footprint = centerFootprints.remove(owner);
        if (footprint == null) return;
        for (long key : footprint) {
            List<Owner> entries = centerChunks.get(key);
            if (entries == null) continue;
            entries.remove(owner);
            if (entries.isEmpty()) centerChunks.remove(key);
        }
    }

    public static void changed(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        CenterClaims data = WORLDS.get(world);
        if (data == null || data.isInternalWrite(x, y, z)) return;
        List<Claim> claims = data.chunks.get(chunk(x >> 4, z >> 4));
        if (claims != null) for (Claim claim : claims) {
            int index = claim.index(x, y, z);
            if (index < 0 || claim.removed.get(index) || claim.cleaned.get(index)) continue;
            if (claim.shape.block(index) == world.getBlock(x, y, z)
                && claim.shape.metadata(index) == world.getBlockMetadata(x, y, z)) continue;
            claim.removed.set(index);
            CenterStructure source = claim.source == null ? null : claim.source.get();
            if (source != null) source.invalidateModule(claim.owner.slot);
            data.markDirty();
        }
        List<Owner> candidates = data.centerChunks.get(chunk(x >> 4, z >> 4));
        if (candidates == null) return;
        for (Owner at : new ArrayList<>(candidates)) {
            WeakReference<CenterStructure> reference = data.centers.get(at);
            CenterStructure center = reference == null ? null : reference.get();
            if (center == null) {
                data.untrack(at);
                continue;
            }
            if (!world.getChunkProvider()
                .chunkExists(at.x >> 4, at.z >> 4)) continue;
            if (!liveController(world, at)) {
                data.untrack(at);
                continue;
            }
            int dx = x - at.x, dz = z - at.z;
            int localX = (int) CriticalStructureAlignment.unrotateX(dx, dz, at.facing);
            int localZ = (int) CriticalStructureAlignment.unrotateZ(dx, dz, at.facing) - 19;
            center.changed(
                new Frame(
                    (gregtech.api.interfaces.tileentity.IGregTechTileEntity) world.getTileEntity(at.x, at.y, at.z)),
                localX,
                y - at.y + 2,
                localZ);
        }
    }

    private void put(Claim claim) {
        owners.put(claim.owner, claim);
        legacyControllers
            .computeIfAbsent(controllerKey(claim.owner.x, claim.owner.y, claim.owner.z), k -> new ArrayList<>())
            .add(claim);
        for (long key : claim.chunks) chunks.computeIfAbsent(key, k -> new ArrayList<>())
            .add(claim);
    }

    private boolean drop(Owner owner) {
        Claim old = owners.remove(owner);
        if (old == null) return false;
        String controller = controllerKey(owner.x, owner.y, owner.z);
        List<Claim> legacy = legacyControllers.get(controller);
        legacy.remove(old);
        if (legacy.isEmpty()) legacyControllers.remove(controller);
        for (long key : old.chunks) {
            List<Claim> rows = chunks.get(key);
            rows.remove(old);
            if (rows.isEmpty()) chunks.remove(key);
        }
        return true;
    }

    public static boolean blocksControllerRemoval(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return false;
        return world.getTileEntity(x, y, z) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity gt
            && gt.getMetaTileEntity() instanceof MTEMineralIntegrationCenter center
            && center.isModuleMigrationPending();
    }

    /** Read saved blocker bits only when an existing GUI/break interaction requests feedback. */
    public static String migrationProblem(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return "";
        CenterClaims data = WORLDS.get(world);
        if (data == null) return "旧模块迁移等待控制器加载。";
        List<Claim> claims = data.legacyControllers.get(controllerKey(x, y, z));
        if (claims == null) return "旧模块安装记录等待转换。";
        for (Claim claim : claims) {
            int index = claim.blocked.nextSetBit(0);
            if (index >= 0 && index < claim.shape.count())
                return "槽位 " + (claim.owner.slot + 1) + " 存在外来方块或容器：" + claim.position(index);
        }
        for (Claim claim : claims) {
            int index = claim.cleaned.nextClearBit(0);
            if (index >= claim.shape.count()) continue;
            int[] at = claim.positionArray(index);
            if (!world.getChunkProvider()
                .chunkExists(at[0] >> 4, at[2] >> 4))
                return "槽位 " + (claim.owner.slot + 1) + " 等待区块加载：" + (at[0] >> 4) + ", " + (at[2] >> 4);
        }
        return "旧模块迁移正在分片处理，请稍后重试。";
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

    private static String controllerKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    boolean migrationPending(Frame frame) {
        List<Claim> claims = legacyControllers.get(controllerKey(frame.x, frame.y, frame.z));
        if (claims == null) return false;
        for (Claim claim : claims)
            if (claim.cleaned.nextClearBit(0) < claim.shape.count() || !claim.blocked.isEmpty()) return true;
        return false;
    }

    boolean hasLegacy(Frame frame, int slot) {
        List<Claim> claims = legacyControllers.get(controllerKey(frame.x, frame.y, frame.z));
        if (claims == null) return false;
        for (Claim claim : claims) if (claim.owner.slot == slot
            && (claim.cleaned.nextClearBit(0) < claim.shape.count() || !claim.blocked.isEmpty())) return true;
        return false;
    }

    /** Only saved claim coordinates are visited, using the saved facing even after controller rotation. */
    void migrateLegacy(Frame frame, CenterStructure structure, int budget) {
        List<Claim> claims = legacyControllers.get(controllerKey(frame.x, frame.y, frame.z));
        if (claims == null || budget <= 0) return;
        long deadline = System.nanoTime()
            + com.miaokatze.gtsr.common.critical.CriticalStructureMonitor.INITIAL_SCAN_NANOS;
        List<Claim> work = new ArrayList<>(claims);
        int first = (int) Math.floorMod(frame.world.getTotalWorldTime(), (long) work.size());
        for (int step = 0; step < work.size(); step++) {
            Claim claim = work.get((first + step) % work.size());
            if (claim.cleaned.nextClearBit(0) >= claim.shape.count() && claim.blocked.isEmpty()) {
                structure.legacyComplete(claim.owner.slot, claim.resource, claim.migrationDamaged);
                if (!structure.legacyRecord(claim.owner.slot, claim.resource)) {
                    drop(claim.owner);
                    markDirty();
                }
                continue;
            }
            claim.source = new WeakReference<>(structure);
            int checks = Math.min(Math.min(budget, 128), claim.shape.count());
            boolean changed = false;
            for (int n = 0; n < checks; n++) {
                int i = claim.cursor;
                claim.cursor = (claim.cursor + 1) % claim.shape.count();
                budget--;
                if (claim.cleaned.get(i)) continue;
                int[] at = CenterStructure.SLOTS[claim.owner.slot];
                int lx = claim.ox + CenterStructure.rotateX(claim.shape.x[i], claim.shape.z[i], at[3]);
                int lz = claim.oz + CenterStructure.rotateZ(claim.shape.x[i], claim.shape.z[i], at[3]);
                int x = claim.owner.x + CriticalStructureAlignment.rotateX(lx, lz, claim.owner.facing);
                int y = claim.owner.y + claim.oy + claim.shape.y[i];
                int z = claim.owner.z + CriticalStructureAlignment.rotateZ(lx, lz, claim.owner.facing);
                if (!frame.loaded(x, z)) continue;
                Block actual = frame.world.getBlock(x, y, z);
                boolean air = actual.isAir(frame.world, x, y, z);
                if (claim.blocked.get(i)) {
                    // A foreign replacement must be removed by its owner. Matching it later never grants ownership.
                    if (air && frame.world.getTileEntity(x, y, z) == null) {
                        claim.blocked.clear(i);
                        claim.cleaned.set(i);
                        changed = true;
                    }
                } else if (claim.removed.get(i)) {
                    claim.migrationDamaged = true;
                    claim.cleaned.set(i);
                    changed = true;
                } else if (air && frame.world.getTileEntity(x, y, z) == null) {
                    claim.migrationDamaged = true;
                    claim.removed.set(i);
                    claim.cleaned.set(i);
                    changed = true;
                } else if (frame.world.getTileEntity(x, y, z) != null || actual != claim.shape.block(i)
                    || frame.world.getBlockMetadata(x, y, z) != claim.shape.metadata(i)) {
                        claim.migrationDamaged = true;
                        claim.removed.set(i);
                        claim.blocked.set(i);
                        changed = true;
                    } else {
                        beginInternalWrite();
                        beginVoxelWrite(x, y, z);
                        try {
                            frame.world.setBlock(x, y, z, Blocks.air, 0, 2);
                            if (frame.world.isAirBlock(x, y, z)) {
                                claim.cleaned.set(i);
                                changed = true;
                            }
                        } catch (RuntimeException refusedWrite) {
                            // A replacement hook may mutate and then throw. Our own successful air write is not damage.
                            if (frame.world.isAirBlock(x, y, z) && frame.world.getTileEntity(x, y, z) == null) {
                                claim.cleaned.set(i);
                                changed = true;
                            }
                        } finally {
                            endVoxelWrite();
                            endInternalWrite();
                        }
                    }
                if ((n & 31) == 31 && System.nanoTime() >= deadline) break;
            }
            if (claim.migrationDamaged || !claim.removed.isEmpty()) structure.legacyDamage(claim.owner.slot);
            if (changed || checks > 0) markDirty();
            if (claim.cleaned.nextClearBit(0) >= claim.shape.count() && claim.blocked.isEmpty()) {
                // Keep the completed claim as a durable receipt until the controller has saved its virtual format.
                structure.legacyComplete(claim.owner.slot, claim.resource, claim.migrationDamaged);
                if (!structure.legacyRecord(claim.owner.slot, claim.resource)) {
                    drop(claim.owner);
                    markDirty();
                }
            }
            if (budget <= 0 || System.nanoTime() >= deadline) break;
        }
    }

    void retireCompleted(Frame frame) {
        List<Claim> claims = legacyControllers.get(controllerKey(frame.x, frame.y, frame.z));
        if (claims == null) return;
        for (Claim claim : new ArrayList<>(claims))
            if (claim.cleaned.nextClearBit(0) >= claim.shape.count() && claim.blocked.isEmpty()) {
                drop(claim.owner);
                markDirty();
            }
    }

    /** Retire only receipts whose matching virtual controller record has already survived a save/reload. */
    void retireLegacy(Frame frame, int slot, String resource) {
        List<Claim> claims = legacyControllers.get(controllerKey(frame.x, frame.y, frame.z));
        if (claims == null) return;
        for (Claim claim : new ArrayList<>(claims)) if (claim.owner.slot == slot && claim.resource.equals(resource)
            && claim.cleaned.nextClearBit(0) >= claim.shape.count()
            && claim.blocked.isEmpty()) {
                drop(claim.owner);
                markDirty();
            }
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
        legacyControllers.clear();
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
            claim.cleaned.or(BitSet.valueOf(entry.getByteArray("cleaned")));
            claim.blocked.or(BitSet.valueOf(entry.getByteArray("blocked")));
            claim.cursor = Math.floorMod(entry.getInteger("cursor"), Math.max(1, shape.count()));
            claim.migrationDamaged = entry.getBoolean("migrationDamaged") || !claim.removed.isEmpty();
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
            if (!claim.cleaned.isEmpty()) entry.setByteArray("cleaned", claim.cleaned.toByteArray());
            if (!claim.blocked.isEmpty()) entry.setByteArray("blocked", claim.blocked.toByteArray());
            entry.setInteger("cursor", claim.cursor);
            entry.setBoolean("migrationDamaged", claim.migrationDamaged);
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
        final BitSet cleaned = new BitSet(), blocked = new BitSet();
        int cursor;
        boolean migrationDamaged;
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

        int[] positionArray(int index) {
            int direction = CenterStructure.SLOTS[owner.slot][3];
            int lx = ox + CenterStructure.rotateX(shape.x[index], shape.z[index], direction);
            int lz = oz + CenterStructure.rotateZ(shape.x[index], shape.z[index], direction);
            return new int[] { owner.x + CriticalStructureAlignment.rotateX(lx, lz, owner.facing),
                owner.y + oy + shape.y[index], owner.z + CriticalStructureAlignment.rotateZ(lx, lz, owner.facing) };
        }

        String position(int index) {
            int[] at = positionArray(index);
            return "(" + at[0] + ", " + at[1] + ", " + at[2] + ")";
        }

        boolean match(int x, int y, int z, Block block, int meta) {
            int index = index(x, y, z);
            return index >= 0 && !removed.get(index)
                && !cleaned.get(index)
                && shape.block(index) == block
                && shape.metadata(index) == meta;
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
