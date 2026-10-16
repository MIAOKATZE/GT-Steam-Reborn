package com.miaokatze.gtsr.client.gui.wand;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.debug.MachineDebugRegistry.Option;
import com.miaokatze.gtsr.common.network.WandNet;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Air saves a wand profile; construction targets apply selections immediately; singularity selects an increment. */
@SideOnly(Side.CLIENT)
public final class GuiMachineDebugSelector extends GuiScreen {

    private final int token;
    private final String family;
    private final Option[] options;
    private final int[] values;
    private final boolean air;
    private static final int WIDTH = 320;

    public GuiMachineDebugSelector(int token, String family, Option[] options, int[] values) {
        this(token, family, options, values, false);
    }

    public GuiMachineDebugSelector(int token, String family, Option[] options, int[] values, boolean air) {
        this.token = token;
        this.family = family;
        this.options = options;
        this.values = values.clone();
        this.air = air;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        for (int i = 0; i < options.length; i++) {
            buttonList.add(new GuiButton(i, left() + 118, top() + 38 + i * 28, 192, 20, label(i)));
        }
        buttonList
            .add(new GuiButton(99, left() + 120, top() + panelHeight() - 27, 80, 20, localize("gtsr.wand.ui.done")));
    }

    private String label(int index) {
        String[] choices = options[index].values();
        int value = values[index];
        if (value < 0 || value >= choices.length) value = values[index] = 0;
        return localize(choices[value]);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 99) {
            mc.displayGuiScreen(air ? new GuiTuningWandSelector() : null);
            return;
        }
        if (button.id < 0 || button.id >= options.length) return;
        int i = button.id;
        values[i] = (values[i] + 1) % options[i].values().length;
        button.displayString = label(i);
        if (air) WandNet.sendAirProfile(family, values);
        else WandNet.sendProfile(token, values);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left() - 1, top() - 1, left() + WIDTH + 1, top() + panelHeight() + 1, 0xFF50505A);
        drawRect(left(), top(), left() + WIDTH, top() + panelHeight(), 0xF0101014);
        drawCenteredString(fontRendererObj, localize("gtsr.wand.debug.title"), width / 2, top() + 8, 0xE0B341);
        drawCenteredString(
            fontRendererObj,
            localize("gtsr.wand.debug.family." + family),
            width / 2,
            top() + 22,
            0xA0A0A0);
        for (int i = 0; i < options.length; i++) {
            fontRendererObj.drawStringWithShadow(
                localize("gtsr.wand.debug.option." + options[i].key()),
                left() + 10,
                top() + 44 + i * 28,
                0xFFFFFF);
        }
        fontRendererObj.drawSplitString(
            localize(
                air ? "gtsr.wand.debug.air_hint"
                    : family.equals("construction") ? "gtsr.wand.debug.construction_immediate"
                        : "gtsr.wand.debug.hint"),
            left() + 10,
            top() + 42 + options.length * 28,
            WIDTH - 20,
            0x7FDFFF);
        if (family.equals("construction")) fontRendererObj.drawSplitString(
            localize("gtsr.wand.debug.scope"),
            left() + 10,
            top() + 68 + options.length * 28,
            WIDTH - 20,
            0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int panelHeight() {
        return 130 + options.length * 28;
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
