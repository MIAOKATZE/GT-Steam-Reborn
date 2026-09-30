package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Persistent authority: absence of an unloaded entity never counts as a kill. */
public class ForgottenLakeEncounterData extends WorldSavedData {

    public static final String NAME = "gtsr_forgotten_encounters";
    private final Map<String, NBTTagCompound> records = new HashMap<>();

    public ForgottenLakeEncounterData() {
        super(NAME);
    }

    public ForgottenLakeEncounterData(String n) {
        super(n);
    }

    public static ForgottenLakeEncounterData get(World w) {
        ForgottenLakeEncounterData d = (ForgottenLakeEncounterData) w.perWorldStorage
            .loadData(ForgottenLakeEncounterData.class, NAME);
        if (d == null) {
            d = new ForgottenLakeEncounterData();
            w.perWorldStorage.setData(NAME, d);
        }
        return d;
    }

    /** Read-only queries never create encounter records. */
    public boolean known(String id) {
        return records.containsKey(id);
    }

    public int remaining(String id) {
        NBTTagCompound n = records.get(id);
        if (n == null) return 0;
        int version = n.getInteger("layoutVersion");
        int count = version == 1 ? 12 : version == 2 ? 16 : 32;
        int mask = count == 32 ? -1 : (1 << count) - 1;
        return count - Integer.bitCount(n.getInteger("deaths") & mask);
    }

    public boolean anyPlatformCleared(String id) {
        if (!known(id)) return false;
        for (int p = 0; p < (layoutVersion(id) == 3 ? 8 : 4); p++) if (platformCleared(id, p)) return true;
        return false;
    }

    public void registerBounds(String id, int ax, int az, int minY, int maxY, int radius) {
        NBTTagCompound n = record(id);
        if (n.hasKey("bounds")) return;
        NBTTagCompound b = new NBTTagCompound();
        b.setInteger("x", ax);
        b.setInteger("z", az);
        b.setInteger("minY", minY);
        b.setInteger("maxY", maxY);
        b.setInteger("radius", radius);
        n.setTag("bounds", b);
        markDirty();
    }

    public boolean contains(String id, double x, double y, double z) {
        NBTTagCompound n = records.get(id);
        if (n == null || !n.hasKey("bounds")) return false;
        NBTTagCompound b = n.getCompoundTag("bounds");
        double dx = x - b.getInteger("x"), dz = z - b.getInteger("z");
        int r = Math.min(160, b.getInteger("radius"));
        return y >= b.getInteger("minY") && y <= b.getInteger("maxY") && dx * dx + dz * dz <= r * r;
    }

    public void registerGuardAnchor(String id, int index, double x, double y, double z) {
        if (!known(id) || index < 0 || index >= 32 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            return;
        NBTTagCompound n = records.get(id);
        String key = "guardAnchor:" + index;
        if (n.hasKey(key)) {
            NBTTagCompound existing = n.getCompoundTag(key);
            if (existing.getDouble("x") == x && existing.getDouble("y") == y && existing.getDouble("z") == z) return;
        }
        NBTTagCompound a = new NBTTagCompound();
        a.setDouble("x", x);
        a.setDouble("y", y);
        a.setDouble("z", z);
        n.setTag(key, a);
        markDirty();
    }

    public double[] nearestLivingAnchor(String id, double x, double y, double z) {
        NBTTagCompound n = records.get(id);
        if (n == null) return null;
        double[] best = null;
        double distance = Double.MAX_VALUE;
        int count = layoutVersion(id) == 1 ? 12 : layoutVersion(id) == 2 ? 16 : 32;
        for (int i = 0; i < count; i++) {
            if ((n.getInteger("deaths") & (1 << i)) != 0 || !n.hasKey("guardAnchor:" + i)) continue;
            NBTTagCompound a = n.getCompoundTag("guardAnchor:" + i);
            double dx = a.getDouble("x") - x, dy = a.getDouble("y") - y, dz = a.getDouble("z") - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < distance) {
                distance = d;
                best = new double[] { a.getDouble("x"), a.getDouble("y"), a.getDouble("z") };
            }
        }
        return best;
    }

