package com.miaokatze.gtsr.common.critical.recipe;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.critical.CriticalConfiguration;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/** Five material stages. One plate funds 256 visual structural voxels (the projection is not a crafting recipe). */
public final class CriticalConstructionCosts {

    private static ItemStack[][] parts;

    private CriticalConstructionCosts() {}

    private static synchronized ItemStack[][] parts() {
        if (parts != null) return parts;
        Materials[] plate = { Materials.NaquadahAlloy, Materials.Neutronium, Materials.Infinity };
        Materials[] circuit = { Materials.ZPM, Materials.UEV, Materials.UXV };
        ItemList[] field = { ItemList.Field_Generator_ZPM, ItemList.Field_Generator_UEV, ItemList.Field_Generator_UXV };
        ItemList[] motor = { ItemList.Electric_Motor_ZPM, ItemList.Electric_Motor_UEV, ItemList.Electric_Motor_UXV };
        ItemStack[][] resolved = new ItemStack[3][4];
        for (int i = 0; i < 3; i++) {
            resolved[i] = new ItemStack[] { CriticalRecipes.ore(OrePrefixes.plate, plate[i], 1),
                CriticalRecipes.ore(OrePrefixes.circuit, circuit[i], 1), field[i].get(1), motor[i].get(1) };
            for (ItemStack stack : resolved[i])
                if (stack == null) throw new IllegalStateException("Missing construction component");
        }
        parts = resolved;
        return parts;
    }

    public static ItemStack[] forStage(CriticalConfiguration cfg, int stage, int voxelCount) {
        if (stage < 0 || stage > 4 || voxelCount <= 0) throw new IllegalArgumentException("Invalid construction stage");
        ItemStack[] p = parts()[cfg.tier.ordinal()];
        // Counts fit the controller's eighteen 64-item input slots even for the largest model.
        int plates = (int) Math.min(768L, Math.max(16L, ((long) voxelCount + 255) / 256));
        if (stage == 0) return new ItemStack[] { count(p[0], plates), count(p[1], 8), count(p[3], 8) };
        if (stage < 4) return new ItemStack[] { count(p[0], plates), count(p[1], 16), count(p[2], 8) };
        int pluginCount = cfg.parallel + cfg.speed + cfg.economy;
        return new ItemStack[] { count(p[0], plates), count(p[1], 32 + 8 * pluginCount),
            count(p[2], 16 + 2 * cfg.economy), count(p[3], 16 + 2 * cfg.speed + 2 * cfg.parallel) };
    }

    private static ItemStack count(ItemStack source, int amount) {
        ItemStack result = source.copy();
        result.stackSize = amount;
        return result;
    }
}
