package com.miaokatze.gtsr.common.api.progress;

import java.util.Locale;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import net.minecraft.util.EnumChatFormatting;

/**
 * 进度显示词条：一条「显示口径」记录（百分比类 0-100、流量 L/s、功率 EU/t 等）。
 * 词条只描述如何取值与如何格式化，不持有机器引用；
 * 实时值经 {@link #getValue()} 读取，显示文本经 {@link #getFormattedText()} 生成。
 * 由 {@link GTSRProgressBar} 容器统一持有，机器基类/mixin 提供 registerEntry 便捷注册。
 */
public class GTSRProgressEntry {

    /** 统一内部键：机器内唯一，用于同步命名（gtsrEntry_&lt;internalKey&gt;）与红石仓/容器查询 */
    private final String internalKey;
    /** 机器本地化显示名 lang 键（如 gtsr.gui.xxx.heat） */
    private final String displayKey;
    /** 数值颜色（显示名固定 WHITE，值文本用此颜色） */
    private final EnumChatFormatting color;
    /** 实时值来源（机器字段 lambda） */
    private final DoubleSupplier valueSupplier;
    /** 格式化器：value → 显示文本（含单位/后缀） */
    private final DoubleFunction<String> formatter;

    private GTSRProgressEntry(String internalKey, String displayKey, EnumChatFormatting color,
        DoubleSupplier valueSupplier, DoubleFunction<String> formatter) {
        this.internalKey = internalKey;
        this.displayKey = displayKey;
        this.color = color;
        this.valueSupplier = valueSupplier;
        this.formatter = formatter;
    }

    /** 工厂：默认 formatter = String.format(Locale.ENGLISH, format, value)（format 如 "%.1f%%"、"%,.0f L/s"、"%,.0f EU/t"） */
    public static GTSRProgressEntry of(String internalKey, String displayKey, String format, EnumChatFormatting color,
        DoubleSupplier valueSupplier) {
        return new GTSRProgressEntry(
            internalKey,
            displayKey,
            color,
            valueSupplier,
            value -> String.format(Locale.ENGLISH, format, value));
    }

    /** 工厂：自定义格式化（如按值追加条件后缀） */
    public static GTSRProgressEntry ofCustom(String internalKey, String displayKey, EnumChatFormatting color,
        DoubleSupplier valueSupplier, DoubleFunction<String> formatter) {
        return new GTSRProgressEntry(internalKey, displayKey, color, valueSupplier, formatter);
    }

    public String getInternalKey() {
        return internalKey;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public EnumChatFormatting getColor() {
        return color;
    }

    /** 实时值（委托 valueSupplier） */
    public double getValue() {
        return valueSupplier.getAsDouble();
    }

    /** 用指定值生成显示文本（GUI 显示走同步值，见 GTSRProgressBarGuiHelper） */
    public String getFormattedText(double value) {
        return formatter.apply(value);
    }

    /** 格式化后的显示文本（含单位/后缀，不含颜色前缀——颜色由 getColor 单独提供） */
    public String getFormattedText() {
        return formatter.apply(getValue());
    }
}
