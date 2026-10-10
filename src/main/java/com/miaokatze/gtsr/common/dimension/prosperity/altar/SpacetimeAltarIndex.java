package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Only successful placements enter the index. Looking up entries never loads chunks. */
public final class SpacetimeAltarIndex extends WorldSavedData {

    private static final String KEY = "gtsrSpacetimeAltars";
    private final List<Entry> entries = new ArrayList<>();

    public SpacetimeAltarIndex() {
        super(KEY);
    }

    public SpacetimeAltarIndex(String name) {
        super(name);
    }

    public static SpacetimeAltarIndex get(World world) {
        SpacetimeAltarIndex data = (SpacetimeAltarIndex) world.mapStorage.loadData(SpacetimeAltarIndex.class, KEY);
        if (data == null) {
            data = new SpacetimeAltarIndex();
            world.mapStorage.setData(KEY, data);
        }
        return data;
    }

    public static Entry nearest(World world, double x, double z) {
        if (world == null || world.isRemote || world.provider.dimensionId != 0) return null;
        Entry nearest = null;
        double distance = Double.MAX_VALUE;
        for (Entry entry : get(world).entries) {
            if (!entry.valid) continue;
            if (world.getChunkProvider()
                .chunkExists(entry.x >> 4, entry.z >> 4)
                && world.getBlock(entry.x, entry.y, entry.z) != SpacetimeAltarBlocks.core) {
                entry.valid = false;
                get(world).markDirty();
                continue;
            }
            double candidate = (entry.x - x) * (entry.x - x) + (entry.z - z) * (entry.z - z);
            if (candidate < distance) {
                nearest = entry;
                distance = candidate;
            }
        }
        return nearest;
    }

    public void add(String id, int x, int y, int z) {
        for (Entry entry : entries) if (entry.id.equals(id)) return;
        entries.add(new Entry(id, x, y, z, true));
        markDirty();
    }

    public Entry find(String id) {
        for (Entry entry : entries) if (entry.id.equals(id)) return entry;
        return null;
    }

    public void markRead(String id) {
        Entry entry = find(id);
        if (entry != null && entry.valid) {
            entry.storyRead = true;
            markDirty();
        }
    }

    public void retire(String id) {
        for (Entry entry : entries) if (entry.id.equals(id)) {
            entry.valid = false;
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        NBTTagList list = tag.getTagList("entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            Entry entry = new Entry(
                row.getString("id"),
                row.getInteger("x"),
                row.getInteger("y"),
                row.getInteger("z"),
                row.getBoolean("valid"));
            entry.storyRead = row.getBoolean("storyRead");
            entries.add(entry);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", entry.id);
            row.setInteger("x", entry.x);
            row.setInteger("y", entry.y);
            row.setInteger("z", entry.z);
            row.setBoolean("valid", entry.valid);
            row.setBoolean("storyRead", entry.storyRead);
            list.appendTag(row);
        }
        tag.setTag("entries", list);
    }

    public static final class Entry {

        public final String id;
        public final int x, y, z;
        public boolean valid, storyRead;

        Entry(String id, int x, int y, int z, boolean valid) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.valid = valid;
        }
    }
}
