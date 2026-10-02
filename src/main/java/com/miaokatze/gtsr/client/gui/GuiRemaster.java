package com.miaokatze.gtsr.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Mouse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.ContainerRemaster;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Field names, values and clues come from the server; answers are never sent to the client. */
@SideOnly(Side.CLIENT)
public final class GuiRemaster extends GuiContainer {

    private final TileRemasterNode tile;
    private String lastDisplay = "";
    private JsonObject view = new JsonObject();
    private int clueScroll;
    private String lastClue = "";
    private int clueLines = 4;

    public GuiRemaster(TileRemasterNode tile) {
        super(new ContainerRemaster(tile));
        this.tile = tile;
        xSize = 300;
        ySize = 280;
    }

    @Override
    public void initGui() {
        ySize = Math.min(280, height - 8);
        clueLines = ySize >= 240 ? 4 : 2;
        super.initGui();
        lastDisplay = "";
        updateButtons();
    }

    private void updateButtons() {
        if (lastDisplay.equals(tile.display)) return;
        lastDisplay = tile.display;
        view = new JsonParser().parse(lastDisplay)
            .getAsJsonObject();
        String clue = view.has("clue") ? view.get("clue")
            .getAsString() : "";
        if (!lastClue.equals(clue)) clueScroll = 0;
        lastClue = clue;
        clueScroll = Math.min(
            clueScroll,
            Math.max(
                0,
                fontRendererObj.listFormattedStringToWidth(clue, 276)
                    .size() - clueLines));
        buttonList.clear();
        JsonArray fields = view.has("fields") ? view.getAsJsonArray("fields") : new JsonArray();
        int fieldTop = 42 + clueLines * 10;
        int spacing = Math.max(11, (ySize - fieldTop - 56) / 8);
        for (int i = 0; i < fields.size() && i < 8; i++) buttonList.add(
            new GuiButton(
                i,
                guiLeft + 12,
                guiTop + fieldTop + i * spacing,
                276,
                spacing - 1,
                fields.get(i)
                    .getAsString()));
        buttonList.add(new GuiButton(100, guiLeft + 12, guiTop + ySize - 26, 88, 20, "读取 / 核验"));
        if (!view.has("readOnly") || !view.get("readOnly")
            .getAsBoolean()) {
            buttonList.add(new GuiButton(101, guiLeft + 106, guiTop + ySize - 26, 88, 20, "安全复位"));
            buttonList.add(new GuiButton(102, guiLeft + 200, guiTop + ySize - 26, 88, 20, "提交 / 解封"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        updateButtons();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xff252b2c);
        drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + 30, 0xff57462e);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(
            view.has("title") ? view.get("title")
                .getAsString() : "现场工单",
            12,
            12,
            0xffeddfb8);
        String clue = view.has("clue") ? view.get("clue")
            .getAsString() : "";
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(clue, 276);
        int wheel = Mouse.getDWheel();
        if (wheel != 0) clueScroll = Math
            .max(0, Math.min(Math.max(0, lines.size() - clueLines), clueScroll + (wheel < 0 ? 1 : -1)));
        for (int i = 0; i < clueLines && i + clueScroll < lines.size(); i++)
            fontRendererObj.drawString(lines.get(i + clueScroll), 12, 36 + i * 10, 0xffc2cebf);
        if (view.has("status")) fontRendererObj.drawSplitString(
            view.get("status")
                .getAsString(),
            12,
            ySize - 50,
            276,
            0xffe0c98a);
    }
}
