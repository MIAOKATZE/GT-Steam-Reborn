package com.miaokatze.gtsr.common.gui;

import com.cleanroommc.modularui.drawable.UITexture;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiTextures;

/** MUI2 adapters for the existing aggregator / processing-cluster texture family. */
final class CriticalGuiStyle {

    static final UITexture PANEL = UITexture.builder()
        .location(GtsrGuiTextures.PANEL_AGGREGATOR)
        .imageSize(GtsrGuiTextures.PANEL_AGGREGATOR_W, GtsrGuiTextures.PANEL_AGGREGATOR_H)
        .build();
    static final UITexture CARD = texture(
        GtsrGuiTextures.LIST_PANEL,
        GtsrGuiTextures.LIST_PANEL_W,
        GtsrGuiTextures.LIST_PANEL_H);
    static final UITexture BUTTON = texture(
        GtsrGuiTextures.BUTTON_NORMAL,
        GtsrGuiTextures.BUTTON_NORMAL_W,
        GtsrGuiTextures.BUTTON_NORMAL_H);
    static final UITexture HOVER = texture(
        GtsrGuiTextures.BUTTON_HOVER,
        GtsrGuiTextures.BUTTON_HOVER_W,
        GtsrGuiTextures.BUTTON_HOVER_H);
    static final UITexture TAB = texture(
        GtsrGuiTextures.TAB_INACTIVE,
        GtsrGuiTextures.TAB_INACTIVE_W,
        GtsrGuiTextures.TAB_INACTIVE_H);
    static final UITexture SELECTED_TAB = texture(
        GtsrGuiTextures.TAB_ACTIVE,
        GtsrGuiTextures.TAB_ACTIVE_W,
        GtsrGuiTextures.TAB_ACTIVE_H);
    static final UITexture CHIP = texture(
        GtsrGuiTextures.CHIP_NORMAL,
        GtsrGuiTextures.CHIP_NORMAL_W,
        GtsrGuiTextures.CHIP_NORMAL_H);
    static final UITexture SLOT = UITexture.builder()
        .location(GtsrGuiTextures.SLOT_FRAME)
        .imageSize(GtsrGuiTextures.SLOT_FRAME_W, GtsrGuiTextures.SLOT_FRAME_H)
        .build();

    private CriticalGuiStyle() {}

    private static UITexture texture(net.minecraft.util.ResourceLocation location, int width, int height) {
        return UITexture.builder()
            .location(location)
            .imageSize(width, height)
            .adaptable(4)
            .build();
    }
}
