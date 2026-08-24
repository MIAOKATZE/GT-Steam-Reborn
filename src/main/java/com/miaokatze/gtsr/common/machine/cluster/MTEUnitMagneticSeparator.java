package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/**
 * 工作单元：磁选机（能力闸门 + 自持能源）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（磁选 MAGNETIC_SEPARATOR），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase。
 *
 * <p>
 * 背面中心 (2,4,4) 一带 {@code DAAAD→DAPAD}：P=自身能源位（标准能源 hatch 添加器），
 * {@code checkMachine} 校验 {@code mEnergyHatches >= 1}；运行判据=自身 {@code drainEnergyInput}
 * 本 tick 可支付（总控不再集中扣 EU）。
 */
public class MTEUnitMagneticSeparator extends MTEBasicProcessingUnit {

    /** 本单元解锁的链路：磁选。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.MAGNETIC_SEPARATOR };

    /** 注册用构造器。 */
    public MTEUnitMagneticSeparator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitMagneticSeparator(String aName) {
        super(aName, PROVIDED_LINKS);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitMagneticSeparator(mName);
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.magnetic_separator";
    }

    /**
     * 统一加工矩阵 + 背面中心能源位：与基类矩阵逐字符一致，仅 z=4 行 y=4 的
     * {@code "DAAAD"→"DAPAD"}（P=(2,4,4)）。
     */
    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { " DDD ", " AAA ", "DAAAD", "DAAAD", "DA~AD", "DAAAD", "AAAAA" },
            { "DFAFD", "ACCCA", "E-B-E", "E---E", "E-B-E", "ABCBA", "AACAA" },
            { "DAAAD", "ACCCA", "EBBBE", "E---E", "EBBBE", "ACCCA", "CCCCC" },
            { "DFAFD", "ACCCA", "E-B-E", "E---E", "E-B-E", "ABCBA", "AACAA" },
            { " DDD ", " AAA ", "DAAAD", "DAAAD", "DAPAD", "DAAAD", "AAAAA" }, };
    }

    /** 基类元素绑定之上追加 P：能源位（atLeast(Energy)，标准 addEnergyInputToMachineList 添加器）。 */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        super.addUnitStructureElements(builder);
        builder.addElement(
            'P',
            buildHatchAdder(MTEUnitMagneticSeparator.class).atLeast(Energy)
                .casingIndex(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10))
                .hint(1)
                .build());
    }

    /** 结构校验：基类 tier 校验之上要求自身能源位至少一个能源 hatch（无 P 不成型）。 */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (errors.isEmpty() && mEnergyHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.energy_hatch_missing"));
        }
    }

    /**
     * 能源探测：本 tick 自身能源仓能否支付 1 EU——{@code drainEnergyInput} 真扣 1 EU 后立即向首个
     * 有效能源仓返还（净零，探测不消耗）；全部能源仓枯竭时返回 false。
     */
    private boolean canPayEnergyProbe() {
        if (!drainEnergyInput(1L)) return false;
        for (MTEHatchEnergy hatch : mEnergyHatches) {
            if (hatch.isValid()) {
                hatch.getBaseMetaTileEntity()
                    .increaseStoredEnergyUnits(1L, true);
                break;
            }
        }
        return true;
    }

    /**
     * 通电闸门：在基类「已成型 + 已入集群」之上追加自身能源可支付——断电即链路不可用
     * （getProvidedLinks 关闭），EU 由本单元自身能源位结算，总控不代扣。
     */
    @Override
    public boolean isModuleEnabled() {
        return super.isModuleEnabled() && canPayEnergyProbe();
    }

    /** 运行信号：基类条件（成型 && 连接 && tier 有效）+ 自身能源可支付（探测性扣返）。 */
    @Override
    public boolean isUnitRunning() {
        return super.isUnitRunning() && canPayEnergyProbe();
    }

    /** 状态细化：已入集群但断电（能源不可支付）→ NO_POWER_OR_INVALID；其余沿基类判定。 */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isModuleEnabled() && cluster != null) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        return super.getUnitStatus();
    }

    /** overlay：GT5U 电磁选矿机前脸 inactive/active（四态常量存在，本切片绑定前两态，glow 由基类统一）。 */
    @Override
    public IIconContainer unitOverlayInactive() {
        return Textures.BlockIcons.OVERLAY_FRONT_EMS;
    }

    @Override
    public IIconContainer unitOverlayActive() {
        return Textures.BlockIcons.OVERLAY_FRONT_EMS_ACTIVE;
    }

    /** tooltip：类型行 + 解锁链步行 + 放置提示 + 自身能源红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */
}
