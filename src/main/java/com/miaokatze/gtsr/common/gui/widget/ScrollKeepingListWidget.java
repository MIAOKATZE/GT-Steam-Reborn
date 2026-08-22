package com.miaokatze.gtsr.common.gui.widget;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ListWidget;

/**
 * 重建时保持滚动位置的节点列表（参考 GT5U MTESplitterModuleGui.WorkaroundListWidget）。
 * 原为 MTECacheHubStatusGui / MTESingularityHubStatusGui 逐行同构的私有内部类，SR-O2-05 上提公共顶层类，
 * 宿主滚动偏移经构造注入的读 / 写函数对耦合：
 * dispose 时把当前滚动偏移写回宿主字段（scrollWriter），首次 postResize 时恢复（scrollReader）——
 * scrollTo 内部 clamp 会自动钳位条目减少导致的超界偏移，无需手动处理；
 * shouldScroll 保证仅在重建后的首次布局恢复一次，后续面板拖动等 resize 不回跳。
 */
public class ScrollKeepingListWidget extends ListWidget<IWidget, ScrollKeepingListWidget> {

    private final IntSupplier scrollReader;
    private final IntConsumer scrollWriter;
    private boolean shouldScroll = true;

    public ScrollKeepingListWidget(IntSupplier scrollReader, IntConsumer scrollWriter) {
        this.scrollReader = scrollReader;
        this.scrollWriter = scrollWriter;
    }

    @Override
    public void postResize() {
        super.postResize();
        if (shouldScroll && getScrollData() != null) {
            getScrollData().scrollTo(getScrollArea(), scrollReader.getAsInt());
            shouldScroll = false;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        // 未初始化即被丢弃时 scrollData 为 null，保留旧值即可
        if (getScrollData() != null) {
            scrollWriter.accept(getScrollData().getScroll());
        }
    }
}
