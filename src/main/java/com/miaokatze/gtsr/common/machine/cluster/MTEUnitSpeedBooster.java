package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 速度增幅模块：配方增速（百分比，不减小实际秒数），可多模块生效、加算后作为独立乘算层。
 * <p>
 * 增益档位（结构 tier）：T1 +5% / T2 +10% / T3 +30% / T4 +40%。
 * 与 tier 时间因子（减小实际秒数）相乘缩短总耗时；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#SPEED}。
 * <p>
 * 锁定流体：盐酸（HydrochloricAcid，{@code Materials.HydrochloricAcid}）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.speed。
 * <p>
 * 正面 overlay 引用装配机（assembler）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional）；
 * 类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitSpeedBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/assembler/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/assembler/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/assembler/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/assembler/OVERLAY_FRONT_ACTIVE");

    public MTEUnitSpeedBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.SPEED);
    }

    public MTEUnitSpeedBooster(String aName) {
        super(aName, ClusterParams.BoosterType.SPEED);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSpeedBooster(mName);
    }

    /** 锁定流体=盐酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.SPEED);
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
