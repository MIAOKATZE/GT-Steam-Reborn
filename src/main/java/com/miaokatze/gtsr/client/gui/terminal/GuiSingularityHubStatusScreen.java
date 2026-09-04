package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.client.terminal.HubTerminalClientCache;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData.HubNodeInfo;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 奇点钻井枢纽「节点状态管理界面」（terminal-native-ui N10，PLAN §4.3-A / §4.5-A / §7.1）。
 * <p>
 * 行为等价映射（旧 MUI2 钻井枢纽状态 GUI，git 基线 b4fabb2 同名源码 → 本类，逐动作对照 PLAN §7.1）：
 * <ul>
 * <li>行渲染字段一一对应：图标（Miner/Driller 节点物品 16×16 renderItem）+ 名字/等级/状态
 * （STATE_* token 替代旧 § 色码）+ 坐标+DIM 灰字；速率等附加信息进 500ms 行 tooltip；</li>
 * <li>5 动作最终调用与旧 action handler 逐行同参（TOGGLE 目标值=客户端取反、
 * RECYCLE 失败红字走原服务端 chat、RENAME 原文发包由服务端裁剪）；</li>
 * <li>重命名 GuiTextField 客户端纯本地（maxLength 24），确认按钮才发包；</li>
 * <li>滚动保持 = GtsrGuiList 偏移自持（数据刷新不回顶，等价旧滚动列表）。</li>
 * </ul>
 * 布局（400×240 切片 A 重排，与缓存枢纽同 GuiTerminalBase 共享常量，四屏几何一致）：
 * 行高 20（单行容纳名字/等级/状态 + 坐标行），行点击选中节点，
 * 底部操作区按选中节点执行 5 动作（20px 冻结行高下等价承载旧行内按钮语义）。
 * 行 tooltip 无速率/容量类信息（与缓存枢纽不同），切片 A 不加数值列与列头。
 */
@SideOnly(Side.CLIENT)
public class GuiSingularityHubStatusScreen extends GuiTerminalBase {

    // 按钮 id（本 GUI 内部路由用）
    private static final int BTN_RENAME = 1;
    private static final int BTN_TOGGLE = 2;
    private static final int BTN_RECYCLE = 3;
    private static final int BTN_UPGRADE = 4;
    private static final int BTN_TELEPORT = 5;

    private GtsrGuiList list;

    /** 选中节点（pos+dim 键；展示/动作均以键在最新快照中活取，等价旧 currentInfo supplier） */
    private boolean hasSelection;
    private int selX, selY, selZ, selDim;

    public GuiSingularityHubStatusScreen(int x, int y, int z, int dim) {
        super(x, y, z, dim);
    }

    // ==================== 基类差异点 ====================

    @Override
    protected TerminalUiType uiType() {
        return TerminalUiType.SINGULARITY_HUB;
    }

    @Override
    protected Class<? extends IMetaTileEntity> targetMachineClass() {
        return MTESingularityDrillingHub.class;
    }

    @Override
    protected String titleText() {
        return StatCollector.translateToLocal("gtsr.hub_status.title");
    }

    @Override
    protected String countText() {
        return String.valueOf(matchedSnapshotNodes().size());
    }

    @Override
    protected List<String> tooltipLinesFor(GuiButton button) {
        HubNodeInfo cur = selectedInfo();
        switch (button.id) {
            case BTN_RENAME:
                return lines(StatCollector.translateToLocal("gtsr.hub_status.rename"));
            case BTN_TOGGLE:
                // 旧 tooltip：allowed ? stop : start（取活取值）
                return lines(
                    StatCollector.translateToLocal(
                        cur != null && cur.allowed ? "gtsr.hub_status.stop" : "gtsr.hub_status.start"));
            case BTN_RECYCLE:
                // 旧 tooltip：可回收 ? recycle : recycle_need_stop（禁用态仍展示提示）
                return lines(
                    StatCollector.translateToLocal(
                        cur != null && cur.recyclable ? "gtsr.hub_status.recycle"
                            : "gtsr.hub_status.recycle_need_stop"));
            case BTN_UPGRADE:
                return lines(StatCollector.translateToLocal("gtsr.hub_status.upgrade"));
            case BTN_TELEPORT:
                return lines(StatCollector.translateToLocal("gtsr.hub_status.teleport"));
            default:
                return null;
        }
    }

