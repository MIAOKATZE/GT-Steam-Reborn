package com.miaokatze.gtsr.common.critical;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

/** Vanilla edit permission follows the task owner even while that owner is offline or in another dimension. */
final class CriticalConstructionAccess {

    // Forge 1.7's global FakePlayerFactory is keyed only by profile, not by WorldServer.
    // This controller-local actor is rebuilt on either world or owner change, never shared across worlds.
    private FakePlayer offlineOwner;

    boolean canEdit(World world, String owner, int x, int y, int z) {
        if (world == null || world.isRemote || owner == null) return false;
        UUID ownerId;
        try {
            ownerId = UUID.fromString(owner);
            if (!ownerId.toString().equalsIgnoreCase(owner)) return false;
        } catch (IllegalArgumentException invalidOwner) {
            return false;
        }
        EntityPlayer actor = null;
        for (Object object : world.playerEntities) {
            if (object instanceof EntityPlayer player && player.worldObj == world
                && ownerId.equals(player.getUniqueID())) {
                actor = player;
                break;
            }
        }
        if (actor == null) {
            if (!(world instanceof WorldServer server)) return false;
            if (offlineOwner == null || offlineOwner.worldObj != world
                || !ownerId.equals(offlineOwner.getUniqueID()))
                offlineOwner = new FakePlayer(server, new GameProfile(ownerId, "[GTSR Builder]"));
            actor = offlineOwner;
        }
        return world.canMineBlock(actor, x, y, z);
    }
}
