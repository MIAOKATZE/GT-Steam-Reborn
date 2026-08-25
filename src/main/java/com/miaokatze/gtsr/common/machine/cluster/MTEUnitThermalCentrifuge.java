package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 工作单元：热力离心机（能力闸门 + 自持能源）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（热离心 THERMOCENTRIFUGE），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase；自持能源语义（P 能源位/
 * 成型校验/EU 探测/断电闸门）全部继承自 {@link MTEUnitSelfPoweredProcessingUnit}。
 *
 * <p>
 * overlay：GT++ 工业热力离心机前脸（忠实引用原资源域，不复制 PNG）。
 */
public class MTEUnitThermalCentrifuge extends MTEUnitSelfPoweredProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.thermal_centrifuge";

    /** 本单元解锁的链路：热离心。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.THERMOCENTRIFUGE };

    /** 注册用构造器。 */
    public MTEUnitThermalCentrifuge(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialThermalCentrifuge,
            TexturesGtBlock.oMCDIndustrialThermalCentrifugeActive,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitThermalCentrifuge(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialThermalCentrifuge,
            TexturesGtBlock.oMCDIndustrialThermalCentrifugeActive,
            PROVIDED_LINKS);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitThermalCentrifuge(mName);
    }
}
