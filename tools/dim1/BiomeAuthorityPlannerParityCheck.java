import java.lang.reflect.Method;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.StructureBurialTiers;
import com.miaokatze.gtsr.config.Config;

/** Same clean-server planner fingerprint on R1 and candidate; candidate also survives client binding. */
public final class BiomeAuthorityPlannerParityCheck {
    private static long hash;
    private static void append(int value) { hash = hash * 31 + value; }
    private static void append(PlacementGate.Intent intent) {
        if (intent == null) { append(-1); return; }
        append(intent.templateName.hashCode()); append(intent.originX); append(intent.originZ); append(intent.sizeX); append(intent.sizeZ);
    }
    private static Object seeded(Class<?> owner, String name, Class<?>[] tailTypes, long seed, Object... tail) throws Exception {
        Class<?>[] types = new Class<?>[tailTypes.length + 1]; types[0] = long.class;
        System.arraycopy(tailTypes, 0, types, 1, tailTypes.length);
        try {
            Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true);
            Object[] args = new Object[tail.length + 1]; args[0] = seed; System.arraycopy(tail, 0, args, 1, tail.length);
            return method.invoke(null, args);
        } catch (NoSuchMethodException old) {
            Method method = owner.getDeclaredMethod(name, tailTypes); method.setAccessible(true);
            return method.invoke(null, tail);
        }
    }
    private static long fingerprint(long seed) throws Exception {
        hash = 1125899906842597L;
        for (int i = 0; i < 4096; i++) {
            int cx = i * 7 - 14336, cz = i * 11 - 22528;
            int x = (cx << 4) + i % 16, z = (cz << 4) + (i * 3) % 16;
            append(RuinedMachinePlacer.intentAt(seed, cx, cz));
            append(RuinPlacer.intentAt(seed, cx, cz));
            append((Integer) seeded(StructureBurialTiers.class, "rosterIndexAtOrigin", new Class<?>[] { int.class, int.class }, seed, x, z));
            Object shape = RuinedMachineShapes.ALL[i % RuinedMachineShapes.ALL.length];
            append((Integer) seeded(RuinedMachinePlacer.class, "buryingAt", new Class<?>[] { shape.getClass(), int.class, int.class }, seed, shape, x, z));
            Object outpost = ProsperityOutpostPlacer.ALL[i % ProsperityOutpostPlacer.ALL.length];
            append((Integer) seeded(ProsperityOutpostPlacer.class, "buryingAt", new Class<?>[] { outpost.getClass(), int.class, int.class }, seed, outpost, x, z));
            Object ruin = RuinShapes.ALL[i % RuinShapes.ALL.length];
            append((Integer) seeded(RuinPlacer.class, "buryingAt", new Class<?>[] { ruin.getClass(), int.class, int.class }, seed, ruin, x, z));
        }
        return hash;
    }
    public static void main(String[] args) throws Exception {
        Config.prosperityRuinChance = 7; Config.prosperityMachineChance = 11;
        Config.prosperityRuinsEnabled = true; Config.prosperityRuinMicroModulation = true;
        Method fixture = BiomeAuthorityWorldScopeCheck.class.getDeclaredMethod("def", String.class, int.class); fixture.setAccessible(true);
        GTSRDimensionDef def = (GTSRDimensionDef) fixture.invoke(null, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78);
        for (long seed : new long[] { 19L, -88372631L }) {
            new GTSRWorldChunkManager(seed, def);
            long expected = fingerprint(seed);
            System.out.println("PLANNER_FINGERPRINT seed=" + seed + " hash=" + expected);
            if (args.length > 0 && args[0].equals("patched")) {
                new GTSRWorldChunkManager(0L, def);
                if (fingerprint(seed) != expected) throw new AssertionError("client manager changed pure planner outputs");
            }
        }
        System.out.println("PASS BiomeAuthorityPlannerParityCheck: 8192 clean planner points; intents, anchor identity and three burial families");
    }
}
