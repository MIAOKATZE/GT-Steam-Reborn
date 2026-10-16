package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.Entity;
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
