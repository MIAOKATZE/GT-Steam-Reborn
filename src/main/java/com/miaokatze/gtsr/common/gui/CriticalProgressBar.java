package com.miaokatze.gtsr.common.gui;

import java.util.function.DoubleSupplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The cluster terminal's thin solid progress line, adapted to MUI2 without stretching sprites. */
final class CriticalProgressBar implements IDrawable {

    private final DoubleSupplier progress;

    CriticalProgressBar(DoubleSupplier progress) {
        this.progress = progress;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        double value = Math.max(0, Math.min(1, progress.getAsDouble()));
        GuiDraw.drawRect(x, y, width, height, 0xFF3A3A3F);
        int filled = (int) Math.round(width * value);
        if (filled > 0) GuiDraw.drawRect(x, y, filled, height, GtsrGuiPalette.TEXT_ACCENT);
    }

    @Override
    public boolean canApplyTheme() {
        return false;
    }
}
