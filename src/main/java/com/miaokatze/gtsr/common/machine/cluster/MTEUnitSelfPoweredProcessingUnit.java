package com.miaokatze.gtsr.common.machine.cluster;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/**
 * 带自持能源仓的加工单元父类（热力离心机/磁选机共用）。
 *
 * <p>
 * 在 {@link MTEBasicProcessingUnit} 的能力闸门之上叠加「自持能源」语义：背面中心能源位
 * {@code 'P'}=自身能源位（标准能源 hatch 添加器，各子类矩阵字面定位，保留为兼容额外能源位）；
 * A 机壳位放宽（T15）：任意 A 位可为对应机壳或能源 hatch 二态（{@link #tieredCasingElement()}），
 * 能源仓统一写入 mEnergyHatches、不计入 casingFamilyTier，上限=本单元 A 位数量（每 A 位至多
 * 一枚）+P 位；checkMachine 校验 {@code mEnergyHatches >= 1}（P 或 A 位满足均可）；运行判据=
 * 全部能源仓合计存量足额（共享 EU 池，跨仓扣减，总控不集中扣 EU，需求不随仓数量放大）。
 * EU 实扣（r6-S6，取代旧「真扣 1 EU 后返还」净零探测）：本环节按链步表实扣运行 EU/t——磁选
 * {@code MAGNETIC_EU_PER_TICK × MAGNETIC_AMPERAGE}=32、热离
 * {@code THERMOCENTRIFUGE_EU_PER_TICK × THERMOCENTRIFUGE_AMPERAGE}=96，均随单元档位再乘
 * {1,2,8,16}（T7）——在集群运行相位内每 tick
 * 持续真扣；能源不足 → 环节闸门关闭（{@code isModuleEnabled()=false}，链路不可经其执行，防免费
 * 运行），恢复供电自动恢复。r5 的「事件式结构重检」与「预热门控」不受影响：本类不改 checkMachine/
 * mStartUpCheck 路径，运行相位判据含满热（预热期不扣不判）。r9：统一加工矩阵已废弃——本类不再
 * 覆写 {@code getUnitShape()}（矩阵与 P 位字面由热离/磁选子类按权威规格各自持有），只保留 P 能源
 * 位元素注入。子类（加工类型/overlay/文案差异）经构造器注入，仅保留构造器与 newMetaEntity。
 */
public abstract class MTEUnitSelfPoweredProcessingUnit extends MTEBasicProcessingUnit {

