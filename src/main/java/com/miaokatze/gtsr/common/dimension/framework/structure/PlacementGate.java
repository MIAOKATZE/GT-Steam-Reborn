package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
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
 * 与 <b>P19</b> 湿区谓词 {@link #dryFootprint(long, int, int, int, int)} 对
 * {@code GTSRVoronoiRiverField} 的直引（dim79 的结构侧目前不经本门，理由见该方法注释；
 * framework→prosperity 直引按 plan p19 §F 拍板集中在 P13 债务账登记）。</li>
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
     * 族：城外废墟（<b>P8 新增</b>，plan §5 P8「废墟族（L5 新增内容）」；
     * {@code prosperity/ruins/ruin/RuinShapes} 的破坏结构模板，全部由既有结构"破败化"派生）。
     * <p>
     * 与既有三族的关系：本族<b>是</b>每 chunk 互斥掷骰链上的第三环（编排器 outpost → 机器 → 废墟），
     * 但只在<b>前二环都没真实落块</b>时才问门，且过的是同一份 {@code prosperityStructureBudgetPerChunk}
     * 预算 ⇒ 它是"在既有预算内挤位"，不是叠加密度（实测对照见
     * {@code tools/dim1/RuinFamilyCheck} 的 DENSITY 表）。本族也是全名册里<b>唯一</b>
     * {@code allowsDamagedVariant=true} 的一族（P8 的 opt-in 损毁算子只对它的模板生效）。
     */
    public static final String FAMILY_RUIN = "ruin";

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
        return readyAtSpan(surfaceY) && surfaceTopLandable;
    }

    /**
     * 就绪门的<b>纯函数臂</b>（<b>P16-B1 新增</b>，只服务跨 chunk 分片结构）：可落地 y 带。
     * <p>
     * 为什么要单独一条：跨 chunk 结构的"放不放"必须是 {@code (worldSeed, 锚点槽)} 的纯函数——
     * 邻槽 chunk 只知道自己那一格，它要能在<b>没有锚点槽世界读数</b>的情况下复现同一个结论，
     * 否则就会出现"邻槽画了半座、锚槽弃权"的鬼影剪影（分片渲染协议的成立前提，见
     * {@link ChunkSliceSink} 类注释）。而旧 {@link #readyAt(int, boolean)} 的第二条臂
     * （顶块过 {@code SurfaceGate}）要读 {@code world.getBlock}，天然不可跨槽复现。
     * <p>
     * <b>继承的先例</b>：城内跨 chunk 结构走的正是同一条口径——{@code ProsperityWorldGenerator
     * .placeCities} 只按 {@code heightAt} 逐列接地，不判世界顶块（城的密度与位置由城窗 H-1/L6 决定，
     * 也从不经这条臂）。本方法把这条既有事实显式成门的一部分，而不是让新族自己写一遍 y 带字面量
     * （plan §2.4 判据 4：同一个数值不得两处漂移；{@link #LANDING_Y_MIN}/{@link #LANDING_Y_MAX} 仍是唯一出处）。
     * <p>
     * 单 chunk 族（outpost / 小机型 / 废墟）一律继续用 {@link #readyAt(int, boolean)} 的两臂全判，
     * 本方法不改变它们任何一条判定。
     */
    public static boolean readyAtSpan(int surfaceY) {
        return surfaceY >= LANDING_Y_MIN && surfaceY <= LANDING_Y_MAX;
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

    /**
     * H-2 规则②的<b>绝对让行档</b>（<b>P8 新增，只服务废墟族</b>）：邻域 {@code (2g+1)²−1} 个 chunk 内
     * 只要 {@code intent} 在<b>任何一个</b>邻槽非空，本座就放弃——不比哈希序。
     * <p>
     * 与 {@link #familySpacingAllows} 的分工：那一条是"同族互贴时恰活一座"的贪心独立集（既有两族在用，
     * 行为与默认值一字未改）；本一条是"新族不得贴任何既有城外结构"的单向约束。之所以需要单向档：
     * 既有两族的掷骰在本片是<b>冻结</b>的（任务包禁止改互斥顺序与概率值），它们不会回头躲新开的一族，
     * 于是跨族贴脸只能由新族这一侧让掉。实测口径与不这么做时的后果写在
     * {@link ChunkGate#request(StructureRegistry.Entry, IntentFn, IntentFn)} 的注释里。
     * <p>
     * 成本：{@code gap=1} 时 8 次重放；档位仍是 {@link #SPACING_GAP_MAX} 钳制，与优先级档同一上界。
     *
     * @return true = 邻域干净（可放）；false = 邻域已有东西要放，本座让行
     */
    public static boolean neighborhoodClearAllows(long worldSeed, int cx, int cz, int gapChunks, IntentFn intent) {
        final int gap = Math.min(Math.max(gapChunks, SPACING_OFF), SPACING_GAP_MAX);
        if (gap <= SPACING_OFF || intent == null) {
            return true; // 与优先级档同一"0 = 关闭"回退位；无重放口时同样不得凭空拒绝
        }
        for (int dx = -gap; dx <= gap; dx++) {
            for (int dz = -gap; dz <= gap; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (intent.intentAt(worldSeed, cx + dx, cz + dz) != null) {
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
     * 生效的每窗重复上限：{@code entry.windowRepeatCap > 0}（roster 显式覆写）优先，否则取<b>本族</b>的
     * Config 键（{@link #familyWindowRepeatCap(String)}）。
     * <p>
     * {@code Entry} 里的"放置分母 / 每窗上限"两字段一律允许 <b>0 = 跟随 Config 族键</b>；本片三条城外族
     * （machine/outpost/ruin）<b>全部传 0</b> ⇒ 树内不存在第二处数字真值。P7 javadoc 预留的
     * "roster 覆写非 0"通道 P8 <b>没有启用</b>：启用它等于把数字从 Config 搬进 roster，
     * plan §2.1 横切 roster 行的"禁止同一数值两处漂移"要为此再加一条对账断言，
     * 而收益只是"少一个 Config 键"，不划算。
     * <p>
     * <b>P7c 语义同步</b>：本值现在喂给 {@link #windowRepeatAllows}（命中集条件的重复上限），
     * 不再是"窗内前 cap 名槽位"的排名配额。
     */
    public static int effectiveWindowRepeatCap(StructureRegistry.Entry entry) {
        return entry.windowRepeatCap > 0 ? entry.windowRepeatCap : familyWindowRepeatCap(entry.family);
    }

    /**
     * 族级"每窗重复上限"的<b>唯一读取点</b>（P8：废墟族需要自己的上限档，但按 family 分支的读取点
     * 全仓只此一处 ⇒ 不会出现"ruin 族的键在两处各自被读"）。
     * 非 ruin 族（machine/outpost/city/unscoped）一律回落到既有族键
     * {@link Config#prosperityStructureWindowRepeatCap}，<b>取值与默认值一字未改</b>。
     */
    public static int familyWindowRepeatCap(String family) {
        return FAMILY_RUIN.equals(family) ? Config.prosperityRuinWindowRepeatCap
            : Config.prosperityStructureWindowRepeatCap;
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
         * 请求放置许可（族 + 模板名口径）。依次过 <b>每 chunk 预算 → 同族互斥 → H-2① 窗重复上限 →
         * H-2② 同族间距</b>；两条 H-2 规则共用同一个命中集。
         *
         * @param intent 本族的命中重放口；{@code null} = 两条规则都不适用（只过预算与互斥）
         */
        public Permit request(String family, String templateName, IntentFn intent) {
            return request0(family, templateName, familyWindowRepeatCap(family), intent, intent, false);
        }

        /**
         * 请求放置许可（<b>P8 新增</b>：窗重复上限与"邻域让行"各用各的命中集，且邻域规则取
         * <b>绝对让行</b>档）。
         * <p>
         * 为什么废墟族要单独一档：{@link #familySpacingAllows} 是<b>优先级</b>让行（邻槽哈希序更高才让，
         * 于是每个贴脸簇恰活一座），它对"同族互贴"是对的。但废墟族是互斥链上<b>填前两环空槽</b>的第三环，
         * 而 outpost/机器这两环不知道第三环的存在（本片不许改它们的判定），于是优先级比较在跨族时
         * 只有一半的概率让新族躲开——实测（2 seed × 2 区 = 874 有效 chunk）把 T4 口径的贴脸率从
         * 8.889% 顶到 29.851%，比 P7c 要治的 23.882% 还差（{@code RuinFamilyCheck} 的 DENSITY-RISE 行）。
         * <p>
         * 本重载把新族的邻域规则改成"<b>邻域内任一城外结构族命中就放弃本座</b>"（绝对让行，不参与哈希
         * 比较）。方向上只会更保守：既有两族的判定、概率与 {@code cap}/{@code gap} 两键的
         * <b>语义与默认值一字未改</b>（它们走的仍是 {@link #request(String, String, IntentFn)} 那条
         * 优先级路径），收紧的只有新增的第三族。
         */
        public Permit request(StructureRegistry.Entry entry, IntentFn windowIntent, IntentFn neighborhoodIntent) {
            return entry == null ? null
                : request0(
                    entry.family,
                    entry.name,
                    effectiveWindowRepeatCap(entry),
                    windowIntent,
                    neighborhoodIntent,
                    true);
        }

        /**
         * 请求放置许可（{@link StructureRegistry.Entry} 口径：带 roster 覆写的窗上限）。
         * 两条 H-2 规则共用 {@code intent}，邻域规则仍是优先级档。
         */
        public Permit request(StructureRegistry.Entry entry, IntentFn intent) {
            return entry == null ? null
                : request0(entry.family, entry.name, effectiveWindowRepeatCap(entry), intent, intent, false);
        }

        /** 无重放口的兼容形态（只过预算与互斥）。生产侧三族都不走这里。 */
        public Permit request(String family, String templateName) {
            return request(family, templateName, (IntentFn) null);
        }

        /** 无重放口的 {@link StructureRegistry.Entry} 形态（P8 废墟族走带 {@code IntentFn} 的重载）。 */
        public Permit request(StructureRegistry.Entry entry) {
            return request(entry, (IntentFn) null);
        }

        /**
         * 判定顺序与预算读取的<b>唯一实现体</b>（全部 {@code request} 重载都汇到这里；
         * {@code tools/dim1/PlacementContractCheck} D3/D4 按这个口径钉计数）。
         *
         * @param intent        H-2① 窗重复上限的命中集
         * @param spacingIntent H-2② 邻域规则的命中集
         * @param absoluteYield true ⇒ 邻域规则用<b>绝对让行</b>档（邻槽有任何命中即放弃，不比哈希序）；
         *                      false ⇒ 沿用 {@link #familySpacingAllows} 的优先级档（既有两族）
         */
        private Permit request0(String family, String templateName, int windowRepeatCap, IntentFn intent,
            IntentFn spacingIntent, boolean absoluteYield) {
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
            final int gapChunks = Config.prosperityStructureFamilyGapChunks;
            final boolean spacingOk = absoluteYield
                ? neighborhoodClearAllows(this.worldSeed, this.chunkX, this.chunkZ, gapChunks, spacingIntent)
                : familySpacingAllows(this.worldSeed, this.chunkX, this.chunkZ, gapChunks, spacingIntent);
            if (!spacingOk) {
                return null;
            }
            this.grantedTemplates.add(templateName);
            return new Permit(this, family, templateName);
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
     * 干区占比阈值——<b>结构族</b>（outpost/machine·巨构/ruin；<b>P19 §F 拍板，U5-redirect
     * 修正</b>）：footprint 采样面上干列占比 ≥ 本值才可放置。原"全列全过"语义对任何非零
     * 河网密度都近乎必弃（探针：61-67% 弃位），与 vanilla 结构"采样检查"的实际口径不符；
     * 0.90 允许小 footprint 摊上 0-2 列河缘滩带（结构本体随 groundFn 逐列接地，个别滩列
     * 只影响观感不影响"整体泡水"），河道/湖心仍必然弃位。
     */
    public static final double DRY_RATIO_STRUCTURAL = 0.90D;

    /**
     * 干区占比阈值——<b>城市 cell</b>（<b>P19 §F 拍板，U5-redirect 修正</b>）：城盘外扩面
     * （{@code CityPlanner.CITY_DRY_HALF_CHUNKS}=8 chunk）干列占比 ≥ 0.85 即放行——城市
     * 地块密度高、街道逐列接地，允许外缘 15% 滩带换"城市能正常生成"；但城心核心区
     * （中心 64×64，{@code CityPlanner} 的 strict 臂）仍要求<b>全干</b>（城市核心泡在水里
     * 是用户明确不可接受的观感）。
     */
    public static final double DRY_RATIO_CITY = 0.85D;

    /**
     * 贴河护带下界（<b>P19 §F 拍板值，U5 二次 redirect 定口径</b>）：{@code -strengthAt ≥
     * 本值} 的列算"贴水"（水面以上贴水边 + 滩带外沿；河核窄带已由 wetAt 覆盖）——
     * "结构生成在水里或河滩里"均不可接受。取 {@code WET_MIN - 0.08}：比水面窄一档的
     * 缓冲，把水线以上第一圈滩坡纳入避让。地形常数不出自本类（单一真值在
     * {@link GTSRVoronoiRiverField}），本类不引 Config（这不是密度数字，不进"不持有
     * 数字"那条纪律的管辖面）。
     */
    private static final double WET_SHORE_GUARD = GTSRVoronoiRiverField.WET_MIN - 0.08D;

    /**
     * 湿区避让谓词的<b>单列判定</b>（P19 §F；U5 二次 redirect 后的<b>回填真值口径</b>）：
     * 一列算"湿"当且仅当 ①回填会置水（{@code wetAt}——{@code GTSRVoronoiRiverField} 的
     * 单一真值，含荒漠整段闸与浅滩干出语义；本门传 {@code rosterIndex=0} 取<b>最保守</b>
     * 非荒漠口径，结构无列身份，任何群系的河道水带都避开。<b>P22 A1a（v1.20.42）口径</b>：
     * {@code wetAt} 收紧为 {@code s ≥ WET_MIN ∧ trunk > 0} 后，枯竭带外河核列（干河床）过
     * ①——干河床可进结构，符合直觉；本谓词零逻辑改动，只随门自动收窄）、②湖置水带
     * （{@code lakeAt < LAKE_WATER_LEVEL}）、③贴河护带（{@code -strengthAt ≥ WET_MIN-0.08}，
     * 拦住水面以上贴水边与滩带外沿——"结构生成在水里或河滩里"均不可接受）、④沼泽河床残潭场
     * （v1.20.42 P22 A1b，{@link GTSRVoronoiRiverField#swampRiverPoolAt} &gt; 0，roster 走生产
     * coarse 身份链——A1a 后干河床本可进结构，但潭列有水有下挖，重新算湿）。
     * <p>
     * <b>为什么没有 heightAt 门（U5 首版读数 49-92% 弃位的根因）</b>：{@code heightAt<68}
     * 大量命中<b>自然洼地</b>（BASE 70 + 三频波动 + zone 乘子 0.6-1.4 的低区，无河无湖、
     * 回填不置水），把干地误判湿区；回填真值口径下自然洼地放行，湿带收窄到河核/湖面/
     * 贴水窄条。三个读数仍是 {@code (worldSeed, x, z)} 纯函数（不读世界方块）。
     */
    private static boolean dryColumnAt(long worldSeed, int x, int z) {
        if (GTSRVoronoiRiverField.wetAt(worldSeed, x, z, 0)) {
            return false;
        }
        // ②湖置水带：O1a 起走 RVF 布尔单一出口（湖<岸>口径 LAKE_SHORE 腿不在此列，保持各处内联）
        if (GTSRVoronoiRiverField.lakeWaterAt(worldSeed, x, z)) {
            return false;
        }
        // ④ 沼泽河床残潭场（v1.20.42 P22 A1b）：潭列（含下挖潭底）算湿——结构不落潭。roster 走
        // 生产 coarse 身份链（本谓词无列身份，wetAt 腿传 0 取最保守口径的先例不适用于此腿：
        // 潭场内部有 roster==3 硬门，传实际 roster 才能让非沼泽列在门腿零成本短路，语义也更准
        // ——潭只存在于实际沼泽河床上）。与 A1a 口径衔接：①的干河床可进结构，但 A1b 起干河床上
        // 的残潭列重新算湿（潭列有水有下挖，"结构生成在水里"仍不可接受）。O1a 起比较式并入
        // RVF swampRiverPoolColumnAt 单一出口（布尔纯包装，腿序/短路语义逐字保持）。
        if (GTSRVoronoiRiverField.swampRiverPoolColumnAt(
            worldSeed,
            x,
            z,
            ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, x >> 2, z >> 2))) {
            return false;
        }
        return -GTSRVoronoiRiverField.strengthAt(worldSeed, x, z) < WET_SHORE_GUARD;
    }

    /**
     * 湿区避让门（<b>P19 §F「结构避水」，城市泡水根因修复；U5-redirect 改占比制</b>）：矩形
     * footprint {@code [x0..x1] × [z0..z1]} 的<b>干区占比判定</b>——采样<b>四角 + 中心 +
     * 周界步 8 + 内部网格步 24</b>，每列过 {@link #dryColumnAt} 三关，干列占比 ≥
     * {@code minRatio} 才 true。
     * <p>
     * <b>占比制而非全过制（U5-redirect 裁决）</b>：全过制对大 footprint 近乎必弃（首版探针：
     * 城市 99.77% / outpost 67.1% / machine 61.4% / ruin 59.3% 弃位——257×257 城盘的 min
     * 高度均值 59，"每列都高出水面"在任何非零河网密度下不成立）。占比制允许边缘摊上少量
     * 河缘滩带列（结构本体随 groundFn 逐列接地，个别滩列只影响观感不影响"整体泡水"）；
     * 河道/湖心仍必然弃位（河道列三关全败）。调用方按族传
     * {@link #DRY_RATIO_STRUCTURAL}（四族）/{@link #DRY_RATIO_CITY}（城盘外扩面）。
     * <p>
     * <b>弃位语义（四族 placer 与城门统一）</b>：调用方（outpost / 机器·巨构 / 废墟 /
     * {@code CityPlanner.cityGateAllows}）在<b>掷骰命中后、落块前</b>查本门，失败即
     * <b>弃位且不重试</b>（vanilla House 模式——不换点、不降级、不递延到下一 chunk），掷骰流
     * 与既有概率值一字不改 ⇒ 同 seed 同坐标恒同结论（确定性）。弃位后该 chunk / 该 cell 的
     * 结构密度略降，属<b>预期观感</b>（结构让水，不是水让结构）；半埋档表不受影响
     * （弃位发生在落块之前，与埋深无关）。
     * <p>
     * <b>纯函数边界</b>：{@link #dryColumnAt} 的三个读数（heightAt / lakeAt / strengthAt）
     * 都不读世界方块，故跨 chunk 分片协议（P16-B1：邻槽必须能独立复现锚点结论）与离线判据
     * （无世界 JVM）都能逐位复算本门。
     * <p>
     * 依赖登记：本方法是 framework→prosperity 的第二处直引（第一处是 {@link #groundFn(long)}），
     * 按 plan p19 §F 拍板集中在 P13 债务账，见类注释。
     *
     * @param minRatio 干列占比下界（{@link #DRY_RATIO_STRUCTURAL} / {@link #DRY_RATIO_CITY}）
     * @return true = 干列占比达标（可放置）；false = 湿/滩/贴河采样列过多（弃位）
     */
    public static boolean dryFootprint(long worldSeed, int x0, int z0, int x1, int z1, double minRatio) {
        return dryRatioAt(worldSeed, x0, z0, x1, z1) >= minRatio;
    }

    /**
     * 城心核心区专用<b>全过制</b>门（{@link #DRY_RATIO_CITY} 的配套臂，P19 §F 拍板）：
     * 中心 64×64 内<b>每列</b>（四角+中心+周界步 8+内部网格步 16 采样，全过制）必须全干——
     * 城市核心泡在水里是用户明确不可接受的观感，占比制的外缘容忍不适用。
     */
    public static boolean dryFootprintStrict(long worldSeed, int x0, int z0, int x1, int z1) {
        final int loX = Math.min(x0, x1);
        final int hiX = Math.max(x0, x1);
        final int loZ = Math.min(z0, z1);
        final int hiZ = Math.max(z0, z1);
        if (!dryColumnAt(worldSeed, loX, loZ) || !dryColumnAt(worldSeed, loX, hiZ)
            || !dryColumnAt(worldSeed, hiX, loZ)
            || !dryColumnAt(worldSeed, hiX, hiZ)
            || !dryColumnAt(worldSeed, loX + (hiX - loX) / 2, loZ + (hiZ - loZ) / 2)) {
            return false;
        }
        for (int x = loX + 8; x < hiX; x += 8) {
            if (!dryColumnAt(worldSeed, x, loZ) || !dryColumnAt(worldSeed, x, hiZ)) {
                return false;
            }
        }
        for (int z = loZ + 8; z < hiZ; z += 8) {
            if (!dryColumnAt(worldSeed, loX, z) || !dryColumnAt(worldSeed, hiX, z)) {
                return false;
            }
        }
        for (int x = loX + 16; x < hiX; x += 16) {
            for (int z = loZ + 16; z < hiZ; z += 16) {
                if (!dryColumnAt(worldSeed, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * footprint 采样面上的干列占比（{@link #dryFootprint(long, int, int, int, int, double)} 的
     * 实现体）：采样面 = 四角 + 周界步 8 + 内部网格步 24 + 中心（内部网格保证大 footprint 的
     * <b>腹地</b>也被覆盖——首版只有周界+中心，湖心/河道穿腹地时边缘采样可能全干而腹地全湿）。
     */
    private static double dryRatioAt(long worldSeed, int x0, int z0, int x1, int z1) {
        final int loX = Math.min(x0, x1);
        final int hiX = Math.max(x0, x1);
        final int loZ = Math.min(z0, z1);
        final int hiZ = Math.max(z0, z1);
        final int[] xs = axisSamples(loX, hiX);
        final int[] zs = axisSamples(loZ, hiZ);
        final int midX = loX + (hiX - loX) / 2;
        final int midZ = loZ + (hiZ - loZ) / 2;
        int dry = 0;
        int total = 0;
        for (final int x : xs) {
            for (final int z : zs) {
                final boolean edge = x == loX || x == hiX || z == loZ || z == hiZ;
                final boolean grid24 = (x - loX) % 24 == 0 && (z - loZ) % 24 == 0;
                final boolean mid = x == midX && z == midZ;
                if (!edge && !grid24 && !mid) {
                    continue;
                }
                total++;
                if (dryColumnAt(worldSeed, x, z)) {
                    dry++;
                }
            }
        }
        return total == 0 ? 1.0D : (double) dry / total;
    }

    /** 轴采样点：起点起步 8 前进 + 显式收尾端点（循环 {@code x < hi} 严格小于 ⇒ 端点不重复）。 */
    private static int[] axisSamples(int lo, int hi) {
        if (hi <= lo) {
            return new int[] { lo };
        }
        final int[] buf = new int[(hi - lo) / 8 + 2];
        int m = 0;
        for (int x = lo; x < hi; x += 8) {
            buf[m++] = x;
        }
        buf[m++] = hi;
        return java.util.Arrays.copyOf(buf, m);
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
