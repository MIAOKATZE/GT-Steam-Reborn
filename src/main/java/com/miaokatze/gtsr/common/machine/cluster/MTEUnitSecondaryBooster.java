package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 副产物增幅模块：按概率额外产出副产物；可多模块生效、加算。
 * <p>
 * 增益档位（结构 tier）：T1 +1% / T2 +2% / T3 +4% / T4 +5%（+副产物概率）。
 * links 串联可能指数叠加，数值刻意压低；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#SECONDARY_OUTPUT}。
 * <p>
 * 锁定流体：硫酸（SulfuricAcid，{@code Materials.SulfuricAcid}）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.secondary。
 * <p>
 * 正面 overlay 引用流体加热器（fluid_heater）原机资源（gregtech 域，无 BlockIcons 常量，走 customOptional；
 * 该目录无 GLOW 变体，仅 inactive/active 两组）；类加载期（贴图缝合前）以 static final 字段绑定，禁止移入方法体。
 */
public class MTEUnitSecondaryBooster extends MTEBasicAmplifierUnit {

    /** 正面 overlay（inactive）：{@code basicmachines/fluid_heater/OVERLAY_FRONT}。 */
    private static final IIconContainer OVERLAY_INACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/fluid_heater/OVERLAY_FRONT");

    /** 正面 overlay（active）：{@code basicmachines/fluid_heater/OVERLAY_FRONT_ACTIVE}。 */
    private static final IIconContainer OVERLAY_ACTIVE = Textures.BlockIcons
        .customOptional("basicmachines/fluid_heater/OVERLAY_FRONT_ACTIVE");

    public MTEUnitSecondaryBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.SECONDARY_OUTPUT);
    }

    public MTEUnitSecondaryBooster(String aName) {
        super(aName, ClusterParams.BoosterType.SECONDARY_OUTPUT);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSecondaryBooster(mName);
    }

    /** 锁定流体=硫酸（解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.SECONDARY_OUTPUT);
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
