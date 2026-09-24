package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * <b>P20 S1（需求 1）：群系交界表层材质混合带</b>——{@link GTSRChunkProviderBase.SurfaceTopSelector}
 * 的框架侧实现，消掉 selector 群系之间「一格换皮」的硬边。
 * <p>
 * ═══ 被消除的根因（{@code plan/tmp/wg41-B1-terrain-border.md} §2.3，主代理亲验）═══
 * 表层身份是<b>逐列单值</b>：{@code GTSRWorldChunkManager.getBiomeGenAt}（1:1 细层 Voronoi）→
 * {@code applyBiomeSurface} 的 {@code blocks[idx] = biome.topBlock}，式子里没有任何距离/权重项
 * ⇒ 相邻两列身份一换，top 方块实例即从 A 族跳到 B 族，<b>边界宽度恒 1 方块</b>。
 * 本类给该站点补上「按粗格距离加权 + 噪声扰动分界」的顶层皮肤选择，其余三段
 * （filler / 主体 / meta）一律不动。
 * <p>
 * <b>P22 A2b（G1）改写上句的"filler 不动"</b>：A2a 取证（{@code plan/tmp/p22-a2a/REPORT.md} §2）
 * 硬线读数——top 皮肤有 8-17 格渐变带，而 filler 在交界 p50=0.5 格单列硬换、0/5160 列例外。
 * filler 写格现经同一实例的 {@link #fillerAt} 走<b>同一条</b> (cell, frac, pick) 表达式
 * （{@link #pickTier}），皮肤与其下 1-2 格同源同档；meta 侧框架仍写本列群系的
 * {@code fillerMeta}，本类以 {@code FILLER_META} 常量等值门把关（现状五档全 0，见
 * {@link #fillerMetaConst}）。主体段（wholeBody）仍不动（v1.20.39 G4 起四群系统一石，无硬线）。
 * <p>
 * ═══ 技法来源与许可纪律（H-2）═══
 * 只取 RTG {@code LandscapeGenerator.setWeightings()} 的<b>范式</b>（距离加权交叉淡化 + 幂 0.7
 * 压平远端 + 线性截断 + Σ 归一，取证见 {@code plan/tmp/wg41-F-1710-reflibs.md} §2.1-A）：
 * <ul>
 * <li><b>不搬字节</b>：下列式子、核构造、缓存结构全部本仓新写（RTG 源码亦不在 {@code plan/clone}
 * 内，无从复制）；</li>
 * <li><b>不搬绝对值</b>（§13 C10）：RTG 的 {@code limit = pow(56²,0.7)} 配的是<b>8 格采样间距</b>
 * （{@code chunkCoordinate(mapX) = (mapX - sampleSize) * 8}）⇒ 它的「7」是<b>比值</b>
 * {@code 56 格 ÷ 8 格}。本仓粗格采样间距是 {@link GTSRGenLayerChain#COARSE_BLOCK_SCALE} = 4 方块
 * ⇒ 等价半径 = {@code 7 × 4 = 28 方块}（混合带全宽 2×28 = 56 方块），
 * <b>不是</b>照抄 56 格半径；又因 {@code pow((k·d)²,0.7) / pow((k·R)²,0.7) = pow(d²,0.7)/pow(R²,0.7)}
 * （间距 {@code k} 在比值里约掉），在<b>粗格单位</b>下分母取 {@code pow(7²,0.7)} 与上面的 28 方块
 * 口径逐字等价；</li>
 * <li>RTG 淡化的是<b>高度场</b>，本仓高度侧渐变已由 {@code ProsperityTerrainProfile.ampAt}
 * （半径 5 粗格 smoothstep 核）做完；本片补的是它<b>没有</b>覆盖的表层皮肤一面，两者同用
 * coarse 1:4 身份面 ⇒ 皮肤带与振幅带在同一批粗格上对齐（不会各滑各的）。</li>
 * </ul>
 * <p>
 * ═══ 形状（每 chunk 一个短命实例，零跨调用状态）═══
 * {@link #forChunk} 在 {@code applyBiomeSurface} 的列循环<b>之外</b>每 chunk 解析一次：
 * 取一张覆盖「本 chunk 的 4×4 中心粗格 ± 核半径」的 coarse 身份窗（1 条短命
 * {@link GTSRGenLayerChain} + 1 次 {@code coarseInts}，与 {@code GTSRGenLayerRosterFace.rosterIndexAt}
 * 同一条面、同一 {@code chainSeed} 口径），并为 16 个中心粗格各算一次核加权，得出
 * 「头名档 a / 次名档 b / 权重差 margin」。逐列只剩：1 次查表 + 1 次
 * {@link GTSRWorldgenHash#valueNoise} 抖动 + 一次比较。
 * <p>
 * <b>为什么按粗格缓存终值而不是逐列查核</b>：核中心 = 列所在粗格、核偏移固定 ⇒ 同一粗格内所有列
 * 的 (a, b, margin) 数学恒等（与 {@code ampAt} 的 P19 U8 改判同一凭据）。
 * <b>为什么不需要线程私有大缓存</b>：本对象的生命周期 = 一个 chunk，跨 chunk 状态为零 ⇒
 * 既无淘汰上限、也不引入「缓存反映首求值时点名册」的账本时点假设。
 * <p>
 * ═══ 降级与零影响凭据 ═══
 * <ol>
 * <li><b>dim79 零影响</b>：{@link #BORDER_BAND_DIM_KEYS} 只含
 * {@link GTSRBiomeAuthority#DIM_KEY_PROSPERITY} ⇒ dim79 走 {@code forChunk} 第一条判断即返
 * {@code null} ⇒ {@code applyBiomeSurface} 的 top 写格逐字退回改造前表达式（本片裁决「显式恒等回退」
 * 那一条；「dim79 无第二 selector」<b>不</b>成立——{@code shattered} 名册有 4 个 selector 成员，
 * 故必须靠白名单而不是靠巧合）。粗格窗取数、噪声、名册反查一律不执行。</li>
 * <li><b>身份不可得 / 未绑定</b>：链盐解析不到（{@link #chainSeedFor} 返回
 * {@link #NO_CHAIN_SEED}，即维度未注册或离线 harness 未把 def 登记进
 * {@link DimensionRegistrar}）、名册 selector 成员 &lt; 2、邻域单档 ⇒ 全部 {@code null}/直通。</li>
 * <li><b>列所在群系不是本维 selector 名册成员</b>（合成数组、外来群系、plains、sanzu 平面）⇒
 * 该列返回 {@code biome.topBlock} 原值 ⇒ 判据「无 plains/grass/dirt」与
 * 「返回值永远来自 {@code biome.topBlock} 候选集」同时成立。</li>
 * <li><b>meta</b>：dim78 五个群系（含名册第 5 元遗忘之川）{@code TOP_META} 全为 0 ⇒ 换皮不换 meta
 * 现状字节相同；另加 {@code field_150604_aj} 相等门，防未来非 0 top meta 群系被静默写错元数据。</li>
 * </ol>
 * <p>
 * ═══ 新增常量与 H-1（波长不得为 16 的倍数）═══
 * {@link #BORDER_KERNEL_RADIUS} = 7 粗格（半宽 28 方块：{@code 28 % 16 = 12}；半宽格数 {@code 7 % 16
 * = 7}；全宽 56 方块：{@code 56 % 16 = 8}）；{@link #BORDER_JITTER_SCALE} = 13（{@code 13 % 16 = 13}）。
 * 中心粗格格距是既存的 4 方块（{@code COARSE_BLOCK_SCALE}，非本片新增），且
 * {@code 16 = 4 × CELLS_PER_CHUNK} 整除 ⇒ 粗格格点 globally 对齐、不随 chunk 复位 ⇒
 * 不会产生 chunk 对齐条纹（账本 §7 的那类坑）。
 */
public final class GTSRSurfaceBorderBand implements GTSRChunkProviderBase.SurfaceTopSelector {

    /**
     * 启用表层混合带的维度白名单（<b>单点声明</b>）。
     * <p>
     * 需求 1 的范围是 dim78 的四个 selector 群系，dim79 本版<b>不</b>参与（裁决「dim79 零影响」）。
     * 本表是框架侧的临时落点：{@code SurfaceSpec} 的 8 参构造（{@code topSelector}）已就位，
     * provider 侧一旦自带选择器实参，本表清空即可（解析序见
     * {@code GTSRChunkProviderBase.applyBiomeSurface}，两条路不会同时生效）。本片写锁不含
     * {@code prosperity/ChunkProviderProsperityRuins.java}（归兄弟片），故实参暂无法搬到声明处。
     */
    private static final String[] BORDER_BAND_DIM_KEYS = { GTSRBiomeAuthority.DIM_KEY_PROSPERITY };

    /**
     * 混合带核半径，<b>粗格</b>单位（= 28 方块半宽 / 56 方块全宽）。
     * 依据档位：带出处 + §13 C10 换算（RTG 比值 {@code 56÷8=7} 格 × 本仓 4 方块格距）。
     */
    private static final int BORDER_KERNEL_RADIUS = 7;

    /** 域分离盐：表层混合带的抖动场（新域，与河流/城/振幅各域互不相关；ASCII "BORD"）。 */
    private static final long S_BORDER_JITTER = 0x424F5244L;

    /** 分界抖动噪声波长（方块）；取 13 而非 RTG 的 10——10 在本仓尺度下易碎且与 16 同族更近。 */
    private static final double BORDER_JITTER_SCALE = 13.0D;

    /** 分界抖动幅度（从 margin 上扣除，值域 [0, amp)，故切换阈实际在 0.55..0.70 间摆动）。 */
    private static final double BORDER_JITTER_AMPLITUDE = 0.15D;

    /**
     * 头名/次名切换阈。§13 已裁定为「本轮取 0.55，实机后校准」：RTG 的 0.8 是对噪声场而不是对
     * 权重差，不可直译，故本值是<b>范式映射</b>而非搬运。
     */
    private static final double BORDER_SWITCH_MARGIN = 0.55D;

    /** 权重幂（RTG 同形：{@code pow(d², 0.7)}，只改带内曲线形状，不改带宽）。 */
    private static final double BORDER_WEIGHT_POWER = 0.7D;

    /** 一个 chunk 内的中心粗格数（16 方块 ÷ {@code COARSE_BLOCK_SCALE}）。 */
    private static final int CELLS_PER_CHUNK = 16 >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;

    /** {@link #chainSeedFor} 的「解析不到域盐」哨兵（0 是合法盐值，不能用 0 当哨兵）。 */
    private static final long NO_CHAIN_SEED = Long.MIN_VALUE;

    /**
     * 紧支核格点与权重（{@code d² < r²}；{@code d = r} 处权重恰为 0，不入表——与
     * {@code ProsperityTerrainProfile.AMP_KERNEL} 同一截断纪律，但<b>独立表</b>：半径不同、
     * 权重形不同（幂 0.7 距离衰减 vs smoothstep），两者不可互相代用）。
     */
    private static final int[] KERNEL_DX;
    private static final int[] KERNEL_DZ;
    private static final double[] KERNEL_W;

    static {
        final int r = BORDER_KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        KERNEL_DX = new int[n];
        KERNEL_DZ = new int[n];
        KERNEL_W = new double[n];
        // §13 C10：分母 = pow(r², 0.7)（粗格单位；等价于 28 方块半径，见类注释的约掉证明）
        final double limit = Math.pow((double) rr, BORDER_WEIGHT_POWER);
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                double w = 1.0D - Math.pow((double) d2, BORDER_WEIGHT_POWER) / limit;
                if (w < 0.0D) {
                    w = 0.0D; // RTG 同式的线性截断（构造上 d2 < rr ⇒ 本行为防御，不静默吃负权）
                }
                KERNEL_DX[k] = i;
                KERNEL_DZ[k] = j;
                KERNEL_W[k] = w;
                wSum += w;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            KERNEL_W[k] /= wSum; // Σ 归一（RTG LandscapeGenerator:122-124 同纪律）
        }
    }

    /** biome id → 本维名册下标（{@code -1} = 非本维 selector 成员，含外来群系与 sanzu）。 */
    private final int[] rosterIndexOfBiomeId = new int[256];
    /** 名册下标 → 群系实例（取 top 方块与 top meta 用；未配槽为 null）。 */
    private final BiomeGenBase[] biomeByRosterIndex;
    /** 中心粗格（chunk 内 4×4）→ 头名档下标；{@code -1} = 邻域单档 ⇒ 直通。 */
    private final int[] headTier;
    /** 中心粗格 → 次名档下标（仅在 {@link #headTier} 非 -1 时有意义）。 */
    private final int[] secondTier;
    /** 中心粗格 → 归一权重差 {@code w[head] - w[second]} ∈ (0, 1]。 */
    private final double[] headMargin;
    /** 粗格身份窗（行主序，覆盖 chunk 的 4×4 中心格 ± 核半径）。 */
    private final byte[] cellTier;
    private final int windowSpan;
    private final int windowCellX0;
    private final int windowCellZ0;
    private final int chunkCellX0;
    private final int chunkCellZ0;

    private GTSRSurfaceBorderBand(BiomeGenBase[] biomeByRosterIndex, int rosterSize, int chunkCellX0, int chunkCellZ0) {
        this.biomeByRosterIndex = biomeByRosterIndex;
        // 未映射的 biome id 必须是 -1（"非本维 selector 成员"），不能被 int 数组默认的 0 误判成头名档
        java.util.Arrays.fill(this.rosterIndexOfBiomeId, -1);
        this.headTier = new int[CELLS_PER_CHUNK * CELLS_PER_CHUNK];
        this.secondTier = new int[this.headTier.length];
        this.headMargin = new double[this.headTier.length];
        this.chunkCellX0 = chunkCellX0;
        this.chunkCellZ0 = chunkCellZ0;
        this.windowCellX0 = chunkCellX0 - BORDER_KERNEL_RADIUS;
        this.windowCellZ0 = chunkCellZ0 - BORDER_KERNEL_RADIUS;
        this.windowSpan = 2 * BORDER_KERNEL_RADIUS + CELLS_PER_CHUNK;
        this.cellTier = new byte[this.windowSpan * this.windowSpan];
        java.util.Arrays.fill(this.cellTier, (byte) -1);
    }

    /** 名册账本 → 本实例的两张反查表（id → 档下标、档下标 → 群系实例）。 */
    private void fillRosterIndexMaps(String dimKey, GTSRBiomeAuthority authority) {
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (!dimKey.equals(key.dimKey()) || !key.inSelector()) {
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            if (biome == null || biome.biomeID < 0 || biome.biomeID >= this.rosterIndexOfBiomeId.length) {
                continue;
            }
            final int index = key.rosterIndex();
            if (index < 0 || index >= this.biomeByRosterIndex.length) {
                continue;
            }
            this.rosterIndexOfBiomeId[biome.biomeID] = index;
            this.biomeByRosterIndex[index] = biome;
        }
    }

    /**
     * 取一张覆盖「本 chunk 的 4×4 中心粗格 ± 核半径」的 coarse 身份窗并折算成名册下标。
     * <b>每 chunk 一条短命链</b>（与 {@code GTSRGenLayerRosterFace.rosterIndexAt} /
     * {@code CityPlanner.bandIndexAt} 同款纪律：零共享可变链实例、零跨 chunk 缓存）。
     */
    private void loadIdentityWindow(long chainSeed, int[] selectorIds) {
        final int[] windowIds = new GTSRGenLayerChain(chainSeed, selectorIds)
            .coarseInts(this.windowCellX0, this.windowCellZ0, this.windowSpan, this.windowSpan);
        // coarseInts 的出参数组前 width*height 项才是行主序窗口本体（vanilla IntCache 可能返回更长
        // 的数组，其尾部属父层裕量）——故按 cellTier 长度取数，不按 windowIds.length。
        for (int i = 0; i < this.cellTier.length && i < windowIds.length; i++) {
            final int id = windowIds[i];
            if (id >= 0 && id < this.rosterIndexOfBiomeId.length) {
                this.cellTier[i] = (byte) this.rosterIndexOfBiomeId[id];
            }
        }
    }

    /**
     * 本 chunk 的混合带选择器；返回 {@code null} = 本维/本状态不启用（调用方逐字退回
     * {@code biome.topBlock}）。
     *
     * @param dimKey    {@code SurfaceSpec.dimKey}（{@code null} = 匿名模板路径，不启用）
     * @param worldSeed 与表层内核同一个<b>未掺盐</b>世界种子（域盐在本方法内自行掺入）
     * @param baseX     chunk 原点世界 X（{@code chunkX * 16}）
     * @param baseZ     chunk 原点世界 Z
     */
    public static GTSRChunkProviderBase.SurfaceTopSelector forChunk(String dimKey, long worldSeed, int baseX,
        int baseZ) {
        if (!borderBandEnabled(dimKey)) {
            return null;
        }
        final long chainSeed = chainSeedFor(dimKey, worldSeed);
        if (chainSeed == NO_CHAIN_SEED) {
            return null;
        }
        final int[] selectorIds = GTSRGenLayerRosterFace.allocatedRosterIds(dimKey);
        if (selectorIds.length < 2) {
            // 单一（或零个）selector 成员 ⇒ 维内根本没有群系交界，混合带无对象可混。
            return null;
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(dimKey);
        final int rosterSize = authority.rosterSize();
        final int shift = GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final GTSRSurfaceBorderBand band = new GTSRSurfaceBorderBand(
            new BiomeGenBase[rosterSize],
            rosterSize,
            baseX >> shift,
            baseZ >> shift);
        band.fillRosterIndexMaps(dimKey, authority);
        band.loadIdentityWindow(chainSeed, selectorIds);
        band.resolveCenterCells(rosterSize);
        return band;
    }

    @Override
    public Block topAt(long worldSeed, int x, int z, BiomeGenBase biome) {
        final int pick = pickTier(worldSeed, x, z, rosterIndexOf(biome));
        if (pick < 0) {
            return biome.topBlock; // 无裁定（非成员/越格/腹地单档/胜者即本档）：逐字退回改造前表达式
        }
        final BiomeGenBase other = biomeByRosterIndex[pick];
        if (other == null || other.topBlock == null) {
            return biome.topBlock; // 该档未配槽（SHORT 降级）：不伪造皮肤
        }
        if (other.field_150604_aj != biome.field_150604_aj) {
            return biome.topBlock; // 换皮不换 meta 的显式门（现状四档 TOP_META 全 0，见类注释③）
        }
        return other.topBlock;
    }

    /**
     * P22 A2b（G1）：filler 段与 top <b>同一条</b>裁定（{@link #pickTier} 同一表达式、同一
     * (cell, frac, pick)），胜档的 {@code fillerBlock} 直接作为该列 1-2 格填充层的方块——
     * 消除 A2a 读数的 filler 硬线（p50=0.5 格单列切换、埋线列对 1.6/断面）。
     * <p>
     * <b>meta 纪律（与 top 的 {@code field_150604_aj} 门同族）</b>：框架侧 filler meta 恒按
     * <b>本列群系</b>查 {@code SurfaceSpec#fillerMeta}（provider 表＝身份→各群系 {@code FILLER_META}
     * 常量），故此处以两侧 <b>{@code FILLER_META} 常量等值</b>为门：常量读不到（非本维声明群系）
     * 或不相等 ⇒ 不改派。<b>现状凭据</b>：dim78 五档 {@code TOP_META}/{@code FILLER_META}
     * 全部为 0（{@code BiomeRustedSteppe}:29 / {@code BiomeGearworkForest}:29 /
     * {@code BiomeBrassWastes}:28 / {@code BiomeFumaroleSwamp}:28 / {@code BiomeSanzuRiver}:43），
     * 改派换料不换 meta 在现状字节相同。
     */
    @Override
    public Block fillerAt(long worldSeed, int x, int z, BiomeGenBase biome) {
        final int pick = pickTier(worldSeed, x, z, rosterIndexOf(biome));
        if (pick < 0) {
            return biome.fillerBlock;
        }
        final BiomeGenBase other = biomeByRosterIndex[pick];
        if (other == null || other.fillerBlock == null) {
            return biome.fillerBlock; // 该档未配槽（SHORT 降级）：不伪造填充层
        }
        final int mineMeta = fillerMetaConst(biome);
        if (mineMeta == UNKNOWN_FILLER_META || fillerMetaConst(other) != mineMeta) {
            return biome.fillerBlock; // 换料不换 meta 的显式门（见上方凭据）
        }
        return other.fillerBlock;
    }

    /** {@link #fillerMetaConst} 的"读不到常量"哨兵（负值 = 非本维声明群系，不得改派）。 */
    private static final int UNKNOWN_FILLER_META = -1;

    /** biome 类 → {@code FILLER_META} 常量（反射一次/类，实例生命周期 = 一个 chunk）。 */
    private final java.util.HashMap<Class<?>, Integer> fillerMetaCache = new java.util.HashMap<>();

    /**
     * 读群系<b>类</b>声明的 {@code FILLER_META} 常量（provider 侧 {@code fillerMetaOf} 查表的
     * 同源真值；读不到 = {@link #UNKNOWN_FILLER_META}，调用方按"不改派"处置）。
     */
    private int fillerMetaConst(BiomeGenBase biome) {
        final Class<?> cls = biome.getClass();
        final Integer hit = fillerMetaCache.get(cls);
        if (hit != null) {
            return hit.intValue();
        }
        int value = UNKNOWN_FILLER_META;
        try {
            value = cls.getField("FILLER_META")
                .getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 非本维声明群系（合成实例/未来子类缺常量）：保守直通，绝不猜 meta
        }
        fillerMetaCache.put(cls, Integer.valueOf(value));
        return value;
    }

    /**
     * 取胜者档下标（topAt 与 fillerAt 共用的<b>唯一</b>裁定式，P22 A2b G1 从 {@link #topAt}
     * 抽出 ⇒ 两条出口对同一列必然给出同一档，皮肤与其下填充层不再跨档）。
     *
     * @param mine 本列群系的名册下标（{@code rosterIndexOf} 结果）
     * @return 胜档下标；{@code -1} = 不改派（非成员 / 越出中心格 / 邻域单档 / 胜者即本档）
     */
    private int pickTier(long worldSeed, int x, int z, int mine) {
        if (mine < 0) {
            return -1; // 非本维 selector 成员：一格都不改派（合成数组/外来群系/sanzu）
        }
        final int shift = GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cx = (x >> shift) - chunkCellX0;
        final int cz = (z >> shift) - chunkCellZ0;
        if (cx < 0 || cx >= CELLS_PER_CHUNK || cz < 0 || cz >= CELLS_PER_CHUNK) {
            return -1; // 越出本 chunk 的 4×4 中心格（生产不可达，防御口径）
        }
        final int cell = cz * CELLS_PER_CHUNK + cx;
        final int head = headTier[cell];
        if (head < 0) {
            return -1; // 邻域单档 = 群系腹地，逐字退回改造前表达式
        }
        final double frac = headMargin[cell] - BORDER_JITTER_AMPLITUDE * GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_BORDER_JITTER, x / BORDER_JITTER_SCALE, z / BORDER_JITTER_SCALE);
        final int pick = frac > BORDER_SWITCH_MARGIN ? head : secondTier[cell];
        return pick == mine ? -1 : pick;
    }

    /** 名册下标 → 群系实例的只读出口（离线探针用；{@code null} = 该档未配槽）。 */
    public BiomeGenBase biomeAtTier(int rosterIndex) {
        return rosterIndex >= 0 && rosterIndex < biomeByRosterIndex.length ? biomeByRosterIndex[rosterIndex] : null;
    }

    /** 该列所在中心粗格的头名档（{@code -1} = 邻域单档＝不在混合带内）；探针只读口。 */
    public int headTierOfColumn(int x, int z) {
        final int shift = GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cx = (x >> shift) - chunkCellX0;
        final int cz = (z >> shift) - chunkCellZ0;
        if (cx < 0 || cx >= CELLS_PER_CHUNK || cz < 0 || cz >= CELLS_PER_CHUNK) {
            return -1;
        }
        return headTier[cz * CELLS_PER_CHUNK + cx];
    }

    // —————————————————————————— 内部 ——————————————————————————

    /**
     * 16 个中心粗格各算一次核加权：档值累加 → Σ 归一 → 头名 a / 次名 b / margin。
     * 并列头名按<b>档下标升序</b>取先（{@code >} 严格比较）⇒ 平权时 a 为下标小者、b 为另一家，
     * margin 恰为 0，切换由抖动场裁定（确定性、跨 chunk 一致）。
     */
    private void resolveCenterCells(int rosterSize) {
        final double[] weight = new double[rosterSize];
        for (int cz = 0; cz < CELLS_PER_CHUNK; cz++) {
            for (int cx = 0; cx < CELLS_PER_CHUNK; cx++) {
                java.util.Arrays.fill(weight, 0.0D);
                double total = 0.0D;
                int distinct = 0;
                for (int k = 0; k < KERNEL_DX.length; k++) {
                    final int tier = cellTier[(BORDER_KERNEL_RADIUS + cx + KERNEL_DX[k]) * windowSpan
                        + (BORDER_KERNEL_RADIUS + cz + KERNEL_DZ[k])];
                    if (tier < 0) {
                        continue; // 窗内无身份格（理论不可达）：不参与，也不计进 Σ
                    }
                    if (weight[tier] == 0.0D) {
                        distinct++;
                    }
                    weight[tier] += KERNEL_W[k];
                    total += KERNEL_W[k];
                }
                final int cell = cz * CELLS_PER_CHUNK + cx;
                if (distinct < 2 || total <= 0.0D) {
                    headTier[cell] = -1;
                    secondTier[cell] = -1;
                    headMargin[cell] = 0.0D;
                    continue;
                }
                int head = -1;
                int second = -1;
                for (int i = 0; i < rosterSize; i++) {
                    if (weight[i] <= 0.0D) {
                        continue;
                    }
                    if (head < 0 || weight[i] > weight[head]) {
                        head = i;
                    }
                }
                for (int i = 0; i < rosterSize; i++) {
                    if (i == head || weight[i] <= 0.0D) {
                        continue;
                    }
                    if (second < 0 || weight[i] > weight[second]) {
                        second = i;
                    }
                }
                headTier[cell] = head;
                secondTier[cell] = second;
                headMargin[cell] = (weight[head] - (second < 0 ? 0.0D : weight[second])) / total;
            }
        }
    }

    /** 列所在群系 → 本维 selector 名册下标；{@code -1} = 不在此名册（不得被混合带改派）。 */
    private int rosterIndexOf(BiomeGenBase biome) {
        final int id = biome.biomeID;
        return id >= 0 && id < rosterIndexOfBiomeId.length ? rosterIndexOfBiomeId[id] : -1;
    }

    private static boolean borderBandEnabled(String dimKey) {
        if (dimKey == null) {
            return false;
        }
        for (final String enabled : BORDER_BAND_DIM_KEYS) {
            if (dimKey.equals(enabled)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code worldSeed ^ def.seedSalt}（与 {@code GTSRWorldChunkManager} 构链、
     * {@code GTSRGenLayerRosterFace.rosterIndexAt} 逐字同一条身份面的种子）。
     * <p>
     * <b>盐值单一真值</b>：只从 {@link DimensionRegistrar#defForDimension} 取
     * {@code def.getSeedSalt()}，本类<b>不</b>再落一份字面量（dim78 链盐的既有三处字面量现状不扩大，
     * 见 {@code ProsperityTerrainProfile.CHAIN_SEED_SALT} 注释）。dimId 来自 L1 账本的
     * {@code boundDimId()}（manager 构造期 {@code bind} 写入，与 {@code WorldProviderBase}
     * 回查 def 同一条路）；解析不到（维度未注册 / 离线 harness 未登记 def / 未绑定）⇒
     * {@link #NO_CHAIN_SEED}，调用方按「不启用」处理。
     */
    private static long chainSeedFor(String dimKey, long worldSeed) {
        final int dimId = GTSRBiomeAuthority.forDimKey(dimKey)
            .boundDimId();
        if (dimId < 0) {
            return NO_CHAIN_SEED;
        }
        final GTSRDimensionDef def = DimensionRegistrar.defForDimension(dimId);
        return def == null ? NO_CHAIN_SEED : worldSeed ^ def.getSeedSalt();
    }
}
