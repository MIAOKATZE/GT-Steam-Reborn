package com.miaokatze.gtsr.common.gui.cluster;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.ByteArraySyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.gui.widget.ScrollKeepingListWidget;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 集群三视图·页 0「拓扑」（批2 E6 重写；数据全部经 {@link ClusterGuiSync} §4.3.1/§4.3.2 快照）。
 *
 * <p>
 * 布局（页内绝对定位；本页<b>唯一滚动区</b> = 槽位列表）：
 * <ul>
 * <li>结构摘要条（y 0..11，不滚动）：成型 ✔/✖ + 等级 + 层数 N/9 + 模块计数（加工·增幅·物流）+
 * 有效链 + 异常摘要（extension_break 段号 / module_conflict、tier 不符计数，红色）；</li>
 * <li>列头行（y 13..22）：段列 + 加工垫/增幅垫/物流垫三列标题；</li>
 * <li>槽位列表（y 24..H-14）：{@link TopoSlotList}（{@link ScrollKeepingListWidget} 防滚动回顶 +
 * 首次布局滚至底部=基础层）。行序段降序（延伸 1-9 在上、基础层最下），每行 = 层标签 + 3 槽位卡；
 * 卡 = 模块名 + tier 徽章（真彩点）+ 六态状态文字 + 底部状态色条 + errId 红字；空槽「空位」灰；
 * typeId=255（未运行加工/增幅）显「未运行，暂无法识别」，<b>不伪装空位</b>；</li>
 * <li>六态图例（y H-12，不滚动）：六状态色块 + 名称（复用 gtsr.gui.cluster.state.*）。</li>
 * </ul>
 *
 * <p>
 * 数据流：{@code KEY_TOPO} byte[150]（30 槽 × [typeId,tier,stateOrdinal,errId,linkId]，
 * seg=i/3、pad=i%3）变化 → 全行重建（常驻列表实例，滚动不回顶）；{@code KEY_RUN} byte[30]
 * 状态 ordinal 由各卡 IKey.dynamic / onUpdateListener 每帧直读，状态刷新零重建。
 * 槽位卡只读：点击仅 tooltip 详情（无放置/移除——模块是实体方块）。
 */
public final class ClusterTopologyView {

    /** 每段垫槽数。 */
    private static final int PAD_COUNT = 3;
    /** 槽位总数（与 ClusterGuiSync.SLOT_COUNT 同值）。 */
    private static final int SLOT_COUNT = 30;
    /** 槽快照字节步长（typeId/tier/stateOrdinal/errId/linkId）。 */
    private static final int SLOT_BYTES = 5;
    /** 摘要条高。 */
    private static final int SUMMARY_H = 11;
    /** 列头行高。 */
    private static final int COLHEADER_DY = 13;
    private static final int COLHEADER_H = 9;
    /** 列表顶偏移。 */
    private static final int LIST_DY = 24;
    /** 图例高。 */
    private static final int LEGEND_H = 12;
    /** 段行高（= 槽位卡高）。 */
    private static final int ROW_H = 22;
    /** 层标签列宽。 */
    private static final int LABEL_W = 36;
    /** 行内卡间距。 */
    private static final int CARD_GAP = 2;
    /** 图例色块尺寸。 */
    private static final int LEGEND_SWATCH = 5;
    /** 空槽/未识别状态条色（暗灰）。 */
    private static final Rectangle BAR_EMPTY = new Rectangle().color(0xFF3A3A3F);
    /** 状态条预分配（六态 + 空/未识别共用 BAR_EMPTY；避免每帧新建）。 */
    private static final Rectangle[] STATUS_BARS = buildStatusBars();

    // —— 每次 build() 新建实例的可变状态（build 为静态入口，无静态可变态）——

    private final PanelSyncManager sync;
    private final int contentH;
    /** 槽位卡宽。 */
    private final int cardW;
    /** 列表实例（常驻；滚动位置随实例持续）。 */
    private ListWidget<IWidget, ?> grid;
    /** 滚动偏移回写宿主（ScrollKeepingListWidget 契约）。 */
    private int listScrollValue;
    /** 拓扑快照解析产物（KEY_TOPO 变化时重析）。 */
    private int[] typeIds = new int[SLOT_COUNT];
    private int[] tiers = new int[SLOT_COUNT];
    private int[] errIds = new int[SLOT_COUNT];
    private int[] linkIds = new int[SLOT_COUNT];

