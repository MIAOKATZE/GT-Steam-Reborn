package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.client.terminal.AggregatorClientCache;
import com.miaokatze.gtsr.common.gui.terminal.ContainerAggregatorConfig;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData;
import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData.OreEntry;
import com.miaokatze.gtsr.common.terminal.TerminalNet;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 地壳物质聚合器「终端配置界面」（terminal-native-ui N14，PLAN §4.1 轨 B / §4.3-C / §4.5-B / §7.3）。
 * <p>
 * extends 原版 {@link GuiContainer}（槽位交互走原生窗口包；shift/拖拽语义见
 * {@link ContainerAggregatorConfig}），绘制层 GTSWN 琥珀工业风（panel_aggregator 475×350 整版），
 * 布局与旧 MUI2 GUI（git 基线 b4fabb2 同名源码）同分区（维度槽面板经 UI 修复切片加宽）：
 * <ul>
 * <li><b>左列</b>：「维度槽」标题 + 刷新钮 + 维度 +X% @y44-56；维度槽面板外框 @(18,58) 158×110
 * （LIST_PANEL 9-slice，右缘 176 与浏览器 x180 留 4px 缝；5×5 槽网格 @24,63 步距 32×20，
 * 公式单源见 {@link ContainerAggregatorConfig#GRID_X} 系）；维度说明 @y172；定向模式钮 @y182；
 * 模式提示块 @y204（宽度感知换行，行距 10）；左下使用说明块 @y278（浏览器下缘之下）；</li>
 * <li><b>配置行</b>：矿石模式钮(104×18) + 时运钮(104×18) + 蒸汽消耗文本 @y22-40；</li>
 * <li><b>右侧矿石浏览器</b>：搜索框 + 搜索/种类/清除配置钮（互不重叠 @x326/374/422）、表头
 * （矿石/权重/维度可点击排序，▲/▼ 指示；操作列不排序）、{@link GtsrGuiList} 矿石列表、
 * 逐矿过滤/定向钮、权重 +X% 粗体；</li>
 * <li><b>玩家背包</b> 162×76 右下。</li>
 * </ul>
 * 行为等价映射（PLAN §7.3 逐条）：
 * <ul>
 * <li>8 标量显示格式化函数对照旧实现逐行移植（oreMode 文案、fortune 罗马数字+奇点灰门控、
 * 蒸汽消耗 240/24000×倍率粗体+公式行+tooltip 明细、UU 紫、oreMode 文案、+X% 粗体）；</li>
 * <li>7 动作同参同名（1/2/4/5/7 无参，3/6 携带 uniqueId+meta 旧编码）；动作后 pollTimer 归零
 * 即时补发一次请求（GTSWN 即时反馈范式，等价旧「动作后列表自动刷新」）；</li>
 * <li>浏览器搜索按本地化显示名（兼容中文）、按下搜索钮才应用；种类三态过滤为
 * 客户端会话态（不发包、重开复位，同旧纪律）；矿石/权重/维度表头点击排序（同列再点反转，
 * 稳定排序，操作列不排序）同为会话态；滚动偏移随列表实例自持（数据刷新不回顶）；</li>
 * <li>刷新语义：放入/移除插件槽不自动重建矿池，REFRESH_POOL 显式（服务端语义，见 N36/N32）；</li>
 * <li>轮询 20t（PLAN §4.3-C 冻结；配置标量变化慢，pollTimer 初值 0 首帧即发）；</li>
 * <li>离 64 格/TE 失活：服务端 canInteractWith + tick 复核自动关窗（C0D），
 * 回包 valid=false 时客户端缓存清空并锚点自关（TerminalClientPacketSink）。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class GuiAggregatorConfigScreen extends GuiContainer {

    // ==================== 几何（PLAN §4.5-B，坐标 = 面板内相对坐标，与旧 GUI 常量同名同值） ====================

    /** 面板宽（panel_aggregator 整版 1:1） */
    public static final int PANEL_W = GtsrGuiTextures.PANEL_AGGREGATOR_W;
    /** 面板高 */
    public static final int PANEL_H = GtsrGuiTextures.PANEL_AGGREGATOR_H;

    /** 轮询周期（tick）：PLAN §4.3-C 冻结 20t（配置标量变化慢） */
    public static final int POLL_INTERVAL_TICKS = 20;

    /** tooltip 悬浮延迟（GTSWN 同款 500ms） */
    private static final long TOOLTIP_DELAY_MILLIS = 500L;

    // 左列（维度槽面板：外框与浏览器同款 LIST_PANEL 9-slice；槽位坐标以 Container 公式为唯一权威）
    private static final int LEFT_X = 18;
    private static final int SLOT_TITLE_Y = 45;
    private static final int SLOT_PANEL_X = LEFT_X;
    private static final int SLOT_PANEL_Y = 58;
    /** 面板外框右缘 176，与浏览器 x180 留 4px 缝（左栏可达最大宽；坐标严对 Container 槽区 24..170） */
    private static final int SLOT_PANEL_W = 158;
    private static final int SLOT_PANEL_H = 110;
    private static final int REFRESH_X = LEFT_X + 32;
    private static final int REFRESH_Y = SLOT_TITLE_Y - 1;
    private static final int DIM_INCREASE_X = REFRESH_X + 26 + 4;
    private static final int DIM_TEXT_Y = 172;
    private static final int DIRECTIONAL_Y = 182;
    private static final int HINT_Y = 204;
    private static final int HINT_LINE_PITCH = 10;
    /** 模式提示换行宽（左栏可用宽：x18..173，不触浏览器 x180） */
    private static final int HINT_WRAP_W = 155;
    /** 使用说明块（浏览器下缘 266 之下；右缘 298 在玩家背包 x303 之左） */
    private static final int HELP_Y = 278;
    private static final int HELP_WRAP_W = 280;

    // 配置行（矿石模式/时运/蒸汽消耗）
    private static final int CFG_ROW_Y = 22;
    private static final int CFG_BTN_W = 104;
    private static final int CFG_BTN_H = 18;
    private static final int STEAM_TEXT_X = 224;

    // 右侧矿石浏览器
    private static final int BROWSER_X = 180;
    private static final int BROWSER_Y = 58;
    private static final int BROWSER_W = 290;
    private static final int BROWSER_H = 208;
    private static final int WEIGHT_INCREASE_X = BROWSER_X + 54;
    private static final int SEARCH_Y = 62;
    private static final int HEADER_Y = 82;
    private static final int LIST_X = BROWSER_X + 4;
    private static final int LIST_Y = 100;
    private static final int LIST_W = BROWSER_W - 8;
    private static final int LIST_H = 156;
    // 表格列宽（标签栏与列表行共用，保证逐列对齐）
    private static final int COL_NAME = 100;
    private static final int COL_WEIGHT = 44;
    private static final int COL_DIM = 56;
    private static final int COL_ACTION = 44;
    // 行内列偏移（相对列表左缘：图标 16 + 4 间隔，与标签栏逐列对齐）
    private static final int ROW_ICON_DX = 0;
    private static final int ROW_NAME_DX = 20;
    private static final int ROW_WEIGHT_DX = 124;
    private static final int ROW_DIM_DX = 172;
    private static final int ROW_ACTION_DX = 232;
    private static final int ROW_ACTION_BTN_H = 14;
    // 搜索行控件（搜索框靠左加宽至 x184..322，搜索/种类/清除配置按钮组靠最右，互不重叠、均留 4px 间距）
    private static final int SEARCH_FIELD_X = BROWSER_X + 4;
    private static final int SEARCH_FIELD_W = 138;
    private static final int SEARCH_ROW_BTN_W = 44;
    private static final int SEARCH_ROW_BTN_H = 16;
    private static final int SEARCH_ROW_BTN_GAP = 4;
    private static final int CATEGORY_BTN_X = BROWSER_X + BROWSER_W
        - SEARCH_ROW_BTN_GAP
        - SEARCH_ROW_BTN_W
        - SEARCH_ROW_BTN_GAP
        - SEARCH_ROW_BTN_W;
    private static final int SEARCH_BTN_X = CATEGORY_BTN_X - SEARCH_ROW_BTN_GAP - SEARCH_ROW_BTN_W;
    private static final int CLEAR_BTN_X = BROWSER_X + BROWSER_W - SEARCH_ROW_BTN_GAP - SEARCH_ROW_BTN_W;

    // 表头排序（矿石/权重/维度可点，操作列不排序；命中区 y80..92 与按钮行 y62..78、列表 y100 起互不交叠）
    private static final int SORT_NAME = 0;
    private static final int SORT_WEIGHT = 1;
    private static final int SORT_DIM = 2;
    private static final int HEADER_HIT_Y = 80;
    private static final int HEADER_HIT_H = 12;

    // 按钮 id（本 GUI 内部路由用）
    private static final int BTN_ORE_MODE = 1;
    private static final int BTN_FORTUNE = 2;
    private static final int BTN_REFRESH = 3;
    private static final int BTN_DIRECTIONAL = 4;
    private static final int BTN_SEARCH = 5;
    private static final int BTN_CATEGORY = 6;
    private static final int BTN_CLEAR = 7;

    // ==================== 格式化数据（旧实现常量逐字移植） ====================

    private static final String[] ROMAN_NUMERALS = { "III", "V", "VII", "IX", "XI", "XIII", "XV" };
    private static final String[] ORE_MODE_NAMES = { "raw", "crushed", "purified" };

    // ==================== 会话态（不发包，重开复位——同旧客户端本地纪律） ====================

    /** 已应用的搜索词（按下搜索钮才应用；按本地化显示名匹配，兼容中文） */
    private String searchText = "";
    /** 种类过滤 3 态：0 全部 / 1 未过滤 / 2 已过滤（排序解耦至表头点击，本钮纯过滤） */
    private int categoryMode = 0;
    /** 表头排序键：0 矿石显示名 / 1 权重 / 2 维度（操作列不可排序）；会话态，重开复位 */
    private int sortKey = SORT_NAME;
    /** 当前排序方向（true 升序；同列再点反转） */
    private boolean sortAsc = true;

    // ==================== 运行时组件 ====================

    private final ContainerAggregatorConfig containerAggregator;
    private GuiTextField searchField;
    private GtsrGuiList oreList;

    /** 轮询计时（初值 0：首个 updateScreen 立即发送首帧请求） */
    private int pollTimer = 0;

    /** 每帧缓存：可见矿石行（快照/搜索词/种类/排序键/方向五元组变化才重算） */
    private List<OreEntry> visibleCache = Collections.emptyList();
    private AggregatorClientCache.Snapshot cacheSnap;
    private String cacheSearch;
    private int cacheCategory = -1;
    private int cacheSortKey = -1;
    private boolean cacheSortAsc;

    /** 本帧悬浮命中的行内过滤/定向钮条目（paintRow 逐行登记，drawTooltips 消费后清空） */
    private OreEntry hoveredRowButtonEntry;

    // ---- 500ms tooltip 状态（键相等性按 Objects.equals，跨帧同一命中才累计时间） ----
    private Object tooltipKey;
    private long tooltipStartMillis;

    public GuiAggregatorConfigScreen(ContainerAggregatorConfig container) {
        super(container);
        this.containerAggregator = container;
    }

    // ==================== 生命周期 ====================

    @Override
    public void initGui() {
        this.xSize = PANEL_W;
        this.ySize = PANEL_H;
        super.initGui(); // guiLeft/guiTop = ((width-xSize)/2, (height-ySize)/2)
        this.searchField = new GuiTextField(
            this.fontRendererObj,
            this.guiLeft + SEARCH_FIELD_X,
            this.guiTop + SEARCH_Y + 1,
            SEARCH_FIELD_W,
            14);
        this.searchField.setMaxStringLength(32);
        this.searchField.setFocused(false);
        this.oreList = new GtsrGuiList(this, this.guiLeft + LIST_X, this.guiTop + LIST_Y, LIST_W, LIST_H);
        this.oreList.setRowSource(this::rowCount);
        this.oreList.setRowPainter(this::paintRow);
        this.oreList.setRowListener(this::rowClicked);

        this.buttonList
            .add(new GtsrGuiButton(BTN_ORE_MODE, this.guiLeft + 8, this.guiTop + CFG_ROW_Y, CFG_BTN_W, CFG_BTN_H, ""));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_FORTUNE,
                this.guiLeft + 8 + CFG_BTN_W + 4,
                this.guiTop + CFG_ROW_Y,
                CFG_BTN_W,
                CFG_BTN_H,
                ""));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_REFRESH,
                this.guiLeft + REFRESH_X,
                this.guiTop + REFRESH_Y,
                26,
                12,
                ellipsized(StatCollector.translateToLocal("gtsr.aggregator_config.refresh"), 26)));
        this.buttonList
            .add(new GtsrGuiButton(BTN_DIRECTIONAL, this.guiLeft + LEFT_X, this.guiTop + DIRECTIONAL_Y, 90, 18, ""));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_SEARCH,
                this.guiLeft + SEARCH_BTN_X,
                this.guiTop + SEARCH_Y,
                SEARCH_ROW_BTN_W,
                SEARCH_ROW_BTN_H,
                ellipsized(StatCollector.translateToLocal("gtsr.aggregator_config.search"), SEARCH_ROW_BTN_W)));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_CATEGORY,
                this.guiLeft + CATEGORY_BTN_X,
                this.guiTop + SEARCH_Y,
                SEARCH_ROW_BTN_W,
                SEARCH_ROW_BTN_H,
                ""));
        this.buttonList.add(
            new GtsrGuiButton(
                BTN_CLEAR,
                this.guiLeft + CLEAR_BTN_X,
                this.guiTop + SEARCH_Y,
                SEARCH_ROW_BTN_W,
                SEARCH_ROW_BTN_H,
                ellipsized(StatCollector.translateToLocal("gtsr.aggregator_config.clear_config"), SEARCH_ROW_BTN_W)));
        updateDynamicLabels();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
        if (--this.pollTimer <= 0) {
            this.pollTimer = POLL_INTERVAL_TICKS;
            sendRequest();
        }
        updateDynamicLabels();
        // 时运按钮：仅原矿模式禁用（超限档位由服务端 checkProcessing 运行前自动钳位）
        AggregatorClientCache.Snapshot snap = currentSnapshot();
        setButtonEnabled(BTN_FORTUNE, snap != null && snap.data.oreMode != 0);
    }

    /** 动态文案按钮（矿石模式/时运/定向/种类）随快照与会话态刷新（等价旧 IKey.dynamic/onUpdateListener） */
    private void updateDynamicLabels() {
        AggregatorClientCache.Snapshot snap = currentSnapshot();
        if (snap != null) {
            setButtonLabel(BTN_ORE_MODE, ellipsized(formatOreModeLabel(snap.data.oreMode), CFG_BTN_W));
            setButtonLabel(BTN_FORTUNE, ellipsized(formatFortuneLabel(snap.data.fortune), CFG_BTN_W));
            setButtonLabel(
                BTN_DIRECTIONAL,
                ellipsized(
                    StatCollector.translateToLocal(
                        snap.data.directionalMode ? "gtsr.aggregator_config.directional.on"
                            : "gtsr.aggregator_config.directional.off"),
                    90));
        }
        setButtonLabel(
            BTN_CATEGORY,
            ellipsized(StatCollector.translateToLocal(categoryLangKey(this.categoryMode)), SEARCH_ROW_BTN_W));
    }

    // ==================== 网络动作（7 动作同参同名，PLAN §7.3-3） ====================

    private void sendRequest() {
        int x = 0, y = 0, z = 0, dim = 0;
        IGregTechTileEntity base = this.containerAggregator.getAggregator()
            .getBaseMetaTileEntity();
        if (base != null) {
            x = base.getXCoord();
            y = base.getYCoord();
            z = base.getZCoord();
        }
        dim = this.mc.theWorld != null ? this.mc.theWorld.provider.dimensionId : 0;
        TerminalNet.sendRequestFromClient(TerminalUiType.AGGREGATOR, dim, x, y, z);
    }

    private void sendAction(int actionCode, byte[] payload) {
        IGregTechTileEntity base = this.containerAggregator.getAggregator()
            .getBaseMetaTileEntity();
        if (base == null || this.mc.theWorld == null) {
            return;
        }
        TerminalNet.sendActionFromClient(
            TerminalUiType.AGGREGATOR,
            this.mc.theWorld.provider.dimensionId,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            actionCode,
            payload);
        // 动作执行后 pollTimer 归零 → 下个 updateScreen 立即补发一次请求（GTSWN 即时反馈范式）
        this.pollTimer = 0;
    }

    /** 动作码（N32 冻结）：1/2/4/5/7 无参，3/6 携带 uniqueId+meta 旧编码 */
    private void sendOreToggle(OreEntry entry, boolean directional) {
        byte[] payload = AggregatorClientCache.oreIdPayload(entry.uniqueId, entry.meta);
        this.sendAction(
            directional ? AggregatorTerminalData.ACTION_TOGGLE_DIRECTIONAL_ORE
                : AggregatorTerminalData.ACTION_TOGGLE_FILTER,
            payload);
    }

    // ==================== 输入分发（列表/搜索框优先，槽位走 vanilla） ====================

    @Override
    public void handleMouseInput() {
        if (this.oreList != null && this.oreList.handleMouseInput()) {
            return; // 滚轮命中列表区：消费（GtsrGuiList 偏移自持）
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        int sortHit = headerSortHit(mouseX, mouseY);
        if (sortHit >= 0) {
            // 表头命中：同列再点反转，换列重置升序（会话态，不发包；命中区与按钮/列表矩形互不交叠）
            if (this.sortKey == sortHit) {
                this.sortAsc = !this.sortAsc;
            } else {
                this.sortKey = sortHit;
                this.sortAsc = true;
            }
            return;
        }
        if (this.oreList != null && this.oreList.mouseClicked(mouseX, mouseY, mouseButton)) {
            return; // 浏览器列表区消费（含逐矿过滤/定向钮）
        }
        super.mouseClicked(mouseX, mouseY, mouseButton); // GuiScreen 按钮分发 + GuiContainer 槽位交互
    }

    /**
     * 表头排序命中（y80..92：按钮行 y62..78 之下、列表 y100 之上，无交叠）：
     *
     * @return 命中的排序键（矿石/权重/维度），未命中或操作列/列间隙返回 -1
     */
    private int headerSortHit(int mouseX, int mouseY) {
        final int relX = mouseX - (this.guiLeft + LIST_X);
        final int relY = mouseY - (this.guiTop + HEADER_HIT_Y);
        if (relY < 0 || relY >= HEADER_HIT_H) {
            return -1;
        }
        if (relX >= ROW_NAME_DX && relX < ROW_NAME_DX + COL_NAME) {
            return SORT_NAME;
        }
        if (relX >= ROW_WEIGHT_DX && relX < ROW_WEIGHT_DX + COL_WEIGHT) {
            return SORT_WEIGHT;
        }
        if (relX >= ROW_DIM_DX && relX < ROW_DIM_DX + COL_DIM) {
            return SORT_DIM;
        }
        return -1;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.oreList != null) {
            this.oreList.mouseClickMove(mouseX, mouseY, clickedMouseButton);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick); // 槽位拖拽（vanilla 默认）
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (this.oreList != null) {
            this.oreList.mouseReleased(mouseX, mouseY, state);
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 搜索框纯本地输入：持有焦点时先于全局快捷键消费；ESC 不被文本框处理 → 关闭 GUI（vanilla C0D）
        if (this.searchField != null && this.searchField.isFocused()
            && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_ORE_MODE:
                this.sendAction(AggregatorTerminalData.ACTION_CYCLE_ORE_MODE, AggregatorClientCache.emptyPayload());
                break;
            case BTN_FORTUNE:
                this.sendAction(AggregatorTerminalData.ACTION_CYCLE_FORTUNE, AggregatorClientCache.emptyPayload());
                break;
            case BTN_REFRESH:
                // 手动刷新矿池：放入/移除插件槽不自动重建，REFRESH_POOL 显式（PLAN §7.3-6）
                this.sendAction(AggregatorTerminalData.ACTION_REFRESH_POOL, AggregatorClientCache.emptyPayload());
                break;
            case BTN_DIRECTIONAL:
                this.sendAction(AggregatorTerminalData.ACTION_TOGGLE_DIRECTIONAL, AggregatorClientCache.emptyPayload());
                break;
            case BTN_CLEAR:
                this.sendAction(AggregatorTerminalData.ACTION_CLEAR_CONFIG, AggregatorClientCache.emptyPayload());
                break;
            case BTN_SEARCH:
                // 搜索按下才应用（按本地化显示名匹配，兼容中文）；纯客户端会话态，不发包
                this.searchText = this.searchField != null ? this.searchField.getText()
                    .trim() : "";
                break;
            case BTN_CATEGORY:
                // 种类过滤 3 态循环（排序已解耦至表头点击）；纯客户端会话态，不发包
                this.categoryMode = (this.categoryMode + 1) % 3;
                break;
            default:
                return;
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // 暗底由 GuiContainer.drawScreen 首行 drawDefaultBackground() 绘制（此处不重复调用，防双重压暗）
        // 整版面板 1:1（契约 §3 #2，整绘不用 9-slice）
        GtsrGuiDrawing.drawRegion(
            GtsrGuiTextures.PANEL_AGGREGATOR,
            this.guiLeft,
            this.guiTop,
            0,
            0,
            PANEL_W,
            PANEL_H,
            this.zLevel);
        // 维度槽面板外框（与浏览器同款 LIST_PANEL 凹陷 9-slice；右缘 176 与浏览器 x180 留 4px 缝）
        GtsrGuiDrawing.drawNineSlice(
            GtsrGuiTextures.LIST_PANEL,
            4,
            this.guiLeft + SLOT_PANEL_X,
            this.guiTop + SLOT_PANEL_Y,
            SLOT_PANEL_W,
            SLOT_PANEL_H,
            this.zLevel);
        // 浏览器背景框（内嵌区装饰）
        GtsrGuiDrawing.drawNineSlice(
            GtsrGuiTextures.LIST_PANEL,
            4,
            this.guiLeft + BROWSER_X,
            this.guiTop + BROWSER_Y,
            BROWSER_W,
            BROWSER_H,
            this.zLevel);
        // 槽格 slot_frame 18×18（25 机器槽 + 36 玩家背包槽；物品区 16×16，框外扩 1px）
        for (Object o : this.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            GtsrGuiDrawing.drawNineSlice(
                GtsrGuiTextures.SLOT_FRAME,
                4,
                this.guiLeft + slot.xDisplayPosition - 1,
                this.guiTop + slot.yDisplayPosition - 1,
                GtsrGuiTextures.SLOT_FRAME_W,
                GtsrGuiTextures.SLOT_FRAME_H,
                this.zLevel);
        }
        drawTexts();
        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }
    }

    /** 静态位置文本（标题/标签/动态值/公式行），颜色与旧 GUI EnumChatFormatting 语义逐字对应 */
    private void drawTexts() {
        // 标题（TEXT_TITLE token，GTSWN 面板解剖学）
        this.fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal("gtsr.aggregator_config.title"),
            this.guiLeft + 8,
            this.guiTop + 6,
            GtsrGuiPalette.TEXT_TITLE);
        AggregatorClientCache.Snapshot snap = currentSnapshot();
        boolean directional = snap != null && snap.data.directionalMode;
        // 左列标题 + 浏览器标题
        this.fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal("gtsr.aggregator_config.slots_title"),
            this.guiLeft + LEFT_X,
            this.guiTop + SLOT_TITLE_Y,
            GtsrGuiPalette.TEXT_BODY);
        this.fontRendererObj.drawStringWithShadow(
            StatCollector.translateToLocal("gtsr.aggregator_config.browser_title"),
            this.guiLeft + BROWSER_X,
            this.guiTop + SLOT_TITLE_Y,
            GtsrGuiPalette.TEXT_BODY);
        // 消耗增加% 实际值（粗体 "+X%"，白字提亮）：维度项（刷新钮右侧）与权重项（浏览器标题右侧）
        if (snap != null) {
            this.fontRendererObj.drawString(
                formatIncreaseValue(snap.data.dimIncrease),
                this.guiLeft + DIM_INCREASE_X,
                this.guiTop + SLOT_TITLE_Y,
                GtsrGuiPalette.TEXT_WHITE);
            this.fontRendererObj.drawString(
                formatIncreaseValue(snap.data.weightIncrease),
                this.guiLeft + WEIGHT_INCREASE_X,
                this.guiTop + SLOT_TITLE_Y,
                GtsrGuiPalette.TEXT_WHITE);
            // 蒸汽消耗文本（两行：粗体主行带金色倍率后缀 + 消耗量公式行；暗色 § 码改 TEXT_BODY 基色提亮）
            List<StyledLine> steamLines = new ArrayList<StyledLine>();
            buildSteamCostLines(steamLines, snap.data.steamMult, snap.data.denseState);
            drawStyledLines(steamLines, STEAM_TEXT_X, CFG_ROW_Y + 1);
            // 维度消耗说明文本（槽面板下方；原 §0 黑字在暗面板不可读 → TEXT_BODY 提亮，语义不变）
            this.fontRendererObj.drawString(
                StatCollector.translateToLocal(
                    directional ? "gtsr.aggregator_config.dim_text.directional"
                        : "gtsr.aggregator_config.dim_text.filtered"),
                this.guiLeft + LEFT_X,
                this.guiTop + DIM_TEXT_Y,
                GtsrGuiPalette.TEXT_BODY);
            // 当前模式提示块（宽度感知换行；琥珀标题 + 正文提亮，定向模式追加 UU 消耗段）
            drawModeHintBlock(directional, snap.data.uuMult);
        }
        // 左下使用说明块（静态；浏览器/玩家背包之外留白区，宽度感知换行）
        drawHelpBlock();
        // 表格标签栏（橙加粗小字，与列表行逐列对齐；当前排序列尾随 ▲/▼，操作列不可排序）
        drawHeaderLabel(SORT_NAME, "gtsr.aggregator_config.col.name", ROW_NAME_DX);
        drawHeaderLabel(SORT_WEIGHT, "gtsr.aggregator_config.col.weight", ROW_WEIGHT_DX);
        drawHeaderLabel(SORT_DIM, "gtsr.aggregator_config.col.dim", ROW_DIM_DX);
        this.fontRendererObj.drawString(
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                + StatCollector.translateToLocal("gtsr.aggregator_config.col.action"),
            this.guiLeft + LIST_X + ROW_ACTION_DX,
            this.guiTop + HEADER_Y,
            0xFFFFFF);
    }

    /** 单个可排序列标签（金加粗；当前排序列尾随 ▲/▼ 方向指示） */
    private void drawHeaderLabel(int sortKey, String langKey, int columnDx) {
        String suffix = this.sortKey != sortKey ? "" : this.sortAsc ? " ▲" : " ▼";
        this.fontRendererObj.drawString(
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                + StatCollector.translateToLocal(langKey)
                + suffix,
            this.guiLeft + LIST_X + columnDx,
            this.guiTop + HEADER_Y,
            0xFFFFFF);
    }

    /** 左下使用说明块：槽位用法/筛选语义/定向语义三段，按 HELP_WRAP_W 宽度感知换行（TEXT_LABEL 弱化基色） */
    private void drawHelpBlock() {
        List<StyledLine> lines = new ArrayList<StyledLine>();
        addWrapped(
            lines,
            StatCollector.translateToLocal("gtsr.aggregator_config.help.slots"),
            HELP_WRAP_W,
            GtsrGuiPalette.TEXT_LABEL);
        addWrapped(
            lines,
            StatCollector.translateToLocal("gtsr.aggregator_config.help.filter"),
            HELP_WRAP_W,
            GtsrGuiPalette.TEXT_LABEL);
        addWrapped(
            lines,
            StatCollector.translateToLocal("gtsr.aggregator_config.help.directional"),
            HELP_WRAP_W,
            GtsrGuiPalette.TEXT_LABEL);
        drawStyledLines(lines, LEFT_X, HELP_Y);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks); // 背景层 + 按钮 + 槽位物品/角标 + 光标物品 + 槽位 tooltip
        this.hoveredRowButtonEntry = null;
        this.oreList.draw(mouseX, mouseY, this.zLevel);
        if (rowCount() == 0) {
            // 旧 gtsr.aggregator_config.empty 空态文案保留
            this.fontRendererObj.drawStringWithShadow(
                EnumChatFormatting.WHITE.toString() + StatCollector.translateToLocal("gtsr.aggregator_config.empty"),
                this.guiLeft + LIST_X + 4,
                this.guiTop + LIST_Y + 6,
                0xFFFFFF);
        }
        drawTooltips(mouseX, mouseY);
    }

    // ==================== 矿石浏览器数据（活取 + 每帧缓存） ====================

    private AggregatorClientCache.Snapshot currentSnapshot() {
        IGregTechTileEntity base = this.containerAggregator.getAggregator()
            .getBaseMetaTileEntity();
        if (base == null) {
            return null;
        }
        AggregatorClientCache.Snapshot snap = AggregatorClientCache.snapshot();
        return snap != null && snap.matchesAnchor(base.getXCoord(), base.getYCoord(), base.getZCoord(), worldDim())
            ? snap
            : null;
    }

    private int worldDim() {
        return this.mc.theWorld != null ? this.mc.theWorld.provider.dimensionId : 0;
    }

    private int rowCount() {
        return visibleEntries().size();
    }

    /** 可见矿石行（种类过滤 + 搜索 + 表头排序；快照/搜索词/种类/排序键/方向五元组变化才重算） */
    private List<OreEntry> visibleEntries() {
        AggregatorClientCache.Snapshot snap = currentSnapshot();
        List<OreEntry> all = snap != null ? snap.data.ores : Collections.<OreEntry>emptyList();
        if (snap != this.cacheSnap || !Objects.equals(this.searchText, this.cacheSearch)
            || this.categoryMode != this.cacheCategory
            || this.sortKey != this.cacheSortKey
            || this.sortAsc != this.cacheSortAsc) {
            this.visibleCache = filterAndSort(all);
            this.cacheSnap = snap;
            this.cacheSearch = this.searchText;
            this.cacheCategory = this.categoryMode;
            this.cacheSortKey = this.sortKey;
            this.cacheSortAsc = this.sortAsc;
        }
        return this.visibleCache;
    }

    /**
     * 客户端过滤与排序（排序/过滤解耦）：种类三态（全部/未过滤/已过滤）+ 搜索词
     * （矿石本地化显示名，兼容中文）过滤，再按表头 sortKey 排序——List.sort 为 TimSort
     * 稳定排序，等值条目保持矿池原序；sortAsc=false 用反向比较器（同向等值仍保序）。
     */
    private List<OreEntry> filterAndSort(List<OreEntry> all) {
        List<OreEntry> visible = new ArrayList<OreEntry>();
        if (all != null) {
            for (OreEntry entry : all) {
                if (entry == null) continue;
                if (this.categoryMode == 1 && entry.filtered) continue;
                if (this.categoryMode == 2 && !entry.filtered) continue;
                if (!this.searchText.isEmpty() && !entryDisplayName(entry).toLowerCase()
                    .contains(this.searchText.toLowerCase())) {
                    continue;
                }
                visible.add(entry);
            }
        }
        visible.sort((a, b) -> {
            int c;
            switch (this.sortKey) {
                case SORT_WEIGHT:
                    c = Float.compare(a.weight, b.weight);
                    break;
                case SORT_DIM:
                    c = String.join("+", a.dimAbbrs)
                        .compareToIgnoreCase(String.join("+", b.dimAbbrs));
                    break;
                case SORT_NAME:
                default:
                    c = entryDisplayName(a).compareToIgnoreCase(entryDisplayName(b));
                    break;
            }
            return this.sortAsc ? c : -c;
        });
        return visible;
    }

    /** 条目显示名（本地化显示名，兼容中文；displayStack 解析失败回退 uniqueId 文本） */
    private static String entryDisplayName(OreEntry entry) {
        return entry.displayStack != null ? entry.displayStack.getDisplayName() : entry.uniqueId;
    }

    // ==================== 列表行绘制（字段与旧 OreEntryInfo 行一一对应） ====================

    private void paintRow(int index, int x, int y, int mouseX, int mouseY) {
        final List<OreEntry> visible = visibleEntries();
        if (index < 0 || index >= visible.size()) {
            return;
        }
        final OreEntry entry = visible.get(index);
        // 矿石图标（16×16 renderItem）
        this.renderItemIcon(entry.displayStack, x + ROW_ICON_DX, y + 2);
        // 本地化名（白加粗）+ 权重（金加粗）+ 维度缩写（金加粗 "Ow+Ne"）
        String name = GtsrGuiList.ellipsis(
            this.fontRendererObj,
            EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + entryDisplayName(entry),
            COL_NAME);
        this.fontRendererObj.drawString(name, x + ROW_NAME_DX, y + 6, 0xFFFFFF);
        String weight = GtsrGuiList.ellipsis(
            this.fontRendererObj,
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + formatWeight(entry.weight),
            COL_WEIGHT);
        this.fontRendererObj.drawString(weight, x + ROW_WEIGHT_DX, y + 6, 0xFFFFFF);
        String dims = GtsrGuiList.ellipsis(
            this.fontRendererObj,
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + String.join("+", entry.dimAbbrs),
            COL_DIM);
        this.fontRendererObj.drawString(dims, x + ROW_DIM_DX, y + 6, 0xFFFFFF);
        // 逐矿过滤/定向钮（44×14；定向模式切换「定向」，否则切换「过滤」）
        boolean directional = this.cacheSnap != null && this.cacheSnap.data.directionalMode;
        String actionKey;
        if (directional) {
            actionKey = entry.aimed ? "gtsr.aggregator_config.directional_ore.off"
                : "gtsr.aggregator_config.directional_ore";
        } else {
            actionKey = entry.filtered ? "gtsr.aggregator_config.unfilter" : "gtsr.aggregator_config.filter";
        }
        boolean hovered = mouseX >= x + ROW_ACTION_DX && mouseX < x + ROW_ACTION_DX + COL_ACTION
            && mouseY >= y + 3
            && mouseY < y + 3 + ROW_ACTION_BTN_H;
        if (hovered) {
            this.hoveredRowButtonEntry = entry; // 登记本帧行内钮命中（drawTooltips 消费）
        }
        GtsrGuiDrawing.drawNineSlice(
            hovered ? GtsrGuiTextures.BUTTON_HOVER : GtsrGuiTextures.BUTTON_NORMAL,
            4,
            x + ROW_ACTION_DX,
            y + 3,
            COL_ACTION,
            ROW_ACTION_BTN_H,
            this.zLevel);
        String actionLabel = GtsrGuiList
            .ellipsis(this.fontRendererObj, StatCollector.translateToLocal(actionKey), COL_ACTION - 4);
        this.fontRendererObj.drawString(
            actionLabel,
            x + ROW_ACTION_DX + (COL_ACTION - this.fontRendererObj.getStringWidth(actionLabel)) / 2,
            y + 3 + (ROW_ACTION_BTN_H - 8) / 2,
            0xFFFFFF);
    }

    /** 行点击：仅逐矿过滤/定向钮区域生效（定向模式发 TOGGLE_DIRECTIONAL_ORE，否则 TOGGLE_FILTER） */
    private void rowClicked(int index, int mouseX, int mouseY, int button) {
        final List<OreEntry> visible = visibleEntries();
        if (index < 0 || index >= visible.size() || button != 0) {
            return;
        }
        boolean withinActionColumn = mouseX >= this.guiLeft + LIST_X + ROW_ACTION_DX
            && mouseX < this.guiLeft + LIST_X + ROW_ACTION_DX + COL_ACTION;
        if (!withinActionColumn) {
            return; // 行内其余区域消费不动作（与旧行为一致：仅按钮响应）
        }
        OreEntry entry = visible.get(index);
        boolean directional = this.cacheSnap != null && this.cacheSnap.data.directionalMode;
        this.sendOreToggle(entry, directional);
    }

    // ==================== tooltip（500ms，GTSWN 范式） ====================

    private void drawTooltips(int mouseX, int mouseY) {
        // 逐矿过滤/定向钮 tooltip（paintRow 本帧登记的行内钮命中）
        if (this.hoveredRowButtonEntry != null) {
            final OreEntry entry = this.hoveredRowButtonEntry;
            final boolean directional = this.cacheSnap != null && this.cacheSnap.data.directionalMode;
            List<String> rowLines = new ArrayList<String>();
            if (directional) {
                rowLines.add(StatCollector.translateToLocal("gtsr.aggregator_config.directional_ore_hint"));
            } else if (entry.filtered) {
                rowLines.add(StatCollector.translateToLocal("gtsr.aggregator_config.unfilter"));
                rowLines.add(StatCollector.translateToLocal("gtsr.aggregator_config.filtered_hint"));
            } else {
                rowLines.add(StatCollector.translateToLocal("gtsr.aggregator_config.filter"));
            }
            this.hoverTooltip("rowBtn" + System.identityHashCode(entry), rowLines, mouseX, mouseY);
            return;
        }
        // 槽 1 提示（控制器槽说明；命中矩形随 Container 槽位公式同源）
        if (inRect(
            mouseX,
            mouseY,
            this.guiLeft + ContainerAggregatorConfig.GRID_X,
            this.guiTop + ContainerAggregatorConfig.GRID_Y,
            18,
            18)) {
            this.hoverTooltip(
                "slot1",
                lines(StatCollector.translateToLocal("gtsr.aggregator_config.slot1_hint")),
                mouseX,
                mouseY);
            return;
        }
        // 蒸汽消耗文本 tooltip（基准 + 倍率 + 明细）
        if (inRect(mouseX, mouseY, this.guiLeft + STEAM_TEXT_X, this.guiTop + CFG_ROW_Y, 236, 22)) {
            AggregatorClientCache.Snapshot snap = currentSnapshot();
            if (snap != null) {
                List<String> lines = new ArrayList<String>();
                lines.add(StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.tip"));
                lines.add(EnumChatFormatting.GRAY + "× " + String.format("%.2f", snap.data.steamMult));
                lines.add(steamDetailLine(snap.data));
                this.hoverTooltip("steam", lines, mouseX, mouseY);
                return;
            }
        }
        // 搜索框 + 搜索钮：search_hint
        if (this.searchField != null
            && inRect(mouseX, mouseY, this.searchField.xPosition, this.searchField.yPosition, SEARCH_FIELD_W, 14)) {
            this.hoverTooltip(
                "field",
                lines(StatCollector.translateToLocal("gtsr.aggregator_config.search_hint")),
                mouseX,
                mouseY);
            return;
        }
        // 常规按钮 tooltip
        GuiButton hoveredButton = null;
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton btn && btn.visible
                && inRect(mouseX, mouseY, btn.xPosition, btn.yPosition, btn.width, btn.height)) {
                hoveredButton = btn;
                break;
            }
        }
        if (hoveredButton == null) {
            this.hoverTooltip(null, null, mouseX, mouseY);
            return;
        }
        List<String> lines = null;
        switch (hoveredButton.id) {
            case BTN_ORE_MODE:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.ore_mode.tip"));
                break;
            case BTN_FORTUNE:
                lines = new ArrayList<String>();
                lines.add(StatCollector.translateToLocal("gtsr.aggregator_config.fortune.tip"));
                lines.add(StatCollector.translateToLocal("gtsr.aggregator_config.fortune.gate"));
                break;
            case BTN_REFRESH:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.refresh.tip"));
                break;
            case BTN_DIRECTIONAL:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.directional.tip"));
                break;
            case BTN_SEARCH:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.search_hint"));
                break;
            case BTN_CATEGORY:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.category.tip"));
                break;
            case BTN_CLEAR:
                lines = lines(StatCollector.translateToLocal("gtsr.aggregator_config.clear_config.tip"));
                break;
            default:
                break;
        }
        this.hoverTooltip("btn" + hoveredButton.id, lines, mouseX, mouseY);
    }

    /**
     * 悬浮命中登记：跨帧同一 key 才累计时间，≥500ms 画 drawHoveringText；
     * key 传 null = 本帧无命中（重置计时）。
     */
    private void hoverTooltip(Object key, List<String> lines, int mouseX, int mouseY) {
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

    private boolean inRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static List<String> lines(String line) {
        List<String> list = new ArrayList<String>(1);
        list.add(line);
        return list;
    }

    // ==================== 格式化函数（旧实现逐行移植，§7.3-4） ====================

    /** 矿石模式按钮文案：模式名 + 蒸汽加成百分比（如「粉碎矿 +200%」）。 */
    private static String formatOreModeLabel(int mode) {
        int m = Math.min(Math.max(mode, 0), 2);
        String name = StatCollector.translateToLocal("gtsr.aggregator_config.ore_mode." + ORE_MODE_NAMES[m]);
        int bonus = (int) Math.round(MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[m] * 100.0d);
        return name + " +" + bonus + "%";
    }

    /** 时运按钮文案：罗马数字 + 蒸汽消耗%（键值含 %s 与 %d 占位，如「时运 III +0%」）；level 为档位值（3-15 奇数）。 */
    private static String formatFortuneLabel(int level) {
        int idx = Math.min(Math.max((level - 3) / 2, 0), 6);
        String roman = ROMAN_NUMERALS[idx];
        int bonus = (int) Math.round(MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[idx] * 100.0d);
        return String.format(StatCollector.translateToLocal("gtsr.aggregator_config.fortune_level"), roman, bonus);
    }

    /** 消耗增加% 实际值（粗体 "+X%"；筛选/定向按模式由服务端算好）：色由调用方 TEXT_WHITE 基色提亮（原 §0 黑改亮）。 */
    private static String formatIncreaseValue(double percent) {
        return EnumChatFormatting.BOLD.toString() + "+" + String.format("%.0f", percent) + "%";
    }

    /**
     * 蒸汽消耗 tooltip 明细行（旧 lambda 语义移植）：明细 =（1+矿石模式加成+时运加成）×（维度槽/过滤加成）；
     * 定向模式：维度项（1+dimIncrease/100，随额外维度槽数动态）× 定向倍率（定向倍率 = UU 倍率 ÷ 模式/时运加成反推）。
     */
    private static String steamDetailLine(AggregatorTerminalData.Snapshot snap) {
        int mode = snap.oreMode;
        int fortune = snap.fortune;
        double modeFortune = 1.0d + MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[Math.min(Math.max(mode, 0), 2)]
            + MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[Math.min(Math.max((fortune - 3) / 2, 0), 6)];
        if (snap.directionalMode) {
            double dirFactor = snap.uuMult / modeFortune;
            // v1.10.55：定向维度项 = 1 + (200% + 20%/额外维度槽)/100，随槽数动态
            double dimFactor = 1.0d + snap.dimIncrease / 100.0d;
            return EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.detail_dir")
                + String.format("%.2f", modeFortune)
                + " × "
                + String.format("%.2f", dimFactor)
                + " × "
                + String.format("%.2f", dirFactor);
        }
        double env = snap.steamMult / modeFortune;
        return EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.detail")
            + String.format("%.2f", modeFortune)
            + " × "
            + String.format("%.2f", env);
    }

    /**
     * 当前模式提示块（宽度感知换行，行距 10px @HINT_Y）：首行模式标题（TEXT_ACCENT 琥珀，原 §1 蓝
     * 在暗面板不可读）；正文整段按左栏可用宽 155px 换行（TEXT_BODY 基色，原 §0 黑提亮）；
     * 定向模式底部追加 UU 消耗段（保持 §d 语义紫、§ 码随断行携带）+ 消耗量公式行。
     * 文案取单键整段（mode_hint.<模式> / .title），替代原 .1~N 死分行键。
     */
    private void drawModeHintBlock(boolean directional, double uuMult) {
        List<StyledLine> lines = new ArrayList<StyledLine>();
        lines.add(
            new StyledLine(
                StatCollector.translateToLocal(
                    directional ? "gtsr.aggregator_config.mode_hint.directional.title"
                        : "gtsr.aggregator_config.mode_hint.filtered.title"),
                GtsrGuiPalette.TEXT_ACCENT));
        addWrapped(
            lines,
            StatCollector.translateToLocal(
                directional ? "gtsr.aggregator_config.mode_hint.directional"
                    : "gtsr.aggregator_config.mode_hint.filtered"),
            HINT_WRAP_W,
            GtsrGuiPalette.TEXT_BODY);
        if (directional) {
            // UU 消耗段（紫色语义保持）：前缀 + 实际速率（含倍率）合段换行 + 消耗量公式行
            String rate = NumberFormatUtil.formatNumber(Math.round(uuMult)) + " L/s"
                + String.format(
                    StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost.mult"),
                    String.format("%.2f", uuMult));
            addWrapped(
                lines,
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost")
                    + rate,
                HINT_WRAP_W,
                GtsrGuiPalette.TEXT_WHITE);
            addWrapped(
                lines,
                StatCollector.translateToLocal("gtsr.aggregator_config.uu_formula"),
                HINT_WRAP_W,
                GtsrGuiPalette.TEXT_BODY);
        }
        drawStyledLines(lines, LEFT_X, HINT_Y);
    }

    /** 权重显示：整数输出整数，否则一位小数。 */
    private static String formatWeight(float weight) {
        if (!Float.isInfinite(weight) && weight == Math.floor(weight)) {
            return String.format("%.0f", weight);
        }
        return String.format("%.1f", weight);
    }

    /** 种类过滤 3 态：全部 / 未过滤 / 已过滤（排序已解耦至表头点击，asc/desc 键随 5 态一并删除）。 */
    private static String categoryLangKey(int mode) {
        switch (mode) {
            case 1:
                return "gtsr.aggregator_config.category.unfiltered";
            case 2:
                return "gtsr.aggregator_config.category.filtered";
            default:
                return "gtsr.aggregator_config.category.all";
        }
    }

    // ==================== 工具 ====================

    /** 样式行：文本 + 本行 drawString 基色（行内 § 码仍照常生效，码色覆盖基色） */
    private static final class StyledLine {

        final String text;
        final int color;

        StyledLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    /**
     * 蒸汽消耗两行（原 formatSteamCostLine 拆段提亮）：第一行 = 基准（致密 240 / 普通 24000 L/s）
     * × 同步倍率，整行加粗、金粗倍率后缀（§ 码保持，基色 TEXT_BODY 替代不可读黑）；第二行 = 消耗量公式。
     */
    private static void buildSteamCostLines(List<StyledLine> out, double steamMult, boolean dense) {
        long basePerSecond = dense ? MTECrustMatterAggregator.DENSE_STEAM_PER_SECOND
            : MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND;
        long perSecond = Math.round(basePerSecond * steamMult);
        out.add(
            new StyledLine(
                EnumChatFormatting.BOLD + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost")
                    + " "
                    + NumberFormatUtil.formatNumber(perSecond)
                    + " L/s "
                    + EnumChatFormatting.GOLD.toString()
                    + EnumChatFormatting.BOLD
                    + String.format(
                        StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.mult"),
                        String.format("%.2f", steamMult)),
                GtsrGuiPalette.TEXT_BODY));
        out.add(
            new StyledLine(
                StatCollector.translateToLocal("gtsr.aggregator_config.steam_formula"),
                GtsrGuiPalette.TEXT_BODY));
    }

    /**
     * 宽度感知贪心换行：按 maxWrap 逐原子累计 getStringWidth 测宽，§ 码两字符零宽透传、
     * 随断行携带到续行行首（遇 §r 清空）；拉丁/数字/符号连续段为原子不拆（公式/数字完整），
     * 其余（CJK/全角）逐字可断；行首空格丢弃。
     */
    private void addWrapped(List<StyledLine> out, String text, int maxWrap, int color) {
        final int n = text.length();
        StringBuilder line = new StringBuilder();
        StringBuilder activeCodes = new StringBuilder();
        int lineW = 0;
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < n) {
                String seq = text.substring(i, i + 2);
                line.append(seq);
                if (text.charAt(i + 1) == 'r') {
                    activeCodes.setLength(0);
                } else {
                    activeCodes.append(seq);
                }
                i += 2;
                continue;
            }
            if (c == ' ') {
                if (line.length() > 0) {
                    line.append(c);
                    lineW += this.fontRendererObj.getCharWidth(c);
                }
                i++;
                continue;
            }
            // 原子：ASCII 可见符连续段保持完整，其余字符逐字成原子（CJK 可任意断行）
            int j = i;
            if (isKeepTogether(c)) {
                while (j < n && text.charAt(j) != '§' && text.charAt(j) != ' ' && isKeepTogether(text.charAt(j))) {
                    j++;
                }
            } else {
                j = i + 1;
            }
            String atom = text.substring(i, j);
            int atomW = this.fontRendererObj.getStringWidth(atom);
            if (lineW > 0 && lineW + atomW > maxWrap) {
                out.add(new StyledLine(line.toString(), color));
                line.setLength(0);
                line.append(activeCodes);
                lineW = 0;
            }
            if (atomW > maxWrap) {
                // 超宽原子（无空格长串）逐字硬断行，整原子不得原样塞入空行而溢出栏宽
                for (int k = 0; k < atom.length(); k++) {
                    int cw = this.fontRendererObj.getCharWidth(atom.charAt(k));
                    if (lineW > 0 && lineW + cw > maxWrap) {
                        out.add(new StyledLine(line.toString(), color));
                        line.setLength(0);
                        line.append(activeCodes);
                        lineW = 0;
                    }
                    line.append(atom.charAt(k));
                    lineW += cw;
                }
            } else {
                line.append(atom);
                lineW += atomW;
            }
            i = j;
        }
        if (line.length() > 0) {
            out.add(new StyledLine(line.toString(), color));
        }
    }

    /** ASCII 可见符（不含空格/§）＝换行时不拆开的原子字符（拉丁词、数字、公式段） */
    private static boolean isKeepTogether(char c) {
        return c >= 0x21 && c < 0x7F;
    }

    /** 样式行逐行绘制（行距 HINT_LINE_PITCH=10；基色生效，行内 § 码覆盖） */
    private void drawStyledLines(List<StyledLine> lines, int panelX, int panelY) {
        int dy = 0;
        for (StyledLine l : lines) {
            this.fontRendererObj.drawString(l.text, this.guiLeft + panelX, this.guiTop + panelY + dy, l.color);
            dy += HINT_LINE_PITCH;
        }
    }

    private void setButtonLabel(int id, String label) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton btn && btn.id == id) {
                btn.displayString = label;
                return;
            }
        }
    }

    private void setButtonEnabled(int id, boolean enabled) {
        for (Object o : this.buttonList) {
            if (o instanceof GuiButton btn && btn.id == id) {
                btn.enabled = enabled;
                return;
            }
        }
    }

    private String ellipsized(String text, int buttonWidth) {
        return GtsrGuiList.ellipsis(this.fontRendererObj, text, buttonWidth - 4);
    }

    /** 行首 16×16 物品图标渲染（itemRender + GUI 标准光照，画完复位顶点色） */
    private void renderItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.zLevel = this.zLevel;
        itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ==================== 锚点（valid=false 自关路径用，TerminalClientPacketSink 询问） ====================

    /** 当前 GUI 锚点与给定 pos+dim 比对（容器绑定的聚合器基 TE 坐标） */
    public boolean isAnchoredAt(int x, int y, int z, int dim) {
        IGregTechTileEntity base = this.containerAggregator.getAggregator()
            .getBaseMetaTileEntity();
        return base != null && base.getXCoord() == x
            && base.getYCoord() == y
            && base.getZCoord() == z
            && worldDim() == dim;
    }
}
