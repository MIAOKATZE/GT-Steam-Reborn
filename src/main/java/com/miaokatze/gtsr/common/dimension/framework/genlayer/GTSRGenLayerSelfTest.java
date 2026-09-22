package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import java.util.Arrays;

/**
 * GenLayer 链离线自测（切片 A；包内 main，不接入构建主流程，不进任何运行期路径）。
 * <p>
 * 对多 seed × 正/负象限采样粗层，断言四件事（tools/dim1 断言脚本风格的 fail-fast main）：
 * <ol>
 * <li><b>确定性</b>：同 seed 重建链两次全窗逐位一致；单点小窗采样与整窗采样逐位一致
 * （粗/细两面都查——B1 三面同解依赖的纯函数语义）；</li>
 * <li><b>均分</b>：各 id 实测占比对 1/N 偏差 ≤ 阈值。<b>阈值的口径是"窗内 selector 格数"，
 * 不是窗边长</b>——见 {@link #SELECTOR_CELLS_PER_AXIS}（P17 S-G 实测重标定：判据②的噪声只随格数
 * m 按 m^-1/2 收敛、与 zoom 档无关、且无系统性偏置地板；取证
 * {@code plan/tmp/p17-sg/SG-RESULT.md} + 驱动 {@code temp/p17-sg/src/{UniformityCalib,RedInjection}.java}
 * + 日志 {@code temp/p17-sg/out/{calib-grid-1,red-injection-1}.log}，下一轮照这两个驱动可整网格复算）；</li>
 * <li><b>成片/非噪点</b>：主导 id 最大 4-连通域 ≥ 阈值（粗格），孤岛格（4 邻全异）占比 ≤ 阈值；</li>
 * <li><b>边界非直线</b>：相邻行"变道位置集合"完全相同的比例 ≤ 阈值（条纹/直线边界的特征是
 * 变道位置恒定；voronoi 有机边界应显著低于）；附带细层 voronoi 抖动率（细层 ≠ 平铺粗层的
 * 比例，证明 voronoi 真实生效而非恒等）。</li>
 * </ol>
 * <b>第 0 项（P17 S-G 新增）：窗规格 ↔ 链 zoom 档同代自检</b>（{@link #checkWindowZoomPinning(long)}）——
 * 本判据的窗长是编译期内联常量，与运行期加载的 {@link GTSRGenLayerChain} 拼错代会让判据②的样本量
 * 口径直接失真（本轮快档那条 12.47pp "均分红"就是这个，见该方法注释）。
 * <p>
 * 运行需要 MC 类路径（链复用 vanilla GenLayer），例：
 * {@code java -cp build/classes/java/main;<minecraft+deps> com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerSelfTest}
 * 纯 int 语义，不触碰 BiomeGenBase 注册表，id 用 [0,254] 内哑元（对齐 BiomeZoneCheck 的 ID_START=180）。
 */
public final class GTSRGenLayerSelfTest {

