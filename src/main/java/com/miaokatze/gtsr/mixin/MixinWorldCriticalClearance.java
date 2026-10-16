package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalRuntime;

/** Tracks natural and player block changes only while a sparse first-start scan is pending. */
@Mixin(World.class)
public abstract class MixinWorldCriticalClearance {

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
