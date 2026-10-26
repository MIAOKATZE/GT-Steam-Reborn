import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.air.ProsperityAirLookup;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.ProsperityBiomes;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

import gregtech.api.enums.Materials;

/**
 * Real registration, fine biome plane and production air lookup; no terrain or save writes.
 * Uses distinct real Materials instances only as identity markers. Actual fluid registration and
 * machine ticks are checked by ProsperityAirAudit in the running game.
 * Run each seed/scenario in a fresh JVM, as in BiomeAllocationCheck.
 */
public final class ProsperityAirBiomeCheck {

    private static final Map<BiomeId, Materials> FEEDS = new EnumMap<>(BiomeId.class);

    public static void main(String[] args) throws Exception {
        long seed = args.length == 0 ? 1397247059L : Long.parseLong(args[0]);
        boolean reproduce = args.length > 1 && "reproduce".equals(args[1]);
        boolean missingRivers = args.length > 1 && "missing-rivers".equals(args[1]);
        boolean focused = java.util.Arrays.asList(args).contains("focused");
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        if (missingRivers) for (int id = 184; id <= 254; id++) {
            if (BiomeGenBase.getBiomeGenArray()[id] == null) new BiomeGenBase(id) {};
        }
        GTSRDimensionDef def = new GTSRDimensionDef(GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
            "Air biome regression", 0x50524F53L, 78, 100, () -> true,
            WorldProviderProsperityRuins.class, ChunkProviderProsperityRuins::new);
        ProsperityBiomes.init(def);
        materials();
        GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        if (missingRivers) require(authority.biomeOf(BiomeId.SANZU_RIVER) == null
            && authority.biomeOf(BiomeId.WITHERED_RIVERBED) == null, "river biomes must really be unallocated");
        GTSRWorldChunkManager manager = new GTSRWorldChunkManager(seed, def);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, manager::biomeAt);
        TestWorld world = world(seed, manager);
        int mismatch = 0, intrusion = 0, missing = 0, split = 0, samples = 0;
        int positive = 0, negative = 0;
        Map<BiomeId, Integer> coverage = new EnumMap<>(BiomeId.class);
        // Pin deterministic windows across both signs; inspect only chunks near a real fine/coarse boundary.
        for (int cz = -96; cz < 96; cz++) for (int cx = -96; cx < 96; cx++) {
            // Frozen-jar verification keeps the captured failure chunks plus a sparse sign-spanning grid.
            if (focused && !(cx == -71 && cz == -96 || cx == -49 && cz == -96 || cx % 16 == 0 && cz % 16 == 0)) continue;
            BiomeId coarse = authority.of(manager.biomeAt(cx, cz)).biomeId;
            BiomeGenBase[] plane = manager.loadBlockGeneratorData(null, cx << 4, cz << 4, 16, 16);
            boolean differs = false;
            for (BiomeGenBase biome : plane) if (authority.of(biome).biomeId != coarse) differs = true;
            if (!differs) continue;
            BiomeId first = null;
            boolean multi = false;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                BiomeId expected = authority.of(plane[lx + lz * 16]).biomeId;
                if (expected != null && GTSRVoronoiRiverField.isDryRiverColumn(seed, x, z)
                    && authority.biomeOf(BiomeId.WITHERED_RIVERBED) != null) expected = BiomeId.WITHERED_RIVERBED;
                if (expected != null) {
                    if (!coverage.containsKey(expected)) System.out.println("SAMPLE " + expected + "=" + x + "," + z);
                    coverage.merge(expected, 1, Integer::sum);
                }
                if (first == null) first = expected;
                else if (first != expected) multi = true;
                Materials actual = ProsperityAirLookup.of(world, x, z);
                samples++;
                if (x >= 0 && z >= 0) positive++;
                if (x < 0 || z < 0) negative++;
                if (actual == FEEDS.get(expected)) continue;
                mismatch++;
                if (actual == FEEDS.get(BiomeId.BRASS_WASTES)) intrusion++;
                if (expected == BiomeId.BRASS_WASTES) missing++;
                if (mismatch <= 6) System.out.println("MISMATCH seed=" + seed + " x=" + x + " z=" + z
                    + " coarse=" + coarse + " fine=" + expected + " actual=" + name(actual));
            }
            if (multi) split++;
        }
        System.out.println("RESULT seed=" + seed + " samples=" + samples + " mismatch=" + mismatch
            + " brassIntrusion=" + intrusion + " brassMissing=" + missing + " splitChunks=" + split
            + " positive=" + positive + " negative=" + negative);
        System.out.println("BIOME_COVERAGE " + coverage);
        if (reproduce) {
            require(mismatch > 0 && intrusion > 0 && missing > 0 && split > 0 && positive > 0 && negative > 0,
                "must reproduce boundary pollution and loss at both coordinate signs");
        } else {
            require(samples > 0 && split > 0 && positive > 0 && negative > 0, "boundary sample must cover both signs");
            if (!focused) require(coverage.size() == (missingRivers ? 4 : 6), "all allocated feeds must be covered");
            require(mismatch == 0, "production air must match actual block biome plane");
        }
        // Rebinding a second manager must never change queries in the first world.
        int crossWorld = 0;
        GTSRWorldChunkManager other = new GTSRWorldChunkManager(seed ^ 0x12345678L, def);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, other::biomeAt);
        for (int z = -1024; z <= 1024; z += 127) for (int x = -1024; x <= 1024; x += 127) {
            BiomeId expected = authority.of(manager.getBiomeGenAt(x, z)).biomeId;
            if (expected != null && authority.biomeOf(BiomeId.WITHERED_RIVERBED) != null
                && GTSRVoronoiRiverField.isDryRiverColumn(seed, x, z)) expected = BiomeId.WITHERED_RIVERBED;
            if (ProsperityAirLookup.of(world, x, z) != FEEDS.get(expected)) crossWorld++;
        }
        System.out.println("WORLD_SCOPE seed=" + seed + " mismatch=" + crossWorld);
        if (!reproduce) require(crossWorld == 0, "another world's manager must not change local air");
        require(ProsperityAirLookup.of(null, 0, 0) == null, "null world fallback");
        if (missingRivers) missingRiverColumns(world, manager);
        if (!reproduce) fallbacks(world, manager);
        System.out.println("PASS ProsperityAirBiomeCheck seed=" + seed + " reproduce=" + reproduce);
    }

    private static void missingRiverColumns(TestWorld world, GTSRWorldChunkManager manager) {
        boolean lake = false, dry = false;
        long sequence = world.getSeed();
        GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimension(78);
        for (int n = 0; n < 32768 && !(lake && dry); n++) {
            sequence = sequence * 6364136223846793005L + 1442695040888963407L;
            int x = (int) (sequence >>> 32) % 32768;
            sequence = sequence * 6364136223846793005L + 1442695040888963407L;
            int z = (int) (sequence >>> 32) % 32768;
            boolean isLake = GTSRVoronoiRiverField.isSanzuColumn(world.getSeed(), x, z);
            boolean isDry = GTSRVoronoiRiverField.isDryRiverColumn(world.getSeed(), x, z);
            if (!isLake && !isDry) continue;
            BiomeId expected = authority.of(manager.getBiomeGenAt(x, z)).biomeId;
            require(expected != null && expected != BiomeId.SANZU_RIVER && expected != BiomeId.WITHERED_RIVERBED,
                "unallocated river biome must not appear in block plane");
            require(ProsperityAirLookup.of(world, x, z) == FEEDS.get(expected), "missing river allocation must keep base air");
            lake |= isLake;
            dry |= isDry;
        }
        require(lake && dry, "must cover actual lake and dry river columns while allocation is missing");
        System.out.println("PASS missingRiverColumns lake=true dry=true baseAir=true");
    }

    private static void fallbacks(TestWorld world, GTSRWorldChunkManager manager) throws Exception {
        WorldProvider provider = world.provider;
        provider.worldChunkMgr = null;
        require(ProsperityAirLookup.of(world, -1, -1) == null, "missing manager fallback");
        provider.worldChunkMgr = new WorldChunkManager() {
            @Override public BiomeGenBase getBiomeGenAt(int x, int z) { return BiomeGenBase.plains; }
        };
        require(ProsperityAirLookup.of(world, -1, -1) == null, "foreign biome fallback");
        // A high ID whose low byte aliases an allocated biome must remain unknown by instance.
        BiomeGenBase alias = (BiomeGenBase) SurfaceHarness.unsafe().allocateInstance(BiomeGenBase.plains.getClass());
        Field biomeId = BiomeGenBase.class.getDeclaredField("biomeID");
        SurfaceHarness.unsafe().putInt(alias, SurfaceHarness.unsafe().objectFieldOffset(biomeId), 180 + 256);
        provider.worldChunkMgr = new WorldChunkManager() {
            @Override public BiomeGenBase getBiomeGenAt(int x, int z) { return alias; }
        };
        require(ProsperityAirLookup.of(world, -1, -1) == null, "high-ID byte alias must not become a local biome");
        provider.worldChunkMgr = manager;
        for (int dimension : new int[] { -1, 0, 79, 12345 }) {
            provider.dimensionId = dimension;
            require(ProsperityAirLookup.of(world, -1, -1) == null, "other dimension fallback " + dimension);
        }
        provider.dimensionId = 78;
        provider.worldChunkMgr = new GTSRWorldChunkManager(world.getSeed(), null);
        require(ProsperityAirLookup.of(world, -1, -1) == null, "empty table fallback");
        provider.worldChunkMgr = manager;
        System.out.println("PASS fallbacks nullWorld/nullManager/foreignBiome/highIdAlias/otherDimension/emptyTable");
    }

    private static void materials() throws Exception {
        String[] fields = { "WastesSigh", "ThickGrease", "MetalGrit", "UmbralMire", "SanzuResidualSteam", "WitheredBreath" };
        BiomeId[] keys = { BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST, BiomeId.BRASS_WASTES,
            BiomeId.FUMAROLE_SWAMP, BiomeId.SANZU_RIVER, BiomeId.WITHERED_RIVERBED };
        for (int i = 0; i < fields.length; i++) {
            Materials marker = (Materials) SurfaceHarness.unsafe().allocateInstance(Materials.class);
            GTSRProsperityAirMaterials.class.getField(fields[i]).set(null, marker);
            FEEDS.put(keys[i], marker);
        }
    }

    private static String name(Materials material) {
        for (Map.Entry<BiomeId, Materials> entry : FEEDS.entrySet()) if (entry.getValue() == material) return entry.getKey().name();
        return "null";
    }

    private static TestWorld world(long seed, GTSRWorldChunkManager manager) throws Exception {
        TestWorld world = (TestWorld) SurfaceHarness.unsafe().allocateInstance(TestWorld.class);
        TestProvider provider = new TestProvider();
        provider.seed = seed;
        provider.dimensionId = 78;
        provider.worldChunkMgr = manager;
        provider.worldObj = world;
        Field field = World.class.getDeclaredField("provider");
        SurfaceHarness.unsafe().putObject(world, SurfaceHarness.unsafe().objectFieldOffset(field), provider);
        return world;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class TestProvider extends WorldProvider {
        long seed;
        @Override public String getDimensionName() { return "air-biome-check"; }
        @Override public long getSeed() { return seed; }
    }

    private static final class TestWorld extends World {
        private TestWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }
}
