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
    private List<GeneratedOwner> generatedOwners;
    private List<GeneratedOwner> pendingTreeOwners, retryOwners;
    private Map<String, List<RemasterSite>> savedPlacementGroups;

    public static final class GeneratedOwner {

        public final RemasterSite site;
        public final int x, z;

        GeneratedOwner(RemasterSite site, int x, int z) {
            this.site = site;
            this.x = x;
            this.z = z;
        }
    }

    /** Rebuilt when saved geometry ownership changes, never for ordinary node/entity state updates. */
    public List<GeneratedOwner> generatedOwners() {
        if (generatedOwners != null) return generatedOwners;
        List<GeneratedOwner> result = new ArrayList<>();
        for (NBTTagCompound record : records.values()) {
            RemasterSite site = RemasterSite.read(record);
            NBTTagCompound state = record.getCompoundTag("state");
            for (Object raw : state.func_150296_c()) {
                String key = (String) raw;
                if (!key.startsWith("geom:") || !state.getBoolean(key)) continue;
                String[] parts = key.split(":");
                if (parts.length != 3) continue;
                try {
                    int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
                    if (site.intersects(x, z)) result.add(new GeneratedOwner(site, x, z));
                } catch (NumberFormatException invalid) { /* Ignore malformed legacy flags. */ }
            }
        }
        result.sort((a, b) -> {
            int id = a.site.id()
                .compareTo(b.site.id());
            return id != 0 ? id : a.x != b.x ? Integer.compare(a.x, b.x) : Integer.compare(a.z, b.z);
        });
        generatedOwners = java.util.Collections.unmodifiableList(result);
        return generatedOwners;
    }

    /** Pending entrance admission is independent of upper geometry, including entrance-only owners. */
    public List<GeneratedOwner> pendingTreeOwners() {
        if (pendingTreeOwners != null) return pendingTreeOwners;
        List<GeneratedOwner> result = new ArrayList<>();
        for (NBTTagCompound record : records.values()) {
            RemasterSite site = RemasterSite.read(record);
            NBTTagCompound state = record.getCompoundTag("state");
            if (!"tree-overlay".equals(site.layout) || !state.getBoolean("tree-entrance:pending")
                || state.getBoolean("tree-entrance:closed")
                || !state.hasKey("legacyAnchorX")
                || !state.hasKey("legacyAnchorZ")) continue;
            int x = (state.getInteger("legacyAnchorX") - 24) >> 4;
            int z = (state.getInteger("legacyAnchorZ") - 27) >> 4;
            if (site.intersects(x, z)) result.add(new GeneratedOwner(site, x, z));
        }
        pendingTreeOwners = java.util.Collections.unmodifiableList(result);
        return pendingTreeOwners;
    }

    /** Cached union shares one tick budget and visits a geometry/pending owner only once. */
    public List<GeneratedOwner> retryOwners() {
        if (retryOwners != null) return retryOwners;
        Map<String, GeneratedOwner> unique = new java.util.LinkedHashMap<>();
        for (GeneratedOwner owner : generatedOwners())
            unique.put(owner.site.id() + ":" + owner.x + ":" + owner.z, owner);
        for (GeneratedOwner owner : pendingTreeOwners())
            unique.putIfAbsent(owner.site.id() + ":" + owner.x + ":" + owner.z, owner);
        List<GeneratedOwner> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> {
            int id = a.site.id()
                .compareTo(b.site.id());
            return id != 0 ? id : a.x != b.x ? Integer.compare(a.x, b.x) : Integer.compare(a.z, b.z);
        });
        retryOwners = java.util.Collections.unmodifiableList(result);
        return retryOwners;
    }

    public boolean legacyTreeLayout(RemasterSite site) {
        return "tree-overlay".equals(site.layout) && state(site.id()).getInteger("treeLayoutRevision") == 1;
    }

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
        NBTTagCompound record = s.save();
        if ("tree-overlay".equals(s.layout)) {
            NBTTagCompound state = new NBTTagCompound();
            state.setInteger("treeLayoutRevision", 2);
            record.setTag("state", state);
        }
        records.put(s.id(), record);
        savedPlacementGroups = null;
        generatedOwners = pendingTreeOwners = retryOwners = null;
        index(s);
        markDirty();
    }

    public RemasterSite site(String id) {
        NBTTagCompound n = records.get(id);
        return n == null ? null : RemasterSite.read(n);
    }

    /** Existing save anchors take precedence over a newly predicted anchor for the same site. */
    public RemasterSite nearestExisting(long seed, String prefab, int x, int z) {
        RemasterSite best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (NBTTagCompound record : records.values()) {
            if (!prefab.equals(record.getString("prefab")) || record.getLong("seed") != seed) continue;
            RemasterSite site = RemasterSite.read(record);
            double dx = site.entryX() - (double) x, dz = site.entryZ() - (double) z;
            double candidate = dx * dx + dz * dz;
            if (candidate < distance) {
                best = site;
                distance = candidate;
            }
        }
        return best;
    }

    public List<RemasterSite> inChunk(int cx, int cz) {
        List<RemasterSite> out = new ArrayList<>();
        List<String> ids = chunks.get(chunkKey(cx, cz));
        if (ids != null) for (String id : ids) out.add(site(id));
        return out;
    }

    /** Same IDs retain their saved anchors; new predictions cannot replace an occupied save cell. */
    public boolean allowsSavedPlacement(RemasterSite candidate) {
        if (records.containsKey(candidate.id()) || "tree-overlay".equals(candidate.layout)) return true;
        String group = RemasterSavedPlacement.group(candidate);
        if (group != null) {
            if (savedPlacementGroups == null) {
                savedPlacementGroups = new HashMap<>();
                for (NBTTagCompound record : records.values()) {
                    RemasterSite saved = RemasterSite.read(record);
                    String savedGroup = RemasterSavedPlacement.group(saved);
                    if (savedGroup != null) savedPlacementGroups.computeIfAbsent(savedGroup, k -> new ArrayList<>())
                        .add(saved);
                }
            }
            List<RemasterSite> saved = savedPlacementGroups.get(group);
            if (saved != null) for (RemasterSite prior : saved) {
                // A city is one family, with one persisted identity per authored prefab.
                if (!RemasterSavedPlacement.city(candidate) || !RemasterSavedPlacement.city(prior)
                    || candidate.prefab.equals(prior.prefab)) return false;
            }
        }
        java.util.Set<String> checked = new java.util.HashSet<>();
        for (int x = candidate.minX() >> 4; x <= candidate.maxX() >> 4; x++)
            for (int z = candidate.minZ() >> 4; z <= candidate.maxZ() >> 4; z++) {
                List<String> ids = chunks.get(chunkKey(x, z));
                if (ids == null) continue;
                for (String id : ids) {
                    if (!checked.add(id)) continue;
                    RemasterSite prior = site(id);
                    if (prior.seed != candidate.seed || "tree-overlay".equals(prior.layout)
                        || RemasterSavedPlacement.cityParentOverlap(candidate, prior)) continue;
                    if (candidate.overlaps(prior.minX(), prior.minZ(), prior.maxX(), prior.maxZ(), 0)) return false;
                }
            }
        return true;
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
        if (state(id).getBoolean(key) != value) {
            if (key.startsWith("geom:")) generatedOwners = retryOwners = null;
            if (key.startsWith("tree-entrance:")) pendingTreeOwners = retryOwners = null;
        }
        state(id).setBoolean(key, value);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        records.clear();
        chunks.clear();
        savedPlacementGroups = null;
        generatedOwners = pendingTreeOwners = retryOwners = null;
        NBTTagList list = n.getTagList("sites", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound r = list.getCompoundTagAt(i);
            if (r.getString("prefab")
                .isEmpty()) continue;
            RemasterSite saved = RemasterSite.read(r);
            if ("tree-overlay".equals(saved.layout)) {
                NBTTagCompound state = r.getCompoundTag("state");
                if (!state.hasKey("treeLayoutRevision")) {
                    boolean anchored = state.hasKey("legacyAnchorX") && state.hasKey("legacyAnchorZ");
                    boolean oldAnchor = anchored && saved.x == state.getInteger("legacyAnchorX") - 154
                        && saved.z == state.getInteger("legacyAnchorZ") - 154;
                    state.setInteger("treeLayoutRevision", !anchored || oldAnchor ? 1 : 2);
                    r.setTag("state", state);
                    markDirty();
                }
            }
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
