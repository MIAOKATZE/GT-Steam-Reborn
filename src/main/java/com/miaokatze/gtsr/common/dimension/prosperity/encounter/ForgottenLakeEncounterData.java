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
        return record(id).getBoolean("node:" + node);
    }

    public void createdNode(String id, String node) {
        record(id).setBoolean("node:" + node, true);
        markDirty();
    }

    public int deaths(String id) {
        return record(id).getInteger("deaths");
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
        return Math.max(1, Math.min(3, record(id).getInteger("layoutVersion")));
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
        return record(id).getBoolean("kingDead");
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
