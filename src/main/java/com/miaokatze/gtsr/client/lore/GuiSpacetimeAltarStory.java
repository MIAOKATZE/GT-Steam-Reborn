package com.miaokatze.gtsr.client.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarStoryNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.TileSpacetimeAltarStory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fixed localized narrative with wrapped, scrollable text; only an authenticated server read opens it. */
@SideOnly(Side.CLIENT)
public final class GuiSpacetimeAltarStory extends GuiScreen {

    private final SpacetimeAltarStoryNetwork.Reading reading;
    private final WorldClient world;
    private final List<String> lines = new ArrayList<>();
    private int left, top, panelWidth, panelHeight, firstLine, visibleLines;
    private boolean submitted;

    private GuiSpacetimeAltarStory(SpacetimeAltarStoryNetwork.Reading reading, WorldClient world) {
        this.reading = reading;
        this.world = world;
    }

    public static void receive(SpacetimeAltarStoryNetwork.Reading reading) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient receivingWorld = mc.theWorld;
        mc.func_152344_a(() -> {
            if (receivingWorld == null || mc.theWorld != receivingWorld
                || mc.thePlayer == null
                || mc.thePlayer.dimension != reading.dimension
                || !mc.thePlayer.getUniqueID()
                    .equals(reading.player))
                return;
            TileEntity actual = receivingWorld.getTileEntity(reading.x, reading.y, reading.z);
            if (!(actual instanceof TileSpacetimeAltarStory)
                || !reading.instance.equals(((TileSpacetimeAltarStory) actual).instanceId())
                || mc.thePlayer.getDistanceSq(reading.x + .5, reading.y + .5, reading.z + .5) > 64) return;
            mc.displayGuiScreen(new GuiSpacetimeAltarStory(reading, receivingWorld));
        });
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(420, Math.max(120, width - 16));
        panelHeight = Math.min(300, Math.max(100, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        visibleLines = Math.max(1, (panelHeight - 82) / 12);
        lines.clear();
        for (int i = 1; i <= 5; i++) {
            lines.addAll(
                fontRendererObj.listFormattedStringToWidth(
                    StatCollector.translateToLocal("gtsr.altar.story." + i),
                    panelWidth - 28));
            lines.add("");
        }
        firstLine = Math.min(firstLine, Math.max(0, lines.size() - visibleLines));
        buttonList.clear();
        GuiButton activate = new GuiButton(
            1,
            left + 10,
            top + panelHeight - 48,
            panelWidth - 20,
            20,
            StatCollector.translateToLocal("gtsr.altar.activate"));
        activate.enabled = !submitted;
        buttonList.add(activate);
        buttonList.add(
            new GuiButton(
                0,
                left + 10,
                top + panelHeight - 24,
                panelWidth - 20,
                20,
                StatCollector.translateToLocal("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1 && !submitted) {
            submitted = true;
            button.enabled = false;
            SpacetimeAltarStoryNetwork.activate(reading.nonce);
        }
        if (button.id == 0) mc.displayGuiScreen(null);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) firstLine = Math
            .max(0, Math.min(Math.max(0, lines.size() - visibleLines), firstLine + (wheel > 0 ? -3 : 3)));
    }

    @Override
    public void updateScreen() {
        if (mc.theWorld != world || mc.thePlayer == null
            || mc.thePlayer.dimension != reading.dimension
            || mc.thePlayer.getDistanceSq(reading.x + .5, reading.y + .5, reading.z + .5) > 64)
            mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xEE151D24);
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("lore.entry.structures.spacetime_altar.title"),
            width / 2,
            top + 10,
            0xC5EEFF);
        for (int i = 0; i < visibleLines && firstLine + i < lines.size(); i++)
            fontRendererObj.drawString(lines.get(firstLine + i), left + 12, top + 28 + i * 12, 0xE0E7EB);
        if (lines.size() > visibleLines) fontRendererObj
            .drawString((firstLine + 1) + "/" + lines.size(), left + panelWidth - 42, top + panelHeight - 62, 0x89A5B3);
        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
