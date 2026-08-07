package com.miaokatze.gtsr.common.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 通用客户端粒子基类：billboard quad 模板 + 默认 additive 渲染。
 * 移植自 Thaumcraft 4.2.3.5 FXGeneric/UtilsFX，自包含实现。
 */
@SideOnly(Side.CLIENT)
public abstract class GTSRFXParticle extends EntityFX {

    public static final ResourceLocation GLOW_TEXTURE = new ResourceLocation("gtsr", "textures/misc/glow.png");
    public static final ResourceLocation GLOW_SMALL_TEXTURE = new ResourceLocation(
        "gtsr",
        "textures/misc/glow_small.png");

    public GTSRFXParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleAlpha = 1.0F;
        this.particleGravity = 0.0F;
    }

    @Override
    public int getFXLayer() {
        return 2;
    }

    /**
     * billboard 四边形模板（参考 UtilsFX.renderFacingQuad），位置插值后按粒子缩放。
     */
    protected void renderGlowQuad(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz,
        IIcon icon) {
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) p - (double) interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) p - (double) interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) p - (double) interpPosZ);
        float s = this.particleScale;
        float u0 = 0.0F;
        float v0 = 0.0F;
        float u1 = 1.0F;
        float v1 = 1.0F;
        if (icon != null) {
            u0 = icon.getMinU();
            v0 = icon.getMinV();
            u1 = icon.getMaxU();
            v1 = icon.getMaxV();
        }
        tess.addVertexWithUV(
            (double) (x - rx * s - ry * s),
            (double) (y - rxz * s),
            (double) (z - rz * s - ryz * s),
            (double) u0,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x - rx * s + ry * s),
            (double) (y + rxz * s),
            (double) (z - rz * s + ryz * s),
            (double) u1,
            (double) v1);
        tess.addVertexWithUV(
            (double) (x + rx * s + ry * s),
            (double) (y + rxz * s),
            (double) (z + rz * s + ryz * s),
            (double) u1,
            (double) v0);
        tess.addVertexWithUV(
            (double) (x + rx * s - ry * s),
            (double) (y - rxz * s),
            (double) (z + rz * s - ryz * s),
            (double) u0,
            (double) v0);
    }

    @Override
    public void renderParticle(Tessellator tess, float p, float rx, float rz, float ry, float rxz, float ryz) {
        // 默认实现：关纹理的 additive 方块
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        tess.setBrightness(0x00F000F0);
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);
        this.renderGlowQuad(tess, p, rx, rz, ry, rxz, ryz, null);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * 绑定 glow 纹理的 additive 版本，供子类在自管 GL 状态下调用。
     */
    protected void renderGlowTextureQuad(Tessellator tess, float p, float rx, float rz, float ry, float rxz,
        float ryz) {
        Minecraft.getMinecraft().renderEngine.bindTexture(GLOW_TEXTURE);
        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        tess.setBrightness(0x00F000F0);
        tess.setColorRGBA_F(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);
        this.renderGlowQuad(tess, p, rx, rz, ry, rxz, ryz, null);
        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
