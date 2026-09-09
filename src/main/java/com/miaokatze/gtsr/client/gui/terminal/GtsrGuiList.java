package com.miaokatze.gtsr.client.gui.terminal;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * gtsr 终端轨滚动列表：GTSWN GuiDeviceEntryList 滚动骨架（:289-349）泛化移植——
 * 滚动偏移 / 滚动条拖拽 / 鼠标滚轮 / 行命中 / GL_SCISSOR_TEST 骨架逐行同构，
 * 不继承 {@code GuiSlot}、不绑定条目类型（plan/ui/terminal-native-ui/PLAN.md N8）。
 * <p>
 * 数据与行内容由宿主经 {@link RowSource}/{@link RowPainter} 注入：每次 draw 活取
 * （自绘每帧重读缓存范式，宿主刷新/排序后本列表即时可见）；滚动偏移自持——数据刷新
 * 不回顶，等价旧滚动列表 dispose 写回语义（自绘列表天然成立）。
 * <p>
 * 视觉（契约 §3 #9-#12）：list_panel 凹陷底（9-slice 4px）+ 行 hover row_hover
 * （左缘 2px 琥珀暗线，仅视觉不改命中）+ 6px 滚动条（track/thumb 纵向 9-slice 2px，
 * 右缘 2px 边距）；行高固定 20（PLAN §4.5-A 冻结）。
 * <p>
 * 行内容契约（v1.20.16 G5/G6）：行文本左缘 ≥{@link #ROW_PAD_LEFT}（4px 边厚不入边带）、
 * 右缘预留 {@link #ROW_PAD_RIGHT}（滚动条 8px 不压字），宽度预算统一走
 * {@link #rowTextWidthCap()}；超宽行用 {@link #wrapLine} 按宽折行（ellipsis 末位兜底）；
 * 行绘制区对齐整行边界（无底行残条），剪刀退出恢复外层状态（不击穿宿主内容区剪刀）。
 * <p>
 * 悬浮：鼠标停在行上计时、换行或移出列表区即重置（{@link #hoveredIndex()} /
 * {@link #hoverElapsedMillis()}），宿主 drawScreen 末尾按 ≥0.5s 询问画
 * {@code drawHoveringText} tooltip（GTSWN GuiDeviceInfoTerminal :631-669 范式）。
 * <p>
 * 契约出处：plan/ui/terminal-native-ui/texture-list.md §4 之 GtsrGuiList。
 * <p>
 * 最小自绘 demo（宿主 GuiScreen 内，S3 枢纽列表接线样板）：
 *
 * <pre>
 * private final GtsrGuiList list = new GtsrGuiList(this, guiLeft + 8, guiTop + 18, 304, 142);
 *
 * &#64;Override
 * public void initGui() {
 *     list.setRowSource(() -> cache.nodeCount());        // 每帧活取
 *     list.setRowPainter((index, x, y, mx, my) -> {      // 行内容：Drawing/Palette 自绘
 *         fontRendererObj.drawString(name(index), x + 4, y + 6, GtsrGuiPalette.TEXT_BODY);
 *     });
 *     list.setRowListener((index, mx, my, btn) -> sendAction(index)); // 可选
 * }
 *
 * &#64;Override
 * public void drawScreen(int mx, int my, float pt) {
 *     list.draw(mx, my, zLevel);
 *     if (list.hoverElapsedMillis() >= 500) this.drawHoveringText(tooltip, mx, my, fontRendererObj);
 * }
 *
 * &#64;Override
 * public void handleMouseInput() { if (list.handleMouseInput()) return; super.handleMouseInput(); }
 * &#64;Override
 * protected void mouseClicked(int mx, int my, int btn) { if (list.mouseClicked(mx, my, btn)) return; super...; }
 * </pre>
 */
public class GtsrGuiList {

    /** 行数据源（宿主活取，每次绘制重读行数；数据刷新不回顶） */
    public interface RowSource {

        /** @return 当前行数（≥0） */
        int rowCount();
    }

    /** 单行绘制回调（index 为数据下标；x/y 为行左上角屏幕坐标，宽即列表宽、高为构造行高） */
    public interface RowPainter {

        void paintRow(int index, int x, int y, int mouseX, int mouseY);
    }

    /** 行点击回调（可选；列表区内其余点击一律消费防穿透，GTSWN 同款） */
    public interface RowListener {

        void rowClicked(int index, int mouseX, int mouseY, int button);
    }

    // ==================== 几何（构造快照） ====================

    /**
     * 行文本左缘最小内边距（LIST_PANEL 4px 边厚）：行内容 x 偏移不得小于此值，
     * 否则压入凹陷边带（v1.20.16 边框层级修复 G5-1；宿主消费先例 PerfPage 旧 x+3）。
     */
    public static final int ROW_PAD_LEFT = 4;

    /** 宿主 GUI（width/height 公有字段活取：滚轮事件坐标换算） */
    private final GuiScreen host;

    /** 列表左边界 */
    private final int listLeft;

    /** 列表上边界 */
    private final int listTop;

    /** 列表右边界 */
    private final int listRight;

    /** 列表内容宽度 */
    private final int listWidth;

    /** 列表可视高度 */
    private final int listHeight;

    /** 列表下边界 */
    private final int listBottom;

    /** 行高（默认 20；链路页可注入 34） */
    private final int slotHeight;

    /** 滚动条宽度 */
    private static final int scrollbarWidth = 6;

    /** 滚动条距离列表右边距 */
    private static final int scrollbarMarginRight = 2;

    /**
     * 行文本右侧滚动条预留 = 滚动条宽 6 + 距右缘 2：满宽行文本 cap 须扣除，
     * 否则与滚动条重叠（v1.20.16 G5-3；宽度预算取 {@link #rowTextWidthCap()}）。
     */
    public static final int ROW_PAD_RIGHT = scrollbarWidth + scrollbarMarginRight;

    // ==================== 注入件 ====================

    /** 行数据源（宿主 initGui 注入） */
    private RowSource rowSource;

    /** 单行绘制回调（宿主 initGui 注入） */
    private RowPainter rowPainter;

    /** 行点击回调（可选） */
    private RowListener rowListener;

    // ==================== 滚动状态 ====================

    /** 当前顶部被滚掉的行数（自持：数据刷新不回顶） */
    private int scrollOffset = 0;

    /** 是否正在拖拽滚动条 */
    private boolean draggingScrollbar = false;

    // ==================== 悬浮计时（tooltip 用） ====================

    /** 当前悬浮行下标（-1=无；换行即重置时间戳） */
    private int hoverIndex = -1;

    /** 进入当前悬浮行的墙钟时间戳（毫秒） */
    private long hoverStartMillis = 0L;

    // ==================== 剪刀状态保存（v1.20.16 G5-4 泄漏修复） ====================

    /**
     * 进入列表剪刀前外层 GL_SCISSOR_TEST 是否已启用（宿主 GuiClusterTerminalScreen
     * 内容区剪刀先例：pushScissor/popScissor 包裹页面绘制）。true=退出恢复 box 并保持启用；
     * false=退出直接关闭（旧实现无条件 glDisable 会击穿外层剪刀——泄漏根因）。
     */
    private boolean outerScissorEnabled = false;

    /** 进入列表剪刀前保存的外层 GL_SCISSOR_BOX（x/y/w/h，outerScissorEnabled 时退出恢复用） */
    private final int[] outerScissorBox = new int[4];

    /** GL_SCISSOR_BOX 查询缓冲（客户端绘制线程单线程 GL，静态复用避免每帧分配） */
    private static final IntBuffer SCISSOR_BOX_QUERY = BufferUtils.createIntBuffer(16);

    /**
     * @param host   宿主 GUI（读 width/height/zLevel 公有字段）
     * @param left   列表左边界（屏幕 GUI 坐标）
     * @param top    列表上边界
     * @param width  列表内容宽度
     * @param height 列表可视高度（建议 20 的整倍数）
     */
    public GtsrGuiList(GuiScreen host, int left, int top, int width, int height) {
        this(host, left, top, width, height, 20);
    }

    /**
     * 创建指定行高的滚动列表。
     *
     * @param rowHeight 单行高度（像素，必须为正数）
     */
    public GtsrGuiList(GuiScreen host, int left, int top, int width, int height, int rowHeight) {
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight must be positive");
        }
        this.host = host;
        this.listLeft = left;
        this.listTop = top;
        this.listRight = left + width;
        this.listWidth = width;
        this.listHeight = height;
        this.slotHeight = rowHeight;
        this.listBottom = this.listTop + this.listHeight;
    }

    // ==================== 注入（宿主 initGui 调用） ====================

    /** 注入行数据源（必填，null 时 draw 直接返回） */
    public void setRowSource(RowSource source) {
        this.rowSource = source;
    }

    /** 注入单行绘制回调（必填，null 时 draw 直接返回） */
    public void setRowPainter(RowPainter painter) {
        this.rowPainter = painter;
    }

    /** 注入行点击回调（可选，不注入则行点击仅消费防穿透） */
    public void setRowListener(RowListener listener) {
        this.rowListener = listener;
    }

    // ==================== 外部绘制入口 ====================

    /**
     * 绘制整个列表：背景、可见行（hover 行先画 row_hover 左缘 2px 琥珀底再画内容）、滚动条；
     * 并维护悬浮行计时。
     *
     * @param mouseX 鼠标 X（屏幕坐标）
     * @param mouseY 鼠标 Y（屏幕坐标）
     * @param zLevel 绘制深度（宿主 Gui 的 zLevel——Gui 中该字段 protected，跨包不可直读，故传参）
     */
    public void draw(int mouseX, int mouseY, float zLevel) {
        if (rowSource == null || rowPainter == null) {
            return;
        }
        final int rows = rowSource.rowCount();
        clampScroll(rows);
        // 鼠标离开列表区即清除悬浮（时间戳随 hoverIndex=-1 一并作废）
        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            hoverIndex = -1;
        }
        drawListBackground(zLevel);
        enableListScissor();
        int firstRow = scrollOffset;
        // 只画完整可见行：剪刀已对齐整行边界（visibleRows()×行高），原 "+1" 补渲染
        // 会从底边不足一行的高度缝隙（如 LIST_H=244=22×11+2 的 2px）露出残行（v1.20.16 G5-2）
        int lastRow = Math.min(rows, firstRow + visibleRows());
        for (int i = firstRow; i < lastRow; i++) {
            int y = listTop + (i - firstRow) * slotHeight;
            // 悬浮计时（GTSWN :181-192 同构）：命中本行才计时，换行即重置时间戳
            if (inRow(mouseX, mouseY, y)) {
                if (hoverIndex != i) {
                    hoverIndex = i;
                    hoverStartMillis = System.currentTimeMillis();
                }
                // 行 hover 高亮（契约 §3 #11，仅视觉不改命中与 tooltip 计时）
                GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.ROW_HOVER, 4, listLeft, y, listWidth, slotHeight, zLevel);
            } else if (hoverIndex == i) {
                hoverIndex = -1;
            }
            rowPainter.paintRow(i, listLeft, y, mouseX, mouseY);
        }
        if (hoverIndex >= rows) {
            hoverIndex = -1; // 行集收缩后的越界悬浮下标作废（宿主仍须对 hoveredIndex 自校验）
        }
        disableListScissor();
        drawScrollbar(rows, zLevel);
    }

    // ==================== 悬浮查询（宿主 tooltip 用） ====================

    /** @return 当前悬浮行下标（-1=无；宿主须自校验 < 当前行数——数据活取，行集可能已变） */
    public int hoveredIndex() {
        return hoverIndex;
    }

    /** @return 当前行已悬浮毫秒数（无悬浮返回 0；宿主按 ≥500ms 门槛出 tooltip） */
    public long hoverElapsedMillis() {
        return hoverIndex < 0 ? 0L : System.currentTimeMillis() - hoverStartMillis;
    }

    // ==================== 背景与裁剪 ====================

    /**
     * 绘制列表背景（覆盖面板面芯/格栅带，避免列表区出现不需要的线条）：
     * 消费 gtsr 贴图 list_panel（INSET 凹陷，9-slice 切片 4px，契约 §3 #12）。
     */
    private void drawListBackground(float zLevel) {
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, listLeft, listTop, listWidth, listHeight, zLevel);
    }

    /**
     * 启用剪刀测试，将后续绘制限制在列表可视区域内。
     * <p>
     * OpenGL 的 scissor 坐标以屏幕左下角为原点，单位是像素，因此需要按 GUI 缩放比例转换。
     * <p>
     * 进入前先保存外层剪刀状态（v1.20.16 G5-4）：宿主内容区剪刀（GuiClusterTerminalScreen
     * pushScissor）在本列表内层启用，退出时恢复其 box 并保持启用，而非无条件 glDisable。
     * 剪刀高度对齐整行边界（{@link #visibleRows()}×行高）：不足一行的高度不参与行绘制，
     * 任何滚动位置都不出现残行（v1.20.16 G5-2）。
     */
    private void enableListScissor() {
        outerScissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (outerScissorEnabled) {
            SCISSOR_BOX_QUERY.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX_QUERY);
            for (int i = 0; i < 4; i++) {
                outerScissorBox[i] = SCISSOR_BOX_QUERY.get(i);
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = sr.getScaleFactor();
        int sx = listLeft * scale;
        int sy = mc.displayHeight - listBottom * scale;
        int sw = listWidth * scale;
        // 绘制区裁剪到整行：底边不足一行的缝隙（listHeight % slotHeight）整段不绘制
        int sh = visibleRows() * slotHeight * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    /**
     * 退出剪刀测试：有外层剪刀则恢复进入前 box 并保持启用（宿主内容区剪刀继续生效），
     * 无外层剪刀才真正关闭——修复旧实现无条件 glDisable 的状态泄漏（v1.20.16 G5-4）。
     */
    private void disableListScissor() {
        if (outerScissorEnabled) {
            GL11.glScissor(outerScissorBox[0], outerScissorBox[1], outerScissorBox[2], outerScissorBox[3]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    // ==================== 滚动条 ====================

    /** 绘制滚动条轨道与滑块。 */
    private void drawScrollbar(int rows, float zLevel) {
        int trackX = listRight - scrollbarWidth - scrollbarMarginRight;
        int maxScroll = getMaxScroll(rows);
        // 轨道（scrollbar_track 纵向 9-slice 切片 2px，宽 6 与区域几何不变）
        GtsrGuiDrawing
            .drawNineSlice(GtsrGuiTextures.SCROLLBAR_TRACK, 2, trackX, listTop, scrollbarWidth, listHeight, zLevel);
        if (maxScroll > 0) {
            int totalRows = Math.max(visibleRows(), rows);
            int thumbH = Math.max(10, listHeight * visibleRows() / totalRows);
            int thumbY = listTop + scrollOffset * (listHeight - thumbH) / maxScroll;
            GtsrGuiDrawing
                .drawNineSlice(GtsrGuiTextures.SCROLLBAR_THUMB, 2, trackX, thumbY, scrollbarWidth, thumbH, zLevel);
        }
    }

    // ==================== 滚动计算 ====================

    /** 返回最大可滚动行数（总条目 - 可见行数，至少为 0）。 */
    private int getMaxScroll(int rows) {
        return Math.max(0, rows - visibleRows());
    }

    /**
     * 行文本宽度 cap（列表宽 − 左内边距 − 右侧滚动条预留，即 {@link #ROW_PAD_LEFT}
     * + {@link #ROW_PAD_RIGHT} 之外的全部余量）：宿主行绘制/折行/ellipsis 的统一宽度预算；
     * 缩放绘制（0.7f/0.6f）时自行按 scale 换算（v1.20.16 G5-1/G5-3，PerfPage 切片 2 联动）。
     */
    public int rowTextWidthCap() {
        return listWidth - ROW_PAD_LEFT - ROW_PAD_RIGHT;
    }

    /** 返回列表可视区域可容纳的完整行数。 */
    public int visibleRows() {
        return listHeight / slotHeight;
    }

    /**
     * 将 scrollOffset 限制在合法范围内（宿主在条目数变化后调用；draw 每帧亦自钳制）。
     */
    public void clampScroll() {
        if (rowSource != null) {
            clampScroll(rowSource.rowCount());
        }
    }

    /** 按给定行数钳制 scrollOffset（内部路径，避免行数重复活取）。 */
    private void clampScroll(int rows) {
        int max = getMaxScroll(rows);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        if (scrollOffset > max) {
            scrollOffset = max;
        }
    }

    /** 按 delta 行滚动并限制范围。 */
    private void scrollBy(int delta) {
        scrollOffset += delta;
        clampScroll();
    }

    // ==================== 鼠标事件 ====================

    /**
     * 处理鼠标滚轮事件（宿主 handleMouseInput 首行调用：命中返回 true 即消费不再下传）。
     *
     * @return 若事件在列表区域内并被消费则返回 true
     */
    public boolean handleMouseInput() {
        int dwheel = Mouse.getEventDWheel();
        if (dwheel == 0) {
            return false;
        }
        int x = Mouse.getEventX() * host.width / Minecraft.getMinecraft().displayWidth;
        int y = host.height - Mouse.getEventY() * host.height / Minecraft.getMinecraft().displayHeight - 1;
        if (x >= listLeft && x <= listRight && y >= listTop && y <= listBottom) {
            scrollBy(-Integer.signum(dwheel));
            return true;
        }
        return false;
    }

    /**
     * 显式滚轮入口（宿主已读取事件方向时调用，不重读 Mouse 事件）：
     * 终端页 wheel(dir) 转发路径使用，坐标口径与 {@link #mouseClicked} 一致（gui 绝对坐标）。
     *
     * @return 若点在列表区域内并被消费则返回 true
     */
    public boolean handleWheel(int mouseX, int mouseY, int dir) {
        if (dir == 0) {
            return false;
        }
        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            scrollBy(-dir);
            return true;
        }
        return false;
    }

    /**
     * 处理鼠标点击事件：滚动条 → 拖拽；行 → RowListener 回调；其余列表区点击一律消费防穿透。
     *
     * @return 若事件在列表区域内并被消费则返回 true
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        // 滚动条区域
        int trackX = listRight - scrollbarWidth - scrollbarMarginRight;
        if (mouseX >= trackX && mouseX <= trackX + scrollbarWidth) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        // 条目区域
        int rows = rowSource == null ? 0 : rowSource.rowCount();
        int row = (mouseY - listTop) / slotHeight + scrollOffset;
        if (row >= 0 && row < rows && rowListener != null) {
            hoverIndex = -1;
            rowListener.rowClicked(row, mouseX, mouseY, button);
        }
        // 消费列表区内的其他点击，避免穿透到底层控件
        return true;
    }

    /** 处理鼠标拖拽（用于滚动条拖拽；宿主 mouseClickMove 转发）。 */
    public void mouseClickMove(int mouseX, int mouseY, int button) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
        }
    }

    /** 处理鼠标释放（结束滚动条拖拽；宿主 mouseMovedOrUp 转发）。 */
    public void mouseReleased(int mouseX, int mouseY, int button) {
        draggingScrollbar = false;
    }

    /** 根据鼠标 Y 坐标更新 scrollOffset（滚动条拖拽用）。 */
    private void updateScrollFromMouse(int mouseY) {
        int rows = rowSource == null ? 0 : rowSource.rowCount();
        int maxScroll = getMaxScroll(rows);
        if (maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }
        int totalRows = Math.max(visibleRows(), rows);
        int thumbH = Math.max(10, listHeight * visibleRows() / totalRows);
        int available = listHeight - thumbH;
        int relY = mouseY - listTop - thumbH / 2;
        if (relY < 0) {
            relY = 0;
        }
        if (relY > available) {
            relY = available;
        }
        scrollOffset = relY * maxScroll / available;
        clampScroll(rows);
    }

    // ==================== 内部工具 ====================

    /** 鼠标是否位于自 y 起的行矩形内（行 hover 计时与高亮共用）。 */
    private boolean inRow(int mouseX, int mouseY, int y) {
        return mouseX >= listLeft && mouseX <= listRight && mouseY >= y && mouseY < y + slotHeight;
    }

    /**
     * 超宽截断+省略号（宽度内放不下时截到 width-6 并补 "..."）。
     * 行文本列通用的静态工具（GTSWN GuiDeviceEntryList.ellipsis 同款语义，
     * 测量与截断基于 vanilla getStringWidth/trimStringToWidth，§ 序列天然跳过）。
     */
    public static String ellipsis(FontRenderer font, String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        return font.trimStringToWidth(text, Math.max(0, width - 6)) + "...";
    }

    /**
     * 按可用宽度把单行文本折行为多行（v1.20.16 G6 折行基建，切片 2 行集生成消费）：
     * 拉丁字母/数字连续段视为"词"整体移动（词边界优先），其余字符——中文、符号、空格——
     * 逐字换行；§ 格式序列（两字符）永不拆断，断行处激活格式在续行行首补写，
     * 逐行 drawString 与整段绘制观感一致（vanilla wrapFormattedString 同款续写语义）。
     * <p>
     * 与 {@link #ellipsis}（截断兜底）互补：单个不可断单元超宽时整单元独占一行原样返回，
     * 由宿主再以 ellipsis 兜底截断。恰好等宽（=maxWidth）视为放得下。
     * 先例：ClusterPerfPage 长公式两行手工拆分（:186-189）/ ClusterLinkEditorPage
     * drawSplitString 折行（:509-518）。
     *
     * @param font     测量与最终绘制同实例的 FontRenderer（宽度口径一致）
     * @param text     单行文本（可含 § 序列；null/空串返回单个空串行）
     * @param maxWidth 最大行宽像素（≤0 时不折行原样单行返回）
     * @return 折行后行集（≥1 行，可直接逐行 drawString）
     */
    public static List<String> wrapLine(FontRenderer font, String text, int maxWidth) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        if (maxWidth <= 0 || font.getStringWidth(text) <= maxWidth) {
            out.add(text);
            return out;
        }
        String activeFormat = "";
        StringBuilder line = new StringBuilder();
        final int n = text.length();
        int i = 0;
        while (i < n) {
            int len = nextUnitLength(text, i);
            String unit = text.substring(i, i + len);
            boolean formatSeq = len == 2 && unit.charAt(0) == '\u00a7';
            if (font.getStringWidth(line.toString() + unit) <= maxWidth) {
                line.append(unit);
                if (formatSeq) {
                    activeFormat = appendFormatCode(activeFormat, unit.charAt(1));
                }
            } else if (font.getStringWidth(line.toString()) == 0) {
                // 当前行无可视内容（空/仅格式前缀）：超宽不可断单元独占一行，ellipsis 宿主侧兜底
                line.append(unit);
                if (formatSeq) {
                    activeFormat = appendFormatCode(activeFormat, unit.charAt(1));
                }
                out.add(line.toString());
                line = new StringBuilder(activeFormat);
            } else {
                // 正常断行：续行行首补写激活格式；断行处行首空格丢弃
                out.add(line.toString());
                line = new StringBuilder(activeFormat);
                if (!unit.equals(" ")) {
                    line.append(unit);
                    if (formatSeq) {
                        activeFormat = appendFormatCode(activeFormat, unit.charAt(1));
                    }
                }
            }
            i += len;
        }
        // 尾行有可视内容才收行（纯 § 序列结尾不产生空尾行；整串零可视内容时保底一行）
        if (out.isEmpty() || font.getStringWidth(line.toString()) > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** 下一个不可拆单元长度：§ 序列 2 字符；拉丁字母/数字连续段（词）整体；其余逐字符。 */
    private static int nextUnitLength(String text, int i) {
        char c = text.charAt(i);
        if (c == '\u00a7' && i + 1 < text.length()) {
            return 2;
        }
        if (isLatinWordChar(c)) {
            int j = i + 1;
            while (j < text.length() && isLatinWordChar(text.charAt(j))) {
                j++;
            }
            return j - i;
        }
        return 1;
    }

    /** 是否拉丁词字符（字母/数字/下划线；§ 序列与空白不参与组词）。 */
    private static boolean isLatinWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }

    /** 追加格式序列到激活格式串（§r/§R 复位清空，其余颜色/样式累积）。 */
    private static String appendFormatCode(String activeFormat, char code) {
        if (code == 'r' || code == 'R') {
            return "";
        }
        return activeFormat + '\u00a7' + code;
    }
}
