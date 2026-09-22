package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * 群系内分支地形变体（<b>P19 §H 新增</b>，plan §H「实现放新类 TerrainVariants（profile 只留调用点）」）：
 * 在 {@link ProsperityTerrainProfile#heightCore} 的三频缓丘 {@code h0} 之上、河谷两段式压低<b>之前</b>，
 * 按 roster 注入群系性格形态项——齿轮森林丘陵/山地（RTG TerrainBase hills 模板 + ATG CoreNoise plateau
 * 模板）、黄铜荒漠垄状沙丘（RTG dunes + TerrainHLDunes domain-warp 模板）、起雾沼泽水位渐变夹持微洼
 * （ATG swamp 模板）、锈蚀草原轻微丘陵与遗忘之川微起伏（同场降档）。<b>只做地形夹持，不置水</b>
 * （沼泽微池水面归批 2 的沼泽小湖面）。
 *
 * <h2>与 {@code ampAt} 振幅场的正交性（两场各管一件事）</h2>
 * {@link ProsperityTerrainProfile#ampAt} 是<b>振幅场</b>：控制三频缓丘的"起伏总量"（sd 量级，
 * P17 偏序 森&gt;原≥沙&gt;沼 由它承担）；本类是<b>形态场</b>：控制"形态分布"（丘陵鼓包/桌状山地/
 * 垄状沙丘/贴水微洼的形状与覆盖），变体项以<b>加性 delta</b> 叠在 h0 上，不乘不改振幅档表——
 * 振幅档与 zone 乘子照旧作用于三频基线，本类的门强/弱不改变 sd 偏序的来源，只改变群系内分布形状。
 *
 * <h2>全软门纪律（无硬地形阈值）</h2>
 * <ul>
 * <li>所有门限都是低频噪声 → smoothstep 带通（{@link #s01}），带外<b>精确等于 0.0</b>：
 * 丘陵/山地门关死的列 delta 恒为 +0.0，{@code h0 + 0.0} 经 round 回 int 与改造前<b>逐位相同</b>
 * （"均匀区（门噪声极值外）h0 与改造前逐位同"的构造性保证）。荒漠沙丘与沼泽夹持是群系身份本身
 * （RTG/ATG 原型亦无关死态），全群系起作用、无恒等区，属设计内例外；身份缺席
 * （{@code rosterIndex} 越界/离线无账本）整列零变体，仍逐位同改造前。</li>
 * <li>半空间折叠 {@code min(d,0)} 用 {@code (d−|d|)/2} 表达，软削顶用 C1 光滑极小
 * {@link #softMin}——全程无 if 形状分支（代码里的 {@code > 0} 短路只影响求值次数，
 * 数学结果与"先求值再乘 0"逐位相同）。</li>
 * <li>跨变体过渡：roster 身份是逐粗格（4 方块）台阶量，若按单点身份硬选变体，群系边界两侧
 * delta 硬跳（森 +14 ↔ 沙 0），正是 P18 T3 在振幅档上修掉的那类悬崖。本类照搬 T3 的药方：
 * 变体 delta 按 11×11 smoothstep 紧支核（半径 5 粗格 = 20 方块，核形与
 * {@code ProsperityTerrainProfile} 的 AMP_KERNEL 同式）对逐粗格身份做<b>加权混合</b>，
 * 边界两侧收敛到同一凸组合 ⇒ C0 连续；带内起伏缓入缓出再由各低频门自身连续性保证。</li>
 * </ul>
 *
 * <h2>参数定值（公式依据 G3 调查原文，可复核 RTG/ATG）</h2>
 * <ul>
 * <li><b>丘陵场（草原 0 / 森林 1 共场不同档）</b>：RTG TerrainBase:33-45 blendedHillHeight
 * S 曲线（r=s+1; r=r³+10; r=cbrt(r); r=r/0.46631−4.62021，s=0 映 ~0.15、s=−1 映 ~0 ⇒ m≥0 只加不减）+
 * :92-104 两波组合（150 波长大波 m、55 波长小波 sm，sm=sm²·m、m+=sm/3）。门噪声波长 320，
 * 森林带 [0.08,0.35]（幅度 ×14 ⇒ 丘陵区 +6..+14）、草原带 [0.20,0.50]（幅度 ×8 ⇒ +2..+6 常态带）；
 * 门带按 valueNoise 实测边际（三角型集中在 0）标定，阈值必须落在 working 区而非薄上尾。</li>
 * <li><b>山地变体（仅森林，更稀有低频门）</b>：门噪声波长 512、带 [0.30,0.60]（部分覆盖 P≈10%）；
 * ATG CoreNoise:154-194 plateau 双档 88/104（档选择 = 同门噪声的 [0.45,0.65] 二级带）线性混合
 * {@code base(1−lf)+ledge·lf} + 碎坡项 {@code |base−ledge|·lf·(rough+1)/2}（rough=波长 40 噪声），
 * lf=门值 ⇒ 山地列 +14..+26 稀有高值；plateau 作用在"已含丘陵"的底上 ⇒ 山顶整平。</li>
 * <li><b>荒漠沙丘（roster 2，无丘陵）</b>：TerrainHLDunes:11-27 domain-warp（波长 20、幅度 6 的
 * x 向方向性抖动先扭坐标）喂 RTG TerrainBase:226-247 dunes 参数化——强度场
 * {@code 0.38+0.30·n(/224)}、主波 {@code n(/60)·st·2}、半空间折叠 {@code (d−|d|)/2}（只取负瓣，
 * 垄形不对称）、二次整形 {@code p·(3p+1.8)}（p=−fold，线性+二次复合防三角边际压扁垄高）、
 * 碎浪细节 {@code n(扭曲坐标/17)·(0.6+st)}、盆底偏置 {@code −1.3·st} ⇒ 强度场高区垄峰 +4..+8、
 * 垄间盆 −1.5 左右（沙丘海/平沙地由强度场连续调制）。</li>
 * <li><b>沼泽（roster 3）</b>：ATG CoreNoise:129-152 swamp 渐变夹持
 * {@code h=(1−f)·h+f·target}，target={@link ProsperityTerrainProfile#SEA_LEVEL}−0.5（贴水线），
 * f=0.55+0.40·s01（波长 256 门）⇒ 地表收进 66..68 带；微洼 = 波长 48 池噪声 s01 带
 * （[0.15,0.50]）再下挖 ≤2.6·f ⇒ 局部 65-66 的低于水线浅洼（<b>干洼</b>，是否置水归批 2 沼泽
 * 小湖面）；变体幅度保守（夹持 ≤±3.2、洼 ≤−2.6）。</li>
 * <li><b>遗忘之川（roster 4）</b>：同沼泽场降档（f=0.25+0.25·s01、洼 ≤1.6）⇒ ±1..2 微起伏。
 * 生产路径身份面只产生 0..3（见 RELIEF_AMPLITUDE_BY_ROSTER 第 5 元同款纪律），本档为名册
 * 对称兜底，与 P17"生产取不到"口径一致。</li>
 * <li><b>软削顶（A 模板）</b>：加权总 delta 过 {@code softMin(·,26,8)}（delta≤18 逐位不动、
 * 上界渐近 26 ⇒ "+14..+26 稀有高值"不失真）；结果高度再过 {@code softMin(·,108,4)}
 * （≤104 逐位不动——P17 实测高度域上沿恰为 104）⇒ 变体列恒 ≤108，[40,110] 钳制对变体列
 * <b>永不截平</b>，sd 不被钳制削顶。</li>
 * </ul>
 *
 * <h2>每列新增成本（U8 性能批总账的申报基数）</h2>
 * 核混合 pass = 69 次 (seed,粗格) 缓存查表 + 5 路乘加（<b>零噪声求值</b>；缓存纪律与
 * {@code ProsperityTerrainProfile.ROSTER_CELL_CACHE} 同款：线程私有、上限 16384、超限整清）。
 * 噪声求值（{@code valueNoise}，每次 4 格点哈希）：草原 1（门开 +2）；森林 2 常态、
 * 丘陵列 4、山地列 3-5；荒漠 3（扭曲/强度/主波，碎浪复用扭曲种子换波长零加费）；沼泽 2。
 * 常态腹地 2-3 次，与任务包"每列 1-3 次"口径一致；门全开列最坏 5 次（幅度小、value noise 便宜，
 * 实测账交 U8 基准判据对 241µs 基线总账）。
 * <b>P19 U8 实测账（GenBenchCheck 口径，基线 = 批2 终态）</b>：权重查表随
 * {@link #weightsAt} 改判为粗格终值缓存（69 次查表 → 1 次，同一粗格内列间恒等），
 * 身份取数并入 Profile 的 {@code chainRosterIndexAt} 单一 memo；其余见 plan/tmp/p19-u8-prered.md。
 * 纯函数、零 {@code net.minecraft} 依赖、零方块读取（身份取数只经
 * {@code GTSRGenLayerRosterFace.rosterIndexAt} 的 int 出口，经 Profile 共享 memo 转发），
 * 同 seed 同坐标恒同输出——四族共用 {@code heightAt} 的同源契约经调用点自动继承。
 */
public final class TerrainVariants {

    /** 变体名册槽数（0..3 = selector 群系，4 = 遗忘之川名册档；越界一律零变体）。 */
    private static final int ROSTER_SLOTS = 5;

    /**
     * 每线程每 seed 的<b>粗格权重向量</b>缓存（P19 U8 改判）：11×11 核对逐粗格身份的加权结果
     * {@code w[0..4]} 只依赖核中心粗格（核偏移固定、各粗格身份只依赖 (seed, 该粗格)）⇒ 同一粗格内
     * 所有列的权重向量<b>数学恒等</b>，缓存终值与旧"逐列 69 次查表累积"逐位相同（同一批加法同一
     * 次序）。每列成本从 69 次 HashMap 查表降为 1 次。纪律同 Profile 各表：线程私有、上限
     * {@link #VAR_CELL_CACHE_CAP}、超限整清重算值不变。
     */
    private static final int VAR_CELL_CACHE_CAP = 65536;
    private static final ThreadLocal<HashMap<Long, HashMap<Long, double[]>>> VAR_CELL_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    // —— 丘陵场（RTG hills 模板）——
    /** 丘陵门噪声域盐（波长 320；森林/草原共场不同带）。 */
    private static final long S_HILL_GATE = 0x6811C21DL;
    /** 丘陵大波域盐（RTG 波长 150）。 */
    private static final long S_HILL_BIG = 0x6811C22DL;
    /** 丘陵小波域盐（RTG 波长 55）。 */
    private static final long S_HILL_SMALL = 0x6811C23DL;
    /** 森林丘陵幅度（门全开 ×形状峰值 ⇒ +6..+14）。 */
    private static final double HILL_AMP_FOREST = 14.0D;
    /** 草原轻微丘陵幅度（门全开 ×形状 ⇒ +2..+6 常态带）。 */
    private static final double HILL_AMP_STEPPE = 10.0D;

    // —— 山地变体（ATG plateau 模板，仅森林）——
    /** 山地门噪声域盐（波长 512，稀有低频门）。 */
    private static final long S_MTN_GATE = 0x6811C24DL;
    /** 山地碎坡噪声域盐（波长 40）。 */
    private static final long S_MTN_ROUGH = 0x6811C25DL;
    /** plateau 双档下檐（ATG ledgelevel 低档）。 */
    private static final double MTN_LEDGE_LOW = 88.0D;
    /** plateau 双档上檐（高档 = 低档 + 16 ⇒ 104，恰在软削顶哨兵之下）。 */
    private static final double MTN_LEDGE_HIGH = 104.0D;

    // —— 荒漠沙丘（RTG dunes + TerrainHLDunes domain-warp 模板）——
    /** 沙丘扭曲噪声域盐（波长 20 扭曲 + 波长 17 碎浪共种子换波长）。 */
    private static final long S_DUNE_WARP = 0x6811C26DL;
    /** 沙丘强度场域盐（波长 224）。 */
    private static final long S_DUNE_STRENGTH = 0x6811C27DL;
    /** 沙丘主波域盐（波长 60）。 */
    private static final long S_DUNE_MAIN = 0x6811C28DL;
    /** 主波增益（d=main·st·2.6；对准三角边际定标使强度场高区垄峰进 +4..+8）。 */
    private static final double DUNE_MAIN_GAIN = 2.6D;
    /** 扭曲幅度（TerrainHLDunes 口径 4-8 取 6，x 向方向性）。 */
    private static final double DUNE_WARP_AMP = 6.0D;
    /** 垄脊二次项（p·(p·QUAD+LIN)，p=−fold ∈[0,|d|]；强度场高区峰上界 ~8，值噪声三角边际下的定标）。 */
    private static final double DUNE_RIDGE_QUAD = 3.0D;
    /** 垄脊线性项（保典型列垄高进入 +2..+4 可见带——纯二次会被三角边际压扁）。 */
    private static final double DUNE_RIDGE_LIN = 1.8D;

    // —— 沼泽/遗忘之川（ATG swamp 模板）——
    /** 夹持门噪声域盐（波长 256）。 */
    private static final long S_SWAMP_CLAMP = 0x6811C29DL;
    /** 微洼噪声域盐（波长 48）。 */
    private static final long S_SWAMP_POOL = 0x6811C2ADL;
    /** 沼泽微洼最大下挖（×f ⇒ ≤2.6，浅洼不破水线 2-3 格）。 */
    private static final double SWAMP_POOL_DEPTH = 2.6D;
    /** 遗忘之川微起伏下挖上限（±1..2 档）。 */
    private static final double SANZU_POOL_DEPTH = 1.6D;

    // —— 软削顶（A 模板）——
    /** 加权总 delta 的光滑上界（softMin k=8；≤18 逐位不动，渐近 26）。 */
    private static final double DELTA_CAP = 26.0D;
    /** 结果高度的光滑哨兵（softMin k=4；≤104 逐位不动，恒 &lt;108 ⇒ 110 钳制零截平）。 */
    private static final double HEIGHT_SENTINEL = 108.0D;

    /** 核半径（粗格；与 ProsperityTerrainProfile.AMP_KERNEL_RADIUS 同值 5 ⇒ 直径 11 粗格）。 */
    private static final int VAR_KERNEL_RADIUS = 5;

    /** 核参与格点（i²+j²&lt;r²；与 Profile AMP_KERNEL 同式同 69 点，d=r 权重恰 0 不入表）。 */
    private static final int[] VAR_KERNEL_DX;
    private static final int[] VAR_KERNEL_DZ;
    /** 归一核权（Σw = 1）。 */
    private static final double[] VAR_KERNEL_W;

    static {
        final int r = VAR_KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        VAR_KERNEL_DX = new int[n];
        VAR_KERNEL_DZ = new int[n];
        VAR_KERNEL_W = new double[n];
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                final double t = 1.0D - Math.sqrt(d2) / r;
                final double w = t * t * (3.0D - 2.0D * t);
                VAR_KERNEL_DX[k] = i;
                VAR_KERNEL_DZ[k] = j;
                VAR_KERNEL_W[k] = w;
                wSum += w;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            VAR_KERNEL_W[k] /= wSum;
        }
    }

    private TerrainVariants() {}

    /**
     * {@code h0} → 群系变体修正（<b>P19 §H 唯一公开出口</b>；调用点仅
     * {@code ProsperityTerrainProfile.heightCore} 的 h0 计算后、两段式压低前一处）。
     * <p>
     * 返回修正后的整型高度：均匀区/门关区逐位等于入参 {@code h0}（见类注释"全软门纪律"），
     * 变体列 = {@code round(h0 + softMin(加权 delta, 26, 8))} 再过 {@code softMin(·,108,4)}。
     * 河谷两段式/巨湖压低/低地防抬升/[40,110] 钳制全部在下游原样作用——本方法只改 h0 这一层的
     * 形态，不碰任何压低语义。
     *
     * @param worldSeed   世界种子（与 {@code heightAt} 同一口径，不含 def.seedSalt）
     * @param x           列 x
     * @param z           列 z
     * @param rosterIndex 调用方已解析的中心粗格名册下标（0..4）；<b>只作域纪律</b>
     *                    （越界/无身份 ⇒ 零变体，与振幅默认档同款的越界回退，非身份等值抑制）——
     *                    变体选择走核加权混合而非单点身份，理由见类注释"跨变体过渡"
     * @param h0          三频缓丘基线高度（BASE + round(relief)，改造前口径）
     * @return 变体修正后的 h0（均匀区逐位等于入参）
     */
    public static int variantAdjustment(long worldSeed, int x, int z, int rosterIndex, int h0) {
        if (rosterIndex < 0 || rosterIndex >= ROSTER_SLOTS) {
            return h0;
        }
        // P19 U8：权重向量按粗格缓存（同一粗格内所有列恒等，见 VAR_CELL_CACHE 注释）；只读不写。
        final double[] w = weightsAt(worldSeed, x, z);
        double delta = 0.0D;
        // —— 丘陵族（草原 w0 / 森林 w1 共场不同档；RTG hills 模板）——
        if (w[0] > 0.0D || w[1] > 0.0D) {
            final double gh = hillGateNoise(worldSeed, x, z);
            // 门带标定对准 valueNoise 实测边际（三角型、集中在 0：P(gh>0.30)≈10%、P(gh>0.5)≈4%），
            // 阈值放进气泡 working 区而不是薄上尾（初版 0.30/0.55 段实测覆盖率 <3%，探针归因后重标）。
            final double gateF = s01((gh - 0.08D) / 0.27D);
            final double gateS = s01((gh - 0.20D) / 0.30D);
            final boolean hillsOn = gateF > 0.0D || gateS > 0.0D;
            final double m = hillsOn ? hillsShape(worldSeed, x, z) : 0.0D;
            if (w[0] > 0.0D) {
                delta += w[0] * (HILL_AMP_STEPPE * m * gateS);
            }
            if (w[1] > 0.0D) {
                double forest = HILL_AMP_FOREST * m * gateF;
                // —— 山地变体（ATG plateau；作用在"已含丘陵"的底上 ⇒ 山顶整平）——
                final double gm = mtnGateNoise(worldSeed, x, z);
                final double mf = s01((gm - 0.30D) / 0.30D);
                if (mf > 0.0D) {
                    final double base = h0 + forest;
                    final double ledge = MTN_LEDGE_LOW + (MTN_LEDGE_HIGH - MTN_LEDGE_LOW) * s01((gm - 0.45D) / 0.20D);
                    final double rough = 0.5D
                        + 0.5D * GTSRWorldgenHash.valueNoise(worldSeed ^ S_MTN_ROUGH, x / 40.0D, z / 40.0D);
                    final double plateau = base * (1.0D - mf) + ledge * mf
                        + Math.abs(base - ledge) * mf * (rough + 1.0D) * 0.5D;
                    forest = plateau - h0;
                }
                delta += w[1] * forest;
            }
        }
        // —— 荒漠沙丘（RTG dunes 参数化 + domain-warp；无丘陵）——
        if (w[2] > 0.0D) {
            final long warpSeed = worldSeed ^ S_DUNE_WARP;
            final double wx = x + DUNE_WARP_AMP * GTSRWorldgenHash.valueNoise(warpSeed, x / 20.0D, z / 20.0D);
            final double st = 0.38D
                + 0.30D * GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_STRENGTH, x / 224.0D, z / 224.0D);
            final double main = GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_MAIN, wx / 60.0D, z / 60.0D);
            final double d = main * st * DUNE_MAIN_GAIN;
            final double fold = (d - Math.abs(d)) * 0.5D;
            final double p = -fold;
            final double bump = p * (p * DUNE_RIDGE_QUAD + DUNE_RIDGE_LIN);
            final double ripple = GTSRWorldgenHash.valueNoise(warpSeed, wx / 17.0D, z / 17.0D) * (0.6D + st);
            delta += w[2] * (bump - 1.3D * st + ripple);
        }
        // —— 沼泽夹持 + 微洼 / 遗忘之川微起伏（ATG swamp 模板；只做地形，不置水）——
        if (w[3] > 0.0D || w[4] > 0.0D) {
            final double gs = GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_CLAMP, x / 256.0D, z / 256.0D);
            final double gate = s01((gs + 1.0D) * 0.5D);
            final double pp = GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_POOL, x / 48.0D, z / 48.0D);
            final double pool = s01((pp - 0.15D) / 0.35D);
            final double target = ProsperityTerrainProfile.SEA_LEVEL - 0.5D;
            if (w[3] > 0.0D) {
                final double f = 0.55D + 0.40D * gate;
                delta += w[3] * ((h0 * (1.0D - f) + target * f) - h0 - SWAMP_POOL_DEPTH * pool * f);
            }
            if (w[4] > 0.0D) {
                final double f4 = 0.25D + 0.25D * gate;
                delta += w[4] * ((h0 * (1.0D - f4) + target * f4) - h0 - SANZU_POOL_DEPTH * pool * f4);
            }
        }
        // 软削顶 A 模板（两道哨兵在观测域内逐位恒等，见类注释）：先封顶 delta，再封顶结果高度
        final double capped = softMin(delta, DELTA_CAP, 8.0D);
        final double adjusted = softMin(h0 + capped, HEIGHT_SENTINEL, 4.0D);
        return (int) Math.round(adjusted);
    }

    /**
     * 11×11 核对逐粗格名册的加权累积（P18 T3 同款防悬崖；中心粗格即调用方
     * {@code rosterIndex} 的来源格 ⇒ 均匀区 {@code w[rosterIndex] == 1.0}、其余精确 0.0）。
     * <b>P19 U8 改判</b>：加权结果按核中心粗格缓存（{@link #weightsAt}）——本方法保留为
     * 未命中路径的一次性求值体，算式与加法次序一字未动；粗格身份取数改走
     * {@link ProsperityTerrainProfile#chainRosterIndexAt} 的单一份共享 memo
     * （与 ampAt/heightCore 同一份，取数式与本类旧内联版逐字同源，消同格短命链重复求值）。
     */
    private static double[] weightsAt(long worldSeed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final HashMap<Long, HashMap<Long, double[]>> bySeed = VAR_CELL_CACHE.get();
        HashMap<Long, double[]> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        final double[] cached = cells.get(key);
        if (cached != null) {
            return cached;
        }
        final double[] w = new double[ROSTER_SLOTS];
        for (int k = 0; k < VAR_KERNEL_DX.length; k++) {
            final int r = ProsperityTerrainProfile
                .chainRosterIndexAt(worldSeed, cellX + VAR_KERNEL_DX[k], cellZ + VAR_KERNEL_DZ[k]);
            if (r >= 0 && r < ROSTER_SLOTS) {
                w[r] += VAR_KERNEL_W[k];
            }
        }
        if (cells.size() >= VAR_CELL_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, w);
        return w;
    }

    /** (cellX, cellZ) → long 打包（与 Profile.packCell 同式：低 32 位 cellZ，负坐标两侧一致）。 */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /** 丘陵门噪声（波长 320；包内可见仅供离线探针/判据取单一真值门值，生产路径勿直调）。 */
    static double hillGateNoise(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_GATE, x / 320.0D, z / 320.0D);
    }

    /** 山地门噪声（波长 512；包内可见同上）。 */
    static double mtnGateNoise(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.valueNoise(worldSeed ^ S_MTN_GATE, x / 512.0D, z / 512.0D);
    }

    /** RTG TerrainBase:33-45 blendedHillHeight（s=−1 映 ~0、s=0 映 ~0.15、s=1 映 ~1.0 ⇒ 恒非负）。 */
    private static double blendedHillHeight(double s) {
        double r = s + 1.0D;
        r = r * r * r + 10.0D;
        r = Math.cbrt(r);
        return r / 0.46631D - 4.62021D;
    }

    /** RTG TerrainBase:92-104 丘陵两波组合（150 大波 m 与 55 小波 sm²·m，m += sm/3）。 */
    private static double hillsShape(long worldSeed, int x, int z) {
        final double mb = blendedHillHeight(
            GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_BIG, x / 150.0D, z / 150.0D));
        double ms = blendedHillHeight(GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_SMALL, x / 55.0D, z / 55.0D));
        ms = ms * ms * mb;
        return mb + ms / 3.0D;
    }

    /** smoothstep 带通（clamp 后 3t²−2t³；带外精确 0.0/1.0——软门的构造性基础）。 */
    private static double s01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    /**
     * C1 光滑极小（多项式补偿式；|a−b| ≥ k 时逐位等于 min(a,b)——两道削顶哨兵在
     * 观测域内恒等、越界域内渐近收口的构造性基础）。
     */
    private static double softMin(double a, double b, double k) {
        final double h = Math.max(0.0D, k - Math.abs(a - b));
        return Math.min(a, b) - h * h / (4.0D * k);
    }
}
