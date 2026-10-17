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
        HorizontalScrollData horizontal = new HorizontalScrollData(false, 4);
        VerticalScrollData vertical = new VerticalScrollData(false, 4);
        horizontal.setCancelScrollEdge(false);
        vertical.setCancelScrollEdge(false);
        horizontal.setScrollSize(contentWidth);
        vertical.setScrollSize(contentHeight);
        FixedViewport viewport = new FixedViewport(horizontal, vertical);
        viewport.getScrollArea()
            .setScrollDataX(horizontal);
        return viewport.name(name)
            .size(contentWidth, contentHeight)
            .child(content);
    }

    private static final class FixedViewport extends ScrollWidget<FixedViewport> {

        private final HorizontalScrollData horizontal;
        private final VerticalScrollData vertical;

        FixedViewport(HorizontalScrollData horizontal, VerticalScrollData vertical) {
            super(vertical);
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public void postResize() {
            horizontal.clamp(getScrollArea());
            vertical.clamp(getScrollArea());
        }
    }
}
