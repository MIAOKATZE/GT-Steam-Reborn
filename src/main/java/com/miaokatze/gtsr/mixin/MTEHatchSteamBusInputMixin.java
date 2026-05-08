package com.miaokatze.gtsr.mixin;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;

@Mixin(value = MTEHatchSteamBusInput.class, remap = false)
public abstract class MTEHatchSteamBusInputMixin extends MTEHatchInputBus {

    public MTEHatchSteamBusInputMixin(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Shadow(remap = false)
    public abstract int getCircuitSlot();

    /**
     * @reason Allow pipe extraction from steam input bus, same as standard input bus behavior
     * @author GTSR
     */
    @Overwrite
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return side == getBaseMetaTileEntity().getFrontFacing() && aIndex != getCircuitSlot();
    }
}
