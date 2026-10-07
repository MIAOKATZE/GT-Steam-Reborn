package com.miaokatze.gtsr.client.weapons;

import java.nio.DoubleBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
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
        if (kind == WeaponKind.SINGULARITY) {
            Vec3 offset = WeaponPose.singularityReloadBody(PortableWeaponClient.reloadProgress(p, partial));
            right = right.addVector(offset.xCoord, offset.yCoord, offset.zCoord);
        }
        aimArm(model.bipedRightArm, p, kind, partial, right);
        if (leftActive(p, kind, partial)) aimArm(model.bipedLeftArm, p, kind, partial, left);
        model.aimedBow = false;
    }

    private static boolean leftActive(EntityPlayer p, WeaponKind kind, float partial) {
        return kind == WeaponKind.SINGULARITY ? PortableWeaponClient.remoteProgress(p, partial) > 0
            : PortableWeaponClient.reloadProgress(p, partial) > 0;
    }

    static Vec3 leftHand(EntityPlayer p, WeaponKind kind, float partial) {
        float progress = PortableWeaponClient.reloadProgress(p, partial);
        if (kind == WeaponKind.SINGULARITY)
            return WeaponPose.remoteHand(PortableWeaponClient.remoteProgress(p, partial));
        if (progress <= 0) return Vec3.createVectorHelper(-1.05, .28, .50);
        Vec3 feed = kind == WeaponKind.QLZ04 ? Vec3.createVectorHelper(-.27, -.23, .23)
            : Vec3.createVectorHelper(-.19, -.05, .26);
        Vec3 offset = WeaponPose.attachmentOffset(kind, kind == WeaponKind.QLZ04 ? "magazine" : "belt", progress);
        return feed.addVector(offset.xCoord, offset.yCoord, offset.zCoord);
    }

    private static void aimArm(ModelRenderer arm, EntityPlayer p, WeaponKind kind, float partial, Vec3 local) {
        Vec3 hand = WeaponPose.modelPoint(p, kind, partial, local.xCoord, local.yCoord, local.zCoord);
        double yaw = Math.toRadians(WeaponPose.yaw(p, partial));
        double x = hand.xCoord - (p.prevPosX + (p.posX - p.prevPosX) * partial);
        double z = hand.zCoord - (p.prevPosZ + (p.posZ - p.prevPosZ) * partial);
        double feet = p.prevPosY + (p.posY - p.prevPosY) * partial - p.yOffset;
        // RenderPlayer lowers non-SP crouching players before RendererLivingEntity establishes its model origin.
        // ModelBiped's body lean is not a parent transform of either arm.
        if (p.isSneaking() && !(p instanceof EntityPlayerSP)) feet -= .125;
        double dx = (x * Math.cos(yaw) + z * Math.sin(yaw)) * 16 - arm.rotationPointX;
        double dz = (x * Math.sin(yaw) - z * Math.cos(yaw)) * 16 - arm.rotationPointZ;
        double dy = (feet + 1.5078125 - hand.yCoord) * 16 - arm.rotationPointY;
        // ModelRenderer applies Rz * Ry * Rx; with Ry=0 its +Y axis becomes
        // (-cos(X)*sin(Z), cos(X)*cos(Z), sin(X)). Solve that order exactly.
        arm.rotateAngleX = (float) Math.atan2(dz, Math.sqrt(dx * dx + dy * dy));
        arm.rotateAngleY = 0;
        arm.rotateAngleZ = (float) Math.atan2(-dx, dy);
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
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            Vec3 eye = WeaponPose.eye(p, partial), r = WeaponPose.right(p, partial);
            Vec3 grip = WeaponPose.localGrip(), support = leftHand(p, kind, partial);
            if (kind == WeaponKind.SINGULARITY) {
                Vec3 offset = WeaponPose.singularityReloadBody(PortableWeaponClient.reloadProgress(p, partial));
                grip = grip.addVector(offset.xCoord, offset.yCoord, offset.zCoord);
            }
            boolean modern = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
                == GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            boolean loading = leftActive(p, kind, partial);
            for (int side = 0; side < (loading ? 2 : 1); side++) {
                double sign = side == 0 ? 1 : -1;
                Vec3 shoulder = eye.addVector(r.xCoord * .31 * sign, -.20, r.zCoord * .31 * sign);
                Vec3 target = side == 0 ? grip : support;
                Vec3 hand = WeaponPose.modelPoint(p, kind, partial, target.xCoord, target.yCoord, target.zCoord);
                hand = PortableWeaponRenderer.viewPoint(p, partial, hand);
                Vec3 elbow = Vec3.createVectorHelper(
                    (shoulder.xCoord + hand.xCoord) / 2 + r.xCoord * .12 * sign,
                    (shoulder.yCoord + hand.yCoord) / 2 - .16,
                    (shoulder.zCoord + hand.zCoord) / 2 + r.zCoord * .12 * sign);
                segment(shoulder, elbow, side != 0, false, modern, r);
                segment(elbow, hand, side != 0, true, modern, r);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
        }
    }

    private static void segment(Vec3 from, Vec3 to, boolean left, boolean lower, boolean modern, Vec3 reference) {
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
            skinSegment(left, lower, modern);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The side UVs retain the original 12-pixel arm layout; splitting geometry does not repack its net. */
    private static void skinSegment(boolean left, boolean lower, boolean modern) {
        int u = left && modern ? 32 : 40, v = left && modern ? 48 : 16;
        float height = modern ? 64 : 32;
        int y = v + 4 + (lower ? 6 : 0);
        boolean mirror = left && !modern;
        GL11.glBegin(GL11.GL_QUADS);
        skinFace(
            new double[][] { { -2, 0, -2 }, { -2, 6, -2 }, { 2, 6, -2 }, { 2, 0, -2 } },
            u + 4,
            y,
            u + 8,
            y + 6,
            height,
            mirror);
        skinFace(
            new double[][] { { 2, 0, 2 }, { 2, 6, 2 }, { -2, 6, 2 }, { -2, 0, 2 } },
            u + 12,
            y,
            u + 16,
            y + 6,
            height,
            mirror);
        skinFace(
            new double[][] { { -2, 0, 2 }, { -2, 6, 2 }, { -2, 6, -2 }, { -2, 0, -2 } },
            u,
            y,
            u + 4,
            y + 6,
            height,
            mirror);
        skinFace(
            new double[][] { { 2, 0, -2 }, { 2, 6, -2 }, { 2, 6, 2 }, { 2, 0, 2 } },
            u + 8,
            y,
            u + 12,
            y + 6,
            height,
            mirror);
        skinFace(
            new double[][] { { -2, 0, 2 }, { -2, 0, -2 }, { 2, 0, -2 }, { 2, 0, 2 } },
            u + 4,
            v,
            u + 8,
            v + 4,
            height,
            mirror);
        skinFace(
            new double[][] { { -2, 6, -2 }, { -2, 6, 2 }, { 2, 6, 2 }, { 2, 6, -2 } },
            u + 8,
            v + 4,
            u + 12,
            v,
            height,
            mirror);
        GL11.glEnd();
    }

    private static void skinFace(double[][] vertices, float u0, float v0, float u1, float v1, float h, boolean mirror) {
        double ax = vertices[1][0] - vertices[0][0], ay = vertices[1][1] - vertices[0][1],
            az = vertices[1][2] - vertices[0][2];
        double bx = vertices[2][0] - vertices[0][0], by = vertices[2][1] - vertices[0][1],
            bz = vertices[2][2] - vertices[0][2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        GL11.glNormal3d((mirror ? -nx : nx) / length, ny / length, nz / length);
        float[][] uv = { { u0, v0 }, { u0, v1 }, { u1, v1 }, { u1, v0 } };
        for (int i = 0; i < 4; i++) {
            GL11.glTexCoord2f(uv[i][0] / 64, uv[i][1] / h);
            GL11.glVertex3d(mirror ? -vertices[i][0] : vertices[i][0], vertices[i][1], vertices[i][2]);
        }
    }

}
