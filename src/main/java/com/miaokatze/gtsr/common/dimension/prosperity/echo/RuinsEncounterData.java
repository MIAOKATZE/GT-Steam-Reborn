package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Independent ledger; never upgrades or rewrites the king's legacy encounter schema. */
public final class RuinsEncounterData extends WorldSavedData {

    private static final String KEY = "gtsr.prosperityEchoSites";
    private final Map<String, NBTTagCompound> sites = new HashMap<>();

    public RuinsEncounterData() {
        super(KEY);
    }

    public RuinsEncounterData(String name) {
        super(name);
    }

    public static RuinsEncounterData get(World world) {
        RuinsEncounterData data = (RuinsEncounterData) world.perWorldStorage.loadData(RuinsEncounterData.class, KEY);
        if (data == null) {
            data = new RuinsEncounterData();
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    private NBTTagCompound record(String id) {
        NBTTagCompound r = sites.get(id);
        if (r == null) {
            r = new NBTTagCompound();
            sites.put(id, r);
        }
        return r;
    }

    public boolean created(String id, String node) {
        NBTTagCompound n = sites.get(id);
        return n != null && n.getBoolean("made:" + node);
    }

    public void markCreated(String id, String node) {
        record(id).setBoolean("made:" + node, true);
        markDirty();
    }

    public void died(String id, int node) {
        if (!id.startsWith("echo:")) return;
        record(id).setBoolean("dead:" + node, true);
        markDirty();
    }

    public boolean dead(String id, int node) {
        NBTTagCompound n = sites.get(id);
        return n != null && n.getBoolean("dead:" + node);
    }

    public boolean ritualReady(String id, long now) {
        NBTTagCompound n = sites.get(id);
        return n == null || now >= n.getLong("ritualUntil");
    }

    public void ritual(String id, long now) {
        record(id).setLong("ritualUntil", now + 24000);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        sites.clear();
        NBTTagList l = n.getTagList("sites", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            sites.put(e.getString("id"), e.getCompoundTag("record"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<String, NBTTagCompound> e : sites.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", e.getKey());
            t.setTag("record", e.getValue());
            l.appendTag(t);
        }
        n.setTag("sites", l);
    }
}
