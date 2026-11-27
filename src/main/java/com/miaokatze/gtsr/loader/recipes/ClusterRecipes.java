// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import cpw.mods.fml.common.registry.GameRegistry;

/** One-way workbench migration for the retained legacy mineral processing machines. */
public final class ClusterRecipes {

    private ClusterRecipes() {}

    public static void addRecipes() {
        if (!MTEMineralIntegrationCenter.REGISTRATION_ENABLED) return;
        GameRegistry.addRecipe(
            new ShapelessOreRecipe(GTSRItemList.OreIntegrationCenter.get(1), GTSRItemList.ClusterController.get(1)));
        migrate(GTSRItemList.ClusterUnitCrusher, CenterModule.CRUSHER);
        migrate(GTSRItemList.ClusterUnitOreWasher, CenterModule.WASHER);
        migrate(GTSRItemList.ClusterUnitCentrifuge, CenterModule.CENTRIFUGE);
        migrate(GTSRItemList.ClusterUnitThermalCentrifuge, CenterModule.THERMAL_CENTRIFUGE);
        migrate(GTSRItemList.ClusterUnitSifter, CenterModule.SIFTER);
        migrate(GTSRItemList.ClusterUnitMagneticSeparator, CenterModule.SEPARATOR);
        migrate(GTSRItemList.ClusterUnitFurnace, CenterModule.SMELTING);
        migrate(GTSRItemList.ClusterBoosterParallel, CenterModule.PARALLEL);
        migrate(GTSRItemList.ClusterBoosterSpeed, CenterModule.SPEED);
        migrate(GTSRItemList.ClusterBoosterPrimary, CenterModule.YIELD);
        migrate(GTSRItemList.ClusterBoosterSecondary, CenterModule.SECONDARY);
        migrate(GTSRItemList.ClusterBoosterSteamSaver, CenterModule.SPEED);
        migrate(GTSRItemList.ClusterUnitLogistics, CenterModule.LOGISTICS);
    }

    private static void migrate(GTSRItemList legacy, CenterModule module) {
        GameRegistry.addRecipe(new ShapelessOreRecipe(CenterModuleItems.stack(module, 1), legacy.get(1)));
    }
}
