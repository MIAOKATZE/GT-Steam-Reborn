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
    private int fieldScroll;
    private int visibleFields;
    private int fieldTop;

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
        boolean readOnly = view.has("readOnly") && view.get("readOnly")
            .getAsBoolean();
        clueLines = readOnly ? Math.max(4, (ySize - 112) / 10) : ySize >= 240 ? 5 : 3;
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
        fieldTop = 42 + clueLines * 10;
        visibleFields = Math.max(1, (ySize - fieldTop - 58) / 22);
        fieldScroll = Math.min(fieldScroll, Math.max(0, fields.size() - visibleFields));
        for (int row = 0; row < visibleFields && row + fieldScroll < fields.size(); row++) {
            int i = row + fieldScroll;
            buttonList.add(
                new GuiButton(
                    i,
                    guiLeft + 12,
                    guiTop + fieldTop + row * 22,
                    276,
                    20,
                    fontRendererObj.trimStringToWidth(
                        fields.get(i)
                            .getAsString(),
                        260)));
        }
        String action = view.has("actionLabel") ? view.get("actionLabel")
            .getAsString() : "读取 / 核验";
        boolean singleAction = view.has("singleAction") && view.get("singleAction")
            .getAsBoolean();
        buttonList.add(
            new GuiButton(100, guiLeft + 12, guiTop + ySize - 26, readOnly || singleAction ? 276 : 88, 20, action));
        if (!readOnly && !singleAction) {
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
        int localX = mouseX - guiLeft, localY = mouseY - guiTop;
        fontRendererObj.drawString(
            fontRendererObj.trimStringToWidth(
                view.has("title") ? view.get("title")
                    .getAsString() : "现场工单",
                276),
            12,
            12,
            0xffeddfb8);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(guidance(), 276), 12, 25, 0xffa4cf91);
        String clue = view.has("clue") ? view.get("clue")
            .getAsString() : "";
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(clue, 276);
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            JsonArray fields = view.has("fields") ? view.getAsJsonArray("fields") : new JsonArray();
            if (localX >= 12 && localX < 288
                && localY >= fieldTop
                && localY < fieldTop + visibleFields * 22
                && fields.size() > visibleFields) {
                fieldScroll = Math.max(0, Math.min(fields.size() - visibleFields, fieldScroll + (wheel < 0 ? 1 : -1)));
                lastDisplay = "";
            } else clueScroll = Math
                .max(0, Math.min(Math.max(0, lines.size() - clueLines), clueScroll + (wheel < 0 ? 1 : -1)));
        }
        for (int i = 0; i < clueLines && i + clueScroll < lines.size(); i++)
            fontRendererObj.drawString(lines.get(i + clueScroll), 12, 36 + i * 10, 0xffc2cebf);
        if (view.has("status")) {
            java.util.List<String> status = fontRendererObj.listFormattedStringToWidth(
                view.get("status")
                    .getAsString(),
                276);
            for (int i = 0; i < Math.min(2, status.size()); i++)
                fontRendererObj.drawString(status.get(i), 12, ySize - 50 + i * 10, 0xffe0c98a);
        }
        JsonArray fields = view.has("fields") ? view.getAsJsonArray("fields") : new JsonArray();
        if (localX >= 12 && localX < 288 && localY >= fieldTop) {
            int row = (localY - fieldTop) / 22;
            if (row < visibleFields && row + fieldScroll < fields.size()) drawHoveringText(
                fontRendererObj.listFormattedStringToWidth(
                    fields.get(row + fieldScroll)
                        .getAsString(),
                    240),
                localX,
                localY,
                fontRendererObj);
        }
    }

    private String guidance() {
        if (!view.has("guideTarget")) return "绿色角标 · 现场线索与机关 / 滚轮翻阅";
        JsonArray target = view.getAsJsonArray("guideTarget");
        if (target.size() != 3) return "沿现场指引寻找关联机关";
        int dx = target.get(0)
            .getAsInt() - tile.xCoord;
        int dy = target.get(1)
            .getAsInt() - tile.yCoord;
        int dz = target.get(2)
            .getAsInt() - tile.zCoord;
        StringBuilder text = new StringBuilder("关联机关：");
        if (dx != 0) text.append(dx > 0 ? "东" : "西")
            .append(Math.abs(dx))
            .append("格 ");
        if (dz != 0) text.append(dz > 0 ? "南" : "北")
            .append(Math.abs(dz))
            .append("格 ");
        if (dy != 0) text.append(dy > 0 ? "上层" : "下层")
            .append(Math.abs(dy))
            .append("格");
        if (dx == 0 && dy == 0 && dz == 0) text.append("当前操作台");
        return text.toString();
    }
}
