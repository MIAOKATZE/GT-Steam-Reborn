package com.miaokatze.gtsr.api.recipe;

/** Shared card-space geometry; GT205 shifts supplied slots by (-5,-11), then stacks inset by (1,1). */
public final class CriticalNEILayout {

    public static final int WIDTH = 170, HEIGHT = 174;
    public static final int INPUT_X = 8, OUTPUT_X = 144, SLOT = 18, PITCH = 20;
    public static final int ITEM_Y = 44, FLUID_Y = 64;
    public static final int BUILD_X = 13, BUILD_Y = 59, BUILD_ROW_PITCH = 36;
    public static final int BUILD_TIME_X = 107;
    public static final int BUILD_ITEM_COLUMNS = 6, BUILD_FLUID_COLUMNS = 2;
    public static final int BUILD_FLUID_X = BUILD_X + BUILD_ITEM_COLUMNS * SLOT;
    public static final int BUILD_FLUID_Y = BUILD_Y, BUILD_OUTPUT_Y = 2;
    public static final int WINDOW_X = 5, WINDOW_Y = 11;
    public static final int ARROW_X = 78, ARROW_WIDTH = 55;
    public static final int CHEMICAL_BAR_Y = 43, CHEMICAL_BAR_WIDTH = 12, CHEMICAL_BAR_HEIGHT = 42;

    private CriticalNEILayout() {}

    public static int arrowCenter(boolean fluid) {
        return (fluid ? FLUID_Y : ITEM_Y) + SLOT / 2;
    }

    /** Centre the process between complete native slot groups, including visible empty slots. */
    public static Flow flow(CriticalRecipeMaps.DisplayInfo info, boolean fluid) {
        var properties = info.recipeMap.getFrontend()
            .getUIProperties();
        int slots = fluid ? properties.maxFluidInputs : properties.maxItemInputs;
        int inputRight = INPUT_X + (Math.max(1, slots) - 1) * PITCH + SLOT;
        return new Flow(inputRight, OUTPUT_X, arrowCenter(fluid));
    }

    public static final class Flow {

        public final int inputRight, outputLeft, centerX, centerY, gapWidth, diagramWidth;

        private Flow(int inputRight, int outputLeft, int centerY) {
            if (inputRight >= outputLeft) throw new IllegalArgumentException("Overlapping NEI port groups");
            this.inputRight = inputRight;
            this.outputLeft = outputLeft;
            this.centerX = (inputRight + outputLeft) / 2;
            this.centerY = centerY;
            this.gapWidth = outputLeft - inputRight;
            this.diagramWidth = Math.min(64, gapWidth - 12);
        }
    }
}
