package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 工作单元：洗矿机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石清洗 ORE_WASH / 化学浴 CHEM_BATH / 简单清洗 SIMPLE_WASH），
 * 自身零配方执行；配方匹配与执行由集群总控侧完成。纹理、集群接线与流体缓冲等公共行为全部
 * 继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；overlay 取 GT5U 蒸汽洗矿机前脸
 * inactive/active（常量直引）。
 */
public class MTEUnitOreWasher extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.ore_washer";

    /** 本单元解锁的链路：矿石清洗、化学浴与简单清洗三条湿法链。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.ORE_WASH, ChainLink.CHEM_BATH,
        ChainLink.SIMPLE_WASH };

    /** 注册用构造器。 */
    public MTEUnitOreWasher(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitOreWasher(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_WASHER_ACTIVE,
            PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitOreWasher(mName);
    }
}
