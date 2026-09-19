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
 * <li><b>H-2 的两条规则各只有一处实现（P7c 改判，plan §7.2「P7 的窗上限设计错误已确认并改判」）</b>：
 * ①<b>窗重复上限</b> {@link #windowRepeatAllows} —— 以<b>实际命中集</b>为条件，按 (窗, 模板) 计数，
 * 只削重复副本、<b>永不削首次出现</b>；②<b>同族间距</b> {@link #familySpacingAllows} —— 贴脸由它负责，
 * 不由配额负责。两者都靠调用方供给的 {@link IntentFn}（placer 掷骰的可重入纯函数）把"别的 chunk
 * 会不会放这一族/这个模板"重放出来，因此仍是跨 chunk 一致的纯函数、无共享状态（plan §2.3 判据 1）。
 * <b>为什么不再委托散布侧</b>：{@code ProsperitySurfaceScatter.windowAllows} 的窗内槽位排名与掷骰
 * <b>独立</b> ⇒ {@code P(放行)=cap/256}，对每 chunk 多次尝试的散布是削峰，但对 1/64、1/16 的稀疏结构
 * 事件是<b>密度乘子</b>（实测 cap=2 把 984 座砍成 12 座，见 p7b 证据文档 §3 与 C6）。散布侧那套
 * 排名语义本身是正确的（P5 交付、{@code RegionRepeatCapCheck} 钉住），本片一字未动；两套规则
 * 从此各自唯一实现、互不共用，由 {@code tools/dim1/PlacementContractCheck} D5 钉住。
 * 该"框架件引用内容件"的过渡依赖（窗边长常量）登记为未闭合项（P13 把窗判定上收至 framework）；
 * 同一性质的还有 {@link #groundFn(long)} 对 {@code ProsperityTerrainProfile.heightAt} 的直引
 * （dim79 的结构侧目前不经本门，理由见该方法注释）。</li>
 * </ol>
 * <p>
 * ═══ 用法（结构侧三步）═══
 * 
 * <pre>
 * PlacementGate.ChunkGate gate = PlacementGate.beginChunk(SurfaceGate.DIM78, worldSeed, cx, cz); // 编排器每 chunk 一个
 * Permit p = placer 内 gate.request(family, template, placer 的 IntentFn); // 预算/互斥/H-2 两条（含掷骰前置短路）
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

    /** 同族间距档 0 = 关闭间距规则（回退位，与 {@link #BUDGET_UNLIMITED} 同一"0 = 关闭"约定）。 */
    public static final int SPACING_OFF = 0;

    /**
     * 同族间距档上界（档 g ⇒ 邻域为 {@code (2g+1)²−1} 个 chunk：g=1 即 8 邻，g=2 即 24 邻）。
     * 超过本值的 Config 输入按本值钳制——重放成本是档位的平方，不设上界等于把热路径成本打开给配置。
     */
    public static final int SPACING_GAP_MAX = 4;

    /**
     * 盐 "WRpL"（P7c 结构侧<b>窗重复上限</b>槽位排序专用）。与散布侧的 "WnC" 相乘的模板域虽同为
     * {@link #templateIdOf(String)}，但盐不同 ⇒ 两套排序互不相关；也与 "ScAt"/"OUtP"/"mac" 隔离。
     */
    private static final long SALT_WINDOW_REPEAT = 0x5752704CL;

    /** 盐 "FmSp"（P7c 同族间距的优先级排序；与 {@link #SALT_WINDOW_REPEAT} 隔离，避免两条规则同一序）。 */
    private static final long SALT_FAMILY_SPACING = 0x466D5370L;

    private PlacementGate() {}

    // ═══════════════════════════════ 纯函数门（无状态）═══════════════════════════════

    /**
     * 一次"命中重放"的结果：某 chunk 槽位在<b>没有任何 H-2 规则</b>时会请求哪个模板、落在何处。
     * 只由 {@link IntentFn} 从 (worldSeed, cx, cz) 纯派生，故跨 chunk 各槽自算必得同一结论。
     */
    public static final class Intent {

        /** 被请求的模板名（与 {@link ChunkGate#request(String, String, IntentFn)} 的入参同一词汇）。 */
        public final String templateName;

        /** footprint 原点（世界块坐标；与 placer 真实落点同源，由同一段掷骰代码给出）。 */
        public final int originX;
        public final int originZ;

        /** footprint 尺寸（outpost 取<b>旋转后</b>口径，与 {@code placeAll} 的收缩钳制一致）。 */
        public final int sizeX;
        public final int sizeZ;

        public Intent(String templateName, int originX, int originZ, int sizeX, int sizeZ) {
            this.templateName = templateName;
            this.originX = originX;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }

    /**
     * 命中重放供给器（H-2 两条规则的唯一外部输入）：给出"槽位 (cx,cz) 在不受窗上限/间距约束时的
     * 放置意图"，{@code null} = 本槽不请求任何模板（掷骰未中 / 该族被禁用 / footprint 越界）。
     * <p>
     * <b>为什么由 placer 供给而不是本类自算</b>：掷骰与概率归 placer 与 {@link Config}（本类"不持有
     * 数字"，见类注释第 2 条），本类只持有"以命中集为条件"的计数与排序语义。placer 必须让
     * {@code placeAll} 的<b>真实</b>掷骰走同一个实现体（否则就是两处真值）——该约束由
     * {@code tools/dim1/PlacementContractCheck} 的 D7/A9 双向钉住。
     */
    public interface IntentFn {

        Intent intentAt(long worldSeed, int cx, int cz);
    }

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
     * H-2 规则①：<b>窗重复上限</b>（P7c 语义）。请求把模板 {@code templateName} 放在本窗时，统计同一
     * 16×16 窗内<b>其它命中同一模板的槽位</b>中按哈希序排在前面者；{@code 前面者数 ≥ cap} 才拒绝。
     * <p>
     * 由此得三条与旧（错误）语义相反方向的性质：①<b>首次出现永不因 cap 被拒</b>（它的排名是 0）；
     * ②窗内同一模板的发射 chunk 数硬上界 = cap（重复副本被削）；③总密度 = {@code Σ min(cap, 命中数)}
     * ⇒ cap 从"不限"降到 1 时<b>下降幅度 = 重复超额部分</b>，而不是把整体乘上 cap/256。
     * 早退条件是"已找到 cap 个更靠前的命中槽"，故热路径成本是 O(窗内命中数)，不是 O(256×掷骰)。
     *
     * @param cap    ≤ 0 = 关闭（回退位）；≥ 窗内槽位数 = 结构上无法约束，一并直接放行（与散布侧同口径）
     * @param intent 命中重放供给器；{@code null} = 无供给器 ⇒ 无法以命中集为条件 ⇒ <b>放行</b>
     *               （本类的正确退化方向：宁可不上限，也绝不退化回密度乘子；P8 若要用 roster 覆写
     *               的上限就必须同时供给重放口，由 {@code PlacementContractCheck} A9 钉住这条语义）
     */
    public static boolean windowRepeatAllows(long worldSeed, int cx, int cz, String templateName, int cap,
        IntentFn intent) {
        if (cap <= BUDGET_UNLIMITED
            || cap >= ProsperitySurfaceScatter.WINDOW_CHUNKS * ProsperitySurfaceScatter.WINDOW_CHUNKS
            || intent == null) {
            return true;
        }
        final long mine = windowRepeatHash(worldSeed, cx, cz, templateName);
        final int side = ProsperitySurfaceScatter.WINDOW_CHUNKS;
        final int baseX = Math.floorDiv(cx, side) * side;
        final int baseZ = Math.floorDiv(cz, side) * side;
        int ahead = 0;
        for (int lx = 0; lx < side; lx++) {
            for (int lz = 0; lz < side; lz++) {
                final int sx = baseX + lx;
                final int sz = baseZ + lz;
                if (sx == cx && sz == cz) {
                    continue; // 自己不占名额（否则首次出现也会被自己挤掉）
                }
                final Intent other = intent.intentAt(worldSeed, sx, sz);
                if (other == null || !templateName.equals(other.templateName)) {
                    continue; // 只数<b>真的会请求同一模板</b>的槽位
                }
                final long h = windowRepeatHash(worldSeed, sx, sz, templateName);
                // 哈希相等（概率可忽略）时按槽位坐标定序 ⇒ 排名是全序且各槽自算一致
                if (h < mine || (h == mine && slotBefore(sx, sz, cx, cz))) {
                    if (++ahead >= cap) {
                        return false; // 前面已有 cap 个副本 ⇒ 本座是重复超额部分
                    }
                }
            }
        }
        return true;
    }

    /**
     * H-2 规则②：<b>同族间距</b>（P7c 新增，贴脸由它负责）。若本 chunk 的 {@code gap} 档邻域内存在
     * <b>优先级更高</b>的同族命中槽（本族的 {@link IntentFn} 在该槽非空），则本座让行。
     * <p>
     * "优先级更高"用哈希序（同族同序、跨 chunk 各槽自算一致），于是这是一个确定性的贪心独立集：
     * 每个贴脸簇里<b>恰有一个</b>（哈希序最高优先的那个）存活，不会整簇同归于尽；这也是"只削贴脸
     * 副本、不削首次出现"在间距维度上的同一条性质。判定只看邻槽的<b>掷骰命中</b>，不看邻槽是否
     * 真的落了块（那要读邻 chunk 的世界，populate 期不成立且跨 chunk 不一致）——登记为口径边界。
     *
     * @param gapChunks 档位：0 = 关闭（回退位），1 = 8 邻（默认），2 = 24 邻，…上界 {@link #SPACING_GAP_MAX}
     */
    public static boolean familySpacingAllows(long worldSeed, int cx, int cz, int gapChunks, IntentFn intent) {
        final int gap = Math.min(Math.max(gapChunks, SPACING_OFF), SPACING_GAP_MAX);
        if (gap <= SPACING_OFF || intent == null) {
            return true;
        }
        final long mine = familyPriorityHash(worldSeed, cx, cz);
        for (int dx = -gap; dx <= gap; dx++) {
            for (int dz = -gap; dz <= gap; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                final int sx = cx + dx;
                final int sz = cz + dz;
                if (intent.intentAt(worldSeed, sx, sz) == null) {
                    continue; // 邻槽不放这一族 ⇒ 不构成贴脸对
                }
                final long h = familyPriorityHash(worldSeed, sx, sz);
                if (h < mine || (h == mine && slotBefore(sx, sz, cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 槽位全序的平局裁决：先 x 后 z（与散布侧同一写法，保证排名是全序且各槽自算一致）。 */
    private static boolean slotBefore(int sx, int sz, int cx, int cz) {
        return sx < cx || (sx == cx && sz < cz);
    }

    /** 族模板 id（散布件型段之上；{@link String#hashCode()} 由 JLS 钉死，跨 JVM/版本稳定）。 */
    public static int templateIdOf(String templateName) {
        return FAMILY_TEMPLATE_ID_BASE + Math.floorMod(templateName.hashCode(), FAMILY_TEMPLATE_ID_SPAN);
    }

    /** 窗重复上限的槽位排序哈希（盐 {@link #SALT_WINDOW_REPEAT}，只走框架唯一件 {@link GTSRWorldgenHash}）。 */
    private static long windowRepeatHash(long worldSeed, int cx, int cz, String templateName) {
        return GTSRWorldgenHash.splitmix64(
            GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ (SALT_WINDOW_REPEAT * (templateIdOf(templateName) + 1L)));
    }

    /**
     * 同族间距的优先级哈希（盐 {@link #SALT_FAMILY_SPACING}；与窗上限不同盐 ⇒ 两条规则的排序互不相关）。
     * 乘子一律不内联框架件已登记的常数（{@code HeightHashSingleSourceCheck} 的 PINNED_CONSTANTS 面），
     * 故这里只做"盐本身"的 xor——盐与 chunkSeed 的位混合已由 {@link GTSRWorldgenHash#splitmix64} 负责。
     */
    private static long familyPriorityHash(long worldSeed, int cx, int cz) {
        return GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_FAMILY_SPACING);
    }

    /**
     * 生效的每窗重复上限：{@code entry.windowRepeatCap > 0}（roster 显式覆写）优先，否则取 Config 族键。
     * <p>
     * {@code Entry} 里的"放置分母 / 每窗上限"两字段一律允许 <b>0 = 跟随 Config 族键</b>，本片两条既有族
     * （machine/outpost）都取 0 ⇒ 树内不存在第二处数字真值；P8 的废墟族若需要"逐模板不同概率"，
     * 才在 roster 里写非 0 值（那时 roster 就是唯一出处，Config 只留族级默认）。
     * <p>
     * <b>P7c 语义同步</b>：本值现在喂给 {@link #windowRepeatAllows}（命中集条件的重复上限），
     * 不再是"窗内前 cap 名槽位"的排名配额。
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
         * 依次过 <b>每 chunk 预算 → 同族互斥 → H-2① 窗重复上限 → H-2② 同族间距</b>；
         * 返回 {@code null} = 不放行（调用方必须直接放弃，且<b>不得</b>扣任何预算）。
         * <p>
         * <b>全部 {@code request} 重载共用本方法</b>：预算读取点与判定顺序因此在全类里各只有一处
         * （plan §2.4 判据 4——同一判定写两遍就是"两处会各自漂移的真值"，
         * {@code tools/dim1/PlacementContractCheck} D3 按这个口径钉计数）。
         *
         * @param intent 本族的命中重放口（见 {@link IntentFn}）；生产侧两族一律非空，
         *               {@code null} 只用于"本槽没有可枚举的命中集合"的兼容形态，此时 H-2 两条规则
         *               一律<b>放行</b>（退化方向见 {@link #windowRepeatAllows} 的 @param 说明）
         */
        private Permit request0(String family, String templateName, int windowRepeatCap, IntentFn intent) {
            final int budget = Config.prosperityStructureBudgetPerChunk;
            if (budget > BUDGET_UNLIMITED && this.committed >= budget) {
                return null;
            }
            if (!FAMILY_UNSCOPED.equals(family) && this.occupiedFamilies.contains(family)) {
                return null;
            }
            if (!windowRepeatAllows(this.worldSeed, this.chunkX, this.chunkZ, templateName, windowRepeatCap, intent)) {
                return null;
            }
            if (!familySpacingAllows(
                this.worldSeed,
                this.chunkX,
                this.chunkZ,
                Config.prosperityStructureFamilyGapChunks,
                intent)) {
                return null;
            }
            this.grantedTemplates.add(templateName);
            return new Permit(this, family, templateName);
        }

        /** 请求放置许可（族 + 模板名口径）；窗上限取 {@link Config} 族键，命中集由 {@code intent} 给出。 */
        public Permit request(String family, String templateName, IntentFn intent) {
            return request0(family, templateName, Config.prosperityStructureWindowRepeatCap, intent);
        }

        /** 无重放口的兼容形态：H-2 两条规则不适用（只过预算与互斥）。生产侧两族不走这里。 */
        public Permit request(String family, String templateName) {
            return request(family, templateName, null);
        }

        /** 请求放置许可（{@link StructureRegistry.Entry} 口径：带 roster 覆写的窗上限）。 */
        public Permit request(StructureRegistry.Entry entry, IntentFn intent) {
            return entry == null ? null : request0(entry.family, entry.name, effectiveWindowRepeatCap(entry), intent);
        }

        /** 无重放口的 {@link StructureRegistry.Entry} 形态（P8 若要用 roster 覆写上限，走带 IntentFn 的重载）。 */
        public Permit request(StructureRegistry.Entry entry) {
            return request(entry, null);
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
