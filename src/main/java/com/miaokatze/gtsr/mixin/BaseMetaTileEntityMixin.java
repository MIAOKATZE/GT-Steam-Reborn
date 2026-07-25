package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.IShiftRightClickDecalcifiable;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 拦截 {@link BaseMetaTileEntity#onRightclick}，在玩家空手潜行右击控制器时，
 * 若目标 MTE 实现了 {@link IShiftRightClickDecalcifiable}，则调用其除垢逻辑并取消默认行为。
 */
@Mixin(value = BaseMetaTileEntity.class, remap = false)
public class BaseMetaTileEntityMixin {

    @Inject(method = "onRightclick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$onShiftRightClickDecalcify(EntityPlayer aPlayer, ForgeDirection side, float aX, float aY,
        float aZ, CallbackInfoReturnable<Boolean> cir) {
        BaseMetaTileEntity self = (BaseMetaTileEntity) (Object) this;

        if (!self.isServerSide()) return;
        if (aPlayer == null || !aPlayer.isSneaking()) return;

        ItemStack heldItem = aPlayer.getHeldItem();
        if (heldItem != null) return;

        IMetaTileEntity mte = self.getMetaTileEntity();
        if (!(mte instanceof IShiftRightClickDecalcifiable)) return;

        IShiftRightClickDecalcifiable decalcifiable = (IShiftRightClickDecalcifiable) mte;
        if (decalcifiable.onShiftRightClick(aPlayer, side, aX, aY, aZ)) {
            cir.setReturnValue(true);
        }
    }
}
