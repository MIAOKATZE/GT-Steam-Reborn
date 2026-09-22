package com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;

/**
 * <b>城外结构半埋的群系档表</b>（dim78 P17 S-D；需求原话「结构生成上也有些差异，比如沙漠更多是
 * 半埋的结构等等」的唯一实现体）。
 *
 * <p>
 * ═══ 为什么需要这一个类（改前三族三套、且全不分流）═══
 * 改前"埋深"在三处各写一遍、且<b>都不看群系</b>：
 * <ol>
 * <li>跨 chunk 巨构：运行期 {@code RuinedColossusShapes.morphAt} 在 {@code 0..c.maxBury} 上均匀掷档，
 * 而 {@code maxBury} 是<b>模板常量</b> 3 / 4（{@code RuinedColossusShapes:1086/1138}）；</li>
 * <li>废墟：半埋被<b>烘进字符盘</b>（{@link RuinDamageOps#halfBury} 的 {@code sink} 1-2 层），
 * 于是"每一座废墟都沉同样的深度"，与落在哪个群系无关；</li>
 * <li>残缺机器（小机型）与 outpost：<b>根本没有埋深参数</b>（{@code wy = ground + 1 + y}，整座永远骑在地表上）。</li>
 * </ol>
 * 群系身份改前只进到"掷骰权重"（{@code RuinedMachinePlacer.machineWeightAt} 一类的
 * {@code MACHINE_WEIGHTS}/{@code SCATTER_WEIGHTS} 查表），没有任何一条链把身份送进形态层。
 * 本类就是把那条链补上：<b>身份 → 埋深档 → 各族自己的验证过的最深档之内的那一窗</b>，
 * 四族（colossus / ruin / machine / outpost）读<b>同一张表、同一个窗口算式、同一个夹紧</b>。
 *
 * <p>
 * ═══ 档表形状（写法对齐 S-A 的 {@code ProsperityTerrainProfile.RELIEF_AMPLITUDE_BY_ROSTER}）═══
 * 下标 = <b>L1 维内名册下标</b>（{@code GTSRBiomeAuthority.BiomeId.rosterIndex()}：
 * 0 锈蚀草原 / 1 齿轮森林 / 2 黄铜荒漠 / 3 起雾沼泽）。值是<b>埋深下限占"本族验证过的最深档"的比例</b>：
 * <ul>
 * <li>黄铜荒漠 {@code 1.00} —— 档最大 ⇒ 窗口塌成一点，沙漠里<b>每一座</b>城外结构都被埋到该族的验证档
 * （需求"沙漠更多是半埋"的直接兑现；沙漠不再有"整骑在地表上"的零埋深读数）；</li>
 * <li>其余三档递减（草原 0.30 &gt; 森林 0.15 &gt; 沼泽 0.05），但窗口<b>上界仍是本形状的验证档</b> ⇒
 * 非沙漠档只是"少了一点全露的机会"，不会比沙漠更深；</li>
 * <li>档缺失（身份不可得 = {@link GTSRGenLayerRosterFace#NO_IDENTITY}）走
 * {@link #DEFAULT_DEPTH_TIER} {@code = 0.0} ⇒ 窗口 {@code [0, ceiling]}，
 * 与改造前<b>逐位相同</b>（巨构那侧 {@code floor=0} 时算式退回原来的
 * {@code roll(salt, originZ, 1-originX, maxBury+1)}；小机型/outpost/废墟改造前就没有埋深通道，
 * 默认档即"不埋"）。于是"身份缺失走档表默认档"这条纪律不需要任何身份等值判断（plan §2 第 8 条），
 * 且未装配 L1 账本的离线 JVM 天然读到改造前读数。</li>
 * </ul>
 * 表里的四个值都是<b>纯档表成员</b>：本类不含任何"身份 == 某群系 ⇒ 抑制/放开"的等值判断
 * （源级红线由 {@code tools/dim1/P17StructureBiomeVarianceCheck} 的 SOURCE 组钉）。
 *
 * <p>
 * ═══ ⚠ 夹紧：为什么"越界"不会是判据红而是 clinit 抛 ═══
 * {@code RuinedColossusShapes} 的静态契约（{@code :539}）要求 {@code 1 <= maxBury < sizeY/2}，
 * 越界是 {@code ExceptionInInitializerError}（类加载即抛，判据根本没有机会报红），而
 * {@code exposedSolid(d)/exposedMachine(d)} 的两条"塌过头"下界（{@code >=24} 格、{@code >= solid/6}、
 * 机芯露出 {@code < 2/3}）也都在那一段 clinit 里判。所以本类把<b>窗口上界一律交给调用方自带的
 * "验证过的档"</b>：巨构传 {@code c.maxBury}（= 静态契约逐档验过的那个数），其余三族传
 * {@link #ceilingFor} 由字符盘实算的档。{@link #floorDepthFor} 再把下限夹进 {@code [0, ceiling]} ⇒
 * <b>任何档、任何身份、任何 ceiling 组合下掷出的埋深都落在"已被验证过的那几档之内"</b>，
 * 不新增任何未验证的深度。这条自证的用例集（含负数/越界 ceiling、全部名册下标、
 * 以及两侧真值 {@code -1..4} 的穷举）在 {@code P17StructureBiomeVarianceCheck} 的 CLAMP 组。
 *
 * <p>
 * ═══ 身份取数的坐标口径（★必须按锚点原点，不能用 chunk 号）═══
 * 埋深是<b>结构形态</b>的一部分，而跨 chunk 巨构的邻槽补片
 * （{@code RuinedMachinePlacer.renderForeignSpans}）走的是<b>锚点世界原点</b>：邻槽拿
 * {@code r.intent.originX/originZ} 重放同一个 {@code morphAt}。若身份改按"当前 chunk 的中心"反查，
 * 同一座巨构会在锚点槽读到 A 档、在邻槽读到 B 档 ⇒ 埋深不一致 ⇒ 邻槽补片造出<b>鬼影埋深</b>
 * （半截结构在邻槽凭空多埋/少埋几层）。故 {@link #rosterIndexAtOrigin} 的入参在四个族里
 * <b>一律是结构原点</b>（{@code Intent.originX/originZ}），与 {@code RuinDamageOps.roll} 的
 * 坐标输入同一口径；小机型/outpost/废墟整机在单 chunk 内，原点与所在 chunk 同解，不存在两套读数。
 * 身份出口仍是 L1 唯一那一条 {@link GTSRBiomeAuthority#ordinalAt(int, int)}（P6 红线：身份只有
 * {@code ordinalAt} 一条出口；本类不自建第二份身份面）。
 */
