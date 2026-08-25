package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 工作单元：熔炉（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（熔炼 FURNACE），自身零配方执行；配方匹配与执行由集群总控侧完成。
 * 纹理、集群接线与流体缓冲等公共行为全部继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；
 * overlay 取 GT5U 蒸汽熔炉前脸 inactive/active（同 GTSR 大型蒸汽熔炉 MTELargeSteamFurnace 绑定）。
 */
public class MTEUnitFurnace extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.furnace";

    /** 本单元解锁的链路：熔炼。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.FURNACE };

    /** 注册用构造器。 */
    public MTEUnitFurnace(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitFurnace(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE,
            PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitFurnace(mName);
    }
}
