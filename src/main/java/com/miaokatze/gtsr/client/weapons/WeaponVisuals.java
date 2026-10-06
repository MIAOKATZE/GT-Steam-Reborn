package com.miaokatze.gtsr.client.weapons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.common.weapons.Effect;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Disposable client entities and depth-tested previews; never applies damage. */
final class WeaponVisuals {

    private final List<Casing> casings = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();
    private int particles;
    private static final ResourceLocation SHELL_TEXTURE = new ResourceLocation("gtsr:textures/weapons/casing.png");
    private static IModelCustom shellModel;
    private static boolean shellAttempted;

    private static final class Burst {

        final double x, y, z;
        final boolean explosion;
        int age;

        Burst(double x, double y, double z, boolean explosion) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.explosion = explosion;
        }
    }

    void clear() {
        for (Casing casing : casings) casing.setDead();
        casings.clear();
        bursts.clear();
        particles = 0;
    }

    void tick() {
        particles = 0;
        casings.removeIf(c -> c.isDead);
        Iterator<Burst> it = bursts.iterator();
        while (it.hasNext()) if (++it.next().age > 14) it.remove();
    }

    void effect(Effect effect, WeaponKind kind) {
        Minecraft mc = Minecraft.getMinecraft();
        if (effect.type == 0) {
            double yaw = Math.toRadians(effect.yaw), pitch = Math.toRadians(effect.pitch);
            double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
            double x = effect.x + dx * .7, y = effect.y + dy * .7, z = effect.z + dz * .7;
            addBurst(x, y, z, false);
            emit("flame", x, y, z, dx * .03, dy * .03, dz * .03);
            emit("smoke", x, y, z, dx * .04, .025, dz * .04);
            if (casings.size() >= 48) casings.remove(0)
                .setDead();
            Casing casing = new Casing(
                mc.theWorld,
                effect.x,
                effect.y - .15,
                effect.z,
                Math.cos(yaw) * .16,
                .13,
                Math.sin(yaw) * .16,
                kind == WeaponKind.LM12 ? .45F : .75F);
            casings.add(casing);
            mc.effectRenderer.addEffect(casing);
            String sound = kind == WeaponKind.LM12 ? "gatling_fire"
                : kind == WeaponKind.T20 ? "autocannon_fire" : "qlz04_fire";
            mc.theWorld.playSound(effect.x, effect.y, effect.z, "gtsr:weapons." + sound, 1.4F, 1, false);
        } else if (effect.type == 1) {
            if (kind != WeaponKind.LM12) {
                addBurst(effect.x, effect.y, effect.z, true);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI * 2 / 12;
                    emit(
                        i % 3 == 0 ? "flame" : "smoke",
                        effect.x,
                        effect.y,
                        effect.z,
                        Math.cos(a) * .13,
                        .04 + i * .004,
                        Math.sin(a) * .13);
                }
                mc.theWorld.playSound(effect.x, effect.y, effect.z, "random.explode", .7F, 1.2F, false);
            } else emit("smoke", effect.x, effect.y, effect.z, 0, .02, 0);
        }
    }

    private void emit(String name, double x, double y, double z, double dx, double dy, double dz) {
        if (particles++ < 64) Minecraft.getMinecraft().theWorld.spawnParticle(name, x, y, z, dx, dy, dz);
    }

    private void addBurst(double x, double y, double z, boolean explosion) {
        if (bursts.size() >= 32) bursts.remove(0);
        bursts.add(new Burst(x, y, z, explosion));
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (Burst burst : bursts) {
                float age = burst.age + event.partialTicks;
                float alpha = Math.max(0, 1 - age / (burst.explosion ? 14 : 3));
                if (alpha <= 0) continue;
                double radius = burst.explosion ? .15 + age * .11 : .12 + age * .025;
                GL11.glColor4f(1, .55F + alpha * .4F, .15F, alpha * .7F);
                GL11.glPushMatrix();
                GL11.glTranslated(burst.x, burst.y, burst.z);
                GL11.glScaled(radius, radius, radius);
                box();
                GL11.glPopMatrix();
                if (burst.explosion) {
                    GL11.glColor4f(1, .7F, .3F, alpha * .6F);
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    for (int i = 0; i < 32; i++) {
                        double a = i * Math.PI * 2 / 32;
                        GL11.glVertex3d(
                            burst.x + Math.cos(a) * radius * 1.8,
                            burst.y + .025,
                            burst.z + Math.sin(a) * radius * 1.8);
                    }
                    GL11.glEnd();
                }
            }
            if (mc.currentScreen == null && mc.inGameHasFocus && PortableWeaponClient.focusProgress() > .1F)
                preview(mc.thePlayer, event.partialTicks);
        }
    }

    private static void preview(EntityPlayer player, float partial) {
        WeaponKind kind = PortableWeapons.kind(player.getHeldItem());
        if (kind == null) return;
        Vec3 aim = player.getLook(partial);
        double x = player.prevPosX + (player.posX - player.prevPosX) * partial;
        double y = player.prevPosY + (player.posY - player.prevPosY) * partial + player.getEyeHeight();
        double z = player.prevPosZ + (player.posZ - player.prevPosZ) * partial;
        x += aim.xCoord * .5;
        y += aim.yCoord * .5;
        z += aim.zCoord * .5;
        double vx = aim.xCoord * kind.projectileSpeed, vy = aim.yCoord * kind.projectileSpeed,
            vz = aim.zCoord * kind.projectileSpeed;
        GL11.glColor4f(.3F, .95F, .85F, .45F * PortableWeaponClient.focusProgress());
        GL11.glLineWidth(1);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x, y, z);
        for (int i = 0; i < 48; i++) {
            Vec3 from = Vec3.createVectorHelper(x, y, z), to = Vec3.createVectorHelper(x + vx, y + vy, z + vz);
            MovingObjectPosition hit = player.worldObj.func_147447_a(from, to, false, true, false);
            if (hit != null) {
                GL11.glVertex3d(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
                break;
            }
            x += vx;
            y += vy;
            z += vz;
            vy -= kind.gravity;
            GL11.glVertex3d(x, y, z);
        }
        GL11.glEnd();
    }

    private static final class Casing extends EntityFX {

        private final float scale;

        Casing(World world, double x, double y, double z, double vx, double vy, double vz, float scale) {
            super(world, x, y, z, 0, 0, 0);
            motionX = vx;
            motionY = vy;
            motionZ = vz;
            particleMaxAge = 32;
            this.scale = scale;
            setSize(.08F, .08F);
        }

        @Override
        public int getFXLayer() {
            return 3;
        }

        @Override
        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            if (particleAge++ >= particleMaxAge) setDead();
            motionY -= .035;
            moveEntity(motionX, motionY, motionZ);
            motionX *= .97;
            motionZ *= .97;
            if (onGround) {
                motionY = 0;
                motionX *= .6;
                motionZ *= .6;
            }
        }

        @Override
        public void renderParticle(Tessellator tessellator, float partial, float rx, float rz, float ryz, float rxy,
            float rxz) {
            try (GlScope scope = new GlScope()) {
                GL11.glTranslated(
                    prevPosX + (posX - prevPosX) * partial - interpPosX,
                    prevPosY + (posY - prevPosY) * partial - interpPosY,
                    prevPosZ + (posZ - prevPosZ) * partial - interpPosZ);
                GL11.glRotatef((particleAge + partial) * 31, .4F, .8F, .2F);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glColor4f(.8F, .65F, .3F, 1);
                GL11.glDisable(GL11.GL_CULL_FACE);
                if (!shellAttempted) {
                    shellAttempted = true;
                    try {
                        shellModel = AdvancedModelLoader
                            .loadModel(new ResourceLocation("gtsr:models/weapons/casing.obj"));
                    } catch (RuntimeException unavailable) {
                        shellModel = null;
                    }
                }
                if (shellModel != null) {
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(SHELL_TEXTURE);
                    GL11.glScalef(scale, scale, scale);
                    shellModel.renderAll();
                } else {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glScalef(.025F * scale, .025F * scale, .075F * scale);
                    box();
                }
            }
        }
    }

    private static void box() {
        GL11.glBegin(GL11.GL_QUADS);
        for (int face = 0; face < 6; face++) {
            int axis = face / 2;
            double side = face % 2 == 0 ? -1 : 1;
            for (int corner = 0; corner < 4; corner++) {
                double a = corner == 0 || corner == 3 ? -1 : 1, b = corner < 2 ? -1 : 1;
                if (axis == 0) GL11.glVertex3d(side, a, b);
                else if (axis == 1) GL11.glVertex3d(a, side, b);
                else GL11.glVertex3d(a, b, side);
            }
        }
        GL11.glEnd();
    }
}
