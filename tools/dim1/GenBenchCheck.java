import java.util.Arrays;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * dim78 地形填充段性能基准（P19 U8 新增，plan §J「新增性能基准判据」）。
 *
 * <p>
 * ═══ 口径（与 241µs 基线同形，P17-SA probe4 复刻）═══ {@code heightAt} 单列串行 walk：
 * 每 chunk 16×16=256 列三参 {@link ProsperityTerrainProfile#heightAt}，chunk 坐标
 * {@code (c%64)*16, (c/64)*16}（64×64 chunk 连续方块域）；账本装配 4 群系（生产形状，与
 * {@code temp/p17-sa/probe4.log} 的"有账本"档同口径——该档实测单 chunk 地形填充段中位
 * <b>241µs</b>，即本判据的基线真值）。
 *
 * <p>
 * ═══ 派生式阈值（写死，防漂移）═══ 对赌门 = 地形填充段劣化 &gt;30% 判红
 * （plan §J「对照 241µs 基线，派生式阈值，劣化&gt;30% 红」）⇒ 上界 =
 * <b>241.0 × 1.30 = 313.3 µs/chunk</b>。全量 per-chunk 计时样本（N seed × M chunk）取
 * <b>中位数</b>对门（中位数口径见任务包；p90 仅展示不作门）。红 ⇒ 本机回归即门红。
 *
 * <p>
 * ═══ 串行纪律（v1.20.38 实测教训）═══ 本判据<b>单线程串行</b>求值，零并行流/零额外线程；
 * 且不得与其它 harness/JVM <b>并发</b>运行（surface_checks.sh 顺序执行各判据即满足）——
 * 与 harness 并发跑会失真（同机争抢使读数虚高，v1.20.38 轮实测结论，勿改并发）。
 *
 * <p>
 * ═══ 读数与出口 ══ stdout 报 per-chunk 中位/均值/p90、单列均值与抽样中位、门判定行
 * {@code GENBENCH ...}；末行 {@code assertions=1}（门 = 唯一断言）。超门 exit 1。
 * 用法：{@code java GenBenchCheck [seeds] [chunksPerSeed]}（默认 4 × 1024 = 4096 chunk，
 * 与 probe4 同量级；快速档可传 2 256）。
 */
public final class GenBenchCheck {

    /** 基线真值（µs/chunk，地形填充段；出处 temp/p17-sa/probe4.log 有账本档三次 216/241/248 的中位）。 */
    static final double BASELINE_US_PER_CHUNK = 241.0D;

    /** 派生式对赌门（劣化 &gt;30% 红）：{@link #BASELINE_US_PER_CHUNK} × 1.30 = 313.3。 */
    static final double GATE_US_PER_CHUNK = BASELINE_US_PER_CHUNK * 1.30D;

    private GenBenchCheck() {}

    public static void main(String[] args) {
        final int seeds = args.length > 0 ? Math.max(1, parseInt(args[0], 4)) : 4;
        final int chunksPerSeed = args.length > 1 ? Math.max(64, parseInt(args[1], 1024)) : 1024;
        for (int i = 0; i < 4; i++) {
            final BiomeGenBase b = new BiomeGenBase(180 + i) {};
            GTSRBiomeAuthority.recordAllocation(GTSRBiomeAuthority.BiomeId.values()[i], 180 + i, 180 + i, b);
        }
        long acc = 0L;
        // —— 预热（JIT + ThreadLocal 噪声表；不计入读数）——
        acc += walk(7L, chunksPerSeed, null);
        // —— 正式读数：per-chunk 串行计时，全部 seed 样本合并取中位 ——
        final long[] chunkNanos = new long[seeds * chunksPerSeed];
        int k = 0;
        final long t0 = System.nanoTime();
        for (int s = 0; s < seeds; s++) {
            final long seed = 0x47454EACL + s;
            final int base = k;
            acc += walk(seed, chunksPerSeed, chunkNanos, base);
            k += chunksPerSeed;
        }
        final long t1 = System.nanoTime();
        // —— 单列抽样中位（每 seed 取首个 32×64 列窗逐列计时；含 nanoTime 开销，仅展示）——
        final long[] colNanos = new long[seeds * 2048];
        int ck = 0;
        for (int s = 0; s < seeds; s++) {
            final long seed = 0x47454EACL + s;
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 64; z++) {
                    final long c0 = System.nanoTime();
                    acc += ProsperityTerrainProfile.heightAt(seed, 1024 + x, 1024 + z);
                    colNanos[ck++] = System.nanoTime() - c0;
                }
            }
        }
        final long[] chunks = chunkNanos.clone();
        final long[] cols = colNanos.clone();
        Arrays.sort(chunks);
        Arrays.sort(cols);
        final double medianChunkUs = chunks[chunks.length / 2] / 1000.0D;
        final double p90ChunkUs = chunks[(int) (chunks.length * 0.9)] / 1000.0D;
        final double meanChunkUs = (t1 - t0) / 1000.0D / chunks.length;
        final double meanColUs = (t1 - t0) / 1000.0D / (chunks.length * 256.0D);
        final double medianColNs = cols[cols.length / 2];
        final boolean pass = medianChunkUs <= GATE_US_PER_CHUNK;
        System.out.printf(
            "GENBENCH serial=1 seeds=%d chunks=%d cols=%d medianChunk=%.1fus meanChunk=%.1fus p90Chunk=%.1fus"
                + " meanCol=%.3fus medianCol=%.0fns acc=%d%n",
            seeds, chunks.length, chunks.length * 256L, medianChunkUs, meanChunkUs, p90ChunkUs, meanColUs,
            medianColNs, acc);
        System.out.printf("GENBENCH gate=medianChunk<=%.1f (baseline %.1f x 1.30, p17-sa probe4 ledger on; red>30%%)"
                + " verdict=%s%n",
            GATE_US_PER_CHUNK, BASELINE_US_PER_CHUNK, pass ? "PASS" : "FAIL");
        System.out.println("GENBENCH note=serial-only by contract (v1.20.38: concurrent harness runs distort);"
            + " terrain-fill segment only (16x16 heightAt walk per chunk)");
        System.out.println("assertions=" + (pass ? 1 : 0) + " failures=" + (pass ? 0 : 1));
        if (!pass) {
            System.out.printf("  FAIL medianChunk %.1fus > gate %.1fus%n", medianChunkUs, GATE_US_PER_CHUNK);
            System.exit(1);
        }
    }

    /** 单 seed 的 64×64 chunk 域串行 walk（SA probe4 同形）；times 非空时逐 chunk 计时。 */
    private static long walk(long seed, int chunks, long[] times, int timeBase) {
        long acc2 = 0L;
        for (int ch = 0; ch < chunks; ch++) {
            final int bx = (ch % 64) * 16;
            final int bz = (ch / 64) * 16;
            final long c0 = times != null ? System.nanoTime() : 0L;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    acc2 += ProsperityTerrainProfile.heightAt(seed, bx + x, bz + z);
                }
            }
            if (times != null) {
                times[timeBase + ch] = System.nanoTime() - c0;
            }
        }
        return acc2;
    }

    private static long walk(long seed, int chunks, long[] ignored) {
        return walk(seed, chunks, null, 0);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            return def;
        }
    }
}
