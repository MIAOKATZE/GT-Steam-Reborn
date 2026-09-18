package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import static com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase.findSurfaceY;

import java.util.Random;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.config.Config;

/**
 * 地表人工痕迹散布器（dim1 S4a，plan §1.2 S4a / 02 §3.2 代码 5 裁剪版）：
 * 散布物件型 = 轨枕 / 废弃管道段 / 铆接板 / （可选）烟囱残段（02 §3.1），落点只落在锈变地表
 * （<b>P4 起直调框架单一谓词 {@link SurfaceGate}</b>：dim78 声明集 = 四自然 top 方块族 ∪
 * prosperitySurface——S-A1 连带放宽，A1 主体换装后自然区 top 为群系新方块；02 §3.2 门。
 * 改造前本类横向直调 {@code ProsperityOutpostPlacer} 的内部谓词（审计 A-2 #3），本处不再互调；
 * {@code RuinedMachinePlacer:106} 的同一处横向直调不在本片允许路径内，残留归 P7）。烟囱残段逐格
 * {@code isAirBlock} 让行（只长在空气里，02 §8.4 优先级 4）。
 * <p>
 * 所有随机从 chunk 确定性哈希派生（盐 "ScAt" 0x5C4174，02 §3.2 原样）；放置经注入的
 * {@link BlockSink}（ChunkClampedSink 钳制），散布落点/短线段钳制在 chunk 内——零越界丢弃。
 * <p>
 * <b>P3（plan §5 P3 / 审计 A-5 §1 #2）</b>：本类原有的私有 {@code findSurfaceY(World,int,int)}
 * 是 dim78/dim79 内 <b>5 份逐字符等价</b>列扫之一（当时的"基准"份），现已并入框架唯一件
 * {@link com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase#findSurfaceY}
 * ——本文件以静态导入引用，故 {@code findSurfaceY(world, x, z)} 调用点一字未改，
 * 接地 y 取值口径（自上而下第一处非空气、兜底 -1）逐位不变。
 * <p>
 * <b>P5（plan §5 P5 / §2.2 H-2·H-3 / §7.1 已锁定 U3「中道 K + 摘竖向件」）——S2 柱阵的直接消解</b>：
 * <ol>
 * <li><b>受控量换口径</b>：改造前的 {@code private static final BUDGET_PER_CHUNK = 64} 是
 * <b>掷点次数</b>（块数口径），且不可 Config 回退（违反 plan §2.3 判据 5）。现主受控量是
 * <b>每 chunk 件数 K</b>（{@link Config#prosperityScatterContoursPerChunk}）+ 每 chunk 落块上限
 * （{@link Config#prosperityScatterBlocksPerChunk}）双天花板；掷点上限
 * （{@link Config#prosperityScatterAttemptsPerChunk}）退化为"随机流终止/兼容"口径，
 * 单独不再构成密度。一件 = 至少落了 1 块的一次散布事件（让行失败的掷点不计件，
 * 但仍消耗随机数——与改造前逐位一致）。</li>
 * <li><b>竖向件从源头摘出</b>（{@link Config#prosperityScatterVerticalPieces}，默认 false）：
 * 改造前权重 {30,25,30,<b>15</b>} 的 15% 是 2-5 格高烟囱残段，实测 8.8-10.2 柱/chunk
 * （解析式 0.15×门后可用=8.76 精确吻合）＝用户截图里那"一根根小柱"。摘出只作用于<b>散布层</b>：
 * 烟囱素材不作废——城内 {@code chimney_stack}（CityVariants 的 'c' 键 meta3）与残骸机器
 * {@code chimney_base}（RuinedMachineShapes）都不经本开关。<b>本动作与 K 取值无关</b>。</li>
 * <li><b>H-2 窗重复上限</b>（{@link Config#prosperityScatterWindowRepeatCap}，默认 2 = U3「柱 ≤2/窗」）：
 * 每 {@value #WINDOW_CHUNKS}×{@value #WINDOW_CHUNKS} chunk 窗内，同一模板允许<b>发射</b>的 chunk 数上限。
 * 实现为纯函数 {@link #windowAllows}（窗内槽位哈希排序 + 名额判定），跨 chunk 无需共享状态且各 chunk
 * 独立重算得同一结论（plan §2.3 判据 1）。本片刻度只挂竖向件；平面件型与结构模板的窗上限归
 * P7 {@code PlacementGate}（plan §4 横切件），故 {@link #windowCapFor(int)} 对平面件返回 0=关闭。</li>
 * </ol>
 * <b>回退</b>（plan §2.3 判据 5，四键见 {@link Config} 的 P5 段注释）：竖向件开关=true +
 * 件数=64 + 落块=0 + 窗上限=0 ⇒ 掷点上限与权重表均为改造前原值 ⇒ 散布层行为<b>逐位</b>复现柱阵态
 * （证据：{@code tools/dim1/Dim78ScatterDensityCheck} 的 BASE/rollback 落块摘要对拍）。
 */
