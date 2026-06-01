package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.CommonMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = CommonMetaTileEntity.class, remap = false)
public abstract class CommonMetaTileEntityMixin {

    @Inject(method = "onPostTick", at = @At("TAIL"), remap = false)
    private void gtsr$onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        CommonMetaTileEntity self = (CommonMetaTileEntity) (Object) this;

        if (self instanceof MTEHatchInput && !(self instanceof MTEHatchCustomFluidBase)) {
            MTEHatchInput hatch = (MTEHatchInput) self;
            if (((MTEHatchInputMixin) (Object) hatch).gtsr$isAutoInput()) {
                ((MTEHatchInputMixin) (Object) hatch).gtsr$doAutoInput(aBaseMetaTileEntity);
            }
        } else if (self instanceof MTEHatchCustomFluidBase) {
            MTEHatchCustomFluidBase hatch = (MTEHatchCustomFluidBase) self;
            if (((MTEHatchCustomFluidBaseMixin) (Object) hatch).gtsr$isAutoInput()) {
                gtsr$doFluidAutoInput(hatch, aBaseMetaTileEntity);
            }
        } else if (self instanceof MTEHatchInputBus) {
            MTEHatchInputBus bus = (MTEHatchInputBus) self;
            if (((MTEHatchInputBusMixin) (Object) bus).gtsr$isAutoInput()) {
                ((MTEHatchInputBusMixin) (Object) bus).gtsr$doAutoInput(aBaseMetaTileEntity, aTick);
            }
        }
    }

    @Inject(method = "onScrewdriverRightClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool, CallbackInfoReturnable<Boolean> cir) {
        CommonMetaTileEntity self = (CommonMetaTileEntity) (Object) this;

        if (self instanceof MTEHatchCustomFluidBase) {
            MTEHatchCustomFluidBase hatch = (MTEHatchCustomFluidBase) self;
            if (!hatch.getBaseMetaTileEntity()
                .getCoverAtSide(side)
                .isGUIClickable()) return;

            MTEHatchCustomFluidBaseMixin mixin = (MTEHatchCustomFluidBaseMixin) (Object) hatch;
            mixin.gtsr$setAutoInput(!mixin.gtsr$isAutoInput());
            cir.setReturnValue(true);

            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.hatch.auto_input")
                    + " "
                    + (mixin.gtsr$isAutoInput()
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
        }
    }

    private void gtsr$doFluidAutoInput(MTEHatchCustomFluidBase hatch, IGregTechTileEntity aBaseMetaTileEntity) {
        if (!aBaseMetaTileEntity.isAllowedToWork()) return;

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        IFluidHandler tTileEntity = aBaseMetaTileEntity.getITankContainerAtSide(front);
        if (tTileEntity == null) return;

        FluidStack drained = tTileEntity.drain(front.getOpposite(), 100, false);
        if (drained == null) return;

        int filled = hatch.fill(front, drained, true);
        if (filled > 0) {
            tTileEntity.drain(front.getOpposite(), filled, true);
        }
    }
}
