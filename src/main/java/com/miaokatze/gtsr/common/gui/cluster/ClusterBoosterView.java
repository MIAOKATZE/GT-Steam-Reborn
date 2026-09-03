package com.miaokatze.gtsr.common.gui.cluster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.gui.widget.ScrollKeepingListWidget;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 集群三视图·页 2「增幅」（批2 E6 重写；数据全部经 {@link ClusterGuiSync} §4.3.5 通道）。
 *
 * <p>
 * 布局（页内绝对定位，582×258）：
 * <ul>
 * <li>六列表（y 10..150，滚动区 = {@link ScrollKeepingListWidget}）：模块/等级/特殊流体/
 * 特殊流体供给（<b>只读检测</b>，非 Toggle；S7 起附实际秒耗数值「N L · N L/s」）/增益/状态；
 * 缺流行<b>红底</b>（#3A1E1E）+「缺 X，增益失效」；正常行深底 #26262B；</li>
 * <li>S7 实耗口径（KEY_BO_COST，20t）：供给列秒耗 = 基础五表值 ×(1+Σ速度/并行联动加成) 的
 * 服务端真值（×10 定点）；行悬浮 tooltip 显示代入实值的公式串（如
 * 「基础 50 × (1 + 10%[速度 钛] + 5%[并行 青铜]) = 57.5 L/s」，客户端本地化拼装）。</li>
 * <li>空状态区分：无模块（空表提示+引导）/未关联（行 flags bit0）/未运行（集群停机提示行）/
 * 缺流体（行红底）；</li>
 * <li>2×3 汇总卡（y 154..216）：速度/并行/主产物/副产物/节汽≤48%（超限红字标注截断）/
 * 蒸汽乘子（附注 生效 N·失效 N）——全部服务端 {@code KEY_BO_SUM} 真值（×100 定点解码）；</li>
 * <li>规则两行（y 220..256）：叠加规则中性灰 + 当前异常状态色（绿全生效/红缺流体/灰无模块）。</li>
 * </ul>
 *
 * <p>
 * 数据流：{@code KEY_BO_STRUCT}（结构字段：类型/tier/段/flags——结构 revision 界，变化重建行骨架）、
 * {@code KEY_BO_LIVE}（tank 存量+可用性——20t 周期）与 {@code KEY_BO_COST}（S7 实耗+联动加成
 * 三元组——20t 周期）分离；行内一切 live 相关内容（行底色/行基色/状态列/余量/秒耗）一律
 * onUpdateListener 或 IKey.dynamic 每 tick 重读缓存，缺流→补液无需重开 GUI 即恢复，
 * 首包 STRUCT 先于 LIVE 应用的暂态红底由后续 tick 自愈。本页无 C2S 动作。
 */
public final class ClusterBoosterView {

    /** 表标题与表头偏移。 */
    private static final int HEADER_DY = 10;
    private static final int HEADER_H = 9;
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
    /** 行底：深底 #26262B / 缺流红底 #3A1E1E（预分配）。 */
    private static final Rectangle ROW_BG = new Rectangle().color(0xFF26262B);
    private static final Rectangle ROW_BG_FAIL = new Rectangle().color(0xFF3A1E1E);
    /** 卡底/规则底。 */
    private static final Rectangle CARD_BG = new Rectangle().color(0xFF26262B);

    private final PanelSyncManager sync;
    /** 列表实例（常驻；滚动位置随实例持续）。 */
    private ListWidget<IWidget, ?> listWidget;
    private int listScrollValue;

    private ClusterBoosterView(PanelSyncManager sync) {
        this.sync = sync;
    }

    /** 构建增幅页（三视图契约入口，双端执行；panel/actions/machine/contentX/Y 为契约保留参数，本页只读）。 */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {
        ClusterBoosterView view = new ClusterBoosterView(sync);

        // 结构字段变化 → 重建行（行骨架 = 类型/tier/段/flags；tank 余量经 LIVE 动态直读）
        SyncHandler<?> structHandler = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_BO_STRUCT);
        if (structHandler instanceof StringSyncValue structSync) {
            structSync.setChangeListener(view::rebuildRows);
        }

