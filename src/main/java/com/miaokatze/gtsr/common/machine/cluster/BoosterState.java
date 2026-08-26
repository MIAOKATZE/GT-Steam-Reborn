package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

/**
 * 增幅聚合器：把一组建幅模块按拍板规则（D8/D9/A4）一次性聚合为不可变快照，GUI（增幅面板表）与
 * 服务器（蒸汽经济/执行计划）共用同一口径；纯计算、无副作用、不持有 tick 状态。
 *
 * <p>
 * 聚合规则：仅统计 {@code cluster != null} 且 {@link MTEBasicAmplifierUnit#isFluidAvailable()}
 * 的「生效」模块；缺流体模块双重豁免——增益与蒸汽惩罚倍率同时不计，但计入失效数。「在场」= 已接入
 * 集群（{@code getCluster() != null}），未接入集群的 STANDBY 模块既不计生效也不计失效。
 *
 * <p>
 * 口径说明：
 * <ul>
 * <li>主产物仅 1 个生效=同型多模块取最高不叠加；同刻多型互不影响各自独立（并行/速度/副产物/节汽
 * 各自按型累加，主产物只取生效模块中的最高一个）；</li>
 * <li>{@link ClusterParams.BoosterType#getBoosterValue(int)} 返回 int（并行=台数、百分比为整数%），
 * 本类把百分比换算为小数（÷100）：两台速度增幅分别贡献 5% 与 10% → {@code getSpeedBonus()=0.15}；</li>
 * <li>空列表（含 null）直接返回 {@link #EMPTY} 单例，与全零快照语义等价。</li>
 * </ul>
 *
 * <p>
 * 缺流体双重豁免验证示例（钛层级 tierIdx=2：速度 30%/惩罚 1.4，主产物 15%/2.0，节汽 8%/1.1）：
 * 速度增幅缺流体、主产物与节汽增幅生效时——速度项 {@code getSpeedBonus()=0}（30% 不计）、节汽项
 * {@code getSaverBonusRaw()=0.08}、惩罚项 {@code getPenaltyProduct()=2.0×1.1=2.2}（缺流体速度增幅
 * 的 1.4 同步豁免不计）；若速度增幅流体恢复，则 {@code getSpeedBonus()=0.30}、
 * {@code getPenaltyProduct()=2.0×1.1×1.4=3.08}。
 */
public final class BoosterState {

    /** 全零空态单例：增益全 0、penaltyProduct=1.0、生效/失效计数为 0；aggregate(空列表或 null) 返回本单例。 */
    public static final BoosterState EMPTY = new BoosterState(0, 0D, 0D, 0D, 0D, 1D, Collections.emptyList(), 0);

    /** 生效并行模块值之和（台数口径）。 */
    private final int parallelBonus;

    /** 生效速度模块百分比之和（小数口径）。 */
    private final double speedBonus;

    /** 生效主产物模块最高值（仅计 1 个，无则 0）。 */
    private final double primaryBonus;

    /** 生效副产物模块百分比之和（小数口径）。 */
    private final double secondaryBonus;

    /** 生效节汽模块百分比之和（未截断，小数口径）。 */
    private final double saverBonusRaw;

    /** 生效模块惩罚倍率连乘积（无生效模块=1.0）。 */
    private final double penaltyProduct;

    /** 生效模块列表（仅 aggregate 内部构造后不再变更，对外经 {@link #getActiveUnits()} 出副本）。 */
    private final List<MTEBasicAmplifierUnit> activeUnits;

    /** 在场但缺流体的失效模块数。 */
    private final int failedCount;

    private BoosterState(int parallelBonus, double speedBonus, double primaryBonus, double secondaryBonus,
        double saverBonusRaw, double penaltyProduct, List<MTEBasicAmplifierUnit> activeUnits, int failedCount) {
        this.parallelBonus = parallelBonus;
        this.speedBonus = speedBonus;
        this.primaryBonus = primaryBonus;
        this.secondaryBonus = secondaryBonus;
        this.saverBonusRaw = saverBonusRaw;
        this.penaltyProduct = penaltyProduct;
        this.activeUnits = activeUnits;
        this.failedCount = failedCount;
    }

