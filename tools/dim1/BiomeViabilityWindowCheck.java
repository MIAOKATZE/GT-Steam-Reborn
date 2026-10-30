import java.util.Arrays;
import java.util.Collections;

import net.minecraft.world.biome.BiomeGenBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;

/** Production manager/layers and vanilla IntCache: pooled spare capacity is not part of a biome query. */
public final class BiomeViabilityWindowCheck {
    public static void main(String[] args) {
        GTSRDimensionDef def = new GTSRDimensionDef(null, "fixture", 0, 78, 78, () -> true, null, null);
        def.addBiome(BiomeGenBase.plains, 1);
        def.addBiome(BiomeGenBase.desert, 1);
        GTSRWorldChunkManager manager = new GTSRWorldChunkManager(19, def);
        GTSRGenLayerChain pollution = new GTSRGenLayerChain(79, new int[] { 1, 2 });
        int assertions = 0, failures = 0;
        for (int z = -2048; z <= 2048; z += 256) for (int x = -2048; x <= 2048; x += 256) {
            BiomeGenBase wanted = manager.getBiomesForGeneration(null, x >> 2, z >> 2, 1, 1)[0];
            pollution.coarseInts(117, -319, 40, 33);
            assertions++;
            if (!manager.areBiomesViable(x, z, 0, Collections.singletonList(wanted))) failures++;
            assertions++;
            if (manager.areBiomesViable(x, z, 0, Collections.singletonList(
                wanted == BiomeGenBase.plains ? BiomeGenBase.desert : BiomeGenBase.plains))) failures++;
        }
        for (int radius : new int[] { 1, 7, 31, 64 }) {
            assertions++;
            if (!manager.areBiomesViable(-73, 129, radius, Arrays.asList(BiomeGenBase.plains, BiomeGenBase.desert)))
                failures++;
        }
        if (failures != 0) throw new AssertionError(failures + "/" + assertions + " biome queries included stale pooled cells");
        System.out.println("PASS BiomeViabilityWindowCheck: " + assertions + " assertions; real GenLayer/IntCache, no game startup");
    }
}
