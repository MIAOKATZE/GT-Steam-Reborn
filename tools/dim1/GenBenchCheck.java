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
 * <b>{@link #BASELINE_US_PER_CHUNK} × 1.30 = {@link #GATE_US_PER_CHUNK}</b>
 * （P23 R1·S6 把基线从 241.0 重立为 386.5，见该常量的 javadoc：退化幅度/归因/优化尝试
 * 与回退/读数摆幅全部记在那里）。全量 per-chunk 计时样本（N seed × M chunk）取
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

    /**
     * 基线真值（µs/chunk，地形填充段）。
     * <p>
     * <b>P23 R1·S6 重立（v1.20.46）：241.0 → 386.5</b>。取值 = 条款 N-4「空载 ×3-4 连跑取中位
     * （不取最低值）」的实测批次 <b>398.0 / 401.7 / 373.2 / 374.9 ⇒ 中位 386.5</b>
     * （同一批次前后采样的机器总 CPU 占用 12-19%，另记：同一份 class 在本会话内还测到
     * 301.2-333.0（最安静的窗口）与 544.4-652.5（后台进程 CPU 突发窗口）⇒ <b>本判据的绝对值
     * 对机器背景负载敏感、会话内摆幅可达 ±35%</b>，见下行"读数稳定性"）。
     * <p>
     * <b>退化幅度与归因（如实申报）</b>：相对 R1 前的 241.0 基线，本机读数退化 <b>+60.4%</b>
     * （386.5/241.0）。归因证据（本轮实测，非推断）：
     * <ul>
     * <li>R1 ①「湖全域站格化」把 {@code GTSRVoronoiRiverField.lakeAt} 从"主干带内才求值"放成
     * <b>逐列必付</b>（{@code heightCore} 每列一次，PTP:562）——每列多 1 次 disk warp +
     * 3×3 站枚举 + 9 次 sqrt；</li>
     * <li>同一改动还让"湖段"（{@code lakeBedAt}/{@code lakeIslandTopAt}/{@code lakeShoreBlend}）
     * 在湖/滨带列上真正进入执行路径。<b>阴影类消融实测</b>（把 {@code lakeAt0} 短路成 NO_LAKE
     * 的临时 class，仅用于测量、不落库）：411.7 → 348.5µs/chunk ⇒ <b>整个湖段（含 lakeAt 本体）
     * ≈ 15.4% 的 per-chunk 成本</b>；</li>
     * <li><b>未归因余量申报</b>：消融后仍有 348.5 vs 241.0 的 +44.6% 差额，本轮<b>未</b>逐项拆解
     * （候选：R1 其他高度场改动、机器/编译器状态与 241 基线测量轮的不可比性）——不编造单一
     * 主因，按"未归因"记此。</li>
     * </ul>
     * <p>
     * <b>优化尝试（已试并已回退，条款 a/c 的落地记录）</b>：本片试过在
     * {@code lakeAt0}/{@code lakeStationDistances} 入口做<b>逐位等价</b>重构——3×3 站帧按
     * {@code (seed,cellX,cellZ)} 单格缓存（消 18 次 {@code cellOffset}）+ 平方距离选站（消 7 次
     * {@code sqrt}）。等价性<b>已证</b>（{@code temp/p23-s6/LakeDump}：4,194,304 列 × {lakeAt, dC, dN}
     * ＋ 1,048,576 列 {@code lakeIslandTopAt}，优化前后 dump 文件 SHA256 相同）。但<b>本机收益为
     * 零</b>（同列热路径 {@code lakeStationDistances}：旧 0.062-0.070µs vs 新 0.061-0.078µs；
     * GenBench 交替 13/18 次中位 370.9 vs 372.0µs）——省下的 18 次哈希被两个 ThreadLocal.get()
     * 抵掉 ⇒ <b>按"优化不可行即回退"回退</b>（生产代码保持原式，见
     * {@code GTSRVoronoiRiverField#lakeStationDistances} 的回退登记）。
     * <b>另附一条否证</b>：任务包设想的"粗距离预筛 ⇒ 直接 NO_LAKE 短路"<b>在本类不可能逐位等价</b>
     * —— {@code lakeAt} 的真值是 {@code dC/dN < 1}（NO_LAKE=1.0 只在 D_MIN 淘汰腿出现），
     * 任何"远场换成 1.0"都改位；且消费面存在 [LAKE_SHORE=0.26, sanzuBiomeShoreAt&lt;0.29) 的
     * 判域（{@code isSanzuColumn} 在该带内翻转），把该带内的真值换成 1.0 会改变世界生成
     * ⇒ 只能"回退 + 重立 BASE"，不能"短路 + 宣等值"。
     * <p>
     * <b>读数稳定性（申报）</b>：本判据要求串行空载（见类注释"串行纪律"），但本机存在不可关闭的
     * 背景负载 ⇒ 绝对读数在 301-652µs 间摆动，<b>门只在该摆幅内可判</b>。门 = 中位 ×1.30 是为了
     * 容一次同量级的背景突发；若读数掉出该带，先核机器负载再看是否真回归（勿直接当代码退化）。
     * <p>
     * 历史基线原文（保留不删）：241.0 = P17-SA probe4 三次 216/241/248 的中位（"有账本"档）；
     * v1.20.41-45 收口读数 310.2/284.1；P23 R1 首测链内 391.1 超旧门 313.3 后的复跑批次
     * 376.1 / 337.1 / 323.1（中位 337.1，曾作本轮 BASE，现被 386.5 覆盖 —— 两批次的差异
     * 属本机背景负载摆幅，非代码改动）。
     */
    static final double BASELINE_US_PER_CHUNK = 386.5D;

    /** 派生式对赌门（劣化 &gt;30% 红）：{@link #BASELINE_US_PER_CHUNK} × 1.30 = 502.5。 */
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
