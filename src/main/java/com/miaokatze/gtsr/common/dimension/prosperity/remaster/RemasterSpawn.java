package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Server spawn admission: no platforms, unloaded-space guesses or quota before successful admission. */
public final class RemasterSpawn {

    private RemasterSpawn() {}

    public static EntityOldEcho spawn(World world, EchoKind kind, String owner, double x, double y, double z, int index,
        boolean objectiveGate) {
        if (world == null || world.isRemote || kind == null) return null;
        EntityOldEcho entity = new EntityOldEcho(world);
        int attempts = kind.flies() ? 1 : 33;
        for (int step = 0; step < attempts; step++) {
            double candidateY = kind.flies() ? y : Math.floor(y) - step;
            entity.initializeEcho(kind, owner, x, candidateY, z, false);
            entity.setNodeIndex(index);
            if (!safe(world, entity, !kind.flies())) continue;
            if (objectiveGate) entity.configureObjective(2, -1, true);
            if (world.spawnEntityInWorld(entity)) return entity;
            return null;
        }
        return null;
    }

    public static boolean safe(World world, Entity entity, boolean support) {
        AxisAlignedBB box = entity.boundingBox;
        if (box == null || box.minY < 1 || box.maxY >= world.getActualHeight()) return false;
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX - .0001);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY - .0001);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ - .0001);
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            for (int by = minY - 1; by <= maxY; by++) {
                if (!world.blockExists(bx, by, bz)) return false;
                if (by >= minY && world.getBlock(bx, by, bz)
                    .getMaterial()
                    .isLiquid()) return false;
            }
            if (support && !world.isSideSolid(bx, minY - 1, bz, ForgeDirection.UP)) return false;
        }
        return !world.isAnyLiquid(box) && world.checkNoEntityCollision(box)
            && world.getCollidingBoundingBoxes(entity, box)
                .isEmpty()
            && world.getEntitiesWithinAABBExcludingEntity(entity, box)
                .isEmpty();
    }
}
