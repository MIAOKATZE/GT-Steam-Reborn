package com.miaokatze.gtsr.client.gui.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen.ClusterPage;
import com.miaokatze.gtsr.client.terminal.ClusterTerminalClientCache;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalActions;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 集群终端·第四页「统计」（terminal-native-ui S3：自增幅页统计三栏独立成页，
 * {@code cl.stats} 数据链零协议扩展——三段 {@code in|out|bonus}，段内 CSV
 * {@code itemId:meta:count} 按 count 降序 Top-64）。
 *
 * <p>
 * 版面：页标题行（右侧清除钮 70×14）+ 三等栏（各 191px，栏距 4）；每栏标题
 * （累计进入/累计输出/增幅产出）+ 物品图标网格（X 步距 17、Y 步距 26 = 16px 图标 +
 * 图标正下方计数行；每栏 11 列 × 8 行 = 88 格 ≥ 同步端 Top-64 上限，静态可容，
 * 滚轮按行换页兜底自持，三栏共享条目窗）。计数走 {@link GtsrNumFormat#compact3}
 * （K/M/B/T/Q ≤3 位有效数字，图标正下方 0.5f 右对齐）；tooltip 物品名 +
 * {@link GtsrNumFormat#grouped} 完整数量不缩写（经宿主 500ms 通道出剪刀统一绘制）。
 *
 * <p>
 * 清除钮两步确认防误触：态 1「清除统计」（muted）→ 点击转 armed「确认清除？」
 * （红字 + CHIP_ACTIVE，3 秒未确认自动回落）→ armed 再点击发
 * {@link ClusterTerminalActions#CLEAR_STATS}（空 payload，服务端复核清三账本）并回落。
 * 本页只读：内容区其余点击一律消费防穿透。
 *
 * <p>
 * <b>live 每帧重读纪律</b>：三栏条目全部由 draw 每帧重读
 * {@link ClusterTerminalClientCache#getStats}（缺包回空串=空页），零构造期快照。
 * 防御：{@code itemId:meta:count} 畸形条目丢弃；{@code Item.getItemById} 注册表缺失
 * 条目跳过（防伪造/版本漂移）——原增幅页统计三栏实现（git 基线同 S3 前工作树）逐字迁入。
 */
@SideOnly(Side.CLIENT)
final class ClusterStatsPage implements ClusterPage {

    /** 栏标题行偏移（页标题行之下）。 */
    private static final int COL_TITLE_DY = 16;
    /** 图标网格起始偏移。 */
    private static final int GRID_DY = 28;
    /** 三等栏宽与栏距（582 = 191×3 + 4×2）。 */
    private static final int COL_W = (GuiClusterTerminalScreen.CONTENT_W - 2 * 4) / 3;
    private static final int COL_GAP = 4;
    /** 图标步距：X = 16px 图标 + 1px 间隙；Y = 16px 图标 + 图标正下方计数行。 */
    private static final int X_PITCH = 17;
    private static final int Y_PITCH = 26;
    /** 每栏列数（(191-4)/17 = 11）。 */
    private static final int COLS_PER_ROW = (COL_W - 4) / X_PITCH;
    /** 网格可视行数（(258-28)/26 = 8；11×8 = 88 格 ≥ Top-64，静态可容）。 */
    private static final int VISIBLE_ROWS = (GuiClusterTerminalScreen.CONTENT_H - GRID_DY) / Y_PITCH;
    /** 清除钮（标题行右侧，chip 样式）。 */
    private static final int CLEAR_BTN_W = 70;
    private static final int CLEAR_BTN_H = 14;
    /** 清除钮 armed 确认窗口（3 秒未确认自动回落）。 */
    private static final long CLEAR_CONFIRM_WINDOW_MS = 3000L;
    /** 栏标题 lang key（列序 = KEY_STATS 段序 in/out/bonus）。 */
    private static final String[] COL_TITLE_KEYS = { "gtsr.terminal.stats.in", "gtsr.terminal.stats.out",
        "gtsr.terminal.stats.bonus" };

    private final GuiClusterTerminalScreen host;
    /** 三栏共享条目窗偏移（按行；自持，滚轮悬停内容区换行，draw 每帧钳制）。 */
    private int statsOffset;
    /** 清除钮两步确认：armed 态与进入时刻（确认窗口内再点击才发动作）。 */
    private boolean clearArmed;
    private long clearArmedAtMillis;

    ClusterStatsPage(GuiClusterTerminalScreen host) {
        this.host = host;
    }

    // ==================== 绘制 ====================

    @Override
    public void draw(int ox, int oy, int mx, int my, float z) {
        tickClearArmed();
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.terminal.stats.title"),
            ox,
            oy,
            0.7f,
            GtsrGuiPalette.TEXT_ACCENT);
        drawClearButton(ox, oy, z);
        drawStats(ox, oy, mx, my, z);
    }

    /** armed 态超确认窗口自动回落（每帧检查；确认点击在 mouseClicked 即时回落）。 */
    private void tickClearArmed() {
        if (this.clearArmed && System.currentTimeMillis() - this.clearArmedAtMillis >= CLEAR_CONFIRM_WINDOW_MS) {
            this.clearArmed = false;
        }
    }

    /** 清除钮（chip 样式）：常态 muted「清除统计」；armed 红「确认清除？」+ CHIP_ACTIVE。 */
    private void drawClearButton(int ox, int oy, float z) {
        int bx = ox + GuiClusterTerminalScreen.CONTENT_W - CLEAR_BTN_W;
        GtsrGuiDrawing.drawNineSlice(
            this.clearArmed ? GtsrGuiTextures.CHIP_ACTIVE : GtsrGuiTextures.CHIP_NORMAL,
            4,
            bx,
            oy,
            CLEAR_BTN_W,
            CLEAR_BTN_H,
            z);
        String label = this.clearArmed ? EnumChatFormatting.RED + tr("gtsr.terminal.stats.clear.confirm")
            : tr("gtsr.terminal.stats.clear");
        int labelW = GuiClusterTerminalScreen.scaledTextWidth(font(), label, 0.7f);
        GuiClusterTerminalScreen.drawScaledText(
            font(),
            label,
            bx + (CLEAR_BTN_W - labelW) / 2,
            oy + (CLEAR_BTN_H - 8) / 2 + 1,
            0.7f,
            this.clearArmed ? GtsrGuiPalette.TEXT_WHITE : GtsrGuiPalette.TEXT_MUTED);
    }

    /**
     * 三栏统计（原增幅页统计三栏迁移版）：栏标题 + 物品图标网格（条目多于网格经滚轮
     * 按行换页，三栏共享 statsOffset 自持）；计数 compact3 图标正下方右对齐；悬浮经宿主
     * 500ms 通道出物品名+完整数量 tooltip。每帧重读 KEY_STATS（live 每帧重读纪律）。
     */
    private void drawStats(int ox, int oy, int mx, int my, float z) {
        List<String[]> segments = statsSegments();
        int maxRows = 0;
        for (String[] segment : segments) {
            maxRows = Math.max(maxRows, (segment.length + COLS_PER_ROW - 1) / COLS_PER_ROW);
        }
        int maxOffset = Math.max(0, maxRows - VISIBLE_ROWS);
        if (this.statsOffset > maxOffset) this.statsOffset = Math.max(0, maxOffset);
        if (this.statsOffset < 0) this.statsOffset = 0;
        int gridY = oy + GRID_DY;
        for (int col = 0; col < 3 && col < segments.size(); col++) {
            int x = ox + col * (COL_W + COL_GAP);
            GuiClusterTerminalScreen.drawScaledText(
                font(),
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr(COL_TITLE_KEYS[col]),
                x + 2,
                oy + COL_TITLE_DY,
                0.55f,
                GtsrGuiPalette.TEXT_ACCENT);
            String[] entries = segments.get(col);
            int start = Math.min(this.statsOffset * COLS_PER_ROW, entries.length);
            int shown = Math.min(entries.length - start, COLS_PER_ROW * VISIBLE_ROWS);
            for (int i = 0; i < shown; i++) {
                long[] parsed = parseStatEntry(entries[start + i]);
                if (parsed == null) continue;
                Item item = Item.getItemById((int) parsed[0]);
                if (item == null) continue; // 注册表缺失条目跳过（防伪造/版本漂移）
                ItemStack stack = new ItemStack(item, 1, (int) parsed[1]);
                int ix = x + 2 + (i % COLS_PER_ROW) * X_PITCH;
                int iy = gridY + (i / COLS_PER_ROW) * Y_PITCH;
                drawItemIcon(stack, ix, iy, z);
                String count = GtsrNumFormat.compact3(parsed[2]);
                int countW = GuiClusterTerminalScreen.scaledTextWidth(font(), count, 0.5f);
                GuiClusterTerminalScreen.drawScaledText(
                    font(),
                    EnumChatFormatting.WHITE + count,
                    ix + 16 - countW,
                    iy + 17,
                    0.5f,
                    GtsrGuiPalette.TEXT_WHITE);
                if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                    List<String> tip = new ArrayList<String>();
                    tip.add(EnumChatFormatting.WHITE + stack.getDisplayName());
                    tip.add(EnumChatFormatting.GRAY + "×" + GtsrNumFormat.grouped(parsed[2]));
                    this.host.requestTooltip("stat" + col + ":" + (start + i), tip);
                }
            }
        }
    }

    // ==================== 解析（原增幅页统计三栏逐字迁入） ====================

    /** KEY_STATS 三段拆分（缺段补空数组；段内 CSV 条目保序）。 */
    private static List<String[]> statsSegments() {
        List<String[]> out = new ArrayList<>();
        String[] parts = getStats().split("\\|", -1);
        for (int i = 0; i < 3; i++) {
            String segment = i < parts.length ? parts[i] : "";
            out.add(segment.isEmpty() ? new String[0] : segment.split(",", -1));
        }
        return out;
    }

    /** 条目 {@code itemId:meta:count} 解析（畸形回 null）。 */
    private static long[] parseStatEntry(String entry) {
        String[] fields = entry.split(":", -1);
        if (fields.length != 3) return null;
        try {
            return new long[] { Long.parseLong(fields[0].trim()), Long.parseLong(fields[1].trim()),
                Long.parseLong(fields[2].trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 物品图标渲染（GuiTerminalBase.renderItemIcon 同款：GUI 标准光照 + 画完复位顶点色；页非 GuiScreen 子类故自持 RenderItem）。 */
    private static void drawItemIcon(ItemStack stack, int x, int y, float z) {
        RenderItem renderItem = RenderItem.getInstance();
        RenderHelper.enableGUIStandardItemLighting();
        renderItem.zLevel = z;
        renderItem.renderItemAndEffectIntoGUI(
            font0(),
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** S1 统计串只读（cl.stats；缺包回空串）。 */
    private static String getStats() {
        return ClusterTerminalClientCache.getStats("");
    }

    // ==================== 输入 ====================

    /**
     * 清除钮两步确认：态 1 点击 → armed；armed 再点击 → 发 CLEAR_STATS（空 payload）并回落。
     * 本页只读：内容区其余点击一律消费防穿透。
     */
    @Override
    public boolean mouseClicked(int ox, int oy, int mx, int my, int button) {
        boolean inPage = mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H;
        if (!inPage) return false;
        int bx = ox + GuiClusterTerminalScreen.CONTENT_W - CLEAR_BTN_W;
        if (mx >= bx && mx < bx + CLEAR_BTN_W && my >= oy && my < oy + CLEAR_BTN_H) {
            if (this.clearArmed) {
                this.host.clusterAction(ClusterTerminalActions.CLEAR_STATS, new byte[0]);
                this.clearArmed = false;
            } else {
                this.clearArmed = true;
                this.clearArmedAtMillis = System.currentTimeMillis();
            }
            return true;
        }
        return true;
    }

    @Override
    public void wheel(int ox, int oy, int mx, int my, int dir) {
        // 悬停内容区即滚动（三栏共享条目窗按行换页；MC 标准方向，与 GtsrGuiList.handleWheel 同号；draw 每帧钳制）
        if (mx >= ox && mx < ox + GuiClusterTerminalScreen.CONTENT_W
            && my >= oy
            && my < oy + GuiClusterTerminalScreen.CONTENT_H) {
            this.statsOffset -= dir;
        }
    }

    // ==================== 共用工具 ====================

    private static net.minecraft.client.gui.FontRenderer font0() {
        return net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
    }

    private net.minecraft.client.gui.FontRenderer font() {
        return this.host.font();
    }

    private static String tr(String key) {
        return GuiClusterTerminalScreen.tr(key);
    }
}
