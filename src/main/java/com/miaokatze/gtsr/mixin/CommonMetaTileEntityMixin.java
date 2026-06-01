package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.IAutoInputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.CommonMetaTileEntity;
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = CommonMetaTileEntity.class, remap = false)
public abstract class CommonMetaTileEntityMixin {

    @Inject(method = "onPostTick", at = @At("TAIL"), remap = false)
    private void gtsr$onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        CommonMetaTileEntity self = (CommonMetaTileEntity) (Object) this;

        if (self instanceof IAutoInputHatch) {
            IAutoInputHatch hatch = (IAutoInputHatch) self;
            if (hatch.gtsr$isAutoInput()) {
                hatch.gtsr$doAutoInput(aBaseMetaTileEntity, aTick);
            }
        }
    }

    @Inject(method = "onScrewdriverRightClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool, CallbackInfoReturnable<Boolean> cir) {
        CommonMetaTileEntity self = (CommonMetaTileEntity) (Object) this;

        if (self instanceof MTEHatchCustomFluidBase) {
            if (!self.getBaseMetaTileEntity()
                .getCoverAtSide(side)
                .isGUIClickable()) return;

            IAutoInputHatch hatch = (IAutoInputHatch) self;
            hatch.gtsr$setAutoInput(!hatch.gtsr$isAutoInput());
            cir.setReturnValue(true);

            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.hatch.auto_input")
                    + " "
                    + (hatch.gtsr$isAutoInput()
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
        }
    }
}
