package com.miaokatze.gtsr.api.recipe;

/** Shared card-space geometry; GT205 shifts supplied slots by (-5,-11), then stacks inset by (1,1). */
public final class CriticalNEILayout {

    public static final int WIDTH = 170, HEIGHT = 174;
    public static final int INPUT_X = 8, OUTPUT_X = 146, SLOT = 18, PITCH = 20;
    public static final int ITEM_Y = 40, FLUID_Y = 64;
    public static final int BUILD_Y = 43, BUILD_FLUID_Y = 127, BUILD_OUTPUT_Y = 23;
    public static final int WINDOW_X = 5, WINDOW_Y = 11;
    public static final int ARROW_X = 78, ARROW_WIDTH = 55;

    private CriticalNEILayout() {}

    public static int arrowCenter(boolean fluid) {
        return (fluid ? FLUID_Y : ITEM_Y) + SLOT / 2;
    }
}
