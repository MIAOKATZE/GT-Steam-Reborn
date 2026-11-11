// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;

/** GT machines inherit this getter; changing the shared Block's hardness would lock every GT machine. */
@Mixin(Block.class)
public abstract class MixinBlockCriticalProtection {

    @Inject(method = "breakBlock", at = @At("HEAD"), require = 1)
    private void gtsr$criticalRemoved(World world, int x, int y, int z, Block removed, int metadata, CallbackInfo ci) {
        CriticalBlockProtection.recordRemoval(world, x, y, z, removed, metadata);
    }

    @Inject(method = "getBlockHardness", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$criticalHardness(World world, int x, int y, int z, CallbackInfoReturnable<Float> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(-1F);
    }

    /** Forge's willHarvest overload delegates here; direct tool removal does not always check hardness first. */
    @Inject(
        method = "removedByPlayer(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;III)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 1)
    private void gtsr$criticalPlayerRemoval(World world, EntityPlayer player, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(false);
    }
}
