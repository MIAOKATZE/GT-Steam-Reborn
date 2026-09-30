package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/** Independent 27-slot chest: vanilla ContainerChest and hopper contract, never joins neighbours. */
public final class TileEntityUnsealedChest extends TileEntity implements IInventory {

    private ItemStack[] contents = new ItemStack[27];
    private int viewers, ticks;
    private String storyOrigin = "";
    private int storyEvent = -1;
    public float lidAngle, prevLidAngle;

    public void setStoryOrigin(String origin, int event) {
        storyOrigin = origin;
        storyEvent = event >= 8 && event <= 10 ? event : -1;
        markDirty();
    }

    public void recordStory(EntityPlayer player) {
        if (!worldObj.isRemote && isUseableByPlayer(player) && validStoryOrigin()) {
            com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress.visit(player, 27 + storyEvent - 8);
            com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress.event(player, storyEvent);
        }
    }

    private boolean validStoryOrigin() {
        if (!(worldObj instanceof net.minecraft.world.WorldServer)
            || !(worldObj.provider instanceof com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins)
            || storyEvent < 8
            || storyEvent > 10) return false;
        String[] parts = storyOrigin.split(":", -1);
        if (parts.length != 5 || !parts[0].equals("cache")
            || !parts[1].equals(new String[] { "mire", "archive", "rail" }[storyEvent - 8])) return false;
        try {
            if (Long.parseLong(parts[2]) != worldObj.getSeed()) return false;
            Integer.parseInt(parts[3]);
            Integer.parseInt(parts[4]);
        } catch (NumberFormatException invalid) {
            return false;
        }
        return com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData.get(worldObj)
            .created(storyOrigin, "chest");
    }

    public int getSizeInventory() {
        return contents.length;
    }

    public ItemStack getStackInSlot(int slot) {
        return contents[slot];
    }

    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = contents[slot];
        if (stack == null) return null;
        ItemStack out = stack.stackSize <= count ? stack : stack.splitStack(count);
        if (stack == out || stack.stackSize <= 0) contents[slot] = null;
        markDirty();
        return out;
    }

    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack out = contents[slot];
        contents[slot] = null;
        markDirty();
        return out;
    }

    public void setInventorySlotContents(int slot, ItemStack stack) {
        contents[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) stack.stackSize = getInventoryStackLimit();
        markDirty();
    }

    public String getInventoryName() {
        return "container.gtsr.unsealedChest";
    }

    public boolean hasCustomInventoryName() {
        return false;
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) <= 64;
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    private void syncViewers() {
        if (!worldObj.isRemote) worldObj.addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), 1, viewers);
    }

    public void openInventory() {
        if (!worldObj.isRemote) {
            viewers++;
            syncViewers();
        }
    }

    public void closeInventory() {
        if (!worldObj.isRemote) {
            viewers = Math.max(0, viewers - 1);
            syncViewers();
        }
    }

    public boolean receiveClientEvent(int id, int value) {
        if (id == 1) {
            viewers = value;
            return true;
        }
        return super.receiveClientEvent(id, value);
    }

    public void updateEntity() {
        if (!worldObj.isRemote && ++ticks % 200 == 0) {
            viewers = 0;
            for (Object obj : worldObj.getEntitiesWithinAABB(
                EntityPlayer.class,
                AxisAlignedBB.getBoundingBox(xCoord - 5, yCoord - 5, zCoord - 5, xCoord + 6, yCoord + 6, zCoord + 6))) {
                EntityPlayer player = (EntityPlayer) obj;
                if (player.openContainer instanceof ContainerChest
                    && ((ContainerChest) player.openContainer).getLowerChestInventory() == this) viewers++;
            }
            syncViewers();
        }
        prevLidAngle = lidAngle;
        lidAngle = Math.max(0, Math.min(1, lidAngle + (viewers > 0 ? .1F : -.1F)));
        if (!worldObj.isRemote && ((prevLidAngle == 0 && lidAngle > 0) || (prevLidAngle >= .5F && lidAngle < .5F))) {
            worldObj.playSoundEffect(
                xCoord + .5,
                yCoord + .5,
                zCoord + .5,
                lidAngle > prevLidAngle ? "random.chestopen" : "random.chestclosed",
                .5F,
                .85F);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        contents = new ItemStack[27];
        storyOrigin = tag.getString("storyOrigin");
        storyEvent = tag.hasKey("storyEvent") ? tag.getInteger("storyEvent") : -1;
        NBTTagList list = tag.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getByte("Slot") & 255;
            if (slot < contents.length) contents[slot] = ItemStack.loadItemStackFromNBT(item);
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < contents.length; slot++) if (contents[slot] != null) {
            NBTTagCompound item = new NBTTagCompound();
            item.setByte("Slot", (byte) slot);
            contents[slot].writeToNBT(item);
            list.appendTag(item);
        }
        tag.setTag("Items", list);
        tag.setString("storyOrigin", storyOrigin);
        tag.setInteger("storyEvent", storyEvent);
    }
}
