package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 工作单元：磁选机（能力闸门 + 自持能源）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（磁选 MAGNETIC_SEPARATOR），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase；自持能源语义（P 能源位/
 * 成型校验/EU 探测/断电闸门）全部继承自 {@link MTEUnitSelfPoweredProcessingUnit}。
 *
 * <p>
 * overlay：GT5U 电磁选矿机前脸 inactive/active（四态常量存在，绑定前两态，glow 由基类统一）。
 */
public class MTEUnitMagneticSeparator extends MTEUnitSelfPoweredProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.magnetic_separator";

    /** 本单元解锁的链路：磁选。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.MAGNETIC_SEPARATOR };

    /** 注册用构造器。 */
    public MTEUnitMagneticSeparator(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_EMS,
            Textures.BlockIcons.OVERLAY_FRONT_EMS_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitMagneticSeparator(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_EMS,
            Textures.BlockIcons.OVERLAY_FRONT_EMS_ACTIVE,
            PROVIDED_LINKS);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitMagneticSeparator(mName);
    }
}
