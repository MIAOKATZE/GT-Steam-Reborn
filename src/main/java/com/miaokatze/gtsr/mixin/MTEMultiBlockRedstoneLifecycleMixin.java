package com.miaokatze.gtsr.mixin;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.common.api.compat.IGTSRRedstoneHatchCheck;
import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchCheck;
import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchLimitError;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.StructureError;

/** 把完整 checkMachine 调用作为事务边界，涵盖多个 StructureChecker 和异常退出。 */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MTEMultiBlockRedstoneLifecycleMixin implements IGTSRRedstoneHatchCheck {

    @Unique
    private GTSRRedstoneHatchCheck gtsr$redstoneHatchCheck;

    @Override
    public GTSRRedstoneHatchCheck getGTSRRedstoneHatchCheck() {
        if (gtsr$redstoneHatchCheck == null) {
            gtsr$redstoneHatchCheck = new GTSRRedstoneHatchCheck((MTEMultiBlockBase) (Object) this);
        }
        return gtsr$redstoneHatchCheck;
    }

    @Inject(method = "clearHatches", at = @At("HEAD"))
    private void gtsr$clearRedstoneHatches(CallbackInfo ci) {
        if (gtsr$redstoneHatchCheck != null) gtsr$redstoneHatchCheck.clear();
    }

    @WrapOperation(
        method = { "checkStructure", "checkMachine_TT" },
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;checkMachine(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;Lnet/minecraft/item/ItemStack;Ljava/util/List;)V"),
        require = 0)
    private void gtsr$checkRedstoneHatches(MTEMultiBlockBase controller, IGregTechTileEntity tile, ItemStack stack,
        List<StructureError> errors, Operation<Void> original) {
        GTSRRedstoneHatchCheck check = getGTSRRedstoneHatchCheck();
        check.begin();
        boolean success = false;
        try {
            original.call(controller, tile, stack, errors);
            // 即使调用方丢弃局部 piece 的错误，也不能让同一最终扫描中的第五个仓绕过全机上限。
            if (check.isOverLimit() && errors.isEmpty()) {
                errors.add(new GTSRRedstoneHatchLimitError(GTSRRedstoneHatchCheck.LIMIT + 1));
            }
            success = errors.isEmpty();
        } finally {
            check.finish(success);
        }
    }
}
