package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Server spawn admission: no platforms, unloaded-space guesses or quota before successful admission. */
public final class RemasterSpawn {

    private RemasterSpawn() {}

    public static EntityOldEcho spawn(World world, EchoKind kind, String owner, double x, double y, double z, int index,
        boolean objectiveGate) {
        if (world == null || world.isRemote || kind == null) return null;
        EntityOldEcho entity = new EntityOldEcho(world);
        // Keep authored floor ownership: a missing support never relocates an industrial guard to another level.
        entity.initializeEcho(kind, owner, x, kind.flies() ? y : Math.floor(y), z, false);
        entity.setNodeIndex(index);
        if (!safe(world, entity, !kind.flies())) return null;
        if (objectiveGate) entity.configureObjective(2, -1, true);
        if (world.spawnEntityInWorld(entity)) return entity;
        return null;
    }

    public static boolean safe(World world, Entity entity, boolean support) {
        AxisAlignedBB box = entity.boundingBox;
        if (box == null || box.minY < 1 || box.maxY >= world.getActualHeight()) return false;
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX - .0001);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY - .0001);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ - .0001);
        boolean supported = !support;
        AxisAlignedBB feet = AxisAlignedBB
            .getBoundingBox(box.minX, box.minY - .0001, box.minZ, box.maxX, box.minY + .0001, box.maxZ);
        List<AxisAlignedBB> floorParts = new ArrayList<>();
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            for (int by = minY - 1; by <= maxY; by++) {
                if (!world.blockExists(bx, by, bz)) return false;
                if (by >= minY && world.getBlock(bx, by, bz)
                    .getMaterial()
                    .isLiquid()) return false;
            }
            if (support) {
                floorParts.clear();
                world.getBlock(bx, minY - 1, bz)
                    .addCollisionBoxesToList(world, bx, minY - 1, bz, feet, floorParts, entity);
                // Like vanilla movement, a wide body may bridge a lower grate or gap. Only blocks whose
                // actual collision surface touches the authored feet count; entities never supply support.
                for (AxisAlignedBB floor : floorParts)
                    if (Math.abs(floor.maxY - box.minY) <= .0001 && floor.maxX > box.minX
                        && floor.minX < box.maxX
                        && floor.maxZ > box.minZ
                        && floor.minZ < box.maxZ) supported = true;
            }
        }
        return supported && !world.isAnyLiquid(box)
            && world.checkNoEntityCollision(box)
            && world.getCollidingBoundingBoxes(entity, box)
                .isEmpty()
            && world.getEntitiesWithinAABBExcludingEntity(entity, box)
                .isEmpty();
    }
}
