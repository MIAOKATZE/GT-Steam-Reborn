package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;
import com.miaokatze.gtsr.common.terminal.TcdsTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import io.netty.buffer.Unpooled;

/**
 * TCDS（热化学致密蒸汽发生器）流量终端（PLAN：TCDS 流量设定迁机器终端 GUI，轨 A 原生自绘，
 * panel_hub_status 400×240 面板 + GuiTerminalBase 五件套风格，枢纽屏同构）。
 * <p>
 * 行为映射（旧 MUI 流量输入行 MTEThermoChemicalDenseSteamGeneratorGui:110-126 → 本屏）：
 * <ul>
 * <li>流量 {@link net.minecraft.client.gui.GuiTextField} 客户端纯本地编辑、确认按钮才发包
 * （复用基类 {@link GuiTerminalBase#renameField} 载体，枢纽重命名框同款纪律）；</li>
 * <li><b>快照回填纪律（照抄 GuiCacheHubStatusScreen.renameField）</b>：轮询快照仅在用户
 * 未开始编辑时回填服务端设定流量；本地编辑标记（{@link #flowEdited}）置位后确认前
 * 快照不覆盖编辑中值，确认动作清除标记恢复回显；</li>
 * <li>确认按钮 → {@link TcdsTerminalData#ACTION_SET_FLOW}（varint flow）→ 服务端
 * {@code setFlow} 权威钳 ≥1；客户端解析失败按 0 发包（不做客户端安全假设）；</li>
 * <li>只读信息两行：设定流量（快照值）+ 理论最大热量（服务端 heatCapDisplay 口径串）。</li>
 * </ul>
 * 客户端快照缓存内联本类静态仓（terminal 轨每 uiType 一仓范式的最小化变体，不加新缓存类）：
 * 锚点匹配 + snapshotVersion 单调门控 + 整体替换防撕裂 + volatile 兜底，写入仅
 * {@link com.miaokatze.gtsr.client.terminal.TerminalClientPacketSink} 主线程切线程后调用。
 */
@SideOnly(Side.CLIENT)
public class GuiTcdsTerminalScreen extends GuiTerminalBase {

    // 按钮 id（本 GUI 内部路由用）
    private static final int BTN_CONFIRM = 1;

    /** 确认按钮宽（枢纽 BTN_RENAME 同款 64×14） */
    private static final int CONFIRM_BTN_W = 64;

    /** 只读信息行起始 y（面板相对；列头带之下，枢纽列表区顶部同节奏） */
    private static final int INFO_ROW_Y = 32;
    /** 只读信息行距（两行标签+值） */
    private static final int INFO_ROW_STEP = 16;
    /** 只读值列 x（面板相对：标签 @8 起，值固定列对齐 @150） */
    private static final int INFO_VALUE_X = 150;

    // ==================== 公式区行模型常量（结构化重排；输入行 y88 之下 8px 起步） ====================

    /** 公式区首行 y（面板相对 = 96，与输入行净距 8px） */
    private static final int FORMULA_Y = INFO_ROW_Y + INFO_ROW_STEP * 4;
    /** 公式区物理行高（不折行，超宽 ellipsis 兜底） */
    private static final int FORMULA_ROW_H = 11;
    /** 公式区正文缩放（组头/KV 行） */
    private static final float FORMULA_BODY_SCALE = 0.7f;
    /** 公式区推导小字缩放 */
    private static final float FORMULA_SMALL_SCALE = 0.6f;
    /** 推导小字缩进（面板相对，列左缘 x8 之内再缩） */
    private static final int FORMULA_SMALL_INDENT = 12;
    /** 公式区右缘（面板相对；净宽 384，x∈[8,392]） */
    private static final int FORMULA_RIGHT = LIST_X + LIST_W;

