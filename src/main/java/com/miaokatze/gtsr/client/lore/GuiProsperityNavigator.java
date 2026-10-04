package com.miaokatze.gtsr.client.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.miaokatze.gtsr.common.commands.RuinLocateCommand;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Portable survey directory, with server-authoritative results and no teleport action. */
@SideOnly(Side.CLIENT)
public final class GuiProsperityNavigator extends GuiScreen {

    private static int nextSerial;
    private final List<String> ids = RuinLocateCommand.names();
    private final List<Integer> filtered = new ArrayList<>();
    private GuiTextField search;
    private int left, top, panelWidth, rows, page, selected = -1, requestSerial, requestedIndex = -1;
    private long requestedAt;
    private String status = tr("gtsr.navigator.hint");
    private boolean pending;

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        panelWidth = Math.min(440, width - 16);
        rows = Math.max(3, Math.min(8, (height - 154) / 23));
        left = (width - panelWidth) / 2;
        top = (height - (rows * 23 + 142)) / 2;
        search = new GuiTextField(fontRendererObj, left + 16, top + 43, panelWidth - 32, 19);
        search.setMaxStringLength(64);
        search.setFocused(true);
        filter();
    }

    private void filter() {
        filtered.clear();
        String query = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        for (int i = 0; i < ids.size(); i++) if (RuinLocateCommand.displayName(ids.get(i))
            .toLowerCase(Locale.ROOT)
            .contains(query)
            || ids.get(i)
                .contains(query))
            filtered.add(i);
        page = 0;
        buttons();
    }

    private void buttons() {
        buttonList.clear();
        int size = rows * 2, start = page * size, columnWidth = (panelWidth - 36) / 2;
        for (int slot = 0; slot < size && start + slot < filtered.size(); slot++) {
            int index = filtered.get(start + slot);
            String name = RuinLocateCommand.displayName(ids.get(index));
            String label = (index == selected ? "§e● " : "")
                + fontRendererObj.trimStringToWidth(name, columnWidth - 23);
            buttonList.add(
                new GuiButton(
                    100 + index,
                    left + 16 + (slot % 2) * (columnWidth + 4),
                    top + 72 + (slot / 2) * 23,
                    columnWidth,
                    20,
                    label));
        }
        int footer = top + 76 + rows * 23;
        GuiButton previous = new GuiButton(1, left + 16, footer, 45, 20, "◀");
        previous.enabled = page > 0;
        buttonList.add(previous);
        GuiButton next = new GuiButton(2, left + panelWidth - 61, footer, 45, 20, "▶");
        next.enabled = (page + 1) * size < filtered.size();
        buttonList.add(next);
        GuiButton locate = new GuiButton(
            3,
            left + panelWidth / 2 - 68,
            footer + 25,
            136,
            20,
            tr("gtsr.navigator.query"));
        locate.enabled = selected >= 0 && !pending;
        buttonList.add(locate);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 100) {
            selected = button.id - 100;
            buttons();
        } else if (button.id == 1 || button.id == 2) {
            page += button.id == 1 ? -1 : 1;
            buttons();
        } else if (button.id == 3 && selected >= 0 && !pending) {
            pending = true;
            requestedAt = System.currentTimeMillis();
            requestedIndex = selected;
            nextSerial = nextSerial == Integer.MAX_VALUE ? 1 : nextSerial + 1;
            requestSerial = nextSerial;
            status = tr("gtsr.navigator.searching");
            buttons();
            NavigatorNetwork.query(selected, requestSerial);
        }
    }

    public void receive(NavigatorNetwork.Result result) {
        if (!pending || result.serial != requestSerial || result.index != requestedIndex) return;
        pending = false;
        status = result.status == 1
            ? StatCollector.translateToLocalFormatted("gtsr.navigator.coordinates", result.x, result.z, result.distance)
            : tr(result.status == 3 ? "gtsr.navigator.cooldown" : "gtsr.navigator.missing");
        buttons();
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        if (pending && System.currentTimeMillis() - requestedAt > 10000) {
            pending = false;
            status = tr("gtsr.navigator.timeout");
            buttons();
        }
    }

    @Override
    protected void keyTyped(char key, int code) {
        String before = search.getText();
        if (search.textboxKeyTyped(key, code)) {
            if (!before.equals(search.getText())) filter();
        } else super.keyTyped(key, code);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int candidate = page + (wheel > 0 ? -1 : 1);
            if (candidate >= 0 && candidate * rows * 2 < filtered.size()) {
                page = candidate;
                buttons();
            }
        }
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        int bottom = top + rows * 23 + 142;
        drawRect(left - 2, top - 2, left + panelWidth + 2, bottom + 2, 0xffa18b54);
        drawRect(left, top, left + panelWidth, bottom, 0xff182a30);
        drawRect(left + 1, top + 1, left + panelWidth - 1, top + 32, 0xff263e43);
        drawCenteredString(fontRendererObj, tr("gtsr.navigator.title"), width / 2, top + 12, 0xffe8d49a);
        search.drawTextBox();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocalFormatted(
                "gtsr.navigator.page",
                filtered.size(),
                page + 1,
                Math.max(1, (filtered.size() + rows * 2 - 1) / (rows * 2))),
            width / 2,
            top + 82 + rows * 23,
            0xffa4c7cc);
        if (filtered.isEmpty())
            drawCenteredString(fontRendererObj, tr("gtsr.navigator.no_match"), width / 2, top + 83, 0xffbfc6ba);
        fontRendererObj.drawSplitString(status, left + 16, bottom - 17, panelWidth - 32, 0xffe8d49a);
        super.drawScreen(x, y, partial);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
