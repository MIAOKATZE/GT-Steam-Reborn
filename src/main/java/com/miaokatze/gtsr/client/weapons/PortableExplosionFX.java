package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityHugeExplodeFX;
import net.minecraft.client.particle.EntityLargeExplodeFX;
import net.minecraft.world.World;

/** TNT's eight-tick texture burst, admitted directly so distant loaded shots are still visible. */
final class PortableExplosionFX extends EntityHugeExplodeFX {

    private int age;

    PortableExplosionFX(World world, double x, double y, double z) {
        super(world, x, y, z, 0, 0, 0);
    }

    @Override
    public void onUpdate() {
        for (int i = 0; i < 6; i++) {
            EntityLargeExplodeFX fragment = new EntityLargeExplodeFX(
                Minecraft.getMinecraft()
                    .getTextureManager(),
                worldObj,
                posX + (rand.nextDouble() - rand.nextDouble()) * 4,
                posY + (rand.nextDouble() - rand.nextDouble()) * 4,
                posZ + (rand.nextDouble() - rand.nextDouble()) * 4,
                age / 8.0,
                0,
                0);
            fragment.prevPosX = fragment.posX;
            fragment.prevPosY = fragment.posY;
            fragment.prevPosZ = fragment.posZ;
            Minecraft.getMinecraft().effectRenderer.addEffect(fragment);
        }
        if (++age >= 8) setDead();
    }
}