    /**
     * 判据②的<b>统计口径常量</b>：粗窗每轴覆盖的 selector 格数。
     * <p>
     * P17 S-G 实测重标定由 16 → <b>32</b>（格数 256 → 1024）。取证与推导全文见
     * {@code plan/tmp/p17-sg/SG-RESULT.md}；要点（全部为 8 seed × 6 原点 = 48 区<b>事先固定</b>网格的
     * 实测值，驱动 {@code temp/p17-sg/src/UniformityCalib.java}，日志
     * {@code temp/p17-sg/out/calib-grid-1.log}，zoom=5）：
     * <table border=1>
     * <caption>每区 maxdev（四 id 份额对 1/4 偏差取最大）分布，单位 pp</caption>
     * <tr>
     * <th>窗(粗格)</th>
     * <th>格数 m</th>
     * <th>p50</th>
     * <th>p90</th>
     * <th>max</th>
     * <th>mean</th>
     * <th>sd</th>
     * <th>超 12pp</th>
     * </tr>
     * <tr>
     * <td>256</td>
     * <td>64</td>
     * <td>8.16</td>
     * <td>14.78</td>
     * <td>15.72</td>
     * <td>9.06</td>
     * <td>3.81</td>
     * <td>12/48</td>
     * </tr>
     * <tr>
     * <td>512</td>
     * <td>256</td>
     * <td>4.05</td>
     * <td>6.38</td>
     * <td>8.13</td>
     * <td>4.35</td>
     * <td>1.58</td>
     * <td>0/48</td>
     * </tr>
     * <tr>
     * <td>1024</td>
     * <td>1024</td>
     * <td>1.92</td>
     * <td>3.34</td>
     * <td>5.28</td>
     * <td>2.11</td>
     * <td>1.06</td>
     * <td>0/48</td>
     * </tr>
     * <tr>
     * <td>2048</td>
     * <td>4096</td>
     * <td>1.13</td>
     * <td>1.77</td>
     * <td>2.18</td>
     * <td>1.14</td>
     * <td>0.49</td>
     * <td>0/48</td>
     * </tr>
     * <tr>
     * <td>4096</td>
     * <td>16384</td>
     * <td>0.45</td>
     * <td>0.80</td>
     * <td>0.95</td>
     * <td>0.50</td>
     * <td>0.22</td>
     * <td>0/48</td>
     * </tr>
     * </table>
     * 三条被实测支撑的结论，决定"抬格数"而不是"改阈值"：
     * <ol>
     * <li><b>格数 m 才是不变量，zoom 档不是</b>：同 m 的实测尾部跨 zoom 重合
     * （m=256：zoom4/窗 256² max 8.79 ↔ zoom5/窗 512² max 8.13；m=1024：zoom4/窗 512² 4.84 ↔
     * zoom5/窗 1024² 5.28）。⇒ S-A「窗边长按 2^(zoom-4) 归一、格数不变」的推理方向是对的，
     * 但把 m 钉在 256 是钉在了噪声吃掉阈值 68% 的位置上；</li>
     * <li><b>没有系统性偏置地板</b>：mean maxdev 逐档 ≈ ÷2（16.95/9.06/4.35/2.11/1.14/0.50 ⇔ m^-1/2），
     * 且 m=16384 时四 id 的<b>均值</b>份额 = 0.24986/0.25014/0.24992/0.25008（最大偏离 0.014pp =
     * 阈值的 0.12%）。⇒ 加窗真能把尾部压下去，不是在掩盖某个不随窗衰减的偏差（class 注释原文
     * "zoom 的中心多数票有收敛偏置、阈值留裕量"因此被证否，偏差项不存在，裕量买的是抽样噪声）；</li>
     * <li><b>m=256 不只是容易假红，它真的会漏红</b>：RED 注入 = 用 selector 的重复项语义把某个
     * id 的权重人为加倍（{@code ids={180,180,181,182,183}}，解析偏差恰 15.00pp > 阈值），
     * 48 区实测（{@code temp/p17-sg/out/red-injection-1.log}）在 m=256 下有 <b>4 区落在阈值之下</b>
     * （最小 9.02pp ⇒ 漏检），m=1024 下 <b>48/48 全红</b>（最小 13.85pp），m=4096 下 48/48（最小 15.11pp）。</li>
     * </ol>
     * 取 m=1024（32×32）是<b>同时</b>满足「零假设尾部 ≤ 阈值/2」（5.28 ≤ 6）与「加倍注入 100% 检出」
     * 两条件的最小档；再往上一档（m=4096）只把 5.28 压到 2.18，代价是窗面积 ×4 且对判据③④无收益。
     * 代价实测：本工具快档单次 1.66s → <b>1.9~2.5s</b>（`WALL_MS` 多次实测，含区数 6→9，
     * 见 {@link #OFFSETS}；日志 {@code temp/p17-sg/out/selftest-before-*} 与 {@code selftest-verify2-*}，
     * 同 JVM 同参数）。
     */
    private static final int SELECTOR_CELLS_PER_AXIS = 32;
    /**
     * 粗层采样窗边长（粗格）= {@code SELECTOR_CELLS_PER_AXIS · 2^zoomLevels}
     * （1 selector 格 = {@code 2^zoomLevels} 粗格，{@link GTSRGenLayerChain#DEFAULT_ZOOM_LEVELS}）。
     * <p>
     * 写成式子而不是字面量，是为了让<b>判据②的样本量口径</b>与 zoom 档结构耦合：今后再动
     * {@code DEFAULT_ZOOM_LEVELS} 不必同步手调本窗（S-A 那次"改链又改判据、两边各自数数"的口径漂移
     * 就是从这两个独立字面量开始的），而 {@code UNIFORM_TOL} 的含义（对 m 格的抽样噪声留 ≥2× 余量）
     * 在任何档位下都保持成立。当前值：zoom=5 ⇒ <b>1024</b>（S-A 档的 512 是 m=256 口径，已被上面
     * 第 3 条实测否掉）。
     */
    private static final int COARSE_WINDOW = SELECTOR_CELLS_PER_AXIS << GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS;
    /**
     * 细层每轴的 selector 格数——<b>本片只为窗规格建立同代换算，不改判据④的口径</b>：取 1 使
     * {@link #FINE_WINDOW} 的数值与历史完全一致（zoom5 → 128 方块，zoom4 → 64 方块，逐位同 S-A 前后
     * 两代），同时纠正 S-A 注释里的算错：128 方块 = 32 粗格 = <b>1×1</b> 个 selector 格，<b>不是</b>"2×2 个"。
     */
    private static final int FINE_SELECTOR_CELLS_PER_AXIS = 1;
    /**
     * 细层采样窗边长（方块，1:1）= {@code FINE_SELECTOR_CELLS_PER_AXIS · 4 · 2^zoomLevels}。
     * <p>
     * 本判据④（{@link #VORONOI_JITTER_MIN}）在 128² 方块 = 1 格口径下实测抖动率 0.8%~2.2%
     * （S-G 现跑 9 窗，见 {@link #VORONOI_JITTER_MIN} 注释与 SG-RESULT §6）——这条余量 1.6×，
     * 比判据②重标定前的 1.48× 健康，故本片不动其数值口径，只把它纳入同一条 zoom 换算式
     * （取证同 SG-RESULT §5）。
     */
    private static final int FINE_WINDOW = FINE_SELECTOR_CELLS_PER_AXIS << (GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS + 2);
    /** 单点一致性抽查步长（粗格；跨步覆盖不同 (x,z) 奇偶性）。 */
    private static final int POINT_STRIDE = 7;

