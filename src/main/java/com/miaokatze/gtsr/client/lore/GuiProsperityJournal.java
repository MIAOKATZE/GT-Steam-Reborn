package com.miaokatze.gtsr.client.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalChapter;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalEntry;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalTab;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Four searchable reading tabs; server snapshots alone own achievement permissions. */
@SideOnly(Side.CLIENT)
public final class GuiProsperityJournal extends GuiScreen {

    private boolean kingUnlocked, appendix;
    private int progress, page, listPage, left, top, panelWidth, panelHeight, rows, linesPerPage;
    private JournalTab tab = JournalTab.CHAPTERS;
    private final int[] selections = new int[JournalTab.values().length];
    private List<JournalEntry> entries = JournalCatalog.entries(tab);
    private final List<String> lines = new ArrayList<String>();

    public GuiProsperityJournal(boolean kingUnlocked) {
        this(kingUnlocked, 0);
    }

    public GuiProsperityJournal(boolean kingUnlocked, int progress) {
        this.kingUnlocked = kingUnlocked;
        this.progress = progress;
    }

    public void setKingUnlocked(boolean unlocked) {
        if (kingUnlocked == unlocked) return;
        kingUnlocked = unlocked;
        page = 0;
        refreshText();
        if (fontRendererObj != null) rebuildButtons();
    }

    private int sceneEntries, sceneBattles;

    public GuiProsperityJournal(boolean kingUnlocked, int progress, int sceneEntries, int sceneBattles) {
        this(kingUnlocked, progress);
        this.sceneEntries = sceneEntries;
        this.sceneBattles = sceneBattles;
    }

    public void setSceneEntries(int entries) {
        sceneEntries = entries;
        refreshText();
    }

    public void setSceneBattles(int battles) {
        sceneBattles = battles;
        refreshText();
    }

    private boolean battleUnlocked(JournalEntry entry) {
        if (entry.tab != JournalTab.STRUCTURES) return true;
        for (int i = 0; i < com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES.length; i++)
            if (entry.id.equals(com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[i]))
                return (sceneBattles & (1 << i)) != 0;
        return true;
    }

    private boolean sceneUnlocked(JournalEntry entry) {
        if (entry.isGiantTree()) return (sceneEntries & (1 << 30)) != 0 || kingUnlocked;
        if (entry.tab != JournalTab.STRUCTURES) return true;
        for (int i = 0; i < com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES.length; i++)
            if (entry.id.equals(com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite.NAMES[i]))
                return (sceneEntries & (1 << i)) != 0 || entry.recordConfirmed(progress);
        return true;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        refreshText();
    }

    public JournalTab getTab() {
        return tab;
    }

    public JournalEntry getCurrentEntry() {
        return entries.get(selections[tab.ordinal()]);
    }

    public void selectTab(JournalTab next) {
        tab = next;
        entries = JournalCatalog.entries(tab);
        page = 0;
        appendix = false;
        listPage = selections[tab.ordinal()] / Math.max(1, rows);
        refreshText();
        if (fontRendererObj != null) rebuildButtons();
    }

    public void selectEntry(String id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) {
            selections[tab.ordinal()] = i;
            listPage = i / Math.max(1, rows);
            page = 0;
            appendix = false;
            refreshText();
            if (fontRendererObj != null) rebuildButtons();
            return;
        }
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(490, width - 12);
        panelHeight = Math.min(290, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rows = Math.max(1, (panelHeight - 106) / 15);
        linesPerPage = Math.max(1, (panelHeight - 126) / (fontRendererObj.FONT_HEIGHT + 3));
        listPage = selections[tab.ordinal()] / rows;
        rebuildButtons();
        refreshText();
    }

