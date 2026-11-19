// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.gui;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only item and input queries used by the mirrored native widget tree. */
@SideOnly(Side.CLIENT)
public final class CenterTerminalClient {

    private CenterTerminalClient() {}

    public static EntityPlayer player() {
        return Minecraft.getMinecraft().thePlayer;
    }

    public static List<ItemStack> catalog() {
        return Loader.isModLoaded("NotEnoughItems") ? codechicken.nei.ItemList.items : Collections.emptyList();
    }

    public static boolean control() {
        return GuiScreen.isCtrlKeyDown();
    }

    public static boolean shift() {
        return GuiScreen.isShiftKeyDown();
    }
}
