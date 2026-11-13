// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.network;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The login packet owns the server recipient UUID; the launcher Session profile is not that identity. */
@SideOnly(Side.CLIENT)
public final class ClientRecipientIdentity {

    // Values contain no connection reference. Netty login writes and client-thread reads share this lock.
    private static final Map<NetworkManager, Login> LOGINS = new WeakHashMap<>();

    private ClientRecipientIdentity() {}

    public static synchronized void recordLogin(NetworkManager connection, GameProfile profile) {
        if (connection == null) return;
        if (profile == null || profile.getId() == null) LOGINS.remove(connection);
        else LOGINS.put(connection, new Login(profile.getId()));
    }

    private static synchronized Login login(NetworkManager connection) {
        return LOGINS.get(connection);
    }

    public static Audience capture(INetHandler source, int dimension, UUID recipient) {
        if (!(source instanceof NetHandlerPlayClient)) return null;
        NetHandlerPlayClient handler = (NetHandlerPlayClient) source;
        NetworkManager manager = handler.getNetworkManager();
        Login login = login(manager);
        Minecraft mc = Minecraft.getMinecraft();
        if (login == null || mc.theWorld == null || mc.thePlayer == null) return null;
        Audience audience = new Audience(source, mc.theWorld, mc.thePlayer, login, dimension, recipient);
        return audience.current() ? audience : null;
    }

    private static final class Login {

        final UUID player;

        Login(UUID player) {
            this.player = player;
        }
    }

    /** A queued callback and retained effect both keep their originating connection/login/world/player objects. */
    public static final class Audience {

        private final Object connection, world, actor, epoch;
        private final int dimension;
        private final UUID recipient;

        Audience(Object connection, Object world, Object actor, Object epoch, int dimension, UUID recipient) {
            this.connection = connection;
            this.world = world;
            this.actor = actor;
            this.epoch = epoch;
            this.dimension = dimension;
            this.recipient = recipient;
        }

        public UUID player() {
            return recipient;
        }

        public boolean current() {
            Minecraft mc = Minecraft.getMinecraft();
            NetHandlerPlayClient current = mc.getNetHandler();
            NetworkManager manager = current == null ? null : current.getNetworkManager();
            Login login = login(manager);
            return matches(
                current,
                mc.theWorld,
                mc.thePlayer,
                login,
                mc.thePlayer == null ? 0 : mc.thePlayer.dimension,
                login == null ? null : login.player,
                manager != null && manager.channel() != null && manager.isChannelOpen());
        }

        boolean matches(Object currentConnection, Object currentWorld, Object currentActor, Object currentEpoch,
            int currentDimension, UUID serverPlayer, boolean open) {
            return connection != null && connection == currentConnection
                && open
                && world != null
                && world == currentWorld
                && actor != null
                && actor == currentActor
                && epoch != null
                && epoch == currentEpoch
                && dimension == currentDimension
                && recipient != null
                && recipient.equals(serverPlayer);
        }
    }
}
