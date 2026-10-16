package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.api.compat.GTVersionCompat;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEBasicMachineWithRecipe;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;

/** Only executable recipe maps enter this catalogue; NEI visual/fuel maps are never executable modes. */
public final class CriticalProcessingModes {

    public static final String ASSEMBLY_LINE = "assembly_line";

    private CriticalProcessingModes() {}

    public static List<String> list(CriticalMachineKind kind) {
        List<String> result = new ArrayList<>();
        if (kind == CriticalMachineKind.ASSEMBLY) {
            result.add(RecipeMaps.assemblerRecipes.unlocalizedName);
            result.add(ASSEMBLY_LINE);
        } else if (kind == CriticalMachineKind.PROCESSING) result.addAll(maps().keySet());
        return result;
    }

    public static boolean isAllowed(CriticalMachineKind kind, String mode) {
        return list(kind).contains(mode);
    }

    public static String selected(CriticalMachineState state) {
        List<String> modes = list(state.getKind());
        String value = state.getProcessingMode();
        return modes.contains(value) ? value : modes.isEmpty() ? "" : modes.get(0);
    }

    public static RecipeMap<?> map(String mode) {
        if (RecipeMaps.assemblerRecipes.unlocalizedName.equals(mode)) return RecipeMaps.assemblerRecipes;
        return maps().get(mode);
    }

    public static String display(String mode) {
        if (RecipeMaps.quantumForceTransformerRecipes.unlocalizedName.equals(mode))
            return StatCollector.translateToLocal("gtsr.critical.mode.qft_base");
        return ASSEMBLY_LINE.equals(mode) ? StatCollector.translateToLocal("gtsr.critical.mode.assembly_line")
            : StatCollector.translateToLocal(mode);
    }

    private static Map<String, RecipeMap<?>> cachedMaps;

    private static Map<String, RecipeMap<?>> maps() {
        if (cachedMaps != null) return cachedMaps;
        Map<String, RecipeMap<?>> result = new java.util.TreeMap<>();
        for (IMetaTileEntity mte : GregTechAPI.METATILEENTITIES)
            if (mte instanceof MTEBasicMachine basic && (mte instanceof MTEBasicMachineWithRecipe
                || mte instanceof gregtech.common.tileentities.machines.basic.MTEBoxinator
                || mte instanceof gregtech.common.tileentities.machines.basic.MTEPotionBrewer)) {
                    RecipeMap<?> map = basic.getRecipeMap();
                    if (map != null && !map.unlocalizedName.contains("fake")
                        && map != RecipeMaps.scannerFakeRecipes
                        && map != RecipeMaps.rockBreakerFakeRecipes
                        && map != RecipeMaps.assemblylineVisualRecipes) result.put(map.unlocalizedName, map);
                }
        // Multi-only production lines with native GTRecipe inputs/outputs. Heat, coils, vacuum,
        // cleanroom and low-gravity requirements are supplied by the critical internal environment.
        RecipeMap<?>[] advanced = { RecipeMaps.blastFurnaceRecipes, RecipeMaps.vacuumFreezerRecipes,
            RecipeMaps.implosionRecipes, RecipeMaps.multiblockChemicalReactorRecipes,
            RecipeMaps.distillationTowerRecipes, RecipeMaps.crackingRecipes, RecipeMaps.pyrolyseRecipes,
            RecipeMaps.plasmaForgeRecipes, RecipeMaps.transcendentPlasmaMixerRecipes,
            RecipeMaps.quantumForceTransformerRecipes, GTVersionCompat.gppRecipeMap("electrolyzerNonCellRecipes"),
            GTVersionCompat.gppRecipeMap("centrifugeNonCellRecipes"),
            GTVersionCompat.gppRecipeMap("mixerNonCellRecipes"),
            GTVersionCompat.gppRecipeMap("chemicalDehydratorRecipes"),
            GTVersionCompat.gppRecipeMap("chemicalDehydratorNonCellRecipes"),
            GTVersionCompat.gppRecipeMap("multiblockRockBreakerRecipes") };
        for (RecipeMap<?> map : advanced) if (map != null) result.put(map.unlocalizedName, map);
        cachedMaps = result;
        return result;
    }
}
