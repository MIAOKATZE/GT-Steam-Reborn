// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.INetHandler;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.miaokatze.gtsr.client.gui.mui2.lore.ProsperityJournalScreen;
import com.miaokatze.gtsr.client.network.ClientRecipientIdentity;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class LoreClient {

    private LoreClient() {}

    public static void receiveTitle(final LoreNetwork.StructureTitle title, INetHandler source) {
        final ClientRecipientIdentity.Audience audience = ClientRecipientIdentity
            .capture(source, title.dimension, title.player);
        if (audience == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient world = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (!audience.current()) return;
                StructureTitleOverlay.show(world, title.key);
            }
        });
    }

    public static void receive(final LoreNetwork.Snapshot snapshot, INetHandler source) {
        final ClientRecipientIdentity.Audience audience = ClientRecipientIdentity
            .capture(source, snapshot.dimension, snapshot.player);
        if (audience == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (!audience.current()) return;
                if (snapshot.open) {
                    if (mc.thePlayer.getHeldItem() == null || mc.thePlayer.getHeldItem()
                        .getItem() != LoreRegistry.journal) return;
                    if (Mui2TerminalRoutes.storyScreens) ProsperityJournalScreen.open(snapshot);
                    else mc.displayGuiScreen(
                        new GuiProsperityJournal(
                            snapshot.kingUnlocked,
                            snapshot.progress,
                            snapshot.sceneEntries,
                            snapshot.sceneBattles));
                } else if (ModularScreen.getCurrent() instanceof ProsperityJournalScreen journal) {
                    journal.receive(snapshot);
                } else if (mc.currentScreen instanceof GuiProsperityJournal) {
                    ((GuiProsperityJournal) mc.currentScreen).setKingUnlocked(snapshot.kingUnlocked);
                    ((GuiProsperityJournal) mc.currentScreen).setProgress(snapshot.progress);
                    ((GuiProsperityJournal) mc.currentScreen).setSceneEntries(snapshot.sceneEntries);
                    ((GuiProsperityJournal) mc.currentScreen).setSceneBattles(snapshot.sceneBattles);
                }
            }
        });
    }
}