    private static final long[] SEEDS = { 12345L, -987654321L, 0x50524F53L };
    /**
     * 采样原点（粗格）。前两项是历史值；第三项是 P17 S-G 补的<b>完全独立远场</b>。
     * <p>
     * 补它的理由是窗抬到 1024 的副作用：{@code (0,0)} 与 {@code (-128,-96)} 两窗的交叠率从
     * 窗 512 时的 50% 涨到 89%（有效独立区数 6 → ≈3.5），区数多样性反而下降；
     * {@code (50000,-40000)} 与两者在 1024 窗下逐轴不相交（x∈[50000,51024)），且 x 不是 32 的整数倍
     * （50000/32 = 1562.5 ⇒ 仍走非格对齐原点路径）、z 为负 ⇒ 仍覆盖负坐标 {@code >> 2} / floorDiv 路径。
     * 该原点取自 S-G 取证网格的固定集合（不是一次性挑出来的幸运点），其读数在
     * {@code calib-grid-1.log} 里可与全网格一起复算。
     */
    private static final int[][] OFFSETS = { { 0, 0 }, { -128, -96 }, { 50000, -40000 } };

    /** 四群系哑元 id（等权轮盘；与 tools/dim1/BiomeZoneCheck 的 ID_START 段对齐，纯 int 不注册）。 */
    private static final int[] BIOME_IDS = { 180, 181, 182, 183 };

