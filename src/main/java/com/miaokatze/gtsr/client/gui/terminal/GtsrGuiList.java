package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

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

    /** 单行绘制回调（index 为数据下标；x/y 为行左上角屏幕坐标，宽即列表宽、高 20） */
    public interface RowPainter {

        void paintRow(int index, int x, int y, int mouseX, int mouseY);
    }

    /** 行点击回调（可选；列表区内其余点击一律消费防穿透，GTSWN 同款） */
    public interface RowListener {

        void rowClicked(int index, int mouseX, int mouseY, int button);
    }

    // ==================== 几何（构造快照） ====================

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

    /** 单行高度（PLAN §4.5-A 冻结 20） */
    private final int slotHeight = 20;

    /** 滚动条宽度 */
    private final int scrollbarWidth = 6;

    /** 滚动条距离列表右边距 */
    private final int scrollbarMarginRight = 2;

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

    /**
     * @param host   宿主 GUI（读 width/height/zLevel 公有字段）
     * @param left   列表左边界（屏幕 GUI 坐标）
     * @param top    列表上边界
     * @param width  列表内容宽度
     * @param height 列表可视高度（建议 20 的整倍数）
     */
    public GtsrGuiList(GuiScreen host, int left, int top, int width, int height) {
        this.host = host;
        this.listLeft = left;
        this.listTop = top;
        this.listRight = left + width;
        this.listWidth = width;
        this.listHeight = height;
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
        // 多渲染一行以覆盖可能部分显示的最底行
        int lastRow = Math.min(rows, firstRow + visibleRows() + 1);
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
     */
    private void enableListScissor() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = sr.getScaleFactor();
        int sx = listLeft * scale;
        int sy = mc.displayHeight - listBottom * scale;
        int sw = listWidth * scale;
        int sh = listHeight * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    /** 关闭剪刀测试，恢复普通绘制。 */
    private void disableListScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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
}
