package com.miaokatze.gtsr.api.recipe;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.nei.RecipeDisplayInfo;

/** Runtime-specific energy text; native voltage overclock and fusion startup text do not apply. */
public final class CriticalRecipeFrontend extends RecipeMapFrontend {

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawDescription(RecipeDisplayInfo recipeInfo) {
        for (String line : CriticalRecipeMaps.description(recipeInfo.recipe)) {
            for (Object wrapped : Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(line, 160)) {
                recipeInfo.drawText((String) wrapped);
            }
        }
    }
}
