package com.miaokatze.gtsr.common.weapons;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
public final class WeaponPose {
    public static Vec3 muzzle(EntityPlayer p, WeaponKind k) { return Vec3.createVectorHelper(0, 0, 0); }
    public static Vec3 eject(EntityPlayer p, WeaponKind k) { return Vec3.createVectorHelper(0, 0, 0); }
}
