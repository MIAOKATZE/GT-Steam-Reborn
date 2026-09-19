package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.config.Config;

/**
 * 结构放置契约（<b>P7，plan §4 横切件 / §5 P7 / §2.2 H-2·H-3 / §2.1 L5 禁止项</b>）：
 * 把"每 chunk 结构预算 + 同族互斥 + H-2 窗重复上限 + 就绪门 + 落块真值"收成 placer 族的<b>唯一入口</b>。
 * <p>
 * ═══ 本类持有什么、不持有什么（单一真值边界）═══
 * <ol>
 * <li><b>持有</b>：族常量（{@link #FAMILY_MACHINE}/{@link #FAMILY_OUTPOST}/{@link #FAMILY_CITY}）、
 * 就绪门的 y 可落带（{@link #LANDING_Y_MIN}/{@link #LANDING_Y_MAX}——改造前机器与 outpost 各写一份
 * 同值字面量，即 plan §2.4 判据 4 的"同一数值多处漂移"面）、族模板 id 派生规则
 * （{@link #templateIdOf(String)}）、预算与上限的<b>语义</b>；</li>
 * <li><b>不持有数字</b>：所有可调数值一律取 {@link Config}（{@code prosperityStructureBudgetPerChunk} /
 * {@code prosperityStructureWindowRepeatCap} / 各族分母），本类不写任何密度字面量（plan §2.3 判据 5
 * 的单值回退位因此仍在 Config 一处）；</li>
 * <li><b>不持有地表门成员集合</b>：就绪门的"顶块是否可落地"仍由 P4 的框架单一谓词
 * {@code SurfaceGate} 判定，placer 把<b>结论</b>（boolean）交给 {@link #readyAt(int, boolean)}。
 * 本类不自持任何方块成员比较（{@code tools/dim1/SurfaceGateUnifyCheck} E 组"全仓自造门残留 == 0"
 * 对本类同样成立）；</li>
 * <li><b>不重复实现 H-2 窗名额判定</b>：窗内槽位哈希排序的唯一实现是 P5 已交付的
 * {@link ProsperitySurfaceScatter#windowAllows(long, int, int, int, int)}（含唯一盐 "WnC"）。
 * 本片只把它<b>泛化</b>到 placer 族（族模板 id 走 {@link #FAMILY_TEMPLATE_ID_BASE} 偏移段，与散布
 * 件型 0..3 隔离），故散布侧与本侧对同一 (seed,cx,cz,template) 必然同结论——由
 * {@code tools/dim1/PlacementContractCheck} A5 组逐点钉住。
 * 该"框架件引用内容件"的过渡依赖登记为未闭合项（P13 删旧生成路径时把窗判定上收至 framework）；
 * 同一性质的还有 {@link #groundFn(long)} 对 {@code ProsperityTerrainProfile.heightAt} 的直引
 * （dim79 的结构侧目前不经本门，理由见该方法注释）。</li>
 * </ol>
 * <p>
 * ═══ 用法（结构侧三步）═══
 * 
 * <pre>
 * PlacementGate.ChunkGate gate = PlacementGate.beginChunk(SurfaceGate.DIM78, worldSeed, cx, cz); // 编排器每 chunk 一个
 * Permit p = placer 内 gate.request(entry);            // 预算 / 互斥 / H-2 窗上限（一道门，含掷骰前置短路）
 * if (p == null) return false;
 * ... 取逐列 heightAt 接地、跑就绪门 PlacementGate.readyAt(...) ...
 * CountingSink counter = PlacementGate.counting(sink); // 包住真实写入
 * place(new StructureBuilder(counter), ...);
 * return p.commit(counter.solid());                    // 落块 0 块 ⇒ 不计成功、不扣预算
 * </pre>
 * <p>
 * 线程性：与 {@link ChunkClampedSink} 同口径——populate 单线程逐 chunk，但多维度可并发，故
 * {@link ChunkGate} 是<b>每 chunk 局部对象</b>（由编排器创建并传递），本类不持有任何跨 chunk 可变状态；
 * H-2 判定是纯函数重算，跨 chunk 各槽自算必得同一结论（plan §2.3 判据 1）。
 */
public final class PlacementGate {

