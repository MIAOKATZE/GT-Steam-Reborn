package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import gregtech.api.enums.Materials;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

@Mixin(value = MTESteamMultiBase.class, remap = false)
public abstract class MTESteamMultiBaseMixin {

    @Shadow(remap = false)
    public ArrayList<MTEHatchCustomFluidBase> mSteamInputFluids;

    private MTESteamMultiBase gtsr$self() {
        return (MTESteamMultiBase) (Object) this;
    }

    /**
     * @author GTSBU
     * @reason Support ic2superheatedsteam as steam source
     */
    @Overwrite
    public ArrayList<FluidStack> getAllSteamStacks() {
        ArrayList<FluidStack> aFluids = new ArrayList<>();
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch == null || !hatch.isValid()) continue;
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.getFluid() != null && fs.amount > 0) {
                String name = fs.getFluid()
                    .getName();
                if ("steam".equals(name) || "ic2superheatedsteam".equals(name)) {
                    aFluids.add(fs);
                }
            }
        }
        return aFluids;
    }

    /**
     * @author GTSBU
     * @reason Support ic2superheatedsteam as steam source
     */
    @Overwrite
    public boolean depleteInput(FluidStack aLiquid) {
        if (aLiquid == null) return false;
        boolean isSteamRequest = aLiquid.isFluidEqual(Materials.Steam.getGas(1));
        for (MTEHatchCustomFluidBase tHatch : mSteamInputFluids) {
            if (tHatch == null || !tHatch.isValid()) continue;
            FluidStack tLiquid = tHatch.getFluid();
            if (tLiquid == null) continue;
            if (tLiquid.isFluidEqual(aLiquid)) {
                FluidStack drained = tHatch.drain(aLiquid.amount, false);
                if (drained != null && drained.amount >= aLiquid.amount) {
                    tHatch.drain(aLiquid.amount, true);
                    return true;
                }
            }
            if (isSteamRequest && "ic2superheatedsteam".equals(
                tLiquid.getFluid()
                    .getName())) {
                FluidStack drained = tHatch.drain(aLiquid.amount, false);
                if (drained != null && drained.amount >= aLiquid.amount) {
                    tHatch.drain(aLiquid.amount, true);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @author GTSBU
     * @reason Support ic2superheatedsteam speed boost
     */
    @Overwrite
    public boolean onRunningTick(ItemStack aStack) {
        MTESteamMultiBase self = gtsr$self();
        if (self.lEUt < 0) {
            long aSteamVal = ((-self.lEUt * 10000) / Math.max(1000, self.mEfficiency));
            if (gtsr$hasSuperheatedSteamInAnyHatch()) {
                aSteamVal *= 4;
                if (self.mProgresstime == 0) {
                    self.mMaxProgresstime = Math.max(1, self.mMaxProgresstime / 4);
                }
            }
            if (!self.tryConsumeSteam((int) aSteamVal)) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                return false;
            }
        }
        return true;
    }

    @Unique
    private boolean gtsr$hasSuperheatedSteamInAnyHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch == null || !hatch.isValid()) continue;
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.getFluid() != null && fs.amount > 0) {
                if ("ic2superheatedsteam".equals(
                    fs.getFluid()
                        .getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
