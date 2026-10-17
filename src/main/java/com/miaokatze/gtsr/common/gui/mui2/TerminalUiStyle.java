package com.miaokatze.gtsr.common.gui.mui2;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiTextures;

/** Small common-safe adapters for the existing terminal assets; no font or GL evaluation. */
public final class TerminalUiStyle {

    public static final UITexture PANEL = texture(
        GtsrGuiTextures.PANEL_HUB_STATUS,
        GtsrGuiTextures.PANEL_HUB_STATUS_W,
        GtsrGuiTextures.PANEL_HUB_STATUS_H);
    public static final UITexture CARD = texture(
        GtsrGuiTextures.LIST_PANEL,
        GtsrGuiTextures.LIST_PANEL_W,
        GtsrGuiTextures.LIST_PANEL_H);
    public static final UITexture BUTTON = texture(
        GtsrGuiTextures.BUTTON_NORMAL,
        GtsrGuiTextures.BUTTON_NORMAL_W,
        GtsrGuiTextures.BUTTON_NORMAL_H);
    public static final UITexture HOVER = texture(
        GtsrGuiTextures.BUTTON_HOVER,
        GtsrGuiTextures.BUTTON_HOVER_W,
        GtsrGuiTextures.BUTTON_HOVER_H);
    public static final UITexture DISABLED = texture(
        GtsrGuiTextures.BUTTON_DISABLED,
        GtsrGuiTextures.BUTTON_DISABLED_W,
        GtsrGuiTextures.BUTTON_DISABLED_H);

    private TerminalUiStyle() {}

    public static ButtonWidget<?> button(String translationKey) {
        return new ButtonWidget<>().background(BUTTON)
            .hoverBackground(HOVER)
            .overlay(
                IKey.lang(translationKey)
                    .color(GtsrGuiPalette.TEXT_BODY));
    }

    private static UITexture texture(ResourceLocation location, int width, int height) {
        return UITexture.builder()
            .location(location)
            .imageSize(width, height)
            .adaptable(4)
            .build();
    }
}
