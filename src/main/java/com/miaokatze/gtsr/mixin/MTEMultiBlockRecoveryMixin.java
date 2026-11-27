// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery;
import com.miaokatze.gtsr.common.machine.recovery.IGtsrStructureRecovery;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.PositionedStructureError;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MTEMultiBlockRecoveryMixin implements IGtsrStructureRecovery {

    @Unique
    private final GtsrStructureRecovery.State gtsr$recovery = new GtsrStructureRecovery.State();

    @Override
    public GtsrStructureRecovery.State gtsr$getStructureRecovery() {
        return gtsr$recovery;
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"))
    private void gtsr$loadRecovery(NBTTagCompound nbt, CallbackInfo ci) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (GtsrStructureRecovery.applies(self)) gtsr$recovery.load(self, nbt);
    }

    @Inject(method = "saveNBTData", at = @At("RETURN"))
    private void gtsr$saveRecovery(NBTTagCompound nbt, CallbackInfo ci) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (GtsrStructureRecovery.applies(self)) gtsr$recovery.save(self, nbt);
    }

    @Inject(method = "checkStructure", at = @At("HEAD"))
    private void gtsr$beginCheck(boolean force, IGregTechTileEntity tile, CallbackInfoReturnable<Boolean> cir) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (!GtsrStructureRecovery.applies(self)) return;
        if (force || self.mStructureChanged) {
            gtsr$recovery.beginCheck();
        }
    }

    @WrapOperation(
        method = "checkStructure",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;checkMachine(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;Lnet/minecraft/item/ItemStack;Ljava/util/List;)V"))
    private void gtsr$unknownIsNotFormed(MTEMultiBlockBase machine, IGregTechTileEntity tile, ItemStack stack,
        List<StructureError> errors, Operation<Void> original) {
        if (GtsrStructureRecovery.applies(machine)) gtsr$recovery.checkErrors = errors;
        try {
            original.call(machine, tile, stack, errors);
        } finally {
            gtsr$recovery.checkErrors = null;
        }
        if (GtsrStructureRecovery.applies(machine) && gtsr$recovery.unknown
            && errors.stream()
                .anyMatch(error -> error instanceof PositionedStructureError))
            GtsrStructureRecovery.confirmedDamage(machine);
        if (GtsrStructureRecovery.applies(machine) && gtsr$recovery.unknown
            && !errors.contains(StructureErrorRegistry.BLOCK_NOT_LOADED))
            errors.add(StructureErrorRegistry.BLOCK_NOT_LOADED);
    }

    @Inject(method = "checkStructure", at = @At("RETURN"), cancellable = true)
    private void gtsr$endCheck(boolean force, IGregTechTileEntity tile, CallbackInfoReturnable<Boolean> cir) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (!GtsrStructureRecovery.applies(self) || !gtsr$recovery.checking) return;
        gtsr$recovery.checking = false;
        if (gtsr$recovery.unknown) {
            self.mMachine = false;
            cir.setReturnValue(false);
            if (self.mUpdate <= 0) self.mUpdate = 20;
        } else if (!cir.getReturnValueZ()) GtsrStructureRecovery.confirmedDamage(self);
        else if (gtsr$recovery.active) {
            gtsr$recovery.active = false;
            gtsr$recovery.phase = GtsrStructureRecovery.State.VERIFIED;
            self.mStartUpCheck = -1;
        } else if (cir.getReturnValueZ()) gtsr$recovery.phase = GtsrStructureRecovery.State.VERIFIED;
    }

    @Inject(method = "stopMachine(Lgregtech/api/util/shutdown/ShutDownReason;)V", at = @At("HEAD"), cancellable = true)
    private void gtsr$pauseUnknownStructure(ShutDownReason reason, CallbackInfo ci) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (reason != ShutDownReasonRegistry.STRUCTURE_INCOMPLETE
            || !GtsrStructureRecovery.isUnknownStructurePause(self)) return;
        // Only paid work is retained after expiry; native efficiency loses its reload protection.
        if (!GtsrStructureRecovery.isRecovering(self)) self.mEfficiency = 0;
        IGregTechTileEntity tile = self.getBaseMetaTileEntity();
        if (tile != null) tile.setActive(false);
        ci.cancel();
    }

    @Inject(method = "onPostTick", at = @At("RETURN"))
    private void gtsr$finishTick(IGregTechTileEntity tile, long tick, CallbackInfo ci) {
        MTEMultiBlockBase self = (MTEMultiBlockBase) (Object) this;
        if (!GtsrStructureRecovery.applies(self) || !tile.isServerSide()) return;
        if (self.mStartUpCheck < 0 && gtsr$recovery.active) {
            gtsr$recovery.active = false;
            gtsr$recovery.phase = GtsrStructureRecovery.State.EXPIRED;
        }
        if (GtsrStructureRecovery.isRecovering(self) || GtsrStructureRecovery.isUnknownStructurePause(self))
            tile.setActive(false);
    }
}
