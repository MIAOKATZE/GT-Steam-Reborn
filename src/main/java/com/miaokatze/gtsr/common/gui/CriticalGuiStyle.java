package com.miaokatze.gtsr.common.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiTextures;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;

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
    static final UITexture DISABLED = TerminalUiStyle.DISABLED;
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

    static final int TEXT_TITLE = GtsrGuiPalette.TEXT_TITLE;
    static final int TEXT_BODY = GtsrGuiPalette.TEXT_BODY;
    static final int TEXT_MUTED = GtsrGuiPalette.TEXT_MUTED;
    static final int TEXT_ACCENT = GtsrGuiPalette.TEXT_ACCENT;
    static final int TEXT_CONTROL = GtsrGuiPalette.TEXT_BODY;
    static final int STATE_ONLINE = GtsrGuiPalette.STATE_ONLINE;
    static final int STATE_OFFLINE = GtsrGuiPalette.STATE_OFFLINE;

    private CriticalGuiStyle() {}

    private static UITexture texture(net.minecraft.util.ResourceLocation location, int width, int height) {
        return UITexture.builder()
            .location(location)
            .imageSize(width, height)
            .adaptable(4)
            .build();
    }

    static TextWidget<?> text(IKey key) {
        return new WrappedText(key);
    }

    static VerticalScrollData scroll() {
        VerticalScrollData scroll = new VerticalScrollData().cancelScrollEdge(false);
        scroll.texture(TerminalUiStyle.SCROLLBAR_THUMB);
        scroll.setScrollSpeed(18);
        return scroll;
    }

    /** Measure paragraphs only after the list has assigned their real width. */
    private static final class WrappedText extends TextWidget<WrappedText> {

        private WrappedText(IKey key) {
            super(key);
        }

        @Override
        public int getDefaultHeight() {
            if (!resizer().isWidthCalculated()) return 0;
            return super.getDefaultHeight();
        }
    }
}
