package com.miaokatze.gtsr.client.gui.mui2.lore;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class LoreWidgets {

    private LoreWidgets() {}

    static ButtonWidget<?> button(String name, String key, Runnable action) {
        return button(name, IKey.lang(key), action);
    }

    static ButtonWidget<?> button(String name, IKey label, Runnable action) {
        return action(LoreUiStyle.button(label), name, action);
    }

    static ButtonWidget<?> action(ButtonWidget<?> widget, String name, Runnable action) {
        return widget.name(name)
            .height(20)
            .onMousePressed(button -> {
                if (button != 0) return false;
                action.run();
                return true;
            });
    }

    static void paragraphs(ListWidget<IWidget, ?> list, Iterable<String> keys, int color) {
        list.removeAll();
        for (String key : keys) list.child(
            new ParagraphText(IKey.lang(key)).color(color)
                .widthRel(1f)
                .marginBottom(7)
                .textAlign(Alignment.TopLeft)
                .shadow(false));
    }

    /** Wait for the list's real width instead of measuring against TextWidget's screen-width fallback. */
    private static final class ParagraphText extends TextWidget<ParagraphText> {

        private ParagraphText(IKey key) {
            super(key);
        }

        @Override
        public int getDefaultHeight() {
            // DimensionSizer keeps a default size of zero unresolved and retries after parent layout.
            if (!resizer().isWidthCalculated()) return 0;
            return super.getDefaultHeight();
        }
    }

    static void scroll(ListWidget<IWidget, ?> list, int direction) {
        if (!list.isValid()) return;
        list.getScrollData()
            .scrollTo(list.getScrollArea(), list.getScrollY() + direction * Math.max(12, list.getArea().height - 12));
    }

    static void close() {
        ModularScreen screen = ModularScreen.getCurrent();
        if (screen != null) screen.close();
    }
}
