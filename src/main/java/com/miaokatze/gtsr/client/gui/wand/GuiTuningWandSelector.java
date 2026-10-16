package com.miaokatze.gtsr.client.gui.wand;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.debug.MachineDebugRegistry;
import com.miaokatze.gtsr.common.items.SingularityTuningWand;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Air selection edits family profiles; machine application remains target validated. */
@SideOnly(Side.CLIENT)
public class GuiTuningWandSelector extends GuiScreen {

    private final String[] families = MachineDebugRegistry.families();
    private static final int WIDTH = 280;

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        for (int i = 0; i < families.length; i++) buttonList.add(
            new GuiButton(
                i,
                left() + 10,
                top() + 32 + i * 28,
                WIDTH - 20,
                20,
                localize("gtsr.wand.debug.family." + families[i])));
        buttonList
            .add(new GuiButton(99, left() + 100, top() + panelHeight() - 28, 80, 20, localize("gtsr.wand.ui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 99) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id < 0 || button.id >= families.length || mc.thePlayer == null) return;
        var held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof SingularityTuningWand)) return;
        var adapter = MachineDebugRegistry.findFamily(families[button.id]);
        mc.displayGuiScreen(
            new GuiMachineDebugSelector(
                0,
                adapter.family(),
                adapter.options(null),
                MachineDebugRegistry.readProfile(held, adapter, null),
                true));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left() - 1, top() - 1, left() + WIDTH + 1, top() + panelHeight() + 1, 0xFF50505A);
        drawRect(left(), top(), left() + WIDTH, top() + panelHeight(), 0xF0101014);
        drawCenteredString(fontRendererObj, localize("gtsr.wand.debug.title"), width / 2, top() + 10, 0xE0B341);
        fontRendererObj.drawSplitString(
            localize("gtsr.wand.debug.air_hint"),
            left() + 10,
            top() + 36 + families.length * 28,
            WIDTH - 20,
            0x7FDFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int panelHeight() {
        return 118 + families.length * 28;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, (height - panelHeight()) / 2);
    }

    private static String localize(String key) {
        return StatCollector.translateToLocal(key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
