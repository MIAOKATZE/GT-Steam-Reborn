package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.criticalSingularityCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.denseStateManipulatorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.gearSteamCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.geothermalSteamBoilerRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamFluidDrillRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.steamSingularityEntanglerRecipes;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

/**
 * 展示配方（SR-A03 组五，自 GTSRRecipeLoader 门面原样迁出）：地热锅炉/流体钻机/齿轮蒸汽
 * 压缩机/蒸汽奇点纠缠装置/临界纠缠奇点稳定装置/致密态操纵装置的 NEI 展示配方。
 * 方法体逐字未动；类内方法排列与 run() 派发顺序不一致属预期（DSM 在 Implosion 后注册），
 * 注册顺序由门面派发表单点决定。
 */
public final class DisplayRecipes {

    private DisplayRecipes() {}

    public static void registerGeothermalBoilerDisplayRecipes() {
        // 无芯片配方
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 1000))
            .itemOutputs(
                new ItemStack(net.minecraft.init.Blocks.obsidian, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalite, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Copper, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Tin, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Silver, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Gold, 1))
            .outputChances(4500, 1500, 1000, 800, 600, 200, 100, 50, 35)
            .duration(0)
            .eut(0)
            .addTo(geothermalSteamBoilerRecipes);

        // 有芯片配方（额外产出金红石粉和白钨矿粉）
        GTValues.RA.stdBuilder()
            .fluidInputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 1000))
            .itemOutputs(
                new ItemStack(net.minecraft.init.Blocks.obsidian, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalite, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Copper, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Tin, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Silver, 1),
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Gold, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Phosphorus, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Rutile, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Scheelite, 1))
            .outputChances(4500, 1500, 1000, 800, 600, 200, 100, 50, 35, 10, 5, 2)
            .duration(0)
            .eut(0)
            .addTo(geothermalSteamBoilerRecipes);
        log("Geothermal boiler display recipes done.");
    }

    public static void registerFluidDrillDisplayRecipes() {
        // 水模式：青铜 200~2000 L/s，钢 200~8000 L/s
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.Water.getFluid(2000))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 蒸馏水模式：水模式的20%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(GTModHandler.getDistilledWater(400))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 盐水模式：水模式的10%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.SaltWater.getFluid(200))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        // 岩浆模式：其他维度0.5%，下界5%（需钢级）
        GTValues.RA.stdBuilder()
            .fluidOutputs(new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 10))
            .duration(20)
            .eut(0)
            .addTo(steamFluidDrillRecipes);

        log("Fluid drill display recipes done.");
    }

    public static void registerGearSteamCompressorDisplayRecipes() {
        // Bronze tier: 6400 L/s steam → 1600 L/s superheated steam + 30 L/s distilled water
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Steam.getGas(6400))
            .fluidOutputs(FluidRegistry.getFluidStack("ic2superheatedsteam", 1600), GTModHandler.getDistilledWater(30))
            .duration(0)
            .eut(0)
            .addTo(gearSteamCompressorRecipes);

        log("Gear steam compressor display recipes done.");
    }

    // 蒸汽奇点纠缠装置展示配方：3 条（蒸汽/过热蒸汽/超临界蒸汽 → 蒸汽纠缠奇点）。
    // 仅用于 NEI 展示，实际产出由机器 checkProcessing() 计算；流体不写量。
    public static void registerSteamSingularityEntanglerDisplayRecipes() {
        ItemStack output = GTSRItemList.SteamEntangledSingularity.get(1);
        if (output == null) {
            warn("Skipped SteamSingularityEntangler display recipes - output is null");
            return;
        }
        for (String fluidName : new String[] { "steam", "ic2superheatedsteam", "supercriticalsteam" }) {
            FluidStack input = FluidRegistry.getFluidStack(fluidName, 1);
            if (input == null) {
                warn("Skipped SSE display recipe for fluid " + fluidName + " - fluid missing");
                continue;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(input)
                .itemOutputs(output)
                .duration(20)
                .eut(0)
                .addTo(steamSingularityEntanglerRecipes);
        }
        log("Steam singularity entangler display recipes done.");
    }

    // 临界纠缠奇点稳定装置展示配方：3 条（致密态蒸汽/过热/超临界 → 临界蒸汽纠缠奇点）。
    public static void registerCriticalSingularityCompressorDisplayRecipes() {
        ItemStack output = GTSRItemList.CriticalSteamEntangledSingularity.get(1);
        if (output == null) {
            warn("Skipped CriticalSingularityCompressor display recipes - output is null");
            return;
        }
        for (String fluidName : new String[] { "densesteam", "densesuperheatedsteam", "densesupercriticalsteam" }) {
            FluidStack input = FluidRegistry.getFluidStack(fluidName, 1);
            if (input == null) {
                warn("Skipped CSC display recipe for fluid " + fluidName + " - fluid missing");
                continue;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(input)
                .itemOutputs(output)
                .duration(20)
                .eut(0)
                .addTo(criticalSingularityCompressorRecipes);
        }
        log("Critical singularity compressor display recipes done.");
    }

    // 致密态操纵装置展示配方：6 条（3 压缩 + 3 解压）。
    // 压缩：1000L 普通蒸汽 → 1L 致密态蒸汽；解压：1L 致密态蒸汽 → 1000L 普通蒸汽。
    public static void registerDenseStateManipulatorDisplayRecipes() {
        String[] normal = { "steam", "ic2superheatedsteam", "supercriticalsteam" };
        String[] dense = { "densesteam", "densesuperheatedsteam", "densesupercriticalsteam" };
        for (int i = 0; i < normal.length; i++) {
            FluidStack normalIn = FluidRegistry.getFluidStack(normal[i], 1000);
            FluidStack denseOut = FluidRegistry.getFluidStack(dense[i], 1);
            if (normalIn != null && denseOut != null) {
                GTValues.RA.stdBuilder()
                    .fluidInputs(normalIn)
                    .fluidOutputs(denseOut)
                    .duration(20)
                    .eut(0)
                    .addTo(denseStateManipulatorRecipes);
            } else {
                warn("Skipped DSM compress display recipe for grade " + i);
            }
            FluidStack denseIn = FluidRegistry.getFluidStack(dense[i], 1);
            FluidStack normalOut = FluidRegistry.getFluidStack(normal[i], 1000);
            if (denseIn != null && normalOut != null) {
                GTValues.RA.stdBuilder()
                    .fluidInputs(denseIn)
                    .fluidOutputs(normalOut)
                    .duration(20)
                    .eut(0)
                    .addTo(denseStateManipulatorRecipes);
            } else {
                warn("Skipped DSM decompress display recipe for grade " + i);
            }
        }
        log("Dense state manipulator display recipes done.");
    }

}
