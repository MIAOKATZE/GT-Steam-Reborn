package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.miaokatze.gtsr.client.gui.mui2.lore.ProsperityNavigatorScreen;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class NavigatorClient {

    private NavigatorClient() {}

    public static void receive(final NavigatorNetwork.Result result) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient world = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (world == null || mc.theWorld != world
                    || mc.thePlayer == null
                    || mc.thePlayer.dimension != result.dimension
                    || !mc.thePlayer.getUniqueID()
                        .equals(result.player)
                    || !NavigatorNetwork.holdsCompass(mc.thePlayer)) return;
                if (result.status == 0) {
                    if (Mui2TerminalRoutes.storyScreens) ProsperityNavigatorScreen.open();
                    else mc.displayGuiScreen(new GuiProsperityNavigator());
                } else if (ModularScreen.getCurrent() instanceof ProsperityNavigatorScreen navigator)
                    navigator.receive(result);
                else if (mc.currentScreen instanceof GuiProsperityNavigator)
                    ((GuiProsperityNavigator) mc.currentScreen).receive(result);
            }
        });
    }
}
