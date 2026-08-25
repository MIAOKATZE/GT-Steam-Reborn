package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 工作单元：粉碎机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石粉碎 CRUSH / 锤击粉碎 HAMMER），自身零配方执行；配方匹配与
 * 执行由集群总控侧完成。集群接线与流体缓冲等公共行为全部继承自
 * MTEBasicProcessingUnit/MTEClusterUnitBase。
 *
 * <p>
 * 贴图：GT5U 蒸汽粉碎机前脸 overlay（OVERLAY_FRONT_STEAM_MACERATOR / _ACTIVE，无 glow）经
 * 基类构造器注入的常量对接入 {@code unitOverlayInactive}/{@code unitOverlayActive} 钩子，
 * 渲染面由基类 {@code getTexture} 的 side==facing 判定；底材走基类 tier 联动。
 */
public class MTEUnitCrusher extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.crusher";

    /** 本单元解锁的链路：矿石粉碎与打粉锤击两条前置破碎链。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.CRUSH, ChainLink.HAMMER };

    /** 注册用构造器。 */
    public MTEUnitCrusher(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR_ACTIVE,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitCrusher(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR,
            Textures.BlockIcons.OVERLAY_FRONT_STEAM_MACERATOR_ACTIVE,
            PROVIDED_LINKS);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCrusher(mName);
    }
}
