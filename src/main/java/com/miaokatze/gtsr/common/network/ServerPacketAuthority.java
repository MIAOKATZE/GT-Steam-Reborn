package com.miaokatze.gtsr.common.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

/** Main-thread liveness check for work captured by a specific connection, not just a UUID. */
public final class ServerPacketAuthority {

    private ServerPacketAuthority() {}

    public static NetHandlerPlayServer connection(EntityPlayer player) {
        return player instanceof EntityPlayerMP mp ? mp.playerNetServerHandler : null;
    }

    public static boolean isCurrentPlayer(EntityPlayer player, NetHandlerPlayServer connection) {
        return player instanceof EntityPlayerMP mp && isCurrentPlayer(mp, connection);
    }

    public static boolean isCurrentPlayer(EntityPlayer player) {
        return player instanceof EntityPlayerMP mp && isCurrentPlayer(mp, mp.playerNetServerHandler);
    }

    public static boolean isCurrentPlayer(EntityPlayerMP player, NetHandlerPlayServer connection) {
        if (player == null || player.isDead || player instanceof FakePlayer
            || connection == null || player.playerNetServerHandler != connection
            || connection.playerEntity != player || connection.netManager == null
            || !connection.netManager.isChannelOpen()
            || player.worldObj == null || player.worldObj.isRemote) return false;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
            || server.worldServerForDimension(player.dimension) != player.worldObj) return false;
        // Object identity matters after respawn/reconnect, even if both entities have the same UUID.
        for (Object online : server.getConfigurationManager().playerEntityList)
            if (online == player) return true;
        return false;
    }
}
