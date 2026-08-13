package com.miaokatze.gtsr.common.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch;

import gregtech.api.modularui2.common.CommonWidgets;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/**
 * 红石仓 GUI：词条下拉、阈值、反向开关、更新频率、当前值实时显示（经 DoubleSyncValue 同步，%.2f）。
 *
 * 词条列表同步：controllerMeta 为瞬态字段（不同步客户端），客户端构建 GUI 时拿不到机器真实词条；
 * 因此经 StringSyncValue（"gtsrRedstoneEntries"，仅服务端→客户端）把 "key=displayKey;..." 编码串
 * 同步到客户端解析进缓存，下拉 options 从缓存读取（缓存空时回退标准键），词条变更后重开 GUI 即刷新。
 */
public class MTEGTSRRedstoneHatchGui extends MTEHatchBaseGui<MTEGTSRRedstoneHatch> {

    /** 客户端词条缓存：internalKey → displayKey lang 键（服务端 "gtsrRedstoneEntries" 同步） */
    private final Map<String, String> clientEntryCache = new HashMap<>();
    /** 客户端词条键列表（服务端顺序） */
    private final List<String> clientEntryKeys = new ArrayList<>();

    public MTEGTSRRedstoneHatchGui(MTEGTSRRedstoneHatch machine) {
        super(machine);
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow col = Flow.column()
            .child(createEntryRow(syncManager))
            .child(createInvertRow())
            .child(createIntervalRow())
            .child(createCurrentValueRow(syncManager))
            .coverChildren()
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .childPadding(2);
        return super.createContentSection(panel, syncManager).child(col);
    }

    // ============================================================
    // 词条列表同步（服务端 → 客户端）
    // ============================================================

    /**
     * 注册词条列表同步：服务端 getter 构建 "key=displayKey;..." 编码串，客户端 setter 解析进缓存；
     * 同步到达（含初始同步）时重建下拉 options（服务端同侧重建无副作用）。
     */
    private void registerEntrySync(PanelSyncManager syncManager, DropdownWidget<String, ?> dropdown) {
        StringSyncValue entriesSync = new StringSyncValue(
            () -> "", // client getter：客户端不对外读取
            this::decodeEntries, // client setter：网络接收时解析缓存
            () -> encodeEntries(machine.getAvailableEntryKeys()), // server getter：真实词条
            null); // server setter：无 C2S，客户端不回推
        entriesSync.changeListener(() -> {
            dropdown.clearOptions();
            dropdown.options(getAvailableKeys());
            dropdown.deleteMenu();
        });
        syncManager.syncValue("gtsrRedstoneEntries", entriesSync);
    }

    /** 编码词条列表："key=displayKey;key=displayKey;..."（displayKey 未知时空串，客户端回退） */
    private String encodeEntries(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String displayKey = machine.getEntryDisplayKey(key);
            sb.append(key)
                .append('=')
                .append(displayKey != null ? displayKey : "")
                .append(';');
        }
        return sb.toString();
    }

    /** 解析编码串进缓存（键顺序保留） */
    private void decodeEntries(String encoded) {
        clientEntryCache.clear();
        clientEntryKeys.clear();
        if (encoded == null || encoded.isEmpty()) return;
        for (String part : encoded.split(";")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            String displayKey = part.substring(eq + 1);
            clientEntryCache.put(key, displayKey.isEmpty() ? null : displayKey);
            clientEntryKeys.add(key);
        }
    }

    /** 当前可用词条：客户端优先读缓存（服务端同步），缓存空时回退机器词条（客户端即标准键） */
    private List<String> getAvailableKeys() {
        if (!clientEntryKeys.isEmpty()) return new ArrayList<>(clientEntryKeys);
        return machine.getAvailableEntryKeys();
    }

    /** 词条显示：缓存优先 → 机器词条（服务端）→ 原始键回退；动态求值以在同步到达后自刷新 */
    private IWidget optionToWidget(String key, boolean forSelectedDisplay) {
        return IKey.dynamic(() -> {
            String displayKey = clientEntryCache.get(key);
            if (displayKey == null) displayKey = machine.getEntryDisplayKey(key);
            return displayKey != null ? StatCollector.translateToLocal(displayKey) : key;
        })
            .asWidget();
    }

    // ============================================================
    // 行布局
    // ============================================================

    /** 行 1：词条下拉 + 阈值输入框 + 阈值标签 */
    private Flow createEntryRow(PanelSyncManager syncManager) {
        // 客户端构建时缓存未同步 → 回退标准键；服务端构建时即真实机器词条
        DropdownWidget<String, ?> dropdown = new DropdownWidget<>("gtsrRedstoneMenu", String.class).size(100, 16)
            .value(new StringSyncValue(machine::getEntryKey, machine::setEntryKey).allowC2S())
            .options(getAvailableKeys())
            .optionToWidget(this::optionToWidget);
        registerEntrySync(syncManager, dropdown);
        return Flow.row()
            .child(dropdown)
            .child(
                new TextFieldWidget().numbersDouble()
                    .size(77, 12)
                    .value(new DoubleSyncValue(machine::getThreshold, machine::setThreshold).allowC2S()))
            .child(
                IKey.lang("gtsr.gui.redstone_hatch.threshold")
                    .asWidget())
            .coverChildren()
            .childPadding(2);
    }

    /** 行 2：反向开关 + 反向标签（仿 pH 传感仓 GUI 的布局风格） */
    private Flow createInvertRow() {
        return Flow.row()
            .child(
                CommonWidgets
                    .createInvertButtonRow(new BooleanSyncValue(machine::isInverted, machine::setInverted).allowC2S()))
            .child(
                IKey.lang("gtsr.gui.redstone_hatch.invert")
                    .asWidget())
            .coverChildren()
            .childPadding(2);
    }

    /** 行 3：更新频率输入框（1-200 tick）+ 标签 */
    private Flow createIntervalRow() {
        return Flow.row()
            .child(
                new TextFieldWidget().numbersInt(v -> Math.max(1, Math.min(200, v)))
                    .size(77, 12)
                    .value(new IntSyncValue(machine::getUpdateInterval, machine::setUpdateInterval).allowC2S()))
            .child(
                IKey.lang("gtsr.gui.redstone_hatch.interval")
                    .asWidget())
            .coverChildren()
            .childPadding(2);
    }

    /** 行 4：当前值标签 + 实时值（经 DoubleSyncValue 同步，格式化 %.2f） */
    private Flow createCurrentValueRow(PanelSyncManager syncManager) {
        DoubleSyncValue currentSync = new DoubleSyncValue(() -> machine.getCurrentValue(), v -> {});
        syncManager.syncValue("gtsrRedstoneCurrent", currentSync);
        return Flow.row()
            .child(
                IKey.lang("gtsr.gui.redstone_hatch.current")
                    .asWidget())
            .child(
                IKey.dynamic(() -> String.format(Locale.ENGLISH, "%.2f", currentSync.getValue()))
                    .asWidget())
            .coverChildren()
            .childPadding(2);
    }
}
