// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTUtility;
import gregtech.nei.RecipeDisplayInfo;

/** Shared by the native matcher, industrial recipe and both NEI displays. */
public final class StoneBreakerRule {

    public static final RecipeMetadataKey<StoneBreakerRule> KEY = new RecipeMetadataKey<StoneBreakerRule>(
        StoneBreakerRule.class,
        "gtsr_stone_breaker_rule") {

        @Override
        public void drawInfo(RecipeDisplayInfo info, Object metadata) {
            if (metadata instanceof StoneBreakerRule value) info
                .drawText(StatCollector.translateToLocalFormatted("gtsr.rockbreaker.site", value.site.displayName()));
        }
    };

    public final StoneSite site;
    public final int eut;
    private final ItemStack sample;
    private final ItemStack circuit;

    StoneBreakerRule(StoneSite site, int eut, ItemStack sample, int program) {
        this.site = site;
        this.eut = eut;
        this.sample = sample;
        this.circuit = GTUtility.getIntegratedCircuit(program);
    }

    public boolean acceptsInputs(ItemStack[] inputs) {
        boolean hasSample = false;
        boolean hasCircuit = false;
        for (ItemStack input : inputs) {
            if (input == null) continue;
            if (input.stackSize > 0 && GTUtility.areStacksEqual(input, sample)) hasSample = true;
            if (GTUtility.areStacksEqual(input, circuit)) hasCircuit = true;
        }
        return hasSample && hasCircuit;
    }

    public boolean canStart(World world, long voltage, ItemStack[] inputs) {
        return site.allows(world) && voltage >= eut && acceptsInputs(inputs);
    }
}
