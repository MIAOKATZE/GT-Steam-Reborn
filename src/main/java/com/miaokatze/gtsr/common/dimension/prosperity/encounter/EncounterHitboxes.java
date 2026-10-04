package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Model-sized attack bounds, independent of the smaller navigation and spawn bounds. */
public final class EncounterHitboxes {

    private EncounterHitboxes() {}

    public static boolean enlarged(Entity entity) {
        if (entity instanceof EntitySilentKing || entity instanceof EntityResidualOathguard) return true;
        if (!(entity instanceof EntityOldEcho)) return false;
        EchoKind kind = ((EntityOldEcho) entity).getKind();
        // The terminal apparition retains its scripted, non-combat contract.
        return kind.hasBossBar() || !kind.isRitual() && kind != EchoKind.DO02 && kind.height >= 2;
    }

    public static AxisAlignedBB attackBounds(Entity entity) {
        if (!enlarged(entity)) return entity.boundingBox;
        double radius, height;
        if (entity instanceof EntitySilentKing) {
            radius = 4.25;
            height = 12;
        } else if (entity instanceof EntityResidualOathguard) {
            // The separate native DR09 implementation renders the same oathguard rig.
            radius = 1.3;
            height = 2.65;
        } else {
            EchoKind kind = ((EntityOldEcho) entity).getKind();
            switch (kind) {
                case DI01:
                    radius = 2.55;
                    height = 5;
                    break;
                case DI02:
                    radius = 2.6;
                    height = 4.5;
                    break;
                case DI03:
                    radius = 4.85;
                    height = 7;
                    break;
                case DI04:
                    radius = 3.25;
                    height = 2.936;
                    break;
                case DI05:
                    radius = 3.05;
                    height = 1.8;
                    break;
                case DI06:
                    radius = 3.5;
                    height = 6;
                    break;
                case DI07:
                    radius = 3.65;
                    height = 6.014;
                    break;
                case DI08:
                    radius = .95;
                    height = 5;
                    break;
                case DI09:
                    radius = 3.4;
                    height = 2.064;
                    break;
                case DI10:
                    radius = 1.55;
                    height = 4.5;
                    break;
                case DI11:
                    radius = .9;
                    height = 4.5;
                    break;
                case DI12:
                    radius = 3.4;
                    height = 1.807;
                    break;
                case DI13:
                    radius = 1.85;
                    height = 4.5;
                    break;
                case DI14:
                    radius = 1.85;
                    height = 5;
                    break;
                case DI15:
                    radius = 1;
                    height = 5.5;
                    break;
                case DC02:
                    radius = 6.75;
                    height = 14;
                    break;
                case DC08:
                    radius = 6;
                    height = 16;
                    break;
                default:
                    radius = Math.max(
                        kind.width / 2,
                        kind == EchoKind.DR17 ? 1.55 : kind == EchoKind.DR10 ? 1.5 : kind == EchoKind.DR09 ? 1.15 : 1);
                    height = Math.max(kind.height, 2.5);
            }
        }
        radius = Math.max(radius, entity.width / 2);
        height = Math.max(height, entity.height);
        return AxisAlignedBB.getBoundingBox(
            entity.posX - radius,
            entity.boundingBox.minY,
            entity.posZ - radius,
            entity.posX + radius,
            entity.boundingBox.minY + height,
            entity.posZ + radius);
    }

    /** Preserve vanilla's six-block server limit while measuring from the visible body. */
    public static double attackDistanceSq(EntityPlayer player, Entity target) {
        if (!enlarged(target)) return player.getDistanceSqToEntity(target);
        AxisAlignedBB box = attackBounds(target)
            .expand(target.getCollisionBorderSize(), target.getCollisionBorderSize(), target.getCollisionBorderSize());
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.boundingBox.minY + player.getEyeHeight(), player.posZ);
        Vec3 point = Vec3.createVectorHelper(
            clamp(eye.xCoord, box.minX, box.maxX),
            clamp(eye.yCoord, box.minY, box.maxY),
            clamp(eye.zCoord, box.minZ, box.maxZ));
        // Vanilla's block ray trace mutates its start vector while stepping through the world.
        double distance = eye.squareDistanceTo(point);
        if (player.worldObj.rayTraceBlocks(eye, point) != null) return Double.MAX_VALUE;
        return distance;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
