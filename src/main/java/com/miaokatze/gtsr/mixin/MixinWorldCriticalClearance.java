// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;
import com.miaokatze.gtsr.common.critical.CriticalRuntime;

/** Tracks natural and player block changes only while a sparse first-start scan is pending. */
@Mixin(World.class)
public abstract class MixinWorldCriticalClearance {

    /** Metadata-only edits do not invoke breakBlock. Preserve their previous material identity until success. */
    @WrapOperation(
        method = "setBlockMetadataWithNotify",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;setBlockMetadata(IIII)Z"),
        require = 1)
    private boolean gtsr$criticalMetadataRemoval(Chunk chunk, int x, int y, int z, int metadata,
        Operation<Boolean> original) {
        Block previous = chunk.getBlock(x, y, z);
        int previousMetadata = chunk.getBlockMetadata(x, y, z);
        boolean changed = original.call(chunk, x, y, z, metadata);
        if (changed) CriticalBlockProtection.recordRemoval(
            (World) (Object) this,
            (chunk.xPosition << 4) + x,
            y,
            (chunk.zPosition << 4) + z,
            previous,
            previousMetadata);
        return changed;
    }

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("RETURN"), require = 1)
    private void gtsr$placed(int x, int y, int z, Block block, int metadata, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) CriticalRuntime.changedDuringClearance((World) (Object) this, x, y, z);
    }

    @Inject(method = "setBlockMetadataWithNotify", at = @At("RETURN"), require = 1)
    private void gtsr$metadata(int x, int y, int z, int metadata, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) CriticalRuntime.changedDuringClearance((World) (Object) this, x, y, z);
    }
}
