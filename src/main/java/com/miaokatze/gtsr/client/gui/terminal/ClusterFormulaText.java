package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.common.util.GtsrNumFormat;

/**
 * KEY_F_FORMULA 串（契约 A）→ 结构化多行的纯静态解析助手（性能页与链编辑页共用；package-private、
 * 无状态无缓存，draw 每帧重建行集直接调用）。输入协议：空链 legacy = 串内无 {@code |}（如
 * "0 L/秒"）单行直显；有链 = {@code |} 连令牌行、{@code :} 分段——{@code S:<idx从1>:<round蒸汽>:<round刻>}
 * ×N、{@code M:<链数>:<%.2f惩罚乘子>:<%.0f节汽%×100>}、{@code R:<%.0f结果L/秒>}；未知令牌与
 * 畸形段（段数不符/数字解析失败）静默丢弃，同 ClusterPerfPage.parseDetail 前向兼容纪律。
 * 配色遵守 ClusterPerfPage 四段式纪律：分步灰 §7 / 附注暗灰 §8（小字）/ 结果绿 §a；lang 键
 * {@code gtsr.terminal.perf.formula.*}（step/tick_unit/chains/penalty/saver/rate_unit，lang 切片落地）。
 */
final class ClusterFormulaText {

    /** 单条成品行。 */
    static final class Line {

        /** 含完整 § 色码的成品行。 */
        public final String text;
        /** true = 0.6f 小字行（仅 M 附注行）。 */
        public final boolean small;

        Line(String text, boolean small) {
            this.text = text;
            this.small = small;
        }
    }

    private ClusterFormulaText() {}

    /** 解析 KEY_F_FORMULA 串：null/空串返回空表；无 {@code |} 即 legacy 单行 §a 直显。 */
    static List<Line> parse(String raw) {
        List<Line> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        if (raw.indexOf('|') < 0) {
            out.add(new Line(EnumChatFormatting.GREEN + raw, false));
            return out;
        }
        for (String row : raw.split("\\|", -1)) {
            if (row.isEmpty()) continue;
            Line line = parseRow(row);
            if (line != null) out.add(line);
        }
        return out;
    }

    /** 单令牌行分发（S 分步/M 附注/R 结果；段数不符或数字解析失败返回 null 整行丢弃）。 */
    private static Line parseRow(String row) {
        String[] f = row.split(":", -1);
        try {
            switch (f[0]) {
                case "S": {
                    if (f.length != 4) return null;
                    long idx = Long.parseLong(f[1].trim());
                    long steam = Long.parseLong(f[2].trim());
                    long ticks = Long.parseLong(f[3].trim());
                    return new Line(
                        EnumChatFormatting.GRAY + String.format(tr("gtsr.terminal.perf.formula.step"), idx)
                            + " "
                            + GtsrNumFormat.grouped(steam)
                            + "×"
                            + GtsrNumFormat.grouped(ticks)
                            + " "
                            + tr("gtsr.terminal.perf.formula.tick_unit"),
                        false);
                }
                case "M": {
                    if (f.length != 4) return null;
                    long chains = Long.parseLong(f[1].trim());
                    double penalty = Double.parseDouble(f[2].trim());
                    long saverX100 = Long.parseLong(f[3].trim());
                    return new Line(
                        EnumChatFormatting.DARK_GRAY + tr("gtsr.terminal.perf.formula.chains")
                            + " "
                            + chains
                            + "   "
                            + tr("gtsr.terminal.perf.formula.penalty")
                            + " ×"
                            + String.format("%.2f", penalty)
                            + "   "
                            + tr("gtsr.terminal.perf.formula.saver")
                            + " -"
                            + String.format("%.1f", (double) saverX100)
                            + "%",
                        true);
                }
                case "R": {
                    if (f.length != 2) return null;
                    long rate = Long.parseLong(f[1].trim());
                    return new Line(
                        EnumChatFormatting.GREEN + "= "
                            + GtsrNumFormat.grouped(rate)
                            + " "
                            + tr("gtsr.terminal.perf.formula.rate_unit"),
                        false);
                }
                default:
                    return null; // 未知令牌（前向兼容）静默丢弃
            }
        } catch (NumberFormatException ignored) {
            return null; // 畸形数字段整行丢弃
        }
    }

    /** lang 简写（包内单源 {@link GuiClusterTerminalScreen#tr}）。 */
    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
