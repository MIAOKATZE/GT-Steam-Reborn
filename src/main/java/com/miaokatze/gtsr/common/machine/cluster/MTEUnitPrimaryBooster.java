package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 主产物增幅模块：按概率令单物品额外 +1 主产物；同类多模块时只生效 1 个（取其一，不叠加）。
 * <p>
 * 增益档位（结构 tier）：T1 +5% / T2 +10% / T3 +15% / T4 +20%（+1 主产物概率）。
 * 工作模块复数只减 link 时间，不参与主产物增幅；蒸汽惩罚乘子见 {@link ClusterParams.BoosterType#PRIMARY_OUTPUT}。
 * <p>
 * 锁定流体：氨气（Ammonia，气态材料，经 {@code getGas(1).getFluid()} 取得）。缺流体时本模块增益失效
 * （状态=缺增幅流体紫）；类型名 key：gtsr.gui.cluster.unit_type.booster.primary。
 */
public class MTEUnitPrimaryBooster extends MTEBasicAmplifierUnit {

    public MTEUnitPrimaryBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    public MTEUnitPrimaryBooster(String aName) {
        super(aName, ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitPrimaryBooster(mName);
    }

    /** 锁定流体=氨气（气态，解析集中在上层 {@link #resolveBoosterFluid}，null 安全）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.PRIMARY_OUTPUT);
    }

    /** tooltip：类型行 + 四档增益行 + 锁定流体行 + 缺流体失效红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
