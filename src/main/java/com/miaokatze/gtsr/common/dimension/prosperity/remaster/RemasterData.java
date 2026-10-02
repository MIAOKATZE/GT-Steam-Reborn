package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Separate named-state ledger: no migration or reinterpretation of v1/v2 objective bitmasks. */
public final class RemasterData extends WorldSavedData {

    private static final String KEY = "gtsr.prosperityRemaster7";
    private final Map<String, NBTTagCompound> records = new HashMap<>();
    private final Map<Long, List<String>> chunks = new HashMap<>();

    private static long chunkKey(int x, int z) {
        return (long) x << 32 ^ (z & 0xffffffffL);
    }

    private void index(RemasterSite s) {
        for (int x = s.minX() >> 4; x <= s.maxX() >> 4; x++) for (int z = s.minZ() >> 4; z <= s.maxZ() >> 4; z++)
            chunks.computeIfAbsent(chunkKey(x, z), k -> new ArrayList<>())
                .add(s.id());
    }

    public RemasterData() {
        super(KEY);
    }

    public RemasterData(String key) {
        super(key);
    }

    public static RemasterData get(World w) {
        RemasterData d = (RemasterData) w.perWorldStorage.loadData(RemasterData.class, KEY);
        if (d == null) {
            d = new RemasterData();
            w.perWorldStorage.setData(KEY, d);
        }
        return d;
    }

    public void register(RemasterSite s) {
        if (records.containsKey(s.id())) return;
        records.put(s.id(), s.save());
        index(s);
        markDirty();
    }

    public RemasterSite site(String id) {
        NBTTagCompound n = records.get(id);
        return n == null ? null : RemasterSite.read(n);
    }

    public List<RemasterSite> inChunk(int cx, int cz) {
        List<RemasterSite> out = new ArrayList<>();
        List<String> ids = chunks.get(chunkKey(cx, cz));
        if (ids != null) for (String id : ids) out.add(site(id));
        return out;
    }

    public NBTTagCompound state(String id) {
        NBTTagCompound n = records.get(id);
        if (n == null) throw new IllegalArgumentException("Unregistered remaster site " + id);
        if (!n.hasKey("state")) n.setTag("state", new NBTTagCompound());
        return n.getCompoundTag("state");
    }

    public boolean flag(String id, String key) {
        return records.containsKey(id) && state(id).getBoolean(key);
    }

    public void flag(String id, String key, boolean value) {
        state(id).setBoolean(key, value);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        records.clear();
        chunks.clear();
        NBTTagList list = n.getTagList("sites", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound r = list.getCompoundTagAt(i);
            if (r.getString("prefab")
                .isEmpty()) continue;
            records.put(
                RemasterSite.read(r)
                    .id(),
                r);
            index(RemasterSite.read(r));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound r : records.values()) list.appendTag(r);
        n.setTag("sites", list);
    }
}