    private int listPageCount() {
        return Math.max(1, (entries.size() + Math.max(1, rows) - 1) / Math.max(1, rows));
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + Math.max(1, linesPerPage) - 1) / Math.max(1, linesPerPage));
    }

    private void rebuildButtons() {
        buttonList.clear();
        int tabWidth = (panelWidth - 20) / 4;
        for (JournalTab value : JournalTab.values()) {
            GuiButton button = new GuiButton(
                10 + value.ordinal(),
                left + 10 + value.ordinal() * tabWidth,
                top + 29,
                tabWidth - 2,
                18,
                StatCollector.translateToLocal(value.titleKey()));
            button.enabled = value != tab;
            buttonList.add(button);
        }
        int first = listPage * Math.max(1, rows);
        for (int row = 0; row < rows && first + row < entries.size(); row++) {
            buttonList.add(new EntryButton(100 + first + row, left + 9, top + 58 + row * 15, 103, 14, first + row));
        }
        buttonList.add(new GuiButton(210, left + 9, top + panelHeight - 29, 20, 18, "<"));
        buttonList.add(new GuiButton(211, left + 92, top + panelHeight - 29, 20, 18, ">"));
        buttonList.add(new GuiButton(200, left + 132, top + panelHeight - 29, 24, 18, "<"));
        buttonList.add(new GuiButton(201, left + panelWidth - 34, top + panelHeight - 29, 24, 18, ">"));
        buttonList.add(new GuiButton(202, left + panelWidth - 26, top + 8, 18, 16, "×"));
        if (getCurrentEntry().isGiantTree()) {
            String key = appendix ? "lore.appendix.return" : kingUnlocked ? "lore.appendix" : "lore.appendix.locked";
            String label = fontRendererObj.trimStringToWidth(StatCollector.translateToLocal(key), 68);
            buttonList.add(new GuiButton(220, left + panelWidth - 87, top + 54, 77, 16, label));
        }
    }

    private void refreshText() {
        if (fontRendererObj == null) return;
        lines.clear();
        JournalEntry entry = getCurrentEntry();
        if (!sceneUnlocked(entry)) {
            appendParagraph(StatCollector.translateToLocal("gtsr.scene.story_locked"));
        } else if (appendix && entry.isGiantTree()) {
            if (kingUnlocked) {
                for (int i = 0; i < JournalChapter.KING_EPILOGUE.paragraphs; i++) {
                    appendParagraph(StatCollector.translateToLocal(JournalChapter.KING_EPILOGUE.paragraphKey(i)));
                }
            } else appendParagraph(StatCollector.translateToLocal("lore.locked.body"));
        } else {
            for (int i = 0; i < entry.paragraphs; i++) appendParagraph(
                StatCollector.translateToLocal(
                    entry.tab == JournalTab.STRUCTURES && i > 0 && !battleUnlocked(entry) ? "lore.ruin.ending.pending"
                        : entry.paragraphKey(i)));
            if (entry.recordMask != 0) {
                if (entry.recordConfirmed(progress)) {
                    appendParagraph(StatCollector.translateToLocal("lore.record.confirmed"));
                    appendParagraph(StatCollector.translateToLocal(entry.key + ".record"));
                } else appendParagraph(StatCollector.translateToLocal("lore.record.pending"));
            }
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private void appendParagraph(String text) {
        for (Object line : fontRendererObj.listFormattedStringToWidth(text, panelWidth - 151)) lines.add((String) line);
        lines.add("");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 10 && button.id < 14) selectTab(JournalTab.values()[button.id - 10]);
        else if (button.id >= 100 && button.id < 100 + entries.size()) selectEntry(entries.get(button.id - 100).id);
        else if (button.id == 200) page = Math.max(0, page - 1);
        else if (button.id == 201) page = Math.min(pageCount() - 1, page + 1);
        else if (button.id == 202) mc.displayGuiScreen(null);
        else if (button.id == 210 || button.id == 211) {
            listPage = Math.max(0, Math.min(listPageCount() - 1, listPage + (button.id == 210 ? -1 : 1)));
            rebuildButtons();
        } else if (button.id == 220) {
            appendix = !appendix;
            page = 0;
            refreshText();
            rebuildButtons();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            drawDefaultBackground();
            drawRect(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF192826);
            drawRect(left, top, left + panelWidth, top + panelHeight, 0xFF9E7448);
            drawRect(left + 3, top + 3, left + panelWidth - 3, top + panelHeight - 3, 0xFF252E29);
            drawRect(left + 120, top + 52, left + panelWidth - 7, top + panelHeight - 34, 0xFFE0D3AF);
            drawRect(left + 123, top + 55, left + panelWidth - 10, top + panelHeight - 37, 0xFFEBDFC3);
            drawRect(left + 117, top + 52, left + 120, top + panelHeight - 34, 0xFFAE814F);
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(StatCollector.translateToLocal("lore.book.title"), panelWidth - 47),
                left + 10,
                top + 12,
                0xE5CAA0);
            JournalEntry entry = getCurrentEntry();
            String titleKey = appendix && entry.isGiantTree()
                ? kingUnlocked ? JournalChapter.KING_EPILOGUE.titleKey() : "lore.locked.title"
                : entry.titleKey();
            int titleWidth = entry.isGiantTree() ? panelWidth - 225 : panelWidth - 151;
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(StatCollector.translateToLocal(titleKey), titleWidth),
                left + 132,
                top + 58,
                0x4B3929);
            drawRect(left + 132, top + 72, left + panelWidth - 18, top + 73, 0xFFB2935D);
            int first = page * Math.max(1, linesPerPage);
            for (int row = 0; row < linesPerPage && first + row < lines.size(); row++) {
                fontRendererObj.drawString(
                    lines.get(first + row),
                    left + 132,
                    top + 80 + row * (fontRendererObj.FONT_HEIGHT + 3),
                    0x493B30);
            }
            String listCount = (listPage + 1) + "/" + listPageCount();
            fontRendererObj.drawString(
                listCount,
                left + 60 - fontRendererObj.getStringWidth(listCount) / 2,
                top + panelHeight - 23,
                0xDABF92);
            String textCount = (page + 1) + " / " + pageCount();
            fontRendererObj.drawString(
                textCount,
                left + (panelWidth + 120 - fontRendererObj.getStringWidth(textCount)) / 2,
                top + panelHeight - 23,
                0xDABF92);
            for (Object object : buttonList) {
                GuiButton button = (GuiButton) object;
                if (button.id == 200) button.enabled = page > 0;
                if (button.id == 201) button.enabled = page + 1 < pageCount();
                if (button.id == 210) button.enabled = listPage > 0;
                if (button.id == 211) button.enabled = listPage + 1 < listPageCount();
            }
            super.drawScreen(mouseX, mouseY, partial);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (mouseX < left || mouseX >= left + panelWidth || mouseY < top + 52 || mouseY >= top + panelHeight - 34)
            return;
        int direction = wheel > 0 ? -1 : 1;
        if (mouseX < left + 117) {
            listPage = Math.max(0, Math.min(listPageCount() - 1, listPage + direction));
            rebuildButtons();
        } else page = Math.max(0, Math.min(pageCount() - 1, page + direction));
    }

    @Override
    protected void keyTyped(char key, int code) {
        if (code == 203) page = Math.max(0, page - 1);
        else if (code == 205) page = Math.min(pageCount() - 1, page + 1);
        else if (code == 200 || code == 208) {
            int selected = selections[tab.ordinal()] + (code == 200 ? -1 : 1);
            selectEntry(entries.get(Math.max(0, Math.min(entries.size() - 1, selected))).id);
        } else if (key >= '1' && key <= '4') selectTab(JournalTab.values()[key - '1']);
        else super.keyTyped(key, code);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private final class EntryButton extends GuiButton {

        private final int index;

        EntryButton(int id, int x, int y, int width, int height, int index) {
            super(id, x, y, width, height, "");
            this.index = index;
        }

        @Override
        public void drawButton(Minecraft client, int mouseX, int mouseY) {
            boolean hover = mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
            boolean selected = selections[tab.ordinal()] == index;
            drawRect(
                xPosition,
                yPosition,
                xPosition + width,
                yPosition + height,
                selected ? 0xFF657860 : hover ? 0xFF384B3F : 0xFF252E29);
            JournalEntry entry = entries.get(index);
            String title = (index + 1) + " · " + StatCollector.translateToLocal(entry.titleKey());
            title = client.fontRenderer.trimStringToWidth(title, width - 8);
            client.fontRenderer.drawString(title, xPosition + 4, yPosition + 3, 0xE2CBA0);
        }
    }
}