    /**
     * 注册用构造器：声明式差异（链路/类型词条/overlay 对）经此透传 {@link MTEBasicProcessingUnit}。
     */
    protected MTEUnitSelfPoweredProcessingUnit(int aID, String aName, String aNameRegional, String unitTypeKey,
        IIconContainer overlayInactive, IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aID, aName, aNameRegional, unitTypeKey, overlayInactive, overlayActive, providedLinks);
    }

    /**
     * 运行期每 tick 实扣 EU（r6-S6）：按构造期注入的链步查表——磁选
     * {@code MAGNETIC_EU_PER_TICK × MAGNETIC_AMPERAGE}=32 EU/t、热离
     * {@code THERMOCENTRIFUGE_EU_PER_TICK × THERMOCENTRIFUGE_AMPERAGE}=96 EU/t，再按本单元
     * 结构 tier 乘功率档位倍数 {1,2,8,16}（T7）；无供电类链步为 0。
     */
    private long runEuPerTick = -1L;

    /** 克隆用构造器：多方块控制器仅需名称，声明式差异常量随类型一同透传。 */
    protected MTEUnitSelfPoweredProcessingUnit(String aName, String unitTypeKey, IIconContainer overlayInactive,
        IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aName, unitTypeKey, overlayInactive, overlayActive, providedLinks);
    }

    /**
     * P 能源位元素（r9：矩阵由子类各自持有，本类只注入能源位——标准 addEnergyInputToMachineList
     * 添加器；buildHatchAdder(具体类.class) 的类型令牌仅作编译期推断用，此处 getClass() 运行期等价）。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        builder.addElement('P', energyHatchElement());
    }

    /** 能源 hatch 元素（P 位与 A 位放宽共用，T15；casingIndex/hint 与原子类逐字一致）。 */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private IStructureElement energyHatchElement() {
        return buildHatchAdder(this.getClass()).atLeast(Energy)
            .casingIndex(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10))
            .hint(1)
            .build();
    }

    /**
     * A 外壳位放宽（T15）：tiered 外壳（默认形态，基类四族 casing 元素原样保留——能源仓分支不写
     * casingFamilyTier）或 {@link #energyHatchElement()}——任意 A 机壳位可替换为能源 hatch（入
     * mEnergyHatches，成型末尾统一刷新贴图；每 A 位至多一枚，天然上限=本单元 A 位数量；重复注册
     * 由 GT5U checkStructure→clearHatches 每轮清表防双计）。与 P 位同元素（同 casingIndex/hint），
     * 误改全部 A 位为能源仓必选不会发生——外壳分支仍为默认首选形态。
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected IStructureElement tieredCasingElement() {
        return ofChain(super.tieredCasingElement(), energyHatchElement());
    }

    // ==================== 运行期 EU 真扣（r6-S6） ====================

    /**
     * 服务端每 tick 先行结算运行相位 EU 实扣，再交基类 setActive（{@code isUnitRunning()} 读到的是
     * 本 tick 扣电后的最新存量闸门）；客户端透传基类（'e' 粒子候选注册已上移单元基类）。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) tryDrainRunEu();
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    /**
     * 运行相位判定：自身成型 + 已入集群 + 集群开机 + 自身物理电源开 + 集群满热 + 链处理窗口激活
     * + 本环节实际参与最近一次成功批（r6-S6 审查修正：空闲保温期/未参与批不扣电）。不含本环节
     * 供电闸门本身，保证断电后恢复供电仍能被探测并自动复位。
     */
    private boolean isInPoweredRunPhase() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide()) return false;
        return isUnitStructureFormed() && cluster != null
            && cluster.isMachineEnabled()
            && base.isAllowedToWork()
            && cluster.isPreheatReady()
            && cluster.isChainWindowActive()
            && hasWorkInProgressLogisticsUnit()
            && participatedInCurrentBatch();
    }

    /** 任一物流单元当前存在未完成的真实工作进度。 */
    private boolean hasWorkInProgressLogisticsUnit() {
        if (cluster == null || cluster.getTopology() == null) return false;
        for (MTEBasicLogisticsUnit unit : cluster.getTopology()
            .getLogisticsUnits()) {
            if (unit != null && unit.isWorkInProgress()) return true;
        }
        return false;
    }

    /** 本环节是否参与最近一次成功批（r6-S8：EU 只为实际命中的链步扣，纯物流批/无关环节不扣）。 */
    private boolean participatedInCurrentBatch() {
        Set<ChainLink> batchLinks = cluster.getLastBatchLinks();
        if (batchLinks == null || batchLinks.isEmpty()) return false;
        for (ChainLink link : rawProvidedLinks()) {
            if (batchLinks.contains(link)) return true;
        }
        return false;
    }

    /** @return 本环节运行 EU/t（懒推导一次；无供电类链步恒 0）。 */
    private long runEuPerTickCost() {
        if (runEuPerTick < 0L) runEuPerTick = resolveRunEuPerTick(rawProvidedLinks(), getUnitStructureTier());
        return runEuPerTick;
    }

    /**
     * 链步 → EU/t 表：磁选 LV×1A、热离 LV×3A（合计口径取自 ClusterParams 安培常量），并按本单元
     * 结构 tier 乘功率档位倍数 {1,2,8,16}（T7）。
     */
    private static long resolveRunEuPerTick(Set<ChainLink> links, int unitTier) {
        long total = 0L;
        if (links == null) return 0L;
        int[] multipliers = ClusterParams.POWER_TIER_MULTIPLIERS;
        int mult = unitTier >= 0 && unitTier < multipliers.length ? multipliers[unitTier] : 1;
        for (ChainLink link : links) {
            switch (link) {
                case MAGNETIC_SEPARATOR:
                    total += (long) ClusterParams.MAGNETIC_EU_PER_TICK * ClusterParams.MAGNETIC_AMPERAGE * mult;
                    break;
                case THERMOCENTRIFUGE:
                    total += (long) ClusterParams.THERMOCENTRIFUGE_EU_PER_TICK * ClusterParams.THERMOCENTRIFUGE_AMPERAGE
                        * mult;
                    break;
                default:
                    break;
            }
        }
        return total;
    }

    /** @return 全部有效能源仓存量合计（EU）。 */
    private long storedEuAcrossHatches() {
        long stored = 0L;
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (hatch != null && hatch.isValid()) stored += hatch.getBaseMetaTileEntity()
                .getStoredEU();
        }
        return stored;
    }

    /**
     * 运行相位内每 tick 真扣本环节 EU/t（原子口径：先跨仓合计探测足额、足额才统一实扣——不足则
     * 全程零扣）。非运行相位不扣不判（保持现状），恢复供电后下一运行相位 tick 自动续扣。
     */
    private void tryDrainRunEu() {
        long need = runEuPerTickCost();
        if (need <= 0L) return;
        if (!isInPoweredRunPhase()) return;
        if (storedEuAcrossHatches() < need) return;
        long remaining = need;
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (remaining <= 0L) break;
            if (hatch == null || !hatch.isValid()) continue;
            IGregTechTileEntity hatchTe = hatch.getBaseMetaTileEntity();
            long take = Math.min(hatchTe.getStoredEU(), remaining);
            if (take > 0L) {
                hatchTe.decreaseStoredEnergyUnits(take, false);
                remaining -= take;
            }
        }
    }

    /**
     * 能源闸门：本环节运行 EU/t 能否由自身能源仓存量支付（只读探测，零副作用；实扣在
     * {@link #tryDrainRunEu} 运行相位逐 tick 进行）。全部能源仓存量不足时返回 false——
     * {@code isModuleEnabled()} 关闭使链路不可经其执行（防免费运行），补电后自动恢复。
     */
    private boolean canPayRunEnergy() {
        long need = runEuPerTickCost();
        return need <= 0L || storedEuAcrossHatches() >= need;
    }

    /**
     * 通电闸门：在基类「已成型 + 已入集群」之上追加自身能源可支付——断电即链路不可用
     * （getProvidedLinks 关闭），EU 由本单元自身能源位结算，总控不代扣。
     */
    @Override
    public boolean isModuleEnabled() {
        return super.isModuleEnabled() && canPayRunEnergy();
    }

    /** 运行信号：基类条件（成型 && 连接 && tier 有效）+ 自身能源可支付（只读存量判据）。 */
    @Override
    public boolean isUnitRunning() {
        return super.isUnitRunning() && canPayRunEnergy();
    }

    /**
     * 能源仓数量区间段（T15，子类仓室群共用）：下限 1（{@link #checkMachine} 强制），上限=本单元
     * shape 的 A 位数量+P 位（每个位置至多承载一枚能源仓，物理天然封顶，不设更紧的人为上限）。
     */
    protected String energyHatchCountRange() {
        return String.format("1-%d", countUnitShapeChar('A') + countUnitShapeChar('P'));
    }

    /**
     * 结构校验：基类 tier 校验之上要求至少一个能源 hatch（T15：P 位或 A 位能源仓满足均可，多仓
     * 合计共享 EU 池；重复注册由 GT5U checkStructure→clearHatches 每轮清表防双计）；成型成功末尾
     * 按 unitStructureTier 统一刷新全部能源仓贴图（切片 2 统一入口）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        runEuPerTick = -1L;
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (errors.isEmpty() && mEnergyHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.energy_hatch_missing"));
            return;
        }
        if (errors.isEmpty()) {
            refreshHatchTextures(mEnergyHatches);
        }
    }

    /** 状态细化：已入集群但断电（能源不可支付）→ NO_POWER_OR_INVALID；其余沿基类判定。 */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isModuleEnabled() && cluster != null) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        return super.getUnitStatus();
    }
}
