package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 工作单元：筛选机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（筛分 SIFTER），自身零配方执行；配方匹配与执行由集群总控侧完成。
 * 纹理、集群接线与流体缓冲等公共行为全部继承自 MTEBasicProcessingUnit/MTEClusterUnitBase；
 * overlay 取 GT++ 工业筛选机前脸 inactive/active（常量直引，忠实引用原资源域，不复制 PNG）。
 */
public class MTEUnitSifter extends MTEBasicProcessingUnit {

    /** GUI 类型词条 key。 */
    private static final String TYPE_NAME_KEY = "gtsr.gui.cluster.unit_type.sifter";

    /** 本单元解锁的链路：筛分。 */
    private static final ChainLink[] PROVIDED_LINKS = { ChainLink.SIFTER };

    /** 注册用构造器。 */
    public MTEUnitSifter(int aID, String aName, String aNameRegional) {
        super(
            aID,
            aName,
            aNameRegional,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialSifter,
            TexturesGtBlock.oMCDIndustrialSifterActive,
            PROVIDED_LINKS);
    }

    /** 克隆用构造器（newMetaEntity 落点）。 */
    public MTEUnitSifter(String aName) {
        super(
            aName,
            TYPE_NAME_KEY,
            TexturesGtBlock.oMCDIndustrialSifter,
            TexturesGtBlock.oMCDIndustrialSifterActive,
            PROVIDED_LINKS);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSifter(mName);
    }
}