public final class StructureBurialTiers {

    /**
     * 埋深档表：下标 = L1 维内名册下标，值 = 该档的<b>埋深下限 / 本族验证过的最深档</b>。
     * 0 锈蚀草原 / 1 齿轮森林 / <b>2 黄铜荒漠（档最大 = 恒取最深验证档）</b> / 3 起雾沼泽。
     * <p>
     * <b>三档非沙漠为什么这么小</b>：档值是"下限"，走 {@link #floorDepthFor} 的量化
     * （{@code Math.round(tier * ceiling)}）⇒ 验证档浅的盘（27 张里 12 张 {@code ceiling ≤ 2}）
     * 三档全部落到下限 0，即"窗口 = 改造前的整段均匀窗"。这不是失误而是刻意的<b>最小惊半径</b>：
     * 本片只把沙漠推到验证档顶，其余三族"至多在最大的几张盘上少掷到最浅的一两档"，
     * 不借群系差异之名整体加大半埋（那是另一条需求，未获授权；对照 S-A 选振幅档时
     * "四档算数均值精确 1.0 ⇒ 不整体加大起伏"的同一条纪律）。逐张盘的实算窗口与露出比见
     * {@code tools/dim1/P17StructureBiomeVarianceCheck} 的 TIER 组读数。
     */
    public static final double[] DEPTH_TIER_BY_ROSTER = { 0.30D, 0.15D, 1.00D, 0.05D };

