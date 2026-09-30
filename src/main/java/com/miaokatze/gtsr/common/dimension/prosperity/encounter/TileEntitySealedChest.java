package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;

/** No inventory exists while sealed: automation cannot extract a deferred roll. */
public class TileEntitySealedChest extends TileEntity {

    private int tier = 1, platform = -1, openingTicks = -1;
    private String encounter = "";

    public int getTier() {
        return tier;
    }

    public int getPlatformId() {
        return platform;
    }

    public String getEncounterId() {
        return encounter;
    }

    public int getOpeningTicks() {
        return openingTicks;
    }

    public void initialize(int t, String id, int p) {
        tier = Math.max(1, Math.min(5, t));
        encounter = id;
        platform = p;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private boolean allowed() {
        if (tier == 1) return openingTicks >= 0;
        if (encounter.isEmpty()) return false;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
        return platform < 0 ? d.kingDead(encounter) : d.platformCleared(encounter, platform);
    }

    public void tryUnlockByClick() {
        if (tier == 1 && openingTicks < 0) startOpening();
    }

    private void startOpening() {
        openingTicks = 0;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public static ItemStack lootForTier(int tier) {
        switch (tier) {
            case 1:
                return new ItemStack(Items.iron_ingot);
            case 2:
                return new ItemStack(Items.gold_ingot);
            case 3:
                return new ItemStack(Items.diamond);
            case 4:
                return new ItemStack(Blocks.diamond_block);
            case 5:
                return new ItemStack(Blocks.emerald_block);
            default:
                throw new IllegalArgumentException("tier");
        }
    }

    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (openingTicks < 0) {
            if (allowed()) startOpening();
            return;
        }
        if (++openingTicks < 60) {
            markDirty();
            if (openingTicks % 5 == 0) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return;
        }
        if (worldObj.getBlock(xCoord, yCoord, zCoord) != ForgottenLakeEncounterRegistry.sealedChest) return;
        if (worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.chest, 2, 3)) {
            TileEntity t = worldObj.getTileEntity(xCoord, yCoord, zCoord);
            if (t instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) t;
                chest.setInventorySlotContents(13, lootForTier(tier));
                chest.markDirty();
            }
        }
    }

    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        tier = n.hasKey("tier") ? n.getInteger("tier") : 1;
        platform = n.hasKey("platform") ? n.getInteger("platform") : -1;
        encounter = n.getString("encounter");
        openingTicks = n.hasKey("opening") ? n.getInteger("opening") : -1;
    }

    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setInteger("tier", tier);
        n.setInteger("platform", platform);
        n.setString("encounter", encounter);
        n.setInteger("opening", openingTicks);
    }

    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        writeToNBT(n);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, n);
    }

    public void onDataPacket(NetworkManager n, S35PacketUpdateTileEntity p) {
        readFromNBT(p.func_148857_g());
    }
}
