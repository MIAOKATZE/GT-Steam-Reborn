package com.miaokatze.gtsr.client.gui.mui2.lore;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarStoryNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.TileSpacetimeAltarStory;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Authenticated reading presentation; closing never sends an activation. */
@SideOnly(Side.CLIENT)
public final class SpacetimeAltarStoryScreen extends ModularScreen {

    public static final String PANEL = "spacetime_altar_story";
    private final Model model;
    private final WorldClient world;
    private final TileEntity board;

    public SpacetimeAltarStoryScreen(Model model, WorldClient world) {
        super("gtsr", model.buildPanel());
        this.model = model;
        this.world = world;
        this.board = world.getTileEntity(model.reading.x, model.reading.y, model.reading.z);
        pausesGame(false);
    }

    /** Caller must retain the original main-thread world/player/board/instance/range checks. */
    public static void open(SpacetimeAltarStoryNetwork.Reading reading, WorldClient world) {
        ClientGUI.open(new SpacetimeAltarStoryScreen(new Model(reading), world));
    }

    public Model model() {
        return model;
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(Math.max(1, Math.min(420, width - 16)), Math.max(1, Math.min(300, height - 16)));
        super.onResize(width, height);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world || mc.thePlayer == null
            || mc.thePlayer.dimension != model.reading.dimension
            || world.getTileEntity(model.reading.x, model.reading.y, model.reading.z) != board
            || !(board instanceof TileSpacetimeAltarStory)
            || !model.reading.instance.equals(((TileSpacetimeAltarStory) board).instanceId())
            || mc.thePlayer.getDistanceSq(model.reading.x + .5, model.reading.y + .5, model.reading.z + .5) > 64)
            close();
    }

    public static final class Model {

        private final SpacetimeAltarStoryNetwork.Reading reading;
        private boolean submitted;

        public Model(SpacetimeAltarStoryNetwork.Reading reading) {
            this.reading = reading;
        }

        public boolean submitted() {
            return submitted;
        }

        public void activate() {
            if (submitted) return;
            submitted = true;
            SpacetimeAltarStoryNetwork.activate(reading.nonce);
        }

        public ModularPanel buildPanel() {
            ModularPanel panel = new ModularPanel(PANEL).size(420, 300)
                .background(TerminalUiStyle.PANEL);
            ListWidget<IWidget, ?> text = new ListWidget<>().name("altar.paragraphs")
                .widthRel(1f)
                .expanded();
            LoreWidgets.paragraphs(
                text,
                Arrays.asList(
                    "gtsr.altar.story.1",
                    "gtsr.altar.story.2",
                    "gtsr.altar.story.3",
                    "gtsr.altar.story.4",
                    "gtsr.altar.story.5"));
            panel.child(
                Flow.col()
                    .sizeRel(1f)
                    .padding(10)
                    .childPadding(6)
                    .child(
                        IKey.lang("lore.entry.structures.spacetime_altar.title")
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_TITLE)
                            .widthRel(1f)
                            .height(20))
                    .child(text)
                    .child(
                        LoreWidgets.button("altar.activate", "gtsr.altar.activate", this::activate)
                            .widthRel(1f)
                            .setEnabledIf(w -> !submitted))
                    .child(
                        LoreWidgets.button("altar.close", "gui.done", LoreWidgets::close)
                            .widthRel(1f)));
            return panel;
        }
    }
}
