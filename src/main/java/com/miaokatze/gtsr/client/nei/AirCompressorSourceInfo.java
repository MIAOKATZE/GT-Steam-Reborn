package com.miaokatze.gtsr.client.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Dimension and biome occupy separate rows, wrapped with the player's actual font. */
@SideOnly(Side.CLIENT)
public final class AirCompressorSourceInfo {

    private AirCompressorSourceInfo() {}

    public static List<String> wrap(String source, int width) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<String> result = new ArrayList<>();
        for (String row : source.replace("；", "\n")
            .replace("; ", "\n")
            .split("\n")) {
            if (!row.trim()
                .isEmpty())
                result
                    .addAll(font.listFormattedStringToWidth(EnumChatFormatting.AQUA + row.trim(), Math.max(32, width)));
        }
        return result;
    }
}
