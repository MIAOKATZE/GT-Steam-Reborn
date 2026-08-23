package com.miaokatze.gtsr.common.gui.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.machine.cluster.BoosterState;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * 集群三视图页 3「增幅面板」（MUI2，对齐 HTML 规格 v2.5 §增幅面板）。
 *
 * <p>
 * 由主 GUI（{@code MTESteamMineralLogisticsClusterGui}）按并行契约调用
 * {@link #build}：本视图自建一个页容器（ParentWidget，定位于 contentX/Y × contentW/H），
 * 经 {@link PagedWidget#addPage} 挂为第 3 页，全部子元素坐标均相对该页容器。
 *
 * <p>
 * 布局（页内自上而下，两段 + 注脚）：
 * <ul>
 * <li>增幅表：标题行（含五种锁定流体本地化名）→ 6 列表头（模块/等级/特殊流体/当前持有/增益/状态）→
 * {@link ListWidget} 滚动行区。每模块一行：模块 = 类型名 + 灰字「@段N」；等级 = {@link ClusterParams.ClusterTier}
 * 本地名（tier&lt;0 显「—」）；特殊流体 = {@link ClusterParams.BoosterType#getFluidLangKey()} 本地名；
 * 当前持有 = tank 余量 L（{@link IKey#dynamic} 实时读同步列表）；增益 = 按类型格式（并行 +N / 百分比 +N%）；
 * 状态 = 流体可用→绿「✔ 生效」，否则整行暗红「✖ 增益失效（缺 XX 流体）」。空表显示提示行。</li>
 * <li>汇总区：6 格 2 行 3 列（深色 DISPLAY 底：黄标签 + 白粗值 + 灰注释）——速度增益 Σ / 并行增益 Σ /
 * 主产物增益（取最高仅 1）/ 副产物增益 Σ / 蒸汽节约 Σ（上限 48%，超限红字标注截断）/ 蒸汽惩罚 ×N.NN。
 * 无增幅模块时照常显示（全 0 / ×1.00）。</li>
 * <li>规则注释：深色底 3 行小字灰（工作模块复数只减 link 时间不叠主产物 / 主产物增幅仅 1 个生效 /
 * 缺特殊流体→增益失效且不计惩罚乘子，节汽上限 48%）。</li>
 * </ul>
 *
 * <p>
 * 同步设计（S2C，本视图无 C2S 动作）：
 * <ul>
 * <li>"cl.boosters"：{@link GenericListSyncHandler}，服务端 supplier 读
 * {@code machine.getTopology().getBoosterUnits()} 逐模块快照；每模块序列化为一条竖线分隔串
 * {@code typeOrdinal|tier|segmentIndex|amount|available}（比规格串多带 segmentIndex——「模块」列
 * 「@段N」后缀所需，照 MTECacheHubStatusGui nodeListSync 范式注册读写/相等判定）；
 * changeListener 重建行（常驻 ListWidget 实例只重建行内容，滚动位置不回顶，照
 * MTECrustMatterAggregatorConfigGui 范式）。</li>
 * <li>"cl.b.par|cl.b.speed|cl.b.prim|cl.b.sec|cl.b.saver|cl.b.pen"：独立
 * {@link IntSyncValue}/{@link DoubleSyncValue}，服务端 supplier 现算
 * {@link BoosterState#aggregate}（纯计算小列表，开销可忽略）；节汽同步未截断原值，客户端显示
 * min(raw, {@link ClusterParams#STEAM_SAVER_CAP}) 并在超限时红字标注。</li>
 * </ul>
 *
 * <p>
 * 文案说明：模块类型/锁定流体/层级名走既有 lang key（gtsr.gui.cluster.*）；本视图局部文案
 * （列头/汇总标签/注释/提示）按任务规格原文硬编码中文，后续如需国际化可平移为 lang key。
 * {@code actions} 为三视图统一契约参数，本页无按钮动作，仅保留占位。
 */
public final class ClusterBoosterView {

    /** 紧凑窗口列表区标题行高（含下缘留白）。 */
    private static final int LIST_TITLE_H = 11;
    /** 表头行高。 */
    private static final int HEADER_H = 11;
    /** 数据行高。 */
    private static final int ROW_H = 14;
    /** 汇总区标题行高。 */
    private static final int SUMMARY_TITLE_H = 10;
    /** 汇总格高（标签 + 值 + 注释三行）。 */
    private static final int CELL_H = 27;
    /** 汇总格水平间距。 */
    private static final int CELL_GAP = 3;
    /** 注释行行高。 */
    private static final int NOTE_LINE_H = 8;
    /** 段间距。 */
    private static final int SECTION_GAP = 3;
    /** Flow 行内子元素间距（表头与数据行共用，保证逐列对齐）。 */
    private static final int CHILD_PADDING = 2;
    /** 固定列宽：等级 / 特殊流体 / 当前持有 / 增益。 */
    private static final int COL_TIER = 34;
    private static final int COL_FLUID = 44;
    private static final int COL_AMOUNT = 50;
    private static final int COL_GAIN = 40;

    /** 工具类禁止实例化。 */
    private ClusterBoosterView() {}

    /**
     * 构建增幅面板页（三视图并行契约入口，双端执行）。
     *
     * @param panel    主面板（本视图内容全部挂在页容器上，不直接向面板加子元素，契约保留参数）
     * @param sync     面板同步管理器（注册 cl.boosters 列表与 cl.b.* 六个汇总值）
     * @param actions  面板级 C2S 动作处理器（本页无动作，契约保留参数）
     * @param machine  集群总控（服务端数据源：拓扑增幅模块 + BoosterState 聚合）
     * @param paged    主 GUI 的三页容器（本页经 addPage 挂为末页）
     * @param contentX 页内容区左上角 X（相对 paged/面板坐标系）
     * @param contentY 页内容区左上角 Y
     * @param contentW 页内容区宽
     * @param contentH 页内容区高
     */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {

        // ==================== 数据同步（S2C） ====================

        // 增幅表行数据：服务端每 tick 检测变化（照 nodeListSync 范式），客户端 changeListener 重建行
        GenericListSyncHandler<BoosterRowInfo> boosterListSync = new GenericListSyncHandler<>(
            () -> collectRows(machine),
            null,
            ClusterBoosterView::readRow,
            ClusterBoosterView::writeRow,
            ClusterBoosterView::rowsEqual,
            null);

        // 汇总六值：服务端 supplier 现算聚合快照（纯计算；六个值各自独立同步键，客户端 IKey.dynamic 直读）
        Supplier<BoosterState> boosterState = () -> BoosterState.aggregate(
            machine.getTopology()
                .getBoosterUnits());
        IntSyncValue parallelSync = new IntSyncValue(
            () -> boosterState.get()
                .getParallelBonus());
        DoubleSyncValue speedSync = new DoubleSyncValue(
            () -> boosterState.get()
                .getSpeedBonus());
        DoubleSyncValue primarySync = new DoubleSyncValue(
            () -> boosterState.get()
                .getPrimaryBonus());
        DoubleSyncValue secondarySync = new DoubleSyncValue(
            () -> boosterState.get()
                .getSecondaryBonus());
        // 节汽同步未截断原值（Σ 口径），截断展示在客户端做（超限红字标注）
        DoubleSyncValue saverSync = new DoubleSyncValue(
            () -> boosterState.get()
                .getSaverBonusRaw());
        DoubleSyncValue penaltySync = new DoubleSyncValue(
            () -> boosterState.get()
                .getPenaltyProduct());

        sync.syncValue("cl.boosters", boosterListSync);
        sync.syncValue("cl.b.par", parallelSync);
        sync.syncValue("cl.b.speed", speedSync);
        sync.syncValue("cl.b.prim", primarySync);
        sync.syncValue("cl.b.sec", secondarySync);
        sync.syncValue("cl.b.saver", saverSync);
        sync.syncValue("cl.b.pen", penaltySync);

        // ==================== 垂直布局（页容器本地坐标，自上而下） ====================

        // 紧凑六列表：模块与状态列按剩余空间分配，表头与数据行共用同一列宽。
        int colModule = Math.max(100, contentW / 4);
        int colStatus = Math
            .max(96, contentW - colModule - COL_TIER - COL_FLUID - COL_AMOUNT - COL_GAIN - CHILD_PADDING * 5);
        int[] cols = { colModule, COL_TIER, COL_FLUID, COL_AMOUNT, COL_GAIN, colStatus };

        int headerY = LIST_TITLE_H + 1;
        int listY = headerY + HEADER_H;
        int summaryBlockH = SUMMARY_TITLE_H + CELL_H * 2 + CELL_GAP;
        int notesBlockH = NOTE_LINE_H * 3;
        int listH = Math.max(48, contentH - listY - summaryBlockH - notesBlockH - SECTION_GAP * 2);
        int summaryTitleY = listY + listH + SECTION_GAP;
        int cellsY = summaryTitleY + SUMMARY_TITLE_H;
        int cellsRow1Y = cellsY + CELL_H + CELL_GAP;
        int notesY = cellsRow1Y + CELL_H + SECTION_GAP;
        int cellW = (contentW - CELL_GAP * 2) / 3;

        // ==================== 页容器与增幅表 ====================

        ParentWidget<?> page = new ParentWidget<>().size(contentW, contentH);

        // 标题：增幅模块清单（五种锁定流体本地化名随类型枚举序拼接）
        page.child(
            IKey.str(
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                    + String.format(tr("gtsr.gui.cluster.booster.title"), fluidNameList()))
                .asWidget()
                .pos(0, 0)
                .scale(0.75f));
        page.child(buildTableHeader(cols, headerY));

        // 常驻 ListWidget 实例：数据变化仅重建行内容，滚动位置随实例持续（不回顶）
        ListWidget<IWidget, ?> listWidget = new ListWidget<>();
        listWidget.pos(0, listY)
            .size(contentW, listH);
        page.child(listWidget);

        // ==================== 汇总区（6 格 2 行 3 列） ====================

        page.child(
            IKey.str(
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                    + tr("gtsr.gui.cluster.booster.summary_title"))
                .asWidget()
                .pos(0, summaryTitleY)
                .scale(0.75f));
        int cellX0 = 0;
        int cellX1 = cellW + CELL_GAP;
        int cellX2 = (cellW + CELL_GAP) * 2;
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.speed"),
                () -> whiteBold("+" + formatPct(speedSync.getDoubleValue()) + "%"),
                () -> grayNote(tr("gtsr.gui.cluster.booster.note.speed")),
                cellX0,
                cellsY,
                cellW));
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.parallel"),
                () -> whiteBold("+" + parallelSync.getIntValue()),
                () -> grayNote(tr("gtsr.gui.cluster.booster.note.parallel")),
                cellX1,
                cellsY,
                cellW));
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.primary"),
                () -> whiteBold("+" + formatPct(primarySync.getDoubleValue()) + "%"),
                () -> grayNote(tr("gtsr.gui.cluster.booster.note.primary")),
                cellX2,
                cellsY,
                cellW));
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.secondary"),
                () -> whiteBold("+" + formatPct(secondarySync.getDoubleValue()) + "%"),
                () -> grayNote(tr("gtsr.gui.cluster.booster.note.additive")),
                cellX0,
                cellsRow1Y,
                cellW));
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.saver"),
                () -> formatSaverValue(saverSync.getDoubleValue()),
                () -> formatSaverNote(saverSync.getDoubleValue()),
                cellX1,
                cellsRow1Y,
                cellW));
        page.child(
            buildSummaryCell(
                tr("gtsr.gui.cluster.booster.summary.penalty"),
                () -> whiteBold("×" + String.format("%.2f", penaltySync.getDoubleValue())),
                () -> grayNote(tr("gtsr.gui.cluster.booster.note.penalty")),
                cellX2,
                cellsRow1Y,
                cellW));

        // ==================== 规则注释（深色底 3 行小字灰） ====================

        page.child(
            new ParentWidget<>().pos(0, notesY)
                .size(contentW, notesBlockH)
                .background(GuiTextures.DISPLAY)
                .child(
                    IKey.str(EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.booster.rule1"))
                        .asWidget()
                        .pos(3, 1)
                        .scale(0.55f))
                .child(
                    IKey.str(EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.booster.rule2"))
                        .asWidget()
                        .pos(3, 9)
                        .scale(0.55f))
                .child(
                    IKey.str(EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.booster.rule3"))
                        .asWidget()
                        .pos(3, 17)
                        .scale(0.55f)));

        // ==================== 行重建接线与挂页 ====================

        // 数据变化（服务端推送）→ 重建行；初始一次（sync handler 尚未初始化时为空表提示，首同步后再刷新）
        boosterListSync.setChangeListener(() -> refreshRows(listWidget, boosterListSync, cols));
        refreshRows(listWidget, boosterListSync, cols);

        paged.addPage(page);
    }

    // ==================== 增幅表 ====================

    /** 表头行：6 列橙字加粗小字，列宽与数据行逐列对齐。 */
    private static IWidget buildTableHeader(int[] cols, int y) {
        Flow row = Flow.row()
            .pos(0, y)
            .height(HEADER_H)
            .childPadding(CHILD_PADDING);
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.module"), cols[0]));
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.tier"), cols[1]));
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.fluid"), cols[2]));
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.supply"), cols[3]));
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.gain"), cols[4]));
        row.child(headerLabel(tr("gtsr.gui.cluster.booster.header.status"), cols[5]));
        return row;
    }

    /** 表头单元格：橙色加粗小字，固定列宽。 */
    private static IWidget headerLabel(String text, int width) {
        return IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + text)
            .asWidget()
            .width(width)
            .scale(0.7f);
    }

    /**
     * 重建增幅表行内容（数据变化时调用；列表实例常驻，滚动位置保持不变）。
     * 空表显示提示行；行序即服务端列表序（行内动态取值按行号回查同一列表，二者保持一致）。
     */
    private static void refreshRows(ListWidget<IWidget, ?> list, GenericListSyncHandler<BoosterRowInfo> listSync,
        int[] cols) {
        list.removeAll();
        List<BoosterRowInfo> rows = listSync.getValue();
        if (rows == null || rows.isEmpty()) {
            list.child(
                IKey.str(EnumChatFormatting.DARK_GRAY + tr("gtsr.gui.cluster.booster.none"))
                    .asWidget()
                    .scale(0.9f));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            list.child(buildBoosterRow(rows.get(i), i, listSync, cols));
        }
    }

    /**
     * 构建单个增幅模块行：六列与表头逐列对齐；缺流体模块整行暗红（含状态列
     * 「✖ 增益失效（缺 XX 流体）」），其余行黑字。「当前持有」列 IKey.dynamic 按行号
     * 回查同步列表实时余量（两次重建之间 tank 抽取也能即时反映）。
     */
    private static IWidget buildBoosterRow(BoosterRowInfo info, int rowIndex,
        GenericListSyncHandler<BoosterRowInfo> listSync, int[] cols) {
        ClusterParams.BoosterType type = boosterType(info.typeOrdinal);
        boolean failed = !info.available;
        // 失效整行暗红；生效行模块名加粗黑字，@段N 后缀灰字
        EnumChatFormatting base = failed ? EnumChatFormatting.DARK_RED : EnumChatFormatting.BLACK;
        String segSuffix = info.segmentIndex >= 0
            ? String.format(tr("gtsr.gui.cluster.editor.segment"), info.segmentIndex)
            : "";
        String moduleText = base.toString() + EnumChatFormatting.BOLD
            + StatCollector.translateToLocal(type.getLangKey())
            + (failed ? EnumChatFormatting.DARK_RED : EnumChatFormatting.GRAY)
            + segSuffix;
        String tierText = info.tier
            >= 0 ? base + StatCollector.translateToLocal(ClusterParams.ClusterTier.get(info.tier)
                .getLangKey()) : base + tr("gtsr.gui.cluster.booster.na");
        String fluidText = base + StatCollector.translateToLocal(type.getFluidLangKey());
        String gainText = base.toString() + EnumChatFormatting.BOLD + formatGain(type, info.tier);
        String statusText = failed ? EnumChatFormatting.DARK_RED + "✖ "
            + String
                .format(tr("gtsr.gui.cluster.booster.failed"), StatCollector.translateToLocal(type.getFluidLangKey()))
            : EnumChatFormatting.DARK_GREEN + "✔ " + tr("gtsr.gui.cluster.booster.active");

        // 当前持有：动态回查同步列表（行号一致；列表变化后行随之重建，行号始终有效）
        IWidget amountText = IKey.dynamic(() -> {
            List<BoosterRowInfo> current = listSync.getValue();
            BoosterRowInfo live = current != null && rowIndex < current.size() ? current.get(rowIndex) : info;
            return base
                + tr(live.available ? "gtsr.gui.cluster.booster.supply.on" : "gtsr.gui.cluster.booster.supply.off")
                + " "
                + NumberFormatUtil.formatNumber(live.amount)
                + " L";
        })
            .asWidget()
            .width(cols[3])
            .scale(0.9f);

        return Flow.row()
            .widthRel(1f)
            .height(ROW_H)
            .childPadding(CHILD_PADDING)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(
                IKey.str(moduleText)
                    .asWidget()
                    .width(cols[0])
                    .scale(0.9f))
            .child(
                IKey.str(tierText)
                    .asWidget()
                    .width(cols[1])
                    .scale(0.9f))
            .child(
                IKey.str(fluidText)
                    .asWidget()
                    .width(cols[2])
                    .scale(0.9f))
            .child(amountText)
            .child(
                IKey.str(gainText)
                    .asWidget()
                    .width(cols[4])
                    .scale(0.9f))
            .child(
                IKey.str(statusText)
                    .asWidget()
                    .width(cols[5])
                    .scale(0.9f));
    }

    /** 增益列格式：并行 +N（台数）；百分比类 +N%；tier&lt;0（结构未成型）显「—」。 */
    private static String formatGain(ClusterParams.BoosterType type, int tier) {
        if (tier < 0) return tr("gtsr.gui.cluster.booster.na");
        int value = type.getBoosterValue(tier);
        return type == ClusterParams.BoosterType.PARALLEL ? "+" + value : "+" + value + "%";
    }

    // ==================== 汇总区 ====================

    /** 汇总格：深色 DISPLAY 底，黄标签 + 白粗值 + 灰注释（值/注释均 IKey.dynamic 随同步值刷新）。 */
    private static IWidget buildSummaryCell(String label, Supplier<String> valueText, Supplier<String> noteText, int x,
        int y, int width) {
        return new ParentWidget<>().pos(x, y)
            .size(width, CELL_H)
            .background(GuiTextures.DISPLAY)
            .child(
                IKey.str(EnumChatFormatting.YELLOW + label)
                    .asWidget()
                    .pos(3, 1)
                    .scale(0.6f))
            .child(
                IKey.dynamic(valueText)
                    .asWidget()
                    .pos(3, 8)
                    .scale(0.75f))
            .child(
                IKey.dynamic(noteText)
                    .asWidget()
                    .pos(3, 18)
                    .scale(0.55f));
    }

    /** 节汽格值：显示截断后生效值；超限时红字并标注「已截断（原始 Σ N%）」。 */
    private static String formatSaverValue(double raw) {
        double effective = Math.min(raw, ClusterParams.STEAM_SAVER_CAP);
        String value = formatPct(effective) + "%";
        if (raw > ClusterParams.STEAM_SAVER_CAP + 1e-9) {
            return EnumChatFormatting.RED.toString() + EnumChatFormatting.BOLD
                + String.format(tr("gtsr.gui.cluster.booster.saver_capped"), value, formatPct(raw));
        }
        return whiteBold(value);
    }

    /** 节汽格注释：常态灰字提示上限；超限时红字重复截断警示。 */
    private static String formatSaverNote(double raw) {
        if (raw > ClusterParams.STEAM_SAVER_CAP + 1e-9) {
            return EnumChatFormatting.RED + tr("gtsr.gui.cluster.booster.saver_capped_note");
        }
        return grayNote(tr("gtsr.gui.cluster.booster.saver_note"));
    }

    /** 白色加粗值文本（汇总格深色底用）。 */
    private static String whiteBold(Object value) {
        return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + value;
    }

    /** 灰色小字注释（深色底用）。 */
    private static String grayNote(String text) {
        return EnumChatFormatting.GRAY + text;
    }

    /** 小数（0.15 口径）转百分比字串：整数显整数（15），否则一位小数（12.5）。 */
    private static String formatPct(double decimal) {
        double pct = decimal * 100D;
        if (Math.abs(pct - Math.rint(pct)) < 1e-6) {
            return String.valueOf((long) Math.rint(pct));
        }
        return String.format("%.1f", pct);
    }

    /** 本视图的本地化简写。 */
    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    // ==================== 行数据快照与序列化 ====================

    /** 服务端快照：逐模块取类型序号 / 结构 tier / 段号 / tank 余量 / 流体可用（照 boosterUnits 列表序）。 */
    private static List<BoosterRowInfo> collectRows(MTESteamMineralLogisticsCluster machine) {
        List<MTEBasicAmplifierUnit> units = machine.getTopology()
            .getBoosterUnits();
        List<BoosterRowInfo> rows = new ArrayList<>(units.size());
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getBoosterType() == null) continue;
            FluidStack tank = unit.getTankContent();
            rows.add(
                new BoosterRowInfo(
                    unit.getBoosterType()
                        .ordinal(),
                    unit.getStructureTier(),
                    unit.getSegmentIndex(),
                    tank != null ? tank.amount : 0,
                    unit.isFluidAvailable()));
        }
        return rows;
    }

    /** 读单条竖线分隔串（typeOrdinal|tier|segmentIndex|amount|available），畸形串防御性回退空态。 */
    private static BoosterRowInfo readRow(PacketBuffer buf) {
        return BoosterRowInfo.deserialize(ByteBufUtils.readUTF8String(buf));
    }

    /** 写单条竖线分隔串（读写顺序与 deserialize 严格一致）。 */
    private static void writeRow(PacketBuffer buf, BoosterRowInfo info) {
        ByteBufUtils.writeUTF8String(buf, info.serialize());
    }

    /** 相等判定：五字段全等才视为未变化（任一变化触发列表重同步 → 行重建）。 */
    private static boolean rowsEqual(BoosterRowInfo a, BoosterRowInfo b) {
        return a.typeOrdinal == b.typeOrdinal && a.tier == b.tier
            && a.segmentIndex == b.segmentIndex
            && a.amount == b.amount
            && a.available == b.available;
    }

    /** 序号安全取增幅类型：越界（枚举演进/脏数据）回退 PARALLEL，防御不可达分支。 */
    private static ClusterParams.BoosterType boosterType(int ordinal) {
        ClusterParams.BoosterType[] values = ClusterParams.BoosterType.values();
        if (ordinal < 0 || ordinal >= values.length) return ClusterParams.BoosterType.PARALLEL;
        return values[ordinal];
    }

    /** 标题用五种锁定流体本地化名（按 BoosterType 枚举序 = 硝酸/盐酸/氨气/硫酸/冷却液）拼接。 */
    private static String fluidNameList() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ClusterParams.BoosterType type : ClusterParams.BoosterType.values()) {
            if (!first) sb.append("／");
            sb.append(StatCollector.translateToLocal(type.getFluidLangKey()));
            first = false;
        }
        return sb.toString();
    }

    /**
     * 增幅模块行数据快照（S2C 单条序列化为竖线分隔串
     * {@code typeOrdinal|tier|segmentIndex|amount|available}；比规格串多带 segmentIndex 供
     * 「模块」列「@段N」后缀）。tier/segmentIndex 未成型/未收集时为 -1。
     */
    private static final class BoosterRowInfo {

        private final int typeOrdinal;
        private final int tier;
        private final int segmentIndex;
        private final int amount;
        private final boolean available;

        private BoosterRowInfo(int typeOrdinal, int tier, int segmentIndex, int amount, boolean available) {
            this.typeOrdinal = typeOrdinal;
            this.tier = tier;
            this.segmentIndex = segmentIndex;
            this.amount = amount;
            this.available = available;
        }

        /** 序列化为竖线分隔串（与 {@link #deserialize} 字段序严格一致）。 */
        private String serialize() {
            return typeOrdinal + "|" + tier + "|" + segmentIndex + "|" + amount + "|" + available;
        }

        /** 反序列化：字段数不符或解析失败回退空态（不崩客户端）。 */
        private static BoosterRowInfo deserialize(String s) {
            if (s == null) return new BoosterRowInfo(0, -1, -1, 0, false);
            String[] parts = s.split("\\|", -1);
            if (parts.length != 5) return new BoosterRowInfo(0, -1, -1, 0, false);
            try {
                return new BoosterRowInfo(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()),
                    Boolean.parseBoolean(parts[4].trim()));
            } catch (NumberFormatException e) {
                return new BoosterRowInfo(0, -1, -1, 0, false);
            }
        }
    }
}