    // 宽度红线（构造性保证：全部入绘文本必为 ellipsis 产物；cap 为 1.0f 口径 = 面板净宽 ÷ 缩放）
    /** 组头 cap（384/0.7f≈548；§l 加粗偏移按组头短标签惯例由右缘余量吸收） */
    private static final int CAP_HEADER = (int) (LIST_W / FORMULA_BODY_SCALE);
    /** KV 标签 cap（(150-8-4)/0.7f≈197，标签与值列间留 4px 空隙） */
    private static final int CAP_KV_LABEL = (int) ((INFO_VALUE_X - LIST_X - 4) / FORMULA_BODY_SCALE);
    /** KV 值 cap（(392-150)/0.7f≈345） */
    private static final int CAP_KV_VALUE = (int) ((FORMULA_RIGHT - INFO_VALUE_X) / FORMULA_BODY_SCALE);
    /** 推导小字 cap（(384-12)/0.6f≈620） */
    private static final int CAP_SMALL = (int) ((LIST_W - FORMULA_SMALL_INDENT) / FORMULA_SMALL_SCALE);

    // ==================== 客户端静态快照缓存（sink 主线程写） ====================

    /** 缓存条目（不可变）：锚点 pos+dim + 快照版本 + 解码后显示数据 */
    private static final class CachedSnapshot {

        final int x, y, z, dim;
        final int version;
        final TcdsTerminalData.Snapshot data;

        CachedSnapshot(int x, int y, int z, int dim, int version, TcdsTerminalData.Snapshot data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.version = version;
            this.data = data;
        }

        /** @return 本快照锚点是否为给定 pos+dim（GUI 渲染前置校验） */
        boolean matchesAnchor(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }

    private static volatile CachedSnapshot cache;

    /**
     * 收到 valid=true 回包（TerminalClientPacketSink 主线程调用）：解码失败整包丢弃
     * （保留旧快照防撕裂）；同锚点按 snapshotVersion 单调门控拒绝迟到旧包；整体替换。
     */
    public static void accept(PacketTerminalData msg) {
        TcdsTerminalData.Snapshot data = TcdsTerminalData.readSnapshot(msg.getPayload());
        if (data == null) {
            return; // 解码失败：整包丢弃
        }
        CachedSnapshot prev = cache;
        if (prev != null && prev.matchesAnchor(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())
            && msg.getSnapshotVersion() < prev.version) {
            return; // 迟到旧包：单调门控拒绝
        }
        cache = new CachedSnapshot(msg.getX(), msg.getY(), msg.getZ(), msg.getDim(), msg.getSnapshotVersion(), data);
    }

