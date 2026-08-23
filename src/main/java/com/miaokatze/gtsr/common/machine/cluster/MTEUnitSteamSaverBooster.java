package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraftforge.fluids.Fluid;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 蒸汽效率增幅模块：按比例节约集群运行蒸汽消耗；可多模块生效、加算，全集群总节约上限 48%。
 * <p>
 * 增益档位（结构 tier）：T1 -2% / T2 -4% / T3 -8% / T4 -12%（运行蒸汽）。
 * 总蒸汽公式中作为 (1 − min(48%, Σ节汽%)) 乘算层；惩罚乘子见 {@link ClusterParams.BoosterType#STEAM_SAVER}。
 * <p>
 * 锁定流体：冷却液——优先取 {@code FluidRegistry.getFluid(ClusterParams.BOOSTER_COOLANT_FLUID)} 注册名，
 * 缺失时回退超冷却液（SuperCoolant）；两者都不可用时本模块增幅禁用（isFluidAvailable 恒 false，不崩）。
 * 类型名 key：gtsr.gui.cluster.unit_type.booster.steam_saver。
 */
public class MTEUnitSteamSaverBooster extends MTEBasicAmplifierUnit {

    public MTEUnitSteamSaverBooster(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, ClusterParams.BoosterType.STEAM_SAVER);
    }

    public MTEUnitSteamSaverBooster(String aName) {
        super(aName, ClusterParams.BoosterType.STEAM_SAVER);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEUnitSteamSaverBooster(mName);
    }

    /** 锁定流体=冷却液（注册名可配，回退 SuperCoolant；解析集中在上层 {@link #resolveBoosterFluid}）。 */
    @Override
    protected Fluid resolveLockedFluid() {
        return resolveBoosterFluid(ClusterParams.BoosterType.STEAM_SAVER);
    }

    /** tooltip：类型行 + 四档增益行 + 锁定流体行 + 缺流体失效红字行，AddedBy 收尾（写法对齐 MTESteamInputHatchGeneric）。 */

}
