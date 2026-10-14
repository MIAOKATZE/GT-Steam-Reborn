package com.miaokatze.gtsr.mixin;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtsr.common.critical.CriticalBlockProtection;

/** 1.7.10's actual entity can-destroy explosion hook; the no-exploder path is covered by Detonate. */
@Mixin(Entity.class)
public abstract class MixinEntityCriticalProtection {

    @Inject(method = "func_145774_a", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtsr$criticalCanExplode(Explosion explosion, World world, int x, int y, int z, Block block,
        float remainingPower, CallbackInfoReturnable<Boolean> cir) {
        if (CriticalBlockProtection.protects(world, x, y, z)) cir.setReturnValue(false);
    }
}