public final class ProsperitySurfaceScatter {

    /** 件型索引 = 权重表下标（02 §3.1 顺序：轨枕/管道/铆接板/烟囱残段）。 */
    public static final int KIND_SLEEPER = 0;

    /** 见 {@link #KIND_SLEEPER}。 */
    public static final int KIND_PIPE = 1;

    /** 见 {@link #KIND_SLEEPER}。 */
    public static final int KIND_RIVET_PLATE = 2;

    /** 见 {@link #KIND_SLEEPER}（竖向件，受 H-2 窗上限与 {@link Config#prosperityScatterVerticalPieces} 双重控制）。 */
    public static final int KIND_CHIMNEY = 3;

    /** H-2 区域窗边长（chunk 数）：16×16 chunk = 256×256 格（plan §2.2 H-2 的尺度定义）。 */
    public static final int WINDOW_CHUNKS = 16;

    /** 盐 "ScAt"（02 §3.2 代码 5 同款）。 */
    private static final long SALT_SCATTER = 0x5C4174L;

    /** 盐 "WnC"（P5 H-2 窗槽位排序专用；与 "ScAt" 隔离，避免同 chunk 的件型掷选与窗排序同相关）。 */
    private static final long SALT_WINDOW_RANK = 0x576E43L;

    /** 本类所属维度键（P4：门的显式维度入参，取 L1 账本同一词汇，不另造字符串）。 */
    private static final String DIM_KEY = SurfaceGate.DIM78;

    private ProsperitySurfaceScatter() {}

