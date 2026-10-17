package com.miaokatze.gtsr.common.gui.mui2;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiTextures;

/** Small common-safe adapters for the existing terminal assets; no font or GL evaluation. */
public final class TerminalUiStyle {

    // Full panels carry rivets, separators and warning stripes at their original coordinates.
    // Keep them on a canonical canvas rather than stretching a four-pixel nine-slice border.
    public static final UITexture HUB_PANEL = fullPanel(
        GtsrGuiTextures.PANEL_HUB_STATUS,
        GtsrGuiTextures.PANEL_HUB_STATUS_W,
        GtsrGuiTextures.PANEL_HUB_STATUS_H);
    public static final UITexture AGGREGATOR_PANEL = fullPanel(
        GtsrGuiTextures.PANEL_AGGREGATOR,
        GtsrGuiTextures.PANEL_AGGREGATOR_W,
        GtsrGuiTextures.PANEL_AGGREGATOR_H);
    public static final UITexture CLUSTER_PANEL = fullPanel(
        GtsrGuiTextures.PANEL_CLUSTER,
        GtsrGuiTextures.PANEL_CLUSTER_W,
        GtsrGuiTextures.PANEL_CLUSTER_H);
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
    public static final UITexture TAB_ACTIVE = texture(
        GtsrGuiTextures.TAB_ACTIVE,
        GtsrGuiTextures.TAB_ACTIVE_W,
        GtsrGuiTextures.TAB_ACTIVE_H);
    public static final UITexture TAB_INACTIVE = texture(
        GtsrGuiTextures.TAB_INACTIVE,
        GtsrGuiTextures.TAB_INACTIVE_W,
        GtsrGuiTextures.TAB_INACTIVE_H);
    public static final UITexture ROW_HOVER = texture(
        GtsrGuiTextures.ROW_HOVER,
        GtsrGuiTextures.ROW_HOVER_W,
        GtsrGuiTextures.ROW_HOVER_H);
    public static final UITexture CHIP_NORMAL = texture(
        GtsrGuiTextures.CHIP_NORMAL,
        GtsrGuiTextures.CHIP_NORMAL_W,
        GtsrGuiTextures.CHIP_NORMAL_H);
    public static final UITexture CHIP_ACTIVE = texture(
        GtsrGuiTextures.CHIP_ACTIVE,
        GtsrGuiTextures.CHIP_ACTIVE_W,
        GtsrGuiTextures.CHIP_ACTIVE_H);
    public static final UITexture SLOT_FRAME = fullPanel(
        GtsrGuiTextures.SLOT_FRAME,
        GtsrGuiTextures.SLOT_FRAME_W,
        GtsrGuiTextures.SLOT_FRAME_H);
    public static final UITexture SCROLLBAR_TRACK = UITexture.builder()
        .location(GtsrGuiTextures.SCROLLBAR_TRACK)
        .imageSize(GtsrGuiTextures.SCROLLBAR_TRACK_W, GtsrGuiTextures.SCROLLBAR_TRACK_H)
        .adaptable(2)
        .build()
        .withColorOverride(0xFFFFFFFF);
    public static final UITexture SCROLLBAR_THUMB = UITexture.builder()
        .location(GtsrGuiTextures.SCROLLBAR_THUMB)
        .imageSize(GtsrGuiTextures.SCROLLBAR_THUMB_W, GtsrGuiTextures.SCROLLBAR_THUMB_H)
        .adaptable(2)
        .build()
        .withColorOverride(0xFFFFFFFF);
    public static final UITexture CARD_TILED = UITexture.builder()
        .location(GtsrGuiTextures.LIST_PANEL)
        .imageSize(GtsrGuiTextures.LIST_PANEL_W, GtsrGuiTextures.LIST_PANEL_H)
        .tiled()
        .build()
        .withColorOverride(0xFFFFFFFF);

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

    private static UITexture fullPanel(ResourceLocation location, int width, int height) {
        return UITexture.builder()
            .location(location)
            .imageSize(width, height)
            .build()
            .withColorOverride(0xFFFFFFFF);
    }
}
