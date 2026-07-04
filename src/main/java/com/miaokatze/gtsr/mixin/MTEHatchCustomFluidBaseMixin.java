package com.miaokatze.gtsr.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.IAutoInputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;

@Mixin(value = MTEHatchCustomFluidBase.class, remap = false)
public abstract class MTEHatchCustomFluidBaseMixin implements IAutoInputHatch {

    @Shadow(remap = false)
    public Fluid mLockedFluid;

    @Unique
    private boolean gtsr$autoInput = false;

    @Inject(method = "isFluidInputAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$allowAllSteamPipeInput(FluidStack aFluid, CallbackInfoReturnable<Boolean> cir) {
        if (mLockedFluid != null && gtsr$isSteamType(mLockedFluid.getName())) {
            if (aFluid != null && aFluid.getFluid() != null
                && gtsr$isSteamType(
                    aFluid.getFluid()
                        .getName())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$saveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setBoolean("gtsr$autoInput", gtsr$autoInput);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$loadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        gtsr$autoInput = aNBT.getBoolean("gtsr$autoInput");
    }

    @Override
    public boolean gtsr$isAutoInput() {
        return gtsr$autoInput;
    }

    @Override
    public void gtsr$setAutoInput(boolean value) {
        gtsr$autoInput = value;
    }

    @Override
    public void gtsr$doAutoInput(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isAllowedToWork()) return;
        // 修复Bug1：加入tick节流，5秒一次（与物品总线节奏一致）
        if (aTick % 100 != 0) return;

        MTEHatchCustomFluidBase self = (MTEHatchCustomFluidBase) (Object) this;

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        IFluidHandler tTileEntity = aBaseMetaTileEntity.getITankContainerAtSide(front);
        if (tTileEntity == null) return;

        // 修复Bug2：采用GT5U标准两阶段模拟-实抽模式（参考GTUtility.moveFluid）
        // 节流方案A：100tick节流 + 1000mB/次，平均200mB/s
        final int TRANSFER_AMOUNT = 1000;
        GTUtility.moveFluid(tTileEntity, self, front.getOpposite(), front, TRANSFER_AMOUNT, null);
    }

    @Unique
    private boolean gtsr$isSteamType(String fluidName) {
        return "steam".equals(fluidName) || "ic2steam".equals(fluidName) || "ic2superheatedsteam".equals(fluidName);
    }
}
