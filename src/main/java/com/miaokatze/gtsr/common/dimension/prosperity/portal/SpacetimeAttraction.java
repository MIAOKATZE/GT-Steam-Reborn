package com.miaokatze.gtsr.common.dimension.prosperity.portal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;

/** Server motion only, bounded work, collision checked, and no persistent flight capabilities. */
public final class SpacetimeAttraction {

    private final Map<Entity, Integer> stages = new HashMap<>();
    private final Map<EntityPlayerMP, Contact> contacts = new HashMap<>();
    private World attractionWorld;

    private static final class Contact {

        final CoreTransferGate gate = new CoreTransferGate();
        final double x, y, z;

        Contact(EntityPlayerMP player) {
            x = player.posX;
            y = player.posY;
            z = player.posZ;
        }
    }

    public void tick(MTESpacetimeCalibration machine, Vec3 centre) {
        if (!machine.isPortalActive()) {
            releaseAll();
            return;
        }
        attractionWorld = machine.getBaseMetaTileEntity()
            .getWorld();
        @SuppressWarnings("unchecked")
        List<Entity> nearby = machine.getBaseMetaTileEntity()
            .getWorld()
            .getEntitiesWithinAABB(
                Entity.class,
                AxisAlignedBB.getBoundingBox(
                    centre.xCoord - 32,
                    centre.yCoord - 32,
                    centre.zCoord - 32,
                    centre.xCoord + 32,
                    centre.yCoord + 32,
                    centre.zCoord + 32));
        nearby = new ArrayList<>(nearby);
        nearby.sort(
            Comparator.comparingInt((Entity e) -> e instanceof EntityPlayer ? 0 : 1)
                .thenComparingDouble(e -> e.getDistanceSq(centre.xCoord, centre.yCoord, centre.zCoord)));
        Set<Entity> retained = new HashSet<>();
        int processed = 0;
        long now = machine.getBaseMetaTileEntity()
            .getWorld()
            .getTotalWorldTime();
        for (Entity entity : nearby) {
            if (processed >= 64) break;
            if (entity.isDead || entity.worldObj != machine.getBaseMetaTileEntity()
                .getWorld()
                || entity.ridingEntity != null
                || entity.riddenByEntity != null
                || entity.isSneaking()
                || (entity instanceof EntityPlayerMP && !SpacetimeTravel.eligible((EntityPlayerMP) entity))
                || !intersectsSphere(entity.boundingBox, centre)) continue;
            if (entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isCreativeMode
                && ((EntityPlayer) entity).capabilities.isFlying) continue;
            if (entity.getEntityData()
                .getLong("gtsrSpacetimeCooldownUntil") > now) continue;
            Vec3 local = machine.worldToLocal(entity.posX, entity.posY, entity.posZ);
            // The front entry corridor intersects the spherical range; arbitrary side/back entities stay untouched.
            double halfWidth = entity.width / 2.0;
            if (Math.abs(local.xCoord - 23.5) > 4 + halfWidth || local.zCoord + halfWidth < 3.5
                || local.zCoord - halfWidth > 17
                || local.yCoord + entity.height < 1
                || local.yCoord > 26) continue;
            processed++;
            if (entity instanceof EntityPlayerMP && contacts.containsKey(entity)) {
                Contact contact = contacts.get(entity);
                AxisAlignedBB heldBox = entity.boundingBox.copy()
                    .offset(contact.x - entity.posX, contact.y - entity.posY, contact.z - entity.posZ);
                if (!entity.worldObj.getCollidingBoundingBoxes(entity, heldBox)
                    .isEmpty()) continue;
                retained.add(entity);
                if (!machine.validatePortalForTravel()) return;
                transferAtCore((EntityPlayerMP) entity, now, retained);
                continue;
            }
            int stage = stages.getOrDefault(entity, local.yCoord >= 12 ? 2 : 0);
            double tx = 23.5, ty, tz;
            if (stage == 0) {
                ty = local.yCoord;
                tz = 13;
                if (Math.abs(local.xCoord - tx) < .25 && Math.abs(local.zCoord - tz) < .25) stage = 1;
            } else {
                ty = 12;
                tz = 13;
            }
            if (stage == 1) {
                ty = 12;
                tz = 13;
                if (local.yCoord >= 11.9) stage = 2;
            }
            if (stage == 2) {
                ty = 24.5;
                tz = 4.5;
            }
            Vec3 target = machine.localToWorld(tx, ty, tz);
            double dx = target.xCoord - entity.posX, dz = target.zCoord - entity.posZ;
            double length = Math.hypot(dx, dz), scale = length > .16 ? .16 / length : 1;
            dx *= scale;
            dz *= scale;
            double dy = Math.max(-.22, Math.min(.22, target.yCoord - entity.posY));
            AxisAlignedBB actual = entity.boundingBox.copy()
                .offset(dx, dy, dz);
            AxisAlignedBB playerClearance = AxisAlignedBB.getBoundingBox(
                entity.posX + dx - .3,
                entity.posY + dy,
                entity.posZ + dz - .3,
                entity.posX + dx + .3,
                entity.posY + dy + 1.8,
                entity.posZ + dz + .3);
            if (!entity.worldObj.getCollidingBoundingBoxes(entity, actual)
                .isEmpty()
                || !entity.worldObj.getCollidingBoundingBoxes(entity, playerClearance)
                    .isEmpty())
                continue;
            // Swept box also catches a thin obstacle between the old and new boxes.
            if (!entity.worldObj.getCollidingBoundingBoxes(entity, entity.boundingBox.addCoord(dx, dy, dz))
                .isEmpty()) continue;
            retained.add(entity);
            stages.put(entity, stage);
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (!player.capabilities.isCreativeMode && player.capabilities.isFlying) {
                    player.capabilities.isFlying = false;
                    player.sendPlayerAbilities();
                }
            }
            entity.motionX = dx;
            entity.motionY = dy;
            entity.motionZ = dz;
            entity.fallDistance = 0;
            entity.velocityChanged = true;
            // Contact with the small core boundary, never teleport merely for entering the attraction sphere.
            AxisAlignedBB core = AxisAlignedBB.getBoundingBox(
                centre.xCoord - .55,
                centre.yCoord - .55,
                centre.zCoord - .55,
                centre.xCoord + .55,
                centre.yCoord + .55,
                centre.zCoord + .55);
            if (entity instanceof EntityPlayerMP && entity.boundingBox.intersectsWith(core)) {
                // This may release and clear all tracked entities; stop this tick instead of continuing on stale state.
                if (!machine.validatePortalForTravel()) return;
                EntityPlayerMP player = (EntityPlayerMP) entity;
                contacts.put(player, new Contact(player));
                transferAtCore(player, now, retained);
            }
        }
        stages.keySet()
            .removeIf(entity -> {
                if (retained.contains(entity)) return false;
                release(entity);
                contacts.remove(entity);
                return true;
            });
    }

    private void transferAtCore(EntityPlayerMP player, long now, Set<Entity> retained) {
        Contact contact = contacts.get(player);
        // Pin to the collision-checked contact position while synchronous discovery/transfer retries.
        player.playerNetServerHandler
            .setPlayerLocation(contact.x, contact.y, contact.z, player.rotationYaw, player.rotationPitch);
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        player.velocityChanged = true;
        contact.gate
            .attempt(now, () -> SpacetimeTravel.enterProsperity(player, contact.gate.reportFailure(now)), () -> {
                player.getEntityData()
                    .setLong("gtsrSpacetimeCooldownUntil", player.worldObj.getTotalWorldTime() + 60);
                contacts.remove(player);
                stages.remove(player);
                retained.remove(player);
            });
    }

    public void releaseAll() {
        for (Entity entity : stages.keySet()) release(entity);
        stages.clear();
        contacts.clear();
    }

    private static boolean intersectsSphere(AxisAlignedBB box, Vec3 centre) {
        double dx = Math.max(box.minX - centre.xCoord, Math.max(0, centre.xCoord - box.maxX));
        double dy = Math.max(box.minY - centre.yCoord, Math.max(0, centre.yCoord - box.maxY));
        double dz = Math.max(box.minZ - centre.zCoord, Math.max(0, centre.zCoord - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= 1024;
    }

    private void release(Entity entity) {
        // A completed trip belongs to its destination; retiring a source lease must not alter it.
        if (entity.worldObj != attractionWorld) return;
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        entity.fallDistance = 0;
        entity.velocityChanged = true;
        if (entity instanceof EntityPlayer && entity.worldObj != null) entity.getEntityData()
            .setLong("gtsrSpacetimeCooldownUntil", entity.worldObj.getTotalWorldTime() + 60);
    }
}
