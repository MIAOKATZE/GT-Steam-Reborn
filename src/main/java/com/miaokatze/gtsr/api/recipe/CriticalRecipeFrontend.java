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
        ModularWindow.Builder builder = super.createNEITemplate(ctx);
        return construction ? builder.setBackground(CriticalNEIShowcase::constructionBackground)
            : builder.setBackground(CriticalNEIShowcase::background);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {}

    @Override
    public List<Pos2d> getItemInputPositions(int count) {
        // The superclass builds its position suppliers before this.construction is assigned.
        // Suppliers are evaluated later, after frontend construction has completed.
        return construction ? constructionGrid(count, BUILD_ITEM_COLUMNS, BUILD_X) : row(count, INPUT_X, ITEM_Y);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int count) {
        return row(count, OUTPUT_X, construction ? BUILD_OUTPUT_Y : ITEM_Y);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int count) {
        return construction ? constructionGrid(count, BUILD_FLUID_COLUMNS, BUILD_FLUID_X)
            : row(count, INPUT_X, FLUID_Y);
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
    protected List<String> handleNEIItemOutputTooltip(List<String> tip,
        GTNEIDefaultHandler.FixedPositionedStack stack) {
        if (construction) tip.add(CriticalRecipeMaps.text("showcase.world_product"));
        return tip;
    }

}
