// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.miaokatze.gtsr.loader.recipes.RockBreakerCatalysts;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.SingleRecipeCheck;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class IndustrialNetherrackLockMixin {

    @Shadow
    protected SingleRecipeCheck mSingleRecipeCheck;

    @Unique
    private SingleRecipeCheck gtsr$pendingNetherrackLock;

    @ModifyReturnValue(method = "loadSingleRecipeChecker", at = @At("RETURN"), require = 1)
    private SingleRecipeCheck gtsr$restoreNetherrackLock(SingleRecipeCheck loaded) {
        gtsr$pendingNetherrackLock = null;
        if (!RockBreakerCatalysts.isNetherrackLock(loaded)) return loaded;
        return gtsr$resolveInWorld(loaded);
    }

    @Unique
    private SingleRecipeCheck gtsr$resolveInWorld(SingleRecipeCheck lock) {
        var tile = ((MTEMultiBlockBase) (Object) this).getBaseMetaTileEntity();
        World world = tile == null ? null : tile.getWorld();
        if (world == null || world.provider == null) {
            // Chunk deserialization may run before the tile receives its world.
            gtsr$pendingNetherrackLock = lock;
            return lock;
        }
        gtsr$pendingNetherrackLock = null;
        return RockBreakerCatalysts.restoreNetherrackLock(lock, world);
    }

    @ModifyReturnValue(method = "getSingleRecipeCheck", at = @At("RETURN"), require = 1)
    private SingleRecipeCheck gtsr$finishNetherrackRestore(SingleRecipeCheck current) {
        if (gtsr$pendingNetherrackLock == null) return current;
        if (current == gtsr$pendingNetherrackLock) {
            mSingleRecipeCheck = gtsr$resolveInWorld(current);
            return mSingleRecipeCheck;
        }
        gtsr$pendingNetherrackLock = null;
        return current;
    }
}
