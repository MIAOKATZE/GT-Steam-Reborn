package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;

/**
 * 并行增幅模块：把增益叠加到所接入物流模块的并行数上，可多模块生效、加算。
 * <p>
 * 增益档位（结构 tier）：T1 +4 / T2 +8 / T3 +32 / T4 +48（每批并行物品数）。
 * 生效前提与蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#PARALLEL}。
 * <p>
 * 锁定流体：硝酸（NitricAcid，{@code Materials.NitricAcid}）。tank 缺流体时本模块增益失效
 * （状态=缺增幅流体紫），不影响集群结构成型；类型名 key：gtsr.gui.cluster.unit_type.booster.parallel。
 * <p>
 * 正面 overlay 忠实跨域引用 GT++ 亚马逊包装机资源（miscutils 域 amazonPackager*，不复制 PNG）；
 * 变体选择对齐 MTEAmazonPackagerLegacy：inactive=oMCAAmazonPackager、active=oMCAAmazonPackagerActive。
 */
public class MTEUnitParallelBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：GT++ {@code TexturesGtBlock.oMCAAmazonPackager}。类加载期绑定，禁止移入方法体。 */
    private static final IIconContainer OVERLAY_INACTIVE = TexturesGtBlock.oMCAAmazonPackager;

    /** 正面 overlay（active）：GT++ {@code TexturesGtBlock.oMCAAmazonPackagerActive}。类加载期绑定。 */
    private static final IIconContainer OVERLAY_ACTIVE = TexturesGtBlock.oMCAAmazonPackagerActive;

    public MTEUnitParallelBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.PARALLEL);
    }

    public MTEUnitParallelBooster(String aName) {
        super(aName, ClusterParams.BoosterType.PARALLEL);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitParallelBooster(mName);
    }

    /** 锁定流体=硝酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.PARALLEL);
    }

    @Override
    protected IIconContainer unitOverlayInactive() {
        return OVERLAY_INACTIVE;
    }

    @Override
    protected IIconContainer unitOverlayActive() {
        return OVERLAY_ACTIVE;
    }

    /** tooltip：类型行 + 四档增益行 + 锁定流体行 + 缺流体失效红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