    /**
     * 判据②阈值。<b>P17 S-G 一个数字都没改</b>（0.12 是 P17 之前的原值；S-A 与本片都只动窗规格）。
     * <p>
     * 为什么不能再往上抬：单 id 权重被抬到 k 倍时的期望份额是 {@code k/(k+3)}，其偏差
     * {@code dev(k) = k/(k+3) - 1/4} 是<b>与窗无关</b>的解析量，dev=0.12 对应 k≈1.762。
     * 把阈值抬到 0.15 就等于宣布"权重加倍（dev 恰 0.15）都不算破坏"——判据②存在的理由消失。
     * 为什么 0.12 在 m=1024 下仍然抓得住它本来要抓的破坏：见 {@link #SELECTOR_CELLS_PER_AXIS}
     * 第 3 条的 RED 注入实测（加倍档 48/48 全红，最小 13.85pp），以及零假设 48 区最大 5.28pp
     * ⇒ 实际工作点落在"噪声上界 5.3pp ↔ 待抓破坏 15pp"之间，阈值 12pp 两头各留 ≥2.2× / 1.25×。
     */
    private static final double UNIFORM_TOL = 0.12;
    private static final int DOMINANT_COMPONENT_MIN = 64;
    private static final double ISLAND_RATIO_MAX = 0.10;
    private static final double STRAIGHT_ROW_MAX = 0.10;
    /**
     * voronoi 抖动率界：细层 ≠ 平铺粗层的比例。vanilla 抖动 ±1.8/4 格下只有格边界附近的方块会被
     * 邻角抢走，故量级本就是百分点级：S-G 现跑（zoom=5、3 seed × 3 原点 = 9 个细窗）实测
     * <b>0.8%~2.2%</b>（日志 {@code temp/p17-sg/out/selftest-verify-1.log}；被本条替换掉的旧注释写
     * "1.4%~3.9%"，那是 zoom=4 代 6 窗的读数，已不适用）。下界取 0.5% 只为证明 voronoi 真实生效
     * （恒等映射会精确得 0），上界 50% 防退化成噪点。S-G <b>未动</b>本判据口径，只把
     * {@link #FINE_WINDOW} 纳入同一条 zoom 换算式。
     */
    private static final double VORONOI_JITTER_MIN = 0.005;
    private static final double VORONOI_JITTER_MAX = 0.50;

    private GTSRGenLayerSelfTest() {}

    public static void main(String[] args) {
        constructorValidation();
        checkWindowZoomPinning(SEEDS[0]);
        for (final long seed : SEEDS) {
            for (final int[] offset : OFFSETS) {
                checkRegion(seed, offset[0], offset[1]);
            }
        }
        zoomVariantSmoke();
        System.out.println(
            "GENLAYER PASS: seeds=" + SEEDS.length
                + " offsets="
                + OFFSETS.length
                + " coarseWindow="
                + COARSE_WINDOW
                + "x"
                + COARSE_WINDOW
                + " selectorCells="
                + SELECTOR_CELLS_PER_AXIS
                + "x"
                + SELECTOR_CELLS_PER_AXIS
                + " fineWindow="
                + FINE_WINDOW
                + "x"
                + FINE_WINDOW
                + " zoomLevels="
                + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS
                + " ids="
                + Arrays.toString(BIOME_IDS)
                + " — pinning/determinism/uniformity/coherence/non-straight-boundary all green");
    }