    /**
     * 默认档（身份不可得 = 该维名册零配槽的 EMPTY 降级态，或未装配 L1 账本的离线 JVM）。
     * 取 0.0 是刻意的：窗口退化为 {@code [0, ceiling]} ⇒ 巨构那侧与改造前<b>逐位相同</b>，
     * 另三族那侧"不埋"= 改造前形态。
     */
    public static final double DEFAULT_DEPTH_TIER = 0.0D;

    /**
     * {@link #ceilingFor} 的"还得多半座结构"下界（百分数）：某档埋深若把露出比压到本值以下，
     * 该档<b>不算进</b>验证过的最深档。
     * <p>
     * 量级借巨构族既有静态契约（{@code RuinedColossusShapes} 的"至少还剩六分之一质量" +
     * {@code RUIN_SILHOUETTE_MIN=24}）与 ruin 族"塌过头"下界同一词汇，不另立单位；
     * 取 34（严格大于三分之一）而不是 17（六分之一）的理由：另三族<b>没有</b>巨构那条
     * "机芯露出比随埋深必降"的静态契约兜着，只有这一条比例闸，故取更靠中间的一档。
     * 实测（21 张字符盘，见 S-D 交付件的逐模板表）：本值只在一处成为约束——
     * {@code ruin_boiler_lean} 的 {@code d=2} 档露出比 17.5% 被它挡掉（该盘 82% 的质量在最下两层）；
     * 其余模板的验证档都由几何界 {@code (sizeY-1)/2} 先决定，本值不构成约束。
     */
    public static final int CEILING_MIN_EXPOSED_PCT = 34;

    private StructureBurialTiers() {}

    /** 字符盘只读视图（三族的 {@code charAt(y,dx,dz)} 形状一致，用方法引用直接喂）。 */
    public interface CharDisc {

        char charAt(int y, int dx, int dz);
    }

    /**
     * 名册下标 → 埋深档系数（与 {@code ProsperityWorldGenerator.weightForRosterIndex} /
     * {@code ProsperityTerrainProfile.reliefAmplitudeForRosterIndex} 同一形状：越界/缺席一律回退默认档，
     * <b>不</b>写"某个群系 ⇒ 抑制"的身份等值判断）。
     */
    public static double depthTierForRosterIndex(int index) {
        return index >= 0 && index < DEPTH_TIER_BY_ROSTER.length ? DEPTH_TIER_BY_ROSTER[index] : DEFAULT_DEPTH_TIER;
    }

