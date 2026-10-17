package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.util.function.DoubleSupplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Read-only heat gauge. No widget state or machine state is written while drawing. */
final class ClusterMeter implements IDrawable {

    private final DoubleSupplier progress;
    private final Rectangle empty = new Rectangle().color(0xff3a3a3f);
    private final Rectangle full = new Rectangle().color(GtsrGuiPalette.TEXT_ACCENT);

    ClusterMeter(DoubleSupplier progress) {
        this.progress = progress;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        empty.draw(context, x, y, width, height, theme);
        int filled = (int) (width * Math.max(0, Math.min(1, progress.getAsDouble())));
        if (filled > 0) full.draw(context, x, y, filled, height, theme);
    }
}
