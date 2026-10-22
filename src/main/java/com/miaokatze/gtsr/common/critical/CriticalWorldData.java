package com.miaokatze.gtsr.common.critical;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Persistent ownership, independent of controller chunk lifetime. Rectangles can cross chunks. */
public final class CriticalWorldData extends WorldSavedData {

    private static final String KEY = "gtsr_critical_regions";
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final Map<Long, CriticalFoundationChunk> foundations = new LinkedHashMap<>();

    public CriticalFoundationChunk foundation(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return foundations.computeIfAbsent(key, ignored -> new CriticalFoundationChunk());
    }

    public CriticalFoundationChunk findFoundation(int chunkX, int chunkZ) {
        return foundations.get(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
    }

    private String nightOwner = "";
    private boolean localClock;
    private long localTime;

    public boolean hasLocalClock() {
        return localClock;
    }

    public long getLocalTime() {
        return localTime;
    }

    public void initializeLocalClock(long time) {
        if (!localClock) {
            localClock = true;
            localTime = time;
            markDirty();
        }
    }

    public void setLocalTime(long time) {
        localTime = time;
        markDirty();
    }

    public boolean hasNightOwner() {
        return !nightOwner.isEmpty();
    }

    public boolean validateNightOwner(World world) {
        if (nightOwner.isEmpty()) return false;
        Region region = regions.get(nightOwner);
        boolean live = region != null && world.getChunkProvider()
            .chunkExists(region.cx >> 4, region.cz >> 4);
        if (live) {
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(region.cx, region.cy, region.cz);
            CriticalMachineState controller = CriticalMachineState.resolve(tile);
            live = controller != null && controller.isEnabled()
                && controller.isStructureComplete()
                && controller.isStructureValidated()
                && controller.getMachineKind() == CriticalMachineKind.SOLAR
                && ownsTask(region.id, region.owner, controller.getJobId(), region.x, region.y, region.z);
        }
        if (!live) {
            nightOwner = "";
            markDirty();
        }
        return live;
    }

    public CriticalWorldData() {
        super(KEY);
    }

    public CriticalWorldData(String name) {
        super(name);
    }

    public static CriticalWorldData get(World world) {
        CriticalWorldData data = (CriticalWorldData) world.perWorldStorage.loadData(CriticalWorldData.class, KEY);
        if (data == null) {
            data = new CriticalWorldData();
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    public boolean acquire(String id, String owner, int x, int y, int z) {
        return acquire(id, owner, x, y, z, 80, 162, x + 40, y, z + 33);
    }

    public boolean acquire(String id, String owner, int x, int y, int z, int size, int height, int cx, int cy, int cz) {
        Region own = regions.get(id);
        if (own != null) return own.x == x && own.y == y && own.z == z && own.owner.equals(owner) && own.size == size;
        for (Region r : regions.values()) {
            if (x < r.x + r.size && x + size > r.x && z < r.z + r.size && z + size > r.z) return false;
        }
        Region region = new Region(id, owner, x, y, z);
        region.size = size;
        region.height = height;
        region.cx = cx;
        region.cy = cy;
        region.cz = cz;
        regions.put(id, region);
        markDirty();
        return true;
    }

    public boolean owns(String id, int x, int y, int z) {
        Region r = regions.get(id);
        return r != null && r.x == x && r.y == y && r.z == z;
    }

    public boolean ownsTask(String id, String owner, String jobId, int x, int y, int z) {
        Region region = regions.get(id);
        return region != null && owns(id, x, y, z) && region.owner.equals(owner) && region.jobId.equals(jobId);
    }

    /** Caller must verify the unique physical controller and its position ledger before recovery binding. */
    public boolean bindTask(String id, String owner, String jobId, int x, int y, int z) {
        Region region = regions.get(id);
        if (region == null || !owns(id, x, y, z) || !region.owner.equals(owner) || jobId.isEmpty()) return false;
        region.jobId = jobId;
        markDirty();
        return true;
    }

    public void release(String id) {
        regions.remove(id);
        if (nightOwner.equals(id)) nightOwner = "";
        markDirty();
    }

    public boolean claimNight(String id) {
        if (nightOwner.isEmpty()) {
            nightOwner = id;
            markDirty();
        }
        return nightOwner.equals(id);
    }

    public boolean claimNight(World world, String id) {
        if (!nightOwner.isEmpty() && !nightOwner.equals(id)) {
            Region previous = regions.get(nightOwner);
            boolean live = previous != null && world.getChunkProvider()
                .chunkExists(previous.cx >> 4, previous.cz >> 4);
            if (live) {
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(previous.cx, previous.cy, previous.cz);
                CriticalMachineState controller = CriticalMachineState.resolve(tile);
                live = controller != null && controller.isEnabled()
                    && controller.isStructureComplete()
                    && controller.isStructureValidated();
            }
            if (!live) {
                nightOwner = "";
                markDirty();
            }
        }
        boolean claimed = claimNight(id);
        if (claimed) CriticalNightClock.anchor(world, this);
        return claimed;
    }

    public boolean holdsNight(String id) {
        return nightOwner.equals(id);
    }

    public void releaseNight(String id) {
        if (nightOwner.equals(id)) {
            nightOwner = "";
            markDirty();
        }
    }

    void updateProtection(CriticalMachineState controller) {
        Region region = regions.get(controller.getStructureId());
        if (region == null || !ownsTask(
            region.id,
            region.owner,
            controller.getJobId(),
            controller.getOriginX(),
            controller.getOriginY(),
            controller.getOriginZ())) return;
        region.protection = controller.protectionSnapshot();
        region.protectionState = null;
        markDirty();
    }

    public boolean protectsPlayerBreak(World world, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (x < region.x || x >= region.x + region.size
                || z < region.z
                || z >= region.z + region.size
                || y < region.y
                || y >= region.y + region.height) continue;
            if (world.getChunkProvider()
                .chunkExists(region.cx >> 4, region.cz >> 4)) {
                CriticalMachineState live = CriticalMachineState
                    .resolve(world.getTileEntity(region.cx, region.cy, region.cz));
                if (live != null && ownsTask(
                    region.id,
                    region.owner,
                    live.getJobId(),
                    live.getOriginX(),
                    live.getOriginY(),
                    live.getOriginZ())) return live.protectsPlayerBreak(x, y, z);
            }
            if (region.protection == null) return false;
            if (region.protectionState == null) {
                region.protectionState = new CriticalMachineState();
                region.protectionState.worldObj = world;
                region.protectionState.xCoord = region.cx;
                region.protectionState.yCoord = region.cy;
                region.protectionState.zCoord = region.cz;
                region.protectionState.readProtectionSnapshot(region.protection);
            }
            return region.protectionState.protectsPlayerBreak(x, y, z);
        }
        return false;
    }

    public void invalidateAt(World world, int x, int y, int z) {
        for (Region r : regions.values()) {
            if (x < r.x || x >= r.x + r.size || z < r.z || z >= r.z + r.size || y < r.y || y >= r.y + r.height)
                continue;
            if (!world.getChunkProvider()
                .chunkExists(r.cx >> 4, r.cz >> 4)) continue;
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(r.cx, r.cy, r.cz);
            CriticalMachineState controller = CriticalMachineState.resolve(tile);
            if (controller != null) controller.invalidateStructure();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        regions.clear();
        foundations.clear();
        NBTTagList foundationList = n.getTagList("foundations", 10);
        for (int i = 0; i < foundationList.tagCount(); i++) {
            NBTTagCompound entry = foundationList.getCompoundTagAt(i);
            CriticalFoundationChunk foundation = foundation(entry.getInteger("x"), entry.getInteger("z"));
            foundation.read(entry);
        }
        nightOwner = n.getString("nightOwner");
        localClock = n.getBoolean("localClock");
        localTime = n.getLong("localTime");
        NBTTagList list = n.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound r = list.getCompoundTagAt(i);
            String id = r.getString("id");
            try {
                UUID.fromString(id);
                Region region = new Region(
                    id,
                    r.getString("owner"),
                    r.getInteger("x"),
                    r.getInteger("y"),
                    r.getInteger("z"));
                region.jobId = r.getString("jobId");
                region.protection = r.hasKey("protection") ? r.getCompoundTag("protection") : null;
                region.size = r.hasKey("size") ? r.getInteger("size") : 80;
                region.height = r.hasKey("height") ? r.getInteger("height") : 162;
                region.cx = r.hasKey("cx") ? r.getInteger("cx") : region.x + 40;
                region.cy = r.hasKey("cy") ? r.getInteger("cy") : region.y;
                region.cz = r.hasKey("cz") ? r.getInteger("cz") : region.z + 33;
                regions.put(id, region);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        NBTTagList foundationList = new NBTTagList();
        for (Map.Entry<Long, CriticalFoundationChunk> entry : foundations.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", (int) (entry.getKey() >> 32));
            tag.setInteger("z", (int) (long) entry.getKey());
            entry.getValue()
                .write(tag);
            foundationList.appendTag(tag);
        }
        n.setTag("foundations", foundationList);
        n.setString("nightOwner", nightOwner);
        n.setBoolean("localClock", localClock);
        n.setLong("localTime", localTime);
        NBTTagList list = new NBTTagList();
        for (Region r : regions.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", r.id);
            t.setString("owner", r.owner);
            t.setString("jobId", r.jobId);
            if (r.protection != null) t.setTag("protection", r.protection.copy());
            t.setInteger("x", r.x);
            t.setInteger("y", r.y);
            t.setInteger("z", r.z);
            t.setInteger("size", r.size);
            t.setInteger("height", r.height);
            t.setInteger("cx", r.cx);
            t.setInteger("cy", r.cy);
            t.setInteger("cz", r.cz);
            list.appendTag(t);
        }
        n.setTag("regions", list);
    }

    private static final class Region {

        final String id, owner;
        final int x, y, z;
        int size = 80, height = 162, cx, cy, cz;
        String jobId = "";
        NBTTagCompound protection;
        CriticalMachineState protectionState;

        Region(String id, String owner, int x, int y, int z) {
            this.id = id;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            cx = x + 40;
            cy = y;
            cz = z + 33;
        }
    }
}
