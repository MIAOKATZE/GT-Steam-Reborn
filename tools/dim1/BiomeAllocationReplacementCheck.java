import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;

/** Native biome instances and production ledger. Each JVM starts with an empty allocation ledger. */
public final class BiomeAllocationReplacementCheck {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void absent(GTSRBiomeAuthority authority, BiomeGenBase biome, String operation) {
        check(!authority.of(biome).resolved(), operation + " still resolves superseded instance");
        check(GTSRBiomeAuthority.identityOf(biome) == null, operation + " retained cross-dimension identity");
        check(GTSRBiomeAuthority.dimKeyOf(biome) == null, operation + " retained dimension identity");
    }

    public static void main(String[] args) throws Exception {
        final BiomeId key = BiomeId.ASHEN_PRAIRIE;
        final GTSRBiomeAuthority ledger = GTSRBiomeAuthority.forDimKey(key.dimKey());
        GTSRBiomeAuthority.recordAllocation(key, 1, 1, BiomeGenBase.plains);
        final GTSRBiomeAuthority oldSource = GTSRBiomeAuthority.scoped(key.dimKey(), 79, (x, z) -> BiomeGenBase.plains);
        check(oldSource.ordinalAt(0, 0).biomeId == key, "initial scoped identity missing");
        GTSRBiomeAuthority.recordAllocation(key, 1, 2, BiomeGenBase.desert);
        absent(ledger, BiomeGenBase.plains, "replacement");
        check(ledger.of(BiomeGenBase.desert).actualId == 2, "replacement actual ID missing");
        check(ledger.of(BiomeGenBase.desert).biomeId == key, "replacement identity missing");
        check(!oldSource.ordinalAt(0, 0).resolved(), "old scoped source falsely remains allocated");
        check(oldSource.nearestBiomeChunk(key, 0, 0, 2, 100).biome == null, "nearest lookup found retired instance");
        check(ledger.ordinalAtSeed(19L, 0x50524F53L, 8, 8).biome == BiomeGenBase.desert,
            "explicit seed did not adopt replacement selector");
        GTSRBiomeAuthority.recordAllocation(key, 1, 2, BiomeGenBase.desert);
        check(ledger.of(BiomeGenBase.desert).biomeId == key, "idempotent same-instance replacement lost identity");
        GTSRBiomeAuthority.recordNoSlot(key, 1, "fixture");
        absent(ledger, BiomeGenBase.desert, "withdrawal");
        check(ledger.allocatedCount() == 0, "withdrawal still allocated");
        check(ledger.degraded() == GTSRBiomeAuthority.Degraded.EMPTY, "withdrawal did not degrade EMPTY");
        check(!ledger.ordinalAtSeed(19L, 0x50524F53L, 8, 8).resolved(), "seed cache resurrected withdrawn selector");
        check(ledger.microStrengthAtSeed(19L, 0x50524F53L, 8, 8) == 1.0F, "empty selector micro not neutral");
        BiomeGenBase[] replacements = { BiomeGenBase.plains, BiomeGenBase.desert, BiomeGenBase.forest,
            BiomeGenBase.swampland, BiomeGenBase.extremeHills };
        Field reverse = GTSRBiomeAuthority.class.getDeclaredField("byInstance");
        reverse.setAccessible(true);
        for (int run = 0; run < 100; run++) {
            final BiomeGenBase biome = replacements[run % replacements.length];
            GTSRBiomeAuthority.recordAllocation(key, 1, biome.biomeID, biome);
            check(((Map<?, ?>) reverse.get(ledger)).size() == 1, "retired instances accumulated in reverse index");
            check(ledger.of(biome).biomeId == key, "current replacement not indexed");
        }
        GTSRBiomeAuthority.recordNoSlot(key, 1, "fixture");
        check(((Map<?, ?>) reverse.get(ledger)).isEmpty(), "withdrawal retained reverse-index entries");
        System.out.println("PASS BiomeAllocationReplacementCheck: " + checks + " native allocation lifecycle checks");
    }
}
