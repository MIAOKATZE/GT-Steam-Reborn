package com.miaokatze.gtsr.common.dimension.framework;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 地表门单一谓词（P4，plan §5 P4 / 审计 A-5 §2「地表门共 4 份定义」/ D-5）。
 * <p>
 * <b>本类回答的只有一个问题</b>：「这一格的地表方块，允许 L4 装饰 / L5 结构把东西落在它上面吗？」
 * 它是一个<b>方块身份集合归属判定</b>，不含 y 域、不含空气让行、不含群系身份——那些仍留在各调用点
 * （理由见下"未并入本谓词的同族判定"）。
 * <p>
 * ═══ 改造前的四份定义与本片处置（审计 A-5 §2 逐条对账）═══
 * <ol>
 * <li>{@code ProsperityDecorPlacer.isNaturalTop}（旧 :190-194）——四自然 top，<b>缺
 * {@code prosperitySurface}</b>：与同维结构层对同一格给出相反结论（审计 D-5 判为缺陷，非设计），
 * 本片<b>补齐</b>并保留为一行委托；</li>
 * <li>{@code ProsperityOutpostPlacer.isNaturalProsperityTop}（旧 :404-409）——四自然 top
 * ∪ {@code prosperitySurface}（S-A1 连带放宽，dim78-fix-slice-A1-report §8）：本片成为 {@link
 * #DIM78_MEMBERS} 的出处，方法体收成一行委托；</li>
 * <li>{@code ShatteredDecorPlacer.isNaturalTop}（旧 :144-147）——dim79 四 top（同名不同域，
 * 与 #1/#2 无交集）：收成一行委托；</li>
 * <li>{@code ShatteredDecorPlacer.placeableOnNaturalTop}（旧 :133-138）——#3 ∧ 「y 域内」
 * ∧「上方为空气」：<b>语义不同，故不并</b>（同一输入可有 #3 真 / #4 假），但它自身的方块集合知识
 * 归零——只保留 y 域与空气让行两道本层门。</li>
 * </ol>
 * 四份的<b>成员比较表达式</b>（原 4 处 {@code a == X || a == Y || ...}）现全仓只有本类一份；
 * 剩余的三个公开同名方法是零集合知识的别名，保留原因与等价性证明见
 * {@code tools/dim1/SurfaceGateUnifyCheck} 的 C 组断言（本片允许路径不含
 * {@code RuinedMachinePlacer.java} 与既有工具，故 {@code RuinedMachinePlacer:106} 与
 * {@code ReplaceSurfaceRuntimeCheck:401-403} 仍按旧名引用，见 plan §5 P4 允许改动 1/3）。
 * <p>
 * ═══ <b>单一真值表：可落地方块集 + 门通过率实测列</b>（plan §2.1 横切 roster / §5 P4 判据）═══
 *
 * <pre>
 * 三个口径（都是<b>列级</b>：每列用生产 findSurfaceY 取顶格方块，再过本谓词）：
 *   A「pristine 自然面」＝L3 表层缝铺完、未经任何 placer 写入；
 *   B「散布前」＝outpost 与残缺机器（两者互斥掷骰）写完、散布尚未落笔；概率单一真值在
 *     {@code config.Config}（{@code prosperityOutpostChance} / {@code prosperityMachineChance}），
 *     本表刻意不写死数值以免与 Config 漂移（plan §2.1 横切 roster）；
 *   C「装饰前」＝真实编排链（outpost →（互斥）机器 → 散布）全部写完、装饰尚未落笔——
 *     这一列就是 {@link com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer}
 *     每次落笔时真正面对的可落地面。
 * 由 tools/dim1/SurfaceGateUnifyCheck 的 measure 模式实测；<b>真正生效的带在代码里</b>：
 * A 99.99-100、B 97.5-100、<b>C [98.6, 99.6]</b>（{@code SurfaceGateUnifyCheck:154 BAND_CHAIN_MIN/MAX}，
 * P5b 按成簇散布实测重标，锚 = 确定性样本 99.202 ±0.6pp）。本段旧文曾写"C 93.5-96.5 / 实测 94.968"，
 * 那是 P5b 之前的历史值且<b>从未跟代码同步</b>——照它去"改回"代码带就是造回归。C 带更早的历史：
 * [68,76]（P4 期小样本 100 / 99.315 / 71.850），P5 把散布从 K=64 含 15% 竖向件改为 K=8 且摘除竖向件后上移。
 * 城 buffer 窗 chunk 按同源 CityPlanner.citiesNear 判据排除；采样前提"列内无悬块/无洞穴"
 * 实测 violations=0（悬块/洞穴会让列级口径与门读到的地表不同源，故必须先证它为空）。
 *
 * 样本（权威跑，v1.20.39 T8 改链后重跑回写 2026-09-22；上一轮 = P17 复测同日）：8 seed × 8 区 × 16×16 chunk
 *                ＝ 16384 chunk 生成 / 12544 区内部 chunk 采样
 *                / <b>11443</b> eligible chunk / <b>2 929 408</b> 列（dim78）＋ 401 408 列（dim79）
 *
 * ★<b>本表没有任何判据钉住这些数字</b>（D 组钉的是成员名集合与通过率带，不是份额）——
 *   改动群系身份链 / 编排链 / 散布门后必须重跑并回写，否则它就成了第二条"看着像真值"的假事实。
 *   复现：{@code java -cp "temp/p4-surface/tools;temp/p4-surface/classes;<CP>" SurfaceGateUnifyCheck measure 8 8 16}
 *   （旧表 P4 期曾记荒漠 8.294，而同一 measure 实测约 24-26，差 2-6 倍 ⇒ 那类陈旧值的成因就是缺这条重跑纪律。）
 *
 * dimKey                   |集合|成员（BlocksGTSR 字段名）                    |口径A  |口径C  |份额 A ／ 份额 C
 * -------------------------+----+----------------------------------------------+-------+-------+------------------
 * prosperity-ruins(dim78)  | 5 |prosperitySurface                             |100.000| 99.178| 0.000 ／  0.000 ← 新增面（列级通过率）
 *                          |    |prosperitySteppeTop                           | %     | %     | 21.076 ／ 20.898
 *                          |    |prosperityForestTop                           |       |       | 26.334 ／ 26.128
 *                          |    |prosperityWastesTop                           |       |       | 22.619 ／ 22.396
 *                          |    |prosperitySwampTop                            |       |       | 29.971 ／ 29.756
 * shattered-lands(dim79)   | 4 |shatteredAshTop / SlagTop / GlassTop / TarTop |100.000| —     | 四 top 合计 100.000
 *                          |    |                                              | %     |（dim79 无编排链可跑）
 * </pre>
 *
 * <b>缺陷修复的净效应（同 seed 集 A/B 两跑，逐位可复现）</b>：
 * <table border="1">
 * <tr>
 * <th>量</th>
 * <th>改造前（装饰门只认四 top）</th>
 * <th>改造后（单一谓词 5 员）</th>
 * <th>差</th>
 * </tr>
 * <tr>
 * <td>口径 C 可落地列数</td>
 * <td>1 964 873（70.299 %）</td>
 * <td>1 965 841（70.334 %）</td>
 * <td>+968 列（+0.035 pp）</td>
 * </tr>
 * <tr>
 * <td>口径 B 可落地列数</td>
 * <td>2 771 192（99.148 %）</td>
 * <td>2 772 552（99.197 %）</td>
 * <td>+1 360 列（+0.049 pp）</td>
 * </tr>
 * <tr>
 * <td>装饰落块（口径 C 面＝生产口径）</td>
 * <td>66 583 块 / 6.0985 块·chunk⁻¹</td>
 * <td>66 637 块 / 6.1034 块·chunk⁻¹</td>
 * <td><b>+54 块（+0.0811 %）／+0.0049 块·chunk⁻¹</b></td>
 * </tr>
 * <tr>
 * <td>装饰落块（口径 A 面＝纯自然面）</td>
 * <td>100 158 块 / 9.1737 块·chunk⁻¹</td>
 * <td>100 158 块 / 9.1737 块·chunk⁻¹</td>
 * <td><b>0（逐块相同）</b></td>
 * </tr>
 * <tr>
 * <td>前序阶段（outpost 命中 / 机器 / 散布落块）</td>
 * <td>164 / 57 603 / 1 098 122</td>
 * <td>164 / 57 603 / 1 098 122</td>
 * <td>0（交叉校验：差异只来自装饰门）</td>
 * </tr>
 * </table>
 * <b>与冒烟 E6 的 82-86% 不是同一个估计量</b>：{@code plan/smoketest/41} 的
 * {@code topGatePass/attempt = 82.0-85.7%} 是真实服务器上<b>散布层逐 attempt</b>的值——scatter
 * 边写边探，看到的表面比"散布全部写完"（口径 C）更干净、比"散布开始前"（口径 B）更脏，
 * 故必然落在 B（99.197 %）与 C（70.334 %）之间；三者不得互相替代。
 * <p>
 * <b>与 P5 的关系（因果链，必读）</b>：v1.20.31 修好"门恒真失效"之后，散布/装饰的配置密度第一次
 * 全量生效（冒烟 E6：scatter attempt/chunk 68.2-84.1、topGatePass/attempt 82.0-85.7%、
 * 烟囱 8.8-10.2 柱/chunk）。本片把 dim78 装饰门补齐 {@code prosperitySurface} 后，装饰的可落面
 * 只会变宽不会变窄，<b>落量增量必须由 P5 的轮廓预算 K（U3 中道档）吸收</b>——增量原值见
 * {@code plan/investigation/p4-surface-gate-20260919.md} 表 3，P5 不得按"门未变"假设写预算。
 * <p>
 * <b>本类为什么落在 framework 层（落点二选一的答复）</b>：P3 的列扫合并件落在
 * {@link GTSRChunkProviderBase} 是因为它签名带 {@code World}、与该基类的 chunk 生命周期同源；
 * 本谓词的输入只有<b>方块身份 + 维度键</b>，消费方是 L4/L5 的 5 个 placer（两个维度、均不继承
 * chunk provider）。把它塞进 provider 基类会让装饰层为一个 {@code ==} 比较去依赖 chunk 生成类
 * （携带 World/BiomeGenBase/噪声全链），而独立叶子类只依赖 {@link BlocksGTSR} 与 {@link Block}。
 * 层级上本类与 L3 同侧（framework 层、两维共用），与 P3 的
 * {@link GTSRWorldgenHash} / {@link GTSRBiomeAuthority} 横切件同族。
 */
public final class SurfaceGate {

    /** 便捷维度键别名（与 L1 账本同一词汇，禁止另造字符串）。 */
    public static final String DIM78 = GTSRBiomeAuthority.DIM_KEY_PROSPERITY;
    /** 便捷维度键别名（同上）。 */
    public static final String DIM79 = GTSRBiomeAuthority.DIM_KEY_SHATTERED;

    /** dim78 成员数（声明即真值；改成员必须同时改本数与 roster 表列，工具会钉住）。 */
    public static final int DIM78_SIZE = 5;
    /** dim79 成员数（同上）。 */
    public static final int DIM79_SIZE = 4;

    /** dim78 成员名（按声明序；{@code BlocksGTSR} 字段名，供日志与离线断言按名比对）。 */
    static final String[] DIM78_NAMES = { "prosperitySurface", "prosperitySteppeTop", "prosperityForestTop",
        "prosperityWastesTop", "prosperitySwampTop" };
    /** dim79 成员名（同上）。 */
    static final String[] DIM79_NAMES = { "shatteredAshTop", "shatteredSlagTop", "shatteredGlassTop",
        "shatteredTarTop" };

    /**
     * dim78 可落地方块集（{@link #DIM78_SIZE} 项）。
     * <p>
     * 首项 {@code prosperitySurface} 是<b>城内 meta0-5 冻结块 + S-A1 前自然区旧地表</b>：结构层
     * （outpost/机器/散布）自 S-A1 起就认它（旧 :404-409），装饰层漏认即审计 D-5 判定的缺陷；
     * 本集合是"两维各自可落地集"的唯一出处。
     */
    private static final Block[] DIM78_TOPS = new Block[DIM78_SIZE];
    /** dim79 可落地方块集（{@link #DIM79_SIZE} 项；dim79 无 {@code prosperitySurface} 对应物）。 */
    private static final Block[] DIM79_TOPS = new Block[DIM79_SIZE];
    /** 未知/未注册维度键的返回值：空集 ⇒ 门恒关（fail-closed，不静默放宽）。 */
    private static final Block[] EMPTY = new Block[0];

    private static final Set<String> UNBOUND_LOGGED = ConcurrentHashMap.newKeySet();

    static {
        bind();
    }

    private SurfaceGate() {}

    /**
     * 从 {@link BlocksGTSR} 取字段现值（幂等）。
     * <p>
     * 生产侧 {@code <clinit>} 必在方块注册之后；离线断言与"方块被总开关关闭"两种场景下字段可能
     * 尚未落位，故 {@link #landableTops(String)} 在检测到空槽时按需重取一次（不缓存"半空"状态）。
     */
    private static void bind() {
        DIM78_TOPS[0] = BlocksGTSR.prosperitySurface;
        DIM78_TOPS[1] = BlocksGTSR.prosperitySteppeTop;
        DIM78_TOPS[2] = BlocksGTSR.prosperityForestTop;
        DIM78_TOPS[3] = BlocksGTSR.prosperityWastesTop;
        DIM78_TOPS[4] = BlocksGTSR.prosperitySwampTop;
        DIM79_TOPS[0] = BlocksGTSR.shatteredAshTop;
        DIM79_TOPS[1] = BlocksGTSR.shatteredSlagTop;
        DIM79_TOPS[2] = BlocksGTSR.shatteredGlassTop;
        DIM79_TOPS[3] = BlocksGTSR.shatteredTarTop;
    }

    /**
     * 本维度声明的<b>可落地方块集</b>（单一真值出口）。返回值是内部数组：<b>不得就地修改</b>
     * （{@code SurfaceGateUnifyCheck} 按名逐一钉住成员，任何改写都会让它变红）。
     * 未知 {@code dimKey} 返回空集 ⇒ 门恒关，并打一行 L8 锚点日志。
     */
    public static Block[] landableTops(String dimKey) {
        if (DIM78.equals(dimKey)) {
            if (DIM78_TOPS[0] == null) {
                bind();
            }
            return DIM78_TOPS;
        }
        if (DIM79.equals(dimKey)) {
            if (DIM79_TOPS[0] == null) {
                bind();
            }
            return DIM79_TOPS;
        }
        warnUnboundOnce(dimKey);
        return EMPTY;
    }

    /**
     * <b>地表门唯一谓词</b>：入参 = 维度键 + 该维度<b>显式</b>方块集合 + 待判地表方块。
     * <p>
     * 判据纯身份比较（改造前四份也都是 {@code ==} 链，无 meta、无 {@code instanceof}、无材质判断），
     * {@code ground == null} 一律关（改造前 {@code a == null字段} 形式在 null 地表上可能误开，
     * 本谓词显式关门；实际调用点的地表 y 来自 {@link GTSRChunkProviderBase#findSurfaceY}，
     * 其契约保证该格非 null 非空气，故该守卫不改变任何实测结果）。
     * <p>
     * {@code landableTops} 与 {@code dimKey} 不匹配（把别维集合传进来）属调用点误用：结果按<b>传入
     * 集合</b>判（保持"入参即语义"的可预测性），同时打一行 WARN 锚点，且被
     * {@code SurfaceGateUnifyCheck} 的源级断言 E 组挡住（生产调用点的集合参数必须写作
     * {@code landableTops(同维度键)}）。
     */
    public static boolean isNaturalTop(String dimKey, Block[] landableTops, Block ground) {
        if (ground == null || landableTops == null) {
            return false;
        }
        if (landableTops != landableTops(dimKey) && dimKey != null) {
            warnForeignSetOnce(dimKey);
        }
        for (final Block top : landableTops) {
            if (top == ground) {
                return true;
            }
        }
        return false;
    }

    /** 按本维声明集判定（{@link #landableTops(String)} 的简写；两处入口同一实现体）。 */
    public static boolean isNaturalTop(String dimKey, Block ground) {
        return isNaturalTop(dimKey, landableTops(dimKey), ground);
    }

    /** 成员名的单行摘要（日志与离线断言可读输出用）。 */
    public static String describe(String dimKey) {
        final String[] names = DIM78.equals(dimKey) ? DIM78_NAMES : DIM79.equals(dimKey) ? DIM79_NAMES : null;
        if (names == null) {
            return "SurfaceGate(" + dimKey + ")=<未申报维度键，门恒关>";
        }
        final StringBuilder sb = new StringBuilder("SurfaceGate(");
        sb.append(dimKey)
            .append(")={");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(names[i])
                .append('=')
                .append(landableTops(dimKey)[i] == null ? "null" : System.identityHashCode(landableTops(dimKey)[i]));
        }
        return sb.append('}')
            .toString();
    }

    /** 未申报维度键（门恒关）一次性锚点。 */
    private static void warnUnboundOnce(String dimKey) {
        if (!UNBOUND_LOGGED.add("unbound/" + dimKey)) {
            return;
        }
        GTSteamReborn.LOG
            .warn("[GTSR] SurfaceGate: dimKey={} 无申报可落地集合 ⇒ 地表门恒关（不铺任何装饰/结构落点；plan §2.1 L8）", String.valueOf(dimKey));
    }

    /** 跨维集合误用一次性锚点。 */
    private static void warnForeignSetOnce(String dimKey) {
        if (!UNBOUND_LOGGED.add("foreign/" + dimKey)) {
            return;
        }
        GTSteamReborn.LOG.warn(
            "[GTSR] SurfaceGate: dimKey={} 收到非本维申报的方块集合（应传 landableTops(dimKey)）⇒ 按传入集合判并计数",
            String.valueOf(dimKey));
    }
}
