package com.miaokatze.gtsr.mixin.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.compat.gtnl.GTNLSteamPayment;

import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

/** Preserves upstream processing results and locks boost mode for the entire recipe. */
@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase", remap = false)
public abstract class SteamMultiMachineBaseGTNLMixin {

    @Shadow
    public abstract int depleteInputQuantity(FluidStack request, boolean simulate);

    @Unique
    private boolean gtsr$boostedRecipe;
    @Unique
    private int gtsr$nativeDuration;
    @Unique
    private boolean gtsr$resumedRecipe;

    @Inject(method = "onRunningTick(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void gtsr$onRunningTickHead(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        MTESteamMultiBlockBase<?> self = (MTESteamMultiBlockBase<?>) (Object) this;
        if (self.lEUt < 0) {
            if (self.lEUt == Long.MIN_VALUE || -self.lEUt > Long.MAX_VALUE / 10000) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                cir.setReturnValue(false);
                return;
            }
            long energy = (-self.lEUt * 10000) / Math.max(1000, self.mEfficiency);
            List<GTNLSteamPayment.Fuel> fuels = GTNLSteamPayment.nativeFuels();
            boolean firstTick = self.mProgresstime == 0 && !gtsr$resumedRecipe;
            gtsr$resumedRecipe = false;
            if (firstTick) gtsr$boostedRecipe = GTNLSteamPayment.canBoost(fuels, energy, this::depleteInputQuantity);
            GTNLSteamPayment.Receipt receipt = GTNLSteamPayment
                .pay(fuels, energy, gtsr$boostedRecipe, this::depleteInputQuantity);
            if (receipt == null) {
                self.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                cir.setReturnValue(false);
                return;
            }
            if (firstTick && gtsr$boostedRecipe) {
                gtsr$nativeDuration = self.mMaxProgresstime;
                self.mMaxProgresstime = Math.max(1, self.mMaxProgresstime / 4);
            }
            GTNLSteamPayment.cool((ICoolingHatchHolder) this, receipt);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void gtsr$saveMode(NBTTagCompound nbt, CallbackInfo ci) {
        MTESteamMultiBlockBase<?> self = (MTESteamMultiBlockBase<?>) (Object) this;
        boolean activeBoost = gtsr$boostedRecipe && self.mMaxProgresstime > 0
            && (self.mProgresstime > 0 || gtsr$resumedRecipe);
        nbt.setBoolean("gtsr.gtnlBoostedRecipe", activeBoost);
        if (activeBoost) {
            // Standard keys always use native units, including when enhancement is disabled on the next launch.
            nbt.setInteger("mMaxProgresstime", gtsr$nativeDuration);
            nbt.setInteger("mProgresstime", (int) Math.min(gtsr$nativeDuration - 1L, self.mProgresstime * 4L));
        }
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void gtsr$loadMode(NBTTagCompound nbt, CallbackInfo ci) {
        gtsr$boostedRecipe = nbt.getBoolean("gtsr.gtnlBoostedRecipe");
        MTESteamMultiBlockBase<?> self = (MTESteamMultiBlockBase<?>) (Object) this;
        if (gtsr$boostedRecipe && self.mMaxProgresstime > 0) {
            gtsr$nativeDuration = self.mMaxProgresstime;
            self.mMaxProgresstime = Math.max(1, gtsr$nativeDuration / 4);
            self.mProgresstime /= 4;
            gtsr$resumedRecipe = true;
        }
    }
}
