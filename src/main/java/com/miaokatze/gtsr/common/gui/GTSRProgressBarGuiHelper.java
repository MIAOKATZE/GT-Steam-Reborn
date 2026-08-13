package com.miaokatze.gtsr.common.gui;

import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.api.progress.IGTSRProgressProvider;

/** 进度行渲染 helper：把机器的 GTSRProgressEntry 词条逐行追加到终端文本列表，并做值同步。 */
public final class GTSRProgressBarGuiHelper {

    private GTSRProgressBarGuiHelper() {}

    /**
     * 为机器的每个词条生成一行（样式仿 MTELargeGeothermalSteamBoilerGui.createTerminalTextWidget 行样式）：
     * WHITE 显示名 + 词条颜色值文本 + RESET，marginBottom(2) + fullWidth()。
     * 值同步用 holder 模式：每词条一个 Double[1] holder，
     * DoubleSyncValue(getter 优先读 holder，否则实时值；setter 写 holder) + syncValue("gtsrEntry_&lt;internalKey&gt;")。
     * 服务器侧 getter 读实时值、客户端 setter 写 holder、客户端 getter 读 holder。
     * 无需调用 machine.cacheEntryValue（holder 已足够；容器缓存方法供红石仓/将来使用）。
     * machine.getProgressEntries() 为空时不添加任何行。
     *
     * @param list        终端文本列表（createTerminalTextWidget 返回值）
     * @param syncManager GUI 同步管理器
     * @param machine     进度提供者（机器）
     */
    public static void appendEntryRows(ListWidget<IWidget, ?> list, PanelSyncManager syncManager,
        IGTSRProgressProvider machine) {
        List<GTSRProgressEntry> entries = machine.getProgressEntries();
        if (entries == null || entries.isEmpty()) return;
        for (GTSRProgressEntry entry : entries) {
            String internalKey = entry.getInternalKey();
            // holder 模式：客户端 setter 写 holder、客户端 getter 读 holder（优先于实时值）；
            // 显示路径经 entry.getFormattedText(sync.getValue()) 走同步值（客户端机器字段可能未同步，不能直接读 supplier）
            Double[] holder = new Double[1];
            DoubleSyncValue sync = new DoubleSyncValue(
                () -> holder[0] != null ? holder[0] : machine.getEntryValue(internalKey),
                value -> holder[0] = value);
            syncManager.syncValue("gtsrEntry_" + internalKey, sync);
            list.child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal(entry.getDisplayKey())
                        + entry.getColor()
                        + entry.getFormattedText(sync.getValue())
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
        }
    }
}
