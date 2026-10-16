package com.miaokatze.gtsr.common.gui.mui2.hub;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Separate registration and rollout boundary for the nearby, immutable node anchor. */
public final class CacheNodeEditorFactory {

    private CacheNodeEditorFactory() {}

    public static void register() {
        HubTerminalFactory.NODE.registerInstance();
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity base) {
        return HubTerminalFactory.open(TerminalUiType.CACHE_NODE, player, base);
    }
}
