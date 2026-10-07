package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Four fine gray sprites per actual bullet segment; bounded lifetime and no world-distance admission filter. */
final class PortableTrailFX extends EntityFX {

    private PortableTrailFX(World world, double x, double y, double z) {
        super(world, x, y, z, 0, 0, 0);
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        motionX = motionZ = 0;
        motionY = .003;
        particleScale = .25F;
        particleMaxAge = 8;
        particleAlpha = .26F;
        setRBGColorF(.45F, .45F, .45F);
        setParticleTextureIndex(7);
        noClip = true;
    }

    static void emit(Entity projectile) {
        double dx = projectile.posX - projectile.prevPosX, dy = projectile.posY - projectile.prevPosY,
            dz = projectile.posZ - projectile.prevPosZ;
        if (dx * dx + dy * dy + dz * dz < 1e-8) return;
        for (int i = 0; i < 4; i++) {
            double f = (i + .5) / 4;
            Minecraft.getMinecraft().effectRenderer.addEffect(
                new PortableTrailFX(
                    projectile.worldObj,
                    projectile.prevPosX + dx * f,
                    projectile.prevPosY + dy * f,
                    projectile.prevPosZ + dz * f));
        }
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (++particleAge >= particleMaxAge) {
            setDead();
            return;
        }
        particleAlpha = .26F * (1 - particleAge / (float) particleMaxAge);
        setParticleTextureIndex(7 - particleAge * 7 / particleMaxAge);
        setPosition(posX, posY + motionY, posZ);
    }
}