    /**
     * 该档的<b>埋深下限</b>（层），已夹进 {@code [0, ceiling]}。
     * <p>
     * 它同时就是<b>另三族（小机型 / outpost / 废墟）的实际埋深</b>：这三族改造前<b>没有</b>抖动式埋深
     * 通道（{@code legacyTop == 0}，见 {@link #depthAt}），窗口被顶到 {@code [floor, floor]} 一点 ⇒
     * 读到的是确定性档深，不引入新掷骰，也不给非沙漠群系添上"改造前根本没有的下沉"。
     * <p>
     * 量化取 {@code Math.round(tier * ceiling)}（沙漠档那一支例外，见下）而不是<b>截断</b>：
     * 27 张盘的验证档只有 1..5 层，截断会把 0.15 与 0.05 两档在所有盘上压成同一个 0 ——
     * 那等于"档表写了四档、世界只看得见两档"。round 之后最深的两张十一层望楼实测分成
     * {@code 沙漠 5 / 草原 2 / 森林 1 / 沼泽 0} 四个不同档，浅盘仍按各自的量化结果自然合并
     * （逐盘读数由 {@code P17StructureBiomeVarianceCheck} 的 TIER 行整表报出）。
     * 而"不借差异之名整体加深"这条仍然守住：{@code ceiling ≤ 2} 的浅盘上最浅三档实测全是 0，
     * 且"埋掉的层必须少于一半"由 {@link #ceilingFor} 的几何界先兜住 ⇒ round 越不出验证档。
     * 沙漠档 {@code tier == 1.0} 时截断值 {@code = ceiling + 1} 严格越界，<b>必须</b>被下面的
     * {@code Math.min(…, ceiling)} 夹回 {@code ceiling}：这条夹紧就是"档表越不出验证档"的机关，
     * 也是 {@code P17StructureBiomeVarianceCheck} CLAMP 组点名要打的用例。
     *
     * @param ceiling 本族<b>验证过的</b>最深档（巨构 = {@code maxBury}；另三族 = {@link #ceilingFor}）
     */
    public static int floorDepthFor(int ceiling, int rosterIndex) {
        if (ceiling <= 0) {
            return 0;
        }
        final double tier = depthTierForRosterIndex(rosterIndex);
        if (tier <= 0.0D) {
            return 0;
        }
        final long scaled = tier >= 1.0D ? (long) ceiling + 1L : Math.round(tier * ceiling);
        // 显式夹紧（下限永不越出 ceiling；越界只可能来自 tier ≥ 1 那一支）
        return (int) Math.max(0L, Math.min(scaled, (long) ceiling));
    }

    /**
     * 一次放置的埋深：在窗口 {@code [floor, max(legacyTop, floor)]} 内纯哈希掷一档，其中
     * <ul>
     * <li>{@code floor = } {@link #floorDepthFor}{@code (ceiling, rosterIndex)} —— 档表给的<b>下限</b>，
     * 沙漠档 {@code = ceiling}（恒取验证档，此时窗口塌成一点，一次掷骰都不发）；</li>
     * <li>{@code legacyTop = } <b>改造前本族的埋深上界</b>。四族里只有跨片巨构在改造前<b>有</b>抖动式
     * 埋深通道（{@code 0..maxBury} 均匀，P16-B2），另三族改造前恒 0 ⇒ 本参数取 0，
     * 于是它们读到的就是档表那个确定性下限。</li>
     * </ul>
     * 这条"上界沿用改造前"的设计是两片纪律的同时满足：① 默认档（身份不可得）时巨构那侧算式与改造前
     * {@code roll(salt, a, b, maxBury + 1)} 逐字符同形、另三族那侧恒 0 也与改造前同形 ⇒
     * <b>未装配 L1 账本的离线 JVM 读数一字不动</b>；② 本片不借"群系差异"之名给整维加大半埋
     * （对照 S-A 选振幅档时"四档算数均值精确 1.0 ⇒ 不整体加大起伏"的同一条纪律）。
     * 零新增随机源：掷骰实现体仍是 {@link RuinDamageOps#roll} 那一份（盐与坐标组合由调用点给）。
     *
     * @param salt      调用点自带的族盐（巨构沿用 {@code morphSeed ^ SALT_MORPH}，保证默认档逐位不变）
     * @param a         哈希输入 a（一律传结构<b>锚点原点</b>的一个分量）
     * @param b         哈希输入 b（同上，另一分量）
     * @param ceiling   本形状<b>验证过的</b>最深档（档表下限按它量化）
     * @param legacyTop 改造前本族的埋深上界（{@code 0 ≤ legacyTop ≤ ceiling}）
     */
    public static int depthAt(long salt, int a, int b, int ceiling, int legacyTop, int rosterIndex) {
        final int top0 = Math.max(0, ceiling);
        final int floor = floorDepthFor(top0, rosterIndex);
        final int top = Math.min(top0, Math.max(Math.max(0, legacyTop), floor));
        if (top <= floor) {
            return floor;
        }
        return floor + (int) RuinDamageOps.roll(salt, a, b, top - floor + 1);
    }

