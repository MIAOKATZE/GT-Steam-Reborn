package com.miaokatze.gtsr.common.machine.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 工作模块基类：能力闸门 + 运行状态色。
 *
 * <p>
 * 工作单元 = 能力闸门：仅声明自身解锁的 {@link ChainLink} 集合（providedLinks），自身零配方执行——
 * 配方查找、消耗与产出全部在集群总控/物流核心侧完成，本类只回答"该单元允许哪些链路通过"。
 *
 * <p>
 * 声明式差异（链路集合、GUI 类型词条 key、前脸 overlay 常量对）在构造期由具体子类以静态常量
 * 一次性注入，此后不可变；集群结构成型时总控遍历各单元收集 providedLinks 并集，即为该集群
 * 当前解锁的工艺链全集。子类只需保留构造器注入与 newMetaEntity，公共结构/注册路径全在
 * {@link MTEClusterUnitBase}（r9：统一矩阵/元素绑定/offsets 实现与 FX 注册覆写已删除——七加工
 * 模块按 plan/结构 权威规格各自持有独立矩阵，'e' 粒子候选注册上移单元基类）。
 *
 * <p>
 * 状态色口径（{@link #getUnitStatus}，六态中本族产出四态）：
 * <ul>
 * <li>STANDBY 灰——未入集群（{@code cluster == null}）或总控未开机
 * （{@code !cluster.isMachineEnabled()}）；</li>
 * <li>IDLE 黄——总控已开机但预热未满（{@code !cluster.isPreheatReady()}），或预热已满但本单元
 * 未被任何「可执行链」引用（见 {@link #isReferencedByActiveChain()}）；</li>
 * <li>WORKING 绿——预热已满且被至少一条可执行链引用；</li>
 * <li>NO_POWER_OR_INVALID 红——非本类产出：磁选/热离心子类覆写先判通电闸门
 * （{@code !isModuleEnabled()}）再 super 委托本类判定。</li>
 * </ul>
 * 其余两态 FLUID_MISSING 蓝 / BOOSTER_FLUID_MISSING 紫分别归物流单元族与增幅单元族，本族不产出。
 */
public abstract class MTEBasicProcessingUnit extends MTEClusterUnitBase<MTEBasicProcessingUnit> {

    /** 本单元解锁的链路集合（构造期一次性注入，外部拿到的是不可变视图）。 */
    private final Set<ChainLink> providedLinks;

    /** GUI 类型词条 key（构造期一次性注入，getUnitTypeNameKey 直读）。 */
    private final String unitTypeKey;

    /** 前脸 inactive 叠层常量（构造期一次性注入；null=无叠层）。 */
    private final IIconContainer overlayInactive;

    /** 前脸 active 叠层常量（构造期一次性注入；null=无叠层）。 */
    private final IIconContainer overlayActive;

    /**
     * 注册用构造器：以子类静态常量注入本单元的声明式差异——解锁的 ChainLink、GUI 类型词条 key
     * 与前脸 overlay 常量对（inactive/active，GT/GT++ 静态常量直引，允许 null）。
     */
    protected MTEBasicProcessingUnit(int aID, String aName, String aNameRegional, String unitTypeKey,
        IIconContainer overlayInactive, IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aID, aName, aNameRegional);
        this.unitTypeKey = unitTypeKey;
        this.overlayInactive = overlayInactive;
        this.overlayActive = overlayActive;
        this.providedLinks = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(providedLinks)));
    }

    /** 克隆用构造器：多方块控制器仅需名称，声明式差异常量随类型一同透传。 */
    protected MTEBasicProcessingUnit(String aName, String unitTypeKey, IIconContainer overlayInactive,
        IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aName);
        this.unitTypeKey = unitTypeKey;
        this.overlayInactive = overlayInactive;
        this.overlayActive = overlayActive;
        this.providedLinks = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(providedLinks)));
    }

    @Override
    public abstract IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

    /** 构造期注入的 GUI 类型词条 key（原五类子类各自覆写收敛为统一直读）。 */
    @Override
    public String getUnitTypeNameKey() {
        return unitTypeKey;
    }

    /** 构造期注入的前脸 inactive 叠层常量（原子类覆写收敛为统一直读）。 */
    @Override
    protected IIconContainer unitOverlayInactive() {
        return overlayInactive;
    }

    /** 构造期注入的前脸 active 叠层常量（原子类覆写收敛为统一直读）。 */
    @Override
    protected IIconContainer unitOverlayActive() {
        return overlayActive;
    }

    /** @return 本单元解锁的链路集合（不可变视图）。 */
    public Set<ChainLink> getProvidedLinks() {
        return isModuleEnabled() ? providedLinks : Collections.emptySet();
    }

    /**
     * @return 构造期注入的原始链路集合（不受能力闸门影响的只读视图；子类运行参数推导用，
     *         如 {@link MTEUnitSelfPoweredProcessingUnit} 按链步查 EU/t 表）。
     */
    protected Set<ChainLink> rawProvidedLinks() {
        return providedLinks;
    }

    /** @return 本单元是否解锁指定链路（结构未成型时关闭能力闸门）。 */
    public boolean providesLink(ChainLink link) {
        return isModuleEnabled() && link != null && providedLinks.contains(link);
    }

    /**
     * 状态细化（判定优先级从高到低）：未入集群或总控未开机 → STANDBY（灰）；预热未满 → IDLE（黄，
     * 预热中）；预热已满 → 被任一可执行链引用（{@link #isReferencedByActiveChain()}）则 WORKING
     * （绿），否则 IDLE（黄，本单元闲置）。磁选/热离心子类覆写先判通电闸门
     * （NO_POWER_OR_INVALID）再 super 委托本方法，本覆写不遮蔽子类细化。
     */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isUnitStructureFormed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        if (cluster == null) return ClusterUnitStatus.STANDBY;
        if (!cluster.isMachineEnabled()) return ClusterUnitStatus.STANDBY;
        if (!cluster.isPreheatReady()) return ClusterUnitStatus.IDLE;
        return isReferencedByActiveChain() ? ClusterUnitStatus.WORKING : ClusterUnitStatus.IDLE;
    }

    /**
     * 引用判定（仅由 {@link #getUnitStatus()} 在 {@code cluster != null} 后调用）：遍历
     * {@link ClusterTopology#getLogisticsUnits()}，任一物流单元的链
     * {@link LogisticsChain#isExecutable(ClusterTopology)} 为真，且其 {@link LogisticsChain#getLinks()}
     * 中存在链步满足 {@link ChainLink#getRequiredUnitClass()}{@code .isInstance(this)} 即被引用。
     * 空链/结构无效/含不可用链步的链 isExecutable 已返回 false，不进入链步比对。
     *
     * @return true = 集群内至少一条可执行链需要本单元
     */
    private boolean isReferencedByActiveChain() {
        ClusterTopology topology = cluster.getTopology();
        for (MTEBasicLogisticsUnit unit : topology.getLogisticsUnits()) {
            if (!unit.getChain()
                .isExecutable(topology)) continue;
            for (ChainLink link : unit.getChain()
                .getLinks()) {
                if (link.getRequiredUnitClass()
                    .isInstance(this)) return true;
            }
        }
        return false;
    }

    /**
     * 模块启用闸门：工作单元族保持基类默认语义不动（仅要求已入集群，{@code cluster != null}）。
     * 磁选/热离心等需持续供电的子类在本覆写链之上继续覆写（{@code super.isModuleEnabled() &&}
     * 追加总控通电判定），本类不收紧闸门，子类覆写经 super 链自然叠加。
     */
    @Override
    public boolean isModuleEnabled() {
        return super.isModuleEnabled();
    }
}
