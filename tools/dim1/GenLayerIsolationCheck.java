import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerSmooth;
import net.minecraft.world.gen.layer.GenLayerVoronoiZoom;
import net.minecraft.world.gen.layer.GenLayerZoom;
import net.minecraft.world.gen.layer.IntCache;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerSelector;

/** Native algorithm reference and deterministic shared-pool isolation; no Forge launch or MC stubs. */
public final class GenLayerIsolationCheck {
    private static long cells;
    private static int checks;
    private static int directFineWindows;
    private static int paddedFineWindows;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void equal(int[] expected, int[] actual, String context) {
        check(expected.length == actual.length, context + " length");
        cells += expected.length;
        if (!Arrays.equals(expected, actual)) {
            for (int i = 0; i < expected.length; i++) if (expected[i] != actual[i]) {
                throw new AssertionError(context + " cell=" + i + " expected=" + expected[i] + " actual=" + actual[i]);
            }
        }
        checks++;
    }

    private static GenLayer[] reference(long seed, int[] ids, int zoom) {
        GenLayer root = new GTSRGenLayerSelector(GTSRGenLayerSelector.SELECTOR_BASE_SEED, ids);
        root = GenLayerZoom.magnify(1000L, root, zoom);
        root = new GenLayerSmooth(1000L, root);
        GenLayer fine = new GenLayerVoronoiZoom(10L, root);
        root.initWorldGenSeed(seed);
        fine.initWorldGenSeed(seed);
        return new GenLayer[] { root, fine };
    }

    private static int[] sample(GenLayer root, int x, int z, int width, int height) {
        IntCache.resetIntCache();
        return Arrays.copyOf(root.getInts(x, z, width, height), width * height);
    }

