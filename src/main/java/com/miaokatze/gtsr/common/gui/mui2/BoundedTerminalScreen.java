package com.miaokatze.gtsr.common.gui.mui2;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client layout only. Pair with a native scroll viewport; clamping alone cannot make fixed content reachable. */
@SideOnly(Side.CLIENT)
public final class BoundedTerminalScreen extends ModularScreen {

    private final int preferredWidth, preferredHeight, edgeMargin;

    public BoundedTerminalScreen(String owner, ModularPanel panel, int preferredWidth, int preferredHeight) {
        this(owner, panel, preferredWidth, preferredHeight, 6);
    }

    public BoundedTerminalScreen(String owner, ModularPanel panel, int preferredWidth, int preferredHeight,
        int edgeMargin) {
        super(owner, panel);
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
        this.edgeMargin = Math.max(0, edgeMargin);
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(
            Math.max(1, Math.min(preferredWidth, width - 2 * edgeMargin)),
            Math.max(1, Math.min(preferredHeight, height - 2 * edgeMargin)));
        super.onResize(width, height);
    }
}
