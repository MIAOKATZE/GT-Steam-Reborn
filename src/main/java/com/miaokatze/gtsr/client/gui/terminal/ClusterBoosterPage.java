package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.BoosterType;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 集群终端·页 2「增幅」（terminal-native-ui N18；旧 MUI2 轨增幅视图（git 基线 b4fabb2）自绘移植，
 * 布局 1:1：六列表 y20..150（行高 15，列宽 148/30/54/86/42/190）/ 空状态提示行 y152 /
 * 2×3 汇总卡 y154..216（191×30）/ 规则两行 y224/234）。
 *
 * <p>
 * 数据流：{@code KEY_BO_STRUCT}（结构字段 {@code typeOrdinal:tier:segment:flags}——结构 revision 界）、
 * {@code KEY_BO_LIVE}（tank 余量+可用性，20t）、{@code KEY_BO_COST}（S7 实耗+联动加成三元组，20t）、
 * {@code KEY_BO_SUM}（8 字段汇总 ×100 定点，20t）。<b>live 每帧重读纪律</b>（v1.11.22 缺流定格教训）：
 * 行底色/行基色/供给列/状态列/汇总卡/规则行全部由 draw 每帧重读缓存——缺流→补液无需重开 GUI 即恢复；
 * 行骨架（类型/tier/段/flags）虽随 STRUCT 语义分组，但绘制同样每帧重解析（零构建期快照求值）。
 * 交互：结构行点击 → SELECT_LOGISTICS(idx)（N18 计划指定交互；客户端先以 KEY_LE_UNITS 单元数
 * 预检索引界内，服务端复核链照旧，越界静默拒绝）。列表滚动偏移自持。
 */
@SideOnly(Side.CLIENT)
final class ClusterBoosterPage implements ClusterPage {

    /** 列表区（滚动）。 */
    private static final int LIST_DY = 20;
    private static final int LIST_H = 130;
    /** 空状态/提示行。 */
    private static final int HINT_DY = LIST_DY + LIST_H + 2;
    /** 汇总区。 */
    private static final int SUMMARY_DY = 154;
    private static final int CELL_H = 30;
    private static final int CELL_GAP = 4;
    private static final int CELL_W = (582 - CELL_GAP * 2) / 3;
    /** 规则两行。 */
    private static final int RULE_DY = SUMMARY_DY + CELL_H * 2 + CELL_GAP + 6;
    /** 数据行高。 */
    private static final int ROW_H = 15;
    /** 列宽：模块/等级/特殊流体/供给/增益/状态。 */
    private static final int[] COLS = { 148, 30, 54, 86, 42, 190 };
    /** 行底：深底 #26262B / 缺流红底 #3A1E1E（旧同值）。 */
    private static final int ROW_BG = 0xFF26262B;
    private static final int ROW_BG_FAIL = 0xFF3A1E1E;

    private final GuiClusterTerminalScreen host;
    /** 列表滚动偏移（自持）。 */
    private int scroll;

    ClusterBoosterPage(GuiClusterTerminalScreen host) {
        this.host = host;
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.boost.title"),
            ox,
            oy,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        drawTableHeader(ox, oy);
        drawRows(ox, oy, mx, my, z);
        GuiClusterTerminalScreen.drawScaledText(font(), hintText(), ox, oy + HINT_DY, 0.6f, GtsrGuiPalette.TEXT_MUTED);
        drawSummary(ox, oy, mx, my);
        drawRules(ox, oy);
    }

    private static void drawTableHeader(int ox, int oy) {
        int cursor = ox;
        for (int i = 0; i < COLS.length; i++) {
            String key = switch (i) {
                case 1 -> "gtsr.cluster.gui.boost.col.tier";
                case 2 -> "gtsr.cluster.gui.boost.col.fluid";
                case 3 -> "gtsr.cluster.gui.boost.col.supply";
                case 4 -> "gtsr.cluster.gui.boost.col.gain";
                case 5 -> "gtsr.cluster.gui.boost.col.status";
                default -> "gtsr.cluster.gui.boost.col.module";
            };
            GuiClusterTerminalScreen.drawScaledText(
                font0(),
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(key),
                cursor + 2,
                oy + 11,
                0.6f,
                GtsrGuiPalette.TEXT_ACCENT);
            cursor += COLS[i] + 2;
        }
    }

    private static net.minecraft.client.gui.FontRenderer font0() {
        return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
    }