    /**
     * 窗规格 ↔ 链 zoom 档<b>同代自检</b>（P17 S-G；本轮快档那条 12.47pp "均分红"的直接根因）。
     * <p>
     * 取证结论（SG-RESULT §4）：那条红的四 id 份额与 S-G 网格里 {@code zoom=5 / 窗 256² / seed=-987654321
     * / off=(0,0)} 那一行<b>逐位相同</b>（id183=0.3747100830078125 = 24557/65536 ⇒ 窗是 256² 不是 512²），
     * 且抛出点栈帧行号 {@code :364 fail ← :112 ← :95 ← :57} <b>只存在于 HEAD 版本文件</b>
     * （工作树版是 :378/:126/:109/:71）。⇒ 跑的是 <b>HEAD 代编译的 {@code GTSRGenLayerSelfTest.class}
     * （窗 256）＋ 工作树代的 {@code GTSRGenLayerChain}（zoom=5）</b>：窗是编译期内联常量、链是运行期
     * 加载，两代一拼，判据②的有效样本量从 256 格掉到 64 格（σ 2.71pp → 5.42pp），12.47pp 只是
     * 2.3σ 的正常波动——既不是生成质量红了，也不是阈值该放宽。
     * <p>
     * 本检查把这个隐形失真变成显式失败：{@link GTSRGenLayerChain#zoomLevels()} 是<b>运行期</b>方法调用
     * （读实际加载的那条链），而 {@code COARSE_WINDOW}/{@code FINE_WINDOW} 是编译期内联常量，
     * 两者不满足 {@link #SELECTOR_CELLS_PER_AXIS}/{@link #FINE_SELECTOR_CELLS_PER_AXIS} 的换算式
     * ⇒ 当场报"混代产物、先整代重编"，而不是留下一条把调查者往阈值上带的均分红。
     * 任一方向的陈旧（本类旧 / 链旧）都会被抓。
     */
    private static void checkWindowZoomPinning(long seed) {
        final int runtimeZoom = new GTSRGenLayerChain(seed, BIOME_IDS).zoomLevels();
        final int expectedCoarse = SELECTOR_CELLS_PER_AXIS << runtimeZoom;
        final int expectedFine = FINE_SELECTOR_CELLS_PER_AXIS << (runtimeZoom + 2);
        if (expectedCoarse != COARSE_WINDOW || expectedFine != FINE_WINDOW) {
            throw new AssertionError(
                "GENLAYER FAIL mixed-generation artifacts: this class's inlined sampling windows (coarse="
                    + COARSE_WINDOW
                    + ", fine="
                    + FINE_WINDOW
                    + ") do not match what the runtime-loaded"
                    + " GTSRGenLayerChain requires (zoomLevels="
                    + runtimeZoom
                    + " => coarse="
                    + expectedCoarse
                    + ", fine="
                    + expectedFine
                    + "). The effective sample size of check (2) is therefore wrong;"
                    + " rebuild this package in one generation (gradle build, or javac all four genlayer sources"
                    + " together) and re-run. Do NOT touch UNIFORM_TOL.");
        }
        System.out.printf(
            "pinning ok: chain.zoomLevels=%d(runtime) => coarseWindow=%d (%dx%d selector cells), fineWindow=%d (%dx%d selector cells)%n",
            runtimeZoom,
            COARSE_WINDOW,
            SELECTOR_CELLS_PER_AXIS,
            SELECTOR_CELLS_PER_AXIS,
            FINE_WINDOW,
            FINE_SELECTOR_CELLS_PER_AXIS,
            FINE_SELECTOR_CELLS_PER_AXIS);
    }

    private static void checkRegion(long seed, int ox, int oz) {
        final GTSRGenLayerChain chain = new GTSRGenLayerChain(seed, BIOME_IDS);
        final GTSRGenLayerChain rebuild = new GTSRGenLayerChain(seed, BIOME_IDS);
        final int[] coarse = chain.coarseInts(ox, oz, COARSE_WINDOW, COARSE_WINDOW);
        // 快照取逻辑窗长 W*H：IntCache 底数组可能更长，尾部是未写的陈旧格
        final int[] snapshot = Arrays.copyOf(coarse, COARSE_WINDOW * COARSE_WINDOW);
        final int[] coarseAgain = rebuild.coarseInts(ox, oz, COARSE_WINDOW, COARSE_WINDOW);
        if (!regionEquals(snapshot, coarseAgain, COARSE_WINDOW * COARSE_WINDOW)) {
            fail(seed, ox, oz, "same-seed rebuild mismatch (chain not deterministic)");
        }
        // 单点小窗 vs 整窗（粗面；chunk 代表点口径 blockX = coarseX << 2）
        for (int z = 0; z < COARSE_WINDOW; z += POINT_STRIDE) {
            for (int x = 0; x < COARSE_WINDOW; x += POINT_STRIDE) {
                final int point = chain.biomeAtCoarse((ox + x) << 2, (oz + z) << 2);
                if (point != snapshot[x + z * COARSE_WINDOW]) {
                    fail(seed, ox, oz, "coarse single-point != window at coarse(" + (ox + x) + "," + (oz + z) + ")");
                }
            }
        }
        statsAndAsserts(seed, ox, oz, snapshot);
        fineFaceChecks(seed, ox, oz, chain, snapshot);
    }