    /** 族：残缺机器（{@code RuinedMachineShapes} 5 机型）。 */
    public static final String FAMILY_MACHINE = "machine";

    /** 族：城外中型废墟（{@code ProsperityOutpostPlacer} 6 变体）。 */
    public static final String FAMILY_OUTPOST = "outpost";

    /** 族：城内地块（{@code CityVariants} 26 变体；密度由城窗 H-1/L6 决定，本片不经本门）。 */
    public static final String FAMILY_CITY = "city";

    /**
     * 族：未入册条目（{@link StructureRegistry.Entry} 的五参构造默认值）。
     * <b>不参与同族互斥</b>（否则未标注的条目会互相吞掉），只受每 chunk 预算约束。
     */
    public static final String FAMILY_UNSCOPED = "unscoped";

    /** 就绪门：结构可落的地表 y 下界（改造前 {@code < 20} 字面量的唯一去处）。 */
    public static final int LANDING_Y_MIN = 20;

    /** 就绪门：结构可落的地表 y 上界（改造前 {@code > 200} 字面量的唯一去处）。 */
    public static final int LANDING_Y_MAX = 200;

    /**
     * 族模板 id 基准偏移：散布件型占 0..3（{@code ProsperitySurfaceScatter.KIND_*}），
     * placer 族模板一律 ≥ 本值，两段永不重合。
     */
    static final int FAMILY_TEMPLATE_ID_BASE = 1000;

    /** 族模板 id 取模域（{@link String#hashCode()} 稳定，名册增员不移动既有条目的 id）。 */
    private static final int FAMILY_TEMPLATE_ID_SPAN = 1 << 20;

    /** 每 chunk 结构预算"不限"哨兵（= Config 键取 0）。 */
    public static final int BUDGET_UNLIMITED = 0;

    private PlacementGate() {}

    // ═══════════════════════════════ 纯函数门（无状态）═══════════════════════════════

    /**
     * 就绪门（L5 结构侧唯一入口）：接地 y 落在可落带内 <b>且</b> 该列顶块过 P4 单一地表门。
     * 两个条件都在这里判，是为了让"门通过但还没落块"这一步在各族之间不可分叉（审计 S2 次要缺陷）。
     *
     * @param surfaceTopLandable 由调用方经 {@code SurfaceGate}（或其一行委托别名）算出的顶块结论
     */
    public static boolean readyAt(int surfaceY, boolean surfaceTopLandable) {
        return surfaceY >= LANDING_Y_MIN && surfaceY <= LANDING_Y_MAX && surfaceTopLandable;
    }

    /** 空气句柄判定（离线 String 键与游戏内 {@code Blocks.air} 同一口径）。 */
    public static boolean isAirHandle(Object block) {
        return block == null || block == Blocks.air || "minecraft:air".equals(String.valueOf(block));
    }

    /**
     * 结构模板的 H-2 窗名额判定（<b>委托</b> P5 唯一实现，见类注释第 4 点）。
     *
     * @param cap ≤ 0 = 关闭（直接放行，回退位）
     */
    public static boolean windowAllowsFor(long worldSeed, int cx, int cz, String templateName, int cap) {
        if (cap <= BUDGET_UNLIMITED) {
            return true;
        }
        return ProsperitySurfaceScatter
            .windowAllows(worldSeed, cx, cz, templateIdOf(templateName), cap);
    }

    /** 族模板 id（散布件型段之上；{@link String#hashCode()} 由 JLS 钉死，跨 JVM/版本稳定）。 */
    public static int templateIdOf(String templateName) {
        return FAMILY_TEMPLATE_ID_BASE + Math.floorMod(templateName.hashCode(), FAMILY_TEMPLATE_ID_SPAN);
    }

    /**
     * 生效的每窗重复上限：{@code entry.windowRepeatCap > 0}（roster 显式覆写）优先，否则取 Config 族键。
     * <p>
     * {@code Entry} 里的"放置分母 / 每窗上限"两字段一律允许 <b>0 = 跟随 Config 族键</b>，本片两条既有族
     * （machine/outpost）都取 0 ⇒ 树内不存在第二处数字真值；P8 的废墟族若需要"逐模板不同概率"，
     * 才在 roster 里写非 0 值（那时 roster 就是唯一出处，Config 只留族级默认）。
     */
    public static int effectiveWindowRepeatCap(StructureRegistry.Entry entry) {
        return entry.windowRepeatCap > 0 ? entry.windowRepeatCap : Config.prosperityStructureWindowRepeatCap;
    }

