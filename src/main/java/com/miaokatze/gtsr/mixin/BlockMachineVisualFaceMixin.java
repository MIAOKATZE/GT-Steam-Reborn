// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** Retain the fixed surface exposed when its neighbouring display block moves away. */
@Mixin(Block.class)
public abstract class BlockMachineVisualFaceMixin {

    @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true)
    private void gtsr$fixedNeighbourFace(IBlockAccess world, int x, int y, int z, int side,
        CallbackInfoReturnable<Boolean> callback) {
        if (MachineVisualVisibility.neighbourHidden(world, x, y, z, side)) callback.setReturnValue(true);
    }
}
