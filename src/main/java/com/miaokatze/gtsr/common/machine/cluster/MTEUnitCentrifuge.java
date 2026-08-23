package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 工作单元：离心机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（离心分离 CENTRIFUGE），自身零配方执行；配方匹配与执行由集群总控侧
 * 完成。纹理、集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase。
 */
public class MTEUnitCentrifuge extends MTEBasicProcessingUnit {

    /** 本单元解锁的链路：离心分离。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.CENTRIFUGE };

    /** 注册用构造器。 */
    public MTEUnitCentrifuge(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitCentrifuge(String aName) {
        super(aName, PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCentrifuge(mName);
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.centrifuge";
    }

    /** tooltip：类型行 + 解锁链步行 + 放置提示，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
