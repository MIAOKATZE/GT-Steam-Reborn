package com.miaokatze.gtsr.common.gui.mui2;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;

/** Native viewport adapter for an existing fixed-size body, without rebuilding its widgets or sync handlers. */
public final class TerminalViewport {

    private TerminalViewport() {}

    /**
     * Build on both sides before sync collection. The body must already have the supplied fixed size
     * and local origin (0, 0). Size/position the returned viewport relative to the bounded panel.
     * Nested lists should also use cancelScrollEdge(false) to hand their edge scroll to this viewport.
     */
    public static ScrollWidget<?> wrap(String name, IWidget content, int contentWidth, int contentHeight) {
        return wrap(name, content, contentWidth, contentHeight, false);
    }

    /** An exact-size industrial canvas must not bootstrap two unnecessary, mutually dependent bars. */
    public static ScrollWidget<?> wrapExact(String name, IWidget content, int contentWidth, int contentHeight) {
        return wrap(name, content, contentWidth, contentHeight, true);
    }

    private static ScrollWidget<?> wrap(String name, IWidget content, int contentWidth, int contentHeight,
        boolean exact) {
        HorizontalScrollData horizontal = new HorizontalScrollData(false, 4);
        VerticalScrollData vertical = new VerticalScrollData(false, 4);
        horizontal.setCancelScrollEdge(false);
        vertical.setCancelScrollEdge(false);
        horizontal.setScrollSize(exact ? 0 : contentWidth);
        vertical.setScrollSize(exact ? 0 : contentHeight);
        if (exact) {
            horizontal.texture(TerminalUiStyle.SCROLLBAR_THUMB);
            vertical.texture(TerminalUiStyle.SCROLLBAR_THUMB);
        }
        FixedViewport viewport = new FixedViewport(
            horizontal,
            vertical,
            exact ? contentWidth : 0,
            exact ? contentHeight : 0);
        viewport.getScrollArea()
            .setScrollDataX(horizontal);
        if (exact) viewport.padding(0);
        return viewport.name(name)
            .size(contentWidth, contentHeight)
            .child(content);
    }

    private static final class FixedViewport extends ScrollWidget<FixedViewport> {

        private final HorizontalScrollData horizontal;
        private final VerticalScrollData vertical;
        private final int contentWidth, contentHeight;

        FixedViewport(HorizontalScrollData horizontal, VerticalScrollData vertical, int contentWidth,
            int contentHeight) {
            super(vertical);
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
        }

        @Override
        public void postResize() {
            if (contentWidth > 0 && contentHeight > 0) {
                // MUI2 asks whether the other bar would be active if this one were present.
                // Equal-size axes therefore keep each other active. With no genuine overflow,
                // leave both ranges dormant; restore the full canvas as soon as either axis overflows.
                boolean fits = contentWidth <= getScrollArea().width && contentHeight <= getScrollArea().height;
                horizontal.setScrollSize(fits ? 0 : contentWidth);
                vertical.setScrollSize(fits ? 0 : contentHeight);
            }
            horizontal.clamp(getScrollArea());
            vertical.clamp(getScrollArea());
        }
    }
}
