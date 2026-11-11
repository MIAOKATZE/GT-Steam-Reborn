// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;

import gregtech.common.blocks.BlockMachines;

/** Guard before GT's callback can trigger the tile's own energy explosion. */
@Mixin(value = BlockMachines.class, remap = false)
public abstract class BlockMachinesCriticalProtectionMixin {

    @Inject(method = { "breakBlock", "func_149749_a" }, at = @At("HEAD"), require = 1)
    private void gtsr$criticalRemoved(World world, int x, int y, int z, Block removed, int metadata, CallbackInfo ci) {
        CriticalBlockProtection.recordRemoval(world, x, y, z, removed, metadata);
    }

    /** GT skips the Block callback when willHarvest is true and otherwise mutates covers before delegating. */
    @Inject(method = "removedByPlayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$criticalPlayerRemoval(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest,
        CallbackInfoReturnable<Boolean> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(false);
    }

    @Inject(method = "getExplosionResistance", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$criticalResistance(Entity entity, World world, int x, int y, int z, double explosionX,
        double explosionY, double explosionZ, CallbackInfoReturnable<Float> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(Float.MAX_VALUE);
    }

    @Inject(method = "onBlockExploded", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$criticalExplosion(World world, int x, int y, int z, Explosion explosion, CallbackInfo ci) {
        if (CriticalBlockProtection.protects(world, x, y, z)) ci.cancel();
    }
}
