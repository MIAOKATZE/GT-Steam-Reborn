package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 工作单元：热离心机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（热离心 THERMOCENTRIFUGE），自身零配方执行；配方匹配与执行由集群
 * 总控侧完成。纹理、集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase。
 *
 * <p>
 * 需集群能源仓持续供电（用户拍板，{@code ClusterParams.THERMOCENTRIFUGE_EU_PER_TICK}）：停电时本单元
 * 链路应视为不可用，通电闸门在总控侧判定（{@code cluster.isPoweredUnitActive}，总控切片提供），
 * EU 消耗在总控 tick 统一结算，本单元不做任何能源读写。
 */
public class MTEUnitThermalCentrifuge extends MTEBasicProcessingUnit {

    /** 本单元解锁的链路：热离心。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.THERMOCENTRIFUGE };

    /** 注册用构造器。 */
    public MTEUnitThermalCentrifuge(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitThermalCentrifuge(String aName) {
        super(aName, PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitThermalCentrifuge(mName);
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.thermal_centrifuge";
    }

    /**
     * 通电闸门：在基类"已入集群"之上追加总控侧通电判定（{@code cluster.isPoweredUnitActive(this)}，
     * 总控切片提供）——需集群能源仓持续供电（用户拍板），EU 消耗在总控 tick 统一结算，
     * 本单元不做任何能源读写。
     */
    @Override
    public boolean isModuleEnabled() {
        return super.isModuleEnabled() && cluster != null && cluster.isPoweredUnitActive(this);
    }

    /** 状态细化：已入集群但通电闸门未过（断电/无效）→ NO_POWER_OR_INVALID；其余沿基类判定。 */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isModuleEnabled() && cluster != null) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        return super.getUnitStatus();
    }

    /** tooltip：类型行 + 解锁链步行 + 放置提示 + 供电红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