    /** 表行（每帧重解析 STRUCT/LIVE/COST；行底色与行基色/状态列随 LIVE 可用位每帧联动）。 */
    private void drawRows(int ox, int oy, int mx, int my, float z) {
        List<int[]> rows = structRows();
        int maxScroll = Math.max(0, rows.size() - LIST_H / ROW_H);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = oy + LIST_DY + (i - this.scroll) * ROW_H;
            if (rowY + ROW_H <= oy + LIST_DY || rowY >= oy + LIST_DY + LIST_H) continue;
            drawBoosterRow(rows.get(i), i, ox, rowY, mx, my);
        }
    }

    /**
     * 单模块行（六列与表头对齐）。缺流表现（行红底 + 行基色红 + 状态「缺 X，增益失效」）每帧重读
     * KEY_BO_LIVE 可用位，补液后无需重开 GUI 即恢复；正常行深底+绿「生效」。供给列只读动态直读
     * KEY_BO_LIVE（20t 周期真值）+ KEY_BO_COST 实耗（S7）。
     */
    private void drawBoosterRow(int[] struct, int rowIndex, int ox, int y, int mx, int my) {
        int typeOrdinal = struct[0], tier = struct[1], segment = struct[2], flags = struct[3];
        BoosterType type = boosterType(typeOrdinal);
        boolean connected = (flags & 0x01) != 0;
        boolean formed = (flags & 0x02) != 0;
        // 行底色每帧重评（首包 STRUCT 先于 LIVE 应用的暂态由后续 tick 自愈）
        GuiClusterTerminalScreen.fillRect(
            ox,
            y,
            GuiClusterTerminalScreen.CONTENT_W,
            ROW_H,
            this.host.zLevel(),
            liveRow(rowIndex)[1] == 0 ? ROW_BG_FAIL : ROW_BG);
        // 模块名 + @段N（行基色：缺流红 / 正常白，与行底色同源）
        String moduleText = EnumChatFormatting.BOLD + tr(type.getLangKey())
            + EnumChatFormatting.WHITE
            + (segment >= 0 ? String.format(tr("gtsr.gui.cluster.editor.segment"), segment) : "");
        String base = baseColor(rowIndex);
        GuiClusterTerminalScreen
            .drawScaledText(font(), base + moduleText, ox + 2, y + 4, 0.65f, GtsrGuiPalette.TEXT_BODY);
        // 等级
        String tierText = tier >= 0 ? tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey())
            : "--";
        GuiClusterTerminalScreen
            .drawScaledText(font(), base + tierText, ox + COLS[0] + 2 + 2, y + 4, 0.65f, GtsrGuiPalette.TEXT_BODY);
        // 特殊流体
        String fluidText = tr(type.getFluidLangKey());
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            base + fluidText,
            ox + COLS[0] + COLS[1] + 6,
            y + 4,
            0.65f,
            GtsrGuiPalette.TEXT_BODY);
        // 供给（只读检测）：余量 L + S7 实际秒耗（联动加成后口径）；悬浮 tooltip 显示代入实值公式串
        int supplyX = ox + COLS[0] + COLS[1] + COLS[2] + 8;
        int[] now = liveRow(rowIndex);
        String state = now[1] != 0 ? EnumChatFormatting.GREEN.toString() : EnumChatFormatting.RED.toString();
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            state + NumberFormatUtil.formatNumber(
                now[0]) + " L " + EnumChatFormatting.WHITE + "· " + rateText(costLpsX10(rowIndex)) + " L/s",
            supplyX,
            y + 4,
            0.65f,
            GtsrGuiPalette.TEXT_BODY);
        // 增益
        String gainText = tier >= 0 && formed && connected ? formatGain(type, tier) : "--";
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            base + gainText,
            ox + COLS[0] + COLS[1] + COLS[2] + COLS[3] + 10,
            y + 4,
            0.65f,
            GtsrGuiPalette.TEXT_BODY);
        // 状态：缺流红「缺 X，增益失效」/ 未关联/未成型 / 绿「生效」（可用位每帧重读）
        String statusText;
        if (!connected) {
            statusText = EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unlinked");
        } else if (!formed) {
            statusText = EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unformed");
        } else if (liveRow(rowIndex)[1] == 0) {
            statusText = EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.boost.fail"), fluidText);
        } else {
            statusText = EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.boost.active");
        }
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            statusText,
            ox + COLS[0] + COLS[1] + COLS[2] + COLS[3] + COLS[4] + 12,
            y + 4,
            0.65f,
            GtsrGuiPalette.TEXT_BODY);
        // 行命中：tooltip（S7 公式串）+ SELECT_LOGISTICS 点击预检标记
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W && my >= y && my < y + ROW_H) {
            String formula = costFormulaText(rowIndex);
            if (!formula.isEmpty()) {
                List<String> tip = new ArrayList<String>();
                tip.add(formula);
                this.host.requestTooltip("bo" + rowIndex, tip);
            }
        }
    }

    /** 行基色：缺流红 / 正常白（每帧重读 KEY_BO_LIVE 可用位，与行底色同源）。 */
    private String baseColor(int rowIndex) {
        return liveRow(rowIndex)[1] == 0 ? EnumChatFormatting.RED.toString() : EnumChatFormatting.WHITE.toString();
    }

    /** 增益格式：并行 +N（台）；百分比类 +N%。 */
    private static String formatGain(BoosterType type, int tier) {
        int value = type.getBoosterValue(tier);
        return type == BoosterType.PARALLEL ? "+" + value : "+" + value + "%";
    }

    /** 空状态/提示行：无模块 / 集群未运行 / 缺流体 N。 */
    private String hintText() {
        List<int[]> rows = structRows();
        if (rows.isEmpty()) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.empty.hint");
        }
        if (!ClusterTerminalClientCache.getBool(ClusterTerminalData.KEY_ENABLED, false)) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.empty.off");
        }
        int failed = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (liveRow(i)[1] == 0) failed++;
        }
        if (failed > 0) {
            return EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.boost.miss"), failed);
        }
        return "";
    }

    // ==================== 2×3 汇总卡 + 规则两行 ====================

    private void drawSummary(int ox, int oy, int mx, int my) {
        for (int i = 0; i < 6; i++) {
            int x = ox + (i % 3) * (CELL_W + CELL_GAP);
            int y = oy + SUMMARY_DY + (i / 3) * (CELL_H + CELL_GAP);
            drawSummaryCell(i, x, y, mx, my);
        }
    }

    /** 汇总卡：深底 + 黄标签（0.6）+ 白粗值（0.8，每帧直读缓存）+ 卡注（0.55）。 */
    private void drawSummaryCell(int index, int x, int y, int mx, int my) {
        String labelKey = switch (index) {
            case 1 -> "gtsr.cluster.gui.boost.sum.parallel";
            case 2 -> "gtsr.cluster.gui.boost.sum.primary";
            case 3 -> "gtsr.cluster.gui.boost.sum.secondary";
            case 4 -> "gtsr.cluster.gui.boost.sum.saver";
            case 5 -> "gtsr.cluster.gui.boost.sum.mult";
            default -> "gtsr.cluster.gui.boost.sum.speed";
        };
        GuiClusterTerminalScreen.fillRect(x, y, CELL_W, CELL_H, this.host.zLevel(), 0xFF26262B);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.YELLOW + tr(labelKey),
            x + 3,
            y + 2,
            0.6f,
            GtsrGuiPalette.TEXT_ACCENT);
        GuiClusterTerminalScreen
            .drawScaledText(font(), summaryValue(index), x + 3, y + 11, 0.8f, GtsrGuiPalette.TEXT_WHITE);
        GuiClusterTerminalScreen
            .drawScaledText(font(), noteText(labelKey), x + 3, y + 22, 0.55f, GtsrGuiPalette.TEXT_MUTED);
    }

    /** 汇总卡值（服务端 KEY_BO_SUM 真值，×100 定点解码；每帧重读）。 */
    private String summaryValue(int index) {
        return switch (index) {
            case 1 -> "+" + summaryAt(1);
            case 2 -> pctText(summaryAt(2));
            case 3 -> pctText(summaryAt(3));
            case 4 -> saverText();
            case 5 -> multText();
            default -> pctText(summaryAt(0));
        };
    }

    /** 卡注：蒸汽乘子卡 = 生效 N·失效 N；节汽卡超限 = 截断警示；其余 = 静态说明。 */
    private String noteText(String labelKey) {
        if (labelKey.endsWith("mult")) {
            return EnumChatFormatting.WHITE
                + String.format(tr("gtsr.cluster.gui.boost.sum.count"), summaryAt(6), summaryAt(7));
        }
        if (labelKey.endsWith("saver")) {
            int raw = summaryAt(4);
            if (raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1) {
                return EnumChatFormatting.RED + tr("gtsr.cluster.gui.boost.saver_capped");
            }
            return EnumChatFormatting.WHITE + "≤ 48%";
        }
        if (labelKey.endsWith("primary")) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.note.primary");
        }
        return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.note.additive");
    }

    private static String pctText(int x100) {
        double pct = x100 / 100.0D * 100.0D;
        return "+" + (Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct))
            : String.format("%.1f", pct)) + "%";
    }

    private String saverText() {
        // x100 定点：8 = 8%；截断展示 min(raw, 48)，超限红字
        int raw = summaryAt(4);
        int capped = (int) Math.min(raw, ClusterParams.STEAM_SAVER_CAP * 100);
        boolean truncated = raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1;
        String text = "-" + capped + "%";
        return truncated ? EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD + text
            : EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + text;
    }

    private String multText() {
        int x100 = summaryAt(5);
        return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
            + "×"
            + String.format("%.2f", x100 / 100.0D);
    }

    /** 规则两行：叠加规则中性灰 + 当前异常状态色。 */
    private void drawRules(int ox, int oy) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.rule.stack"),
            ox,
            oy + RULE_DY,
            0.6f,
            GtsrGuiPalette.TEXT_MUTED);
        GuiClusterTerminalScreen
            .drawScaledText(font(), ruleStateText(), ox, oy + RULE_DY + 10, 0.6f, GtsrGuiPalette.TEXT_BODY);
    }

    private String ruleStateText() {
        List<int[]> rows = structRows();
        if (rows.isEmpty()) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.boost.rule.none");
        }
        int failed = summaryAt(7);
        if (failed > 0) {
            return EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.boost.rule.fail"), failed);
        }
        return EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.boost.rule.ok");
    }

    // ==================== 快照解析 ====================

    /**
     * 解析 KEY_BO_STRUCT：条目逗号分隔，条目内 {@code typeOrdinal:tier:segment:flags} 四元组列表
     * （与 KEY_BO_LIVE 按下标一一对应；畸形四元组防御跳过）。
     */
    private List<int[]> structRows() {
        List<int[]> rows = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_STRUCT, "");
        if (encoded.isEmpty()) return rows;
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length != 4) continue;
            try {
                rows.add(
                    new int[] { Integer.parseInt(fields[0].trim()), Integer.parseInt(fields[1].trim()),
                        Integer.parseInt(fields[2].trim()), Integer.parseInt(fields[3].trim()) });
            } catch (NumberFormatException ignored) {
                // 畸形四元组跳过
            }
        }
        return rows;
    }

    /** 解析 KEY_BO_LIVE 第 index 项 {@code amount,available}（越界/畸形回 {0,0}）。 */
    private static int[] liveRow(int index) {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_LIVE, "");
        if (encoded.isEmpty()) return new int[] { 0, 0 };
        String[] entries = encoded.split(",", -1);
        if (index < 0 || index >= entries.length) return new int[] { 0, 0 };
        String[] fields = entries[index].split(":", -1);
        if (fields.length != 2) return new int[] { 0, 0 };
        try {
            return new int[] { Integer.parseInt(fields[0].trim()), Integer.parseInt(fields[1].trim()) };
        } catch (NumberFormatException e) {
            return new int[] { 0, 0 };
        }
    }

    /**
     * 解析 KEY_BO_COST 第 index 项（S7 实耗）：{@code lpsX10:base:pct:tier:type:pct:tier:type...}
     * 变长字段整型数组；越界/畸形返回 null（调用方按无数据显示处理）。与 KEY_BO_STRUCT/LIVE
     * 按下标一一对应。
     */
    private static int[] costRow(int index) {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_COST, "");
        if (encoded.isEmpty()) return null;
        String[] entries = encoded.split(",", -1);
        if (index < 0 || index >= entries.length) return null;
        String[] fields = entries[index].split(":", -1);
        if (fields.length < 2 || (fields.length - 2) % 3 != 0) return null;
        try {
            int[] out = new int[fields.length];
            for (int i = 0; i < fields.length; i++) {
                out[i] = Integer.parseInt(fields[i].trim());
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 第 index 行实际秒耗 ×10 定点（无数据/畸形回 0）。 */
    private static int costLpsX10(int index) {
        int[] cost = costRow(index);
        return cost != null ? cost[0] : 0;
    }

    /** 秒耗文本：×10 定点 → 整数值省小数、非整数保留一位小数（如 575→"57.5"、400→"40"）。 */
    private static String rateText(int lpsX10) {
        if (lpsX10 % 10 == 0) return String.valueOf(lpsX10 / 10);
        return String.format("%.1f", lpsX10 / 10.0D);
    }

    /**
     * S7 公式串（tooltip，代入实值）：{@code 基础 50 × (1 + 10%[速度 钢] + 5%[并行 青铜]) = 57.5 L/s}。
     * 施加方类型经三元组 typeOrdinal 本地化（速度/并行加成表同值，不可由 pct 反推）；无联动加成时
     * 显示 {@code 基础 N × (1) = N L/s}；无实耗数据（未成型/越界）返回空串不显示 tooltip。
     */
    private static String costFormulaText(int rowIndex) {
        int[] cost = costRow(rowIndex);
        if (cost == null || cost.length < 2 || cost[1] <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.WHITE)
            .append(tr("gtsr.cluster.gui.boost.cost.base"))
            .append(' ')
            .append(NumberFormatUtil.formatNumber(cost[1]))
            .append(" × (1");
        for (int i = 2; i + 2 < cost.length; i += 3) {
            int pct = cost[i], tier = cost[i + 1], typeOrdinal = cost[i + 2];
            sb.append(" + ")
                .append(pct)
                .append("%[")
                .append(sourceLabel(typeOrdinal))
                .append(' ')
                .append(tierLabel(tier))
                .append(']');
        }
        sb.append(") = ")
            .append(rateText(cost[0]))
            .append(" L/s");
        return sb.toString();
    }

    /** 施加方类型短标签（SPEED/PARALLEL；越界回退并行）。 */
    private static String sourceLabel(int typeOrdinal) {
        if (typeOrdinal == BoosterType.SPEED.ordinal()) {
            return tr("gtsr.cluster.gui.boost.cost.src.speed");
        }
        return tr("gtsr.cluster.gui.boost.cost.src.parallel");
    }

    /** 施加方 tier 标签（复用集群层级 lang key；越界回 "--"）。 */
    private static String tierLabel(int tier) {
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return "--";
        return tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey());
    }

    /** 解析 KEY_BO_SUM 八字段 CSV（畸形回退全 0 数组；旧 parseIntCsv 移植）。 */
    private static int[] summary() {
        return parseIntCsv(ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_SUM, ""), 8);
    }

    private static int summaryAt(int index) {
        int[] array = summary();
        return index >= 0 && index < array.length ? array[index] : 0;
    }

    /** 客户端解析定长 int CSV（畸形项回 0；旧 MUI2 同步轨 parseIntCsv 移植）。 */
    private static int[] parseIntCsv(String csv, int length) {
        int[] out = new int[length];
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split(",", -1);
        int n = Math.min(length, parts.length);
        for (int i = 0; i < n; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 序号安全取增幅类型（越界回退 PARALLEL）。 */
    private static BoosterType boosterType(int ordinal) {
        BoosterType[] values = BoosterType.values();
        if (ordinal < 0 || ordinal >= values.length) return BoosterType.PARALLEL;
        return values[ordinal];
    }

    // ==================== 输入 ====================

    /**
     * 结构行点击 → SELECT_LOGISTICS(idx)（N18 计划指定交互）：客户端先以 KEY_LE_UNITS 单元数
     * 预检索引界内（镜像服务端复核），越界不发包；发送后 pollTimer 归零即时补发。
     */
    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        List<int[]> rows = structRows();
        int row = (my - (oy + LIST_DY)) / ROW_H + this.scroll;
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy + LIST_DY
            && my < oy + LIST_DY + LIST_H
            && row >= 0
            && row < rows.size()) {
            int unitCount = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_UNITS, "")
                .isEmpty() ? 0
                    : ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_UNITS, "")
                        .split(",", -1).length;
            if (row < unitCount) {
                this.host.clusterAction(ClusterTerminalActions.SELECT_LOGISTICS, intPayload(row));
            }
            return true;
        }
        return false;
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H) {
            this.scroll += dir;
        }
    }

    // ==================== payload 构造 ====================

    /** SELECT_LOGISTICS payload：[idx int]。 */
    private static byte[] intPayload(int idx) {
        net.minecraft.network.PacketBuffer pb = new net.minecraft.network.PacketBuffer(
            io.netty.buffer.Unpooled.buffer(4));
        pb.writeInt(idx);
        byte[] payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
        return payload;
    }

    private static net.minecraft.client.gui.FontRenderer font() {
        return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
