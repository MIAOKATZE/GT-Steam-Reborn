package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 蒸汽矿物物流集群「集群终端」主壳（terminal-native-ui N15，PLAN §4.5-C 冻结布局，620×340 =
 * {@link ClusterParams#GUI_WIDTH}/{@link ClusterParams#GUI_HEIGHT}）。extends
 * {@link GuiTerminalBase} 轨 A 骨架（doesGuiPauseGame=false、updateScreen 轮询 10t 初值 0、
 * 锚点失效自关、动作后 pollTimer 归零即时补发），绘制轨为原版自绘：
 * <ul>
 * <li>标题栏 y0-16：机器名 + tier 徽标真彩点 4 色（随 KEY_TIER 每帧联动）+ tier 名 +
 * 运行状态（未成型灰/停机灰/预热橙/运行绿）+ 电源钮 56×20 右部（点击发 TOGGLE_POWER）；</li>
 * <li>四卡区 y18-58：蒸汽/润滑/热量/吞吐 4×142×32 内嵌卡（数值每帧读缓存；蒸汽/润滑异常
 * 红字+⚠ 三重编码与旧轨一致）；</li>
 * <li>左页签轨 x2-30：3 页签 28×28（tab_active/inactive）竖排，点击切页；</li>
 * <li>内容区 x32,y62,582×258：三页填充（{@link ClusterTopologyPage}/
 * {@link ClusterLinkEditorPage}/{@link ClusterBoosterPage}，构造期注入、每帧自绘）；</li>
 * <li>底栏 y324-338：运行提示/异常摘要（chip_normal/chip_active + 灰字/红字，优先级与旧
 * footbarText 一致）。</li>
 * </ul>
 * <b>live 每帧重读纪律</b>（v1.11.22 缺流定格教训）：标题 tier 点/运行状态/四卡数值/底栏摘要
 * 全部在 drawScreen 每帧读 {@link ClusterTerminalClientCache}，零构造期快照求值；页签/卡片
 * 骨架静态。初始页随 open 包 initialPage 传输（1=链路页，物流模块兼容入口）。
 */
@SideOnly(Side.CLIENT)
public class GuiClusterTerminalScreen extends GuiTerminalBase {

    // ==================== 主壳布局常量（PLAN §4.5-C 冻结；推导同旧主壳） ====================

    /** 内容区左上角 X（页签轨右侧，面板坐标）。 */
    static final int CONTENT_X = 32;
    /** 内容区左上角 Y（四卡区之下）。 */
    static final int CONTENT_Y = 62;
    /** 内容区宽（620 - 32 - 6 右缘）。 */
    static final int CONTENT_W = ClusterParams.GUI_WIDTH - CONTENT_X - 6;
    /** 内容区高（底栏之上）。 */
    static final int CONTENT_H = ClusterParams.GUI_HEIGHT - CONTENT_Y - 20;
    /** 底栏 Y（面板坐标，单行 14px）。 */
    static final int FOOTBAR_Y = ClusterParams.GUI_HEIGHT - 16;
    /** 四卡区（y 18..58，卡高 32 顶对齐）。 */
    private static final int CARDS_Y = 18;
    private static final int CARD_W = 142;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int CARDS_X0 = CONTENT_X;
    /** 页签轨（x 2..30）：3 页签 28×28 竖排。 */
    private static final int TAB_X = 2;
    private static final int TAB_Y = CONTENT_Y;
    private static final int TAB_SIZE = 28;
    private static final int TAB_PITCH = 30;
    /** 电源钮（标题栏右部）。 */
    private static final int POWER_BTN_X = ClusterParams.GUI_WIDTH - 116;
    private static final int POWER_BTN_Y = 1;
    private static final int POWER_BTN_W = 56;
    private static final int POWER_BTN_H = 20;
    private static final int POWER_BTN_ID = 100;
    /** 页签 lang key（0=拓扑 / 1=链路 / 2=增幅）。 */
    private static final String[] TAB_LANG_KEYS = { "gtsr.cluster.gui.tab.topology", "gtsr.cluster.gui.tab.links",
        "gtsr.cluster.gui.tab.boosters" };
    /** tier 徽章真彩点（青铜/钢/钛/钨钢 + 未成型灰；旧 TIER_BADGE_DOTS 同值）。 */
    private static final int[] TIER_BADGE_COLORS = { 0xFFC87E3B, 0xFFC2C8D0, 0xFF8EA2C8, 0xFF6E7F8C, 0xFF6E6E6E };
    /** 卡底深色（#26262B，禁纯黑；旧 CARD_BG_ARGB 同值）。 */
    static final int CARD_BG_ARGB = 0xFF26262B;
    /** 进度条底色（暗灰，旧 BAR_EMPTY 同值）。 */
    static final int BAR_EMPTY_ARGB = 0xFF3A3A3F;

    // ==================== 状态 ====================

    /** 当前页（初始随 open 包 initialPage；钳 0..2）。 */
    private int activePage;
    private final ClusterTopologyPage topologyPage;
    private final ClusterLinkEditorPage linkEditorPage;
    private final ClusterBoosterPage boosterPage;
    private GuiButton powerButton;
    /** 页内 tooltip 延迟登记（页绘制在内容区剪刀内，出剪后再统一画，避免被裁剪）。 */
    private Object pendingTipKey;
    private List<String> pendingTipLines;

    public GuiClusterTerminalScreen(int x, int y, int z, int dim, int initialPage) {
        super(x, y, z, dim);
        this.activePage = Math.max(0, Math.min(2, initialPage));
        this.topologyPage = new ClusterTopologyPage(this);
        this.linkEditorPage = new ClusterLinkEditorPage(this);
        this.boosterPage = new ClusterBoosterPage(this);
    }

    // ==================== GuiTerminalBase 差异点 ====================

    @Override
    protected TerminalUiType uiType() {
        return TerminalUiType.CLUSTER_TERMINAL;
    }

    @Override
    protected Class<? extends IMetaTileEntity> targetMachineClass() {
        return MTESteamMineralLogisticsCluster.class;
    }

    @Override
    protected String titleText() {
        return tr("gt.blockmachines.gtsr.cluster.controller.name");
    }

    @Override
    protected String countText() {
        return "";
    }

    @Override
    protected List<String> tooltipLinesFor(GuiButton button) {
        return null; // 电源钮 tooltip 由 drawScreen 走 pendingTip 通道
    }

    /** 缓存是否锚定于本 GUI（页绘制前置校验；无回包时按旧轨首同步前默认值渲染）。 */
    boolean cacheLive() {
        return ClusterTerminalClientCache.isAnchored(this.anchorX, this.anchorY, this.anchorZ, this.anchorDim);
    }

    /** 页/主壳共用：发集群 C2S 动作 + pollTimer 归零即时补发（GTSWN 即时反馈范式）。 */
    void clusterAction(ClusterTerminalActions action, byte[] payload) {
        sendAction(action.ordinal(), payload);
        requestImmediateRefresh();
    }

    /** 当前绘制深度（Gui.zLevel 对页包外不可见，经宿主转发）。 */
    float zLevel() {
        return this.zLevel;
    }

    /** 宿主字体渲染器（GuiScreen.fontRendererObj 为 protected，页经宿主转发）。 */
    FontRenderer font() {
        return this.fontRendererObj;
    }

    /** 页绘制期登记 tooltip（出剪刀后由宿主统一经 500ms 通道绘制）。 */
    void requestTooltip(Object key, List<String> lines) {
        this.pendingTipKey = key;
        this.pendingTipLines = lines;
    }

    // ==================== 生命周期 ====================

    @Override
    public void initGui() {
        this.guiLeft = (this.width - ClusterParams.GUI_WIDTH) / 2;
        this.guiTop = (this.height - ClusterParams.GUI_HEIGHT) / 2;
        this.buttonList.clear();
        this.powerButton = new GtsrGuiButton(
            POWER_BTN_ID,
            this.guiLeft + POWER_BTN_X,
            this.guiTop + POWER_BTN_Y,
            POWER_BTN_W,
            POWER_BTN_H,
            "");
        this.buttonList.add(this.powerButton);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.id == POWER_BTN_ID) {
            clusterAction(ClusterTerminalActions.TOGGLE_POWER, new byte[0]);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dwheel != 0) {
            // 滚轮坐标按事件坐标换算（GtsrGuiList.handleMouseInput 同款），转发当前页（页内自钳制）
            pageFor(this.activePage)
                .wheel(contentOriginX(), contentOriginY(), mouseX0(), mouseY0(), Integer.signum(dwheel));
        }
    }

    /** 当前鼠标 X（handleMouseInput 阶段按事件坐标换算）。 */
    private int mouseX0() {
        return org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth;
    }

    private int mouseY0() {
        return this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int ox = contentOriginX();
        int oy = contentOriginY();
        // 页签切换（页签轨在内容区外，先行命中）
        for (int i = 0; i < TAB_LANG_KEYS.length; i++) {
            int tx = this.guiLeft + TAB_X;
            int ty = this.guiTop + TAB_Y + i * TAB_PITCH;
            if (mouseX >= tx && mouseX < tx + TAB_SIZE && mouseY >= ty && mouseY < ty + TAB_SIZE) {
                this.activePage = i;
                this.pendingTipKey = null;
                return;
            }
        }
        if (pageFor(this.activePage).mouseClicked(ox, oy, mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private ClusterPage pageFor(int index) {
        switch (index) {
            case 0:
                return this.topologyPage;
            case 1:
                return this.linkEditorPage;
            default:
                return this.boosterPage;
        }
    }

    /** 内容区原点（屏幕坐标）。 */
    private int contentOriginX() {
        return this.guiLeft + CONTENT_X;
    }

    private int contentOriginY() {
        return this.guiTop + CONTENT_Y;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        final float z = this.zLevel;
        // 整版面板 1:1（契约 §3 #3）
        GtsrGuiDrawing.drawRegion(
            GtsrGuiTextures.PANEL_CLUSTER,
            this.guiLeft,
            this.guiTop,
            0,
            0,
            ClusterParams.GUI_WIDTH,
            ClusterParams.GUI_HEIGHT,
            z);
        drawTitleBar();
        drawTopCards();
        drawTabs();
        // 内容区：剪刀内自绘三页之一
        final int ox = contentOriginX();
        final int oy = contentOriginY();
        this.pendingTipKey = null;
        this.pendingTipLines = null;
        pushScissor(ox, oy, CONTENT_W, CONTENT_H);
        pageFor(this.activePage).draw(ox, oy, mouseX, mouseY, z);
        popScissor();
        drawFootbar();
        // 电源钮标签随 KEY_ENABLED 每帧联动，再画原版按钮列表
        this.powerButton.displayString = ClusterTerminalClientCache.getBool(ClusterTerminalData.KEY_ENABLED, false)
            ? tr("gtsr.gui.cluster.power.on")
            : tr("gtsr.gui.cluster.power.off");
        super.drawScreen(mouseX, mouseY, partialTicks);
        // tooltip：电源钮区 + 页内登记（均在剪刀外绘制）
        if (mouseX >= this.guiLeft + POWER_BTN_X && mouseX < this.guiLeft + POWER_BTN_X + POWER_BTN_W
            && mouseY >= this.guiTop + POWER_BTN_Y
            && mouseY < this.guiTop + POWER_BTN_Y + POWER_BTN_H) {
            hoverTooltip("power", lines(tr("gtsr.gui.cluster.power.tip")), mouseX, mouseY);
        } else {
            hoverTooltip(this.pendingTipKey, this.pendingTipLines, mouseX, mouseY);
        }
    }

    // —— 标题栏：机器名 + tier 徽章（真彩点+名）+ 运行状态（数值每帧读缓存） ——

    private void drawTitleBar() {
        final FontRenderer font = this.fontRendererObj;
        drawScaledText(font, titleText(), this.guiLeft + 4, this.guiTop + 5, 0.85f, GtsrGuiPalette.TEXT_TITLE);
        int tier = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_TIER, -1);
        int dotColor = TIER_BADGE_COLORS[tier >= 0 && tier < 4 ? tier : 4];
        fillRect(this.guiLeft + 210, this.guiTop + 6, 5, 5, this.zLevel, dotColor);
        String tierText = tier >= 0 ? EnumChatFormatting.WHITE + tr(
            ClusterParams.ClusterTier.get(tier)
                .getLangKey())
            : EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.title.unformed");
        drawScaledText(font, tierText, this.guiLeft + 218, this.guiTop + 4, 0.8f, GtsrGuiPalette.TEXT_WHITE);
        drawScaledText(font, runStateText(), this.guiLeft + 290, this.guiTop + 4, 0.8f, GtsrGuiPalette.TEXT_WHITE);
    }

    /** 运行状态文案：未成型灰/停机灰/预热中橙/运行中绿（enabled + 满热 + 成型；旧 runStateText 同逻辑）。 */
    private String runStateText() {
        boolean enabled = ClusterTerminalClientCache.getBool(ClusterTerminalData.KEY_ENABLED, false);
        int tier = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_TIER, -1);
        if (tier < 0) return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.title.unformed");
        if (!enabled) return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.title.standby");
        int heat = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT, 0);
        return heat >= 100 ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.title.run")
            : EnumChatFormatting.GOLD + tr("gtsr.cluster.gui.title.preheating");
    }

    // —— 顶栏四卡（各 142×32 深底卡；数值每帧读缓存） ——

    private void drawTopCards() {
        final FontRenderer font = this.fontRendererObj;
        final float z = this.zLevel;
        for (int i = 0; i < 4; i++) {
            int x = this.guiLeft + CARDS_X0 + i * (CARD_W + CARD_GAP);
            int y = this.guiTop + CARDS_Y;
            fillRect(x, y, CARD_W, CARD_H, z, CARD_BG_ARGB);
        }
        String[] labels = { tr("gtsr.cluster.gui.card.steam"), tr("gtsr.cluster.gui.card.lube"),
            tr("gtsr.cluster.gui.card.heat"), tr("gtsr.cluster.gui.card.thru") };
        for (int i = 0; i < labels.length; i++) {
            drawScaledText(
                font,
                EnumChatFormatting.GRAY + labels[i],
                this.guiLeft + CARDS_X0 + i * (CARD_W + CARD_GAP) + 3,
                this.guiTop + CARDS_Y + 3,
                0.6f,
                GtsrGuiPalette.TEXT_MUTED);
        }
        int y0 = this.guiTop + CARDS_Y + 11;
        // 卡 1：蒸汽 L/s（异常三重编码：红字 + ⚠ + 中文词）
        drawScaledText(
            font,
            rateLine(
                ClusterTerminalData.KEY_STEAM,
                ClusterTerminalData.SUPPLY_STEAM_SHORT,
                "gtsr.cluster.gui.card.steam.short"),
            this.guiLeft + CARDS_X0 + 3,
            y0,
            0.9f,
            GtsrGuiPalette.TEXT_WHITE);
        // 卡 2：润滑 L/s
        int lubeX = CARDS_X0 + CARD_W + CARD_GAP;
        drawScaledText(
            font,
            rateLine(
                ClusterTerminalData.KEY_LUBE,
                ClusterTerminalData.SUPPLY_LUBE_SHORT,
                "gtsr.cluster.gui.card.lube.short"),
            this.guiLeft + lubeX + 3,
            y0,
            0.9f,
            GtsrGuiPalette.TEXT_WHITE);
        // 卡 3：热量百分比（1% 步进橙数字 + 进度条）
        int heatX = CARDS_X0 + (CARD_W + CARD_GAP) * 2;
        int heat = Math.max(0, Math.min(100, ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT, 0)));
        drawScaledText(
            font,
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + heat + "%",
            this.guiLeft + heatX + 3,
            y0,
            0.9f,
            GtsrGuiPalette.TEXT_WHITE);
        int barX = this.guiLeft + heatX + 3;
        int barY = this.guiTop + CARDS_Y + 24;
        int barW = CARD_W - 6;
        fillRect(barX, barY, barW, 7, this.zLevel, BAR_EMPTY_ARGB);
        fillRect(barX, barY, (int) (barW * heat / 100.0D), 7, this.zLevel, GtsrGuiPalette.TEXT_ACCENT);
        // 卡 4：吞吐（真实矿/s + 累计小字）
        int thruX = CARDS_X0 + (CARD_W + CARD_GAP) * 3;
        long thru = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_THRU, 0);
        String thruText = (thru > 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.WHITE)
            + NumberFormatUtil.formatNumber(thru)
            + " "
            + tr("gtsr.cluster.gui.card.thru.unit");
        drawScaledText(font, thruText, this.guiLeft + thruX + 3, y0, 0.9f, GtsrGuiPalette.TEXT_WHITE);
        String totalText = EnumChatFormatting.GRAY + String.format(
            tr("gtsr.cluster.gui.card.thru.total"),
            NumberFormatUtil.formatNumber(ClusterTerminalClientCache.getLong(ClusterTerminalData.KEY_TOTAL, 0L)));
        drawScaledText(
            font,
            totalText,
            this.guiLeft + thruX + 3,
            this.guiTop + CARDS_Y + 24,
            0.6f,
            GtsrGuiPalette.TEXT_MUTED);
    }

    /** 速率行：数值 + 单位；异常（供给位）三重编码——红字 + ⚠ 图标 + 中文词（旧 rateLine 同逻辑）。 */
    private String rateLine(String valueKey, int supplyBit, String shortKey) {
        int value = ClusterTerminalClientCache.getInt(valueKey, 0);
        boolean alert = (ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SUPPLY, 0) & supplyBit) != 0;
        String number = NumberFormatUtil.formatNumber(value);
        if (alert) {
            return EnumChatFormatting.RED + "\u26a0 "
                + EnumChatFormatting.BOLD
                + number
                + " "
                + EnumChatFormatting.RED
                + tr("gtsr.cluster.gui.card.unit.lps")
                + " \u26a0 "
                + tr(shortKey);
        }
        return EnumChatFormatting.WHITE + number + " " + tr("gtsr.cluster.gui.card.unit.lps");
    }

    // —— 左页签轨（3×28×28 竖排） ——

    private void drawTabs() {
        for (int i = 0; i < TAB_LANG_KEYS.length; i++) {
            int tx = this.guiLeft + TAB_X;
            int ty = this.guiTop + TAB_Y + i * TAB_PITCH;
            if (i == this.activePage) {
                GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.TAB_ACTIVE, 4, tx, ty, TAB_SIZE, TAB_SIZE, this.zLevel);
            } else {
                GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.TAB_INACTIVE, 4, tx, ty, TAB_SIZE, TAB_SIZE, this.zLevel);
            }
            String label = tr(TAB_LANG_KEYS[i]);
            int color = i == this.activePage ? GtsrGuiPalette.TEXT_ACCENT : GtsrGuiPalette.TEXT_MUTED;
            int w = (int) (this.fontRendererObj.getStringWidth(label));
            drawScaledText(
                this.fontRendererObj,
                label,
                tx + (TAB_SIZE - (int) (w * 0.8f)) / 2,
                ty + (TAB_SIZE - 8) / 2,
                0.8f,
                color);
        }
    }

    // —— 底栏：异常摘要（红/chip_active）优先，其次未成型提示，默认运行提示（灰/chip_normal） ——

    private void drawFootbar() {
        boolean alert;
        String text = footbarAlertText();
        if (text != null) {
            alert = true;
        } else {
            alert = false;
            text = EnumChatFormatting.GRAY + tr(footbarNormalKey());
        }
        final FontRenderer font = this.fontRendererObj;
        int textW = (int) (font.getStringWidth(text) * 0.7f);
        int chipW = Math.min(CONTENT_W, textW + 10);
        GtsrGuiDrawing.drawNineSlice(
            alert ? GtsrGuiTextures.CHIP_ACTIVE : GtsrGuiTextures.CHIP_NORMAL,
            4,
            this.guiLeft + CONTENT_X,
            this.guiTop + FOOTBAR_Y,
            chipW,
            14,
            this.zLevel);
        drawScaledText(
            font,
            text,
            this.guiLeft + CONTENT_X + 5,
            this.guiTop + FOOTBAR_Y + 4,
            0.7f,
            alert ? GtsrGuiPalette.TEXT_WHITE : GtsrGuiPalette.TEXT_MUTED);
    }

    /** 异常摘要文本（有异常返回红字串，null=无异常；旧 footbarText 前两段优先级）。 */
    private String footbarAlertText() {
        int supply = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_SUPPLY, 0);
        if ((supply & ClusterTerminalData.SUPPLY_STEAM_SHORT) != 0) {
            return EnumChatFormatting.RED + "\u26a0 " + tr("gtsr.cluster.gui.foot.steam_short");
        }
        if ((supply & ClusterTerminalData.SUPPLY_LUBE_SHORT) != 0) {
            return EnumChatFormatting.RED + "\u26a0 " + tr("gtsr.cluster.gui.foot.lube_short");
        }
        int brk = ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_BREAK, -1);
        if (brk >= 1) {
            return EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.topo.error.ext"), brk);
        }
        return null;
    }

    /** 无异常时的运行提示键（未成型提示优先，旧 footbarText 后段）。 */
    private String footbarNormalKey() {
        if (ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_TIER, -1) < 0) {
            return "gtsr.cluster.gui.foot.unformed";
        }
        return "gtsr.cluster.gui.foot.normal";
    }

    // ==================== 页面共用绘制/输入工具（包内静态） ====================

    /** lang 简写。 */
    static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    /** 缩放描边文字（§ 色码由 FontRenderer 原生解析，颜色参数只兜底无码段）。 */
    static void drawScaledText(FontRenderer font, String text, int x, int y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        if (scale == 1.0f) {
            font.drawStringWithShadow(text, x, y, color);
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /** 缩放文字宽（测量 = 原生宽 × scale，取整）。 */
    static int scaledTextWidth(FontRenderer font, String text, float scale) {
        return (int) (font.getStringWidth(text) * scale);
    }

    /** 纯色矩形（Tessellator 直绘，blend 自管理，画完复位顶点色；z 透传）。 */
    static void fillRect(int x, int y, int w, int h, float z, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, a);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(x, y + h, z);
        tessellator.addVertex(x + w, y + h, z);
        tessellator.addVertex(x + w, y, z);
        tessellator.addVertex(x, y, z);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 启用剪刀测试（GUI 坐标 → 屏幕像素，GtsrGuiList 同款换算）。 */
    static void pushScissor(int x, int y, int w, int h) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(
            mc,
            mc.displayWidth,
            mc.displayHeight);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + h) * scale, w * scale, h * scale);
    }

    /** 关闭剪刀测试。 */
    static void popScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** 可变行参数快捷构造。 */
    static List<String> lines(String... items) {
        List<String> out = new ArrayList<String>();
        for (String item : items) {
            out.add(item);
        }
        return out;
    }

    // ==================== 页面契约（N16/N17/N18 共用；坐标均为屏幕绝对坐标） ====================

    /** 集群终端页契约：内容区原点 (ox,oy) + 582×258 绘制；输入事件由宿主转发当前页。 */
    interface ClusterPage {

        /** 每帧绘制（宿主已对内容区启用剪刀；动态内容一律每帧读 ClusterTerminalClientCache）。 */
        void draw(int ox, int oy, int mx, int my, float z);

        /** @return 命中并消费返回 true（未命中返回 false 交还宿主） */
        boolean mouseClicked(int ox, int oy, int mx, int my, int button);

        /** 滚轮（dir=±1 行；仅鼠标落在内容区时生效）；页内自钳制。 */
        void wheel(int ox, int oy, int mx, int my, int dir);
    }
}
