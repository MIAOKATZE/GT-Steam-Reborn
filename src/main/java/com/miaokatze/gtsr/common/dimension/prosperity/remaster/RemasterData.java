package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/** Separate named-state ledger: no migration or reinterpretation of v1/v2 objective bitmasks. */
public final class RemasterData extends WorldSavedData {

    private static final String KEY = "gtsr.prosperityRemaster7";
    private final Map<String, NBTTagCompound> records = new HashMap<>();
    // Pausing a prefab/variant freezes its ledger, not its saved progress. Keep opaque records out
    // of every runtime/index lookup while preserving them for a later compatible rollout.
    private final List<NBTTagCompound> frozenRecords = new ArrayList<>();
    // A readable identity with unreadable progress must not be replayed by seed planning.
    private final Set<String> frozenIds = new HashSet<>();
    // An unreadable container has no trustworthy identities. Preserve it and admit no fresh sites.
    private NBTBase frozenSites;
    private final Map<Long, List<String>> chunks = new HashMap<>();
    private List<GeneratedOwner> generatedOwners;
    private List<GeneratedOwner> retryOwners;

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
                } catch (NumberFormatException invalid) { /* Ignore malformed ownership flags. */ }
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

    /** Only industrial geometry has runtime node/entity retries; native tree chunks are already complete. */
    public List<GeneratedOwner> retryOwners() {
        if (retryOwners != null) return retryOwners;
        List<GeneratedOwner> result = new ArrayList<>();
        for (GeneratedOwner owner : generatedOwners())
            if ("compact-prefab".equals(owner.site.layout) || "natural-prefab".equals(owner.site.layout))
                result.add(owner);
        retryOwners = java.util.Collections.unmodifiableList(result);
        return retryOwners;
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
        if (frozenSites != null || !RemasterRollout.allowsGeneration(s)
            || frozenIds.contains(s.id())
            || records.containsKey(s.id())) return;
        NBTTagCompound record = s.save();
        records.put(s.id(), record);
        generatedOwners = retryOwners = null;
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

    /** The same saved identity is stable; different scenes must not overlap an actual saved footprint. */
    public boolean allowsSavedPlacement(RemasterSite candidate) {
        if (frozenSites != null || !RemasterRollout.allowsGeneration(candidate) || frozenIds.contains(candidate.id()))
            return false;
        if (records.containsKey(candidate.id())) return true;
        java.util.Set<String> checked = new java.util.HashSet<>();
        for (int x = candidate.minX() >> 4; x <= candidate.maxX() >> 4; x++)
            for (int z = candidate.minZ() >> 4; z <= candidate.maxZ() >> 4; z++) {
                List<String> ids = chunks.get(chunkKey(x, z));
                if (ids == null) continue;
                for (String id : ids) {
                    if (!checked.add(id)) continue;
                    RemasterSite prior = site(id);
                    if (prior.seed != candidate.seed) continue;
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
        }
        state(id).setBoolean(key, value);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        records.clear();
        frozenRecords.clear();
        frozenIds.clear();
        frozenSites = null;
        chunks.clear();
        generatedOwners = retryOwners = null;
        NBTBase container = n.getTag("sites");
        if (container != null && (!(container instanceof NBTTagList) || n.getTagList("sites", 10)
            .tagCount() != ((NBTTagList) container).tagCount())) {
            // getTagList silently returns an empty list for the wrong element type.
            frozenSites = container.copy();
            return;
        }
        NBTTagList list = n.getTagList("sites", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound record = list.getCompoundTagAt(i);
            if (!hasIdentity(record)) {
                // Numeric getters otherwise turn missing/wrong-type identity fields into zero.
                frozenRecords.add((NBTTagCompound) record.copy());
                continue;
            }
            // Filter before indexing: removed prefabs must never reach descriptor loading.
            RemasterSite saved = RemasterSite.read(record);
            if (!RemasterRollout.allowsGeneration(saved) || !record.hasKey("y", 3)
                || saved.y < 0
                || saved.y >= 256
                || Math.abs((long) saved.x) > 30000000L
                || Math.abs((long) saved.z) > 30000000L
                || !readableState(record)) {
                frozenRecords.add((NBTTagCompound) record.copy());
                frozenIds.add(saved.id());
                continue;
            }
            if (records.containsKey(saved.id())) {
                // Conflicting duplicate progress has no authoritative winner. Never pick the first
                // (possibly unclaimed) copy and silently delete a later spent/claimed ledger.
                frozenRecords.add((NBTTagCompound) record.copy());
                frozenIds.add(saved.id());
                continue;
            }
            records.put(saved.id(), record);
        }
        // Input order must not let a healthy-looking duplicate replace unreadable same-site progress.
        for (String id : frozenIds) {
            NBTTagCompound record = records.remove(id);
            if (record != null) frozenRecords.add((NBTTagCompound) record.copy());
        }
        for (NBTTagCompound record : records.values()) index(RemasterSite.read(record));
    }

    private static boolean hasIdentity(NBTTagCompound record) {
        return record.hasKey("prefab", 8) && record.hasKey("variant", 3)
            && record.hasKey("seed", 4)
            && record.hasKey("x", 3)
            && record.hasKey("z", 3)
            && record.hasKey("layout", 8);
    }

    /** Validate only schemas this revision reads; unknown extension tags stay opaque and untouched. */
    private static boolean readableState(NBTTagCompound record) {
        if (!record.hasKey("state")) return true;
        if (!record.hasKey("state", 10)) return false;
        NBTTagCompound state = record.getCompoundTag("state");
        for (Object raw : state.func_150296_c()) {
            String key = (String) raw;
            if (key.startsWith("spawner:")) {
                if (!state.hasKey(key, 10)) return false;
                NBTTagCompound spawner = state.getCompoundTag(key);
                if (!typedIfPresent(spawner, 3, "count", "serial", "deadCount", "contract", "pendingLedgerVersion")
                    || !typedIfPresent(spawner, 4, "next", "unsealAt", "unlockAt", "pulseAt")) return false;
            } else if (flagKey(key) && !state.hasKey(key, 1)) return false;
        }
        return typedIfPresent(
            state,
            3,
            "legacyAnchorX",
            "legacyAnchorY",
            "legacyAnchorZ",
            "industrialBossStage",
            "industrialRevivalTicks") && typedIfPresent(state, 5, "industrialBossHealth")
            && typedIfPresent(state, 8, "legacyEncounter");
    }

    private static boolean typedIfPresent(NBTTagCompound tag, int type, String... keys) {
        for (String key : keys) if (tag.hasKey(key) && !tag.hasKey(key, type)) return false;
        return true;
    }

    private static boolean flagKey(String key) {
        return key.equals("boss-dead") || key.equals("mission-boss-ready")
            || key.startsWith("geom:")
            || key.startsWith("node:")
            || key.startsWith("entity:")
            || key.startsWith("dead:")
            || key.startsWith("claimed:")
            || key.startsWith("destroyed:")
            || key.startsWith("sealed:")
            || key.startsWith("puzzle:")
            || key.startsWith("witness:")
            || key.startsWith("story-read:")
            || key.startsWith("boss-dead:")
            || spawnerMemberFlag(key);
    }

    private static boolean spawnerMemberFlag(String key) {
        if (!key.startsWith("spawner-admitted:") && !key.startsWith("spawner-dead:")) return false;
        int member = key.lastIndexOf(':');
        // The runtime/compactor consumes canonical nonnegative serials only. Future marker suffixes
        // are opaque extension data, not malformed booleans, and must not freeze a valid survivor.
        if (member <= key.indexOf(':') + 1) return false;
        String serial = key.substring(member + 1);
        try {
            int parsed = Integer.parseInt(serial);
            return parsed >= 0 && Integer.toString(parsed)
                .equals(serial);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        if (frozenSites != null) {
            n.setTag("sites", frozenSites.copy());
            return;
        }
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound r : records.values()) list.appendTag(r);
        for (NBTTagCompound r : frozenRecords) list.appendTag(r.copy());
        n.setTag("sites", list);
    }
}