    // ═══════════════════════════════ 每 chunk 门（局部对象）═══════════════════════════════

    /**
     * 本 chunk 的结构放置门（由编排器 {@code ProsperityWorldGenerator.generate} 每 chunk 创建一个）。
     * 持"已真实落块的结构数 + 已占用族"两份本地计数，跨 chunk 不共享（H-3 的尺度就是单 chunk）。
     */
    public static final class ChunkGate {

        private final String dimKey;
        private final long worldSeed;
        private final int chunkX;
        private final int chunkZ;
        private final Set<String> occupiedFamilies = new HashSet<>();
        /**
         * 本 chunk 内<b>被放行</b>的模板名（按放行顺序）。只读用途是给离线断言
         * （{@code tools/dim1/PlacementContractCheck}）区分"被预算/互斥拒"与"被窗上限拒"；
         * 生产链不读它，故不构成分支或第二真值。
         */
        private final List<String> grantedTemplates = new ArrayList<>();
        private int committed;

        ChunkGate(String dimKey, long worldSeed, int chunkX, int chunkZ) {
            this.dimKey = dimKey;
            this.worldSeed = worldSeed;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        public String dimKey() {
            return this.dimKey;
        }

        public long worldSeed() {
            return this.worldSeed;
        }

        public int chunkX() {
            return this.chunkX;
        }

        public int chunkZ() {
            return this.chunkZ;
        }

        /** 本 chunk 已真实落块的结构数（只由 {@link Permit#commit(int)} 递增）。 */
        public int committed() {
            return this.committed;
        }

        /** 本 chunk 已被放行的模板名（只读快照；见 {@link #grantedTemplates} 的用途说明）。 */
        public List<String> requestedTemplates() {
            return new ArrayList<>(this.grantedTemplates);
        }

        /**
         * 请求放置许可（族 + 模板名口径；placer 侧的唯一用法，不需要先在 {@link StructureRegistry}
         * 里有条目，故离线驱动与注册顺序都影响不到门的行为）。
         * 依次过 <b>每 chunk 预算 → 同族互斥 → H-2 窗重复上限</b>；
         * 返回 {@code null} = 不放行（调用方必须直接放弃，且<b>不得</b>扣任何预算）。
         * <p>
         * <b>两个 {@code request} 重载共用本方法</b>：预算读取点与判定顺序因此在全类里各只有一处
         * （plan §2.4 判据 4——同一判定写两遍就是"两处会各自漂移的真值"，
         * {@code tools/dim1/PlacementContractCheck} D3 按这个口径钉计数）。
         */
        private Permit request0(String family, String templateName, int windowRepeatCap) {
            final int budget = Config.prosperityStructureBudgetPerChunk;
            if (budget > BUDGET_UNLIMITED && this.committed >= budget) {
                return null;
            }
            if (!FAMILY_UNSCOPED.equals(family) && this.occupiedFamilies.contains(family)) {
                return null;
            }
            if (!windowAllowsFor(this.worldSeed, this.chunkX, this.chunkZ, templateName, windowRepeatCap)) {
                return null;
            }
            this.grantedTemplates.add(templateName);
            return new Permit(this, family, templateName);
        }

        /** 请求放置许可（族 + 模板名口径）；窗上限取 {@link Config} 族键。 */
        public Permit request(String family, String templateName) {
            return request0(family, templateName, Config.prosperityStructureWindowRepeatCap);
        }

        /** 请求放置许可（{@link StructureRegistry.Entry} 口径：带 roster 覆写的窗上限）。 */
        public Permit request(StructureRegistry.Entry entry) {
            return entry == null
                ? null
                : request0(entry.family, entry.name, effectiveWindowRepeatCap(entry));
        }

        /** 记账（只由 {@link Permit#commit(int)} 调）。 */
        void onCommit(String family) {
            this.committed++;
            this.occupiedFamilies.add(family);
        }
    }

