package com.miaokatze.gtsr.client.weapons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.WeaponKind;

/** Portable carry hardware and ammunition cases, shared by inventory and world renders. */
final class WeaponMeshes {

    static void carryHandle(WeaponKind kind) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        box(-.065, .12, -.05, .065, .34, .02, .14F, .17F, .18F);
        box(-.065, .12, .48, .065, .34, .55, .14F, .17F, .18F);
        box(-.075, .30, -.05, .075, .38, .55, .22F, .25F, .25F);
        if (kind == WeaponKind.SINGULARITY) {
            box(-.22, -.14, -.65, .22, .19, -.4, .2F, .22F, .25F);
            box(-.14, .05, -.68, .14, .12, -.66, .66F, .45F, .9F);
        }
        GL11.glPopAttrib();
    }

    static void ammoPack(WeaponKind kind, boolean mimic) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glTranslatef(0, -.05F, 0);
        box(-.39, -.38, -.23, .39, .3, .23, .18F, .22F, .23F);
        box(-.43, .21, -.26, .43, .33, .26, .31F, .35F, .34F);
        box(-.3, .33, -.045, -.23, .5, .045, .13F, .15F, .14F);
        box(.23, .33, -.045, .3, .5, .045, .13F, .15F, .14F);
        box(-.3, .46, -.045, .3, .52, .045, .26F, .29F, .27F);
        for (int i = -1; i <= 1; i++) {
            double x = i * .25;
            box(x - .025, -.35, -.255, x + .025, .18, -.23, .38F, .42F, .39F);
        }
        float green = kind == WeaponKind.LM12 ? .65F : kind == WeaponKind.T20 ? .48F : .32F;
        box(-.2, -.06, -.265, .2, .08, -.25, .82F, green, .16F);
        box(-.45, -.1, -.06, -.39, .09, .06, .55F, .57F, .49F);
        box(.39, -.1, -.06, .45, .09, .06, .55F, .57F, .49F);
        if (mimic) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(new ResourceLocation("textures/misc/enchanted_item_glint.png"));
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef((System.currentTimeMillis() % 6000) / 6000F, 0, 0);
            GL11.glRotatef(35, 0, 0, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            box(-.395, -.385, -.268, .395, .335, .268, .55F, .26F, .8F);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, float r, float g,
        float b) {
        double[] lo = { x0, y0, z0 }, hi = { x1, y1, z1 };
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int face = 0; face < 6; face++) {
            int axis = face / 2, a = (axis + 1) % 3, bAxis = (axis + 2) % 3;
            float shade = face == 3 ? 1 : face == 2 ? .6F : .82F;
            t.setColorRGBA_F(r * shade, g * shade, b * shade, 1);
            float nx = axis == 0 ? (face % 2 == 0 ? -1 : 1) : 0;
            float ny = axis == 1 ? (face % 2 == 0 ? -1 : 1) : 0;
            float nz = axis == 2 ? (face % 2 == 0 ? -1 : 1) : 0;
            t.setNormal(nx, ny, nz);
            for (int corner = 0; corner < 4; corner++) {
                double[] v = lo.clone();
                v[axis] = face % 2 == 0 ? lo[axis] : hi[axis];
                v[a] = corner == 0 || corner == 3 ? lo[a] : hi[a];
                v[bAxis] = corner < 2 ? lo[bAxis] : hi[bAxis];
                t.addVertexWithUV(v[0], v[1], v[2], corner == 0 || corner == 3 ? 0 : 1, corner < 2 ? 0 : 1);
            }
        }
        t.draw();
    }
}
