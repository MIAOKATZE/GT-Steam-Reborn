package com.miaokatze.gtsr.common.gui;

import java.util.function.IntSupplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ListWidget;

/** Storage diagnostics use the same live, server-synchronized counts as the hub GUI. */
public final class HubStorageGuiHelper {

    public static final String MISSING_WARNING_NAME = "gtsr.hub.storage.missing";
    public static final String CONSEQUENCE_WARNING_NAME = "gtsr.hub.storage.consequence";

    private HubStorageGuiHelper() {}

    public static void appendWarningRows(ListWidget<IWidget, ?> list, IntSupplier tier, IntSupplier normal,
        IntSupplier reinforced, IntSupplier overpressure) {
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.RED + StatCollector.translateToLocal(
                    missingUnitKey(tier.getAsInt(), normal.getAsInt(), reinforced.getAsInt(), overpressure.getAsInt()))
                    + EnumChatFormatting.RESET)
                .asWidget()
                .name(MISSING_WARNING_NAME)
                .setEnabledIf(
                    w -> isMissing(tier.getAsInt(), normal.getAsInt(), reinforced.getAsInt(), overpressure.getAsInt()))
                .marginBottom(2)
                .fullWidth());
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                    normal.getAsInt() + reinforced.getAsInt() + overpressure.getAsInt() == 0
                        ? "gtsr.gui.hub.storage_no_capacity"
                        : "gtsr.gui.hub.storage_cannot_form")
                    + EnumChatFormatting.RESET)
                .asWidget()
                .name(CONSEQUENCE_WARNING_NAME)
                .setEnabledIf(
                    w -> isMissing(tier.getAsInt(), normal.getAsInt(), reinforced.getAsInt(), overpressure.getAsInt()))
                .marginBottom(2)
                .fullWidth());
    }

    private static boolean isMissing(int tier, int normal, int reinforced, int overpressure) {
        if (tier >= 3) return overpressure == 0;
        if (tier == 2) return reinforced == 0;
        if (tier == 1) return normal == 0;
        // Before a complete structure scan the tier is unknown; report only the observed absence.
        return normal + reinforced + overpressure == 0;
    }

    private static String missingUnitKey(int tier, int normal, int reinforced, int overpressure) {
        if (!isMissing(tier, normal, reinforced, overpressure)) return "";
        if (tier >= 3) return "gtsr.gui.hub.storage_missing_overpressure";
        if (tier == 2) return "gtsr.gui.hub.storage_missing_reinforced";
        if (tier == 1) return "gtsr.gui.hub.storage_missing_normal";
        return "gtsr.gui.hub.storage_missing";
    }
}
