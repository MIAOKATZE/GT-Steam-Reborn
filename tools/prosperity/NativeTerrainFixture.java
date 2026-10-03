import net.minecraft.world.biome.BiomeGenBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Degraded;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.*;

/** Initialize before any Planner/height read; never initialize after a degraded terrain cache was used. */
public final class NativeTerrainFixture {
    private static boolean initialized;
    private static final int[] IDS = {183, 194, 195, 197, 198, 199};
    private static final BiomeId[] KEYS = {BiomeId.RUSTED_STEPPE, BiomeId.GEARWORK_FOREST,
        BiomeId.BRASS_WASTES, BiomeId.FUMAROLE_SWAMP, BiomeId.SANZU_RIVER, BiomeId.WITHERED_RIVERBED};
    private NativeTerrainFixture() {}

    public static synchronized void initialize() {
        if (!initialized) {
            GTSRBiomeAuthority authority = authority();
            if (authority.allocatedCount() != 0) throw new AssertionError("native fixture requires fresh empty ledger");
            BiomeGenBase[] biomes = {new BiomeRustedSteppe(IDS[0]), new BiomeGearworkForest(IDS[1]),
                new BiomeBrassWastes(IDS[2]), new BiomeFumaroleSwamp(IDS[3]),
                new BiomeSanzuRiver(IDS[4]), new BiomeWitheredRiverbed(IDS[5])};
            for (int i = 0; i < KEYS.length; i++)
                GTSRBiomeAuthority.recordAllocation(KEYS[i], 180 + i, IDS[i], biomes[i]);
            assertNormal();
            initialized = true;
            System.out.println("NATIVE_NORMAL_6 degraded=NONE actual=183,194,195,197,198,199 rosterAtOrigin="
                + ProsperityTerrainProfile.chainRosterIndexAt(20261001L, 0, 0)
                + " heightAtOrigin=" + ProsperityTerrainProfile.heightAt(20261001L, 0, 0));
        } else assertNormal();
    }

    private static GTSRBiomeAuthority authority() {
        return GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
    }

    public static void assertNormal() {
        GTSRBiomeAuthority authority = authority();
        if (authority.degraded() != Degraded.NONE || authority.rosterSize() != 6 || authority.allocatedCount() != 6)
            throw new AssertionError("native normal roster required: " + authority.degraded() + " allocated=" + authority.allocatedCount());
        for (int i = 0; i < KEYS.length; i++) {
            BiomeGenBase biome = authority.biomeOf(KEYS[i]);
            if (biome == null || biome.biomeID != IDS[i] || authority.actualIdOf(KEYS[i]) != IDS[i]
                || GTSRBiomeAuthority.identityOf(biome) != KEYS[i])
                throw new AssertionError("native slot identity mismatch: " + KEYS[i]);
        }
        int roster = ProsperityTerrainProfile.chainRosterIndexAt(20261001L, 0, 0);
        if (roster < 0 || roster >= 4) throw new AssertionError("native selector roster missing: " + roster);
    }
}
