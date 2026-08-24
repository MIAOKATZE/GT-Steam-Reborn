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
 * 特殊流体供给（<b>只读检测</b>，非 Toggle）/增益/状态；缺流行<b>红底</b>（#3A1E1E）+
 * 「缺 X，增益失效」；正常行深底 #26262B；</li>
 * <li>空状态区分：无模块（空表提示+引导）/未关联（行 flags bit0）/未运行（集群停机提示行）/
 * 缺流体（行红底）；</li>
 * <li>2×3 汇总卡（y 154..216）：速度/并行/主产物/副产物/节汽≤48%（超限红字标注截断）/
 * 蒸汽乘子（附注 生效 N·失效 N）——全部服务端 {@code KEY_BO_SUM} 真值（×100 定点解码）；</li>
 * <li>规则两行（y 220..256）：叠加规则中性灰 + 当前异常状态色（绿全生效/红缺流体/灰无模块）。</li>
 * </ul>
 *
 * <p>
 * 数据流：{@code KEY_BO_STRUCT}（结构字段：类型/tier/段/flags——结构 revision 界，变化重建行）与
 * {@code KEY_BO_LIVE}（tank 存量+可用性——20t 周期）分离；行内容随 STRUCT 重建，余量文字
 * IKey.dynamic 直读 LIVE 缓存。本页无 C2S 动作。
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
     * 单模块行（六列与表头对齐；缺流行红底+状态「缺 X，增益失效」，正常行深底+绿「生效」）。
     * 供给列只读动态直读 KEY_BO_LIVE（20t 周期真值，非可操作 Toggle）。
     */
    private IWidget buildBoosterRow(int[] struct, int rowIndex) {
        int typeOrdinal = struct[0], tier = struct[1], segment = struct[2], flags = struct[3];
        ClusterParams.BoosterType type = boosterType(typeOrdinal);
        boolean connected = (flags & 0x01) != 0;
        boolean formed = (flags & 0x02) != 0;
        int[] live = liveRow(rowIndex);

        Flow row = Flow.row()
            .widthRel(1f)
            .height(ROW_H)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .background(live[1] == 0 ? ROW_BG_FAIL : ROW_BG);
        String base = live[1] == 0 ? EnumChatFormatting.RED.toString() : EnumChatFormatting.WHITE.toString();
        // 模块名 + @段N
        String moduleText = base.toString() + EnumChatFormatting.BOLD
            + tr(type.getLangKey())
            + EnumChatFormatting.GRAY
            + (segment >= 0 ? String.format(tr("gtsr.gui.cluster.editor.segment"), segment) : "");
        row.child(
            IKey.str(moduleText)
                .asWidget()
                .width(COLS[0])
                .scale(0.65f));
        // 等级
        String tierText = tier >= 0 ? base + tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey())
            : base + "--";
        row.child(
            IKey.str(tierText)
                .asWidget()
                .width(COLS[1])
                .scale(0.65f));
        // 特殊流体
        row.child(
            IKey.str(base + tr(type.getFluidLangKey()))
                .asWidget()
                .width(COLS[2])
                .scale(0.65f));
        // 供给（只读检测）：余量 L + 可用/不足
        row.child(IKey.dynamic(() -> {
            int[] now = liveRow(rowIndex);
            String state = now[1] != 0 ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.boost.supply.ok")
                : EnumChatFormatting.RED + tr("gtsr.cluster.gui.boost.supply.short");
            return state + EnumChatFormatting.GRAY + " " + NumberFormatUtil.formatNumber(now[0]) + " L";
        })
            .asWidget()
            .width(COLS[3])
            .scale(0.65f));
        // 增益
        String gain = tier >= 0 && formed && connected ? formatGain(type, tier) : "--";
        row.child(
            IKey.str(base + gain)
                .asWidget()
                .width(COLS[4])
                .scale(0.65f));
        // 状态：缺流红「缺 X，增益失效」/ 未关联/未成型 / 绿「生效」
        String status;
        if (!connected) {
            status = EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unlinked");
        } else if (!formed) {
            status = EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.boost.state.unformed");
        } else if (live[1] == 0) {
            status = EnumChatFormatting.RED
                + String.format(tr("gtsr.cluster.gui.boost.fail"), tr(type.getFluidLangKey()));
        } else {
            status = EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.boost.active");
        }
        row.child(
            IKey.str(status)
                .asWidget()
                .width(COLS[5])
                .scale(0.65f));
        return row;
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
