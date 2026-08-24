package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * 工作单元：粉碎机（能力闸门）。
 *
 * <p>
 * 仅声明解锁的 ChainLink（矿石粉碎 CRUSH / 锤击粉碎 HAMMER），自身零配方执行；配方匹配与
 * 执行由集群总控侧完成。集群接线与流体缓冲等公共行为全部继承自 MTEClusterUnitBase。
 *
 * <p>
 * 贴图例外：GT5U 蒸汽粉碎机 overlay 为 TOP 常量（无对应 FRONT 变体），本类覆写
 * {@link #getTexture} 把 overlay 放到顶面（side==UP）；底材走基类 tier 联动。
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
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitCrusher(mName);
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.crusher";
    }

    /** overlay：GT5U 蒸汽粉碎机 TOP 面 inactive/active（常量直引，无 glow）。 */
    @Override
    public IIconContainer unitOverlayInactive() {
        return Textures.BlockIcons.OVERLAY_TOP_STEAM_MACERATOR;
    }

    @Override
    public IIconContainer unitOverlayActive() {
        return Textures.BlockIcons.OVERLAY_TOP_STEAM_MACERATOR_ACTIVE;
    }

    /**
     * TOP overlay 面处理（参考 GT5U MTESteamMacerator 族）：顶面（side==UP）= tier 底材 + 启停
     * overlay（无 glow，TextureFactory.of 直接叠层，NEI 安全零分配）；其余面仅 tier 底材。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(casingTextureIdForTier(getUnitStructureTier()));
        if (side == ForgeDirection.UP) {
            return new ITexture[] { casing, TextureFactory.of(active ? unitOverlayActive() : unitOverlayInactive()) };
        }
        return new ITexture[] { casing };
    }

    /** tooltip：类型行 + 解锁链步行 + 放置提示，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */
}
