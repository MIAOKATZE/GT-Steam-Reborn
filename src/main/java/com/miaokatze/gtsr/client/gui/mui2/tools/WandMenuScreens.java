package com.miaokatze.gtsr.client.gui.mui2.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.debug.MachineDebugRegistry;
import com.miaokatze.gtsr.common.debug.MachineDebugRegistry.Option;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.network.WandNet;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client presentation only. Every edit keeps the original validated wand protocol. */
@SideOnly(Side.CLIENT)
public final class WandMenuScreens extends ModularScreen {

    public static final String FAMILIES = "wand_families", PROFILE = "wand_profile";
    private final boolean profile;

    private WandMenuScreens(ModularPanel panel, boolean profile) {
        super("gtsr", panel);
        this.profile = profile;
        pausesGame(false);
    }

    public static void openFamilies() {
        ClientGUI.open(new WandMenuScreens(familiesPanel(), false));
    }

    public static void openProfile(int token, String family, Option[] options, int[] values, boolean air) {
        ClientGUI.open(new WandMenuScreens(profilePanel(token, family, options, values, air), true));
    }

    public static ModularPanel familiesPanel() {
        ModularPanel panel = new ModularPanel(FAMILIES).size(320, 260)
            .background(TerminalUiStyle.PANEL);
        ListWidget<IWidget, ?> families = new ListWidget<>().name("wand.family.list")
            .widthRel(1f)
            .expanded();
        for (String family : MachineDebugRegistry.families()) {
            families.child(
                TerminalUiStyle.button("gtsr.wand.debug.family." + family)
                    .name("wand.family." + family)
                    .widthRel(1f)
                    .height(24)
                    .onMousePressed(button -> {
                        if (button != 0) return false;
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.thePlayer == null) return true;
                        var held = mc.thePlayer.getHeldItem();
                        if (held == null || !(held.getItem() instanceof SingularityTuningWand)) return true;
                        var adapter = MachineDebugRegistry.findFamily(family);
                        if (adapter != null) openProfile(
                            0,
                            family,
                            adapter.options(null),
                            MachineDebugRegistry.readProfile(held, adapter, null),
                            true);
                        return true;
                    }));
        }
        families.child(
            IKey.lang("gtsr.wand.debug.air_hint")
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .widthRel(1f)
                .textAlign(Alignment.TopLeft)
                .marginTop(5));
        panel.child(
            Flow.col()
                .sizeRel(1f)
                .padding(10)
                .childPadding(5)
                .child(
                    IKey.lang("gtsr.wand.debug.title")
                        .asWidget()
                        .color(GtsrGuiPalette.TEXT_TITLE)
                        .widthRel(1f)
                        .height(20))
                .child(families)
                .child(done(false)));
        return panel;
    }

    public static ModularPanel profilePanel(int token, String family, Option[] options, int[] initial, boolean air) {
        int[] values = initial.clone();
        ModularPanel panel = new ModularPanel(PROFILE).size(360, 300)
            .background(TerminalUiStyle.PANEL);
        ListWidget<IWidget, ?> rows = new ListWidget<>().name("wand.profile.options")
            .widthRel(1f)
            .expanded();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            if (values[index] < 0 || values[index] >= options[index].values().length) values[index] = 0;
            rows.child(
                Flow.col()
                    .name("wand.option." + options[i].key())
                    .widthRel(1f)
                    .coverChildrenHeight()
                    .child(
                        IKey.lang("gtsr.wand.debug.option." + options[i].key())
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_LABEL)
                            .widthRel(1f))
                    .child(
                        new ButtonWidget<>().name("wand.choice." + options[i].key())
                            .background(TerminalUiStyle.BUTTON)
                            .hoverBackground(TerminalUiStyle.HOVER)
                            .widthRel(1f)
                            .height(24)
                            .overlay(
                                IKey.dynamic(
                                    () -> StatCollector.translateToLocal(options[index].values()[values[index]]))
                                    .color(GtsrGuiPalette.TEXT_BODY))
                            .onMousePressed(button -> {
                                if (button != 0) return false;
                                values[index] = (values[index] + 1) % options[index].values().length;
                                if (air) WandNet.sendAirProfile(family, values);
                                else WandNet.sendProfile(token, values);
                                return true;
                            })));
        }
        rows.child(
            IKey.lang(
                air ? "gtsr.wand.debug.air_hint"
                    : "construction".equals(family) ? "gtsr.wand.debug.construction_immediate" : "gtsr.wand.debug.hint")
                .asWidget()
                .color(GtsrGuiPalette.TEXT_BODY)
                .widthRel(1f)
                .marginTop(6)
                .textAlign(Alignment.TopLeft));
        if ("construction".equals(family)) rows.child(
            IKey.lang("gtsr.wand.debug.scope")
                .asWidget()
                .color(GtsrGuiPalette.TEXT_MUTED)
                .widthRel(1f)
                .marginTop(6)
                .textAlign(Alignment.TopLeft));
        Flow content = Flow.col()
            .sizeRel(1f)
            .padding(10)
            .childPadding(5)
            .child(
                IKey.lang("gtsr.wand.debug.title")
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_TITLE)
                    .widthRel(1f)
                    .height(18))
            .child(
                IKey.lang("gtsr.wand.debug.family." + family)
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_LABEL)
                    .widthRel(1f)
                    .height(16))
            .child(rows);
        panel.child(content.child(done(air)));
        return panel;
    }

    private static ButtonWidget<?> done(boolean back) {
        return TerminalUiStyle.button("gtsr.wand.ui.done")
            .name("wand.done")
            .widthRel(1f)
            .height(20)
            .onMousePressed(button -> {
                if (button != 0) return false;
                if (back) openFamilies();
                else {
                    ModularScreen screen = ModularScreen.getCurrent();
                    if (screen != null) screen.close();
                }
                return true;
            });
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(
            Math.max(1, Math.min(profile ? 360 : 320, width - 12)),
            Math.max(1, Math.min(profile ? 300 : 260, height - 12)));
        super.onResize(width, height);
    }
}