    /** 一次放置请求的许可凭据：只能被 commit 一次，之后作废。 */
    public static final class Permit {

        private final ChunkGate gate;
        private final String family;
        private final String templateName;
        private boolean spent;

        Permit(ChunkGate gate, String family, String templateName) {
            this.gate = gate;
            this.family = family;
            this.templateName = templateName;
        }

        public String family() {
            return this.family;
        }

        public String templateName() {
            return this.templateName;
        }

        /**
         * 用<b>真实落块数</b>兑现许可（plan §2.1 L5「禁止失败计成功」）。
         *
         * @param solidLanded 被 sink 实际接受的非空气落块数
         * @return true = 结构确实落了地（预算与互斥据此才扣减）；false = 0 块落地，预算不扣、许可作废
         */
        public boolean commit(int solidLanded) {
            if (this.spent) {
                throw new IllegalStateException("[GTSR] PlacementGate permit reused: " + this.templateName);
            }
            this.spent = true;
            if (solidLanded <= 0) {
                return false;
            }
            this.gate.onCommit(this.family);
            return true;
        }

        /** 未落块也要显式归还（与 commit(0) 等价，语义更清楚；不扣预算）。 */
        public void abort() {
            this.spent = true;
        }
    }

    /** 编排器入口：为本 chunk 建一个结构放置门。 */
    public static ChunkGate beginChunk(String dimKey, long worldSeed, int chunkX, int chunkZ) {
        return new ChunkGate(dimKey, worldSeed, chunkX, chunkZ);
    }

    /**
     * 接地供给器（<b>L5 结构侧唯一出口</b>，plan §2.1 L2「heightAt 唯一制式」+ §2.2 H-4「接地高度
     * 归列层」）：城 / 城外废墟 / 残缺机器三族的"每列落地 y"都取本方法返回的同一个供给器，
     * 因此"同一列取到的 y 逐点相同"是构造性质而非巧合（改造前机器那一族另有半条链走
     * {@code findSurfaceY} 列扫，即审计 A-4）。
     * <p>
     * 与类注释第 4 条的窗判定同一口径：本方法引用的 {@link ProsperityTerrainProfile#heightAt}
     * 是 dim78 的高度实现——框架件按维度取高度场本应经注入，但 {@code PlacementGate} 目前只有
     * dim78 一个结构侧消费者（dim79 的 {@code WorldGenShatteredRuins} 不在本片允许路径内），
     * 故先直引并把"注入化/上收"登记为 P13 收口项，避免为不存在的第二消费方先造一层抽象。
     */
    public static CityVariants.GroundFn groundFn(final long worldSeed) {
        return (x, z) -> ProsperityTerrainProfile.heightAt(worldSeed, x, z);
    }

    /**
     * 落块计数包装：placer 把真实写入 sink 的那一步包进 {@link CountingSink}，
     * 再用 {@link CountingSink#solid()} 作为 {@link Permit#commit(int)} 的入参。
     */
    public static CountingSink counting(BlockSink sink) {
        return new CountingSink(sink);
    }

    /** 接受/丢弃计数 Sink 包装（协议语义透传，不改写入行为）。 */
    public static final class CountingSink implements BlockSink {

        private final BlockSink parent;
        private int solid;
        private int accepted;
        private int dropped;

        public CountingSink(BlockSink parent) {
            this.parent = parent;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            final boolean ok = this.parent.setBlock(x, y, z, block, meta, flags);
            if (!ok) {
                this.dropped++;
                return false;
            }
            this.accepted++;
            if (!isAirHandle(block)) {
                this.solid++;
            }
            return true;
        }

        /** 被接受的非空气落块数（= {@link Permit#commit(int)} 的入参）。 */
        public int solid() {
            return this.solid;
        }

        /** 被接受的写入总数（含内腔清空）。 */
        public int accepted() {
            return this.accepted;
        }

        /** 被丢弃的写入数（越界/句柄不合法/世界拒绝）。 */
        public int dropped() {
            return this.dropped;
        }
    }

    /** {@code Block} 句柄重载（游戏内 placer 直接用，避免调用方自己包 isAirHandle）。 */
    public static boolean isAirBlock(Block block) {
        return block == null || block == Blocks.air;
    }
}
