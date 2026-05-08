package com.miaokatze.gtsr.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

@Mixin(value = MTEHatchSteamBusOutput.class, remap = false)
public abstract class MTEHatchSteamBusOutputMixin extends MTEHatchOutputBus {

    public MTEHatchSteamBusOutputMixin(String aName, int aTier, String[] aDescription,
        gregtech.api.interfaces.ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    /**
     * @reason Enable auto-output for steam output bus, same as standard output bus behavior
     * @author GTSR
     */
    @Overwrite
    public boolean pushOutputInventory() {
        return true;
    }
}
