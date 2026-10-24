package com.miaokatze.gtsr.api.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.miaokatze.gtsr.client.nei.CriticalNEIShowcase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;

/** GT205 native clickable stacks with a separate, read-only engineering presentation. */
public final class CriticalRecipeFrontend extends RecipeMapFrontend {

    private final boolean construction;

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        this(uiPropertiesBuilder, neiPropertiesBuilder, false);
    }

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder, boolean construction) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
        this.construction = construction;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularWindow.Builder createNEITemplate(GTNEIDefaultHandler.NEITemplateContext ctx) {
        return super.createNEITemplate(ctx).setBackground(CriticalNEIShowcase::background);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {}

    @Override
    public List<Pos2d> getItemInputPositions(int count) {
        // The superclass builds its position suppliers before this.construction is assigned.
        // Suppliers are evaluated later, after frontend construction has completed.
        return construction ? grid(count, 7, 9, 57) : row(count, 22, 35);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int count) {
        return row(count, construction ? 147 : 140, 35);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int count) {
        return row(count, construction ? 147 : 13, construction ? 59 : 35);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int count) {
        return row(count, 140, 35);
    }

    private static List<Pos2d> row(int count, int x, int y) {
        return grid(count, count == 0 ? 1 : count, x, y);
    }

    private static List<Pos2d> grid(int count, int columns, int x, int y) {
        List<Pos2d> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add(new Pos2d(x + i % columns * 18 + 5, y + i / columns * 18 + 11));
        return positions;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawDescription(RecipeDisplayInfo recipeInfo) {
        CriticalNEIShowcase.draw(recipeInfo.recipe);
    }

    @Override
    protected List<String> handleNEIItemOutputTooltip(List<String> tip,
        GTNEIDefaultHandler.FixedPositionedStack stack) {
        if (construction) tip.add(CriticalRecipeMaps.text("showcase.world_product"));
        return tip;
    }

    @Override
    public List<String> handleNEIItemTooltip(ItemStack stack, List<String> tip,
        GTNEIDefaultHandler.CachedDefaultRecipe recipe) {
        super.handleNEIItemTooltip(stack, tip, recipe);
        CriticalRecipeMaps.DisplayInfo info = CriticalRecipeMaps.displayInfo(recipe.mRecipe);
        if (info != null && info.construction())
            tip.add(CriticalRecipeMaps.formatted("showcase.page", info.page + 1, info.pages));
        if (info != null) {
            tip.add(CriticalRecipeMaps.text("showcase.details"));
            tip.addAll(CriticalRecipeMaps.description(recipe.mRecipe));
            if (info.construction() || info.showcase()) tip.add(CriticalRecipeMaps.text("showcase.reference"));
        }
        return tip;
    }
}
