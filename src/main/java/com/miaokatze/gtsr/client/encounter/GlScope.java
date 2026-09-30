package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** 1.7.10固定管线状态护栏：两个MC纹理单元的矩阵、属性、当前矩阵模式均对称恢复。 */
final class GlScope implements AutoCloseable {

    private final float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
    private final int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

    GlScope() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(-1 /* GL_CLIENT_ALL_ATTRIB_BITS, absent in LWJGL 2 binding */);
        for (int u : new int[] { OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit }) {
            OpenGlHelper.setActiveTexture(u);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
    }

    public void close() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        for (int u : new int[] { OpenGlHelper.lightmapTexUnit, OpenGlHelper.defaultTexUnit }) {
            OpenGlHelper.setActiveTexture(u);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
        }
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        OpenGlHelper.setActiveTexture(unit);
        GL11.glMatrixMode(mode);
    }
}
