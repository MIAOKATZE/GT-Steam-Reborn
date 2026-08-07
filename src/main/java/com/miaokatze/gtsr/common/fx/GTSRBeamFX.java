package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 旋转三带光束粒子：自管批次 + 滑动环绕纹理。
 * 参考 Thaumcraft 4.2.3.5 FXBeamWand（三带渲染、旋转、带面），自包含实现。
 */
@SideOnly(Side.CLIENT)
public class GTSRBeamFX extends GTSRFXParticle {

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final float length;
    private final float width;
    private float yaw;
    private float pitch;
    private float rot;
    private final int maxAge = 10000;

    private GTSRBeamFX(World world, double x, double y, double z, float length, float width, long seed) {
        super(world, x, y, z);
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.length = length;
        this.width = width;
        this.rand.setSeed(seed);
        this.yaw = this.rand.nextFloat() * 2.0F * (float) Math.PI;
        this.pitch = (this.rand.nextFloat() - 0.5F) * 0.9F;
        this.rot = this.rand.nextFloat() * 2.0F * (float) Math.PI;
    }

    public static GTSRBeamFX add(World world, double x, double y, double z, float length, float width) {
        GTSRBeamFX fx = new GTSRBeamFX(world, x, y, z, length, width, System.nanoTime());
        GTSRFXEngine.instance()
            .addEffect(fx);
        return fx;
    }

    @Override
    public void onUpdate() {
        this.particleAge++;
        this.yaw += (this.rand.nextFloat() - 0.5F) * 0.06F;
        this.pitch += (this.rand.nextFloat() - 0.5F) * 0.04F;
        if (this.pitch > 1.2F) {
            this.pitch = 1.2F;
        }
        if (this.pitch < -1.2F) {
            this.pitch = -1.2F;
        }
        this.rot += 0.05F;
    }

    @Override
    public void renderParticle(Tessellator tess, float partialframe, float cosyaw, float cospitch, float sinyaw,
        float sinsinpitch, float cossinpitch) {
        EntityClientPlayerMP renderentity = Minecraft.getMinecraft().thePlayer;
        int visibleDistance = 100;
        if (!Minecraft.getMinecraft().gameSettings.fancyGraphics) {
            visibleDistance = 50;
        }
        if (renderentity == null
            || renderentity.getDistanceSq(this.posX, this.posY, this.posZ) > (double) visibleDistance) {
            return;
        }
        tess.draw(); // 刷新外层批次，本粒子自管批次（TC4 FXBeamWand 兼容模式）
        GL11.glPushMatrix();
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Minecraft.getMinecraft().renderEngine.bindTexture(GTSRFXParticle.GLOW_TEXTURE);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTranslated(
            this.centerX - (double) interpPosX,
            this.centerY - (double) interpPosY,
            this.centerZ - (double) interpPosZ);
        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(180.0F + (float) Math.toDegrees((double) this.yaw), 0.0F, 0.0F, -1.0F);
        GL11.glRotatef((float) Math.toDegrees((double) this.pitch), 1.0F, 0.0F, 0.0F);
        for (int i = 0; i < 3; i++) {
            GL11.glRotatef(60.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef((float) Math.toDegrees((double) this.rot), 0.0F, 1.0F, 0.0F);
            float slide = (float) this.particleAge * 0.5F;
            float v0 = (0.0F + slide) * 0.25F;
            float v1 = (this.length + slide) * 0.25F;
            tess.startDrawingQuads();
            tess.setBrightness(0x00F000F0);
            tess.setColorRGBA_F(0.85F, 0.92F, 1.0F, 0.5F);
            tess.addVertexWithUV((double) (-this.width * 0.7F), (double) this.length, 0.0D, 1.0D, (double) v1);
            tess.addVertexWithUV((double) (-this.width), 0.0D, 0.0D, 1.0D, (double) v0);
            tess.addVertexWithUV((double) this.width, 0.0D, 0.0D, 0.0D, (double) v0);
            tess.addVertexWithUV((double) (this.width * 0.7F), (double) this.length, 0.0D, 0.0D, (double) v1);
            tess.draw();
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
        tess.startDrawingQuads(); // 恢复外层批次
    }
}
