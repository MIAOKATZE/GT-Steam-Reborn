package com.miaokatze.gtsr.common.weapons;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Shared physical gun transform: model +Y up, -Z muzzle. FOV never moves this transform. */
public final class WeaponPose {

    // Positive world Y raises the gun; place its body at the waist, below the carry handle.
    public static final double SCALE = .72, RIGHT = .46, UP = -.68, FORWARD = .38;

    private WeaponPose() {}

    public static Vec3 eye(EntityPlayer p, float partial) {
        return Vec3.createVectorHelper(
            p.prevPosX + (p.posX - p.prevPosX) * partial,
            p.prevPosY + (p.posY - p.prevPosY) * partial
                - p.yOffset
                + 1.62
                + p.getEyeHeight()
                - p.getDefaultEyeHeight(),
            p.prevPosZ + (p.posZ - p.prevPosZ) * partial);
    }

    public static float yaw(EntityPlayer p, float partial) {
        return p.prevRotationYaw + MathHelper.wrapAngleTo180_float(p.rotationYaw - p.prevRotationYaw) * partial;
    }

    public static float pitch(EntityPlayer p, float partial) {
        return p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial;
    }

    public static Vec3 right(EntityPlayer p, float partial) {
        double y = Math.toRadians(yaw(p, partial));
        return Vec3.createVectorHelper(-Math.cos(y), 0, -Math.sin(y));
    }

    public static Vec3 forward(EntityPlayer p, float partial) {
        double y = Math.toRadians(yaw(p, partial)), t = Math.toRadians(pitch(p, partial));
        return Vec3.createVectorHelper(-Math.sin(y) * Math.cos(t), -Math.sin(t), Math.cos(y) * Math.cos(t));
    }

    public static Vec3 up(EntityPlayer p, float partial) {
        double y = Math.toRadians(yaw(p, partial)), t = Math.toRadians(pitch(p, partial));
        return Vec3.createVectorHelper(-Math.sin(y) * Math.sin(t), Math.cos(t), Math.cos(y) * Math.sin(t));
    }

    public static Vec3 modelPoint(EntityPlayer p, float partial, double x, double y, double z) {
        Vec3 eye = eye(p, partial), r = right(p, partial), u = up(p, partial), f = forward(p, partial);
        double rx = RIGHT + SCALE * x, uy = SCALE * y, fz = FORWARD - SCALE * z;
        return eye.addVector(
            r.xCoord * rx + u.xCoord * uy + f.xCoord * fz,
            UP + r.yCoord * rx + u.yCoord * uy + f.yCoord * fz,
            r.zCoord * rx + u.zCoord * uy + f.zCoord * fz);
    }

    public static Vec3 origin(EntityPlayer p, float partial) {
        return modelPoint(p, partial, 0, 0, 0);
    }

    /** Mean of existing barrel minimum-Z end vertices, measured from actual v87/v88 OBJ. */
    public static Vec3 localMuzzle(WeaponKind kind) {
        if (kind == WeaponKind.SINGULARITY) return Vec3.createVectorHelper(0, .076875, -1.359375);
        if (kind == WeaponKind.T20) return Vec3.createVectorHelper(-.0003125125, .076875, -1.359375);
        if (kind == WeaponKind.QLZ04) return Vec3.createVectorHelper(0, .0006249125, -1.325);
        return Vec3.createVectorHelper(0, -.0006249854, -1.328125);
    }

    public static Vec3 localGrip() {
        return Vec3.createVectorHelper(0, .34, .28);
    }

    public static Vec3 localSupport() {
        return Vec3.createVectorHelper(0, -.28, -.30);
    }

    /** Fetch ammunition outside the left torso, then approach the feed only from its left side. */
    public static Vec3 reloadOffset(WeaponKind kind, float progress) {
        if (progress <= 0 || progress >= 1) return Vec3.createVectorHelper(0, 0, 0);
        double reach = progress < .25 ? progress / .25 : progress < .65 ? 1 : (1 - progress) / .35;
        double fetch = progress < .25 ? progress / .25 : progress < .65 ? (.65 - progress) / .4 : 0;
        return Vec3.createVectorHelper(-1.35 * reach, -.45 * fetch, -.35 * fetch);
    }

    /** Left feed attachments sit ahead of the torso, including while ammunition is fetched. */
    public static Vec3 attachmentOffset(WeaponKind kind, String part, float progress) {
        if (!"magazine".equals(part) && !"belt".equals(part)) return Vec3.createVectorHelper(0, 0, 0);
        Vec3 movement = "belt".equals(part) || kind == WeaponKind.QLZ04 ? reloadOffset(kind, progress)
            : Vec3.createVectorHelper(0, 0, 0);
        // The imported attachment's rear edge is local Z=.42; -.1 places it at world forward=.1496.
        return movement.addVector(0, 0, -.1);
    }

    public static Vec3 muzzle(EntityPlayer p, WeaponKind kind) {
        return muzzle(p, kind, 1);
    }

    public static Vec3 muzzle(EntityPlayer p, WeaponKind kind, float partial) {
        Vec3 v = localMuzzle(kind);
        return modelPoint(p, partial, v.xCoord, v.yCoord, v.zCoord);
    }

    public static Vec3 eject(EntityPlayer p, WeaponKind kind) {
        return eject(p, kind, 1);
    }

    public static Vec3 eject(EntityPlayer p, WeaponKind kind, float partial) {
        return modelPoint(p, partial, .19, -.03, .48);
    }
}
