// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.miaokatze.gtsr.client.gui.mui2.lore.ProsperityNavigatorScreen;
import com.miaokatze.gtsr.client.network.ClientRecipientIdentity;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class NavigatorClient {

    private NavigatorClient() {}

    public static void receive(final NavigatorNetwork.Result result, INetHandler source) {
        final ClientRecipientIdentity.Audience audience = ClientRecipientIdentity
            .capture(source, result.dimension, result.player);
        if (audience == null) return;
        final Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(new Runnable() {

            @Override
            public void run() {
                if (!audience.current() || !NavigatorNetwork.holdsCompass(mc.thePlayer)) return;
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
