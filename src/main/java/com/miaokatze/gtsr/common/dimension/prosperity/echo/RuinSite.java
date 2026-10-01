package com.miaokatze.gtsr.common.dimension.prosperity.echo;

/** Immutable deterministic anchor. Size is measured in complete chunk footprints. */
public final class RuinSite {

    public final int kind, x, z, y, width, depth, biome, layout;
    public final long seed;

    public RuinSite(long seed, int kind, int x, int y, int z, int biome) {
        this(seed, kind, x, y, z, biome, 2);
    }

    public RuinSite(long seed, int kind, int x, int y, int z, int biome, int layout) {
        this.layout = layout;
        this.seed = seed;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.biome = biome;
        width = widthFor(kind, layout);
        depth = depthFor(kind, layout);
    }

    public static RuinSite legacy(long seed, int kind, int x, int y, int z, int biome) {
        return new RuinSite(seed, kind, x, y, z, biome, 1);
    }

    public static int widthFor(int kind, int layout) {
        return layout < 2 ? (kind < 2 ? 96 : kind < 7 ? 48 : 32) : (kind < 2 ? 288 : kind < 7 ? 96 : 32);
    }

    public static int depthFor(int kind, int layout) {
        return layout < 2 ? (kind < 2 ? 96 : kind < 7 ? 48 : 16) : (kind < 2 ? 192 : kind < 7 ? 72 : 32);
    }

    public int entryX() {
        return x + width / 2;
    }

    public int entryZ() {
        return z + 2;
    }

    public int entryY() {
        return layout < 2 ? y + 1 : y + RuinTerrainLayout.level(this, width / 2, 2) + 1;
    }

    public String id() {
        return "echo:" + seed + ":" + kind + ":" + x + ":" + z + (layout < 2 ? "" : ":v2");
    }

    public boolean intersects(int cx, int cz) {
        return (cx << 4) < x + width && (cx << 4) + 15 >= x && (cz << 4) < z + depth && (cz << 4) + 15 >= z;
    }

    public boolean overlaps(RuinSite other, int margin) {
        return x - margin < other.x + other.width && x + width + margin > other.x
            && z - margin < other.z + other.depth
            && z + depth + margin > other.z;
    }

    public static final String[] NAMES = { "fallen_foundry", "subsided_factory", "boiler_shrine", "weaving_mill",
        "sniper_watch", "mirror_barracks", "resonant_station", "switchhouse", "pumping_house", "workers_lodging",
        "shore_crane", "cold_altar", "trench_infirmary", "ash_crematory", "rift_observatory", "weighbridge",
        "valve_yard", "archivist_kiosk", "sap_collector", "mire_boardwalk", "pilgrim_arch", "signal_bridge",
        "fungus_cellar", "boiler_chapel", "broken_aqueduct", "gear_garden", "expedition_camp" };
}
