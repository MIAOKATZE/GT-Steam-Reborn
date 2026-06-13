package com.miaokatze.gtsr.api.recipe;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.common.gui.modularui.UIHelper;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidGridFrontend extends RecipeMapFrontend {

    private static final int xDirMaxCount = 3;
    private static final int yOrigin = 8;

    private final int fluidRowCount;

    public FluidGridFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(uiPropertiesBuilder.logoPos(new Pos2d(80, 62)), neiPropertiesBuilder);
        this.fluidRowCount = getFluidRowCount();
    }

    @Override
    protected NEIRecipePropertiesBuilder modifyNEIProperties(NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        neiPropertiesBuilder.recipeBackgroundSize(new Size(170, 82 + Math.max(fluidRowCount - 2, 0) * 18));
        return super.modifyNEIProperties(neiPropertiesBuilder);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        return UIHelper.getGridPositions(fluidInputCount, 16, yOrigin, xDirMaxCount);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return UIHelper.getGridPositions(fluidOutputCount, 106, yOrigin, xDirMaxCount);
    }

    private int getFluidRowCount() {
        return (Math.max(uiProperties.maxFluidInputs, uiProperties.maxFluidOutputs) - 1) / xDirMaxCount + 1;
    }
}
