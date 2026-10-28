package com.miaokatze.gtsr.api.recipe;

import static com.miaokatze.gtsr.api.recipe.CriticalNEILayout.*;

import java.util.ArrayList;
import java.util.List;

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
    private final boolean chemical;

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        this(uiPropertiesBuilder, neiPropertiesBuilder, false);
    }

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder, boolean construction) {
        this(uiPropertiesBuilder, neiPropertiesBuilder, construction, false);
    }

    public CriticalRecipeFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder, boolean construction, boolean chemical) {
        super(uiPropertiesBuilder, neiPropertiesBuilder);
        this.construction = construction;
        this.chemical = chemical;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularWindow.Builder createNEITemplate(GTNEIDefaultHandler.NEITemplateContext ctx) {
        ModularWindow.Builder builder = super.createNEITemplate(ctx);
        return construction ? builder.setBackground(CriticalNEIShowcase::constructionBackground)
            : chemical ? builder.setBackground(CriticalNEIShowcase::chemicalBackground)
                : builder.setBackground(CriticalNEIShowcase::background);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {}

    @Override
    public List<Pos2d> getItemInputPositions(int count) {
        // The superclass builds its position suppliers before this.construction is assigned.
        // Suppliers are evaluated later, after frontend construction has completed.
        return construction ? constructionGrid(count, BUILD_ITEM_COLUMNS, BUILD_X)
            : chemical ? row(count, CHEMICAL_CIRCUIT_X, CHEMICAL_CIRCUIT_Y) : row(count, INPUT_X, ITEM_Y);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int count) {
        return row(count, OUTPUT_X, construction ? BUILD_OUTPUT_Y : ITEM_Y);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int count) {
        return construction ? constructionGrid(count, BUILD_FLUID_COLUMNS, BUILD_FLUID_X)
            : chemical ? grid(count, CHEMICAL_COLUMNS, INPUT_X, CHEMICAL_FLUID_Y) : row(count, INPUT_X, FLUID_Y);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int count) {
        return row(count, OUTPUT_X, FLUID_Y);
    }

    private static List<Pos2d> constructionGrid(int count, int columns, int x) {
        List<Pos2d> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions
            .add(new Pos2d(x + i % columns * SLOT + WINDOW_X, BUILD_Y + i / columns * BUILD_ROW_PITCH + WINDOW_Y));
        return positions;
    }

    private static List<Pos2d> row(int count, int x, int y) {
        return grid(count, count == 0 ? 1 : count, x, y);
    }

    private static List<Pos2d> grid(int count, int columns, int x, int y) {
        List<Pos2d> positions = new ArrayList<>(count);
        // GT205 adds WINDOW_OFFSET=(-5,-11) to these suppliers. Native stack is slot+(1,1).
        for (int i = 0; i < count; i++)
            positions.add(new Pos2d(x + i % columns * PITCH + WINDOW_X, y + i / columns * PITCH + WINDOW_Y));
        return positions;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawDescription(RecipeDisplayInfo recipeInfo) {
        CriticalNEIShowcase.draw(recipeInfo.recipe);
    }

    @Override
    protected List<String> handleNEIItemInputTooltip(List<String> tip, GTNEIDefaultHandler.FixedPositionedStack stack) {
        if (chemical && stack.isNotConsumed()) {
            for (String key : new String[] { "bus", "not_consumed", "number" })
                tip.add(CriticalRecipeMaps.text("showcase.circuit." + key));
        }
        if (construction && stack.isNotConsumed()
            && com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity
                .isStackEqual(stack.item)) {
            for (String key : new String[] { "title", "optional", "interval", "reduction", "scope" })
                tip.add(CriticalRecipeMaps.text("showcase.curing." + key));
        }
        return tip;
    }

    @Override
    protected List<String> handleNEIItemOutputTooltip(List<String> tip,
        GTNEIDefaultHandler.FixedPositionedStack stack) {
        if (construction) tip.add(CriticalRecipeMaps.text("showcase.world_product"));
        return tip;
    }

    @Override
    public List<String> handleNEIItemTooltip(net.minecraft.item.ItemStack stack, List<String> tip,
        GTNEIDefaultHandler.CachedDefaultRecipe recipe) {
        tip = super.handleNEIItemTooltip(stack, tip, recipe);
        var info = CriticalRecipeMaps.displayInfo(recipe.mRecipe);
        if (info != null && info.plasma != null) tip.addAll(CriticalNEIShowcase.plasmaTooltip(info.plasma));
        if (info != null && info.solar != null) tip.addAll(CriticalNEIShowcase.solarTooltip(info.solar));
        return tip;
    }

}
