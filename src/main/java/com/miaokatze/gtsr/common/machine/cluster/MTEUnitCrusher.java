package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 工作单元：粉碎机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石粉碎 CRUSH / 锤击粉碎 HAMMER），自身零配方执行；配方匹配与
 * 执行由集群总控侧完成。纹理、集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase。
 */
public class MTEUnitCrusher extends MTEBasicProcessingUnit {

    /** 本单元解锁的链路：矿石粉碎与打粉锤击两条前置破碎链。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.CRUSH, ChainLink.HAMMER };

    /** 注册用构造器。 */
    public MTEUnitCrusher(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitCrusher(String aName) {
        super(aName, PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCrusher(mName);
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.crusher";
    }

    /** tooltip：类型行 + 解锁链步行 + 放置提示，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
