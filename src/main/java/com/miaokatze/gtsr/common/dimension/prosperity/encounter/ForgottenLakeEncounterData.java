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

    public void guardDied(String id, int index) {
        if (index >= 0 && index < 12) {
            record(id).setInteger("deaths", deaths(id) | (1 << index));
            markDirty();
        }
    }

    public boolean platformCleared(String id, int platform) {
        return platform >= 0 && platform < 4 && (deaths(id) & (7 << (platform * 3))) == (7 << (platform * 3));
    }

    public boolean allGuardsDead(String id) {
        return deaths(id) == 4095;
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
            records.put(e.getString("id"), e.getCompoundTag("record"));
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
