// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui.mui2;

import net.minecraft.network.NetHandlerPlayServer;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

/** Common live connection/container gate only; factories retain all local/remote business permissions. */
public final class Mui2SessionGate {

    private Mui2SessionGate() {}

    public static boolean active(SyncHandler<?> session, PosGuiData data, NetHandlerPlayServer connection) {
        return session.isValid() && !session.getSyncManager()
            .isClient()
            && ServerPacketAuthority.isCurrentPlayer(data.getPlayer(), connection)
            && data.getPlayer().openContainer == session.getSyncManager()
                .getContainer();
    }
}
