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

    /**
     * 枢纽仓 hatch 读数行（Waila）：GREEN 存量 + " L" + RESET 空格 YELLOW 容量 + " L" + RESET。
     * 四份枢纽 I/O hatch getInfoData 双行块单源（O2-16）。
     */
    public static String formatHubTankLine(long aStored, long aCapacity) {
        return EnumChatFormatting.GREEN + UnitFormatUtil.format(aStored)
            + " L"
            + EnumChatFormatting.RESET
            + " "
            + EnumChatFormatting.YELLOW
            + UnitFormatUtil.format(aCapacity)
            + " L"
            + EnumChatFormatting.RESET;
    }
}