    private ClusterTopologyView(PanelSyncManager sync, int contentW, int contentH) {
        this.sync = sync;
        this.contentH = contentH;
        this.cardW = (contentW - LABEL_W - CARD_GAP * (PAD_COUNT - 1) - 6) / PAD_COUNT;
    }

    /**
     * 构建拓扑页并挂入分页容器（三视图契约入口，双端执行；契约保留参数 panel/actions 未用——本页只读）。
     */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {
        ClusterTopologyView view = new ClusterTopologyView(sync, contentW, contentH);

        // 拓扑快照变化监听：重析 + 全行重建（常驻实例不回顶）
        SyncHandler<?> topoSync = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_TOPO);
        if (topoSync instanceof ByteArraySyncValue value) {
            value.setChangeListener(() -> {
                view.parseTopo(value.getValue());
                view.rebuildRows();
            });
            view.parseTopo(value.getValue());
        }

        ParentWidget<?> page = new ParentWidget<>().size(contentW, contentH);
        page.child(view.buildSummary());
        view.buildColumnHeaders(page);
        view.grid = new TopoSlotList(() -> view.listScrollValue, value -> view.listScrollValue = value);
        view.grid.pos(0, LIST_DY)
            .size(contentW, contentH - LIST_DY - LEGEND_H - 2);
        page.child(view.grid);
        page.child(view.buildLegend());
        view.rebuildRows();
        paged.addPage(page);
    }

    // —— 结构摘要条（成型/等级/层数/模块计数/有效链/异常摘要） ——

    private IWidget buildSummary() {
        return Flow.row()
            .pos(0, 0)
            .height(SUMMARY_H)
            .childPadding(8)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(IKey.dynamic(() -> {
                int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1);
                return tier >= 0 ? EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.topo.formed")
                    : EnumChatFormatting.GRAY + "✖ " + tr("gtsr.cluster.gui.title.unformed");
            })
                .asWidget()
                .scale(0.75f))
            .child(IKey.dynamic(() -> {
                int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1);
                return tier >= 0 ? EnumChatFormatting.WHITE + tr(
                    ClusterParams.ClusterTier.get(tier)
                        .getLangKey())
                    : EnumChatFormatting.GRAY + "--";
            })
                .asWidget()
                .scale(0.75f))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + String.format(
                        tr("gtsr.cluster.gui.topo.segs"),
                        ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_SEGMENTS, 0)))
                    .asWidget()
                    .scale(0.75f))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.WHITE + moduleCountText())
                    .asWidget()
                    .scale(0.75f))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE + String.format(
                        tr("gtsr.cluster.gui.topo.chains"),
                        countBits(ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_LE_AVAIL, 0))))
                    .asWidget()
                    .scale(0.75f))
            .child(
                IKey.dynamic(() -> errorSummaryText())
                    .asWidget()
                    .scale(0.75f));
    }

    /** 模块计数文本：加工·增幅·物流（按 typeId 注册表统计）。 */
    private String moduleCountText() {
        int work = 0, boost = 0, logi = 0;
        for (int typeId : typeIds) {
            if (typeId >= 1 && typeId <= 7) work++;
            else if (typeId >= 8 && typeId <= 12) boost++;
            else if (typeId == 13) logi++;
        }
        return String.format(tr("gtsr.cluster.gui.topo.modules"), work, boost, logi);
    }

    /** 异常摘要：延伸断裂（KEY_BREAK）+ 快照 errId 计数（冲突/tier 不符/未关联），无异常空串。 */
    private String errorSummaryText() {
        StringBuilder sb = new StringBuilder();
        int brk = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_BREAK, -1);
        if (brk >= 1) {
            sb.append(String.format(tr("gtsr.cluster.gui.topo.error.ext"), brk))
                .append(' ');
        }
        int conflict = 0, tierMismatch = 0, unlinked = 0;
        for (int errId : errIds) {
            switch (errId) {
                case ClusterGuiSync.ERR_MODULE_CONFLICT -> conflict++;
                case ClusterGuiSync.ERR_TIER_MISMATCH -> tierMismatch++;
                case ClusterGuiSync.ERR_NOT_CONNECTED -> unlinked++;
                default -> {}
            }
        }
        if (conflict > 0) sb.append(String.format(tr("gtsr.cluster.gui.topo.error.conflict"), conflict))
            .append(' ');
        if (tierMismatch > 0) sb.append(String.format(tr("gtsr.cluster.gui.topo.error.tier"), tierMismatch))
            .append(' ');
        if (unlinked > 0) sb.append(String.format(tr("gtsr.cluster.gui.topo.error.unlinked"), unlinked))
            .append(' ');
        return sb.length() == 0 ? ""
            : EnumChatFormatting.RED + sb.toString()
                .trim();
    }

    /** 列头行：段列留空 + 三垫列标题（与卡列逐列对齐）。 */
    private void buildColumnHeaders(ParentWidget<?> page) {
        for (int pad = 0; pad < PAD_COUNT; pad++) {
            int x = LABEL_W + pad * (cardW + CARD_GAP);
            page.child(
                IKey.str(EnumChatFormatting.GRAY + tr(padLangKey(pad)))
                    .asWidget()
                    .pos(x, COLHEADER_DY)
                    .scale(0.6f));
        }
    }

    private static String padLangKey(int pad) {
        return switch (pad) {
            case 1 -> "gtsr.cluster.gui.topo.pad.boost";
            case 2 -> "gtsr.cluster.gui.topo.pad.logi";
            default -> "gtsr.cluster.gui.topo.pad.work";
        };
    }

    // —— 槽位列表 ——

    /** 解析拓扑快照 byte[150]（畸形/短包防御：越界槽按空槽处理）。 */
    private void parseTopo(byte[] snapshot) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int base = i * SLOT_BYTES;
            if (snapshot == null || base + SLOT_BYTES > snapshot.length) {
                typeIds[i] = ClusterGuiSync.TYPE_EMPTY;
                tiers[i] = -1;
                errIds[i] = 0;
                linkIds[i] = 255;
                continue;
            }
            // 五字节均为无符号语义（tier/errId/linkId 255 哨兵），读回 int
            typeIds[i] = snapshot[base] & 0xFF;
            tiers[i] = (snapshot[base + 1] & 0xFF) == 255 ? -1 : (snapshot[base + 1] & 0xFF);
            errIds[i] = snapshot[base + 3] & 0xFF;
            linkIds[i] = snapshot[base + 4] & 0xFF;
        }
    }

    /** 重建全部段行：行序段降序（延伸在上、基础最下）；常驻列表实例只重建行内容。 */
    private void rebuildRows() {
        if (grid == null) return;
        int segmentCount = Math.max(1, ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_SEGMENTS, 0) + 1);
        grid.removeAll();
        Map<Integer, Integer> rows = new TreeMap<>(Comparator.reverseOrder());
        for (int i = 0; i < SLOT_COUNT; i++) {
            int segment = i / PAD_COUNT;
            if (segment < segmentCount) rows.put(segment, segment);
        }
        for (int segment : rows.keySet()) {
            grid.child(buildSegmentRow(segment));
        }
    }

    /** 单段行：层标签 + 3 槽位卡（pad 0/1/2 = 加工/增幅/物流，左→右）。 */
    private IWidget buildSegmentRow(int segment) {
        Flow row = Flow.row()
            .widthRel(1f)
            .height(ROW_H)
            .childPadding(CARD_GAP)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        row.child(
            IKey.str(layerLabel(segment))
                .asWidget()
                .width(LABEL_W)
                .scale(0.7f));
        for (int pad = 0; pad < PAD_COUNT; pad++) {
            row.child(buildSlotCard(segment * PAD_COUNT + pad));
        }
        return row;
    }

    private static String layerLabel(int segment) {
        if (segment <= 0) {
            return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
                + tr("gtsr.cluster.gui.topo.layer.base");
        }
        return EnumChatFormatting.GRAY + String.format(tr("gtsr.cluster.gui.topo.layer.ext"), segment);
    }

    /**
     * 单槽位卡（只读；点击仅 tooltip 详情）：首行模块名 + tier 真彩点；次行状态文字（六态色）；
     * errId ≠ 0 时红字附加；底部状态色条随 KEY_RUN 每帧联动。空槽「空位」；
     * typeId=255「未运行，暂无法识别」（不伪装空位）。
     */
    private IWidget buildSlotCard(int slotIndex) {
        int typeId = typeIds[slotIndex];
        boolean occupied = typeId != ClusterGuiSync.TYPE_EMPTY;
        boolean unrecognized = typeId == ClusterGuiSync.TYPE_UNRECOGNIZED;
        ParentWidget<?> card = new ParentWidget<>().size(cardW, ROW_H);
        if (occupied && !unrecognized) {
            card.child(
                IKey.lang(typeLangKey(typeId))
                    .asWidget()
                    .pos(2, 1)
                    .scale(0.75f)
                    .width(cardW - 26));
            // tier 徽章真彩点（右上角）
            ParentWidget<?> dot = new ParentWidget<>().pos(cardW - 8, 3)
                .size(4, 4)
                .background(tierDot(tiers[slotIndex]));
            card.child(dot);
        }
        if (unrecognized) {
            card.child(
                IKey.str(EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.topo.slot.unrecognized"))
                    .asWidget()
                    .pos(2, 1)
                    .scale(0.7f)
                    .width(cardW - 4));
        }
        if (!occupied) {
            card.child(
                IKey.str(EnumChatFormatting.DARK_GRAY + tr("gtsr.cluster.gui.topo.slot.empty"))
                    .asWidget()
                    .pos(2, 4)
                    .scale(0.7f));
        }
        // 状态行：六态色文字（KEY_RUN 每帧直读；errId 附加红字）
        if (occupied) {
            card.child(
                IKey.dynamic(() -> statusLine(slotIndex))
                    .asWidget()
                    .pos(2, 11)
                    .scale(0.65f)
                    .width(cardW - 4));
        }
        // 底部状态色条：随 KEY_RUN 每帧联动（onUpdateListener 切预分配 Rectangle）
        ParentWidget<?> bar = new ParentWidget<>().pos(1, ROW_H - 4)
            .size(cardW - 2, 3)
            .background(BAR_EMPTY);
        if (occupied) {
            bar.onUpdateListener(w -> w.background(statusBarOf(runOrdinalOf(slotIndex))), true);
        }
        card.child(bar);
        // tooltip 详情（只读）
        card.tooltipBuilder(tooltip -> {
            if (unrecognized) {
                tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.topo.slot.unrecognized")));
                return;
            }
            if (!occupied) {
                tooltip.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + tr("gtsr.cluster.gui.topo.slot.empty")));
                return;
            }
            tooltip.addLine(IKey.lang(typeLangKey(typeId)));
            int tier = tiers[slotIndex];
            if (tier >= 0) {
                tooltip.addLine(
                    IKey.str(
                        EnumChatFormatting.GRAY + tr(
                            ClusterParams.ClusterTier.get(tier)
                                .getLangKey())));
            }
            int runOrdinal = runOrdinalOf(slotIndex);
            ClusterUnitStatus status = statusOf(runOrdinal);
            if (status != null) tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + tr(status.getLangKey())));
            String errText = errorText(errIds[slotIndex]);
            if (errText != null) tooltip.addLine(IKey.str(EnumChatFormatting.RED + errText));
            int linkId = linkIds[slotIndex];
            if (typeId == 13 && linkId != 255) {
                tooltip.addLine(
                    IKey.str(EnumChatFormatting.GRAY + String.format(tr("gtsr.cluster.gui.topo.linked"), linkId + 1)));
            }
        });
        return card;
    }

    /** 状态行文字：六态色 + 状态名；errId ≠ 0 附加红字短因。 */
    private String statusLine(int slotIndex) {
        ClusterUnitStatus status = statusOf(runOrdinalOf(slotIndex));
        if (status == null) return EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.state.standby");
        String text = statusColor(status) + tr(status.getLangKey());
        String errText = errorText(errIds[slotIndex]);
        return errText != null ? text + EnumChatFormatting.RED + " · " + errText : text;
    }

    /** KEY_RUN 槽状态 ordinal（255/越界 → -1）。 */
    private int runOrdinalOf(int slotIndex) {
        byte[] run = ClusterGuiSync.bytesOf(sync, ClusterGuiSync.KEY_RUN, null);
        if (run == null || slotIndex >= run.length) return -1;
        int value = run[slotIndex] & 0xFF;
        return value == 255 ? -1 : value;
    }

    private static ClusterUnitStatus statusOf(int ordinal) {
        ClusterUnitStatus[] values = ClusterUnitStatus.values();
        if (ordinal < 0 || ordinal >= values.length) return null;
        return values[ordinal];
    }

    /** 六态 → MC 近似文字色（工作中绿/空转橙/缺处理流体蓝/缺增幅流体紫/待机灰/未通电红）。 */
    private static EnumChatFormatting statusColor(ClusterUnitStatus status) {
        return switch (status) {
            case WORKING -> EnumChatFormatting.GREEN;
            case IDLE -> EnumChatFormatting.GOLD;
            case FLUID_MISSING -> EnumChatFormatting.BLUE;
            case BOOSTER_FLUID_MISSING -> EnumChatFormatting.DARK_PURPLE;
            case STANDBY -> EnumChatFormatting.GRAY;
            default -> EnumChatFormatting.RED;
        };
    }

    private static Rectangle statusBarOf(int runOrdinal) {
        ClusterUnitStatus status = statusOf(runOrdinal);
        return status == null ? BAR_EMPTY : STATUS_BARS[status.ordinal()];
    }

    private static Rectangle[] buildStatusBars() {
        ClusterUnitStatus[] values = ClusterUnitStatus.values();
        Rectangle[] bars = new Rectangle[values.length];
        for (ClusterUnitStatus status : values) {
            bars[status.ordinal()] = new Rectangle().color((status.getColorRgb() << 8) | 0xFF);
        }
        return bars;
    }

    /** tier 真彩点（-1 → 未成型灰）。 */
    private static Rectangle tierDot(int tier) {
        return switch (tier) {
            case 0 -> new Rectangle().color(0xFFC87E3B);
            case 1 -> new Rectangle().color(0xFFC2C8D0);
            case 2 -> new Rectangle().color(0xFF8EA2C8);
            case 3 -> new Rectangle().color(0xFF6E7F8C);
            default -> new Rectangle().color(0xFF6E6E6E);
        };
    }

    /** typeId → 模块名 lang key（注册表见 ClusterGuiSync 类注释）。 */
    private static String typeLangKey(int typeId) {
        return switch (typeId) {
            case 1 -> "gtsr.gui.cluster.unit_type.crusher";
            case 2 -> "gtsr.gui.cluster.unit_type.ore_washer";
            case 3 -> "gtsr.gui.cluster.unit_type.centrifuge";
            case 4 -> "gtsr.gui.cluster.unit_type.thermal_centrifuge";
            case 5 -> "gtsr.gui.cluster.unit_type.sifter";
            case 6 -> "gtsr.gui.cluster.unit_type.magnetic_separator";
            case 7 -> "gtsr.gui.cluster.unit_type.furnace";
            case 8 -> "gtsr.gui.cluster.unit_type.booster.parallel";
            case 9 -> "gtsr.gui.cluster.unit_type.booster.speed";
            case 10 -> "gtsr.gui.cluster.unit_type.booster.primary";
            case 11 -> "gtsr.gui.cluster.unit_type.booster.secondary";
            case 12 -> "gtsr.gui.cluster.unit_type.booster.steam_saver";
            case 13 -> "gtsr.gui.cluster.unit_type.logistics";
            default -> "gtsr.cluster.gui.topo.slot.unrecognized";
        };
    }

    /** errId → 短因文本（未知值通用异常）。 */
    private static String errorText(int errId) {
        return switch (errId) {
            case 0 -> null;
            case ClusterGuiSync.ERR_MODULE_CONFLICT -> tr("gtsr.cluster.gui.topo.err.conflict");
            case ClusterGuiSync.ERR_TIER_MISMATCH -> tr("gtsr.cluster.gui.topo.err.tier");
            case ClusterGuiSync.ERR_NOT_CONNECTED -> tr("gtsr.cluster.gui.topo.err.unlinked");
            default -> tr("gtsr.cluster.gui.topo.err.generic");
        };
    }

    /** 六态图例（不滚动）：色块真彩 + 名称小字。 */
    private IWidget buildLegend() {
        Flow legend = Flow.row()
            .pos(0, contentH - LEGEND_H)
            .height(LEGEND_H)
            .childPadding(6)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        for (ClusterUnitStatus status : ClusterUnitStatus.values()) {
            legend.child(
                new Rectangle().color((status.getColorRgb() << 8) | 0xFF)
                    .asWidget()
                    .size(LEGEND_SWATCH, LEGEND_SWATCH));
            legend.child(
                IKey.str(EnumChatFormatting.GRAY + tr(status.getLangKey()))
                    .asWidget()
                    .scale(0.6f));
        }
        return legend;
    }

    private static int countBits(int value) {
        int count = 0;
        while (value != 0) {
            value &= value - 1;
            count++;
        }
        return count;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    /**
     * 拓扑槽位列表：{@link ScrollKeepingListWidget}（重建/尺寸变化防滚动回顶）+ 首次布局滚至底部
     * （基础层默认入视野；scrollTo 内部 clamp 把超界值钳到底，故传 {@code Integer.MAX_VALUE}）。
     */
    private static final class TopoSlotList extends ScrollKeepingListWidget {

        private boolean snapPending = true;

        private TopoSlotList(IntSupplier scrollReader, IntConsumer scrollWriter) {
            super(scrollReader, scrollWriter);
        }

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
