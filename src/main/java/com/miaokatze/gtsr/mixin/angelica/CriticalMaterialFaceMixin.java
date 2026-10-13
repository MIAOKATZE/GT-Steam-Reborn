package com.miaokatze.gtsr.mixin.angelica;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtsr.common.critical.CriticalMaterials;

/** Retain vanilla neighbor culling instead of NotFine's optional extra obstruction checks. */
@Mixin(CriticalMaterials.MaterialBlock.class)
public abstract class CriticalMaterialFaceMixin {

    @Redirect(
        method = "shouldSideBeRendered",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z"),
        require = 1)
    private boolean gtsr$vanillaFaces(Block block, IBlockAccess world, int x, int y, int z, int side) {
        return side == 0 && block.getBlockBoundsMinY() > 0 || side == 1 && block.getBlockBoundsMaxY() < 1
            || side == 2 && block.getBlockBoundsMinZ() > 0
            || side == 3 && block.getBlockBoundsMaxZ() < 1
            || side == 4 && block.getBlockBoundsMinX() > 0
            || side == 5 && block.getBlockBoundsMaxX() < 1
            || !world.getBlock(x, y, z)
                .isOpaqueCube();
    }
}