    /**
     * populate 入口。<b>签名与改造前逐字相同</b>（{@code tools/dim1/SurfaceGateUnifyCheck} 的编排链
     * 采样按本签名调用）；预算/权重/上限全部在方法内从 {@link Config} 取值。
     *
     * @param biomeScatterWeight 群系散布权重（02 §1.1：草原 1.2/森林 1.0/荒漠 1.5/沼泽 1.1）
     */
    public static void scatter(World world, long worldSeed, int cx, int cz, float biomeScatterWeight, BlockSink sink) {
        if (sink == null || biomeScatterWeight <= 0F) {
            return;
        }
        // 权重表（P5：Config 化）。竖向件关闭时其权重强制为 0 ⇒ 下标 3 在 pickWeighted 里不可达。
        final int[] weights = weightedTable();
        if (weights == null) {
            return; // 四项权重全 0 = 散布层关闭（plan §2.3 判据 5 的单值回退口径之一）
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_SCATTER);
        final int attempts = (int) (Config.prosperityScatterAttemptsPerChunk * biomeScatterWeight);
        final int contourCap = scaledByWeight(Config.prosperityScatterContoursPerChunk, biomeScatterWeight);
        final int blockCap = scaledByWeight(Config.prosperityScatterBlocksPerChunk, biomeScatterWeight);
        final StructureBuilder builder = new StructureBuilder(sink);
        int contours = 0;
        int blocks = 0;
        for (int i = 0; i < attempts; i++) {
            // H-3 双天花板（0 = 关闭对应上限）：都在掷点前判，故不消耗随机数
            if (contourCap > 0 && contours >= contourCap) {
                break;
            }
            if (blockCap > 0 && blocks >= blockCap) {
                break;
            }
            final int x = (cx << 4) + rand.nextInt(16);
            final int z = (cz << 4) + rand.nextInt(16);
            final int surfaceY = findSurfaceY(world, x, z); // WorldGenRunawaySingularity.java:82-91 范式
            if (surfaceY <= 0 || surfaceY > 200) {
                continue;
            }
            // 只散布在锈变地表上（02 §3.2 代码 5 门；P4 起走框架单一谓词，集合见 SurfaceGate
            // roster 表：四自然 top ∪ prosperitySurface）
            if (!SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY),
                world.getBlock(x, surfaceY, z))) {
                continue;
            }
            final int y = surfaceY + 1;
            final int placed;
            switch (pickWeighted(rand, weights)) {
                case KIND_SLEEPER:
                    placed = placeSleeper(builder, world, x, y, z);
                    break;
                case KIND_PIPE:
                    placed = placePipeRun(builder, world, x, y, z, rand, cx, cz);
                    break;
                case KIND_RIVET_PLATE:
                    placed = placeRivetPlate(builder, world, x, y, z);
                    break;
                case KIND_CHIMNEY:
                    placed = windowAllows(worldSeed, cx, cz, KIND_CHIMNEY,
                        windowCapFor(KIND_CHIMNEY)) ? placeChimneyStub(builder, world, x, y, z, rand) : 0;
                    break;
                default:
                    placed = 0;
            }
            if (placed > 0) {
                contours++;
                blocks += placed;
            }
        }
    }

    /**
     * 权重表（02 §3.1 四项，下标 = {@link #KIND_SLEEPER}..{@link #KIND_CHIMNEY}；P5 起取自 Config）。
     *
     * @return 四项权重（负值按 0 处理）；四项之和 ≤ 0 时返回 {@code null} = 散布层关闭
     */
    private static int[] weightedTable() {
        final int sleeper = Math.max(0, Config.prosperityScatterWeightSleeper);
        final int pipe = Math.max(0, Config.prosperityScatterWeightPipe);
        final int rivet = Math.max(0, Config.prosperityScatterWeightRivetPlate);
        // 竖向件开关：关闭 ⇒ 权重强制 0 ⇒ pickWeighted 的下标 3 不可达（权重和不变的那部分照旧）
        final int chimney = Config.prosperityScatterVerticalPieces ? Math.max(0, Config.prosperityScatterWeightChimney)
            : 0;
        return sleeper + pipe + rivet + chimney > 0 ? new int[] { sleeper, pipe, rivet, chimney } : null;
    }

    /** 上限折算：{@code round(base×群系权重)}；base ≤ 0 ⇒ 0 = 关闭该上限（不做折算）。 */
    private static int scaledByWeight(int base, float biomeScatterWeight) {
        return base <= 0 ? 0 : Math.max(1, Math.round(base * biomeScatterWeight));
    }

    /**
     * 本层实际生效的 H-2 窗上限（按件型）。P5 只挂竖向件（plan §7.1 U3「柱 ≤2/窗」）；
     * 平面件型返回 0 = 关闭——结构/平面模板的窗重复上限是 P7 {@code PlacementGate} 的主对象，
     * 在本片用同一个数字去削平面散布会把"痕迹"削成光地（实测见类注释与 plan/investigation/p5-*）。
     * <p>
     * 公开只为离线断言（{@code tools/dim1/RegionRepeatCapCheck} 要把"哪个件型挂了上限"钉成申报项，
     * 防止后续片顺手改口径），生产侧唯一调用者是本类的散布主循环。
     */
    public static int windowCapFor(int kind) {
        return kind == KIND_CHIMNEY ? Config.prosperityScatterWindowRepeatCap : 0;
    }

    /**
     * H-2 判据：给定 16×16 chunk 窗（chunk 坐标按 {@value #WINDOW_CHUNKS} 对齐，负坐标 floorDiv 安全），
     * 同一模板在本窗内的<b>发射名额</b>是否还轮到自己。
     * <p>
     * 纯函数、无共享状态：窗内 256 个槽位各取一个确定性哈希，本槽按"哈希序"排名，
     * 只有前 {@code cap} 名允许发射 ⇒ <b>任何窗内任何模板的发射 chunk 数硬上界 = cap</b>
     * （不是统计意义上的稀疏化，故可被 {@code tools/dim1/RegionRepeatCapCheck} 钉死）。
     * 哈希一律走框架唯一件 {@link GTSRWorldgenHash}（plan §2.1 L2「禁止手搓哈希」）。
     *
     * @param cap ≤ 0（关闭）或 ≥ 窗内槽位数（结构上无法约束）时直接放行
     */
    public static boolean windowAllows(long worldSeed, int cx, int cz, int template, int cap) {
        if (cap <= 0 || cap >= WINDOW_CHUNKS * WINDOW_CHUNKS) {
            return true;
        }
        final long mine = windowSlotHash(worldSeed, cx, cz, template);
        final int baseX = Math.floorDiv(cx, WINDOW_CHUNKS) * WINDOW_CHUNKS;
        final int baseZ = Math.floorDiv(cz, WINDOW_CHUNKS) * WINDOW_CHUNKS;
        int rank = 0;
        for (int lx = 0; lx < WINDOW_CHUNKS; lx++) {
            for (int lz = 0; lz < WINDOW_CHUNKS; lz++) {
                final int sx = baseX + lx;
                final int sz = baseZ + lz;
                final long h = windowSlotHash(worldSeed, sx, sz, template);
                // 哈希相等（概率可忽略）时按槽位坐标定序，保证排名是全序且各槽自算一致
                if (h < mine || (h == mine && (sx < cx || (sx == cx && sz < cz)))) {
                    if (++rank >= cap) {
                        return false; // 名额已被更靠前的 cap 个槽位占满（早退，热路径成本 O(cap)）
                    }
                }
            }
        }
        return true;
    }

    /** 窗槽位哈希（盐隔离 + 模板隔离 + splitmix64 终结；同一 (seed,cx,cz,template) 恒同值）。 */
    private static long windowSlotHash(long worldSeed, int cx, int cz, int template) {
        return GTSRWorldgenHash.splitmix64(
            GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ (SALT_WINDOW_RANK * (template + 1L)));
    }

    /**
     * 轨枕：单方块 meta0（占位让行：只落空气位，02 §8.4 优先级 4）。
     *
     * @return 本次散布事件被 Sink 接受的落块数（P5 件数/落块上限计数用；0 = 让行未落）
     */
    private static int placeSleeper(StructureBuilder builder, World world, int x, int y, int z) {
        if (!world.isAirBlock(x, y, z)) {
            return 0;
        }
        return builder.setBlock(x, y, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.SLEEPER, BlockSink.FLAG_POPULATE)
            ? 1
            : 0;
    }

    /** 废弃管道段：1-3 连直排（哈希方向 ±X/±Z），段内逐格空气让行，越 chunk 截断（零越界丢弃）。 */
    private static int placePipeRun(StructureBuilder builder, World world, int x, int y, int z, Random rand, int cx,
        int cz) {
        final boolean alongX = rand.nextBoolean();
        final int dir = rand.nextBoolean() ? 1 : -1;
        final int local = alongX ? (x - (cx << 4)) : (z - (cz << 4));
        // 段长上限收到 chunk 边界（dir=+1 时留界内余量，dir=-1 时退格数即余量）
        final int free = dir > 0 ? 15 - local : local;
        final int length = Math.min(1 + rand.nextInt(3), Math.max(1, free + 1));
        int placed = 0;
        for (int i = 0; i < length; i++) {
            final int wx = alongX ? x + dir * i : x;
            final int wz = alongX ? z : z + dir * i;
            if (!world.isAirBlock(wx, y, wz)) {
                break; // 让行规则：只长在空气里（02 §3.2）
            }
            if (builder.setBlock(wx, y, wz, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.PIPE,
                BlockSink.FLAG_POPULATE)) {
                placed++;
            }
        }
        return placed;
    }

    /** 铆接板：单方块 meta2（平铺姿态，占位让行）。 */
    private static int placeRivetPlate(StructureBuilder builder, World world, int x, int y, int z) {
        if (!world.isAirBlock(x, y, z)) {
            return 0;
        }
        return builder.setBlock(x, y, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.RIVET_PLATE,
            BlockSink.FLAG_POPULATE) ? 1 : 0;
    }

    /**
     * 烟囱残段：2-5 高残塔（逐格空气让行），1/3 概率顶部破口换铆接板（02 §3.2 代码 5 原样）。
     * <p>
     * <b>P5</b>：本件 = 用户截图里的"柱"。默认不再被掷到（{@link Config#prosperityScatterVerticalPieces}
     * =false ⇒ 权重 0 ⇒ 下标 3 不可达）；即便重新打开，也要先过 {@link #windowAllows} 的窗名额。
     * 实现体与掷点次数（height / 顶部破口的 nextInt）保持改造前逐字一致，以便回退口径可复现。
     */
    private static int placeChimneyStub(StructureBuilder builder, World world, int x, int y, int z, Random rand) {
        final int height = 2 + rand.nextInt(4); // 2-5 残塔
        int placed = 0;
        for (int i = 0; i < height; i++) {
            if (!world.isAirBlock(x, y + i, z)) {
                break; // 让行规则：只长在空气里
            }
            if (builder.setBlock(x, y + i, z, BlocksGTSR.ruinDebris, BlockRuinDebrisMeta.CHIMNEY,
                BlockSink.FLAG_POPULATE)) {
                placed++;
            }
        }
        if (placed > 0 && rand.nextInt(3) == 0) { // 顶部破口掉半块
            if (builder.setBlock(
                x,
                y + placed - 1,
                z,
                BlocksGTSR.ruinDebris,
                BlockRuinDebrisMeta.RIVET_PLATE,
                BlockSink.FLAG_POPULATE)) {
                placed++;
            }
        }
        return placed;
    }

    /** 权重选取（02 §3.2 pickWeighted 原样）。 */
    private static int pickWeighted(Random rand, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = rand.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            if (roll < weights[i]) {
                return i;
            }
            roll -= weights[i];
        }
        return 0;
    }

    /** BlockRuinDebris meta 常量引用（避免反向依赖 S2 方块类名在散布层扩散）。 */
    private static final class BlockRuinDebrisMeta {

        static final int SLEEPER = 0;
        static final int PIPE = 1;
        static final int RIVET_PLATE = 2;
        static final int CHIMNEY = 3;
    }
}