    /** Reconstruct absent legacy anchor slots from their saved layout, without loading chunks. */
    public void ensureGuardAnchors(World w, String id) {
        if (!known(id)) return;
        String[] parts = id.split(":");
        if (parts.length != 3) return;
        try {
            int ax = Integer.parseInt(parts[1]), az = Integer.parseInt(parts[2]);
            int[] dimensions = ForgottenLakeEncounterStructure.encounterDimensions(w, ax, az);
            int version = layoutVersion(id);
            for (int room = 0; room < (version == 3 ? 8 : 4); room++) {
                for (int ordinal = 0; ordinal < guardCount(id, room); ordinal++) {
                    int index = guardIndex(id, room, ordinal);
                    if (records.get(id)
                        .hasKey("guardAnchor:" + index) || guardDead(id, room, ordinal)) continue;
                    int[] p;
                    if (version == 3) p = ForgottenLakeEncounterStructure
                        .guardPosition(ax, az, dimensions[0], dimensions[1], room, ordinal);
                    else if (version == 2) p = ForgottenLakeEncounterStructure
                        .guardPositionV2(ax, az, dimensions[0], dimensions[1], room, ordinal);
                    else {
                        p = ForgottenLakeEncounterStructure.legacyPlatform(ax, az, dimensions[0], dimensions[1], room);
                        p[0] += -3 + ordinal * 3;
                        p[1]++;
                        p[2] += 3;
                    }
                    registerGuardAnchor(id, index, p[0] + .5, p[1], p[2] + .5);
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    private NBTTagCompound record(String id) {
        NBTTagCompound n = records.get(id);
        if (n == null) {
            n = new NBTTagCompound();
            n.setInteger("layoutVersion", 3);
            records.put(id, n);
            markDirty();
        }
        return n;
    }

    public boolean created(String id, String node) {
        NBTTagCompound n = records.get(id);
        return n != null && n.getBoolean("node:" + node);
    }

    public void createdNode(String id, String node) {
        record(id).setBoolean("node:" + node, true);
        markDirty();
    }

    public int deaths(String id) {
        NBTTagCompound n = records.get(id);
        return n == null ? 0 : n.getInteger("deaths");
    }

    public void registerLayout(String id, int version) {
        // Loading legacy records assigns V1 before any queries; registration never upgrades them.
        boolean fresh = !records.containsKey(id);
        NBTTagCompound n = record(id);
        if (fresh || !n.hasKey("layoutVersion")) {
            n.setInteger("layoutVersion", Math.max(1, Math.min(3, version)));
            markDirty();
        }
    }

    public int layoutVersion(String id) {
        NBTTagCompound n = records.get(id);
        return n == null ? 3 : Math.max(1, Math.min(3, n.getInteger("layoutVersion")));
    }

    public int guardCount(String id, int platform) {
        int version = layoutVersion(id);
        if (platform < 0 || platform >= (version == 3 ? 8 : 4)) return 0;
        return version == 1 ? 3 : new int[] { 3, 4, 5, 4 }[platform % 4];
    }

    public int guardIndex(String id, int platform, int ordinal) {
        if (ordinal < 0 || ordinal >= guardCount(id, platform)) return -1;
        int start = 0;
        for (int p = 0; p < platform; p++) start += guardCount(id, p);
        return start + ordinal;
    }

    public boolean guardDead(String id, int platform, int ordinal) {
        int i = guardIndex(id, platform, ordinal);
        return i >= 0 && (deaths(id) & (1 << i)) != 0;
    }

    public void guardDied(String id, int index) {
        int version = layoutVersion(id);
        int count = version == 1 ? 12 : version == 2 ? 16 : 32;
        if (index >= 0 && index < count) {
            record(id).setInteger("deaths", deaths(id) | (1 << index));
            markDirty();
        }
    }

    public boolean platformCleared(String id, int platform) {
        int count = guardCount(id, platform);
        if (count == 0) return false;
        int mask = ((1 << count) - 1) << guardIndex(id, platform, 0);
        return (deaths(id) & mask) == mask;
    }

    public boolean allGuardsDead(String id) {
        int version = layoutVersion(id);
        // V3 consumes every bit of the existing int, including the sign bit (guard 31).
        int mask = version == 1 ? 4095 : version == 2 ? 65535 : -1;
        return (deaths(id) & mask) == mask;
    }

    public boolean kingDead(String id) {
        NBTTagCompound n = records.get(id);
        return n != null && n.getBoolean("kingDead");
    }

    public void kingDied(String id) {
        record(id).setBoolean("kingDead", true);
        markDirty();
    }

    public void readFromNBT(NBTTagCompound n) {
        records.clear();
        NBTTagList l = n.getTagList("encounters", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            NBTTagCompound saved = e.getCompoundTag("record");
            saved.setInteger(
                "layoutVersion",
                saved.hasKey("layoutVersion") ? Math.max(1, Math.min(3, saved.getInteger("layoutVersion"))) : 1);
            records.put(e.getString("id"), saved);
        }
    }

    public void writeToNBT(NBTTagCompound n) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<String, NBTTagCompound> e : records.entrySet()) {
            NBTTagCompound a = new NBTTagCompound();
            a.setString("id", e.getKey());
            a.setTag("record", e.getValue());
            l.appendTag(a);
        }
        n.setTag("encounters", l);
    }
}
