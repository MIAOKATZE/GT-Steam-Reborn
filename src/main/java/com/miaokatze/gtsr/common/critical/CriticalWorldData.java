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
            .chunkExists((region.x + 40) >> 4, (region.z + 32) >> 4);
        if (live) {
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(region.x + 40, region.y, region.z + 32);
            live = tile instanceof TileEntityCriticalController controller && controller.isEnabled()
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
        Region own = regions.get(id);
        if (own != null) return own.x == x && own.y == y && own.z == z && own.owner.equals(owner);
        for (Region r : regions.values()) {
            if (x < r.x + 80 && x + 80 > r.x && z < r.z + 80 && z + 80 > r.z) return false;
        }
        regions.put(id, new Region(id, owner, x, y, z));
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
                .chunkExists((previous.x + 40) >> 4, (previous.z + 32) >> 4);
            if (live) {
                net.minecraft.tileentity.TileEntity tile = world
                    .getTileEntity(previous.x + 40, previous.y, previous.z + 32);
                live = tile instanceof TileEntityCriticalController controller && controller.isEnabled()
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

    public void invalidateAt(World world, int x, int y, int z) {
        for (Region r : regions.values()) {
            if (x < r.x || x >= r.x + 80 || z < r.z || z >= r.z + 80 || y < r.y || y > r.y + 161) continue;
            if (!world.getChunkProvider()
                .chunkExists((r.x + 40) >> 4, (r.z + 32) >> 4)) continue;
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(r.x + 40, r.y, r.z + 32);
            if (tile instanceof TileEntityCriticalController controller) controller.invalidateStructure();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        regions.clear();
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
                regions.put(id, region);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        n.setString("nightOwner", nightOwner);
        n.setBoolean("localClock", localClock);
        n.setLong("localTime", localTime);
        NBTTagList list = new NBTTagList();
        for (Region r : regions.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", r.id);
            t.setString("owner", r.owner);
            t.setString("jobId", r.jobId);
            t.setInteger("x", r.x);
            t.setInteger("y", r.y);
            t.setInteger("z", r.z);
            list.appendTag(t);
        }
        n.setTag("regions", list);
    }

    private static final class Region {

        final String id, owner;
        final int x, y, z;
        String jobId = "";

        Region(String id, String owner, int x, int y, int z) {
            this.id = id;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
