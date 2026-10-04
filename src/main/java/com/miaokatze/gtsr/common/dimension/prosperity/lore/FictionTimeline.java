package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Persistent world authority. A completed site never creates another presentation or reward. */
public final class FictionTimeline extends WorldSavedData {

    private static final String KEY = "gtsr.fictionDemonstration";
    private boolean worldCompleted;
    public final Map<String, NBTTagCompound> sites = new LinkedHashMap<>();

    public FictionTimeline() {
        super(KEY);
    }

    public FictionTimeline(String key) {
        super(key);
    }

    public static FictionTimeline get(World world) {
        FictionTimeline data = (FictionTimeline) world.perWorldStorage.loadData(FictionTimeline.class, KEY);
        if (data == null) {
            data = new FictionTimeline();
            world.perWorldStorage.setData(KEY, data);
        }
        return data;
    }

    public static int elapsed(NBTTagCompound site, long now) {
        long elapsed = Math.max(site.getInteger("elapsed"), Math.max(0L, now - site.getLong("start")));
        return (int) Math.min(440L, elapsed);
    }

    public static int elapsed(NBTTagCompound site, long now, long wall) {
        int ticks = elapsed(site, now);
        long began = site.getLong("startWall");
        if (began > 0 && wall >= began) ticks = Math.max(ticks, (int) Math.min(440L, (wall - began) / 50));
        return ticks;
    }

    public static int phase(int elapsed) {
        return elapsed >= 440 ? 4 : elapsed >= 200 ? 3 : elapsed >= 120 ? 2 : 1;
    }

    public boolean blue() {
        return worldCompleted();
    }

    /** Legacy completed site records are also authoritative world completion evidence. */
    public boolean worldCompleted() {
        if (worldCompleted) return true;
        for (NBTTagCompound site : sites.values()) if (site.getBoolean("complete")) return true;
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        worldCompleted = tag.getBoolean("worldCompleted");
        sites.clear();
        NBTTagList list = tag.getTagList("sites", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound site = list.getCompoundTagAt(i);
            String id = site.getString("id");
            if (!id.isEmpty()) {
                int elapsed = Math.max(0, Math.min(440, site.getInteger("elapsed")));
                site.setInteger("elapsed", elapsed);
                if (elapsed >= 440) site.setBoolean("complete", true);
                sites.put(id, site);
            }
        }
        worldCompleted = worldCompleted();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        worldCompleted = worldCompleted();
        tag.setBoolean("worldCompleted", worldCompleted);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound site : sites.values()) list.appendTag(site.copy());
        tag.setTag("sites", list);
    }
}
