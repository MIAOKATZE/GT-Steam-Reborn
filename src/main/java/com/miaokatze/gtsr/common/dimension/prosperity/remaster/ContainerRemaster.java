package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/** Vanilla window-bound button packets run on the server's normal container path. */
public final class ContainerRemaster extends Container {

    public final TileRemasterNode tile;

    public ContainerRemaster(TileRemasterNode tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile != null && !tile.isInvalid()
            && player.worldObj == tile.getWorldObj()
            && player.getDistanceSq(tile.xCoord + .5, tile.yCoord + .5, tile.zCoord + .5) <= 64
            && player.worldObj.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
            && player.worldObj.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile;
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int button) {
        if (!canInteractWith(player)) return false;
        return RemasterRuntime.action(player, tile, button);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }
}
