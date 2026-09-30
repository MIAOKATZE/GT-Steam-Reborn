package com.miaokatze.gtsr.client.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalChapter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A dedicated chronicle reader with contents, chapter gating and resolution-aware pagination. */
@SideOnly(Side.CLIENT)
public final class GuiProsperityJournal extends GuiScreen {

    private boolean kingUnlocked;
    private int chapter, page, left, top, panelWidth, panelHeight;
    private final List<String> lines = new ArrayList<>();
    private int linesPerPage;

    public GuiProsperityJournal(boolean unlocked) {
        kingUnlocked = unlocked;
    }

    public void setKingUnlocked(boolean unlocked) {
        kingUnlocked = unlocked;
        page = 0;
        refreshText();
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(490, width - 12);
        panelHeight = Math.min(260, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        linesPerPage = Math.max(5, (panelHeight - 98) / (fontRendererObj.FONT_HEIGHT + 3));
        buttonList.clear();
        JournalChapter[] chapters = JournalChapter.values();
        for (int i = 0; i < chapters.length; i++) {
            buttonList.add(new ChapterButton(100 + i, left + 10, top + 35 + i * 14, 112, 13));
        }
        buttonList.add(new GuiButton(200, left + 139, top + panelHeight - 26, 42, 18, "<"));
        buttonList.add(new GuiButton(201, left + panelWidth - 57, top + panelHeight - 26, 42, 18, ">"));
        buttonList.add(new GuiButton(202, left + panelWidth - 28, top + 8, 18, 18, "×"));
        refreshText();
    }

    private void refreshText() {
        if (fontRendererObj == null) return;
        lines.clear();
        JournalChapter entry = JournalChapter.values()[chapter];
        if (entry.requiresKingAchievement && !kingUnlocked) {
            appendParagraph(StatCollector.translateToLocal("lore.locked.body"));
        } else {
            for (int p = 0; p < entry.paragraphs; p++) {
                appendParagraph(StatCollector.translateToLocal(entry.paragraphKey(p)));
            }
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private void appendParagraph(String text) {
        for (Object line : fontRendererObj.listFormattedStringToWidth(text, panelWidth - 157)) {
            lines.add((String) line);
        }
        lines.add("");
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + linesPerPage - 1) / Math.max(1, linesPerPage));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 100 && button.id < 100 + JournalChapter.values().length) {
            chapter = button.id - 100;
            page = 0;
            refreshText();
        } else if (button.id == 200) page = Math.max(0, page - 1);
        else if (button.id == 201) page = Math.min(pageCount() - 1, page + 1);
        else if (button.id == 202) mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawRect(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF192826);
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xFF9E7448);
        drawRect(left + 3, top + 3, left + panelWidth - 3, top + panelHeight - 3, 0xFF252E29);
        drawRect(left + 128, top + 30, left + panelWidth - 7, top + panelHeight - 32, 0xFFE0D3AF);
        drawRect(left + 131, top + 33, left + panelWidth - 10, top + panelHeight - 35, 0xFFEBDFC3);
        drawRect(left + 125, top + 30, left + 128, top + panelHeight - 32, 0xFFAE814F);
        // Hand-set root tracery and brass pins, matching the royal timber palette.
        for (int x = 8; x < panelWidth - 8; x += 13) {
            int h = 2 + (x * 7 % 5);
            drawRect(left + x, top + panelHeight - 7 - h, left + x + 1, top + panelHeight - 5, 0xFF617E63);
            drawRect(left + x + 1, top + panelHeight - 8 - h, left + x + 4, top + panelHeight - 7 - h, 0xFF9B9B60);
        }
        for (int x : new int[] { left + 6, left + panelWidth - 8 }) {
            for (int y : new int[] { top + 6, top + panelHeight - 8 }) drawRect(x, y, x + 2, y + 2, 0xFFE7C880);
        }
        fontRendererObj.drawString(StatCollector.translateToLocal("lore.book.title"), left + 10, top + 13, 0xE5CAA0);
        JournalChapter entry = JournalChapter.values()[chapter];
        String title = entry.requiresKingAchievement && !kingUnlocked
            ? StatCollector.translateToLocal("lore.locked.title")
            : StatCollector.translateToLocal(entry.titleKey());
        fontRendererObj.drawString(title, left + 139, top + 39, 0x4B3929);
        drawRect(left + 139, top + 51, left + panelWidth - 17, top + 52, 0xFFB2935D);
        int first = page * linesPerPage;
        for (int i = 0; i < linesPerPage && first + i < lines.size(); i++) {
            fontRendererObj.drawString(
                lines.get(first + i),
                left + 139,
                top + 59 + i * (fontRendererObj.FONT_HEIGHT + 3),
                0x493B30);
        }
        String count = (page + 1) + " / " + pageCount();
        fontRendererObj.drawString(
            count,
            left + (panelWidth + 124 - fontRendererObj.getStringWidth(count)) / 2,
            top + panelHeight - 20,
            0xDABF92);
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.id == 200) button.enabled = page > 0;
            if (button.id == 201) button.enabled = page + 1 < pageCount();
        }
        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    protected void keyTyped(char key, int code) {
        if (code == 203) page = Math.max(0, page - 1);
        else if (code == 205) page = Math.min(pageCount() - 1, page + 1);
        else super.keyTyped(key, code);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private final class ChapterButton extends GuiButton {

        ChapterButton(int id, int x, int y, int w, int h) {
            super(id, x, y, w, h, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft client, int mouseX, int mouseY) {
            int index = id - 100;
            JournalChapter entry = JournalChapter.values()[index];
            boolean hover = mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
            boolean selected = index == chapter;
            drawRect(
                xPosition,
                yPosition,
                xPosition + width,
                yPosition + height,
                selected ? 0xFF657860 : hover ? 0xFF384B3F : 0xFF252E29);
            boolean locked = entry.requiresKingAchievement && !kingUnlocked;
            String title = StatCollector.translateToLocal(locked ? "lore.locked.title" : entry.titleKey());
            title = client.fontRenderer.trimStringToWidth((index + 1) + " · " + title, width - 8);
            client.fontRenderer.drawString(title, xPosition + 4, yPosition + 2, locked ? 0x8F897A : 0xE2CBA0);
        }
    }
}
