package com.miaokatze.gtsr.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.util.GTUtility;

/** Shared world-loading and movement helpers used by hub status teleport actions. */
public final class HubTeleportUtil {

    private HubTeleportUtil() {}

    public static World resolveTargetWorld(EntityPlayer player, int dimensionId) {
        if (player == null) return null;
        if (player.dimension == dimensionId) return player.worldObj;
        if (!DimensionManager.isDimensionRegistered(dimensionId)) return null;

        World world = DimensionManager.getWorld(dimensionId);
        if (world == null) {
            DimensionManager.initDimension(dimensionId);
            world = DimensionManager.getWorld(dimensionId);
        }
        return world;
    }

    /**
     * Loads the target chunk once when a user explicitly requests a remote operation.
     * Normal status polling must not call this helper.
     */
    public static boolean ensureChunkLoaded(World world, int x, int z) {
        if (world == null) return false;
        if (!world.blockExists(x, 0, z)) {
            try {
                world.getChunkProvider()
                    .loadChunk(x >> 4, z >> 4);
            } catch (Exception ignored) {
                return false;
            }
        }
        return world.blockExists(x, 0, z);
    }

    public static int findSafeTeleportHeight(World world, int x, int y, int z) {
        if (world == null) return -1;
        int maxY = world.getActualHeight() - 2;
        for (int targetY = y + 1; targetY <= maxY; targetY++) {
            if (world.isAirBlock(x, targetY, z) && world.isAirBlock(x, targetY + 1, z)
                && world.getBlock(x, targetY - 1, z)
                    .getMaterial()
                    .blocksMovement()) {
                return targetY;
            }
        }
        return -1;
    }

    /** Moves the player after all target validation and safe-height checks have completed. */
    public static boolean teleportPlayer(EntityPlayer player, World targetWorld, int targetDimension, int x, int y,
        int z) {
        if (!(player instanceof EntityPlayerMP playerMP) || targetWorld == null) return false;
        if (!consumeSteamEntangledSingularity(player)) return false;

        double targetX = x + 0.5D;
        double targetY = y;
        double targetZ = z + 0.5D;
        if (player.dimension == targetDimension) {
            playerMP.closeScreen();
            if (player.ridingEntity != null) player.mountEntity(null);
            if (player.riddenByEntity != null) player.riddenByEntity.mountEntity(null);
            playerMP.playerNetServerHandler
                .setPlayerLocation(targetX, targetY, targetZ, player.rotationYaw, player.rotationPitch);
        } else {
            GTUtility.moveEntityToDimensionAtCoords(playerMP, targetDimension, targetX, targetY, targetZ);
        }
        return true;
    }

    private static boolean consumeSteamEntangledSingularity(EntityPlayer player) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(stack, true, true)) {
                stack.stackSize--;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
                player.inventoryContainer.detectAndSendChanges();
                return true;
            }
        }
        return false;
    }
}
