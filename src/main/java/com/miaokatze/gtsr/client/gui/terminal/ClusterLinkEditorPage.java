package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM.Form;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.Unpooled;

/**
 * 集群终端·页 1「链路」（terminal-native-ui N17；旧 MUI2 轨链路视图（git 基线 b4fabb2）自绘移植，
 * 布局 1:1：顶行 y0..14（下拉 200×14 + 两级横幅）/ 左列 x0..272（可用链 10 行 ×34 + 底部反馈行
 * y246 钉底）/ 右列 x280..582（chips 区 88 高 + FSM 推演条 120..146 + 性能组 146 起））。
 *
 * <p>
 * <b>FSM 整体移植</b>（删前逐函数对照旧 MUI2 轨链路视图，语义逐字、绘制轨适配）：
 * stagedOrdinals / stagingDirty / displayOrdinals / ensureStaged / stageAppend(≤16) / stageMove /
 * stageRemove / stageClear / attemptSave（客户端 {@code isValidStructure} 纯函数预校验通过才发
 * SAVE_CHAIN）/ lastRejectAt 1500ms 拒绝反馈 / 快照追平自动清脏（checkSnapshotCaughtUp）。
 * 反馈行优先级：模块未关联（灰）&lt; 保存被拒·链无效（红）&lt; 暂存未保存更改（橙）&lt; 已授权可执行（绿）。
 *
 * <p>
 * <b>live 每帧重读</b>：chips/横幅/反馈行/锁因/性能组全部由 draw 每帧读
 * {@link ClusterTerminalClientCache}（旧轨「KEY_LE_CHAIN 变化重建 chips」的监听重建在自绘轨
 * 天然由每帧重绘承载，暂存态 {@code stagingDirty} 期间展示本地 staged 副本不被快照覆盖——
 * displayOrdinals 分流保留）。编辑四类动作只改本地暂存不发 C2S；保存按钮统一下发
 * SAVE_CHAIN（[len int][ordinal int×len]）。列表滚动偏移自持（防回顶等价语义）。
 */
@SideOnly(Side.CLIENT)
final class ClusterLinkEditorPage implements ClusterPage {

    /** 顶行高（下拉 + 横幅）。 */
    private static final int DROPDOWN_W = 200;
    /** 左列宽。 */
    private static final int LEFT_W = 272;
    /** 右列起点与宽（页内坐标，合计 = 582 内容宽）。 */
    private static final int RIGHT_X = 280;
    private static final int RIGHT_W = 302;
    /** 列标题偏移。 */
    private static final int TITLE_DY = 5;
    /** 链步行区：起始偏移与行距（修订 FC 行高 ×2：34 = 33 按钮高 + 1 行距）。 */
    private static final int LINKS_DY = 28;
    private static final int LINK_PITCH = 34;
    /** 左列底部同步反馈行高（Y 按 contentH 钉底）。 */
    private static final int FEEDBACK_H = 12;
    /** 右列：chips 区。 */
    private static final int CHIPS_DY = 28;
    private static final int CHIPS_H = 88;
    /** 右列：FSM 推演条。 */
    private static final int FLOW_DY = CHIPS_DY + CHIPS_H + 4;
    private static final int FLOW_H = 26;
    /** 右列：性能头与面板。 */
    private static final int FOLD_DY = FLOW_DY + FLOW_H + 4;
    private static final int PERF_DY = FOLD_DY + 15;
    /** 同步反馈：待应用窗口（ms，本地编辑后等待 S2C 回流）。 */
    private static final long APPLY_WINDOW_MS = 1500L;

    /** 链步枚举缓存（ordinal 即 KEY_LE_LOCK 各段下标）。 */
    private static final ChainLink[] LINKS = ChainLink.values();

    private final GuiClusterTerminalScreen host;
    /** 可用链列表滚动偏移（自持）。 */
    private int linksScroll;
    /** chips 滚动偏移（自持）。 */
    private int chipsScroll;
    /** 下拉菜单展开态。 */
    private boolean dropdownOpen;
    /** 同步反馈：最近一次客户端拒绝时间戳（0=无；锁定点击与保存校验失败共用）。 */
    private long lastRejectAt;
    /** 本地暂存链：干净态恒跟随服务器快照（见 {@link #displayOrdinals}），dirty 期间为独立编辑副本不被覆盖。 */
    private List<Integer> stagedOrdinals = new ArrayList<>();
    /** 暂存脏标记：首个本地编辑置位；服务器快照追平暂存（保存生效回流）时自动清除。 */
    private boolean stagingDirty = false;

    ClusterLinkEditorPage(GuiClusterTerminalScreen host) {
        this.host = host;
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        int feedbackDy = GuiClusterTerminalScreen.CONTENT_H - FEEDBACK_H;
        drawTopRow(ox, oy);
        drawLinkColumn(ox, oy, mx, my, z, feedbackDy);
        drawChainColumn(ox, oy, mx, my, z, feedbackDy);
    }

