package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;

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
                if (result.status == 0) mc.displayGuiScreen(new GuiProsperityNavigator());
                else if (mc.currentScreen instanceof GuiProsperityNavigator)
                    ((GuiProsperityNavigator) mc.currentScreen).receive(result);
            }
        });
    }
}
