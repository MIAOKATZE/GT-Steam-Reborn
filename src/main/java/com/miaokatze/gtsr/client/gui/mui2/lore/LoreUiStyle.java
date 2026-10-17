package com.miaokatze.gtsr.client.gui.mui2.lore;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Original journal, survey and altar visuals, adapted to the responsive MUI2 reading layouts. */
@SideOnly(Side.CLIENT)
final class LoreUiStyle {

    static final int JOURNAL_TITLE = 0xFFE5CAA0;
    static final int JOURNAL_ARTICLE_TITLE = 0xFF4B3929;
    static final int JOURNAL_TEXT = 0xFF493B30;
    static final int JOURNAL_ENTRY_TEXT = 0xFFE2CBA0;
    static final int NAVIGATOR_TITLE = 0xFFE8D49A;
    static final int NAVIGATOR_MUTED = 0xFFBFC6BA;
    static final int ALTAR_TITLE = 0xFFC5EEFF;
    static final int ALTAR_TEXT = 0xFFE0E7EB;

    private static final IDrawable BUTTON = TerminalUiStyle.BUTTON.withColorOverride(0xFFFFFFFF);
    private static final IDrawable BUTTON_HOVERED = TerminalUiStyle.HOVER.withColorOverride(0xFFFFFFFF);
    private static final IDrawable BUTTON_DISABLED = TerminalUiStyle.DISABLED.withColorOverride(0xFFFFFFFF);

    static final IDrawable JOURNAL_PANEL = (context, x, y, width, height, theme) -> {
        Gui.drawRect(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF192826);
        Gui.drawRect(x, y, x + width, y + height, 0xFF9E7448);
        Gui.drawRect(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF252E29);
    };
    static final IDrawable JOURNAL_PAPER = (context, x, y, width, height, theme) -> {
        Gui.drawRect(x - 3, y, x, y + height, 0xFFAE814F);
        Gui.drawRect(x, y, x + width, y + height, 0xFFE0D3AF);
        Gui.drawRect(x + 3, y + 3, x + width - 3, y + height - 3, 0xFFEBDFC3);
        Gui.drawRect(x + 3, y + 20, x + width - 3, y + 21, 0xFFB2935D);
    };
    static final IDrawable NAVIGATOR_PANEL = (context, x, y, width, height, theme) -> {
        Gui.drawRect(x - 2, y - 2, x + width + 2, y + height + 2, 0xFFA18B54);
        Gui.drawRect(x, y, x + width, y + height, 0xFF182A30);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 32, 0xFF263E43);
    };
    static final IDrawable NAVIGATOR_SEARCH = (context, x, y, width, height, theme) -> {
        Gui.drawRect(x, y, x + width, y + height, 0xFFA0A0A0);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000);
    };
    static final IDrawable ALTAR_PANEL = (context, x, y, width, height, theme) -> Gui
        .drawRect(x, y, x + width, y + height, 0xEE151D24);

    private LoreUiStyle() {}

    static ButtonWidget<?> button(IKey label) {
        return button(label, () -> false);
    }

    static ButtonWidget<?> button(IKey label, BooleanSupplier selectedTab) {
        ButtonWidget<?> button = new ButtonWidget<>();
        IDrawable background = (context, x, y, width, height, theme) -> {
            IDrawable texture = !button.isEnabled() ? BUTTON_DISABLED
                : selectedTab.getAsBoolean() || button.isHovering() ? BUTTON_HOVERED : BUTTON;
            texture.draw(context, x, y, width, height, theme);
        };
        return button.background(background)
            .hoverBackground(background)
            .overlay(
                label
                    .color(
                        () -> !button.isEnabled() ? GtsrGuiPalette.TEXT_MUTED
                            : selectedTab.getAsBoolean() ? GtsrGuiPalette.TEXT_ACCENT : GtsrGuiPalette.TEXT_BODY)
                    .shadow(false));
    }

    static ButtonWidget<?> forestEntry(IKey label, BooleanSupplier selected) {
        ButtonWidget<?> button = new ButtonWidget<>();
        IDrawable background = (context, x, y, width, height, theme) -> {
            IDrawable texture = !button.isEnabled() ? BUTTON_DISABLED
                : selected.getAsBoolean() || button.isHovering() ? BUTTON_HOVERED : BUTTON;
            texture.draw(context, x, y, width, height, theme);
        };
        return button.background(background)
            .hoverBackground(background)
            .padding(4)
            .overlay(
                label.color(() -> selected.getAsBoolean() ? GtsrGuiPalette.TEXT_ACCENT : GtsrGuiPalette.TEXT_BODY)
                    .alignment(Alignment.CenterLeft)
                    .shadow(false));
    }
}
