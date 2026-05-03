package com.miaokatze.gtsgu.common.machine.base;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;

public abstract class MTESteamStorageUnit extends MTETieredMachineBlock {

    public static final int PRESSURE_CAPACITY = 16_000_000;
    public static final int REINFORCED_CAPACITY = 64_000_000;

    protected final int capacityPerUnit;

    public MTESteamStorageUnit(int aID, String aName, String aNameRegional, int capacity) {
        super(aID, aName, aNameRegional, 0, 0, "Steam Storage Unit for Steam Hub Array");
        this.capacityPerUnit = capacity;
    }

    public MTESteamStorageUnit(String aName, int capacity, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, 0, 0, aDescription, aTextures);
        this.capacityPerUnit = capacity;
    }

    @Override
    public abstract MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity);

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != ForgeDirection.UNKNOWN;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {}

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {}

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        return null;
    }

    public int getCapacityPerUnit() {
        return capacityPerUnit;
    }
}
