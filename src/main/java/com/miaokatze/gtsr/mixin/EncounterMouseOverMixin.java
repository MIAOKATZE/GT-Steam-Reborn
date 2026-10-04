package com.miaokatze.gtsr.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterHitboxes;

@Mixin(EntityRenderer.class)
public abstract class EncounterMouseOverMixin {

    @Redirect(
        method = "getMouseOver",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;getEntitiesWithinAABBExcludingEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;"),
        require = 1)
    private List gtsr$attackCandidates(WorldClient world, Entity observer, AxisAlignedBB search) {
        List result = new ArrayList(world.getEntitiesWithinAABBExcludingEntity(observer, search));
        for (Object value : world.loadedEntityList) {
            Entity entity = (Entity) value;
            if (entity != observer && !entity.isDead
                && EncounterHitboxes.enlarged(entity)
                && EncounterHitboxes.attackBounds(entity)
                    .expand(1, 1, 1)
                    .intersectsWith(search)
                && !result.contains(entity)) result.add(entity);
        }
        return result;
    }

    @Redirect(
        method = "getMouseOver",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/entity/Entity;boundingBox:Lnet/minecraft/util/AxisAlignedBB;",
            opcode = 180),
        require = 1)
    private AxisAlignedBB gtsr$attackBounds(Entity entity) {
        return EncounterHitboxes.attackBounds(entity);
    }
}