    private static void statsAndAsserts(long seed, int ox, int oz, int[] coarse) {
        final int n = BIOME_IDS.length;
        final int total = coarse.length;
        final int[] counts = new int[n];
        for (final int id : coarse) {
            counts[indexOf(id)]++;
        }
        // 均分
        final StringBuilder shares = new StringBuilder();
        double maxDev = 0.0;
        for (int i = 0; i < n; i++) {
            final double share = (double) counts[i] / total;
            maxDev = Math.max(maxDev, Math.abs(share - 1.0 / n));
            shares.append(String.format("%s=%.1f%%", shortId(BIOME_IDS[i]), share * 100));
            if (Math.abs(share - 1.0 / n) > UNIFORM_TOL) {
                fail(
                    seed,
                    ox,
                    oz,
                    "uniformity broken: id " + BIOME_IDS[i]
                        + " share "
                        + share
                        + " (cells="
                        + total
                        + "="
                        + COARSE_WINDOW
                        + "x"
                        + COARSE_WINDOW
                        + ", selectorCells="
                        + SELECTOR_CELLS_PER_AXIS
                        + "x"
                        + SELECTOR_CELLS_PER_AXIS
                        + ", tol="
                        + UNIFORM_TOL
                        + ")");
            }
            if (i < n - 1) {
                shares.append(' ');
            }
        }
        // 成片：主导 id 最大 4-连通域 + 孤岛率
        int dominant = 0;
        for (int i = 1; i < n; i++) {
            if (counts[i] > counts[dominant]) {
                dominant = i;
            }
        }
        final int largest = largestComponent(coarse, BIOME_IDS[dominant]);
        if (largest < DOMINANT_COMPONENT_MIN) {
            fail(seed, ox, oz, "dominant id " + BIOME_IDS[dominant] + " largest component " + largest);
        }
        final double islands = islandRatio(coarse);
        if (islands > ISLAND_RATIO_MAX) {
            fail(seed, ox, oz, "island ratio " + islands + " > " + ISLAND_RATIO_MAX);
        }
        // 边界非直线
        final double straight = straightRowRatio(coarse);
        if (straight > STRAIGHT_ROW_MAX) {
            fail(seed, ox, oz, "straight-boundary row ratio " + straight + " > " + STRAIGHT_ROW_MAX);
        }
        System.out.printf(
            "seed=%d off=(%d,%d) %s maxDev=%.2fpp/%.0fpp largestComp(%d)=%d cells(=%.0f blocks) islands=%.1f%% straightRows=%.1f%%%n",
            seed,
            ox,
            oz,
            shares,
            maxDev * 100.0,
            UNIFORM_TOL * 100.0,
            BIOME_IDS[dominant],
            largest,
            largest * 4.0,
            islands * 100,
            straight * 100);
    }

