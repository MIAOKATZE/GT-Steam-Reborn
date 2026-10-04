package com.miaokatze.gtsr.client.architecture;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSpawnerContract;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Transparent voxel sigils consume synchronized server quota, with an expanding unlock cascade. */
@SideOnly(Side.CLIENT)
public final class RemasterSpawnerRenderer extends TileEntitySpecialRenderer {

    public static void registerRenderer() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileRemasterNode.class, new RemasterSpawnerRenderer());
    }

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileRemasterNode node = (TileRemasterNode) tile;
        if (!"spawner".equals(node.role) || node.getWorldObj() == null) return;
        JsonObject view;
        try {
            view = new JsonParser().parse(node.display)
                .getAsJsonObject();
        } catch (RuntimeException ignored) {
            return;
        }
        if (!view.has("tier")) return;
        int tier = view.get("tier")
            .getAsInt();
        boolean spent = view.get("spent")
            .getAsBoolean();
        double time = node.getWorldObj()
            .getTotalWorldTime() + partial;
        double unlock = spent ? Math.max(
            0,
            Math.min(
                1,
                (time - view.get("unlockAt")
                    .getAsLong()) / 40))
            : 0;
        double pulse = Math.max(
            0,
            1 - (time - view.get("pulseAt")
                .getAsLong()) / 24);
        int count = spent ? 8
            : RemasterSpawnerContract.runes(
                tier,
                view.get("count")
                    .getAsInt());
        float r = spent ? 1F : tier == 3 ? .72F : 1F;
        float g = spent ? .85F : tier == 1 ? .45F : .12F;
        float b = spent ? .14F : tier == 3 ? 1F : .08F;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + .5, y + .5, z + .5);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count + time * (spent ? .018 : .008);
                double radius = .72 + pulse * .12 + Math.sin(unlock * Math.PI) * .4;
                GL11.glPushMatrix();
                GL11.glTranslated(
                    Math.cos(angle) * radius,
                    Math.sin(time * .09 + i) * (.045 + pulse * .14) + unlock * .12,
                    Math.sin(angle) * radius);
                GL11.glRotated(-angle * 180 / Math.PI, 0, 1, 0);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.setBrightness(0xF000F0);
                t.setColorRGBA_F(r, g, b, .76F);
                box(t, -.025, -.14, -.08, .025, .14, -.025);
                box(t, -.025, .09, -.08, .025, .14, .11);
                box(t, -.025, -.02, -.04, .025, .03, .08);
                box(t, -.025, -.14, .055, .025, .03, .11);
                t.draw();
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void box(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[][] v = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 }, { x0, y0, z1 }, { x1, y0, z1 },
            { x1, y1, z1 }, { x0, y1, z1 } };
        int[][] faces = { { 0, 3, 2, 1 }, { 4, 5, 6, 7 }, { 0, 4, 7, 3 }, { 1, 2, 6, 5 }, { 3, 7, 6, 2 },
            { 0, 1, 5, 4 } };
        for (int[] face : faces) for (int index : face) t.addVertex(v[index][0], v[index][1], v[index][2]);
    }
}
