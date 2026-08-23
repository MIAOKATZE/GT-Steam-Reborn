package com.miaokatze.gtsr.common.gui.cluster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterTopology;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * 蒸汽矿物物流集群 GUI 三视图·页 1「拓扑总览」（Modern UI 2，对齐 HTML v2.5 §拓扑总览的游戏内适配）。
 *
 * <p>
 * 布局（全部挂 {@link PagedWidget} 页容器内，页内坐标以页左上角为原点）：
 * <ul>
 * <li>头部一行（不滚动）：结构等级徽章（四族同级时 {@link ClusterParams.ClusterTier#getLangKey()}，
 * tier&lt;0 显示「未成型」灰）+「%d 段 · 延伸 %d 层」动态文本 + 成型 ✔/未成型 ✖ 状态摘要；</li>
 * <li>槽位网格：{@link ListWidget} 滚动容器（<b>全页唯一滚动区</b>，主面板无滚动）。行序 = 段降序
 * （延伸层在上、基础结构在最下，与 HTML v2.5 一致），每行 = 左侧层标签（「延伸层 N」/「基础结构」）
 * + 三个槽位卡（左→右 = 工作垫/增幅垫/物流垫，pad 0/1/2）；</li>
 * <li>槽位卡（宽约内容宽/3，纯文字无图标）：模块名（空槽显示「＋ 空槽位」灰字）+ tier 小徽章
 * （仅模块在场时）+ 底部状态色条（色 = {@link ClusterUnitStatus#getColorRgb()}，经
 * {@code (rgb << 8) | 0xFF} 组装 ARGB；纯色绘制物取 MUI2 最简单的 {@link Rectangle}）；
 * 模块是实体方块、GUI 只读展示，槽位卡不可点击放置，悬浮 tooltip 显示状态名；</li>
 * <li>底部一行（不滚动）：六状态色图例（色块 + 状态名小字）。</li>
 * </ul>
 *
 * <p>
 * <b>调用契约</b>：由 MTESteamMineralLogisticsClusterGui 以
 * {@code build(panel, sync, actions, machine, paged, contentX, contentY, contentW, contentH)} 调用，
 * 本视图经 {@code paged.addPage(...)} 挂页。约定调用方已把 paged 定位并尺寸于
 * {@code (contentX, contentY, contentW, contentH)}，页容器 {@code sizeRel(1f)} 恰覆盖内容区；
 * {@code panel/actions/contentX/contentY} 为契约保留参数（本视图只读无 C2S 动作，未使用）。
 *
 * <p>
 * <b>同步设计</b>（照 MTECacheHubStatusGui nodeListSync / ConfigGui oreListSync 范式）：
 * 单键 {@code "cl.slots"} = {@link GenericListSyncHandler}&lt;String&gt;，服务端每 tick 变化检测
 * （段数或任一槽内容变化即字符串列表不等 → 推送），每槽一条 {@code "seg|pad|unitTypeKey|statusOrdinal|tier"}。
 * 第 5 字段携带<b>集群</b>结构 tier（{@code machine.getStructureTierIndex()}，空槽行也携带）：
 * 头部等级徽章与成型判定由其派生，成型但暂无任何模块的集群也能正确显示等级（取舍：未成型时全表为 -1，
 * 显示「未成型 ✖」，此时服务端拓扑恒为 1 段 × 3 空槽，与「空集群显示 1 行 3 空槽」自查项一致）。
 * 客户端 changeListener 解析快照重建 ListWidget 行（常驻实例 + removeAll + 重建行，滚动位置随实例持续）。
 *
 * <p>
 * <b>默认滚至底部（基础行）的取舍</b>：MUI2（2.3.79）无原生「滚到底」API，故用
 * {@code getScrollData().scrollTo(getScrollArea(), Integer.MAX_VALUE)}——scrollTo 内部 clamp 会把超界值
 * 钳到 {@code scrollSize - 可视高} 即底部；仅在首次出现行的布局后执行一次（见 BottomSnapListWidget），
 * 之后行重建保持既有滚动、不再干扰用户拖动。弃用 reverseLayout 倒序渲染的备选方案：那会把基础行放到
 * 首行顶部，行序语义（延伸层在上）与 HTML v2.5 相反。
 *
 * <p>
 * <b>lang 键清单</b>（除既有 gtsr.gui.cluster.tier.* / gtsr.gui.cluster.state.* 外，本视图新增，
 * 由 lang 并行切片补齐，缺键时显示原键）：gtsr.gui.cluster.topology.unformed（未成型）、
 * .seginfo（%d 段 · 延伸 %d 层）、.formed（成型 ✔）、.unformed_mark（未成型 ✖）、.base（基础结构）、
 * .extension（延伸层 %d）、.empty_slot（＋ 空槽位）。
 */
public final class ClusterTopologyView {

    /** 槽位快照同步键（GenericListSyncHandler，S2C）。 */
    private static final String SYNC_KEY = "cl.slots";
    /** 每段垫槽数（工作/增幅/物流）。 */
    private static final int PAD_COUNT = 3;
    /** 头部行高。 */
    private static final int HEADER_H = 12;
    /** 图例行高（小字）。 */
    private static final int LEGEND_H = 10;
    /** 列表区顶部偏移（头部之下留 2px）。 */
    private static final int LIST_TOP = HEADER_H + 2;
    /** 列表区与图例之间的留白。 */
    private static final int LIST_BOTTOM_GAP = 2;
    /** 段行高（= 槽位卡高）。 */
    private static final int ROW_H = 26;
    /** 行内子元素间距（Flow childPadding）。 */
    private static final int ROW_PAD = 2;
    /** 左侧层标签列宽（「延伸层 NN」@0.8 缩放）。 */
    private static final int LABEL_W = 38;
    /** 滚动条宽度预留（滚动条激活时挤压内容，预留避免卡片换行/溢出）。 */
    private static final int SCROLLBAR_RESERVE = 5;
    /** 空槽状态条颜色（暗灰，与六状态色区分）。 */
    private static final int EMPTY_BAR_ARGB = 0xFF4A4A4A;
    /** 图例色块尺寸。 */
    private static final int LEGEND_SWATCH = 5;

    private static final String LANG_UNFORMED = "gtsr.gui.cluster.topology.unformed";
    private static final String LANG_SEGINFO = "gtsr.gui.cluster.topology.seginfo";
    private static final String LANG_FORMED = "gtsr.gui.cluster.topology.formed";
    private static final String LANG_UNFORMED_MARK = "gtsr.gui.cluster.topology.unformed_mark";
    private static final String LANG_BASE = "gtsr.gui.cluster.topology.base";
    private static final String LANG_EXTENSION = "gtsr.gui.cluster.topology.extension";
    private static final String LANG_EMPTY_SLOT = "gtsr.gui.cluster.topology.empty_slot";

    // —— 每次 build() 新建实例的可变状态（build 为静态入口，无静态可变态，多玩家/多次打开互不干扰）——

    /** 槽位卡宽（约内容宽/3，按 contentW 推导）。 */
    private final int cardW;
    /** 列表区高度（contentH 减头部/图例）。 */
    private final int listH;
    /** 槽位快照同步（客户端 changeListener 重建行的数据源）。 */
    private GenericListSyncHandler<String> slotsSync;
    /** 常驻槽位网格实例（滚动位置随实例持续，行内容重建不重置滚动）。 */
    private ListWidget<IWidget, ?> grid;
    /** 头部徽章/状态摘要派生值（rebuildRows 时更新，IKey.dynamic 每帧读取）。 */
    private int headerTier = -1;
    /** 头部「%d 段」派生值（= 最大段下标 + 1）。 */
    private int headerSegments = 0;

    private ClusterTopologyView(int contentW, int contentH) {
        // 卡宽 ≈ 内容宽/3：扣除层标签列、行内间距（标签↔卡、卡↔卡共 3 处）与滚动条预留
        this.cardW = (contentW - LABEL_W - PAD_COUNT * ROW_PAD - SCROLLBAR_RESERVE) / PAD_COUNT;
        this.listH = contentH - LIST_TOP - LEGEND_H - LIST_BOTTOM_GAP;
    }

    /**
     * 构建拓扑总览页并挂入分页容器。
     *
     * <p>
     * 契约保留参数说明：{@code panel}（本视图内容全部挂 paged 页内，未用）、{@code actions}
     * （本页只读展示，无 C2S 动作）、{@code contentX/contentY}（约定 paged 已定位于此，页内坐标以页
     * 左上角为原点，未直接使用）。
     *
     * @param panel    主面板（保留参数）
     * @param sync     面板同步管理器（注册 "cl.slots"）
     * @param actions  集群动作处理器（保留参数，本页只读）
     * @param machine  集群总控（服务端侧为快照数据源）
     * @param paged    三视图分页容器（已定位并尺寸于内容区）
     * @param contentX 内容区左上角 X（面板坐标，契约保留）
     * @param contentY 内容区左上角 Y（面板坐标，契约保留）
     * @param contentW 内容区宽（页尺寸与卡片宽度推导依据）
     * @param contentH 内容区高（列表区/图例布局推导依据）
     */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {
        ClusterTopologyView view = new ClusterTopologyView(contentW, contentH);

        // 1) 槽位快照同步（S2C）：服务端每 tick 变化检测，客户端 changeListener 重建行
        GenericListSyncHandler<String> slotsSync = new GenericListSyncHandler<>(
            () -> serializeSlots(machine),
            null,
            buf -> ByteBufUtils.readUTF8String(buf),
            (buf, value) -> ByteBufUtils.writeUTF8String(buf, value),
            (a, b) -> a == null ? b == null : a.equals(b),
            null);
        view.slotsSync = slotsSync;
        slotsSync.setChangeListener(view::rebuildRows);
        sync.syncValue(SYNC_KEY, slotsSync);

        // 2) 槽位网格：全页唯一滚动区（常驻实例，默认滚至底部=基础行，见 BottomSnapListWidget 取舍注记）
        ListWidget<IWidget, ?> grid = new BottomSnapListWidget();
        grid.pos(0, LIST_TOP)
            .size(contentW, view.listH);
        view.grid = grid;

        // 3) 页容器挂 paged：头部（不滚动）→ 网格（滚动）→ 图例（不滚动）
        paged.addPage(
            new ParentWidget<>().sizeRel(1f)
                .child(view.buildHeader())
                .child(grid)
                .child(buildLegend(contentH)));

        // 4) 初始行构建（首帧前数据可能尚未同步到达，首次同步后 changeListener 会再重建）
        view.rebuildRows();
    }

    // —— 头部 / 图例 ——

    /** 头部一行：结构等级徽章 + 段数/延伸层数 + 成型状态摘要（全部 IKey.dynamic，随同步数据刷新）。 */
    private IWidget buildHeader() {
        return Flow.row()
            .pos(0, 0)
            .height(HEADER_H)
            .childPadding(8)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(
                IKey.dynamic(
                    () -> headerTier >= 0
                        ? EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                            + StatCollector.translateToLocal(
                                ClusterParams.ClusterTier.get(headerTier)
                                    .getLangKey())
                        : EnumChatFormatting.GRAY + StatCollector.translateToLocal(LANG_UNFORMED))
                    .asWidget()
                    .scale(0.9f))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.BLACK + String.format(
                        StatCollector.translateToLocal(LANG_SEGINFO),
                        headerSegments,
                        Math.max(0, headerSegments - 1)))
                    .asWidget()
                    .scale(0.9f))
            .child(
                IKey.dynamic(
                    () -> headerTier >= 0 ? EnumChatFormatting.GREEN + StatCollector.translateToLocal(LANG_FORMED)
                        : EnumChatFormatting.GRAY + StatCollector.translateToLocal(LANG_UNFORMED_MARK))
                    .asWidget()
                    .scale(0.9f));
    }

    /** 底部六状态色图例一行（小字，不滚动）：色块颜色与槽位卡状态条同源（ClusterUnitStatus 六态）。 */
    private static IWidget buildLegend(int contentH) {
        Flow legend = Flow.row()
            .pos(0, contentH - LEGEND_H)
            .height(LEGEND_H)
            .childPadding(5)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        for (ClusterUnitStatus status : ClusterUnitStatus.values()) {
            legend.child(
                new Rectangle().color(toArgb(status.getColorRgb()))
                    .asWidget()
                    .size(LEGEND_SWATCH, LEGEND_SWATCH));
            legend.child(
                IKey.str(EnumChatFormatting.GRAY + StatCollector.translateToLocal(status.getLangKey()))
                    .asWidget()
                    .scale(0.7f));
        }
        return legend;
    }

    // —— 槽位网格行 ——

    /**
     * 解析客户端快照并重建全部段行（常驻 ListWidget 实例 + removeAll + 重建行：滚动位置随实例持续，
     * 服务端同步到达 / 段数或槽内容变化时触发）。行序 = 段降序（延伸层在上、基础结构在最下）。
     */
    private void rebuildRows() {
        if (slotsSync == null || grid == null) return;
        List<String> lines = slotsSync.getValue();
        // 段降序行容器：TreeMap 反序键保证迭代即渲染序
        Map<Integer, SlotData[]> rows = new TreeMap<>(Comparator.reverseOrder());
        int maxTier = -1;
        int maxSegment = -1;
        for (String line : lines) {
            SlotData card = parseCard(line);
            if (card == null) continue;
            rows.computeIfAbsent(card.segment, key -> new SlotData[PAD_COUNT])[card.pad] = card;
            if (card.tier > maxTier) maxTier = card.tier;
            if (card.segment > maxSegment) maxSegment = card.segment;
        }
        headerTier = maxTier;
        headerSegments = maxSegment + 1;
        grid.removeAll();
        for (Map.Entry<Integer, SlotData[]> entry : rows.entrySet()) {
            grid.child(buildSegmentRow(entry.getKey(), entry.getValue()));
        }
    }

    /** 构建单个段行：左侧层标签 + 三个槽位卡（pad 0/1/2 = 工作垫/增幅垫/物流垫，左→右）。 */
    private IWidget buildSegmentRow(int segment, SlotData[] cards) {
        Flow row = Flow.row()
            .widthRel(1f)
            .height(ROW_H)
            .childPadding(ROW_PAD)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        row.child(buildLayerLabel(segment));
        for (int pad = 0; pad < PAD_COUNT; pad++) {
            SlotData card = cards != null ? cards[pad] : null;
            row.child(buildSlotCard(card));
        }
        return row;
    }

    /** 左侧层标签：段 0 =「基础结构」黑色加粗，其余 =「延伸层 N」灰色（N 即段下标，k+1 = 第 k+1 延伸段）。 */
    private static IWidget buildLayerLabel(int segment) {
        String text = segment <= 0
            ? EnumChatFormatting.BLACK.toString() + EnumChatFormatting.BOLD + StatCollector.translateToLocal(LANG_BASE)
            : EnumChatFormatting.GRAY + String.format(StatCollector.translateToLocal(LANG_EXTENSION), segment);
        return IKey.str(text)
            .asWidget()
            .width(LABEL_W)
            .scale(0.8f);
    }

    /**
     * 构建单个槽位卡（约内容宽/3，纯文字）：模块名 + tier 小徽章（模块在场且 tier≥0 时）+ 底部状态色条。
     * 游戏内模块是实体方块、GUI 只读展示——卡不可点击放置，悬浮 tooltip 显示模块名与状态名
     * （{@code IKey.lang(status.getLangKey())}）；空槽显示「＋ 空槽位」灰字与暗灰条。
     *
     * @param card 槽位快照；null 或无模块字段按空槽渲染（服务端每段恒发 3 槽，此为防御分支）
     */
    private IWidget buildSlotCard(SlotData card) {
        boolean hasUnit = card != null && card.hasUnit();
        ClusterUnitStatus status = hasUnit ? statusOf(card.statusOrdinal) : null;
        int barColor = hasUnit ? toArgb(status.getColorRgb()) : EMPTY_BAR_ARGB;
        ParentWidget<?> cardWidget = new ParentWidget<>().size(cardW, ROW_H);
        if (hasUnit) {
            cardWidget.child(
                IKey.lang(card.typeKey)
                    .asWidget()
                    .pos(2, 2)
                    .scale(0.85f)
                    .width(cardW - 4));
            if (card.tier >= 0) {
                cardWidget.child(
                    IKey.str(
                        EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                            ClusterParams.ClusterTier.get(card.tier)
                                .getLangKey()))
                        .asWidget()
                        .pos(2, 12)
                        .scale(0.7f));
            }
        } else {
            cardWidget.child(
                IKey.str(EnumChatFormatting.GRAY + StatCollector.translateToLocal(LANG_EMPTY_SLOT))
                    .asWidget()
                    .pos(2, 2)
                    .scale(0.85f));
        }
        // 底部状态色条：MUI2 最简纯色绘制物 Rectangle（ARGB = (rgb << 8) | 0xFF，见 ClusterUnitStatus javadoc）
        cardWidget.child(
            new Rectangle().color(barColor)
                .asWidget()
                .pos(1, ROW_H - 3)
                .size(cardW - 2, 2));
        cardWidget.tooltipBuilder(tooltip -> {
            if (hasUnit) {
                tooltip.addLine(IKey.lang(card.typeKey));
                if (status != null) tooltip.addLine(IKey.lang(status.getLangKey()));
            } else {
                tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + StatCollector.translateToLocal(LANG_EMPTY_SLOT)));
            }
        });
        return cardWidget;
    }

    // —— 序列化 / 解析 ——

    /**
     * 服务端快照序列化（"cl.slots" 数据源）：{@link ClusterTopology#getSlots()} 按 segment 升序、
     * pad 升序产出每段恰 3 槽（含空槽），逐槽编码为 {@code "seg|pad|unitTypeKey|statusOrdinal|tier"}。
     * 第 5 字段为集群结构 tier（空槽也携带，头部徽章由其派生，见类头同步设计）。
     */
    private static List<String> serializeSlots(MTESteamMineralLogisticsCluster machine) {
        int clusterTier = machine.getStructureTierIndex();
        List<ClusterTopology.SlotSnapshot> slots = machine.getTopology()
            .getSlots();
        List<String> lines = new ArrayList<>(slots.size());
        for (ClusterTopology.SlotSnapshot slot : slots) {
            MTEClusterUnitBase unit = slot.unit;
            boolean hasUnit = unit != null;
            lines.add(
                slot.segment + "|"
                    + slot.pad
                    + "|"
                    + (hasUnit ? unit.getUnitTypeNameKey() : "")
                    + "|"
                    + (hasUnit ? unit.getUnitStatus()
                        .ordinal() : -1)
                    + "|"
                    + clusterTier);
        }
        return lines;
    }

    /**
     * 客户端解析单行快照；格式不符（字段数 ≠ 5 / 数值非法 / pad 越界）返回 null 由重建循环跳过。
     */
    private static SlotData parseCard(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5) return null;
        try {
            int segment = Integer.parseInt(parts[0]);
            int pad = Integer.parseInt(parts[1]);
            int statusOrdinal = Integer.parseInt(parts[3]);
            int tier = Integer.parseInt(parts[4]);
            if (segment < 0 || pad < 0 || pad >= PAD_COUNT) return null;
            return new SlotData(segment, pad, parts[2], statusOrdinal, tier);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 状态序号 → 枚举（越界返回 null，防御旧存档/异常数据）。 */
    private static ClusterUnitStatus statusOf(int ordinal) {
        ClusterUnitStatus[] values = ClusterUnitStatus.values();
        if (ordinal < 0 || ordinal >= values.length) return null;
        return values[ordinal];
    }

    /** 0xRRGGBB → ARGB（组装方式照 ClusterUnitStatus 类 javadoc 约定）。 */
    private static int toArgb(int rgb) {
        return (rgb << 8) | 0xFF;
    }

    /** 槽位快照行（客户端解析产物，不可变）。 */
    private static final class SlotData {

        private final int segment;
        private final int pad;
        /** 模块类型名 lang key；空串 = 空槽。 */
        private final String typeKey;
        /** 状态序号（{@link ClusterUnitStatus}）；-1 = 空槽。 */
        private final int statusOrdinal;
        /** 集群结构 tier（-1 = 未成型）。 */
        private final int tier;

        private SlotData(int segment, int pad, String typeKey, int statusOrdinal, int tier) {
            this.segment = segment;
            this.pad = pad;
            this.typeKey = typeKey;
            this.statusOrdinal = statusOrdinal;
            this.tier = tier;
        }

        /** 模块在场判定：类型键非空且状态序号合法。 */
        private boolean hasUnit() {
            return typeKey != null && !typeKey.isEmpty() && statusOf(statusOrdinal) != null;
        }
    }

    /**
     * 默认滚至底部的槽位网格（延伸层在上、基础结构最下，默认视图定位到基础行）。
     * MUI2 无原生「滚到底」API：scrollTo 的内部 clamp 会把超界值钳到底部，故传 {@code Integer.MAX_VALUE}；
     * 仅在首次出现行的布局后执行一次（此前数据未到、列表为空，不消耗标志），之后常驻实例保持用户滚动，
     * 行重建（removeAll + 重建）不再干扰。
     */
    private static final class BottomSnapListWidget extends ListWidget<IWidget, BottomSnapListWidget> {

        private boolean snapPending = true;

        @Override
        public void postResize() {
            super.postResize();
            if (snapPending && getScrollData() != null && hasChildren()) {
                getScrollData().scrollTo(getScrollArea(), Integer.MAX_VALUE);
                snapPending = false;
            }
        }
    }
}
