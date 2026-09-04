package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.client.terminal.HubTerminalClientCache;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData;
import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData.CacheNodeInfo;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * 蒸汽/蓄水枢纽「缓存节点状态管理界面」公共基类（terminal-native-ui N11，
 * PLAN §4.3-B / §4.5-A / §7.2）。子类薄壳（N12/N13）仅差标题 key 与节点图标映射。
 * <p>
 * 行为等价映射（旧 MUI2 缓存枢纽状态 GUI 基类 + 两薄壳，git 基线 b4fabb2 同名源码 → 本类，
 * 逐动作对照 PLAN §7.2）：
 * <ul>
 * <li>行渲染（400×240 切片 A 列内化）：左列=图标 16×16 renderItem（子类 instanceof 类型串静态
 * 映射，零网络开销）+ 名字（离线红字后缀）+ 流体名（强调）；右侧储量/容量/速率三列右对齐
 * （formatKMG 逐行移植；速率原仅在 500ms 行 tooltip 展示，切片 A 起列内化为行内唯一来源，
 * 行 tooltip 只留名字/类型与坐标维度补充，不留重复显示）；列表上方 muted 列头标签行
 * （TEXT_LABEL，速率/容量档仍只在底部按钮与 tooltip 展示）；</li>
 * <li>6 动作最终调用与旧两薄壳委托表逐字对应（同批继承方法、同参序）；发送类仓
 * （type 以 _out 结尾）容量按钮禁用（旧 supportsCapTier 判定）；modeLocked 行模式按钮
 * 禁用仍展示；离线行禁操作按钮仅展示绑定记录；</li>
 * <li>重命名 GuiTextField 客户端纯本地（maxLength 24），确认才发包，服务端裁剪自持。</li>
 * </ul>
 * 布局同奇点版（§4.5-A）：行高 20、行点击选中、底部操作区执行 6 动作。
 */
@SideOnly(Side.CLIENT)
public abstract class GuiCacheHubStatusScreen extends GuiTerminalBase {

    // 按钮 id（本 GUI 内部路由用）
    private static final int BTN_RENAME = 1;
    private static final int BTN_TELEPORT = 2;
    private static final int BTN_RATE = 3;
    private static final int BTN_CAP = 4;
    private static final int BTN_MODE = 5;
    private static final int BTN_AUTO = 6;

    // ---- 行内数值列（400×240 切片 A 列内化；行相对偏移，行宽 = LIST_W = 384，右缘留 10px 避滚动条） ----

    /** 储量列右缘（行相对；列宽 70） */
    private static final int COL_STORED_RIGHT = 232;
    /** 容量列右缘（行相对；列宽 70） */
    private static final int COL_CAP_RIGHT = 308;
    /** 速率列右缘（行相对；列宽 60） */
    private static final int COL_RATE_RIGHT = 374;
    /** 左列（图标+名/流体）文本可用宽：图标位 24 起，至储量列左缘（232-70=162）留 6px 间隔 */
    private static final int LEFT_CELL_W = 162 - 24 - 6;

    /** 「输入模式」按钮宽：短标签键在 zh/en 双语下均完整显示（en 最长 ~130px；ellipsized 仍兜底），完整「点击切换」提示留在 hover tooltip */
    private static final int MODE_BTN_W = 136;

    private GtsrGuiList list;

    /** 终端类型（蒸汽/蓄水；构造传入，服务端分派对应 array 委托表的凭据） */
    private final TerminalUiType uiType;

    /** 选中节点（pos+dim 键；展示/动作均以键在最新快照中活取） */
    private boolean hasSelection;
    private int selX, selY, selZ, selDim;

    protected GuiCacheHubStatusScreen(TerminalUiType uiType, int x, int y, int z, int dim) {
        super(x, y, z, dim);
        this.uiType = uiType;
    }

    // ==================== 子类差异点（旧两薄壳同款两件套 + uiType 构造传参） ====================

    /** 面板标题 lang key（旧 getTitleLangKey） */
    protected abstract String getTitleLangKey();

    /** 按 type 字符串静态映射对应缓存节点物品图标；未知类型返回 null（旧 getNodeIcon） */
    protected abstract ItemStack getNodeIcon(String type);

