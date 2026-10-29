package com.miaokatze.gtsr.common.machine.hatch.critical;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.IntToLongFunction;
import java.util.regex.Pattern;

import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuis;

/** Per-filter long limits; the original stocking slots and native int recipe lifecycle remain intact. */
final class CriticalMEInputSettings {

    private final long[] limits = new long[16];

    CriticalMEInputSettings() {
        Arrays.fill(limits, Long.MAX_VALUE);
    }

    long get(int slot) {
        return limits[slot];
    }

    void set(int slot, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative stocking limit");
        limits[slot] = amount;
    }

    void save(NBTTagCompound tag) {
        NBTTagCompound data = new NBTTagCompound();
        for (int i = 0; i < limits.length; i++) data.setLong(Integer.toString(i), limits[i]);
        tag.setTag("criticalLongLimits", data);
    }

    void load(NBTTagCompound tag) {
        NBTTagCompound data = tag.getCompoundTag("criticalLongLimits");
        for (int i = 0; i < limits.length; i++) {
            String key = Integer.toString(i);
            limits[i] = data.hasKey(key) ? Math.max(0, data.getLong(key)) : Long.MAX_VALUE;
        }
    }

    ParentWidget<?> addControl(ParentWidget<?> content, ModularPanel parent, PanelSyncManager syncManager,
        IntToLongFunction available, java.util.function.BiConsumer<Integer, Long> setter) {
        IPanelHandler settings = syncManager.syncedPanel("criticalLongLimits", true, (manager, handler) -> {
            int[] selected = { 1 };
            IntSyncValue slot = new IntSyncValue(
                () -> selected[0],
                value -> selected[0] = Math.max(1, Math.min(16, value))).allowC2S();
            LongSyncValue limit = new LongSyncValue(
                () -> get(selected[0] - 1),
                value -> setter.accept(selected[0] - 1, Math.max(0, value))).allowC2S();
            LongSyncValue stock = new LongSyncValue(() -> available.applyAsLong(selected[0] - 1));
            manager.syncValue("selected", slot);
            manager.syncValue("limit", limit);
            manager.syncValue("available", stock);
            Flow column = Flow.col()
                .coverChildren()
                .marginTop(15)
                .childPadding(3);
            column.child(
                IKey.lang("gtsr.gui.critical_me.slot")
                    .asWidget());
            column.child(
                new TextFieldWidget().value(slot)
                    .numbersInt(1, 16)
                    .setMaxLength(2)
                    .width(130));
            column.child(
                IKey.lang("gtsr.gui.critical_me.limit")
                    .asWidget());
            // MUI2 numbersLong parses through double; retain every digit above 2^53 with a plain string field.
            column.child(
                new TextFieldWidget().value(limit)
                    .setPattern(Pattern.compile("[0-9]*"))
                    .setValidator(
                        text -> text.isEmpty() ? "0"
                            : new BigInteger(text).min(BigInteger.valueOf(Long.MAX_VALUE))
                                .toString())
                    .setMaxLength(19)
                    .setTextAlignment(Alignment.CENTER)
                    .width(130));
            column.child(
                IKey.dynamic(
                    () -> IKey.lang("gtsr.gui.critical_me.available", Long.toString(stock.getLongValue()))
                        .get())
                    .asWidget());
            return GTGuis.createPopUpPanel("criticalLongLimits")
                .coverChildren()
                .relative(parent)
                .padding(5)
                .child(column)
                .leftRel(1)
                .topRel(0);
        });
        // The normal ME variants have no auto-pull toggle, leaving this existing centre control position free.
        return content.child(
            new ButtonWidget<>().size(16)
                .pos(73, 1)
                .overlay(IKey.str("L"))
                .tooltip(t -> t.addLine(IKey.lang("gtsr.gui.critical_me.limit")))
                .onMousePressed(button -> {
                    if (settings.isPanelOpen()) settings.closePanel();
                    else settings.openPanel();
                    return true;
                }));
    }
}
