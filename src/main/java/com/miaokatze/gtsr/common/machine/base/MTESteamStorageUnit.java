package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;

public abstract class MTESteamStorageUnit extends MTETieredMachineBlock {

    public static final int PRESSURE_CAPACITY = 320_000_000;
    public static final int REINFORCED_CAPACITY = 1_280_000_000;

    protected final long capacityPerUnit;

    public MTESteamStorageUnit(int aID, String aName, String aNameRegional, long capacity) {
        super(aID, aName, aNameRegional, 0, 0, "Steam Storage Unit for Steam Hub Array");
        this.capacityPerUnit = capacity;
    }

    public MTESteamStorageUnit(String aName, long capacity, String[] aDescription, ITexture[][][] aTextures) {
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

    public long getCapacityPerUnit() {
        return capacityPerUnit;
    }
}