        ParentWidget<?> page = new ParentWidget<>().size(contentW, contentH);
        page.child(
            IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.boost.title"))
                .asWidget()
                .pos(0, 0)
                .scale(0.7f));
        page.child(buildTableHeader());
        view.listWidget = new ScrollKeepingListWidget(
            () -> view.listScrollValue,
            value -> view.listScrollValue = value);
        view.listWidget.pos(0, LIST_DY)
            .size(contentW, LIST_H);
        page.child(view.listWidget);
        page.child(
            IKey.dynamic(view::hintText)
                .asWidget()
                .pos(0, HINT_DY)
                .scale(0.6f));
        buildSummary(page, view);
        buildRules(page, view);
        view.rebuildRows();
        paged.addPage(page);
    }

    // ==================== 六列表 ====================

    private static IWidget buildTableHeader() {
        Flow row = Flow.row()
            .pos(0, HEADER_DY)
            .height(HEADER_H)
            .childPadding(2);
        row.child(headerLabel("gtsr.cluster.gui.boost.col.module", COLS[0]));
        row.child(headerLabel("gtsr.cluster.gui.boost.col.tier", COLS[1]));
        row.child(headerLabel("gtsr.cluster.gui.boost.col.fluid", COLS[2]));
        row.child(headerLabel("gtsr.cluster.gui.boost.col.supply", COLS[3]));
        row.child(headerLabel("gtsr.cluster.gui.boost.col.gain", COLS[4]));
        row.child(headerLabel("gtsr.cluster.gui.boost.col.status", COLS[5]));
        return row;
    }

    private static IWidget headerLabel(String key, int width) {
        return IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(key))
            .asWidget()
            .width(width)
            .scale(0.6f);
    }

    /** 重建表行（KEY_BO_STRUCT 变化时；空表显示空状态提示行，实例常驻不回顶）。 */
    private void rebuildRows() {
        if (listWidget == null) return;
        listWidget.removeAll();
        List<int[]> rows = structRows();
        if (rows.isEmpty()) {
            listWidget.child(
                IKey.str(EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.empty"))
                    .asWidget()
                    .scale(0.75f));
            listWidget.child(
                IKey.str(EnumChatFormatting.DARK_GRAY + tr("gtsr.cluster.gui.boost.empty.hint"))
                    .asWidget()
                    .scale(0.65f));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            listWidget.child(buildBoosterRow(rows.get(i), i));
        }
    }

    /**
     * 单模块行（六列与表头对齐；行骨架 = 类型/tier/段/flags，随 KEY_BO_STRUCT 重建）。
     * 缺流表现（行红底 + 行基色红 + 状态「缺 X，增益失效」）经 onUpdateListener/IKey.dynamic
     * 每 tick 重读 KEY_BO_LIVE，补液后无需重开 GUI 即恢复；正常行深底+绿「生效」。
     * 供给列只读动态直读 KEY_BO_LIVE（20t 周期真值，非可操作 Toggle）。
     */
    private IWidget buildBoosterRow(int[] struct, int rowIndex) {
        int typeOrdinal = struct[0], tier = struct[1], segment = struct[2], flags = struct[3];
        ClusterParams.BoosterType type = boosterType(typeOrdinal);
        boolean connected = (flags & 0x01) != 0;
        boolean formed = (flags & 0x02) != 0;
        // 行内静态文本构建期捕获一次；颜色前缀与缺流状态每帧重读 KEY_BO_LIVE 可用位
        String moduleText = EnumChatFormatting.BOLD + tr(type.getLangKey())
            + EnumChatFormatting.GRAY
            + (segment >= 0 ? String.format(tr("gtsr.gui.cluster.editor.segment"), segment) : "");
        String tierText = tier >= 0 ? tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey())
            : "--";
        String fluidText = tr(type.getFluidLangKey());
        String gainText = tier >= 0 && formed && connected ? formatGain(type, tier) : "--";

        Flow row = Flow.row()
            .widthRel(1f)
            .height(ROW_H)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(ROW_BG);
        // 缺流红底每 tick 重评（首包 STRUCT 先于 LIVE 应用的暂态由后续 tick 自愈）
        row.onUpdateListener(w -> w.background(liveRow(rowIndex)[1] == 0 ? ROW_BG_FAIL : ROW_BG), false);
        // 模块名 + @段N
        row.child(
            IKey.dynamic(() -> baseColor(rowIndex) + moduleText)
                .asWidget()
                .width(COLS[0])
                .scale(0.65f));
        // 等级
        row.child(
            IKey.dynamic(() -> baseColor(rowIndex) + tierText)
                .asWidget()
                .width(COLS[1])
                .scale(0.65f));
        // 特殊流体
        row.child(
            IKey.dynamic(() -> baseColor(rowIndex) + fluidText)
                .asWidget()
                .width(COLS[2])
                .scale(0.65f));
        // 供给（只读检测）：余量 L + S7 实际秒耗（联动加成后口径）；悬浮 tooltip 显示代入实值公式串
        row.child(IKey.dynamic(() -> {
            int[] now = liveRow(rowIndex);
            String state = now[1] != 0 ? EnumChatFormatting.GREEN.toString() : EnumChatFormatting.RED.toString();
            return state + NumberFormatUtil.formatNumber(
                now[0]) + " L " + EnumChatFormatting.GRAY + "· " + rateText(costLpsX10(rowIndex)) + " L/s";
        })
            .asWidget()
            .width(COLS[3])
            .scale(0.65f)
            .tooltipBuilder(t -> {
                String formula = costFormulaText(rowIndex);
                if (!formula.isEmpty()) t.addLine(IKey.str(formula));
            }));
        // 增益
        row.child(
            IKey.dynamic(() -> baseColor(rowIndex) + gainText)
                .asWidget()
                .width(COLS[4])
                .scale(0.65f));
        // 状态：缺流红「缺 X，增益失效」/ 未关联/未成型 / 绿「生效」（可用位每帧重读）
        row.child(IKey.dynamic(() -> {
            if (!connected) {
                return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unlinked");
            }
            if (!formed) {
                return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unformed");
            }
            if (liveRow(rowIndex)[1] == 0) {
                return EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.boost.fail"), fluidText);
            }
            return EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.boost.active");
        })
            .asWidget()
            .width(COLS[5])
            .scale(0.65f));
        return row;
    }

    /** 行基色：缺流红 / 正常白（每帧重读 KEY_BO_LIVE 可用位，与行底色同源）。 */
    private String baseColor(int rowIndex) {
        return liveRow(rowIndex)[1] == 0 ? EnumChatFormatting.RED.toString() : EnumChatFormatting.WHITE.toString();
    }

    /** 增益格式：并行 +N（台）；百分比类 +N%。 */
    private static String formatGain(ClusterParams.BoosterType type, int tier) {
        int value = type.getBoosterValue(tier);
        return type == ClusterParams.BoosterType.PARALLEL ? "+" + value : "+" + value + "%";
    }

    /** 空状态/提示行：无模块 / 集群未运行 / 缺流体 N。 */
    private String hintText() {
        List<int[]> rows = structRows();
        if (rows.isEmpty()) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.empty.hint");
        }
        if (!ClusterGuiSync.boolOf(sync, ClusterGuiSync.KEY_ENABLED, false)) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.empty.off");
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

    private static void buildSummary(ParentWidget<?> page, ClusterBoosterView view) {
        // 卡 1：速度
        page.child(summaryCell("gtsr.cluster.gui.boost.sum.speed", () -> pctText(summaryOf(view, 0)), 0, 0, view));
        // 卡 2：并行
        page.child(
            summaryCell(
                "gtsr.cluster.gui.boost.sum.parallel",
                () -> "+" + summaryOf(view, 1),
                CELL_W + CELL_GAP,
                0,
                view));
        // 卡 3：主产物
        page.child(
            summaryCell(
                "gtsr.cluster.gui.boost.sum.primary",
                () -> pctText(summaryOf(view, 2)),
                (CELL_W + CELL_GAP) * 2,
                0,
                view));
        // 卡 4：副产物
        page.child(
            summaryCell(
                "gtsr.cluster.gui.boost.sum.secondary",
                () -> pctText(summaryOf(view, 3)),
                0,
                CELL_H + CELL_GAP,
                view));
        // 卡 5：节汽（≤48% 截断红字）
        page.child(
            summaryCell(
                "gtsr.cluster.gui.boost.sum.saver",
                () -> saverText(view),
                CELL_W + CELL_GAP,
                CELL_H + CELL_GAP,
                view));
        // 卡 6：蒸汽乘子 + 生效/失效 N
        page.child(
            summaryCell(
                "gtsr.cluster.gui.boost.sum.mult",
                () -> multText(view),
                (CELL_W + CELL_GAP) * 2,
                CELL_H + CELL_GAP,
                view));
    }

    /** 汇总卡：深底 + 黄标签（0.55）+ 白粗值（0.8，IKey.dynamic 直读同步缓存）。 */
    private static IWidget summaryCell(String labelKey, java.util.function.Supplier<String> value, int x, int y,
        ClusterBoosterView view) {
        return new ParentWidget<>().pos(x, SUMMARY_DY + y)
            .size(CELL_W, CELL_H)
            .background(CARD_BG)
            .child(
                IKey.str(EnumChatFormatting.YELLOW + tr(labelKey))
                    .asWidget()
                    .pos(3, 1)
                    .scale(0.6f))
            .child(
                IKey.dynamic(value::get)
                    .asWidget()
                    .pos(3, 10)
                    .scale(0.8f))
            .child(
                IKey.dynamic(() -> noteText(view, labelKey))
                    .asWidget()
                    .pos(3, 21)
                    .scale(0.55f));
    }

    /** 卡注：蒸汽乘子卡 = 生效 N·失效 N；节汽卡超限 = 截断警示；其余 = 静态说明。 */
    private static String noteText(ClusterBoosterView view, String labelKey) {
        if (labelKey.endsWith("mult")) {
            int[] sum = view.summary();
            return EnumChatFormatting.GRAY
                + String.format(tr("gtsr.cluster.gui.boost.sum.count"), at(sum, 6), at(sum, 7));
        }
        if (labelKey.endsWith("saver")) {
            int raw = at(view.summary(), 4);
            if (raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1) {
                return EnumChatFormatting.RED + tr("gtsr.cluster.gui.boost.saver_capped");
            }
            return EnumChatFormatting.GRAY + "≤ 48%";
        }
        if (labelKey.endsWith("primary")) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.note.primary");
        }
        return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.note.additive");
    }

    private static String pctText(int x100) {
        double pct = x100 / 100.0D * 100.0D;
        return "+" + (Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct))
            : String.format("%.1f", pct)) + "%";
    }

    private static String saverText(ClusterBoosterView view) {
        // x100 定点：8 = 8%；截断展示 min(raw, 48)，超限红字
        int raw = at(view.summary(), 4);
        int capped = (int) Math.min(raw, ClusterParams.STEAM_SAVER_CAP * 100);
        boolean truncated = raw > ClusterParams.STEAM_SAVER_CAP * 100 + 1;
        String text = "-" + capped + "%";
        return truncated ? EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD + text
            : EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + text;
    }

    private static String multText(ClusterBoosterView view) {
        int x100 = at(view.summary(), 5);
        return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
            + "×"
            + String.format("%.2f", x100 / 100.0D);
    }

    /** 规则两行：叠加规则中性灰 + 当前异常状态色。 */
    private static void buildRules(ParentWidget<?> page, ClusterBoosterView view) {
        page.child(
            IKey.str(EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.rule.stack"))
                .asWidget()
                .pos(0, RULE_DY)
                .scale(0.6f));
        page.child(
            IKey.dynamic(() -> ruleStateText(view))
                .asWidget()
                .pos(0, RULE_DY + 10)
                .scale(0.6f));
    }

    private static String ruleStateText(ClusterBoosterView view) {
        List<int[]> rows = view.structRows();
        if (rows.isEmpty()) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.rule.none");
        }
        int failed = at(view.summary(), 7);
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
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_BO_STRUCT, "");
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
    private int[] liveRow(int index) {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_BO_LIVE, "");
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
    private int[] costRow(int index) {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_BO_COST, "");
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
    private int costLpsX10(int index) {
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
    private String costFormulaText(int rowIndex) {
        int[] cost = costRow(rowIndex);
        if (cost == null || cost.length < 2 || cost[1] <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(EnumChatFormatting.GRAY)
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
        if (typeOrdinal == ClusterParams.BoosterType.SPEED.ordinal()) {
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

    /** 解析 KEY_BO_SUM 八字段 CSV（畸形回退全 0 数组）。 */
    private int[] summary() {
        return ClusterGuiSync.parseIntCsv(ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_BO_SUM, ""), 8);
    }

    private static int at(int[] array, int index) {
        return index >= 0 && index < array.length ? array[index] : 0;
    }

    /** 服务端真值实时读（汇总卡 dynamic 直读；value supplier 每帧重解析小 CSV，开销可忽略）。 */
    private static int summaryOf(ClusterBoosterView view, int index) {
        return at(view.summary(), index);
    }

    /** 序号安全取增幅类型（越界回退 PARALLEL）。 */
    private static ClusterParams.BoosterType boosterType(int ordinal) {
        ClusterParams.BoosterType[] values = ClusterParams.BoosterType.values();
        if (ordinal < 0 || ordinal >= values.length) return ClusterParams.BoosterType.PARALLEL;
        return values[ordinal];
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
