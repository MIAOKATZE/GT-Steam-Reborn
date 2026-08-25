package com.miaokatze.gtsr.common.machine.cluster;

import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

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
 * 在 {@link MTEBasicProcessingUnit} 的能力闸门之上叠加「自持能源」语义：背面中心 (2,4,4) 一带
 * {@code DAAAD→DAPAD}，P=自身能源位（标准能源 hatch 添加器），checkMachine 校验
 * {@code mEnergyHatches >= 1}（无 P 不成型）；运行判据=自身 {@code drainEnergyInput} 本 tick
 * 可支付（总控不集中扣 EU）。EU 探测保持原样：真扣 1 EU 后立即返还（净零，仅记录项的已知副作用
 * 不动）。子类（加工类型/overlay/文案差异）经构造器注入，仅保留构造器与 newMetaEntity。
 */
public abstract class MTEUnitSelfPoweredProcessingUnit extends MTEBasicProcessingUnit {

    /**
     * 注册用构造器：声明式差异（链路/类型词条/overlay 对）经此透传 {@link MTEBasicProcessingUnit}。
     */
    protected MTEUnitSelfPoweredProcessingUnit(int aID, String aName, String aNameRegional, String unitTypeKey,
        IIconContainer overlayInactive, IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aID, aName, aNameRegional, unitTypeKey, overlayInactive, overlayActive, providedLinks);
    }

    /** 克隆用构造器：多方块控制器仅需名称，声明式差异常量随类型一同透传。 */
    protected MTEUnitSelfPoweredProcessingUnit(String aName, String unitTypeKey, IIconContainer overlayInactive,
        IIconContainer overlayActive, ChainLink... providedLinks) {
        super(aName, unitTypeKey, overlayInactive, overlayActive, providedLinks);
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

    /**
     * 基类元素绑定之上追加 P：能源位（atLeast(Energy)，标准 addEnergyInputToMachineList 添加器；
     * 原 buildHatchAdder(具体类.class) 的类型令牌仅作编译期推断用，此处 getClass() 运行期等价）。
     */
    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        super.addUnitStructureElements(builder);
        builder.addElement('P', energyHatchElement());
    }

    /** P 位能源 hatch 元素（casingIndex/hint 与原子类逐字一致）。 */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private IStructureElement energyHatchElement() {
        return buildHatchAdder(this.getClass()).atLeast(Energy)
            .casingIndex(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10))
            .hint(1)
            .build();
    }

    /**
     * 结构校验：基类 tier 校验之上要求自身能源位至少一个能源 hatch（无 P 不成型）；成型成功末尾按
     * unitStructureTier 刷新能源仓贴图（切片 2 统一入口）。
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        if (errors.isEmpty() && mEnergyHatches.isEmpty()) {
            errors.add(new ClusterStructureError("gtsr.gui.cluster.structure.energy_hatch_missing"));
            return;
        }
        if (errors.isEmpty()) {
            refreshHatchTextures(mEnergyHatches);
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
}
