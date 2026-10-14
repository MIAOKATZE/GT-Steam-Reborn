package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;

/** GT machines inherit this getter; changing the shared Block's hardness would lock every GT machine. */
@Mixin(Block.class)
public abstract class MixinBlockCriticalProtection {

    @Inject(method = "getBlockHardness", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtsr$criticalHardness(World world, int x, int y, int z, CallbackInfoReturnable<Float> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(-1F);
    }
}
