package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 集群终端·页 0「拓扑」（terminal-native-ui N16，只读；旧 MUI2 轨拓扑视图（git 基线 b4fabb2）
 * 自绘移植，
 * 行高/列宽 1:1：摘要条 11 + 列头 13 + 槽位列表 24..244（行高 22）+ 六态图例 246）。
 *
 * <p>
 * 数据流：{@code KEY_TOPO} byte[150]（30 槽 × [typeId,tier,stateOrdinal,errId,linkId]，seg=i/3、
 * pad=i%3；解码注册表照 wiki §1.1-1.4 冻结，与 N33 常量一致）、{@code KEY_RUN} byte[30]（20t 采样，
 * 空槽 255）、KEY_TIER/KEY_SEGMENTS/KEY_BREAK/KEY_LE_AVAIL。<b>live 每帧重读</b>：快照与状态字节
 * 由 {@link #draw} 每帧解析直读（零构造期快照求值，无 KEY_RUN 监听遗漏面）；行序段降序（延伸在上、
 * 基础最下）；typeId=255「未运行，暂无法识别」不伪装空位。列表滚动偏移自持（数据刷新不回顶，
 * 等价旧 MUI2 滚动保持列表语义）。槽位卡只读：点击仅 tooltip 详情（模块是实体方块）。
 */
@SideOnly(Side.CLIENT)
final class ClusterTopologyPage implements ClusterPage {

    private static final int PAD_COUNT = 3;
    private static final int SLOT_COUNT = 30;
    private static final int SLOT_BYTES = 5;
    private static final int COLHEADER_DY = 13;
    private static final int LIST_DY = 24;
    private static final int LEGEND_H = 12;
    private static final int ROW_H = 22;
    private static final int LABEL_W = 36;
    private static final int CARD_GAP = 2;
    private static final int LEGEND_SWATCH = 5;
    /** 空槽/未识别状态条色（暗灰，旧 BAR_EMPTY 同值）。 */
    private static final int BAR_EMPTY = 0xFF3A3A3F;

    private final GuiClusterTerminalScreen host;
    /** 滚动偏移（行数；自持）。 */
    private int scroll;
    /** 拓扑快照解析产物（draw 每帧重析）。 */
    private final int[] typeIds = new int[SLOT_COUNT];
    private final int[] tiers = new int[SLOT_COUNT];
    private final int[] errIds = new int[SLOT_COUNT];
    private final int[] linkIds = new int[SLOT_COUNT];

    ClusterTopologyPage(GuiClusterTerminalScreen host) {
        this.host = host;
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        parseTopo(ClusterTerminalClientCache.getBytes(ClusterTerminalData.KEY_TOPO));
        final int cardW = (GuiClusterTerminalScreen.CONTENT_W - LABEL_W - CARD_GAP * (PAD_COUNT - 1) - 6) / PAD_COUNT;
        drawSummary(ox, oy);
        drawColumnHeaders(ox, oy, cardW);
        drawSlotRows(ox, oy, mx, my, z, cardW);
        drawLegend(ox, oy);
    }

    /** 解析拓扑快照 byte[150]（畸形/短包防御：越界槽按空槽处理；旧 parseTopo 同逻辑）。 */
    private void parseTopo(byte[] snapshot) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int base = i * SLOT_BYTES;
            if (snapshot == null || base + SLOT_BYTES > snapshot.length) {
                this.typeIds[i] = ClusterTerminalData.TYPE_EMPTY;
                this.tiers[i] = -1;
                this.errIds[i] = 0;
                this.linkIds[i] = 255;
                continue;
            }
            // 五字节均为无符号语义（tier/errId/linkId 255 哨兵），读回 int
            this.typeIds[i] = snapshot[base] & 0xFF;
            this.tiers[i] = (snapshot[base + 1] & 0xFF) == 255 ? -1 : (snapshot[base + 1] & 0xFF);
            this.errIds[i] = snapshot[base + 3] & 0xFF;
            this.linkIds[i] = snapshot[base + 4] & 0xFF;
        }
    }

    /** 结构摘要条（成型/等级/层数/模块计数/有效链/异常摘要；动态每帧重读）。 */
    private void drawSummary(int ox, int oy) {
        int tier = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_TIER, -1);
        String[] parts = new String[] {
            tier >= 0 ? EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.topo.formed")
                : EnumChatFormatting.WHITE + "✖ " + tr("gtsr.cluster.gui.title.unformed"),
            tier >= 0 ? EnumChatFormatting.WHITE + tr(
                ClusterParams.ClusterTier.get(tier)
                    .getLangKey())
                : EnumChatFormatting.WHITE + "--",
            EnumChatFormatting.WHITE + String.format(
                tr("gtsr.cluster.gui.topo.segs"),
                ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SEGMENTS, 0)),
            EnumChatFormatting.WHITE + moduleCountText(),
            EnumChatFormatting.WHITE + String.format(
                tr("gtsr.cluster.gui.topo.chains"),
                countBits(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LE_AVAIL, 0))),
            errorSummaryText() };
        int cursor = ox;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            GuiClusterTerminalScreen.drawScaledText(font(), part, cursor, oy, 0.75f, GtsrGuiPalette.TEXT_WHITE);
            cursor += GuiClusterTerminalScreen.scaledTextWidth(font(), part, 0.75f) + 8;
        }
    }

    /** 模块计数文本：加工·增幅·物流（按 typeId 注册表统计）。 */
    private String moduleCountText() {
        int work = 0, boost = 0, logi = 0;
        for (int typeId : this.typeIds) {
            if (typeId >= 1 && typeId <= 7) work++;
            else if (typeId >= 8 && typeId <= 12) boost++;
            else if (typeId == 13) logi++;
        }
        return String.format(tr("gtsr.cluster.gui.topo.modules"), work, boost, logi);
    }

    /** 异常摘要：延伸断裂（KEY_BREAK）+ 快照 errId 计数（冲突/tier 不符/未关联），无异常空串。 */
    private String errorSummaryText() {
        StringBuilder sb = new StringBuilder();
        int brk = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_BREAK, -1);
        if (brk >= 1) {
            sb.append(String.format(tr("gtsr.cluster.gui.topo.error.ext"), brk))
                .append(' ');
        }
        int conflict = 0, tierMismatch = 0, unlinked = 0;
        for (int errId : this.errIds) {
            switch (errId) {
                case ClusterTerminalData.ERR_MODULE_CONFLICT -> conflict++;
                case ClusterTerminalData.ERR_TIER_MISMATCH -> tierMismatch++;
                case ClusterTerminalData.ERR_NOT_CONNECTED -> unlinked++;
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
    private void drawColumnHeaders(int ox, int oy, int cardW) {
        for (int pad = 0; pad < PAD_COUNT; pad++) {
            int x = ox + LABEL_W + pad * (cardW + CARD_GAP);
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                EnumChatFormatting.WHITE + tr(padLangKey(pad)),
                x,
                oy + COLHEADER_DY,
                0.6f,
                GtsrGuiPalette.TEXT_MUTED);
        }
    }

    private static String padLangKey(int pad) {
        return switch (pad) {
            case 1 -> "gtsr.cluster.gui.topo.pad.boost";
            case 2 -> "gtsr.cluster.gui.topo.pad.logi";
            default -> "gtsr.cluster.gui.topo.pad.work";
        };
    }

    /** 槽位列表：行序段降序（延伸在上、基础最下）；剪刀内逐行绘制（每行 = 层标签 + 3 槽位卡）。 */
    private void drawSlotRows(int ox, int oy, int mx, int my, float z, int cardW) {
        int segmentCount = Math.max(1, ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SEGMENTS, 0) + 1);
        int visible = (GuiClusterTerminalScreen.CONTENT_H - LIST_DY - LEGEND_H - 2) / ROW_H;
        int maxScroll = Math.max(0, segmentCount - visible);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;
        for (int rowIdx = 0; rowIdx < visible; rowIdx++) {
            int segment = segmentCount - 1 - (rowIdx + this.scroll);
            if (segment < 0) break;
            int rowY = oy + LIST_DY + rowIdx * ROW_H;
            GuiClusterTerminalScreen
                .drawScaledText(font(), layerLabel(segment), ox, rowY + 6, 0.7f, GtsrGuiPalette.TEXT_BODY);
            for (int pad = 0; pad < PAD_COUNT; pad++) {
                drawSlotCard(
                    ox + LABEL_W + pad * (cardW + CARD_GAP),
                    rowY,
                    mx,
                    my,
                    z,
                    cardW,
                    segment * PAD_COUNT + pad);
            }
        }
    }

    private static String layerLabel(int segment) {
        if (segment <= 0) {
            return EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD
                + tr("gtsr.cluster.gui.topo.layer.base");
        }
        return EnumChatFormatting.WHITE + String.format(tr("gtsr.cluster.gui.topo.layer.ext"), segment);
    }

    /**
     * 单槽位卡（只读；点击仅 tooltip 详情）：首行模块名 + tier 真彩点；次行状态文字（六态色）；
     * errId ≠ 0 时红字附加；底部状态色条随 KEY_RUN 每帧联动。空槽「空位」；
     * typeId=255「未运行，暂无法识别」（不伪装空位）。
     */
    private void drawSlotCard(int x, int y, int mx, int my, float z, int cardW, int slotIndex) {
        int typeId = this.typeIds[slotIndex];
        boolean occupied = typeId != ClusterTerminalData.TYPE_EMPTY;
        boolean unrecognized = typeId == ClusterTerminalData.TYPE_UNRECOGNIZED;
        int tier = this.tiers[slotIndex];
        if (occupied && !unrecognized) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                GtsrGuiList.ellipsis(font(), tr(typeLangKey(typeId)), (int) ((cardW - 26) / 0.75f)),
                x + 2,
                y + 2,
                0.75f,
                GtsrGuiPalette.TEXT_BODY);
            GuiClusterTerminalScreen.fillRect(x + cardW - 8, y + 3, 4, 4, z, tierDotColor(tier));
        }
        if (unrecognized) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.topo.slot.unrecognized"),
                x + 2,
                y + 2,
                0.7f,
                GtsrGuiPalette.TEXT_MUTED);
        }
        if (!occupied) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.topo.slot.empty"),
                x + 2,
                y + 5,
                0.7f,
                GtsrGuiPalette.TEXT_MUTED);
        }
        // 状态行：六态色文字（KEY_RUN 每帧直读；errId 附加红字）
        if (occupied) {
            GuiClusterTerminalScreen
                .drawScaledText(font(), statusLine(slotIndex), x + 2, y + 12, 0.65f, GtsrGuiPalette.TEXT_BODY);
        }
        // 底部状态色条：随 KEY_RUN 每帧联动
        int barColor = BAR_EMPTY;
        if (occupied) {
            ClusterUnitStatus status = statusOf(runOrdinalOf(slotIndex));
            if (status != null) barColor = (status.getColorRgb() << 8) | 0xFF;
        }
        GuiClusterTerminalScreen.fillRect(x + 1, y + ROW_H - 4, cardW - 2, 3, z, barColor);
        // tooltip 详情（只读；经宿主 500ms 通道出剪绘制）
        if (mx >= x && mx < x + cardW && my >= y && my < y + ROW_H) {
            this.host.requestTooltip("topo" + slotIndex, slotTooltip(typeId, tier, slotIndex, occupied, unrecognized));
        }
    }

    /** 槽位 tooltip 行（名/tier/状态/异常/关联；旧 buildSlotCard tooltipBuilder 同内容）。 */
    private java.util.List<String> slotTooltip(int typeId, int tier, int slotIndex, boolean occupied,
        boolean unrecognized) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        if (unrecognized) {
            lines.add(EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.topo.slot.unrecognized"));
            return lines;
        }
        if (!occupied) {
            lines.add(EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.topo.slot.empty"));
            return lines;
        }
        lines.add(tr(typeLangKey(typeId)));
        if (tier >= 0) {
            lines.add(
                EnumChatFormatting.WHITE + tr(
                    ClusterParams.ClusterTier.get(tier)
                        .getLangKey()));
        }
        ClusterUnitStatus status = statusOf(runOrdinalOf(slotIndex));
        if (status != null) lines.add(EnumChatFormatting.WHITE + tr(status.getLangKey()));
        String errText = errorText(this.errIds[slotIndex]);
        if (errText != null) lines.add(EnumChatFormatting.RED + errText);
        int linkId = this.linkIds[slotIndex];
        if (typeId == 13 && linkId != 255) {
            lines.add(EnumChatFormatting.WHITE + String.format(tr("gtsr.cluster.gui.topo.linked"), linkId + 1));
        }
        return lines;
    }

    /** 状态行文字：六态色 + 状态名；errId ≠ 0 附加红字短因。 */
    private String statusLine(int slotIndex) {
        ClusterUnitStatus status = statusOf(runOrdinalOf(slotIndex));
        if (status == null) return EnumChatFormatting.WHITE + tr("gtsr.gui.cluster.state.standby");
        String text = statusColor(status) + tr(status.getLangKey());
        String errText = errorText(this.errIds[slotIndex]);
        return errText != null ? text + EnumChatFormatting.RED + " · " + errText : text;
    }

    /** KEY_RUN 槽状态 ordinal（255/越界 → -1）。 */
    private int runOrdinalOf(int slotIndex) {
        byte[] run = ClusterTerminalClientCache.getBytes(ClusterTerminalData.KEY_RUN);
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

    /** tier 真彩点（-1 → 未成型灰）。 */
    private static int tierDotColor(int tier) {
        return switch (tier) {
            case 0 -> 0xFFC87E3B;
            case 1 -> 0xFFC2C8D0;
            case 2 -> 0xFF8EA2C8;
            case 3 -> 0xFF6E7F8C;
            default -> 0xFF6E6E6E;
        };
    }

    /** typeId → 模块名 lang key（注册表见 ClusterTerminalData 类注释，冻结只尾追）。 */
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
            case ClusterTerminalData.ERR_MODULE_CONFLICT -> tr("gtsr.cluster.gui.topo.err.conflict");
            case ClusterTerminalData.ERR_TIER_MISMATCH -> tr("gtsr.cluster.gui.topo.err.tier");
            case ClusterTerminalData.ERR_NOT_CONNECTED -> tr("gtsr.cluster.gui.topo.err.unlinked");
            default -> tr("gtsr.cluster.gui.topo.err.generic");
        };
    }

    /** 六态图例（不滚动）：色块真彩 + 名称小字。 */
    private void drawLegend(int ox, int oy) {
        int cursor = ox;
        int y = oy + GuiClusterTerminalScreen.CONTENT_H - LEGEND_H;
        for (ClusterUnitStatus status : ClusterUnitStatus.values()) {
            GuiClusterTerminalScreen.fillRect(
                cursor,
                y + 2,
                LEGEND_SWATCH,
                LEGEND_SWATCH,
                this.host.zLevel(),
                (status.getColorRgb() << 8) | 0xFF);
            cursor += LEGEND_SWATCH + 3;
            String name = EnumChatFormatting.WHITE + tr(status.getLangKey());
            GuiClusterTerminalScreen.drawScaledText(font(), name, cursor, y + 1, 0.6f, GtsrGuiPalette.TEXT_MUTED);
            cursor += GuiClusterTerminalScreen.scaledTextWidth(font(), name, 0.6f) + 6;
        }
    }

    private static int countBits(int value) {
        int count = 0;
        while (value != 0) {
            value &= value - 1;
            count++;
        }
        return count;
    }

    // ==================== 输入（只读页：滚轮滚动；点击不消费） ====================

    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        return false; // 槽位卡只读（旧轨点击仅 tooltip，无动作）
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H) {
            this.scroll += dir;
        }
    }

    private net.minecraft.client.gui.FontRenderer font() {
        return this.host.font();
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
