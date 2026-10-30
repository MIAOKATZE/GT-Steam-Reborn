import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;

/** Native vanilla World/GenLayer/IntCache and production authority/manager; no Forge startup. */
public final class BiomeAuthorityWorldScopeCheck {
    private static final long SALT = 0x50524F53L;
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static GTSRDimensionDef def(String key, int dim) throws Exception {
        GTSRDimensionDef def = new GTSRDimensionDef(key, key, SALT, dim, dim, () -> true, null, null);
        BiomeGenBase[] biomes = { BiomeGenBase.plains, BiomeGenBase.desert, BiomeGenBase.forest, BiomeGenBase.swampland };
        int i = 0;
        for (GTSRBiomeAuthority.BiomeId id : GTSRBiomeAuthority.BiomeId.values()) {
            if (key.equals(id.dimKey()) && id.inSelector()) {
                BiomeGenBase biome = biomes[i++];
                GTSRBiomeAuthority.recordAllocation(id, biome.biomeID, biome.biomeID, biome);
                def.addBiome(biome, 1);
            }
        }
        Method registered = GTSRDimensionDef.class.getDeclaredMethod("markRegistered", int.class, int.class);
        registered.setAccessible(true);
        registered.invoke(def, dim, dim);
        return def;
    }

    private static World world(GTSRWorldChunkManager manager, int dim, boolean client) throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        World world = (World) unsafe.allocateInstance(WorldServer.class);
        Field provider = World.class.getField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        world.provider.dimensionId = dim;
        world.provider.worldChunkMgr = manager;
        world.isRemote = client;
        return world;
    }

    private static GTSRBiomeAuthority authority(World world) throws Exception {
        // Identical regression runs on R1: its API is dimension-only. Patched path is exact-world.
        try {
            return (GTSRBiomeAuthority) GTSRBiomeAuthority.class.getMethod("forWorld", World.class).invoke(null, world);
        } catch (NoSuchMethodException old) {
            return GTSRBiomeAuthority.forDimension(world.provider.dimensionId);
        }
    }

    private static long fingerprint(GTSRWorldChunkManager manager) {
        long hash = 1125899906842597L;
        for (int z = -4096; z <= 4096; z += 251) for (int x = -4096; x <= 4096; x += 239) {
            hash = hash * 31 + manager.biomeAt(x >> 4, z >> 4).biomeID;
            hash = hash * 31 + manager.getBiomeGenAt(x, z).biomeID;
            hash = hash * 31 + Float.floatToIntBits(manager.microStrengthAt(x >> 4, z >> 4));
        }
        return hash;
    }

    public static void main(String[] args) throws Exception {
        GTSRDimensionDef def = def(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79);
        GTSRWorldChunkManager server = new GTSRWorldChunkManager(19L, def);
        World serverWorld = world(server, 79, false);
        long baselineHash = fingerprint(server);
        System.out.println("SERVER_ONLY_FINGERPRINT=" + baselineHash);
        int[] expected = new int[1024];
        for (int i = 0; i < expected.length; i++) expected[i] = server.biomeAt(i * 3 - 1536, i * 7 - 3584).biomeID;
        GTSRBiomeAuthority serverAuthority = authority(serverWorld);
        GTSRWorldChunkManager client = new GTSRWorldChunkManager(0L, def);
        World clientWorld = world(client, 79, true);
        int disagreements = 0;
        for (int i = 0; i < expected.length; i++) {
            int x = ((i * 3 - 1536) << 4) + 8, z = ((i * 7 - 3584) << 4) + 8;
            if (authority(serverWorld).ordinalAt(x, z).actualId != expected[i]) disagreements++;
        }
        check(disagreements == 0, "Client manager construction replaced server authority at " + disagreements + "/1024 coordinates");
        check(serverAuthority == authority(serverWorld), "server authority identity changed");
        check(authority(clientWorld) != serverAuthority, "client and server share spatial authority");
        check(baselineHash == fingerprint(server), "server layout changed after client construction");
        for (int i = 0; i < 256; i++) {
            int x = i * 239 - 4096, z = i * 251 - 4096;
            check(authority(clientWorld).ordinalAt(x, z).biome == client.biomeAt(x >> 4, z >> 4), "client fog source unavailable");
            check(serverAuthority.ordinalAt(x, z).biome == server.biomeAt(x >> 4, z >> 4), "spawn biome mismatch");
        }
        World replacement = world(new GTSRWorldChunkManager(73L, def), 79, false);
        check(authority(replacement) != serverAuthority, "same dimension new save reused old source");
        int different = 0;
        for (int i = 0; i < 256; i++) {
            if (authority(replacement).ordinalAt(i * 128, -i * 128).biome != serverAuthority.ordinalAt(i * 128, -i * 128).biome) different++;
        }
        check(different > 0, "different-seed replacement world has no distinct biomes");
        WorldChunkManager saved = serverWorld.provider.worldChunkMgr;
        serverWorld.provider.worldChunkMgr = null;
        check(!authority(serverWorld).isBound(), "missing manager fell back to stale global source");
        serverWorld.provider.worldChunkMgr = saved;
        check(authority(serverWorld) == serverAuthority, "manager restoration changed authority");
        check(!GTSRBiomeAuthority.forDimension(79).isBound(), "registry retains a manager source");

        Method seeded = GTSRBiomeAuthority.class.getMethod("ordinalAtSeed", long.class, long.class, int.class, int.class);
        Method micro = GTSRBiomeAuthority.class.getMethod("microStrengthAtSeed", long.class, long.class, int.class, int.class);
        for (int i = 0; i < 512; i++) {
            int x = i * 239 - 61440, z = i * 251 - 65536;
            GTSRBiomeAuthority.Resolution resolution = (GTSRBiomeAuthority.Resolution) seeded.invoke(
                GTSRBiomeAuthority.forDimension(79), 19L, SALT, x, z);
            check(resolution.biome == server.biomeAt(x >> 4, z >> 4), "explicit seed changed chunk-center identity");
            float strength = (Float) micro.invoke(GTSRBiomeAuthority.forDimension(79), 19L, SALT, x, z);
            check(strength == server.microStrengthAt(x >> 4, z >> 4), "explicit seed changed micro strength");
        }
        Field caches = GTSRBiomeAuthority.class.getDeclaredField("seedChains");
        caches.setAccessible(true);
        java.util.List<?> entries = (java.util.List<?>) caches.get(GTSRBiomeAuthority.forDimension(79));
        Object reused = entries.get(entries.size() - 1);
        seeded.invoke(GTSRBiomeAuthority.forDimension(79), 19L, SALT, 27, -39);
        check(entries.get(entries.size() - 1) == reused, "same seed rebuilt planner chain");
        for (long seed = 0; seed < 20; seed++) seeded.invoke(GTSRBiomeAuthority.forDimension(79), seed, SALT, 27, -39);
        check(entries.size() == 4, "seed-only planner cache is not bounded");
        // An isolated historical-tool binding is deliberately poisonous, but runtime routing ignores it.
        GTSRBiomeAuthority.bind(def.getKey(), 79, (x, z) -> BiomeGenBase.ocean);
        check(authority(serverWorld) == serverAuthority, "legacy fixture binding escaped into runtime");
        check(authority(clientWorld).ordinalAt(8, 8).biome == client.biomeAt(0, 0), "legacy binding replaced client fog source");
        GTSRGenLayerChain first = new GTSRGenLayerChain(19L, new int[] { 1, 2, 4, 6 });
        GTSRGenLayerChain second = new GTSRGenLayerChain(0L, new int[] { 1, 2, 4, 6 });
        int[] owned = first.coarseInts(-99, 81, 17, 13), snapshot = owned.clone();
        for (int i = 0; i < 100; i++) second.coarseInts(i * 31, -i * 43, 19, 17);
        check(Arrays.equals(owned, snapshot), "another chain overwrote returned window");
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Thread[] readers = new Thread[2];
        for (int index = 0; index < 2; index++) {
            final GTSRWorldChunkManager manager = index == 0 ? server : client;
            final long expectedHash = fingerprint(manager);
            readers[index] = new Thread(() -> {
                try {
                    start.await();
                    for (int run = 0; run < 8; run++) if (fingerprint(manager) != expectedHash)
                        throw new AssertionError("concurrent world sampling changed output");
                } catch (Throwable error) { failed.compareAndSet(null, error); }
            });
            readers[index].start();
        }
        start.countDown();
        for (Thread reader : readers) reader.join();
        if (failed.get() != null) throw new AssertionError("concurrent native chains failed", failed.get());
        check(true, "concurrent sampling");
        GTSRDimensionDef emptyDef = new GTSRDimensionDef(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, "empty", SALT, 78, 78, () -> true, null, null);
        GTSRWorldChunkManager empty = new GTSRWorldChunkManager(19L, emptyDef);
        check(empty.biomeAt(0, 0) == null, "empty manager invented identity");
        check(!((GTSRBiomeAuthority.Resolution) seeded.invoke(GTSRBiomeAuthority.forDimKey(emptyDef.getKey()), 19L, SALT, 8, 8)).resolved(), "empty seeded query invented identity");
        check((Float) micro.invoke(GTSRBiomeAuthority.forDimKey(emptyDef.getKey()), 19L, SALT, 8, 8) == 1.0F, "empty selector micro not neutral");
        GTSRBiomeAuthority.recordAllocation(GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE, 1, 1, BiomeGenBase.plains);
        emptyDef.addBiome(BiomeGenBase.plains, 1);
        GTSRWorldChunkManager shortManager = new GTSRWorldChunkManager(19L, emptyDef);
        check(shortManager.degraded() == GTSRBiomeAuthority.Degraded.SHORT, "short ledger lost degraded state");
        check(((GTSRBiomeAuthority.Resolution) seeded.invoke(GTSRBiomeAuthority.forDimKey(emptyDef.getKey()), 19L, SALT, -47, 99)).biome == shortManager.biomeAt(-3, 6), "short selector seed query drifted");
        System.out.println("PASS BiomeAuthorityWorldScopeCheck: " + checks + " checks; two logical sides, replacement save, isolated GenLayer concurrency");
    }
}
