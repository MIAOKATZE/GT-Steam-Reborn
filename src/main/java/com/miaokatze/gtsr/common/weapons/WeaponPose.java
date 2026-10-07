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
        return modelPoint(p, PortableWeapons.kind(p.getHeldItem()), partial, x, y, z);
    }

    public static Vec3 modelPoint(EntityPlayer p, WeaponKind kind, float partial, double x, double y, double z) {
        return physical(p, kind, partial).modelPoint(x, y, z);
    }

    private static Vec3 carriedPoint(EntityPlayer p, float partial, double x, double y, double z) {
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

    /** Rigid physical pose shared by rendering, hands, effects and authoritative projectile creation. */
    public static final class Physical {

        public final Vec3 origin, right, up, forward;

        private Physical(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
            this.origin = origin;
            this.right = right;
            this.up = up;
            this.forward = forward;
        }

        public Vec3 modelPoint(double x, double y, double z) {
            return origin.addVector(
                SCALE * (right.xCoord * x + up.xCoord * y - forward.xCoord * z),
                SCALE * (right.yCoord * x + up.yCoord * y - forward.yCoord * z),
                SCALE * (right.zCoord * x + up.zCoord * y - forward.zCoord * z));
        }
    }

    public static Physical physical(EntityPlayer p, WeaponKind kind, float partial) {
        Vec3 r = right(p, partial), u = up(p, partial), f = forward(p, partial);
        Vec3 origin = carriedPoint(p, partial, 0, 0, 0);
        if (kind != WeaponKind.LM12 && kind != WeaponKind.T20) return new Physical(origin, r, u, f);
        Vec3 grip = localGrip(), pivot = carriedPoint(p, partial, grip.xCoord, grip.yCoord, grip.zCoord);
        Vec3 target = EntityWeaponProjectile.aimTarget(p.worldObj, p, eye(p, partial), f);
        Vec3 delta = Vec3.createVectorHelper(
            target.xCoord - pivot.xCoord,
            target.yCoord - pivot.yCoord,
            target.zCoord - pivot.zCoord);
        Vec3 muzzle = localMuzzle(kind);
        double a = SCALE * (muzzle.xCoord - grip.xCoord), b = SCALE * (muzzle.yCoord - grip.yCoord),
            depth = SCALE * (grip.zCoord - muzzle.zCoord), length = Math.sqrt(delta.dotProduct(delta));
        // A wall within barrel reach cannot be aimed at without reversing the gun. The existing
        // eye-to-muzzle obstruction segment handles it while the gun retains its carried direction.
        if (length <= Math.sqrt(a * a + b * b + depth * depth) + .05) return new Physical(origin, r, u, f);
        double c = Math.sqrt(length * length - a * a - b * b);
        // In the desired basis target-pivot = right*a + up*b + forward*c. Rotate that
        // reference vector onto the target analytically, preserving the world grip exactly.
        Vec3 reference = Vec3.createVectorHelper(
            (r.xCoord * a + u.xCoord * b + f.xCoord * c) / length,
            (r.yCoord * a + u.yCoord * b + f.yCoord * c) / length,
            (r.zCoord * a + u.zCoord * b + f.zCoord * c) / length);
        Vec3 direction = Vec3.createVectorHelper(delta.xCoord / length, delta.yCoord / length, delta.zCoord / length);
        Vec3 cross = reference.crossProduct(direction);
        double sine = Math.sqrt(cross.dotProduct(cross)), cosine = reference.dotProduct(direction);
        if (sine > 1.0E-12) {
            Vec3 axis = Vec3.createVectorHelper(cross.xCoord / sine, cross.yCoord / sine, cross.zCoord / sine);
            r = rotate(r, axis, sine, cosine);
            u = rotate(u, axis, sine, cosine);
            f = rotate(f, axis, sine, cosine);
        }
        origin = pivot.addVector(
            -SCALE * (r.xCoord * grip.xCoord + u.xCoord * grip.yCoord - f.xCoord * grip.zCoord),
            -SCALE * (r.yCoord * grip.xCoord + u.yCoord * grip.yCoord - f.yCoord * grip.zCoord),
            -SCALE * (r.zCoord * grip.xCoord + u.zCoord * grip.yCoord - f.zCoord * grip.zCoord));
        return new Physical(origin, r, u, f);
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double sine, double cosine) {
        Vec3 cross = axis.crossProduct(v);
        double along = axis.dotProduct(v) * (1 - cosine);
        return Vec3.createVectorHelper(
            v.xCoord * cosine + cross.xCoord * sine + axis.xCoord * along,
            v.yCoord * cosine + cross.yCoord * sine + axis.yCoord * along,
            v.zCoord * cosine + cross.zCoord * sine + axis.zCoord * along);
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

    /** Fifty-tick remote crush reaches at tick ten, holds, then returns during the final ten ticks. */
    public static Vec3 remoteHand(float progress) {
        double reach = progress < .2F ? progress * 5 : progress < .8F ? 1 : (1 - progress) * 5;
        reach = Math.max(0, Math.min(1, reach));
        return Vec3.createVectorHelper(-1.05 + .15 * reach, .28 + .5 * reach, .50 - .6 * reach);
    }

    public static Vec3 localSupport() {
        return Vec3.createVectorHelper(0, -.28, -.30);
    }

    /** Single right-hand recharge: ease back, hold while charging, then ease forward. */
    public static Vec3 singularityReloadBody(float progress) {
        double amount = progress < .2 ? smooth(progress / .2) : progress < .75 ? 1 : 1 - smooth((progress - .75) / .25);
        return Vec3.createVectorHelper(0, 0, .18 * amount);
    }

    public static Vec3 singularityReloadBarrel(float progress) {
        if (progress <= .2 || progress >= .75) return Vec3.createVectorHelper(0, 0, 0);
        double envelope = Math.sin((progress - .2) / .55 * Math.PI);
        return Vec3.createVectorHelper(
            .007 * envelope * Math.sin(progress * Math.PI * 40),
            .005 * envelope * Math.sin(progress * Math.PI * 46),
            .002 * envelope * Math.sin(progress * Math.PI * 52));
    }

    private static double smooth(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
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
        return modelPoint(p, kind, partial, v.xCoord, v.yCoord, v.zCoord);
    }

    public static Vec3 eject(EntityPlayer p, WeaponKind kind) {
        return eject(p, kind, 1);
    }

    public static Vec3 eject(EntityPlayer p, WeaponKind kind, float partial) {
        return modelPoint(p, kind, partial, .19, -.03, .48);
    }
}
