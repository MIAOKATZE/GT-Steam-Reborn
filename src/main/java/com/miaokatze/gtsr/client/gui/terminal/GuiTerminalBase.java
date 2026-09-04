package com.miaokatze.gtsr.client.gui.terminal;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 终端轨纯展示 UI 公共基类（terminal-native-ui N9，PLAN §4.1 轨 A / §4.5-A）。
 * <p>
 * 行为骨架（GTSWN 范式，GuiQuantumTerminal :71-84 同款纪律）：
 * <ul>
 * <li>extends 原版 {@link GuiScreen} 自绘，<b>完全不走 openGui</b>（零 windowId 污染，
 * wiki fml-opengui-windowid-semantics §3）；</li>
 * <li>{@code doesGuiPauseGame()=false}（否则单人模式服务端暂停→轮询死锁，R10）；</li>
 * <li>{@code updateScreen} 轮询发送请求：pollTimer 初值 0（首帧即发）、周期
 * {@value #POLL_INTERVAL_TICKS} tick（PLAN §4.3 通用：轮询 10t）；</li>
 * <li>锚点失效自关：客户端侧锚点 TE 机器类不再匹配 → displayGuiScreen(null)；
 * 离 64 格走服务端回包 valid=false 由 TerminalClientPacketSink 统一自关（等价旧
 * canInteractWith）；GUI 关闭不通知服务端（纯展示无状态残留）；</li>
 * <li>GTSWN 面板解剖绘制骨架：暗底 + panel_hub_status 整版 1:1 + 标题（左）与节点计数（右）；</li>
 * <li>重命名 {@link GuiTextField} 支持（客户端纯本地，确认才发包，maxLength 24）；</li>
 * <li>500ms drawHoveringText tooltip（GTSWN GuiDeviceInfoTerminal :631-669 范式）。</li>
 * </ul>
 * 布局（400×240 切片 A 重排，原 PLAN §4.5-A 冻结 320×200 记档；契约 §2 净空裁决内容止于行 233）：
 * 列头行 y15-23（缓存枢纽列头用，钻井枢纽留白）；列表内嵌区 (8,24) 384×176
 * （GtsrGuiList 行高 20、滚动偏移自持）；底部操作区：重命名行 y204 + 动作按钮行 y219。
 * 枢纽各屏（钻井/蒸汽/蓄水）共用本类布局常量，几何一致。
 */
@SideOnly(Side.CLIENT)
public abstract class GuiTerminalBase extends GuiScreen {

    /** 面板宽（切片 A 400×240，与 panel_hub_status 同尺寸 1:1；原 PLAN §4.5-A 冻结 320 记档） */
    public static final int PANEL_W = GtsrGuiTextures.PANEL_HUB_STATUS_W;
    /** 面板高 */
    public static final int PANEL_H = GtsrGuiTextures.PANEL_HUB_STATUS_H;

    // ---- 枢纽共享布局常量（400×240 切片 A 重排；钻井/蒸汽/蓄水四屏几何一致） ----

    /** 列头标签行 y（GuiCacheHubStatusScreen 列头用；钻井枢纽该带留白保持几何一致） */
    protected static final int COLUMN_HEADER_Y = 15;

    /** 列表内嵌区左边界（面板相对；契约 §2 内容侧净空 x+8） */
    protected static final int LIST_X = 8;
    /** 列表内嵌区上边界（列头行之下） */
    protected static final int LIST_Y = 24;
    /** 列表内嵌区宽（面板相对 400-2×8） */
    protected static final int LIST_W = 384;
    /** 列表内嵌区高（8 整行 + 16px 局部行；GtsrGuiList 行高 20 冻结） */
    protected static final int LIST_H = 176;

    /** 重命名行 y（列表底 200 下方 4px，同旧 320×200 布局的 4px 行距节奏） */
    protected static final int RENAME_ROW_Y = 204;

    /** 动作按钮行 y（重命名行下 1px 间隔，行高 14；止于净空行 233 上方，同旧行距节奏） */
    protected static final int ACTION_ROW_Y = 219;

    /** 轮询周期（tick）：PLAN §4.3-A/B 冻结 10t */
    public static final int POLL_INTERVAL_TICKS = 10;

    /** tooltip 悬浮延迟（GTSWN 同款 500ms） */
    private static final long TOOLTIP_DELAY_MILLIS = 500L;

    /** 重命名框高（initGui 构造与命中判定共用；GuiTextField 无公开高度读取） */
    protected static final int RENAME_FIELD_H = 14;

    /** 锚点（打开时的枢纽 pos+dim，回包/快照/复核三方共同凭据） */
    protected final int anchorX, anchorY, anchorZ, anchorDim;

    /** 面板左上角屏幕坐标（initGui 居中） */
    protected int guiLeft, guiTop;

    /** 轮询计时（初值 0：首个 updateScreen 立即发送首帧请求） */
    private int pollTimer = 0;

    /** 重命名文本框（客户端纯本地；确认按钮才发包） */
    protected GuiTextField renameField;

    // ---- 500ms tooltip 状态（键相等性按 Objects.equals，跨帧同一命中才累计时间） ----
    private Object tooltipKey;
    private long tooltipStartMillis;

    protected GuiTerminalBase(int x, int y, int z, int dim) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.anchorDim = dim;
    }

    // ==================== 子类差异点 ====================

    /** 本 UI 的终端类型（请求/动作包 uiType） */
    protected abstract TerminalUiType uiType();

    /** 目标机器类（客户端锚点复核口径，与 open 包第二校验同表） */
    protected abstract Class<? extends IMetaTileEntity> targetMachineClass();

    /** 标题文字（已本地化） */
    protected abstract String titleText();

    /** 标题栏右对齐的节点计数文字 */
    protected abstract String countText();

    /** 动作按钮悬浮文案（null=无）；由 drawButtonTooltips 逐帧询问 */
    protected abstract List<String> tooltipLinesFor(GuiButton button);

    // ==================== 生命周期 ====================

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - PANEL_W) / 2;
        this.guiTop = (this.height - PANEL_H) / 2;
        this.renameField = new GuiTextField(
            this.fontRendererObj,
            this.guiLeft + 8,
            this.guiTop + RENAME_ROW_Y,
            140,
            14);
        this.renameField.setMaxStringLength(24);
        this.renameField.setFocused(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (--this.pollTimer <= 0) {
            this.pollTimer = POLL_INTERVAL_TICKS;
            sendRequest();
        }
        if (!isAnchorValid()) {
            // 锚点失效自关（客户端 TE 复核；64 格离场由服务端 valid=false 路径关闭）
            this.mc.displayGuiScreen(null);
        }
    }

    /** 轮询请求（动作后经 {@link #requestImmediateRefresh} 重置 pollTimer 即时补发） */
    protected void sendRequest() {
        TerminalNet.sendRequestFromClient(this.uiType(), this.anchorDim, this.anchorX, this.anchorY, this.anchorZ);
    }

    /** 动作发送（uiType 锚点复用 open 锚点；参数仅原始值，零计算结果回传） */
    protected void sendAction(int actionCode, byte[] payload) {
        TerminalNet.sendActionFromClient(
            this.uiType(),
            this.anchorDim,
            this.anchorX,
            this.anchorY,
            this.anchorZ,
            actionCode,
            payload);
    }

    /** 动作执行后调用：pollTimer 归零 → 下个 updateScreen 立即补发一次请求（GTSWN 即时反馈范式） */
    protected void requestImmediateRefresh() {
        this.pollTimer = 0;
    }

    /**
     * 客户端锚点复核：世界/维度匹配 + 锚点处 GT 基 TE 存活 + 机器类匹配。
     */
    protected boolean isAnchorValid() {
        WorldClient world = this.mc.theWorld;
        if (world == null || world.provider.dimensionId != this.anchorDim) {
            return false;
        }
        TileEntity te = world.getTileEntity(this.anchorX, this.anchorY, this.anchorZ);
        if (!(te instanceof IGregTechTileEntity baseTE)) {
            return false;
        }
        IMetaTileEntity mte = baseTE.getMetaTileEntity();
        return mte != null && targetMachineClass().isInstance(mte);
    }

    /** 服务端 valid=false 自关路径用：当前 GUI 锚点与回包锚点比对 */
    public boolean isAnchoredAt(int x, int y, int z, int dim) {
        return this.anchorX == x && this.anchorY == y && this.anchorZ == z && this.anchorDim == dim;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        // 整版面板 1:1（契约 §3 #1，整绘不用 9-slice）
        GtsrGuiDrawing.drawRegion(
            GtsrGuiTextures.PANEL_HUB_STATUS,
            this.guiLeft,
            this.guiTop,
            0,
            0,
            PANEL_W,
            PANEL_H,
            this.zLevel);
        // 标题栏：标题左对齐 + 节点计数右对齐（契约 §2：标题文字起 y+6 避排气板）
        this.fontRendererObj
            .drawStringWithShadow(this.titleText(), this.guiLeft + 8, this.guiTop + 6, GtsrGuiPalette.TEXT_TITLE);
        final String count = this.countText();
        this.fontRendererObj.drawStringWithShadow(
            count,
            this.guiLeft + PANEL_W - 8 - this.fontRendererObj.getStringWidth(count),
            this.guiTop + 6,
            GtsrGuiPalette.TEXT_MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks); // 原版按钮列表
    }

    // ==================== tooltip（500ms） ====================

    /**
     * 悬浮命中登记：跨帧同一 key 才累计时间，≥500ms 画 drawHoveringText；
     * key 传 null = 本帧无命中（重置计时）。
     */
    protected final void hoverTooltip(Object key, List<String> lines, int mouseX, int mouseY) {
        if (key == null) {
            this.tooltipKey = null;
            return;
        }
        if (!Objects.equals(this.tooltipKey, key)) {
            this.tooltipKey = key;
            this.tooltipStartMillis = System.currentTimeMillis();
            return;
        }
        if (lines != null && !lines.isEmpty()
            && System.currentTimeMillis() - this.tooltipStartMillis >= TOOLTIP_DELAY_MILLIS) {
            this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
        }
    }

    /**
     * 按钮悬浮 tooltip 询问（子类 drawScreen 末尾调用）：命中按钮矩形（含禁用态——
     * 旧轨禁用按钮仍展示提示）→ {@link #tooltipLinesFor}。
     */
    protected final void drawButtonTooltips(int mouseX, int mouseY) {
        GuiButton hovered = null;
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton button && button.visible && isMouseOverButton(button, mouseX, mouseY)) {
                hovered = button;
                break;
            }
        }
        if (hovered == null) {
            this.hoverTooltip(null, null, mouseX, mouseY);
            return;
        }
        this.hoverTooltip("btn" + hovered.id, this.tooltipLinesFor(hovered), mouseX, mouseY);
    }

    private static boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseY >= button.yPosition
            && mouseX < button.xPosition + button.width
            && mouseY < button.yPosition + button.height;
    }

    // ==================== 公共绘制/输入工具 ====================

    /** 行首 16×16 物品图标渲染（itemRender + GUI 标准光照，画完复位顶点色） */
    protected void renderItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.zLevel = this.zLevel;
        itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 重命名框绘制（子类 drawScreen 调用） */
    protected void drawRenameField() {
        if (this.renameField != null) {
            this.renameField.drawTextBox();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 重命名框纯本地输入：持有焦点时先于全局快捷键消费；ESC 不被文本框处理 → 关闭 GUI（与旧轨一致）
        if (this.renameField != null && this.renameField.isFocused()
            && this.renameField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
