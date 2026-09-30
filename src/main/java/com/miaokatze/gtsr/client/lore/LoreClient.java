package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class LoreClient {

    private LoreClient() {}

    public static void receive(final LoreNetwork.Snapshot snapshot) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient receivingWorld = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (mc.theWorld == null || mc.theWorld != receivingWorld
                    || mc.thePlayer == null
                    || mc.thePlayer.dimension != snapshot.dimension
                    || !mc.thePlayer.getUniqueID()
                        .equals(snapshot.player))
                    return;
                if (snapshot.open) {
                    if (mc.thePlayer.getHeldItem() == null || mc.thePlayer.getHeldItem()
                        .getItem() != LoreRegistry.journal) return;
                    mc.displayGuiScreen(new GuiProsperityJournal(snapshot.kingUnlocked, snapshot.progress));
                } else if (mc.currentScreen instanceof GuiProsperityJournal) {
                    ((GuiProsperityJournal) mc.currentScreen).setKingUnlocked(snapshot.kingUnlocked);
                    ((GuiProsperityJournal) mc.currentScreen).setProgress(snapshot.progress);
                }
            }
        });
    }
}
