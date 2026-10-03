package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.nbt.NBTTagCompound;

/** A persisted revision-seven anchor. The prefab name and variant never change after registration. */
public final class RemasterSite {

    public final String prefab;
    public final int variant, x, y, z;
    public final long seed;
    public final String layout;
    public final int roadVersion;
    public final int entryApronVersion;
    public final String entryApronSha;
    final int[] entryApronWrites;

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z) {
        this(prefab, variant, seed, x, y, z, "prefab");
    }

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z, String layout) {
        this(prefab, variant, seed, x, y, z, layout, 0);
    }

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z, String layout, int roadVersion) {
        this(prefab, variant, seed, x, y, z, layout, roadVersion, 0, "", new int[0]);
    }

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z, String layout, int roadVersion,
        int entryApronVersion, String entryApronSha, int[] entryApronWrites) {
        this.prefab = prefab;
        this.variant = variant;
        this.seed = seed;
        this.x = x;
        this.y = y;
        this.z = z;
        this.layout = layout;
        this.roadVersion = roadVersion;
        this.entryApronVersion = entryApronVersion;
        this.entryApronSha = entryApronSha;
        this.entryApronWrites = entryApronWrites.clone();
    }

    public String id() {
        return "echo:r7:" + seed
            + ":"
            + prefab
            + ":"
            + variant
            + ":"
            + x
            + ":"
            + z
            + ("prefab".equals(layout) ? "" : ":" + layout);
    }

    public int[] entryApronWrites() {
        return entryApronWrites.clone();
    }

    public RemasterPrefab plan() {
        return RemasterCatalog.get(prefab, variant);
    }

    public boolean intersects(int cx, int cz) {
        return (cx << 4) <= maxX() && (cx << 4) + 15 >= minX() && (cz << 4) <= maxZ() && (cz << 4) + 15 >= minZ();
    }

    public int minX() {
        return x + bound("min", 0) - RemasterTerrain.margin(this);
    }

    public int minZ() {
        return z + bound("min", 2) - RemasterTerrain.margin(this);
    }

    public int maxX() {
        return x + bound("max", 0) + RemasterTerrain.margin(this);
    }

    public int maxZ() {
        return z + bound("max", 2) + RemasterTerrain.margin(this);
    }

    private int bound(String side, int axis) {
        if ("city-grid".equals(layout)) return "min".equals(side) ? 0 : axis == 0 ? 767 : 383;
        return RemasterCatalog.descriptor(prefab, variant)
            .getAsJsonArray(side)
            .get(axis)
            .getAsInt();
    }

    public boolean overlaps(int x0, int z0, int x1, int z1, int margin) {
        return minX() - margin <= x1 && maxX() + margin >= x0 && minZ() - margin <= z1 && maxZ() + margin >= z0;
    }

    public int entryX() {
        if ("city-grid".equals(layout)) return x + 4;
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("x") ? x + entry.get("x")
            .getAsInt() : x + bound("min", 0) + 4;
    }

    public int entryZ() {
        if ("city-grid".equals(layout)) return z + 4;
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("z") ? z + entry.get("z")
            .getAsInt() : z + bound("min", 2) + 4;
    }

    public int entryY() {
        if ("city-grid".equals(layout)) return y;
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("topY") ? y + entry.get("topY")
            .getAsInt() + 1 : y + 1;
    }

    private com.google.gson.JsonObject entrance() {
        com.google.gson.JsonObject metadata = plan().metadata;
        com.google.gson.JsonObject entry = metadata.getAsJsonObject("surfaceEntrance");
        return entry != null ? entry : metadata.getAsJsonObject("productionSurfaceEntrance");
    }

    public NBTTagCompound save() {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("prefab", prefab);
        n.setInteger("variant", variant);
        n.setLong("seed", seed);
        n.setInteger("x", x);
        n.setInteger("y", y);
        n.setInteger("z", z);
        n.setString("layout", layout);
        n.setInteger("roadVersion", roadVersion);
        if (entryApronVersion > 0) {
            n.setInteger("entryApronVersion", entryApronVersion);
            n.setString("entryApronSha", entryApronSha);
            n.setIntArray("entryApronWrites", entryApronWrites.clone());
        }
        return n;
    }

    public static RemasterSite read(NBTTagCompound n) {
        return new RemasterSite(
            n.getString("prefab"),
            n.getInteger("variant"),
            n.getLong("seed"),
            n.getInteger("x"),
            n.getInteger("y"),
            n.getInteger("z"),
            n.hasKey("layout") ? n.getString("layout") : "prefab",
            n.getInteger("roadVersion"),
            n.getInteger("entryApronVersion"),
            n.getString("entryApronSha"),
            n.getIntArray("entryApronWrites"));
    }
}
