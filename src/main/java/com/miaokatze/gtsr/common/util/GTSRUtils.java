package com.miaokatze.gtsr.common.util;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** 通用工具（v1.10.63）：品牌尾缀行统一入口，禁止逐类硬编码。 */
public final class GTSRUtils {

    private GTSRUtils() {}

    /**
     * 品牌尾缀行（全项目统一调用，禁止逐类硬编码）：
     * WHITE「添加模组：」+ AQUA GT + GREEN - + GOLD Steam + RED - + BLUE Reborn
     */
    public static String getAddedByLine() {
        return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
            + " "
            + EnumChatFormatting.AQUA
            + "GT"
            + EnumChatFormatting.GREEN
            + "-"
            + EnumChatFormatting.GOLD
            + "Steam"
            + EnumChatFormatting.RED
            + "-"
            + EnumChatFormatting.BLUE
            + "Reborn";
    }
}
