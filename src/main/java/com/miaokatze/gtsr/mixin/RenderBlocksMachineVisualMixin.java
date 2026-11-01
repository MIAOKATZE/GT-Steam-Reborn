package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.client.machinevisual.MachineVisualVisibility;

/** GT casing and frame ISBRHs both enter here, including Angelica's tessellated block path. */
@Mixin(RenderBlocks.class)
public abstract class RenderBlocksMachineVisualMixin {

    @Shadow
    public IBlockAccess blockAccess;

    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void gtsr$machineProxy(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> callback) {
        if (MachineVisualVisibility.hidden(blockAccess, block, x, y, z)) callback.setReturnValue(false);
    }
}
