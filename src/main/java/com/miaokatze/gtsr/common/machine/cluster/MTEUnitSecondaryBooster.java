package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

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
 */
public class MTEUnitSecondaryBooster extends MTEBasicAmplifierUnit {

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

    /** tooltip：类型行 + 四档增益行 + 锁定流体行 + 缺流体失效红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
