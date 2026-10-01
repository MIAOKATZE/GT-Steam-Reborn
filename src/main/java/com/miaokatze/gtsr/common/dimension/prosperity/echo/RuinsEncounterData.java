package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Independent ledger; never upgrades or rewrites the king's legacy encounter schema. */
public final class RuinsEncounterData extends WorldSavedData {

    private static final String KEY = "gtsr.prosperityEchoSites";
    private final Map<String, NBTTagCompound> sites = new HashMap<>();
    private final Map<Long, Set<String>> spatial = new HashMap<>();

    private static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void index(String id, NBTTagCompound r) {
        if (!r.hasKey("width")) return;
        int x = r.getInteger("x"), z = r.getInteger("z");
        for (int cx = x >> 8; cx <= (x + r.getInteger("width") - 1) >> 8; cx++)
            for (int cz = z >> 8; cz <= (z + r.getInteger("depth") - 1) >> 8; cz++) {
                long k = cell(cx, cz);
                if (!spatial.containsKey(k)) spatial.put(k, new HashSet<String>());
                spatial.get(k)
                    .add(id);
            }
    }

    public void registerSite(RuinSite s) {
        NBTTagCompound r = record(s.id());
        if (r.hasKey("width")) return;
        r.setInteger("layoutVersion", s.layout);
        r.setInteger("kind", s.kind);
        r.setLong("seed", s.seed);
        r.setInteger("x", s.x);
        r.setInteger("y", s.y);
        r.setInteger("z", s.z);
        r.setInteger("width", s.width);
        r.setInteger("depth", s.depth);
        index(s.id(), r);
        markDirty();
    }

    public RuinSite site(String id) {
        NBTTagCompound r = sites.get(id);
        if (r == null || !r.hasKey("width")) return null;
        return new RuinSite(
            r.getLong("seed"),
            r.getInteger("kind"),
            r.getInteger("x"),
            r.getInteger("y"),
            r.getInteger("z"),
            0,
            r.hasKey("layoutVersion") ? r.getInteger("layoutVersion") : 1);
    }

    public List<RuinSite> existingSitesNear(int x, int z, int radius) {
        Set<String> ids = new HashSet<>();
        for (int cx = (x - radius) >> 8; cx <= (x + radius) >> 8; cx++)
            for (int cz = (z - radius) >> 8; cz <= (z + radius) >> 8; cz++) {
                Set<String> found = spatial.get(cell(cx, cz));
                if (found != null) ids.addAll(found);
            }
        List<RuinSite> result = new ArrayList<>();
        for (String id : ids) {
            RuinSite s = site(id);
            if (s != null) result.add(s);
        }
        return result;
    }

    public int objectiveMask(String id) {
        NBTTagCompound r = sites.get(id);
        return r == null ? 0 : r.getInteger("objectiveMask");
    }

    public void objectiveMask(String id, int mask) {
        record(id).setInteger("objectiveMask", mask);
        markDirty();
    }

    public void zoneClearMask(String id, int mask) {
        record(id).setInteger("zoneClearMask", mask);
        markDirty();
    }

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
        spatial.clear();
        NBTTagList l = n.getTagList("sites", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            String id = e.getString("id");
            NBTTagCompound r = e.getCompoundTag("record");
            if (!r.hasKey("width") && id.startsWith("echo:")) {
                try {
                    String[] parts = id.split(":");
                    int kind = Integer.parseInt(parts[2]);
                    r.setInteger("kind", kind);
                    r.setLong("seed", Long.parseLong(parts[1]));
                    r.setInteger("x", Integer.parseInt(parts[3]));
                    r.setInteger("z", Integer.parseInt(parts[4]));
                    r.setInteger("layoutVersion", 1);
                    r.setInteger("width", RuinSite.widthFor(kind, 1));
                    r.setInteger("depth", RuinSite.depthFor(kind, 1));
                    r.setInteger(
                        "y",
                        com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile.heightAt(
                            r.getLong("seed"),
                            r.getInteger("x") + RuinSite.widthFor(kind, 1) / 2,
                            r.getInteger("z") + RuinSite.depthFor(kind, 1) / 2) + 1);
                } catch (RuntimeException malformed) { /* Preserve opaque legacy record. */ }
            }
            sites.put(id, r);
            index(id, r);
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
