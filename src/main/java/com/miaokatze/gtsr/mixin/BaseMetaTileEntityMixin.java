package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.api.IShiftRightClickDecalcifiable;
import com.miaokatze.gtsr.common.terminal.MachineTerminalHistory;
import com.miaokatze.gtsr.common.terminal.MachineTerminalIdentity;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 拦截 {@link BaseMetaTileEntity#onRightclick}，在玩家空手潜行右击控制器时，
 * 若目标 MTE 实现了 {@link IShiftRightClickDecalcifiable}，则调用其除垢逻辑并取消默认行为。
 */
@Mixin(value = BaseMetaTileEntity.class, remap = false)
public class BaseMetaTileEntityMixin implements MachineTerminalIdentity {

    @Unique
    private MachineTerminalIdentity.State gtsr$terminalIdentity = new MachineTerminalIdentity.State();
    @Unique
    private Container gtsr$containerBeforeInteraction;

    @Override
    public String gtsr$terminalTargetIdentity() {
        boolean initialized = gtsr$terminalIdentity.initialized();
        String identity = gtsr$terminalIdentity.identity();
        if (!initialized) ((BaseMetaTileEntity) (Object) this).markDirty();
        return identity;
    }

    @Inject(method = { "writeToNBT", "func_145841_b" }, at = @At("RETURN"), remap = false, require = 1)
    private void gtsr$saveTerminalIdentity(NBTTagCompound tag, CallbackInfo ci) {
        gtsr$terminalIdentity.write(tag);
    }

    @Inject(method = { "readFromNBT", "func_145839_a" }, at = @At("RETURN"), remap = false, require = 1)
    private void gtsr$loadTerminalIdentity(NBTTagCompound tag, CallbackInfo ci) {
        gtsr$terminalIdentity.read(tag);
    }

    @Inject(method = "onRightclick", at = @At("RETURN"), remap = false)
    private void gtsr$recordOpenedMachine(EntityPlayer player, ForgeDirection side, float x, float y, float z,
        CallbackInfoReturnable<Boolean> cir) {
        BaseMetaTileEntity self = (BaseMetaTileEntity) (Object) this;
        if (self.isServerSide() && player != null)
            MachineTerminalHistory.recordOpened(player, self, gtsr$containerBeforeInteraction);
        gtsr$containerBeforeInteraction = null;
    }

    @Inject(method = "onRightclick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtsr$onShiftRightClickDecalcify(EntityPlayer aPlayer, ForgeDirection side, float aX, float aY,
        float aZ, CallbackInfoReturnable<Boolean> cir) {
        BaseMetaTileEntity self = (BaseMetaTileEntity) (Object) this;

        if (!self.isServerSide()) return;
        gtsr$containerBeforeInteraction = aPlayer == null ? null : aPlayer.openContainer;
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
