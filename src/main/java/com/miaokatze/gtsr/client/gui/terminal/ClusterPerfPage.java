package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 集群终端·第五页「性能」（terminal-native-ui S3 新独立页：集群级极详细只读页，六分组三栏排版，
 * 只消费既有缓存键、零协议扩展）。
 *
 * <p>
 * 版面：页标题 + 三栏各 191px（栏距 4），每栏一个 {@link GtsrGuiList}（行高 11，惰性重建范式
 * 照链路页 perfList）；组标题行=金粗 0.7f（{@code gtsr.terminal.perf.group.*}），数据行 0.7f，
 * 长行 ellipsis。三栏六分组：
 * <ul>
 * <li>栏 1【运行汇总】：耗时（KEY_F_TIME ×100 定点→%.2f 秒）/ 有效并行（KEY_F_PAR raw）/
 * 预测吞吐（KEY_F_THRU ×100→%.2f）/ 实际加权公式（KEY_F_FORMULA，标签+长串两行）/ 热量 / 润滑 /
 * 真实吞吐 / 累计处理（后四行与主壳顶卡同口径，数值 grouped）；</li>
 * <li>栏 2【蒸汽口径 + 集群详情】：总蒸汽耗与链蒸汽耗（KEY_F_TOTAL/KEY_F_STEAM 均 ×100 定点，
 * <b>÷100.0D 解码修复</b>——服务端 toX100 后原值直读会放大 100 倍）+ 结算蒸汽（KEY_STEAM 原值，
 * 行尾注口径来源）；KEY_F_DETAIL 的 FLUID/LUBE/BOOST 令牌行（自 d6acf00 基线
 * formatDetailRow/x100Text/boosterLabel 逐字移植，LINK/LOGI/PEAK 由链路页消费、本页丢弃）；</li>
 * <li>栏 3【链路概览 + 增幅汇总 + 增幅实耗】：KEY_LE_CHAINS len 派生四行（链数/总链步/最长链/
 * 平均链长，len 首整数 token 防御解析）+ KEY_BO_SUM 八字段（增幅页汇总卡同式解码，
 * pctText/saverText/multText）+ KEY_BO_COST 逐增幅器实耗行（tooltip 完整公式，
 * costFormulaText 同式、NumberFormatUtil 换 {@link GtsrNumFormat#grouped}）。</li>
 * </ul>
 *
 * <p>
 * <b>live 每帧重读纪律</b>：三列行集全部由 draw 每帧重建（零构造期快照）；列表实例仅随几何
 * 惰性重建。交互：只读页——内容区点击一律消费防穿透；滚轮分栏转发鼠标所落栏的
 * {@code list.handleWheel}；拖拽/释放转发各栏列表（滚动条）。
 */
@SideOnly(Side.CLIENT)
final class ClusterPerfPage implements ClusterPage {

    /** 三等栏宽与栏距（582 = 191×3 + 4×2）。 */
    private static final int COL_W = (GuiClusterTerminalScreen.CONTENT_W - 2 * 4) / 3;
    private static final int COL_GAP = 4;
    /** 列表起始偏移（页标题行之下）与高度。 */
    private static final int LIST_DY = 14;
    private static final int LIST_H = GuiClusterTerminalScreen.CONTENT_H - LIST_DY;
    /** 数据行高（组标题行同行高，金粗区分）。 */
    private static final int ROW_H = 11;

    private final GuiClusterTerminalScreen host;
    /** 三栏滚动列表（惰性重建范式）。 */
    private final GtsrGuiList[] colLists = new GtsrGuiList[3];
    /** 各栏列表左缘几何快照（top/width/height 三栏共享）。 */
    private final int[] colListLefts = new int[3];
    private int colListTop = -1;
    private int colListWidth = -1;
    private int colListHeight = -1;
    /** 本帧三列行集（draw 每帧重建；live 每帧重读纪律）。 */
    private final List<List<String>> colFrameLines;
    /** 本帧增幅实耗公式串（与第三列 boostcost 行下标一一对应；tooltip 用）。 */
    private final List<String> boostFormulas = new ArrayList<>();
    /** 本帧 boostcost 首行在第三列行集中的下标（未含 boostcost 组时置 Integer.MAX_VALUE）。 */
    private int boostCostStart = Integer.MAX_VALUE;

    ClusterPerfPage(GuiClusterTerminalScreen host) {
        this.host = host;
        this.colFrameLines = new ArrayList<List<String>>();
        for (int i = 0; i < 3; i++) {
            this.colFrameLines.add(Collections.<String>emptyList());
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.terminal.perf.title"),
            ox,
            oy,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        // 三列行集每帧重建（draw 每帧重读缓存；boostFormulas/boostCostStart 随第三列同步刷新）
        this.colFrameLines.set(0, buildSummaryLines());
        this.colFrameLines.set(1, buildSteamDetailLines());
        this.colFrameLines.set(2, buildChainsBoostLines());
        for (int col = 0; col < 3; col++) {
            ensureColList(col, ox + col * (COL_W + COL_GAP), oy + LIST_DY, COL_W, LIST_H);
            this.colLists[col].draw(mx, my, z);
        }
    }

    /** 三栏列表惰性重建（几何快照比对，链路页 ensurePerfList 同范式）。 */
    private void ensureColList(int col, int left, int top, int width, int height) {
        if (this.colLists[col] != null && this.colListLefts[col] == left
            && this.colListTop == top
            && this.colListWidth == width
            && this.colListHeight == height) {
            return;
        }
        this.colListLefts[col] = left;
        this.colListTop = top;
        this.colListWidth = width;
        this.colListHeight = height;
        GtsrGuiList list = new GtsrGuiList(this.host, left, top, width, height, ROW_H);
        final int c = col;
        list.setRowSource(
            () -> this.colFrameLines.get(c)
                .size());
        list.setRowPainter((index, x, y, mouseX, mouseY) -> paintPerfRow(c, index, x, y, mouseX, mouseY));
        this.colLists[col] = list;
    }

    /** 单行绘制：数据行 0.7f 长行 ellipsis（组标题行经 § GOLD/BOLD 前缀同款渲染）；boostcost 行命中出公式 tooltip。 */
    private void paintPerfRow(int col, int index, int x, int y, int mx, int my) {
        List<String> lines = this.colFrameLines.get(col);
        if (index < 0 || index >= lines.size()) return;
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            GtsrGuiList.ellipsis(font(), lines.get(index), (int) ((this.colListWidth - 6) / 0.7f)),
            x + 3,
            y + 2,
            0.7f,
            GtsrGuiPalette.TEXT_BODY);
        if (col == 2 && mx >= x && mx < x + this.colListWidth && my >= y && my < y + ROW_H) {
            int booster = index - this.boostCostStart;
            if (booster >= 0 && booster < this.boostFormulas.size()) {
                this.host.requestTooltip(
                    "perf.bo" + booster,
                    GuiClusterTerminalScreen.lines(this.boostFormulas.get(booster)));
            }
        }
    }

    // ==================== 栏 1【运行汇总】 ====================

    /** 运行汇总（KEY_F_* ×100 定点解码 + 顶卡口径四行；每帧重读）。 */
    private List<String> buildSummaryLines() {
        List<String> out = new ArrayList<>();
        out.add(groupHeader("gtsr.terminal.perf.group.summary"));
        int timeRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TIME, 0);
        int parRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        int thruRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_THRU, 0);
        out.add(kvLine("gtsr.cluster.gui.link.perf.time", String.format("%.2f", timeRaw / 100.0D) + " s"));
        out.add(kvLine("gtsr.cluster.gui.link.perf.parallel", String.valueOf(parRaw)));
        out.add(
            kvLine(
                "gtsr.cluster.gui.link.perf.thru",
                String.format("%.2f", thruRaw / 100.0D) + " " + tr("gtsr.cluster.gui.card.thru.unit")));
        // 实际加权公式：标签 + 长串两行（ellipsis 兜底）
        out.add(EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.link.perf.formula") + " =");
        out.add(
            EnumChatFormatting.GREEN + ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_F_FORMULA, "0 L/s"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.heat",
                ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT, 0) + "%"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.lube",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LUBE, 0)) + " L/s"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.thru",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_THRU, 0)) + " "
                    + tr("gtsr.cluster.gui.card.thru.unit")));
        out.add(
            EnumChatFormatting.YELLOW + String.format(
                tr("gtsr.cluster.gui.card.thru.total"),
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getLong(ClusterTerminalData.KEY_TOTAL, 0L))));
        return out;
    }

    // ==================== 栏 2【蒸汽口径 + 集群详情】 ====================

    /**
     * 蒸汽口径三行 + 集群详情行：总蒸汽耗/链蒸汽耗为 ×100 定点（<b>÷100.0D 解码修复</b>），
     * 结算蒸汽为 KEY_STEAM 原值（行尾注口径来源）；详情仅消费 FLUID/LUBE/BOOST 令牌行。
     */
    private List<String> buildSteamDetailLines() {
        List<String> out = new ArrayList<>();
        out.add(groupHeader("gtsr.terminal.perf.group.steam"));
        int totalRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TOTAL, 0);
        int chainRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_STEAM, 0);
        out.add(
            kvLine(
                "gtsr.cluster.gui.link.perf.steam_total",
                GtsrNumFormat.grouped(Math.round(totalRaw / 100.0D)) + " L/s"));
        out.add(
            kvLine("gtsr.cluster.gui.link.perf.steam", GtsrNumFormat.grouped(Math.round(chainRaw / 100.0D)) + " L/s")
                + " "
                + EnumChatFormatting.GRAY
                + "· "
                + tr("gtsr.terminal.perf.caliber.formula"));
        out.add(
            kvLine(
                "gtsr.cluster.gui.card.steam",
                GtsrNumFormat.grouped(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_STEAM, 0)) + " L/s")
                + " "
                + EnumChatFormatting.GRAY
                + "· "
                + tr("gtsr.terminal.perf.caliber.settle"));
        out.add(groupHeader("gtsr.terminal.perf.group.detail"));
        String detail = ClusterTerminalClientCache.getFDetail("");
        if (!detail.isEmpty()) {
            for (String row : detail.split("\\|", -1)) {
                String line = formatDetailRow(row);
                if (line != null) out.add(line);
            }
        }
        return out;
    }

    /**
     * 单详情行本地化（性能页专用，自 d6acf00 基线逐字移植后仅保留 FLUID/LUBE/BOOST 三 case；
     * 行首令牌分发、{@code |} 分行、{@code :} 分段，畸形行返回 null 丢弃；
     * LINK/LOGI/PEAK 由链路页消费，本页与未知令牌同走 default 丢弃）。
     */
    private static String formatDetailRow(String row) {
        if (row == null || row.isEmpty()) return null;
        String[] f = row.split(":", -1);
        try {
            switch (f[0]) {
                case "FLUID": {
                    if (f.length < 3) return null;
                    // 流体注册名可含 ':'：名称取首尾定界之间，末段恒为数量
                    String fluidName = row.substring(row.indexOf(':') + 1, row.lastIndexOf(':'));
                    return EnumChatFormatting.GREEN + String.format(
                        tr("gtsr.terminal.f.detail.fluid"),
                        fluidName,
                        GtsrNumFormat.grouped(Long.parseLong(f[f.length - 1].trim())));
                }
                case "LUBE": {
                    if (f.length < 3) return null;
                    String key = "logi".equals(f[1]) ? "gtsr.terminal.f.detail.lube.logi"
                        : "gtsr.terminal.f.detail.lube.cluster";
                    return EnumChatFormatting.GREEN + String.format(tr(key), x100Text(f[2]));
                }
                case "BOOST": {
                    if (f.length < 3) return null;
                    return EnumChatFormatting.GREEN + String.format(
                        tr("gtsr.terminal.f.detail.boost"),
                        boosterLabel(Integer.parseInt(f[1].trim())),
                        x100Text(f[2]));
                }
                default:
                    return null; // 未知令牌丢弃（前向兼容：服务端新增令牌旧客户端不炸）
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** ×100 定点字段 → 两位小数文本（畸形回 "0.00"；d6acf00 基线逐字移植）。 */
    private static String x100Text(String rawX100) {
        try {
            return String.format("%.2f", Integer.parseInt(rawX100.trim()) / 100.0D);
        } catch (NumberFormatException ignored) {
            return "0.00";
        }
    }

    /** 增幅类型序号 → 本地名（越界回退并行型，与 boosterType 防御口径一致；d6acf00 基线逐字移植）。 */
    private static String boosterLabel(int typeOrdinal) {
        ClusterParams.BoosterType[] values = ClusterParams.BoosterType.values();
        ClusterParams.BoosterType type = typeOrdinal >= 0 && typeOrdinal < values.length ? values[typeOrdinal]
            : ClusterParams.BoosterType.PARALLEL;
        return tr(type.getLangKey());
    }

    // ==================== 栏 3【链路概览 + 增幅汇总 + 增幅实耗】 ====================

    /** 链路概览四行 + 增幅汇总七行 + 增幅实耗逐增幅器行（公式串随行缓存供 tooltip）。 */
    private List<String> buildChainsBoostLines() {
        List<String> out = new ArrayList<>();
        this.boostFormulas.clear();
        out.add(groupHeader("gtsr.terminal.perf.group.chains"));
        int count = 0, sum = 0, longest = 0;
        for (int len : leChainLens()) {
            count++;
            sum += len;
            if (len > longest) longest = len;
        }
        if (count == 0) {
            out.add(mutedLine("gtsr.terminal.perf.chains.count", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.steps", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.longest", "--"));
            out.add(mutedLine("gtsr.terminal.perf.chains.avg", "--"));
        } else {
            out.add(kvLine("gtsr.terminal.perf.chains.count", String.valueOf(count)));
            out.add(kvLine("gtsr.terminal.perf.chains.steps", String.valueOf(sum)));
            out.add(kvLine("gtsr.terminal.perf.chains.longest", String.valueOf(longest)));
            out.add(kvLine("gtsr.terminal.perf.chains.avg", String.format("%.1f", (double) sum / count)));
        }
        out.add(groupHeader("gtsr.terminal.perf.group.boost"));
        int[] sum8 = summary();
        out.add(kvLine("gtsr.cluster.gui.boost.sum.speed", pctText(sum8[0])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.parallel", "+" + sum8[1]));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.primary", pctText(sum8[2])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.secondary", pctText(sum8[3])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.saver", saverText(sum8[4])));
        out.add(kvLine("gtsr.cluster.gui.boost.sum.mult", multText(sum8[5])));
        out.add(EnumChatFormatting.YELLOW + String.format(tr("gtsr.cluster.gui.boost.sum.count"), sum8[6], sum8[7]));
        out.add(groupHeader("gtsr.terminal.perf.group.boostcost"));
        this.boostCostStart = out.size();
        List<int[]> costs = costRows();
        for (int i = 0; i < costs.size(); i++) {
            int[] cost = costs.get(i);
            this.boostFormulas.add(costFormulaText(cost));
            out.add(EnumChatFormatting.WHITE + "#" + (i + 1) + " " + rateText(cost[0]) + " L/s");
        }
        return out;
    }

    /**
     * KEY_LE_CHAINS 各条目 len 整数首 token（S2b 同款防御：peak/ordinal 尾巴 {@code "3.5.3"}
     * 不进 len；S2b 的 parseLeadingInt 为链路页 private，本页自持等价最小解析）。
     * 仅统计 len ≥ 1 的有效链（空链 len=0 条目不计入链数/均值）。
     */
    private static List<Integer> leChainLens() {
        List<Integer> out = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getLeChains("");
        if (encoded.isEmpty()) return out;
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length < 2) continue;
            int len = parseLeadingInt(fields[1].trim());
            if (len > 0) out.add(len);
        }
        return out;
    }

    /** 首个非数字字符前的整数前缀（兼容 {@code "1.5.3"} 型 ordinal 尾巴；无有效数字前缀回 -1）。 */
    private static int parseLeadingInt(String raw) {
        if (raw == null || raw.isEmpty()) return -1;
        int start = raw.charAt(0) == '-' ? 1 : 0;
        int end = start;
        while (end < raw.length() && raw.charAt(end) >= '0' && raw.charAt(end) <= '9') end++;
        if (end == start) return -1;
        try {
            return Integer.parseInt(raw.substring(0, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** 解析 KEY_BO_SUM 八字段 CSV（畸形回退全 0 数组；增幅页 summary 同款迁入）。 */
    private static int[] summary() {
        return parseIntCsv(ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_SUM, ""), 8);
    }

    /** 客户端解析定长 int CSV（畸形项回 0；增幅页 parseIntCsv 同款迁入）。 */
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

    /** 加成百分比文本（×100 定点 → +N% / +N.N%；增幅页 pctText 逐字迁入）。 */
    private static String pctText(int x100) {
        double pct = x100 / 100.0D * 100.0D;
        return "+" + (Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct))
            : String.format("%.1f", pct)) + "%";
    }

    /** 节汽率文本（×100 定点：截断展示 min(raw, cap)，超限红粗；增幅页 saverText 同式迁参版）。 */
    private static String saverText(int raw) {
        int capped = (int) Math.min(raw, ClusterParams.STEAM_SAVER_CAP * 100);
        boolean truncated = raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1;
        String text = "-" + capped + "%";
        return truncated ? EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD + text
            : EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + text;
    }

    /** 蒸汽乘子文本（×100 定点 → ×N.NN 白粗；增幅页 multText 逐字迁入）。 */
    private static String multText(int x100) {
        return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
            + "×"
            + String.format("%.2f", x100 / 100.0D);
    }

    /** KEY_BO_COST 全条目解析（{@code lpsX10:base[:pct:tier:type]...}；畸形条目丢弃，增幅页 costRow 同防御）。 */
    private static List<int[]> costRows() {
        List<int[]> out = new ArrayList<>();
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_BO_COST, "");
        if (encoded.isEmpty()) return out;
        for (String entry : encoded.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length < 2 || (fields.length - 2) % 3 != 0) continue;
            try {
                int[] arr = new int[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    arr[i] = Integer.parseInt(fields[i].trim());
                }
                out.add(arr);
            } catch (NumberFormatException ignored) {
                // 畸形条目丢弃
            }
        }
        return out;
    }

    /** 秒耗文本：×10 定点 → 整数值省小数、非整数保留一位小数（如 575→"57.5"、400→"40"；增幅页同款迁入）。 */
    private static String rateText(int lpsX10) {
        if (lpsX10 % 10 == 0) return String.valueOf(lpsX10 / 10);
        return String.format("%.1f", lpsX10 / 10.0D);
    }

    /**
     * S7 公式串（tooltip，代入实值）：{@code 基础 50 × (1 + 10%[速度 钢] + 5%[并行 青铜]) = 57.5 L/s}。
     * 增幅页 costFormulaText 语义移植（参数化 cost 数组；NumberFormatUtil 调用点换
     * {@link GtsrNumFormat#grouped}）；无联动加成时显示 {@code 基础 N × (1) = N L/s}；
     * 无实耗数据（基础值 ≤ 0）返回空串不出 tooltip。
     */
    private static String costFormulaText(int[] cost) {
        if (cost == null || cost.length < 2 || cost[1] <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.WHITE)
            .append(tr("gtsr.cluster.gui.boost.cost.base"))
            .append(' ')
            .append(GtsrNumFormat.grouped(cost[1]))
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

    /** 施加方类型短标签（SPEED/PARALLEL；越界回退并行；增幅页同款迁入）。 */
    private static String sourceLabel(int typeOrdinal) {
        if (typeOrdinal == ClusterParams.BoosterType.SPEED.ordinal()) {
            return tr("gtsr.cluster.gui.boost.cost.src.speed");
        }
        return tr("gtsr.cluster.gui.boost.cost.src.parallel");
    }

    /** 施加方 tier 标签（复用集群层级 lang key；越界回 "--"；增幅页同款迁入）。 */
    private static String tierLabel(int tier) {
        if (tier < 0 || tier >= ClusterParams.TIER_COUNT) return "--";
        return tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey());
    }

    // ==================== 行文本工具 ====================

    /** 组标题行（金粗 0.7f）。 */
    private static String groupHeader(String key) {
        return EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(key);
    }

    /** 数据行（黄标签 = 绿值，链路页 perfLines 同式）。 */
    private static String kvLine(String labelKey, String value) {
        return EnumChatFormatting.YELLOW + tr(labelKey) + " = " + EnumChatFormatting.GREEN + value;
    }

    /** 数据行（黄标签 = 灰值，无数据哨兵 "--" 用）。 */
    private static String mutedLine(String labelKey, String value) {
        return EnumChatFormatting.YELLOW + tr(labelKey) + " = " + EnumChatFormatting.GRAY + value;
    }

    // ==================== 输入 ====================

    /** 只读页：内容区内点击一律消费防穿透（无按钮）。 */
    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        return mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H;
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        // 滚轮路由 = 鼠标所落栏的 list.handleWheel（列表自判区内命中并消费；链路页现行转发范式）
        for (GtsrGuiList list : this.colLists) {
            if (list != null && list.handleWheel(mx, my, dir)) return;
        }
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int button) {
        for (GtsrGuiList list : this.colLists) {
            if (list != null) list.mouseClickMove(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (GtsrGuiList list : this.colLists) {
            if (list != null) list.mouseReleased(mouseX, mouseY, button);
        }
    }

    // ==================== 共用工具 ====================

    private net.minecraft.client.gui.FontRenderer font() {
        return this.host.font();
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
