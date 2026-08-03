package com.miaokatze.gtsr.api.compat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

/**
 * 蒸汽冷却支持公共逻辑工具类。
 * <p>
 * 抽取自 {@link com.miaokatze.gtsr.mixin.MTESteamMultiBaseMixin}，供 GTSR 父类 mixin 与
 * GTNL 专用 mixin 共享，避免代码重复。所有方法均为静态，工具类风格，不可实例化。
 * <p>
 * 通过 {@link ICoolingHatchHolder} 接口访问冷却舱室列表与累积蒸汽状态，
 * 使 GTNL 子类 mixin 能间接访问父类 mixin 注入的 @Unique private 字段。
 */
public final class SteamCoolingSupport {

    /** 普通蒸汽转冷却水的比例：160L 蒸汽 = 1L 水 */
    private static final int STEAM_PER_WATER = 160;

    /** 私有构造器，工具类不可实例化 */
    private SteamCoolingSupport() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 推送冷却产物到冷却舱室。
     * <p>
     * 过热蒸汽 → 遍历压力冷却舱室调用 {@code pushCoolingSteam}；
     * 普通蒸汽 → 累积到 holder，每 160L 转 1L 水推送至普通冷却舱室。
     *
     * @param holder        冷却舱室持有者
     * @param steamConsumed 本次消耗的蒸汽量
     * @param isSuperheated 是否为过热蒸汽
     */
    public static void pushCoolingProducts(ICoolingHatchHolder holder, int steamConsumed, boolean isSuperheated) {
        if (isSuperheated) {
            // 过热蒸汽 → 推送至压力冷却舱室（转为普通蒸汽存储）
            for (MTEPressureSteamCoolingHatch hatch : holder.gtsr$getPressureHatches()) {
                if (hatch != null && hatch.isValid()) {
                    hatch.pushCoolingSteam(steamConsumed);
                }
            }
        } else {
            // 普通蒸汽 → 累积，每 160L 转 1L 水推送至普通冷却舱室
            holder.gtsr$setAccumulatedSteam(holder.gtsr$getAccumulatedSteam() + steamConsumed);
            int waterAmount = holder.gtsr$getAccumulatedSteam() / STEAM_PER_WATER;
            if (waterAmount > 0) {
                holder.gtsr$setAccumulatedSteam(holder.gtsr$getAccumulatedSteam() % STEAM_PER_WATER);
                for (MTESteamCoolingHatch hatch : holder.gtsr$getCoolingHatches()) {
                    if (hatch != null && hatch.isValid()) {
                        hatch.pushCoolingWater(waterAmount);
                    }
                }
            }
        }
    }

    /**
     * 更新冷却仓底材纹理（v1.9.41 新增）。
     * <p>
     * 蒸汽机器的 {@code updateHatchTexture()} 必须调用本方法，否则冷却仓底材停留在
     * 结构 adder 初始化的最低 tier 材质，分级机器升级后显示错误。
     *
     * @param holder    冷却舱室持有者
     * @param textureID 当前机器 tier 对应的外壳贴图索引
     */
    public static void updateHatchTextures(ICoolingHatchHolder holder, int textureID) {
        for (MTESteamCoolingHatch hatch : holder.gtsr$getCoolingHatches()) {
            if (hatch != null && hatch.isValid()) {
                hatch.updateTexture(textureID);
            }
        }
        for (MTEPressureSteamCoolingHatch hatch : holder.gtsr$getPressureHatches()) {
            if (hatch != null && hatch.isValid()) {
                hatch.updateTexture(textureID);
            }
        }
    }

    /**
     * 检测蒸汽输入舱室中是否含有过热蒸汽。
     * <p>
     * 遍历 {@code mSteamInputFluids}（public 字段），检查是否存在
     * {@code ic2superheatedsteam} 且 amount > 0。
     *
     * @param self 蒸汽多方块机器实例
     * @return 含有过热蒸汽返回 true，否则 false
     */
    public static boolean hasSuperheatedSteam(MTESteamMultiBlockBase self) {
        // GT5U jar 中 mSteamInputFluids 未保留泛型签名（原始类型 ArrayList），需显式 instanceof + 强转
        for (Object hatchObj : self.mSteamInputFluids) {
            if (!(hatchObj instanceof MTEHatchCustomFluidBase)) continue;
            MTEHatchCustomFluidBase hatch = (MTEHatchCustomFluidBase) hatchObj;
            FluidStack fluid = hatch.getFluid();
            if (fluid != null && fluid.getFluid() != null
                && "ic2superheatedsteam".equals(
                    fluid.getFluid()
                        .getName())
                && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清空冷却舱室列表与累积蒸汽状态。
     * <p>
     * 在 {@code clearHatches} 注入中调用，确保机器结构重建时清理冷却舱室引用。
     *
     * @param holder 冷却舱室持有者
     */
    public static void clearHatches(ICoolingHatchHolder holder) {
        holder.gtsr$getCoolingHatches()
            .clear();
        holder.gtsr$getPressureHatches()
            .clear();
        holder.gtsr$setAccumulatedSteam(0);
    }

    /**
     * 保存累积蒸汽状态到 NBT。
     *
     * @param holder 冷却舱室持有者
     * @param aNBT   NBT 标签
     */
    public static void saveNBT(ICoolingHatchHolder holder, NBTTagCompound aNBT) {
        aNBT.setInteger("gtsr.accumulatedSteam", holder.gtsr$getAccumulatedSteam());
    }

    /**
     * 从 NBT 加载累积蒸汽状态。
     *
     * @param holder 冷却舱室持有者
     * @param aNBT   NBT 标签
     */
    public static void loadNBT(ICoolingHatchHolder holder, NBTTagCompound aNBT) {
        holder.gtsr$setAccumulatedSteam(aNBT.getInteger("gtsr.accumulatedSteam"));
    }
}