    /**
     * <b>另三族（小机型 / outpost / 废墟）的统一出口</b>：改造前它们<b>没有</b>抖动式埋深通道
     * （整座恒骑在地表顶上），所以 {@code legacyTop == 0} —— 窗口塌成一点，埋深就是
     * {@link #floorDepthFor} 那个已夹紧的档表下限，<b>不发掷骰、不新增随机源、也不引入族盐</b>。
     * <p>
     * 走这一层而不是让三族各自抄 {@link #floorDepthFor}：窗口规则（{@code [下限, max(改造前上界, 下限)]}）
     * 全仓仍只有 {@link #depthAt} 一份实现体，四族同源是"同一个函数 + 同一张表"，不是"同名的四份算式"。
     */
    public static int burialDepthFor(int ceiling, int rosterIndex) {
        return depthAt(0L, 0, 0, ceiling, 0, rosterIndex);
    }

    /**
     * 由字符盘实算"该模板<b>验证过的</b>最深档"：取同时满足两条的最大档 {@code d}——
     * ① {@code d < sizeY/2}（与巨构族静态契约 {@code maxBury < sizeY/2} 同一条几何界，
     * 保证埋掉的层永远少于一半 ⇒ 结构不会整座消失）；
     * ② 埋 {@code d} 层后露出的实心格 ≥ {@link #CEILING_MIN_EXPOSED_PCT}（"还得多半座结构"）。
     *
     * @return {@code 0} = 该模板不接受任何埋深（改造前形态，逐位不变）
     */
    public static int ceilingFor(int sizeX, int sizeY, int sizeZ, CharDisc disc) {
        if (sizeY < 3 || sizeX <= 0 || sizeZ <= 0) {
            return 0;
        }
        final int[] layer = new int[sizeY];
        int total = 0;
        for (int y = 0; y < sizeY; y++) {
            int n = 0;
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (ChunkSpans.isSolid(disc.charAt(y, x, z))) {
                        n++;
                    }
                }
            }
            layer[y] = n;
            total += n;
        }
        if (total <= 0) {
            return 0;
        }
        final int geometric = (sizeY - 1) / 2;
        int best = 0;
        int above = total;
        for (int d = 1; d <= geometric; d++) {
            above -= layer[d - 1];
            if (above * 100L < (long) total * CEILING_MIN_EXPOSED_PCT) {
                break; // 露出比已经掉到下界外：更深的档一律不算验证档（上面那条 break 单调成立，
                       // 因为 above 随 d 单调不增）
            }
            best = d;
        }
        return best;
    }

    /**
     * 结构<b>锚点原点</b>（块坐标）→ L1 维内名册下标（<b>四族唯一的身份取数点</b>）。
     * <p>
     * 为什么入参是原点而不是 chunk 号：见类注释"身份取数的坐标口径"——邻槽补片按原点重放，
     * 按 chunk 中心取身份会让同一座结构在两槽读到不同档（鬼影埋深）。
     * <p>
     * 调用频度是"每座结构一次"，不是"每列一次"（S-A 那侧 {@code heightAt} 的逐列取数代价是另一个
     * 量级，见 {@code plan/tmp/p17-sa/SA-RESULT.md} §5）；出口仍是 L1 那一条
     * {@link GTSRBiomeAuthority#ordinalAt(int, int)}，本类不自建第二份身份面、也不持有身份缓存。
     *
     * @return {@code [0, rosterSize)} 内的名册下标；身份不可得时 {@code -1}（⇒ 默认档，见类注释）
     */
    public static int rosterIndexAtOrigin(int originBlockX, int originBlockZ) {
        return GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .ordinalAt(originBlockX, originBlockZ).ordinal;
    }
}
