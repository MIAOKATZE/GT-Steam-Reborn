package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.nbt.NBTTagCompound;

/** A persisted revision-seven anchor. The prefab name and variant never change after registration. */
public final class RemasterSite {

    public final String prefab;
    public final int variant, x, y, z;
    public final long seed;
    public final String layout;

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z) {
        this(prefab, variant, seed, x, y, z, "compact-prefab");
    }

    public RemasterSite(String prefab, int variant, long seed, int x, int y, int z, String layout) {
        this.prefab = prefab;
        this.variant = variant;
        this.seed = seed;
        this.x = x;
        this.y = y;
        this.z = z;
        this.layout = layout;
    }

    public String id() {
        return "echo:r7:" + seed + ":" + prefab + ":" + variant + ":" + x + ":" + z + ":" + layout;
    }

    public RemasterPrefab plan() {
        return RemasterCatalog.get(prefab, variant);
    }

    public boolean intersects(int cx, int cz) {
        return (cx << 4) <= maxX() && (cx << 4) + 15 >= minX() && (cz << 4) <= maxZ() && (cz << 4) + 15 >= minZ();
    }

    public int minX() {
        return x + bound("min", 0);
    }

    public int minZ() {
        return z + bound("min", 2);
    }

    public int maxX() {
        return x + bound("max", 0);
    }

    public int maxZ() {
        return z + bound("max", 2);
    }

    private int bound(String side, int axis) {
        // The native canopy/encounter bound is independent of the fixed preview capture's extent.
        if ("tree-overlay".equals(layout)) return "min".equals(side) ? axis == 0 ? 0 : -8 : axis == 0 ? 320 : 312;
        com.google.gson.JsonObject descriptor = RemasterCatalog.descriptor(prefab, variant);
        int authored = descriptor.getAsJsonArray(side)
            .get(axis)
            .getAsInt();
        if (!"natural-prefab".equals(layout)) return authored;
        int entry = descriptor.getAsJsonObject("placement")
            .getAsJsonObject("futurePlacement")
            .getAsJsonArray("entrance")
            .get(axis)
            .getAsInt();
        // The small natural apron participates in owner indexing and placement collision checks.
        return "min".equals(side) ? Math.min(authored, entry - 2) : Math.max(authored, entry + 2);
    }

    public boolean overlaps(int x0, int z0, int x1, int z1, int margin) {
        return minX() - margin <= x1 && maxX() + margin >= x0 && minZ() - margin <= z1 && maxZ() + margin >= z0;
    }

    public int entryX() {
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("x") ? x + entry.get("x")
            .getAsInt() : x + authoredMin(0) + 4;
    }

    public int entryZ() {
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("z") ? z + entry.get("z")
            .getAsInt() : z + authoredMin(2) + 4;
    }

    private int authoredMin(int axis) {
        return "tree-overlay".equals(layout) ? bound("min", axis)
            : RemasterCatalog.descriptor(prefab, variant)
                .getAsJsonArray("min")
                .get(axis)
                .getAsInt();
    }

    public int entryY() {
        com.google.gson.JsonObject entry = entrance();
        return entry != null && entry.has("topY") ? y + entry.get("topY")
            .getAsInt() + 1 : y + 1;
    }

    private com.google.gson.JsonObject entrance() {
        return RemasterCatalog.entrance(prefab, variant);
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
            n.getString("layout"));
    }
}