    // ==================== 顶行：下拉 + 两级有效性横幅 ====================

    private void drawTopRow(int ox, int oy) {
        // 下拉头（chip 承载；展开态 chip_active）
        GtsrGuiDrawing.drawNineSlice(
            this.dropdownOpen ? GtsrGuiTextures.CHIP_ACTIVE : GtsrGuiTextures.CHIP_NORMAL,
            4,
            ox,
            oy,
            DROPDOWN_W,
            14,
            this.host.zLevel());
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            GtsrGuiList.ellipsis(font(), formatUnitOption(selectedDisplayIndex()), (int) ((DROPDOWN_W - 12) / 0.75f)),
            ox + 5,
            oy + 4,
            0.75f,
            GtsrGuiPalette.TEXT_BODY);
        // 两级横幅（恒渲染单行动态）：结构有效（FSM 终态）+ 当前可执行（服务端逐 link 查询 + 失败步）
        GuiClusterTerminalScreen
            .drawScaledText(font(), bannerText(), ox + DROPDOWN_W + 12, oy + 2, 0.75f, GtsrGuiPalette.TEXT_BODY);
        // 展开的选项菜单（页剪刀内，向下展开）
        if (this.dropdownOpen) {
            int unitCount = unitSegments().length;
            int optionCount = Math.max(1, unitCount);
            for (int i = 0; i < optionCount; i++) {
                int myY = oy + 16 + i * 13;
                GuiClusterTerminalScreen.fillRect(ox, myY, DROPDOWN_W, 12, this.host.zLevel(), 0xF0202024);
                GuiClusterTerminalScreen.drawScaledText(
                    font(),
                    GtsrGuiList
                        .ellipsis(font(), formatUnitOption(i >= unitCount ? -1 : i), (int) ((DROPDOWN_W - 8) / 0.7f)),
                    ox + 4,
                    myY + 3,
                    0.7f,
                    GtsrGuiPalette.TEXT_BODY);
            }
        }
    }

    private int selectedDisplayIndex() {
        int unitCount = unitSegments().length;
        if (unitCount == 0) return -1;
        return Math
            .max(0, Math.min(unitCount - 1, ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SEL_LOGI, 0)));
    }

    /** 下拉选择：仅正向分发 C2S（服务端复核后经 KEY_SEL_LOGI 推回权威值）。 */
    private void onUnitSelected(int idx) {
        if (idx >= 0) {
            this.host.clusterAction(ClusterTerminalActions.SELECT_LOGISTICS, intPayload(idx));
        }
    }

    /** 下拉选项文案：物流模块 @段N · 已关联/未关联 · 已授权/未授权（flags 见 KEY_LE_UNITS）。 */
    private String formatUnitOption(int idx) {
        int[] segments = unitSegments();
        if (idx < 0 || idx >= segments.length) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.link.unit.none");
        }
        int flags = unitFlags()[idx];
        boolean connected = (flags & 0x01) != 0;
        boolean powered = (flags & 0x04) != 0;
        return EnumChatFormatting.WHITE + tr("gtsr.gui.cluster.unit_type.logistics")
            + EnumChatFormatting.WHITE
            + String.format(tr("gtsr.gui.cluster.editor.segment"), segments[idx])
            + " · "
            + (connected ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.link.unit.linked")
                : EnumChatFormatting.RED + tr("gtsr.cluster.gui.link.unit.unlinked"))
            + " · "
            + (powered ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.link.unit.authorized")
                : EnumChatFormatting.RED + tr("gtsr.cluster.gui.link.unit.unauthorized"));
    }

    /** 物流单元段号数组（KEY_LE_UNITS 解析缓存直读）。 */
    private int[] unitSegments() {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_UNITS, "");
        List<Integer> segs = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        parseUnits(encoded, segs, flags);
        int[] out = new int[segs.size()];
        for (int i = 0; i < out.length; i++) out[i] = segs.get(i);
        return out;
    }

    /** 物流单元 flags 数组（与 unitSegments 同解析）。 */
    private int[] unitFlags() {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_UNITS, "");
        List<Integer> segs = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        parseUnits(encoded, segs, flags);
        int[] out = new int[flags.size()];
        for (int i = 0; i < out.length; i++) out[i] = flags.get(i);
        return out;
    }

    /** 解析 "seg:flags,seg:flags,..."（畸形段跳过）。 */
    private static void parseUnits(String encoded, List<Integer> segs, List<Integer> flags) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String entry : encoded.split(",", -1)) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            try {
                segs.add(
                    Integer.parseInt(
                        entry.substring(0, colon)
                            .trim()));
                flags.add(
                    Integer.parseInt(
                        entry.substring(colon + 1)
                            .trim()));
            } catch (NumberFormatException ignored) {
                // 畸形段跳过（segs/flags 已加项需成对——异常前不添加）
            }
        }
    }

    /** 两级横幅文案：结构（FSM 终态，绿/红）+ 当前（服务端可执行查询，绿/红+失败步名）。 */
    private String bannerText() {
        int exec = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LE_EXEC, 0);
        boolean structValid = exec >= 1;
        StringBuilder sb = new StringBuilder();
        sb.append(
            structValid ? EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.link.banner.struct_ok")
                : EnumChatFormatting.RED + "✖ " + tr("gtsr.cluster.gui.link.banner.struct_bad"));
        sb.append(EnumChatFormatting.WHITE)
            .append(" | ");
        if (exec == 2) {
            sb.append(EnumChatFormatting.GREEN)
                .append("✔ ")
                .append(tr("gtsr.cluster.gui.link.banner.exec_ok"));
        } else {
            int fail = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_LE_FAIL, 0);
            String failName = fail > 0 && fail <= LINKS.length ? tr(LINKS[fail - 1].getLangKey()) : "--";
            sb.append(EnumChatFormatting.RED)
                .append("✖ ")
                .append(String.format(tr("gtsr.cluster.gui.link.banner.exec_bad"), failName));
        }
        return sb.toString();
    }

    // ==================== 左列：可用链 + 同步反馈（无预设） ====================

    private void drawLinkColumn(int ox, int oy, int mx, int my, float z, int feedbackDy) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.link.avail"),
            ox,
            oy + TITLE_DY,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        // 可用链滚动区（修订 FC）：10 行 ×34 超列区剩余高，滚动偏移自持
        int listH = feedbackDy - LINKS_DY - 2;
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, ox, oy + LINKS_DY, LEFT_W, listH, z);
        int maxScroll = Math.max(0, LINKS.length - listH / LINK_PITCH);
        if (this.linksScroll > maxScroll) this.linksScroll = maxScroll;
        if (this.linksScroll < 0) this.linksScroll = 0;
        for (int i = 0; i < LINKS.length; i++) {
            int rowY = oy + LINKS_DY + 2 + (i - this.linksScroll) * LINK_PITCH;
            if (rowY + LINK_PITCH - 1 <= oy + LINKS_DY || rowY >= oy + LINKS_DY + listH) continue;
            drawLinkRow(LINKS[i], i, ox + 2, rowY, LEFT_W - 4, mx, my);
        }
        // 同步反馈行 + 链长计数（动态，钉底）
        GuiClusterTerminalScreen
            .drawScaledText(font(), feedbackText(), ox, oy + feedbackDy, 0.65f, GtsrGuiPalette.TEXT_BODY);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.WHITE + String
                .format(tr("gtsr.cluster.gui.link.chain.len"), displayOrdinals().size(), ClusterParams.CHAIN_MAX_LINKS),
            ox + 150,
            oy + feedbackDy,
            0.65f,
            GtsrGuiPalette.TEXT_MUTED);
    }

    /**
     * 单个链步行（恒渲染——禁用行灰字+锁因仍在）：两行文本（名称+在链×N / 基准秒+介质需求+锁因红字），
     * 可用点击本地暂存追加（决策7：不发 C2S），锁定点击或链长已满本地拒绝（反馈行提示）。
     */
    private void drawLinkRow(ChainLink link, int index, int x, int y, int w, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + LINK_PITCH - 1;
        if (hovered) {
            GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.ROW_HOVER, 4, x, y, w, LINK_PITCH - 1, this.host.zLevel());
        }
        String[] overlay = formatLinkOverlay(link).split("\n", -1);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            GtsrGuiList.ellipsis(font(), overlay[0], (int) (w / 0.75f)),
            x + 3,
            y + 3,
            0.75f,
            GtsrGuiPalette.TEXT_BODY);
        if (overlay.length > 1) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                GtsrGuiList.ellipsis(font(), overlay[1], (int) (w / 0.7f)),
                x + 3,
                y + 18,
                0.7f,
                GtsrGuiPalette.TEXT_BODY);
        }
        if (hovered) {
            List<String> tip = new ArrayList<String>();
            tip.add(tr("gtsr.gui.cluster.chain.append"));
            int kind = lockKind(link.ordinal());
            if (kind != 0) tip.add(EnumChatFormatting.RED + lockReasonText(link, kind));
            this.host.requestTooltip("le.link" + index, tip);
        }
    }

    /** 链步行 overlay：首行 名称 + 在链 ×N（绿）；次行 基准秒 + 介质需求 + 锁因（红）。 */
    private String formatLinkOverlay(ChainLink link) {
        boolean available = lockKind(link.ordinal()) == 0;
        EnumChatFormatting base = available ? EnumChatFormatting.WHITE : EnumChatFormatting.WHITE;
        StringBuilder first = new StringBuilder(base.toString()).append(tr(link.getLangKey()));
        int count = countInChain(link.ordinal());
        if (count > 0) {
            first.append("  ")
                .append(EnumChatFormatting.GREEN)
                .append(String.format(tr("gtsr.gui.cluster.chain.in_chain"), count));
        }
        StringBuilder second = new StringBuilder(
            available ? EnumChatFormatting.WHITE.toString() : EnumChatFormatting.WHITE.toString())
                .append(String.format(tr("gtsr.gui.cluster.editor.link_seconds"), link.getBaseSecondsPrecise()))
                .append(" · ")
                .append(mediumText(link));
        int kind = lockKind(link.ordinal());
        if (kind != 0) {
            second.append(" · ")
                .append(EnumChatFormatting.RED)
                .append(lockReasonText(link, kind));
        }
        return first + "\n" + second;
    }

    /** 介质需求短文案（每批 1000L 水/化浴液、需持续通电、简易水洗）。 */
    private static String mediumText(ChainLink link) {
        return switch (link) {
            case ORE_WASH -> tr("gtsr.cluster.gui.link.need_water");
            case CHEM_BATH -> tr("gtsr.cluster.gui.link.need_chem");
            case MAGNETIC_SEPARATOR, THERMOCENTRIFUGE -> tr("gtsr.cluster.gui.link.need_power");
            case SIMPLE_WASH -> tr("gtsr.cluster.gui.link.need_simple_wash");
            default -> tr("gtsr.cluster.gui.link.no_medium");
        };
    }

    /** 锁因文案（kind → 既有锁定 key；module 类带所需单元名填充 %s）。 */
    private static String lockReasonText(ChainLink link, int kind) {
        return switch (kind) {
            case 1 -> tr("gtsr.gui.cluster.link.locked_simple_wash");
            case 2 -> String.format(tr("gtsr.gui.cluster.link.locked_module"), tr(unitTypeKey(link)));
            case 3 -> tr("gtsr.gui.cluster.link.locked_unformed");
            default -> tr("gtsr.gui.cluster.link.locked_power");
        };
    }

    /** 链步所需工作单元类型 lang key（与 ChainLink.getRequiredUnitClass 的映射一一对应，客户端安全）。 */
    private static String unitTypeKey(ChainLink link) {
        return switch (link) {
            case CRUSH, HAMMER -> "gtsr.gui.cluster.unit_type.crusher";
            case SIMPLE_WASH, ORE_WASH, CHEM_BATH -> "gtsr.gui.cluster.unit_type.ore_washer";
            case CENTRIFUGE -> "gtsr.gui.cluster.unit_type.centrifuge";
            case THERMOCENTRIFUGE -> "gtsr.gui.cluster.unit_type.thermal_centrifuge";
            case SIFTER -> "gtsr.gui.cluster.unit_type.sifter";
            case MAGNETIC_SEPARATOR -> "gtsr.gui.cluster.unit_type.magnetic_separator";
            case FURNACE -> "gtsr.gui.cluster.unit_type.furnace";
        };
    }

    /**
     * 同步反馈行（优先级从高到低，决策9）：模块未关联（灰）/ 保存被拒·链无效（红，复用 lastRejectAt
     * 窗口）/ 暂存未保存更改（橙）/ 已授权可执行（绿——保存生效后快照追平暂存即回到此绿态确认）。
     * 本方法每帧求值，兼作快照追平检测点（相等才清 dirty，显示内容不变无渲染抖动）。
     */
    private String feedbackText() {
        checkSnapshotCaughtUp();
        long now = System.currentTimeMillis();
        if (unitSegments().length == 0) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.link.unit.none");
        }
        if (now - this.lastRejectAt < APPLY_WINDOW_MS) {
            return EnumChatFormatting.RED + "✖ " + tr("gtsr.cluster.gui.link.chain.invalid");
        }
        if (this.stagingDirty) {
            return EnumChatFormatting.GOLD + tr("gtsr.cluster.gui.link.chain.unsaved");
        }
        return EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.link.sync.ok");
    }

    /**
     * 快照追平检测（幂等）：仅当暂存脏且服务器 KEY_LE_CHAIN 快照与本地暂存完全相等
     * （SAVE_CHAIN 已被服务端接受并回推）时清脏；不等则保持 dirty 继续等待（服务端静默拒绝时
     * 橙态持续，用户可修改后重试保存）。
     */
    private void checkSnapshotCaughtUp() {
        if (!this.stagingDirty) return;
        if (chainOrdinals().equals(this.stagedOrdinals)) {
            this.stagingDirty = false;
        }
    }

    // ==================== 右列：当前有序链 + FSM 推演 + 性能详情 ====================

    private void drawChainColumn(int ox, int oy, int mx, int my, float z, int feedbackDy) {
        final int rx = ox + RIGHT_X;
        // 标题 + 保存/清空钮（决策7：客户端预校验通过才发 SAVE_CHAIN）
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.link.chain"),
            rx,
            oy + TITLE_DY,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        drawSmallButton(rx + RIGHT_W - 86, oy + TITLE_DY - 2, 42, 12, tr("gtsr.cluster.gui.link.chain.save"), mx, my);
        drawSmallButton(rx + RIGHT_W - 42, oy + TITLE_DY - 2, 42, 12, tr("gtsr.cluster.gui.link.chain.clear"), mx, my);

        // chips 滚动列表（滚动偏移自持；暂存脏期间显示本地 staged 副本）
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, rx, oy + CHIPS_DY, RIGHT_W, CHIPS_H, z);
        List<Integer> ordinals = displayOrdinals();
        int chipsInner = CHIPS_H - 4;
        int maxChipsScroll = Math.max(0, ordinals.size() - chipsInner / 15);
        if (this.chipsScroll > maxChipsScroll) this.chipsScroll = maxChipsScroll;
        if (this.chipsScroll < 0) this.chipsScroll = 0;
        if (ordinals.isEmpty()) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.link.chain.empty"),
                rx + 4,
                oy + CHIPS_DY + 4,
                0.8f,
                GtsrGuiPalette.TEXT_MUTED);
        } else {
            for (int i = 0; i < ordinals.size(); i++) {
                int rowY = oy + CHIPS_DY + 2 + (i - this.chipsScroll) * 15;
                if (rowY + 14 <= oy + CHIPS_DY || rowY >= oy + CHIPS_DY + CHIPS_H) continue;
                drawChipRow(ordinals.get(i), i, rx + 2, rowY, mx, my);
            }
        }

        // FSM 推演条：原矿 →(链步)→ 形态 →…→ 终态（按宽折行）
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, rx, oy + FLOW_DY, RIGHT_W, FLOW_H, z);
        String flow = formatFlowLine();
        if (!flow.isEmpty()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(rx + 3, oy + FLOW_DY + 2, 0.0f);
            GL11.glScalef(0.6f, 0.6f, 1.0f);
            font().drawSplitString(flow, 0, 0, (int) ((RIGHT_W - 6) / 0.6f), GtsrGuiPalette.TEXT_BODY);
            GL11.glPopMatrix();
        }

        // 性能详情常驻显示（服务端真值，×100 定点解码；每帧重读缓存）
        GuiClusterTerminalScreen
            .drawScaledText(font(), tr("gtsr.cluster.gui.link.perf"), rx, oy + FOLD_DY, 0.7f, GtsrGuiPalette.TEXT_BODY);
        int perfH = Math.max(30, feedbackDy - PERF_DY);
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, rx, oy + PERF_DY, RIGHT_W, perfH, z);
        String[] perfLines = perfLines();
        for (int i = 0; i < perfLines.length; i++) {
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                GtsrGuiList.ellipsis(font(), perfLines[i], (int) ((RIGHT_W - 6) / 0.7f)),
                rx + 3,
                oy + PERF_DY + 3 + i * 11,
                0.7f,
                GtsrGuiPalette.TEXT_BODY);
        }
    }

    /** 小型 chip 按钮（42×12 保存/清空钮；hover 亮态）。 */
    private void drawSmallButton(int x, int y, int w, int h, String label, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        GtsrGuiDrawing.drawNineSlice(
            hovered ? GtsrGuiTextures.CHIP_ACTIVE : GtsrGuiTextures.CHIP_NORMAL,
            4,
            x,
            y,
            w,
            h,
            this.host.zLevel());
        int textW = GuiClusterTerminalScreen.scaledTextWidth(font(), label, 0.7f);
        GuiClusterTerminalScreen
            .drawScaledText(font(), label, x + (w - textW) / 2, y + (h - 8) / 2 + 1, 0.7f, GtsrGuiPalette.TEXT_BODY);
    }

    /** 单 chip 行：序号 / 名称 / 实际耗时（基准×tier÷同类模块数，显示口径） / ◀ ▶ ✖。 */
    private void drawChipRow(int linkOrdinal, int index, int x, int y, int mx, int my) {
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + (index + 1) + ".",
            x + 2,
            y + 3,
            0.75f,
            GtsrGuiPalette.TEXT_ACCENT);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            GtsrGuiList.ellipsis(font(), EnumChatFormatting.WHITE + tr(LINKS[linkOrdinal].getLangKey()), 44),
            x + 18,
            y + 4,
            0.65f,
            GtsrGuiPalette.TEXT_BODY);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GREEN + stepTimeText(linkOrdinal),
            x + 64,
            y + 4,
            0.6f,
            GtsrGuiPalette.TEXT_BODY);
        drawChipButton(x + 114, y, 14, 13, "◀", mx, my);
        drawChipButton(x + 130, y, 14, 13, "▶", mx, my);
        drawChipButton(x + 146, y, 16, 13, EnumChatFormatting.RED + "✖", mx, my);
    }

    /** chip 行内小钮（◀▶✖；hover 亮态）。 */
    private void drawChipButton(int x, int y, int w, int h, String glyph, int mx, int my) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
        GtsrGuiDrawing.drawNineSlice(
            hovered ? GtsrGuiTextures.CHIP_ACTIVE : GtsrGuiTextures.CHIP_NORMAL,
            4,
            x,
            y,
            w,
            h,
            this.host.zLevel());
        int gw = GuiClusterTerminalScreen.scaledTextWidth(font(), glyph, 0.7f);
        GuiClusterTerminalScreen
            .drawScaledText(font(), glyph, x + (w - gw) / 2, y + (h - 8) / 2, 0.7f, GtsrGuiPalette.TEXT_BODY);
    }

    /** chip 实际耗时显示：base × TIER_TIME_FACTOR[tier] ÷ max(1, 同类模块数)（读 KEY_TIER/KEY_LE_LOCK 缓存）。 */
    private String stepTimeText(int linkOrdinal) {
        int tier = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_TIER, 0);
        double factor = ClusterParams.TIER_TIME_FACTOR[Math.max(0, Math.min(ClusterParams.TIER_COUNT - 1, tier))];
        int[] counts = lockCounts();
        int modules = Math.max(1, linkOrdinal < counts.length ? counts[linkOrdinal] : 0);
        return formatSec(LINKS[linkOrdinal].getBaseSecondsPrecise() * factor / modules) + "s";
    }

    /** FSM 推演条：原矿 →(链步)→ 形态 →…→ 终态（终态绿 + ✓终；末位非终态红）。 */
    private String formatFlowLine() {
        List<Integer> ordinals = displayOrdinals();
        if (ordinals.isEmpty()) {
            return EnumChatFormatting.WHITE + tr("gtsr.cluster.gui.link.chain.empty");
        }
        StringBuilder sb = new StringBuilder();
        Form form = ClusterChainFSM.start();
        sb.append(formTag(form, false));
        for (int i = 0; i < ordinals.size(); i++) {
            Form next = ClusterChainFSM.next(form, LINKS[ordinals.get(i)]);
            boolean last = i == ordinals.size() - 1;
            sb.append(EnumChatFormatting.WHITE)
                .append(" →(")
                .append(EnumChatFormatting.WHITE)
                .append(tr(LINKS[ordinals.get(i)].getLangKey()))
                .append(EnumChatFormatting.WHITE)
                .append(")→ ")
                .append(formTag(next, last));
            form = next;
        }
        return sb.toString();
    }

    private static String formTag(Form form, boolean last) {
        boolean terminal = ClusterChainFSM.isTerminal(form);
        EnumChatFormatting color = terminal ? EnumChatFormatting.GREEN
            : (last ? EnumChatFormatting.RED : EnumChatFormatting.AQUA);
        return color + tr(ClusterChainFSM.formLangKey(form))
            + (terminal ? " ✓" + tr("gtsr.gui.cluster.chain.preview_terminal") : "");
    }

    /** 性能详情 6 行（常驻 ×100 定点真值：耗时/并行/吞吐/本链蒸汽/总蒸汽/实际加权公式；每帧重读）。 */
    private String[] perfLines() {
        int timeRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TIME, 0);
        int parRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_PAR, 0);
        int thruRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_THRU, 0);
        int steamRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_STEAM, 0);
        int totalRaw = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_F_TOTAL, 0);
        String formula = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_F_FORMULA, "0 L/s");
        return new String[] {
            EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.link.perf.time")
                + " = "
                + EnumChatFormatting.GREEN
                + String.format("%.2f", timeRaw / 100.0D)
                + " s",
            EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.link.perf.parallel")
                + " = "
                + EnumChatFormatting.GREEN
                + parRaw,
            EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.link.perf.thru")
                + " = "
                + EnumChatFormatting.GREEN
                + String.format("%.2f", thruRaw / 100.0D)
                + " "
                + tr("gtsr.cluster.gui.card.thru.unit"),
            EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.link.perf.steam")
                + " = "
                + NumberFormatUtil.formatNumber(steamRaw)
                + " L/s",
            EnumChatFormatting.YELLOW + tr("gtsr.cluster.gui.link.perf.steam_total")
                + " = "
                + EnumChatFormatting.RED
                + NumberFormatUtil.formatNumber(totalRaw)
                + " L/s",
            EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.link.perf.formula")
                + " = "
                + EnumChatFormatting.GREEN
                + formula };
    }

    // ==================== 本地暂存编辑（决策7：不发 C2S，保存按钮统一下发；FSM 函数级移植） ====================

    /** 当前展示链：干净态跟随服务器快照（KEY_LE_CHAIN），暂存脏期间为本地编辑副本。 */
    private List<Integer> displayOrdinals() {
        return this.stagingDirty ? this.stagedOrdinals : chainOrdinals();
    }

    /** 干净态首次本地编辑：物化快照副本为独立暂存并置脏（此后不被快照覆盖）。 */
    private void ensureStaged() {
        if (!this.stagingDirty) {
            this.stagedOrdinals = new ArrayList<>(chainOrdinals());
            this.stagingDirty = true;
        }
    }

    /** 暂存链尾追加（保持 CHAIN_MAX_LINKS 上限）。 */
    private void stageAppend(int ordinal) {
        ensureStaged();
        if (this.stagedOrdinals.size() >= ClusterParams.CHAIN_MAX_LINKS) return;
        this.stagedOrdinals.add(ordinal);
    }

    /** 暂存链步位移（-1 左移 / +1 右移；越界安全忽略）。 */
    private void stageMove(int index, int dir) {
        ensureStaged();
        if (index < 0 || index >= this.stagedOrdinals.size()) return;
        int target = index + dir;
        if (target < 0 || target >= this.stagedOrdinals.size()) return;
        Collections.swap(this.stagedOrdinals, index, target);
    }

    /** 按索引删除暂存链步（越界安全忽略）。 */
    private void stageRemove(int index) {
        ensureStaged();
        if (index < 0 || index >= this.stagedOrdinals.size()) return;
        this.stagedOrdinals.remove(index);
    }

    /** 清空暂存链。 */
    private void stageClear() {
        ensureStaged();
        this.stagedOrdinals.clear();
    }

    /**
     * 保存暂存链（决策7/9）：staged 非空且 FSM 结构有效才发 SAVE_CHAIN；否则红字反馈不发包。
     * 结构校验为纯函数客户端安全：按 ordinal 建 {@link LogisticsChain} 后复用
     * {@link LogisticsChain#isValidStructure()}。
     */
    private void attemptSave() {
        List<Integer> staged = displayOrdinals();
        boolean valid = !staged.isEmpty() && isValidStructure(staged);
        if (!valid) {
            this.lastRejectAt = System.currentTimeMillis();
            return;
        }
        int[] ordinals = new int[staged.size()];
        for (int i = 0; i < ordinals.length; i++) {
            ordinals[i] = staged.get(i);
        }
        this.host.clusterAction(ClusterTerminalActions.SAVE_CHAIN, chainPayload(ordinals));
    }

    /** 客户端结构校验（纯函数）：恰好一个终态产物（FSM 终态 ∈ {DUST, INGOT}）。 */
    private static boolean isValidStructure(List<Integer> ordinals) {
        int[] arr = new int[ordinals.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ordinals.get(i);
        }
        return LogisticsChain.fromOrdinalArray(arr)
            .isValidStructure();
    }

    // ==================== 快照读数 ====================

    /** 当前链 ordinal 列表（KEY_LE_CHAIN 解析，越界项丢弃）。 */
    private List<Integer> chainOrdinals() {
        return parseIntList(ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_CHAIN, ""), LINKS.length);
    }

    /** 链步锁因数组（KEY_LE_LOCK 第 1 段 per-link "kind:count"）。 */
    private int[] lockKinds() {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_LOCK, "");
        int[] out = new int[LINKS.length];
        if (encoded.isEmpty()) return out;
        String[] entries = encoded.split(",", -1);
        int n = Math.min(LINKS.length, entries.length);
        for (int i = 0; i < n; i++) {
            int colon = entries[i].indexOf(':');
            if (colon <= 0) continue;
            try {
                out[i] = Integer.parseInt(
                    entries[i].substring(0, colon)
                        .trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private int lockKind(int linkOrdinal) {
        int[] kinds = lockKinds();
        return linkOrdinal >= 0 && linkOrdinal < kinds.length ? kinds[linkOrdinal] : 0;
    }

    /** 链步同类模块计数数组（KEY_LE_LOCK 第 2 段 per-link）。 */
    private int[] lockCounts() {
        String encoded = ClusterTerminalClientCache.getStr(ClusterTerminalData.KEY_LE_LOCK, "");
        int[] out = new int[LINKS.length];
        if (encoded.isEmpty()) return out;
        String[] entries = encoded.split(",", -1);
        int n = Math.min(LINKS.length, entries.length);
        for (int i = 0; i < n; i++) {
            int colon = entries[i].indexOf(':');
            if (colon <= 0) continue;
            try {
                out[i] = Integer.parseInt(
                    entries[i].substring(colon + 1)
                        .trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 「在链 ×N」计数（读展示链：干净态快照 / 暂存脏期间 staged）。 */
    private int countInChain(int linkOrdinal) {
        int count = 0;
        for (int ordinal : displayOrdinals()) {
            if (ordinal == linkOrdinal) count++;
        }
        return count;
    }

    /** 客户端解析变长 int CSV 为列表（越界 [0,bound) 项丢弃，防枚举演进脏数据；旧 parseIntList 移植）。 */
    private static List<Integer> parseIntList(String csv, int bound) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",", -1)) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0 && value < bound) out.add(value);
            } catch (NumberFormatException ignored) {
                // 畸形项丢弃
            }
        }
        return out;
    }

    /** 秒数统一两位小数（含整数值，如 16t 显 0.80s）。 */
    private static String formatSec(double seconds) {
        return String.format("%.2f", seconds);
    }

    // ==================== 输入 ====================

    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        boolean inPage = mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H;
        if (!inPage) return false;
        // 下拉菜单：展开时选项命中优先，其次页内任意点击收起
        if (this.dropdownOpen) {
            int unitCount = unitSegments().length;
            int optionCount = Math.max(1, unitCount);
            for (int i = 0; i < optionCount; i++) {
                int myY = oy + 16 + i * 13;
                if (mx >= ox && mx < ox + DROPDOWN_W && my >= myY && my < myY + 12) {
                    if (i < unitCount) onUnitSelected(i);
                    this.dropdownOpen = false;
                    return true;
                }
            }
            this.dropdownOpen = false;
            return true;
        }
        // 下拉头：展开/收起
        if (mx >= ox && mx < ox + DROPDOWN_W && my >= oy && my < oy + 14) {
            this.dropdownOpen = !this.dropdownOpen;
            return true;
        }
        final int rx = ox + RIGHT_X;
        // 保存钮（客户端预校验通过才发 SAVE_CHAIN）
        if (mx >= rx + RIGHT_W - 86 && mx < rx + RIGHT_W - 44 && my >= oy + TITLE_DY - 2 && my < oy + TITLE_DY + 10) {
            attemptSave();
            return true;
        }
        // 清空钮
        if (mx >= rx + RIGHT_W - 42 && mx < rx + RIGHT_W && my >= oy + TITLE_DY - 2 && my < oy + TITLE_DY + 10) {
            if (!displayOrdinals().isEmpty()) {
                stageClear();
            }
            return true;
        }
        // 可用链行点击：可用且未满 → 暂存追加；锁定点击或链长已满本地拒绝（红字反馈）
        if (mx >= ox && mx < ox + LEFT_W && my >= oy + LINKS_DY) {
            int row = (my - (oy + LINKS_DY + 2)) / LINK_PITCH + this.linksScroll;
            if (row >= 0 && row < LINKS.length) {
                ChainLink link = LINKS[row];
                if (lockKind(link.ordinal()) == 0 && displayOrdinals().size() < ClusterParams.CHAIN_MAX_LINKS) {
                    stageAppend(link.ordinal());
                } else {
                    this.lastRejectAt = System.currentTimeMillis();
                }
                return true;
            }
        }
        // chips 行内钮：◀ ▶ ✖（本地暂存位移/删除）
        int chipsTop = oy + CHIPS_DY;
        if (mx >= rx && mx < rx + RIGHT_W && my >= chipsTop && my < chipsTop + CHIPS_H) {
            List<Integer> ordinals = displayOrdinals();
            int row = (my - (chipsTop + 2)) / 15 + this.chipsScroll;
            if (row >= 0 && row < ordinals.size()) {
                int cx = rx + 2;
                int rowY = chipsTop + 2 + (row - this.chipsScroll) * 15;
                if (my >= rowY && my < rowY + 14) {
                    if (mx >= cx + 114 && mx < cx + 128) {
                        stageMove(row, -1);
                        return true;
                    }
                    if (mx >= cx + 130 && mx < cx + 144) {
                        stageMove(row, 1);
                        return true;
                    }
                    if (mx >= cx + 146 && mx < cx + 162) {
                        stageRemove(row);
                        return true;
                    }
                }
            }
            return true; // chips 区内其余点击消费防穿透
        }
        return false;
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H) {
            this.linksScroll += dir;
            this.chipsScroll += dir;
        }
    }

    // ==================== payload 构造 ====================

    /** SAVE_CHAIN payload：[len int][ordinal int × len]（服务端读序逐字一致）。 */
    private static byte[] chainPayload(int[] ordinals) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(4 + ordinals.length * 4));
        pb.writeInt(ordinals.length);
        for (int ordinal : ordinals) {
            pb.writeInt(ordinal);
        }
        return readAll(pb);
    }

    /** SELECT_LOGISTICS payload：[idx int]。 */
    private static byte[] intPayload(int idx) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(4));
        pb.writeInt(idx);
        return readAll(pb);
    }

    private static byte[] readAll(PacketBuffer pb) {
        byte[] payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
        return payload;
    }

    private net.minecraft.client.gui.FontRenderer font() {
        return this.host.font();
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