    // ==================== 结构与输入 ====================

    @Override
    public void initGui() {
        super.initGui();
        this.list = new GtsrGuiList(this, this.guiLeft + LIST_X, this.guiTop + LIST_Y, LIST_W, LIST_H);
        this.list.setRowSource(this::rowCount);
        this.list.setRowPainter(this::paintRow);
        this.list.setRowListener(this::selectRow);

        this.buttonList.add(
            new GtsrGuiButton(
                BTN_RENAME,
                this.guiLeft + 152,
                this.guiTop + RENAME_ROW_Y,
                64,
                14,
                ellipsized("gtsr.hub_status.rename", 64)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_TELEPORT,
                this.guiLeft + 8,
                this.guiTop + ACTION_ROW_Y,
                64,
                14,
                ellipsized("gtsr.hub_status.teleport", 64)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_TOGGLE,
                this.guiLeft + 74,
                this.guiTop + ACTION_ROW_Y,
                78,
                14,
                ellipsized("gtsr.hub_status.stop", 78)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_RECYCLE,
                this.guiLeft + 154,
                this.guiTop + ACTION_ROW_Y,
                78,
                14,
                ellipsized("gtsr.hub_status.recycle", 78)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_UPGRADE,
                this.guiLeft + 234,
                this.guiTop + ACTION_ROW_Y,
                78,
                14,
                ellipsized("gtsr.hub_status.upgrade", 78)));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 按钮可用态/文案随选中节点活取值刷新（等价旧 onUpdateListener + setEnabled）
        HubNodeInfo cur = selectedInfo();
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton button) {
                switch (button.id) {
                    case BTN_TOGGLE:
                        button.displayString = ellipsized(
                            cur != null && cur.allowed ? "gtsr.hub_status.stop" : "gtsr.hub_status.start",
                            78);
                        break;
                    default:
                        break;
                }
            }
        }
        setButtonEnabled(BTN_TOGGLE, cur != null);
        setButtonEnabled(BTN_RECYCLE, cur != null && cur.recyclable);
        setButtonEnabled(BTN_UPGRADE, cur != null);
        setButtonEnabled(BTN_TELEPORT, cur != null);
        setButtonEnabled(BTN_RENAME, cur != null);
        if (this.renameField != null) {
            this.renameField.setEnabled(cur != null);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        // 点击时活取选中节点（旧 currentInfo.get() == null 即忽略的同款守卫）
        final HubNodeInfo cur = selectedInfo();
        if (cur == null) {
            return;
        }
        switch (button.id) {
            case BTN_RENAME:
                // 纯本地文本框确认才发包；服务端裁剪剔 §/去空白/≤24（机器方法自持）
                this.sendAction(
                    SingularityHubTerminalData.ACTION_RENAME,
                    HubTerminalClientCache.posNamePayload(
                        cur.x,
                        cur.y,
                        cur.z,
                        cur.dim,
                        this.renameField != null ? this.renameField.getText() : ""));
                break;
            case BTN_TOGGLE:
                // 目标值 = 客户端取反（旧 sendToggle 同参）
                this.sendAction(
                    SingularityHubTerminalData.ACTION_TOGGLE,
                    HubTerminalClientCache.posBoolPayload(cur.x, cur.y, cur.z, cur.dim, !cur.allowed));
                break;
            case BTN_RECYCLE:
                this.sendAction(
                    SingularityHubTerminalData.ACTION_RECYCLE,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            case BTN_UPGRADE:
                this.sendAction(
                    SingularityHubTerminalData.ACTION_UPGRADE,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            case BTN_TELEPORT:
                this.sendAction(
                    SingularityHubTerminalData.ACTION_TELEPORT,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            default:
                return;
        }
        this.requestImmediateRefresh();
    }

    @Override
    public void handleMouseInput() {
        if (this.list.handleMouseInput()) {
            return; // 滚轮命中列表区：消费（GtsrGuiList 偏移自持）
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.renameField != null) {
            this.renameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (this.list.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        this.list.mouseClickMove(mouseX, mouseY, clickedMouseButton);
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        this.list.mouseReleased(mouseX, mouseY, state);
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks); // 暗底 + 面板 + 标题 + 按钮
        this.list.draw(mouseX, mouseY, this.zLevel);
        if (rowCount() == 0) {
            // 旧 gtsr.hub_status.empty 空态文案保留
            this.fontRendererObj.drawStringWithShadow(
                StatCollector.translateToLocal("gtsr.hub_status.empty"),
                this.guiLeft + 14,
                this.guiTop + LIST_Y + 8,
                GtsrGuiPalette.TEXT_MUTED);
        }
        this.drawRenameField();

        // 行 tooltip（500ms；GtsrGuiList 悬浮计时）
        final int hovered = this.list.hoveredIndex();
        if (hovered >= 0 && hovered < rowCount() && this.list.hoverElapsedMillis() >= 500) {
            this.drawHoveringText(rowTooltipLines(hovered), mouseX, mouseY, this.fontRendererObj);
        }
        // 重命名框 tooltip（旧 rename_hint）
        if (this.renameField != null && mouseX >= this.renameField.xPosition
            && mouseY >= this.renameField.yPosition
            && mouseX < this.renameField.xPosition + this.renameField.getWidth()
            && mouseY < this.renameField.yPosition + RENAME_FIELD_H) {
            this.hoverTooltip(
                "field",
                lines(StatCollector.translateToLocal("gtsr.hub_status.rename_hint")),
                mouseX,
                mouseY);
        } else {
            this.drawButtonTooltips(mouseX, mouseY);
        }
    }

    /** 单行绘制：图标 + 名字/等级/状态行 + 坐标+维度行（字段与旧 HubNodeInfo 行一一对应） */
    private void paintRow(int index, int x, int y, int mouseX, int mouseY) {
        final List<HubNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        final HubNodeInfo info = nodes.get(index);
        final int textX = x + 24;
        final int textWidth = LIST_W - 24 - 10;
        // 图标（纯客户端静态映射，零网络开销）
        this.renderItemIcon(
            (info.isMiner ? GTSRItemList.SingularityMinerNode : GTSRItemList.SingularityDrillingNode).get(1),
            x + 4,
            y + 2);

        // 第 1 行：名字 + "Mk-N"（强调色，等价旧 §b）+ 状态（STATE_* token）
        String typeName = StatCollector
            .translateToLocal(info.isMiner ? "gtsr.drilling.node_miner" : "gtsr.drilling.node_driller");
        String nodeName = info.name.isEmpty() ? typeName : info.name;
        String tierText = "Mk" + (info.tier + 1);
        String statusText;
        int statusColor;
        if (!info.allowed) {
            statusText = StatCollector.translateToLocal("gtsr.hub_status.status.stopped");
            statusColor = GtsrGuiPalette.STATE_OFFLINE;
        } else if (info.working) {
            statusText = StatCollector.translateToLocal("gtsr.gui.status.running");
            statusColor = GtsrGuiPalette.STATE_ONLINE;
        } else {
            statusText = StatCollector.translateToLocal("gtsr.gui.status.idle");
            statusColor = GtsrGuiPalette.STATE_IDLE;
        }
        int cursor = textX;
        String drawnName = GtsrGuiList.ellipsis(this.fontRendererObj, nodeName, textWidth / 2);
        this.fontRendererObj.drawStringWithShadow(drawnName, cursor, y + 1, GtsrGuiPalette.TEXT_BODY);
        cursor += this.fontRendererObj.getStringWidth(drawnName) + 2;
        this.fontRendererObj.drawStringWithShadow(tierText, cursor, y + 1, GtsrGuiPalette.TEXT_ACCENT);
        cursor += this.fontRendererObj.getStringWidth(tierText) + 4;
        if (cursor < textX + textWidth) {
            this.fontRendererObj.drawStringWithShadow(
                GtsrGuiList.ellipsis(this.fontRendererObj, statusText, textX + textWidth - cursor),
                cursor,
                y + 1,
                statusColor);
        }

        // 第 2 行：坐标 + 维度（旧 line2 灰字）
        String coords = "(" + info.x + ", " + info.y + ", " + info.z + ") DIM: " + info.dim;
        this.fontRendererObj.drawStringWithShadow(
            GtsrGuiList.ellipsis(this.fontRendererObj, coords, textWidth),
            textX,
            y + 10,
            GtsrGuiPalette.TEXT_MUTED);
    }

    /** 行 tooltip（500ms）：名字/类型、等级、坐标维度、工作/允许状态 */
    private List<String> rowTooltipLines(int index) {
        final List<HubNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return Collections.emptyList();
        }
        final HubNodeInfo info = nodes.get(index);
        String typeName = StatCollector
            .translateToLocal(info.isMiner ? "gtsr.drilling.node_miner" : "gtsr.drilling.node_driller");
        List<String> lines = new ArrayList<String>();
        lines.add(info.name.isEmpty() ? typeName : info.name);
        lines.add(typeName + " Mk" + (info.tier + 1));
        lines.add("(" + info.x + ", " + info.y + ", " + info.z + ") DIM: " + info.dim);
        lines.add(
            StatCollector.translateToLocal(
                !info.allowed ? "gtsr.hub_status.status.stopped"
                    : info.working ? "gtsr.gui.status.running" : "gtsr.gui.status.idle"));
        return lines;
    }

    // ==================== 数据活取 ====================

    private List<HubNodeInfo> matchedSnapshotNodes() {
        HubTerminalClientCache.DrillingSnapshot snapshot = HubTerminalClientCache.drillingSnapshot();
        if (snapshot != null && snapshot.matchesAnchor(this.anchorX, this.anchorY, this.anchorZ, this.anchorDim)) {
            return snapshot.nodes;
        }
        return Collections.emptyList();
    }

    private int rowCount() {
        return matchedSnapshotNodes().size();
    }

    /** 选中节点活取（键=pos+dim，旧 findNode 语义；null=未选中/键已被服务端移除） */
    private HubNodeInfo selectedInfo() {
        if (!this.hasSelection) {
            return null;
        }
        for (HubNodeInfo node : matchedSnapshotNodes()) {
            if (node.matchesPos(this.selX, this.selY, this.selZ, this.selDim)) {
                return node;
            }
        }
        return null;
    }

    private void selectRow(int index, int mouseX, int mouseY, int button) {
        final List<HubNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        final HubNodeInfo info = nodes.get(index);
        this.hasSelection = true;
        this.selX = info.x;
        this.selY = info.y;
        this.selZ = info.z;
        this.selDim = info.dim;
        // 重命名框预填选中节点当前名（等价旧每行字段初始文本语义）
        if (this.renameField != null) {
            this.renameField.setText(info.name);
        }
    }

    // ==================== 工具 ====================

    private void setButtonEnabled(int id, boolean enabled) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton button && button.id == id) {
                button.enabled = enabled;
                return;
            }
        }
    }

    private String ellipsized(String langKey, int buttonWidth) {
        return GtsrGuiList.ellipsis(this.fontRendererObj, StatCollector.translateToLocal(langKey), buttonWidth - 4);
    }

    private static List<String> lines(String line) {
        List<String> list = new ArrayList<String>(1);
        list.add(line);
        return list;
    }
}