    private static int[] referenceFine(GenLayer fine, int x, int z, int width, int height) {
        // Native Voronoi underallocates the *filled* intermediate rectangle for some unaligned
        // odd windows. Never treat its stale pooled tail as a worldgen oracle. A multiple-of-four
        // envelope is fully filled; cropping it also proves the documented window-independent map.
        final int paddedWidth = (width + 3) & ~3, paddedHeight = (height + 3) & ~3;
        int[] padded = sample(fine, x, z, paddedWidth, paddedHeight);
        int[] out = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(padded, row * paddedWidth, out, row * width, width);
        }
        if (width + ((x - 2) & 3) <= ((width >> 2) + 1) * 4
            && height + ((z - 2) & 3) <= ((height >> 2) + 1) * 4) {
            equal(out, sample(fine, x, z, width, height), "native direct/envelope");
            directFineWindows++;
        } else {
            paddedFineWindows++;
        }
        return out;
    }

    private static void nativeParity() throws Exception {
        long[] seeds = { 0L, 1L, -1L, 19L, -88372631L, Long.MIN_VALUE, Long.MAX_VALUE };
        int[][] rosters = { { 0 }, { 254 }, { 1, 2, 4, 6 }, { 0, 17, 180, 254 }, { 180, 180, 181, 182 } };
        int[][] windows = {
            { 0, 0, 1, 1 }, { -1, -1, 1, 1 }, { -3, 7, 3, 3 }, { 7, -3, 3, 7 },
            { -17, -15, 17, 13 }, { 31, -43, 29, 23 }, { -100003, 200007, 32, 33 },
            { -30000000, 29999871, 65, 67 }, { 29999001, -29999003, 128, 129 }
        };
        for (long seed : seeds) for (int[] ids : rosters) for (int zoom = 0; zoom <= 8; zoom++) {
            GTSRGenLayerChain chain = new GTSRGenLayerChain(seed, ids, zoom);
            GenLayer[] nativeRoots = reference(seed, ids, zoom);
            for (int[] w : windows) {
                String context = "seed=" + seed + " zoom=" + zoom + " window=" + Arrays.toString(w);
                equal(sample(nativeRoots[0], w[0], w[1], w[2], w[3]),
                    chain.coarseInts(w[0], w[1], w[2], w[3]), "coarse " + context);
                equal(referenceFine(nativeRoots[1], w[0], w[1], w[2], w[3]),
                    chain.fineInts(w[0], w[1], w[2], w[3]), "fine " + context);
                check(sample(nativeRoots[0], w[0] >> 2, w[1] >> 2, 1, 1)[0] == chain.biomeAtCoarse(w[0], w[1]),
                    "coarse point " + context);
                check(sample(nativeRoots[1], w[0], w[1], 1, 1)[0] == chain.biomeAtFine(w[0], w[1]),
                    "fine point " + context);
            }
        }
        // Grow then shrink private scratch; test both large native planes and their retained cap.
        GTSRGenLayerChain chain = new GTSRGenLayerChain(Long.MIN_VALUE, new int[] { 0, 17, 180, 254 }, 8);
        GenLayer[] roots = reference(Long.MIN_VALUE, new int[] { 0, 17, 180, 254 }, 8);
        equal(sample(roots[0], -12031, 32761, 1025, 769), chain.coarseInts(-12031, 32761, 1025, 769), "large coarse");
        equal(referenceFine(roots[1], -12031, 32761, 513, 515), chain.fineInts(-12031, 32761, 513, 515), "large fine");
        equal(sample(roots[0], -19, 21, 3, 5), chain.coarseInts(-19, 21, 3, 5), "shrink coarse");
        equal(referenceFine(roots[1], -19, 21, 3, 5), chain.fineInts(-19, 21, 3, 5), "shrink fine");
        inspectRetained(chain);
    }

    private static void inspectRetained(GTSRGenLayerChain chain) throws Exception {
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        for (String field : new String[] { "coarseRoot", "fineRoot" }) {
            Field root = GTSRGenLayerChain.class.getDeclaredField(field);
            root.setAccessible(true);
            Object layer = root.get(chain);
            while (layer != null && visited.put(layer, Boolean.TRUE) == null) {
                Object next = null;
                for (Class<?> type = layer.getClass(); type != Object.class; type = type.getSuperclass()) {
                    for (Field f : type.getDeclaredFields()) {
                        f.setAccessible(true);
                        if (f.getName().equals("retained")) check(((int[]) f.get(layer)).length <= 4096, "int scratch grew past cap");
                        if (f.getName().equals("retainedJitter")) check(((double[]) f.get(layer)).length <= 8192, "jitter scratch grew past cap");
                        if (f.getName().equals("parent")) next = f.get(layer);
                    }
                }
                layer = next;
            }
        }
    }

    private static void oddWindowCoverage() {
        int[] ids = { 0, 17, 180, 254 };
        GTSRGenLayerChain chain = new GTSRGenLayerChain(19L, ids, 0);
        GenLayer fine = reference(19L, ids, 0)[1];
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            int[] padded = sample(fine, x, z, 4, 4), expected = new int[9];
            for (int row = 0; row < 3; row++) System.arraycopy(padded, row * 4, expected, row * 3, 3);
            // Old facades returned an oversized pooled array. Compare its logical cells here so
            // the baseline failure establishes underfill, not only the older ownership defect.
            equal(expected, Arrays.copyOf(chain.fineInts(x, z, 3, 3), 9), "unaligned odd window x=" + x + " z=" + z);
        }
    }

    private static void borrowedPoolMustSurvive() {
        GTSRGenLayerChain chain = new GTSRGenLayerChain(19L, new int[] { 180, 181, 182, 183 });
        for (int operation = 0; operation < 4; operation++) {
            IntCache.resetIntCache();
            int[][] borrowed = { IntCache.getIntCache(256), IntCache.getIntCache(512), IntCache.getIntCache(1024) };
            for (int[] array : borrowed) Arrays.fill(array, 0x6d6d6d6d);
            final String before = IntCache.getCacheSizes();
            switch (operation) {
                case 0: chain.coarseInts(-31, 43, 29, 23); break;
                case 1: chain.fineInts(-31, 43, 32, 24); break;
                case 2: chain.biomeAtCoarse(-31, 43); break;
                default: chain.biomeAtFine(-31, 43);
            }
            for (int[] array : borrowed) for (int value : array) {
                check(value == 0x6d6d6d6d, "GTSR reclaimed a live external IntCache array (operation=" + operation + ")");
            }
            check(before.equals(IntCache.getCacheSizes()), "GTSR changed the global IntCache ledger");
        }
    }

    private static void ownedResults() {
        GTSRGenLayerChain chain = new GTSRGenLayerChain(19L, new int[] { 180, 181, 182, 183 });
        int[] coarse = chain.coarseInts(-91, 83, 31, 17), fine = chain.fineInts(-91, 83, 31, 17);
        int[] coarseCopy = coarse.clone(), fineCopy = fine.clone();
        for (int n = 0; n < 20; n++) {
            chain.coarseInts(n * 91, -n * 31, 37, 21);
            chain.fineInts(n * 91, -n * 31, 37, 21);
            IntCache.resetIntCache();
            Arrays.fill(IntCache.getIntCache(100000), -1);
        }
        equal(coarseCopy, coarse, "retained coarse output");
        equal(fineCopy, fine, "retained fine output");
        Arrays.fill(coarse, -2);
        Arrays.fill(fine, -3);
        equal(coarseCopy, chain.coarseInts(-91, 83, 31, 17), "caller cannot poison coarse scratch");
        equal(fineCopy, chain.fineInts(-91, 83, 31, 17), "caller cannot poison fine scratch");
    }

    private static void concurrency(boolean vanilla) throws Exception {
        final GTSRGenLayerChain shared = new GTSRGenLayerChain(19L, new int[] { 180, 181, 182, 183 });
        final GTSRGenLayerChain other = new GTSRGenLayerChain(-97L, new int[] { 0, 17, 190, 254 });
        final WorldChunkManager nativeManager = new WorldChunkManager(73L, WorldType.DEFAULT);
        final int[][][] expected = new int[4][32][];
        final BiomeGenBase[][] nativeExpected = new BiomeGenBase[32][];
        for (int i = 0; i < 32; i++) {
            expected[0][i] = shared.coarseInts(i * 31, -i * 43, 29, 23);
            expected[1][i] = shared.fineInts(i * 31, -i * 43, 29, 23);
            expected[2][i] = other.coarseInts(i * 31, -i * 43, 29, 23);
            expected[3][i] = other.fineInts(i * 31, -i * 43, 29, 23);
            nativeExpected[i] = nativeManager.getBiomesForGeneration(null, i * 31, -i * 43, 29, 23);
        }
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[5];
        for (int index = 0; index < readers.length; index++) {
            final int worker = index;
            readers[index] = new Thread(() -> {
                try {
                    start.await();
                    for (int run = 0; run < 2000 && failure.get() == null; run++) {
                        int n = run % 32;
                        if (worker == 4) {
                            if (vanilla) {
                                if (!Arrays.equals(nativeExpected[n], nativeManager.getBiomesForGeneration(null, n * 31, -n * 43, 29, 23)))
                                    throw new AssertionError("external native WCM changed at run=" + run);
                            } else {
                                // Separate phase: do not sabotage vanilla's own safety while testing GTSR.
                                IntCache.resetIntCache();
                                Arrays.fill(IntCache.getIntCache(4096), -12345);
                            }
                        } else {
                            GTSRGenLayerChain chain = worker < 2 ? shared : other;
                            int[] result = (worker & 1) == 0 ? chain.coarseInts(n * 31, -n * 43, 29, 23)
                                : chain.fineInts(n * 31, -n * 43, 29, 23);
                            if (!Arrays.equals(expected[worker][n], result))
                                throw new AssertionError("GTSR worker=" + worker + " changed at run=" + run);
                        }
                    }
                } catch (Throwable t) { failure.compareAndSet(null, t); }
            }, "biome-isolation-" + index);
            readers[index].setDaemon(true);
            readers[index].start();
        }
        start.countDown();
        for (Thread reader : readers) {
            reader.join(60000L);
            check(!reader.isAlive(), "sampling thread timed out");
        }
        if (failure.get() != null) throw new AssertionError("concurrent native isolation failed", failure.get());
        checks++;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("window-only")) {
            oddWindowCoverage();
            System.out.println("PASS GenLayerIsolationCheck: 625 unaligned odd windows match native envelope crops");
            return;
        }
        borrowedPoolMustSurvive(); // Deterministic red on the R2 monitor-only chain, without races.
        if (args.length > 0 && args[0].equals("pool-only")) {
            System.out.println("PASS GenLayerIsolationCheck: borrowed global cache remains untouched");
            return;
        }
        nativeParity();
        oddWindowCoverage();
        ownedResults();
        concurrency(true);
        concurrency(false);
        System.out.println("PASS GenLayerIsolationCheck: " + checks + " checks; " + cells + " compared cells (including native-reference self-checks); "
            + directFineWindows + " direct fine windows, " + paddedFineWindows + " native-envelope crops; "
            + "20000 concurrent operations; bounded private scratch and owned results");
    }
}