    /** 失效清理（valid=false 回包）：仅当缓存锚点与回包锚点一致时清空（跨锚点旧失效包不清新仓） */
    public static void invalidate(PacketTerminalData msg) {
        CachedSnapshot prev = cache;
        if (prev != null && prev.matchesAnchor(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) {
            cache = null;
        }
    }

    // ==================== 实例状态 ====================

    /**
     * 输入框本地编辑标记：用户在流量框键入/粘贴后置位（确认前轮询快照不覆盖编辑中值，
     * 照抄 GuiCacheHubStatusScreen.renameField 纪律）；确认动作清除恢复服务端值回填。
     */
    private boolean flowEdited = false;

    public GuiTcdsTerminalScreen(int x, int y, int z, int dim) {
        super(x, y, z, dim);
    }

    // ==================== 基类差异点 ====================

    @Override
    protected TerminalUiType uiType() {
        return TerminalUiType.TCDS;
    }

    /** 目标机器类（客户端锚点复核口径，与 open 包第二校验同表） */
    @Override
    protected Class<? extends IMetaTileEntity> targetMachineClass() {
        return MTEThermoChemicalDenseSteamGenerator.class;
    }

    @Override
    protected String titleText() {
        return StatCollector.translateToLocal("gtsr.tcds_terminal.title");
    }

    /** 无节点计数语义（标题栏右位留空，基类画空串无绘制） */
    @Override
    protected String countText() {
        return "";
    }

    @Override
    protected List<String> tooltipLinesFor(GuiButton button) {
        if (button.id == BTN_CONFIRM) {
            return lines(StatCollector.translateToLocal("gtsr.tcds_terminal.confirm_hint"));
        }
        return null;
    }

    // ==================== 结构与输入 ====================

    @Override
    public void initGui() {
        super.initGui();
        // 流量输入框复用基类 renameField 载体（客户端纯本地、确认才发包）；限长 9 位数字
        // （int 域安全，超长输入靠 maxLength 拦截，解析兜底仍走服务端钳制）
        this.renameField.setMaxStringLength(9);
        // TCDS 专用布局：输入为第三行（标签 y64、文本框 y74），不改基类共用常量。
        this.renameField.yPosition = this.guiTop + INFO_ROW_Y + INFO_ROW_STEP * 2 + 10;
        // 确认按钮与输入框同行，保持输入框右侧直接确认。
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_CONFIRM,
                this.guiLeft + 152,
                this.renameField.yPosition,
                CONFIRM_BTN_W,
                14,
                ellipsized("gtsr.tcds_terminal.confirm", CONFIRM_BTN_W)));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.renameField != null) {
            this.renameField.updateCursorCounter();
        }
        // 快照回填（快照回显纪律）：仅在用户未开始本地编辑时回填服务端设定流量；
        // 文本一致时不重写（避免每轮询周期无谓复位光标）
        if (this.renameField != null && !this.flowEdited) {
            TcdsTerminalData.Snapshot cur = matchedSnapshot();
            if (cur != null) {
                String serverText = String.valueOf(cur.flow);
                if (!serverText.equals(this.renameField.getText())) {
                    this.renameField.setText(serverText);
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.renameField != null) {
            this.renameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 与 GuiTerminalBase.keyTyped 同序（输入框焦点内先消费），额外登记本地编辑标记：
        // 消费任意键入（含粘贴）即置位，确认前轮询快照停止回填（不覆盖编辑中值）
        final boolean consumed = this.renameField != null && this.renameField.isFocused()
            && this.renameField.textboxKeyTyped(typedChar, keyCode);
        if (consumed) {
            this.flowEdited = true;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_CONFIRM) {
            // 客户端仅解析（失败/空按 0 发），服务端 setFlow 权威钳 ≥1（不在客户端做安全假设）
            this.sendAction(TcdsTerminalData.ACTION_SET_FLOW, varintPayload(parseFlowInput()));
            this.flowEdited = false; // 确认后恢复服务端值回填（即时刷新快照即回显新设定值）
            this.requestImmediateRefresh();
        }
    }

    /** 输入框文本 → int（trim 后解析；空/非数字返回 0，负数原样发包，均由服务端钳 ≥1 收口） */
    private int parseFlowInput() {
        if (this.renameField == null) {
            return 0;
        }
        try {
            return Integer.parseInt(
                this.renameField.getText()
                    .trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 动作 payload 构造：varint flow（服务端 PacketBuffer.readVarIntFromBuffer 对称解码） */
    private static byte[] varintPayload(int value) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(5));
        pb.writeVarIntToBuffer(value);
        byte[] payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
        return payload;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks); // 暗底 + panel_hub_status 整版 + 标题 + 按钮

        // 只读信息两行（快照活取；无快照/复核失败留白等待下轮轮询）
        TcdsTerminalData.Snapshot cur = matchedSnapshot();
        if (cur != null) {
            drawLabelValue(
                "gtsr.tcds_terminal.flow_label",
                NumberFormatUtil.formatNumber(cur.flow) + " L/t",
                this.guiTop + INFO_ROW_Y,
                GtsrGuiPalette.TEXT_ACCENT);
            drawLabelValue(
                "gtsr.tcds_terminal.heat_cap_label",
                cur.heatCap,
                this.guiTop + INFO_ROW_Y + INFO_ROW_STEP,
                GtsrGuiPalette.TEXT_BODY);
        }

        // 输入区标签行（输入框上方）
        this.fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal("gtsr.tcds_terminal.input_label"),
            this.guiLeft + 8,
            this.guiTop + INFO_ROW_Y + INFO_ROW_STEP * 2,
            GtsrGuiPalette.TEXT_LABEL);
        this.drawRenameField();
        final List<FormulaRow> formulaRows = drawFormulaLines(cur);

        // 输入框 hover tooltip（500ms）：输入说明与非法值兜底语义
        if (this.renameField != null && mouseX >= this.renameField.xPosition
            && mouseY >= this.renameField.yPosition
            && mouseX < this.renameField.xPosition + this.renameField.getWidth()
            && mouseY < this.renameField.yPosition + RENAME_FIELD_H) {
            this.hoverTooltip(
                "field",
                lines(StatCollector.translateToLocal("gtsr.tcds_terminal.input_hint")),
                mouseX,
                mouseY);
        } else if (formulaRows == null || !formulaTooltipHit(formulaRows, mouseX, mouseY)) {
            // 公式区逐行 tooltip 未命中（含无快照/区域外）：按钮兜底（原序保留）
            this.drawButtonTooltips(mouseX, mouseY);
        }
    }

    /**
     * 公式区绘制（结构化行模型，替代旧 8 行单色平铺）：组头（金粗 0.7f @x8）/ KV（标签
     * TEXT_LABEL @8、值语义色 @150 对齐）/ 推导小字（0.6f 缩进 12 TEXT_MUTED）三类物理行，
     * 固定 11px 节奏不折行，入绘文本全部经 ellipsis 定宽（红线见 CAP_* 常量）。
     *
     * @return 本帧行集（无快照为 null；供逐行 tooltip 命中复用，避免二次构建）
     */
    private List<FormulaRow> drawFormulaLines(TcdsTerminalData.Snapshot snap) {
        if (snap == null) return null;
        List<FormulaRow> rows = buildFormulaRows(snap);
        int y = this.guiTop + FORMULA_Y;
        for (FormulaRow row : rows) {
            if (row.kind == FormulaRow.KIND_KV) {
                drawScaledText(
                    this.fontRendererObj,
                    row.label,
                    this.guiLeft + LIST_X,
                    y,
                    FORMULA_BODY_SCALE,
                    GtsrGuiPalette.TEXT_LABEL);
                drawScaledText(
                    this.fontRendererObj,
                    row.value,
                    this.guiLeft + INFO_VALUE_X,
                    y,
                    FORMULA_BODY_SCALE,
                    row.valueColor);
            } else if (row.kind == FormulaRow.KIND_HEADER) {
                drawScaledText(
                    this.fontRendererObj,
                    row.label,
                    this.guiLeft + LIST_X,
                    y,
                    FORMULA_BODY_SCALE,
                    GtsrGuiPalette.TEXT_ACCENT);
            } else {
                drawScaledText(
                    this.fontRendererObj,
                    row.label,
                    this.guiLeft + LIST_X + FORMULA_SMALL_INDENT,
                    y,
                    FORMULA_SMALL_SCALE,
                    GtsrGuiPalette.TEXT_MUTED);
            }
            y += FORMULA_ROW_H;
        }
        return rows;
    }

    /**
     * 公式区行集构建（每帧重建，产出式逐项同源 {@code MTEThermoChemicalDenseSteamGenerator#processFuels}）。
     * 行数预算：双燃料满配 12 行 × 11px = 132px（y96..228 < 契约净空 233）；单燃料 11 行；
     * 停机（fuelKind=0）不产逐族推导行共 10 行。旧 8 行 formula_* 信息等价可达：
     * formula_flow→行2（值+tooltip）、formula_efficiency→行3 常显、formula_output→行5-7+G2 头 tooltip、
     * formula_cap→G1 头 tooltip、formula_consumption→行9-11+G3 头 tooltip、
     * formula_shortage/buffer/chip→行12（正文+tooltip 三行全文）。
     * 状态判定按服务端全有全无扣料语义：任一消耗 >0 即正常产（绿），双双归零为本 tick 停产
     * （中性琥珀）；快照不含停因细分，旧 "checked" 残差不再呈现。
     */
    private List<FormulaRow> buildFormulaRows(TcdsTerminalData.Snapshot snap) {
        final List<FormulaRow> rows = new ArrayList<FormulaRow>();
        final String eta = String.format(java.util.Locale.ENGLISH, "%.3f", snap.efficiency);

        final boolean dual = snap.fuelKind == 3;
        final boolean gasActive = snap.fuelKind == 1 || dual;
        final boolean liquidActive = snap.fuelKind == 2 || dual;

        // 燃料/流量代入串（旧 formula_flow、formula_consumption 行值同源拼装）
        final String fuelDisp;
        final String flowSub;
        if (dual) {
            fuelDisp = "gas " + snap.gasConsumption + " + liquid " + snap.liquidConsumption;
            flowSub = fuelDisp + " = " + (snap.gasConsumption + snap.liquidConsumption) + " L/t";
        } else if (gasActive || liquidActive) {
            fuelDisp = (gasActive ? "gas " : "liquid ") + snap.fuelConsumption;
            flowSub = fuelDisp + " = " + snap.fuelConsumption + " L/t";
        } else {
            fuelDisp = "0";
            flowSub = "0 L/t";
        }
        // 逐族取整项与产出代入串（合计用服务端权威 output）
        final long gasTerm = gasActive
            ? familyTerm(dual ? snap.gasConsumption : snap.fuelConsumption, snap.gasHeatValue, snap)
            : 0;
        final long liquidTerm = liquidActive
            ? familyTerm(dual ? snap.liquidConsumption : snap.fuelConsumption, snap.liquidHeatValue, snap)
            : 0;
        final String outputSub;
        if (dual) {
            outputSub = "gas " + gasTerm + " + liquid " + liquidTerm + " = " + snap.output + " L/t";
        } else if (gasActive) {
            outputSub = "gas " + gasTerm + " = " + snap.output + " L/t";
        } else if (liquidActive) {
            outputSub = "liquid " + liquidTerm + " = " + snap.output + " L/t";
        } else {
            outputSub = "0 L/t";
        }
        // 运行状态：服务端全有全无扣料（任一不足整 tick 双归零），快照无法细分缺气/缺水/缓冲满
        final String statusText;
        final int statusColor;
        if (snap.airConsumption > 0 || snap.waterConsumption > 0) {
            statusText = tr("gtsr.tcds_terminal.status_ok");
            statusColor = GtsrGuiPalette.STATE_ONLINE;
        } else {
            statusText = tr("gtsr.tcds_terminal.status_stopped");
            statusColor = GtsrGuiPalette.STATE_IDLE;
        }
        // 行12 tooltip：旧 formula_shortage/buffer/chip 三行全文
        final List<String> statusTip = new ArrayList<String>(3);
        statusTip.add(tr("gtsr.tcds_terminal.formula_shortage") + " → " + statusText);
        statusTip.add(tr("gtsr.tcds_terminal.formula_buffer") + " → " + GtsrNumFormat.grouped(snap.output) + " L/t");
        statusTip.add(
            tr("gtsr.tcds_terminal.formula_chip") + " = "
                + GtsrNumFormat.grouped(snap.output)
                + " ÷ 1000 = "
                + GtsrNumFormat.grouped(snap.output / 1000));

        // G1 流量与效率
        rows.add(
            headerRow(
                "gtsr.tcds_terminal.group_flow",
                lines(tr("gtsr.tcds_terminal.formula_cap") + " = " + snap.heatCap)));
        rows.add(
            kvRow(
                "gtsr.tcds_terminal.kv_actual_flow",
                flowSub,
                GtsrGuiPalette.TEXT_BODY,
                lines(tr("gtsr.tcds_terminal.formula_flow") + " = " + flowSub)));
        rows.add(smallRow(tr("gtsr.tcds_terminal.formula_efficiency") + " → " + eta));

        // G2 产出（总产出千分位高亮；逐族代入小字仅相应燃料族出）
        rows.add(
            headerRow(
                "gtsr.tcds_terminal.group_output",
                lines(tr("gtsr.tcds_terminal.formula_output") + " = " + outputSub)));
        rows.add(
            kvRow(
                "gtsr.tcds_terminal.kv_output_total",
                GtsrNumFormat.grouped(snap.output) + " L/t",
                GtsrGuiPalette.TEXT_ACCENT,
                null));
        if (gasActive) {
            rows.add(smallRow(tr("gtsr.tcds_terminal.derive_gas") + " = " + GtsrNumFormat.grouped(gasTerm) + " L/t"));
        }
        if (liquidActive) {
            rows.add(
                smallRow(tr("gtsr.tcds_terminal.derive_liquid") + " = " + GtsrNumFormat.grouped(liquidTerm) + " L/t"));
        }

        // G3 消耗与状态
        rows.add(
            headerRow(
                "gtsr.tcds_terminal.group_consumption",
                lines(
                    tr("gtsr.tcds_terminal.formula_consumption") + " = "
                        + fuelDisp
                        + " + air "
                        + snap.airConsumption
                        + " + water "
                        + snap.waterConsumption)));
        rows.add(kvRow("gtsr.tcds_terminal.kv_fuel", fuelDisp, GtsrGuiPalette.TEXT_BODY, null));
        rows.add(
            kvRow(
                "gtsr.tcds_terminal.kv_air",
                GtsrNumFormat.grouped(snap.airConsumption),
                GtsrGuiPalette.TEXT_BODY,
                null));
        rows.add(
            kvRow(
                "gtsr.tcds_terminal.kv_water",
                GtsrNumFormat.grouped(snap.waterConsumption),
                GtsrGuiPalette.TEXT_BODY,
                null));
        rows.add(
            kvRow(
                "gtsr.tcds_terminal.kv_status",
                statusText + " · " + GtsrNumFormat.grouped(snap.output / 1000),
                statusColor,
                statusTip));
        return rows;
    }

    /** 组头行（金粗 0.7f；ClusterPerfPage.groupHeader 同款 §6§l 惯例，绘制色走 TEXT_ACCENT）。 */
    private FormulaRow headerRow(String langKey, List<String> tooltip) {
        String text = EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(langKey);
        return new FormulaRow(
            FormulaRow.KIND_HEADER,
            GtsrGuiList.ellipsis(this.fontRendererObj, text, CAP_HEADER),
            null,
            0,
            tooltip);
    }

    /** KV 行（标签 TEXT_LABEL @8、值语义色 @150 对齐；标签与值各自 ellipsis 定宽）。 */
    private FormulaRow kvRow(String labelKey, String value, int valueColor, List<String> tooltip) {
        return new FormulaRow(
            FormulaRow.KIND_KV,
            GtsrGuiList.ellipsis(this.fontRendererObj, tr(labelKey), CAP_KV_LABEL),
            GtsrGuiList.ellipsis(this.fontRendererObj, value, CAP_KV_VALUE),
            valueColor,
            tooltip);
    }

    /** 推导小字行（0.6f 缩进 12，TEXT_MUTED）。 */
    private FormulaRow smallRow(String text) {
        return new FormulaRow(
            FormulaRow.KIND_SMALL,
            GtsrGuiList.ellipsis(this.fontRendererObj, text, CAP_SMALL),
            null,
            0,
            null);
    }

    /**
     * 公式区逐行 tooltip 命中（行矩形 = 面板 x∈[8,392] × 行 y 起 11px）：命中带 tooltip 的行
     * 登记 hoverTooltip（键 "fr"+行号，与输入框 "field"/按钮 "btn"+id 键空间不冲突）。
     *
     * @return 是否已登记命中（未命中落回按钮兜底）
     */
    private boolean formulaTooltipHit(List<FormulaRow> rows, int mouseX, int mouseY) {
        if (mouseX < this.guiLeft + LIST_X || mouseX >= this.guiLeft + FORMULA_RIGHT) {
            return false;
        }
        int rel = mouseY - this.guiTop - FORMULA_Y;
        if (rel < 0) {
            return false;
        }
        int index = rel / FORMULA_ROW_H;
        if (index >= rows.size()) {
            return false;
        }
        FormulaRow row = rows.get(index);
        if (row.tooltip == null) {
            return false;
        }
        this.hoverTooltip("fr" + index, row.tooltip, mouseX, mouseY);
        return true;
    }

    /** 单族取整项：流量×热值×η×2×(热量/100) 后 Math.round，与 processFuels 逐族项同式（仅展示代入用）。 */
    private static long familyTerm(int flow, int heatValue, TcdsTerminalData.Snapshot snap) {
        return Math.round(flow * (double) heatValue * snap.efficiency * 2.0d * (snap.heat / 100.0d));
    }

    /** 缩放文字绘制（glPushMatrix + glScalef；GuiClusterTerminalScreen.drawScaledText 同款，本屏私有最小实现）。 */
    private static void drawScaledText(FontRenderer font, String text, int x, int y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /**
     * 公式区单条物理行（固定 11px，不折行）：文本已在构建期按 1.0f 口径 cap 经 ellipsis 定宽，
     * 绘制侧零测量；tooltip 行集 null = 该行不登记（未命中落回按钮兜底）。
     */
    private static final class FormulaRow {

        static final int KIND_HEADER = 0;
        static final int KIND_KV = 1;
        static final int KIND_SMALL = 2;

        /** 行类型（KIND_*） */
        final int kind;
        /** HEADER/SMALL：行全文；KV：标签文本（均已 ellipsis） */
        final String label;
        /** KV 值列文本（ellipsis 产物；非 KV 为 null） */
        final String value;
        /** KV 值语义色（非 KV 忽略） */
        final int valueColor;
        /** 悬停 tooltip 行集（null=不登记） */
        final List<String> tooltip;

        FormulaRow(int kind, String label, String value, int valueColor, List<String> tooltip) {
            this.kind = kind;
            this.label = label;
            this.value = value;
            this.valueColor = valueColor;
            this.tooltip = tooltip;
        }
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    /** 只读信息行：标签（TEXT_LABEL，@8 起）+ 值（指定语义色，固定列 @150 对齐） */
    private void drawLabelValue(String labelKey, String value, int screenY, int valueColor) {
        this.fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal(labelKey),
            this.guiLeft + 8,
            screenY,
            GtsrGuiPalette.TEXT_LABEL);
        this.fontRendererObj.drawStringWithShadow(value, this.guiLeft + INFO_VALUE_X, screenY, valueColor);
    }

    // ==================== 数据活取 ====================

    /** 当前锚点匹配的快照（无回包/已失效/锚点不符为 null） */
    private TcdsTerminalData.Snapshot matchedSnapshot() {
        CachedSnapshot snapshot = cache;
        if (snapshot != null && snapshot.matchesAnchor(this.anchorX, this.anchorY, this.anchorZ, this.anchorDim)) {
            return snapshot.data;
        }
        return null;
    }

    // ==================== 工具 ====================

    private String ellipsized(String langKey, int buttonWidth) {
        return GtsrGuiList.ellipsis(this.fontRendererObj, StatCollector.translateToLocal(langKey), buttonWidth - 4);
    }

    private static List<String> lines(String line) {
        List<String> list = new ArrayList<String>(1);
        list.add(line);
        return list;
    }
}
