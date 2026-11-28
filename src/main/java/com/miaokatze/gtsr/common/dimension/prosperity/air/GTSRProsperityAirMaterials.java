// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.prosperity.air;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtsr.common.dimension.prosperity.fluid.ProsperityFluidContainers;
import com.miaokatze.gtsr.common.dimension.prosperity.fluid.ProsperityNativeFluids;
import com.miaokatze.gtsr.main.GTSteamReborn;

import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.SubTag;
import gregtech.api.enums.TextureSet;
import gregtech.api.interfaces.IMaterialHandler;

/**
 * Existing public Materials facades for the six raw gases and abyssal liquid.
 * The existing GT material handler registers their independent stable-name fluids before init.
 * All facades remain outside GT numeric material slots; containers use native universal fluid cells.
 */
public class GTSRProsperityAirMaterials implements IMaterialHandler {

    /** 荒原叹息（en: Waste Sigh）——锈蚀草原 rustedSteppe 产出（用户口径，运行时查表见 ProsperityAirLookup）。 */
    public static Materials WastesSigh;
    /** 浓稠油污（en: Thick Grease）——齿轮森林 gearworkForest 产出（同上）。 */
    public static Materials ThickGrease;
    /** 金属风沙（en: Metal Grit）——黄铜荒漠 brassWastes 产出（同上）。 */
    public static Materials MetalGrit;
    /** 至暗泥泞（en: Umbral Mire）——起雾沼泽 fumaroleSwamp 产出（同上）。 */
    public static Materials UmbralMire;
    /**
     * 三途余汽（en: Sanzu Residual Steam）——遗忘之川 sanzuRiver 产出（v1.20.39 plan §3.4 第五气；
     * 群系枚举与 ProsperityAirLookup 接线由 T5 切片落位）。
     */
    public static Materials SanzuResidualSteam;
    /**
     * 深渊执念（en: Abyssal Obsession）——dim78 河湖流体本体材料（v1.20.40 P19 plan §I 第六员，
     * 唯一液体）。深蓝 0x234A6B（普通档 colorMultiplier 0x3A6FA0 / 遗忘之川档 0x1C3A63 由
     * BlockAbyssalFluid 两端点乘色（v1.20.50 P27 D3 提亮档，两端点间按湖压力场连续梯度插值，
     * 见 AbyssalTintField），见其类注释）。FluidRegistry 名 = "abyssal_obsession"。
     */
    public static Materials AbyssalObsession;
    /**
     * 枯竭气息（en: Withered Breath）——枯竭河床 witheredRiverbed 产出（v1.20.48 P25 第七气，
     * 用户裁定；群系枚举与 ProsperityAirLookup 接线由 P25 切片落位）。枯竭土黄 0x8A7A52
     * （与枯竭河床群系草叶色同源，"气色即地色"）。FluidRegistry 名 = "withered_breath"。
     */
    public static Materials WitheredBreath;

    @Override
    public void onMaterialsInit() {
        int registered = 0;
        registered += register("wastesigh", "Waste Sigh", 0x00b08a4a, material -> WastesSigh = material);
        registered += register("thickgrease", "Thick Grease", 0x004a3f28, material -> ThickGrease = material);
        registered += register("metalgrit", "Metal Grit", 0x0096613d, material -> MetalGrit = material);
        registered += register("umbralmire", "Umbral Mire", 0x002c251d, material -> UmbralMire = material);
        // 三途余汽保留苍白幽灵青。
        registered += register(
            "sanzu_residual_steam",
            "Sanzu Residual Steam",
            0x00a8c8d8,
            material -> SanzuResidualSteam = material);
        // 深渊执念保留原流体名称、颜色及现有方块/桶挂接。
        registered += registerLiquid(
            "abyssal_obsession",
            "Abyssal Obsession",
            0x00234A6B,
            material -> AbyssalObsession = material);
        // 枯竭气息保留枯竭土黄。
        registered += register("withered_breath", "Withered Breath", 0x008a7a52, material -> WitheredBreath = material);
        GTSteamReborn.LOG.info("[GTSR] prosperity air materials registered: " + registered);
    }

    private static Materials[] rawGases() {
        return new Materials[] { WastesSigh, ThickGrease, MetalGrit, UmbralMire, SanzuResidualSteam, WitheredBreath };
    }

    public static boolean isRawGas(Materials candidate) {
        for (Materials material : rawGases()) if (material != null && material == candidate) return true;
        return false;
    }

    private static int register(String name, String defaultLocalName, int argb, Holder setter) {
        return registerCommon(name, defaultLocalName, argb, true, setter);
    }

    private static int registerLiquid(String name, String defaultLocalName, int argb, Holder setter) {
        return registerCommon(name, defaultLocalName, argb, false, setter);
    }

    private static int registerCommon(String name, String defaultLocalName, int argb, boolean gas, Holder setter) {
        MaterialBuilder builder = new MaterialBuilder().setName(name)
            .setDefaultLocalName(defaultLocalName)
            .setIconSet(TextureSet.SET_FLUID)
            .setARGB(argb);
        if (gas) builder.addSubTag(SubTag.TRANSPARENT);
        Materials material = builder.constructMaterial();
        ProsperityNativeFluids.register(material, gas, 295);
        setter.accept(material);
        return 1;
    }

    /** dev 探针（S3 验收②）：回读 FluidRegistry 与静态持有者/mGas，逐材料输出非空证据。 */
    public static void logRegistrationProbe() {
        probe("wastesigh", WastesSigh);
        probe("thickgrease", ThickGrease);
        probe("metalgrit", MetalGrit);
        probe("umbralmire", UmbralMire);
        probe("sanzu_residual_steam", SanzuResidualSteam);
        // 深渊执念为液体：mGas 恒 null 是期望态，非空证据走 mFluid（v1.20.40 P19-U1）。
        probeLiquid("abyssal_obsession", AbyssalObsession);
        // 枯竭气息（v1.20.48 P25 第七气，用户裁定）
        probe("withered_breath", WitheredBreath);
    }

    private static void probe(String fluidName, Materials holder) {
        ProsperityNativeFluids.verify(holder);
        ProsperityFluidContainers.filledCell(holder.getGas(1000));
        final Fluid fluid = FluidRegistry.getFluid(fluidName);
        final String gasName = holder != null && holder.mGas != null ? holder.mGas.getName() : "null";
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity air dev probe: " + fluidName
                + " FluidRegistry="
                + (fluid != null ? "ok" : "null")
                + " mGas="
                + gasName);
    }

    /** 液体材料探针（P19-U1）：与 {@link #probe} 同格式，非空证据读 mFluid（液体无 mGas 通路）。 */
    private static void probeLiquid(String fluidName, Materials holder) {
        ProsperityNativeFluids.verify(holder);
        ProsperityFluidContainers.filledCell(holder.getFluid(1000));
        final Fluid fluid = FluidRegistry.getFluid(fluidName);
        final String liquidName = holder != null && holder.mFluid != null ? holder.mFluid.getName() : "null";
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity air dev probe: " + fluidName
                + " FluidRegistry="
                + (fluid != null ? "ok" : "null")
                + " mFluid="
                + liquidName);
    }

    /** 单参消费接口（避免额外依赖 java.util.function.Consumer 的触发时机差异，语义同其 accept）。 */
    @FunctionalInterface
    private interface Holder {

        void accept(Materials material);
    }
}
