package com.miaokatze.gtsr.common.machine.turbine;

import java.util.List;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;

import gregtech.api.enums.Materials;
import gregtech.api.util.GTModHandler;

/**
 * 巨型蒸汽轮机「蒸汽类型域」单点工具类（O2-04/A01 段 1 自 MTEMegaSteamTurbineArray 外移）。
 *
 * <p>
 * 纯函数无状态：SteamType 枚举、优先级、流体分类、总量求和与芯片因子换算的单一事实来源。
 * 实例状态（芯片槽位、叠加层数、仓室列表）由机器侧以显式参数传入，本类不持有机器状态。
 */
public final class SteamTurbineSteamTypes {

    private SteamTurbineSteamTypes() {}

    public enum SteamType {

        NONE(0, 0, 0, ""),
        STEAM(0.5f, 0.5f, 15000, "gtsr.gui.steam_type.normal"),
        DENSE_STEAM(0.5f, 500, 20000, "gtsr.gui.steam_type.dense"),
        SH_STEAM(1.0f, 1.0f, 20000, "gtsr.gui.steam_type.superheated"),
        DENSE_SH_STEAM(1.0f, 1000, 25000, "gtsr.gui.steam_type.dense_superheated"),
        SC_STEAM(1.0f, 1.0f, 25000, "gtsr.gui.steam_type.supercritical"),
        DENSE_SC_STEAM(1.0f, 1000, 30000, "gtsr.gui.steam_type.dense_supercritical");

        public final float steamEffFactor;
        public final float euPerL;
        public final int maxEfficiency;
        public final String nameKey;

        SteamType(float steamEffFactor, float euPerL, int maxEfficiency, String nameKey) {
            this.steamEffFactor = steamEffFactor;
            this.euPerL = euPerL;
            this.maxEfficiency = maxEfficiency;
            this.nameKey = nameKey;
        }

        boolean isDense() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == DENSE_SC_STEAM;
        }

        public boolean requiresHighTier() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == SC_STEAM || this == DENSE_SC_STEAM;
        }
    }

    public static final SteamType[] PRIORITY = { SteamType.DENSE_SC_STEAM, SteamType.SC_STEAM, SteamType.DENSE_SH_STEAM,
        SteamType.DENSE_STEAM, SteamType.SH_STEAM, SteamType.STEAM };

    public static SteamType classifyFluid(FluidStack fs) {
        if (fs == null || fs.amount <= 0) return SteamType.NONE;
        if (isDenseSupercriticalSteamFluid(fs)) return SteamType.DENSE_SC_STEAM;
        if (isSupercriticalSteamFluid(fs)) return SteamType.SC_STEAM;
        if (isDenseSuperheatedSteamFluid(fs)) return SteamType.DENSE_SH_STEAM;
        if (isDenseSteamFluid(fs)) return SteamType.DENSE_STEAM;
        if (GTModHandler.isSuperHeatedSteam(fs)) return SteamType.SH_STEAM;
        if (GTModHandler.isAnySteam(fs)) return SteamType.STEAM;
        return SteamType.NONE;
    }

    private static boolean isDenseSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSteam.mGas;
    }

    private static boolean isDenseSuperheatedSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSuperheatedSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSuperheatedSteam.mGas;
    }

    private static boolean isSupercriticalSteamFluid(FluidStack fs) {
        if (fs == null) return false;
        Fluid scFluid = FluidRegistry.getFluid("supercriticalsteam");
        return scFluid != null && fs.getFluid() == scFluid;
    }

    private static boolean isDenseSupercriticalSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSupercriticalSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSupercriticalSteam.mGas;
    }

    /**
     * 循环超限芯片：仅提升输出侧蒸汽转换倍率，蒸汽家族内叠加，致密族只叠加致密族。
     * 未激活（芯片未装或叠加层不足）或 NONE 时返回类型自身因子。
     * 因子值以 SteamType.steamEffFactor 字段为单一事实来源，不在此硬编码。
     */
    public static float effectiveSteamEffFactor(SteamType type, boolean overlimitActive) {
        if (!overlimitActive || type == SteamType.NONE) {
            return type.steamEffFactor;
        }
        return switch (type) {
            case STEAM -> SteamType.STEAM.steamEffFactor;
            case SH_STEAM -> SteamType.SH_STEAM.steamEffFactor + SteamType.STEAM.steamEffFactor;
            case SC_STEAM -> SteamType.SC_STEAM.steamEffFactor + SteamType.SH_STEAM.steamEffFactor
                + SteamType.STEAM.steamEffFactor;
            case DENSE_STEAM -> SteamType.DENSE_STEAM.steamEffFactor;
            case DENSE_SH_STEAM -> SteamType.DENSE_SH_STEAM.steamEffFactor + SteamType.DENSE_STEAM.steamEffFactor;
            case DENSE_SC_STEAM -> SteamType.DENSE_SC_STEAM.steamEffFactor + SteamType.DENSE_SH_STEAM.steamEffFactor
                + SteamType.DENSE_STEAM.steamEffFactor;
            default -> type.steamEffFactor;
        };
    }

    /**
     * 输出倍率配套的等效 EU/L。将芯片倍率同时应用到分母，使同一蒸汽类型的消耗保持不变。
     */
    public static float effectiveSteamEuPerL(SteamType type, boolean overlimitActive) {
        if (type == SteamType.NONE || type.steamEffFactor <= 0.0f) return type.euPerL;
        return type.euPerL * effectiveSteamEffFactor(type, overlimitActive) / type.steamEffFactor;
    }

    // v1.10.61：求和 long 化——跨仓同型蒸汽累加可超 int max（2.147B L）溢出为负，
    // 与 checkProcessing 门控（consumption 为 long）配套，避免永久假 NO_FUEL。
    public static long totalSteamAmount(SteamType type, List<FluidStack> stored,
        List<MTEHatchPressureSteamInput> pressureInputs, List<MTEOverpressureTurbineInputHatch> overpressureInputs) {
        long total = 0;
        for (FluidStack fs : stored) {
            if (classifyFluid(fs) == type) total += fs.amount;
        }
        for (MTEHatchPressureSteamInput hatch : pressureInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) total += fs.amount;
        }
        for (MTEOverpressureTurbineInputHatch hatch : overpressureInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) total += fs.amount;
        }
        return total;
    }
}
