package com.miaokatze.gtsr.client.weapons;

import java.nio.DoubleBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Public Forge subscriber; physical gun placement does not inherit vanilla use/equip animation. */
public final class PortableWeaponPlayerPose {

    private static boolean registered;
    private static final ModelBiped SKIN_MODEL = new ModelBiped();
    private static final ModelRenderer RIGHT_UPPER = arm(false, 16), RIGHT_LOWER = arm(false, 22);
    private static final ModelRenderer LEFT_UPPER = arm(true, 16), LEFT_LOWER = arm(true, 22);

    private static ModelRenderer arm(boolean mirror, int v) {
        ModelRenderer arm = new ModelRenderer(SKIN_MODEL, 40, v);
        arm.mirror = mirror;
        arm.addBox(-2, 0, -2, 4, 6, 4);
        return arm;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new PortableWeaponPlayerPose());
    }

    @SubscribeEvent
    public void firstPerson(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.thirdPersonView != 0
            || mc.renderViewEntity != mc.thePlayer
            || mc.thePlayer.isPlayerSleeping()) return;
        WeaponKind kind = PortableWeapons.kind(mc.thePlayer.getHeldItem());
        if (kind != null) PortableWeaponRenderer.renderHeld(mc.thePlayer, kind, event.partialTicks, true);
    }

    @SubscribeEvent
    public void thirdPerson(RenderPlayerEvent.Post event) {
        EntityPlayer player = event.entityPlayer;
        if (player.isInvisible() || player.isPlayerSleeping()) return;
        WeaponKind kind = PortableWeapons.kind(player.getHeldItem());
        if (kind != null) PortableWeaponRenderer.renderHeld(player, kind, event.partialRenderTick, false);
    }

    /** Called after every native setRotationAngles; vanilla resets models before the next entity. */
    public static void apply(ModelBiped model, Entity entity) {
        if (!(entity instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) entity;
        WeaponKind kind = PortableWeapons.kind(p.getHeldItem());
        if (kind == null) return;
        float partial = PortableWeaponClient.renderPartialTicks();
        Vec3 right = WeaponPose.localGrip(), left = leftHand(p, kind, partial);
        aimArm(model.bipedRightArm, p, partial, right);
        aimArm(model.bipedLeftArm, p, partial, left);
        model.aimedBow = false;
    }

    private static Vec3 leftHand(EntityPlayer p, WeaponKind kind, float partial) {
        float progress = PortableWeaponClient.reloadProgress(p, partial);
        if (progress <= 0) return WeaponPose.localSupport();
        double reach = Math.sin(progress * Math.PI);
        if (progress > .7 && kind != WeaponKind.QLZ04)
            return Vec3.createVectorHelper(.235, .03, .65 + Math.sin((progress - .7) / .3 * Math.PI) * .18);
        return kind == WeaponKind.QLZ04
            ? Vec3.createVectorHelper(.27 - .6 * reach, -.23 + .35 * reach, .23 - .7 * reach)
            : Vec3.createVectorHelper(.19 + reach * .35, -.05, .26);
    }

    private static void aimArm(ModelRenderer arm, EntityPlayer p, float partial, Vec3 local) {
        Vec3 hand = WeaponPose.modelPoint(p, partial, local.xCoord, local.yCoord, local.zCoord);
        double yaw = Math.toRadians(p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * partial);
        double x = hand.xCoord - (p.prevPosX + (p.posX - p.prevPosX) * partial);
        double z = hand.zCoord - (p.prevPosZ + (p.posZ - p.prevPosZ) * partial);
        double feet = p.prevPosY + (p.posY - p.prevPosY) * partial - p.yOffset;
        double dx = (x * Math.cos(yaw) + z * Math.sin(yaw)) * 16 - arm.rotationPointX;
        double dz = (x * Math.sin(yaw) - z * Math.cos(yaw)) * 16 - arm.rotationPointZ;
        double dy = (feet + 1.5078125 - hand.yCoord) * 16 - arm.rotationPointY;
        arm.rotateAngleX = (float) Math.atan2(dz, dy);
        arm.rotateAngleY = 0;
        arm.rotateAngleZ = (float) Math.atan2(-dx, Math.sqrt(dy * dy + dz * dz));
    }

    /** Two native skin segments per arm, with a distinct elbow and moving support/loading hand. */
    static void renderArms(AbstractClientPlayer p, WeaponKind kind, float partial) {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(p.getLocationSkin());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            Vec3 eye = WeaponPose.eye(p, partial), r = WeaponPose.right(p, partial);
            Vec3 grip = WeaponPose.localGrip(), support = leftHand(p, kind, partial);
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                Vec3 shoulder = eye.addVector(r.xCoord * .31 * sign, -.20, r.zCoord * .31 * sign);
                Vec3 target = side == 0 ? grip : support;
                Vec3 hand = WeaponPose.modelPoint(p, partial, target.xCoord, target.yCoord, target.zCoord);
                Vec3 elbow = Vec3.createVectorHelper(
                    (shoulder.xCoord + hand.xCoord) / 2 + r.xCoord * .12 * sign,
                    (shoulder.yCoord + hand.yCoord) / 2 - .16,
                    (shoulder.zCoord + hand.zCoord) / 2 + r.zCoord * .12 * sign);
                segment(shoulder, elbow, side == 0 ? RIGHT_UPPER : LEFT_UPPER, r);
                segment(elbow, hand, side == 0 ? RIGHT_LOWER : LEFT_LOWER, r);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
        }
    }

    private static void segment(Vec3 from, Vec3 to, ModelRenderer mesh, Vec3 reference) {
        // Minecraft 1.7 Vec3.subtract returns argument minus receiver.
        Vec3 direction = from.subtract(to);
        double length = direction.lengthVector();
        if (length < 1e-5) return;
        Vec3 y = direction.normalize();
        Vec3 z = reference.crossProduct(y)
            .normalize();
        Vec3 x = y.crossProduct(z)
            .normalize();
        DoubleBuffer matrix = BufferUtils.createDoubleBuffer(16);
        matrix
            .put(
                new double[] { x.xCoord, x.yCoord, x.zCoord, 0, y.xCoord, y.yCoord, y.zCoord, 0, z.xCoord, z.yCoord,
                    z.zCoord, 0, from.xCoord, from.yCoord, from.zCoord, 1 })
            .flip();
        GL11.glPushMatrix();
        try {
            GL11.glMultMatrix(matrix);
            GL11.glScaled(1D / 16, length / 6, 1D / 16);
            mesh.render(1);
        } finally {
            GL11.glPopMatrix();
        }
    }
}
