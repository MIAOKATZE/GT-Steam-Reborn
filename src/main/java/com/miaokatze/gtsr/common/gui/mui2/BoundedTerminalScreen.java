package com.miaokatze.gtsr.common.gui.mui2;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client layout only. Pair with a native scroll viewport; clamping alone cannot make fixed content reachable. */
@SideOnly(Side.CLIENT)
public final class BoundedTerminalScreen extends ModularScreen {

    private final int preferredWidth, preferredHeight;

    public BoundedTerminalScreen(String owner, ModularPanel panel, int preferredWidth, int preferredHeight) {
        super(owner, panel);
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(
            Math.max(1, Math.min(preferredWidth, width - 12)),
            Math.max(1, Math.min(preferredHeight, height - 12)));
        super.onResize(width, height);
    }
}