    private static void fineFaceChecks(long seed, int ox, int oz, GTSRGenLayerChain chain, int[] coarseSnapshot) {
        final int bx0 = ox << 2;
        final int bz0 = oz << 2;
        final int[] fine = chain.fineInts(bx0, bz0, FINE_WINDOW, FINE_WINDOW);
        // 细面确定性 + 单点一致性（小步距抽查）；快照同样取逻辑窗长
        final int[] fineCopy = Arrays.copyOf(fine, FINE_WINDOW * FINE_WINDOW);
        final GTSRGenLayerChain rebuild = new GTSRGenLayerChain(seed, BIOME_IDS);
        final int[] fineAgain = rebuild.fineInts(bx0, bz0, FINE_WINDOW, FINE_WINDOW);
        if (!regionEquals(fineCopy, fineAgain, FINE_WINDOW * FINE_WINDOW)) {
            fail(seed, ox, oz, "fine face rebuild mismatch");
        }
        for (int z = 0; z < FINE_WINDOW; z += POINT_STRIDE) {
            for (int x = 0; x < FINE_WINDOW; x += POINT_STRIDE) {
                if (chain.biomeAtFine(bx0 + x, bz0 + z) != fineCopy[x + z * FINE_WINDOW]) {
                    fail(seed, ox, oz, "fine single-point != window at block(" + (bx0 + x) + "," + (bz0 + z) + ")");
                }
            }
        }
        // 细层 id 全部 ∈ 入参集合（防御钳制路径正常时不触发；这里显式复核）
        int jitter = 0;
        for (int z = 0; z < FINE_WINDOW; z++) {
            for (int x = 0; x < FINE_WINDOW; x++) {
                final int id = fineCopy[x + z * FINE_WINDOW];
                if (indexOf(id) < 0) {
                    fail(seed, ox, oz, "fine id " + id + " outside input set");
                }
                final int tiled = coarseSnapshot[(x >> 2) + (z >> 2) * COARSE_WINDOW];
                if (id != tiled) {
                    jitter++;
                }
            }
        }
        final double ratio = (double) jitter / fineCopy.length;
        if (ratio < VORONOI_JITTER_MIN || ratio > VORONOI_JITTER_MAX) {
            fail(
                seed,
                ox,
                oz,
                "voronoi jitter ratio " + ratio + " outside [" + VORONOI_JITTER_MIN + "," + VORONOI_JITTER_MAX + "]");
        }
        System.out.printf("seed=%d off=(%d,%d) fine: ids-valid, voronoiJitter=%.1f%%%n", seed, ox, oz, ratio * 100);
    }

    /** 构造期 fail fast 契约：空表/越界 id/越界 zoomLevels 必须抛 IllegalArgumentException。 */
    private static void constructorValidation() {
        expectThrow("null biomeIds", () -> new GTSRGenLayerChain(1L, null));
        expectThrow("empty biomeIds", () -> new GTSRGenLayerChain(1L, new int[0]));
        expectThrow("id 255 (vanilla sentinel)", () -> new GTSRGenLayerChain(1L, new int[] { 10, 255 }));
        expectThrow("id 256 (byte aliasing)", () -> new GTSRGenLayerChain(1L, new int[] { 256 }));
        expectThrow("negative id", () -> new GTSRGenLayerChain(1L, new int[] { -1 }));
        expectThrow("zoomLevels -1", () -> new GTSRGenLayerChain(1L, BIOME_IDS, -1));
        expectThrow("zoomLevels 9", () -> new GTSRGenLayerChain(1L, BIOME_IDS, 9));
    }

    /** zoom 档位变体冒烟：0/1/6 可构造、确定性、输出 id 合法。 */
    private static void zoomVariantSmoke() {
        for (final int zoom : new int[] { 0, 1, 6 }) {
            final GTSRGenLayerChain a = new GTSRGenLayerChain(42L, BIOME_IDS, zoom);
            final GTSRGenLayerChain b = new GTSRGenLayerChain(42L, BIOME_IDS, zoom);
            final int[] wa = a.coarseInts(-8, -8, 64, 64);
            final int[] sa = Arrays.copyOf(wa, 64 * 64);
            final int[] wb = b.coarseInts(-8, -8, 64, 64);
            if (!regionEquals(sa, wb, 64 * 64)) {
                fail(42L, -8, -8, "zoom=" + zoom + " rebuild mismatch");
            }
            for (final int id : sa) {
                if (indexOf(id) < 0) {
                    fail(42L, -8, -8, "zoom=" + zoom + " id " + id + " outside set");
                }
            }
            if (a.zoomLevels() != zoom) {
                fail(42L, -8, -8, "zoomLevels() mismatch");
            }
        }
    }