    /**
     * 聚合一组建幅模块：单次遍历一次性算全字段，返回不可变快照（不持有入参列表）。
     *
     * <p>
     * 生效 = 已接入集群 && 锁定流体可用（{@link MTEBasicAmplifierUnit#isFluidAvailable()}）
     * && tank 存量足以支付<b>本秒用量</b>（{@link MTEBasicAmplifierUnit#amplifierFluidPerSec()}——
     * S7 联动加成后实耗，§3.6.3——增幅流体按秒支付，不足支付本秒用量的模块不入快照：<b>无增益也无蒸汽惩罚</b>，
     * 计失效数；实扣增幅流体由主控按同口径另行执行）；在场但缺流体的模块同样计失效
     * （双重豁免：增益与惩罚均不计）；未接入集群（STANDBY）的模块与 null 元素直接跳过。
     * 无生效且无失效模块时返回 {@link #EMPTY} 单例（空列表/null 入参同此，语义等价）。
     *
     * <p>
     * 主/副产物增益经 {@link #getPrimaryBonus()}（同类取最高仅一生效）/ {@link #getSecondaryBonus()}
     * （加算）暴露给 {@code ClusterChainExecutor.rollOutputs}，真实作用于产物 chance。
     *
     * @param units 增幅模块列表（可为 null 或空）
     * @return 不可变聚合快照
     */
    public static BoosterState aggregate(List<MTEBasicAmplifierUnit> units) {
        if (units == null || units.isEmpty()) return EMPTY;
        int parallel = 0;
        int failed = 0;
        double speed = 0D;
        double primary = 0D;
        double secondary = 0D;
        double saverRaw = 0D;
        double penalty = 1D;
        List<MTEBasicAmplifierUnit> active = new ArrayList<>();
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getCluster() == null) continue;
            if (!unit.isTierValidForConnection() || !unit.isFluidAvailable() || !canPayAmplifierFluidThisSecond(unit)) {
                failed++;
                continue;
            }
            int value = unit.getBoosterValueForStructureTier();
            switch (unit.getBoosterType()) {
                case PARALLEL -> parallel += value;
                case SPEED -> speed += value / 100D;
                case PRIMARY_OUTPUT -> primary = Math.max(primary, value / 100D);
                case SECONDARY_OUTPUT -> secondary += value / 100D;
                case STEAM_SAVER -> saverRaw += value / 100D;
            }
            penalty *= unit.getBoosterType()
                .getPenaltyMultiplier();
            active.add(unit);
        }
        if (active.isEmpty() && failed == 0) return EMPTY;
        return new BoosterState(parallel, speed, primary, secondary, saverRaw, penalty, active, failed);
    }

    /**
     * 本秒增幅流体支付能力（§3.6.3，S7 联动口径）：模块 tank 存量 ≥ 其<b>实际</b>按秒增幅液消耗
     * （{@link MTEBasicAmplifierUnit#amplifierFluidPerSec()}——基础五表值 × (1 + Σ速度/并行联动
     * 加成)，与主控实扣同口径）才计入本秒快照。只读判定、不实扣。
     */
    private static boolean canPayAmplifierFluidThisSecond(MTEBasicAmplifierUnit unit) {
        int perSecLps = unit.amplifierFluidPerSec();
        FluidStack tank = unit.getTankContent();
        return tank != null && tank.amount >= perSecLps;
    }

    // ==================== 只读访问器 ====================

    /** @return Σ 生效并行模块值（台数口径，加算，叠加物流基并行）。 */
    public int getParallelBonus() {
        return parallelBonus;
    }

    /** @return Σ 生效速度模块百分比（小数口径：5% + 10% → 0.05 + 0.10 = 0.15）。 */
    public double getSpeedBonus() {
        return speedBonus;
    }

    /**
     * @return 生效主产物模块取最高且仅计 1（无则 0）。主产物仅 1 个生效=同型多模块取最高不叠加
     *         （如 3 台钛档 15% 模块同时生效 → 0.15 而非 0.45）；同刻多型互不影响各自独立。
     */
    public double getPrimaryBonus() {
        return primaryBonus;
    }

    /** @return Σ 生效副产物模块百分比（小数口径）。 */
    public double getSecondaryBonus() {
        return secondaryBonus;
    }

    /** @return Σ 生效节汽模块百分比（小数口径，未做上限截断）。 */
    public double getSaverBonusRaw() {
        return saverBonusRaw;
    }

    /** @return 节汽生效值 = min(raw, {@link ClusterParams#STEAM_SAVER_CAP})。 */
    public double getSaverBonusEffective() {
        return Math.min(saverBonusRaw, ClusterParams.STEAM_SAVER_CAP);
    }

    /** @return Π 生效模块 {@link ClusterParams.BoosterType#getPenaltyMultiplier()}（无生效模块=1.0，缺流体模块豁免不计）。 */
    public double getPenaltyProduct() {
        return penaltyProduct;
    }

    /** @return 生效模块数（已接入集群且锁定流体可用）。 */
    public int getActiveCount() {
        return activeUnits.size();
    }

    /** @return 失效模块数（在场=已接入集群但锁定流体缺失；STANDBY 模块不计）。 */
    public int getFailedCount() {
        return failedCount;
    }

    /** @return 生效模块列表副本（GUI 增幅面板表用，调用方修改不影响本快照）。 */
    public List<MTEBasicAmplifierUnit> getActiveUnits() {
        return new ArrayList<>(activeUnits);
    }
}