    // ==================== 基类差异点 ====================

    @Override
    protected final TerminalUiType uiType() {
        return this.uiType;
    }

    /** 目标机器类随 uiType 定（与服务端 N31 交叉复核同表；客户端锚点复核口径） */
    @Override
    protected final Class<? extends IMetaTileEntity> targetMachineClass() {
        return this.uiType == TerminalUiType.STEAM_HUB ? MTESteamHubArray.class : MTEWaterHubArray.class;
    }

    @Override
    protected String titleText() {
        return StatCollector.translateToLocal(getTitleLangKey());
    }

    @Override
    protected String countText() {
        return String.valueOf(matchedSnapshotNodes().size());
    }

    @Override
    protected List<String> tooltipLinesFor(GuiButton button) {
        CacheNodeInfo cur = selectedInfo();
        switch (button.id) {
            case BTN_RENAME:
                return lines(StatCollector.translateToLocal("gtsr.hub_status.rename"));
            case BTN_TELEPORT:
                return lines(StatCollector.translateToLocal("gtsr.hub_status.teleport"));
            case BTN_RATE:
                if (cur == null) return null;
                // 旧 tooltip：当前百分比 + 说明
                List<String> rate = new ArrayList<String>(2);
                rate.add(cur.rate + "%");
                rate.add(StatCollector.translateToLocal("gtsr.cache_hub_status.rate_tip"));
                return rate;
            case BTN_CAP:
                if (cur == null) return null;
                List<String> cap = new ArrayList<String>(2);
                cap.add(cur.capPct + "%");
                cap.add(StatCollector.translateToLocal("gtsr.cache_hub_status.cap_tip"));
                return cap;
            case BTN_MODE:
                // 旧 tooltip：out ? mode_tip_input : mode_tip_output（取活取值）
                return lines(
                    StatCollector.translateToLocal(
                        cur != null && cur.out ? "gtsr.cache_hub_status.mode_tip_input"
                            : "gtsr.cache_hub_status.mode_tip_output"));
            case BTN_AUTO:
                if (cur == null) return null;
                List<String> auto = new ArrayList<String>(2);
                auto.add(
                    StatCollector.translateToLocal(
                        cur.auto ? "gtsr.cache_hub_status.auto_on" : "gtsr.cache_hub_status.auto_off"));
                auto.add(StatCollector.translateToLocal("gtsr.cache_hub_status.auto_tip"));
                return auto;
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
        // 传送 + 速率 + 容量 + 模式 + 自动输出（旧行内横排按钮序：teleport→rate→cap→mode→auto；
        // mode 用短标签键双语完整显示，完整「点击切换」提示留在 hover tooltip；auto 随 mode 加宽右移 244→272→304）
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_TELEPORT,
                this.guiLeft + 8,
                this.guiTop + ACTION_ROW_Y,
                56,
                14,
                ellipsized("gtsr.hub_status.teleport", 56)));
        this.buttonList.add(new GtsrGuiButton(BTN_RATE, this.guiLeft + 66, this.guiTop + ACTION_ROW_Y, 48, 14, ""));
        this.buttonList.add(new GtsrGuiButton(BTN_CAP, this.guiLeft + 116, this.guiTop + ACTION_ROW_Y, 48, 14, ""));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_MODE,
                this.guiLeft + 166,
                this.guiTop + ACTION_ROW_Y,
                MODE_BTN_W,
                14,
                ellipsized("gtsr.cache_hub_status.mode_short_output", MODE_BTN_W)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_AUTO,
                this.guiLeft + 304,
                this.guiTop + ACTION_ROW_Y,
                68,
                14,
                ellipsized("gtsr.cache_hub_status.auto_off", 68)));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // 按钮可用态/文案随选中节点活取值刷新（等价旧 onUpdateListener + setEnabled）
        CacheNodeInfo cur = selectedInfo();
        boolean offline = cur == null || cur.type.isEmpty();
        boolean rateEnabled = cur != null && !offline;
        boolean capEnabled = cur != null && !offline && supportsCapTier(cur.type);
        boolean modeEnabled = cur != null && !offline && !cur.modeLocked;
        setButtonEnabled(BTN_TELEPORT, cur != null);
        setButtonEnabled(BTN_RATE, rateEnabled);
        setButtonEnabled(BTN_CAP, capEnabled);
        setButtonEnabled(BTN_MODE, modeEnabled);
        setButtonEnabled(BTN_AUTO, rateEnabled);
        setButtonEnabled(BTN_RENAME, cur != null);
        if (this.renameField != null) {
            this.renameField.setEnabled(cur != null);
        }
        if (cur != null) {
            setButtonText(BTN_RATE, cur.rate + "%");
            setButtonText(BTN_CAP, cur.capPct + "%");
            setButtonText(
                BTN_MODE,
                ellipsized(
                    cur.out ? "gtsr.cache_hub_status.mode_short_input" : "gtsr.cache_hub_status.mode_short_output",
                    MODE_BTN_W));
            setButtonText(
                BTN_AUTO,
                ellipsized(cur.auto ? "gtsr.cache_hub_status.auto_on" : "gtsr.cache_hub_status.auto_off", 68));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        // 点击时活取选中节点（旧 currentInfo.get() == null 即忽略的同款守卫）
        final CacheNodeInfo cur = selectedInfo();
        if (cur == null) {
            return;
        }
        switch (button.id) {
            case BTN_RENAME:
                this.sendAction(
                    CacheHubTerminalData.ACTION_RENAME,
                    HubTerminalClientCache.posNamePayload(
                        cur.x,
                        cur.y,
                        cur.z,
                        cur.dim,
                        this.renameField != null ? this.renameField.getText() : ""));
                break;
            case BTN_TELEPORT:
                this.sendAction(
                    CacheHubTerminalData.ACTION_TELEPORT,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            case BTN_RATE:
                this.sendAction(
                    CacheHubTerminalData.ACTION_CYCLE_RATE,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            case BTN_CAP:
                this.sendAction(
                    CacheHubTerminalData.ACTION_CYCLE_CAP,
                    HubTerminalClientCache.posPayload(cur.x, cur.y, cur.z, cur.dim));
                break;
            case BTN_MODE:
                // 目标值 = 客户端取反（旧 sendSetMode 同参）
                this.sendAction(
                    CacheHubTerminalData.ACTION_SET_MODE,
                    HubTerminalClientCache.posBoolPayload(cur.x, cur.y, cur.z, cur.dim, !cur.out));
                break;
            case BTN_AUTO:
                // 目标值 = 客户端取反（旧 sendSetAutoOutput 同参）
                this.sendAction(
                    CacheHubTerminalData.ACTION_SET_AUTO,
                    HubTerminalClientCache.posBoolPayload(cur.x, cur.y, cur.z, cur.dim, !cur.auto));
                break;
            default:
                return;
        }
        this.requestImmediateRefresh();
    }

    @Override
    public void handleMouseInput() {
        if (this.list.handleMouseInput()) {
            return;
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
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawColumnHeaders();
        this.list.draw(mouseX, mouseY, this.zLevel);
        if (rowCount() == 0) {
            this.fontRendererObj.drawStringWithShadow(
                StatCollector.translateToLocal("gtsr.hub_status.empty"),
                this.guiLeft + 14,
                this.guiTop + LIST_Y + 8,
                GtsrGuiPalette.TEXT_MUTED);
        }
        this.drawRenameField();

        final int hovered = this.list.hoveredIndex();
        if (hovered >= 0 && hovered < rowCount() && this.list.hoverElapsedMillis() >= 500) {
            this.drawHoveringText(rowTooltipLines(hovered), mouseX, mouseY, this.fontRendererObj);
        }
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

    /**
     * 单行绘制（400×240 切片 A 列内化）：左列=图标 + 名字（离线红字后缀）+ 流体名（强调）；
     * 右侧储量/容量/速率三列右对齐、行内垂直居中（列头见 {@link #drawColumnHeaders}）。
     * 速率原仅在 500ms 行 tooltip 展示（旧轨同款），切片 A 起列内化为行内唯一来源；
     * 坐标+DIM 为补充信息移入行 tooltip（{@link #rowTooltipLines}）。
     */
    private void paintRow(int index, int x, int y, int mouseX, int mouseY) {
        final List<CacheNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        final CacheNodeInfo info = nodes.get(index);
        final int textX = x + 24;
        final boolean offline = info.type.isEmpty();

        // 节点图标（子类静态映射）；未知类型无图标（旧同款仅省图标位）
        ItemStack iconStack = getNodeIcon(info.type);
        this.renderItemIcon(iconStack, x + 4, y + 2);

        // 左列第 1 行：名字 + 离线红字后缀（名字空回退默认名：图标栈显示名/未知节点）
        String defaultName = iconStack != null ? iconStack.getDisplayName()
            : StatCollector.translateToLocal("gtsr.cache_hub_status.unknown_node");
        String nodeName = info.name.isEmpty() ? defaultName : info.name;
        // 名字与后缀合成一体按左列宽裁剪：先为后缀预留宽度，名字按余宽 ellipsis，后缀紧随其尾不越入数值列
        String offlineSuffix = offline ? " " + StatCollector.translateToLocal("gtsr.cache_hub_status.offline") : null;
        String shownName = GtsrGuiList.ellipsis(
            this.fontRendererObj,
            nodeName,
            offlineSuffix != null ? LEFT_CELL_W - this.fontRendererObj.getStringWidth(offlineSuffix) : LEFT_CELL_W);
        this.fontRendererObj.drawStringWithShadow(shownName, textX, y + 1, GtsrGuiPalette.TEXT_BODY);
        if (offlineSuffix != null) {
            this.fontRendererObj.drawStringWithShadow(
                offlineSuffix,
                textX + this.fontRendererObj.getStringWidth(shownName),
                y + 1,
                GtsrGuiPalette.STATE_OFFLINE);
        }

        // 左列第 2 行：流体名（强调，K/M/G 储量容量已移右侧数值列）
        String fluidText = localizeFluid(info.fluid);
        this.fontRendererObj.drawStringWithShadow(
            GtsrGuiList.ellipsis(this.fontRendererObj, fluidText, LEFT_CELL_W),
            textX,
            y + 10,
            GtsrGuiPalette.TEXT_ACCENT);

        // 右侧数值列（右对齐、y+6 垂直居中；formatKMG K/M/G 逐行移植）
        drawRightAligned(formatKMG(info.stored), x + COL_STORED_RIGHT, y + 6, GtsrGuiPalette.TEXT_BODY);
        drawRightAligned(formatKMG(info.cap), x + COL_CAP_RIGHT, y + 6, GtsrGuiPalette.TEXT_MUTED);
        drawRightAligned(info.rate + "%", x + COL_RATE_RIGHT, y + 6, GtsrGuiPalette.TEXT_ACCENT);
    }

    /** 右缘定位文字绘制（数值列/列头共用；右对齐保证同列逐行对齐） */
    private void drawRightAligned(String text, int rightEdge, int y, int color) {
        this.fontRendererObj
            .drawStringWithShadow(text, rightEdge - this.fontRendererObj.getStringWidth(text), y, color);
    }

    /**
     * 列头标签行（列表上方 {@link GuiTerminalBase#COLUMN_HEADER_Y}，TEXT_LABEL 亮标签 token，
     * 与行内数值列同右缘对齐）。lang 文件不在本切片授权范围：列头沿用行 tooltip 既有英文
     * 小写字面风格（旧 tooltip "rate:"/"cap:" 同款），本地化留待后续 lang 授权切片。
     */
    private void drawColumnHeaders() {
        final int y = this.guiTop + COLUMN_HEADER_Y;
        final int x = this.guiLeft + LIST_X;
        drawRightAligned("stored", x + COL_STORED_RIGHT, y, GtsrGuiPalette.TEXT_LABEL);
        drawRightAligned("cap", x + COL_CAP_RIGHT, y, GtsrGuiPalette.TEXT_LABEL);
        drawRightAligned("rate", x + COL_RATE_RIGHT, y, GtsrGuiPalette.TEXT_LABEL);
    }

    /**
     * 行 tooltip（500ms，补充信息）：名/类型、坐标维度。
     * 400×240 切片 A：流体/储量/容量/速率已列内化为行内唯一来源，不再重复展示。
     */
    private List<String> rowTooltipLines(int index) {
        final List<CacheNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return Collections.emptyList();
        }
        final CacheNodeInfo info = nodes.get(index);
        List<String> lines = new ArrayList<String>();
        lines.add(info.name.isEmpty() ? info.type : info.name);
        lines.add("(" + info.x + ", " + info.y + ", " + info.z + ") DIM: " + info.dim);
        return lines;
    }

    // ==================== 数据活取 ====================

    private List<CacheNodeInfo> matchedSnapshotNodes() {
        HubTerminalClientCache.CacheSnapshot snapshot = HubTerminalClientCache.cacheSnapshot();
        if (snapshot != null && snapshot.matchesAnchor(this.anchorX, this.anchorY, this.anchorZ, this.anchorDim)) {
            return snapshot.nodes;
        }
        return Collections.emptyList();
    }

    private int rowCount() {
        return matchedSnapshotNodes().size();
    }

    /** 选中节点活取（键=pos+dim，旧 findNode 语义） */
    private CacheNodeInfo selectedInfo() {
        if (!this.hasSelection) {
            return null;
        }
        for (CacheNodeInfo node : matchedSnapshotNodes()) {
            if (node.matchesPos(this.selX, this.selY, this.selZ, this.selDim)) {
                return node;
            }
        }
        return null;
    }

    private void selectRow(int index, int mouseX, int mouseY, int button) {
        final List<CacheNodeInfo> nodes = matchedSnapshotNodes();
        if (index < 0 || index >= nodes.size()) {
            return;
        }
        final CacheNodeInfo info = nodes.get(index);
        this.hasSelection = true;
        this.selX = info.x;
        this.selY = info.y;
        this.selZ = info.z;
        this.selDim = info.dim;
        if (this.renameField != null) {
            this.renameField.setText(info.name);
        }
    }

    // ==================== 工具（旧实现逐行移植） ====================

    private void setButtonEnabled(int id, boolean enabled) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton button && button.id == id) {
                button.enabled = enabled;
                return;
            }
        }
    }

    private void setButtonText(int id, String text) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton button && button.id == id) {
                button.displayString = text;
                return;
            }
        }
    }

    private String ellipsized(String langKey, int buttonWidth) {
        return GtsrGuiList.ellipsis(this.fontRendererObj, StatCollector.translateToLocal(langKey), buttonWidth - 4);
    }

    /**
     * 容量档是否适用于该类型串（S4）：发送类仓（*_out）罐只出不进、容量上限无意义，
     * 按钮禁用（旧 supportsCapTier 逐字移植；空 type 为离线行外层已另行禁用）。
     */
    private static boolean supportsCapTier(String type) {
        return type != null && !type.endsWith("_out");
    }

    /**
     * 流体注册名 → 本地化名（客户端按当前语言显示）；空串表示无流体。
     * 服务端只发注册名，避免服务端语言污染客户端（旧 localizeFluid 逐行移植）。
     */
    private static String localizeFluid(String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return StatCollector.translateToLocal("gtsr.cache_hub_status.no_fluid");
        }
        Fluid fluid = FluidRegistry.getFluid(registryName);
        if (fluid == null) return registryName;
        return new FluidStack(fluid, 1).getLocalizedName();
    }

    /**
     * 储量格式化：K/M/G 千位递进、小数点后 2 位；小于 1000 直接显示原值 + "L"
     * （旧 formatKMG 逐行移植，Locale.ROOT 锁定小数点）。
     */
    public static String formatKMG(long amount) {
        if (amount < 1000) return amount + "L";
        final String[] units = { "K", "M", "G" };
        double value = amount;
        int unit = -1;
        do {
            value /= 1000.0;
            unit++;
        } while (value >= 1000.0 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.2f%s", value, units[unit]);
    }

    private static List<String> lines(String line) {
        List<String> list = new ArrayList<String>(1);
        list.add(line);
        return list;
    }
}