    /** 指定 id 的最大 4-连通域大小（BFS，粗格网格）。 */
    private static int largestComponent(int[] grid, int id) {
        final int w = COARSE_WINDOW;
        final boolean[] seen = new boolean[grid.length];
        final int[] queue = new int[grid.length];
        int best = 0;
        for (int start = 0; start < grid.length; start++) {
            if (seen[start] || grid[start] != id) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            int size = 0;
            while (head < tail) {
                final int cell = queue[head++];
                size++;
                final int cx = cell % w;
                final int cz = cell / w;
                if (cx > 0 && !seen[cell - 1] && grid[cell - 1] == id) {
                    seen[cell - 1] = true;
                    queue[tail++] = cell - 1;
                }
                if (cx < w - 1 && !seen[cell + 1] && grid[cell + 1] == id) {
                    seen[cell + 1] = true;
                    queue[tail++] = cell + 1;
                }
                if (cz > 0 && !seen[cell - w] && grid[cell - w] == id) {
                    seen[cell - w] = true;
                    queue[tail++] = cell - w;
                }
                if (cz < w - 1 && !seen[cell + w] && grid[cell + w] == id) {
                    seen[cell + w] = true;
                    queue[tail++] = cell + w;
                }
            }
            if (size > best) {
                best = size;
            }
        }
        return best;
    }

    /** 孤岛率：4 邻均与本格不同 id 的格子占比（盐胡椒特征）。 */
    private static double islandRatio(int[] grid) {
        final int w = COARSE_WINDOW;
        int islands = 0;
        for (int z = 0; z < w; z++) {
            for (int x = 0; x < w; x++) {
                final int id = grid[x + z * w];
                final boolean loneWest = x == 0 || grid[x - 1 + z * w] != id;
                final boolean loneEast = x == w - 1 || grid[x + 1 + z * w] != id;
                final boolean loneNorth = z == 0 || grid[x + (z - 1) * w] != id;
                final boolean loneSouth = z == w - 1 || grid[x + (z + 1) * w] != id;
                if (loneWest && loneEast && loneNorth && loneSouth) {
                    islands++;
                }
            }
        }
        return (double) islands / grid.length;
    }

    /**
     * 边界直线度：相邻两行的"变道位置集合"（行内 id 发生变化的 x 下标集）完全相同的行对占比。
     * 直线条纹图该值 → 1；有机边界应显著低。双方都无变道的行对不计入分母。
     */
    private static double straightRowRatio(int[] grid) {
        final int w = COARSE_WINDOW;
        boolean[] prev = null;
        int straight = 0;
        int compared = 0;
        for (int z = 0; z < w; z++) {
            final boolean[] changes = new boolean[w];
            int changeCount = 0;
            for (int x = 1; x < w; x++) {
                if (grid[x + z * w] != grid[x - 1 + z * w]) {
                    changes[x] = true;
                    changeCount++;
                }
            }
            if (prev != null && (changeCount > 0 || anyTrue(prev))) {
                compared++;
                if (Arrays.equals(changes, prev)) {
                    straight++;
                }
            }
            prev = changes;
        }
        return compared == 0 ? 0.0 : (double) straight / compared;
    }

    private static boolean anyTrue(boolean[] flags) {
        for (final boolean flag : flags) {
            if (flag) {
                return true;
            }
        }
        return false;
    }

    /**
     * 前 {@code length} 格逐位相等（Java 8 无 Arrays.equals 区间重载；两数组均可能来自
     * IntCache、长于逻辑窗，只比逻辑区）。
     */
    private static boolean regionEquals(int[] a, int[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(int id) {
        for (int i = 0; i < BIOME_IDS.length; i++) {
            if (BIOME_IDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private static String shortId(int id) {
        return "id" + id;
    }

    private static void expectThrow(String what, Runnable construction) {
        try {
            construction.run();
        } catch (final IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("constructor validation missing for: " + what);
    }

    private static void fail(long seed, int ox, int oz, String message) {
        throw new AssertionError("GENLAYER FAIL seed=" + seed + " off=(" + ox + "," + oz + "): " + message);
    }
}
