package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;
import com.miaokatze.gtsr.common.terminal.TcdsTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

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
        drawFormulaLines();

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
        } else {
            this.drawButtonTooltips(mouseX, mouseY);
        }
    }

    /** 公式只读区：静态双语文案，第三行输入之后按 16px 节奏绘制，底界 y233 内最多 8 行。 */
    private void drawFormulaLines() {
        String[] keys = { "gtsr.tcds_terminal.formula_flow", "gtsr.tcds_terminal.formula_efficiency",
            "gtsr.tcds_terminal.formula_output", "gtsr.tcds_terminal.formula_cap",
            "gtsr.tcds_terminal.formula_consumption", "gtsr.tcds_terminal.formula_shortage",
            "gtsr.tcds_terminal.formula_buffer", "gtsr.tcds_terminal.formula_chip" };
        int y = this.guiTop + INFO_ROW_Y + INFO_ROW_STEP * 4;
        for (String key : keys) {
            this.fontRendererObj.drawStringWithShadow(
                StatCollector.translateToLocal(key),
                this.guiLeft + 8,
                y,
                GtsrGuiPalette.TEXT_MUTED);
            y += INFO_ROW_STEP;
        }
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
