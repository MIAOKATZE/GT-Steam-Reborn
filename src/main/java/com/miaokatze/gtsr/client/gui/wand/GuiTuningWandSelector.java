package com.miaokatze.gtsr.client.gui.wand;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.network.WandNet;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 奇点调谐棒选择界面（客户端独占，Shift+右击空气打开；交互口径对齐 Outpost GuiTunerSelector
 * ——每次变更即时提交 {@code WandNet} 包由服务端写手持 NBT，无需保存动作）。
 *
 * <p>
 * 布局：居中 210×168 面板（原生双矩形直绘）；标题；词条三选一（横排 62×18）；
 * 增量八档（2×4 网格 46×18）；当前生效组合回显；「完成」钮仅收起界面。
 */
@SideOnly(Side.CLIENT)
public class GuiTuningWandSelector extends GuiScreen {

    /** 按钮身份：词条三选一（BTN_ENTRY_BASE..+2） */
    private static final int BTN_ENTRY_BASE = 0;
    /** 按钮身份：增量档位（BTN_DELTA_BASE..+7，序对齐 DELTA_STEPS） */
    private static final int BTN_DELTA_BASE = 10;
    /** 按钮身份：完成（收起界面，变更已即时提交） */
    private static final int BTN_DONE = 99;

    // === 面板几何（面板左上角 initGui 按屏幕居中） ===

    private static final int PANEL_W = 210;
    private static final int PANEL_H = 168;
    private static final int TITLE_Y = 10;
    private static final int ENTRY_LABEL_Y = 28;
    private static final int ENTRY_Y = 40;
    private static final int ENTRY_W = 62, ENTRY_H = 18, ENTRY_PITCH = 66, ENTRY_X0 = 8;
    private static final int DELTA_LABEL_Y = 68;
    private static final int DELTA_Y0 = 80, DELTA_W = 46, DELTA_H = 18, DELTA_PITCH_X = 50, DELTA_PITCH_Y = 22,
        DELTA_X0 = 8;
    private static final int CURRENT_Y = 132;
    private static final int DONE_Y = 146, DONE_W = 80, DONE_H = 18;

    /** 面板底/框（原生暗色调） */
    private static final int PANEL_BG = 0xF0101014;
    private static final int PANEL_BORDER = 0xFF50505A;

    /** 词条游标（0=超限 1=失稳 2=撕裂；点击即提交） */
    private int entry;
    /** 增量档位索引（0..7；点击即提交） */
    private int deltaIndex;

    public GuiTuningWandSelector(int entry, int deltaIndex) {
        this.entry = entry;
        this.deltaIndex = deltaIndex;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.buttonList.clear();
        for (int i = 0; i < SingularityTuningWand.ENTRY_COUNT; i++) {
            this.buttonList.add(
                new GuiButton(
                    BTN_ENTRY_BASE + i,
                    this.left() + ENTRY_X0 + i * ENTRY_PITCH,
                    this.top() + ENTRY_Y,
                    ENTRY_W,
                    ENTRY_H,
                    localize("gtsr.wand.entry." + SingularityTuningWand.entryNameKey(i))));
        }
        for (int i = 0; i < SingularityTuningWand.DELTA_STEPS.length; i++) {
            this.buttonList.add(
                new GuiButton(
                    BTN_DELTA_BASE + i,
                    this.left() + DELTA_X0 + (i % 4) * DELTA_PITCH_X,
                    this.top() + DELTA_Y0 + (i / 4) * DELTA_PITCH_Y,
                    DELTA_W,
                    DELTA_H,
                    SingularityTuningWand.deltaLabel(i)));
        }
        this.buttonList.add(
            new GuiButton(
                BTN_DONE,
                this.left() + (PANEL_W - DONE_W) / 2,
                this.top() + DONE_Y,
                DONE_W,
                DONE_H,
                localize("gtsr.wand.ui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_DONE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id >= BTN_ENTRY_BASE && button.id < BTN_ENTRY_BASE + SingularityTuningWand.ENTRY_COUNT) {
            this.entry = button.id - BTN_ENTRY_BASE;
            sendSelection();
            return;
        }
        if (button.id >= BTN_DELTA_BASE && button.id < BTN_DELTA_BASE + SingularityTuningWand.DELTA_STEPS.length) {
            this.deltaIndex = button.id - BTN_DELTA_BASE;
            sendSelection();
        }
    }

    /** 全量提交当前选择（服务端钳位校验后写手持 NBT 并同步） */
    private void sendSelection() {
        WandNet.sendSelectFromClient(this.entry, this.deltaIndex);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(this.left() - 1, this.top() - 1, this.left() + PANEL_W + 1, this.top() + PANEL_H + 1, PANEL_BORDER);
        drawRect(this.left(), this.top(), this.left() + PANEL_W, this.top() + PANEL_H, PANEL_BG);

        String title = localize("gtsr.wand.ui.title");
        this.fontRendererObj.drawStringWithShadow(
            title,
            this.left() + (PANEL_W - this.fontRendererObj.getStringWidth(title)) / 2,
            this.top() + TITLE_Y,
            0xFFE0B341);

        this.fontRendererObj.drawStringWithShadow(
            localize("gtsr.wand.ui.entry_group"),
            this.left() + ENTRY_X0,
            this.top() + ENTRY_LABEL_Y,
            0xFFA0A0A0);
        this.fontRendererObj.drawStringWithShadow(
            localize("gtsr.wand.ui.delta_group"),
            this.left() + DELTA_X0,
            this.top() + DELTA_LABEL_Y,
            0xFFA0A0A0);

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawSelectionOverlay();

        String current = localize("gtsr.wand.ui.current") + ": "
            + localize("gtsr.wand.entry." + SingularityTuningWand.entryNameKey(this.entry))
            + " "
            + SingularityTuningWand.deltaLabel(this.deltaIndex);
        this.fontRendererObj.drawStringWithShadow(
            current,
            this.left() + (PANEL_W - this.fontRendererObj.getStringWidth(current)) / 2,
            this.top() + CURRENT_Y,
            0xFF7FDFFF);
    }

    /** 选中态覆盖层（按钮绘制之后）：琥珀内描边标记当前词条与档位 */
    private void drawSelectionOverlay() {
        int ex = this.left() + ENTRY_X0 + this.entry * ENTRY_PITCH;
        drawSelectionFrame(ex, this.top() + ENTRY_Y, ENTRY_W, ENTRY_H);
        int dx = this.left() + DELTA_X0 + (this.deltaIndex % 4) * DELTA_PITCH_X;
        int dy = this.top() + DELTA_Y0 + (this.deltaIndex / 4) * DELTA_PITCH_Y;
        drawSelectionFrame(dx, dy, DELTA_W, DELTA_H);
    }

    private void drawSelectionFrame(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + 1, 0xFFE0B341);
        drawRect(x, y + h - 1, x + w, y + h, 0xFFE0B341);
        drawRect(x, y, x + 1, y + h, 0xFFE0B341);
        drawRect(x + w - 1, y, x + w, y + h, 0xFFE0B341);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return Math.max(20, (this.height - PANEL_H) / 2);
    }

    private static String localize(String key) {
        return StatCollector.translateToLocal(key);
    }
}
