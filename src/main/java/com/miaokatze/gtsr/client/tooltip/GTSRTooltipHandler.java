package com.miaokatze.gtsr.client.tooltip;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.miaokatze.gtsr.common.util.GTSRUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Retains original dynamic text and never writes to the hovered stack or its NBT. */
public final class GTSRTooltipHandler {

    private static boolean registered;

    public static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(new GTSRTooltipHandler());
        registered = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        List<String> keys = GTSRTooltipCatalog.descriptionKeys(event.itemStack);
        if (keys == null) return;
        GTSRMachineTooltips.rewrite(event.itemStack, event.toolTip, GuiScreen.isShiftKeyDown());
        for (String key : keys) {
            String line = StatCollector.translateToLocal(key);
            if (!containsLine(event.toolTip, line)) event.toolTip.add(EnumChatFormatting.GRAY + line);
        }
        normalizeBrand(event.toolTip, GTSRUtils.getAddedByLine());
    }

    /** Only complete, formatting-free lines are equal; substrings, authors and lore remain untouched. */
    static void normalizeBrand(List<String> lines, String brand) {
        String plain = plain(brand);
        int first = -1;
        for (int index = 0; index < lines.size(); index++) {
            if (!plain.equals(plain(lines.get(index)))) continue;
            if (first < 0) {
                first = index;
                lines.set(index, brand);
            } else {
                lines.remove(index--);
            }
        }
        if (first < 0) lines.add(brand);
    }

    private static boolean containsLine(List<String> lines, String line) {
        String expected = plain(line);
        for (String existing : lines) if (expected.equals(plain(existing))) return true;
        return false;
    }

    private static String plain(String line) {
        return line == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(line);
    }
}
