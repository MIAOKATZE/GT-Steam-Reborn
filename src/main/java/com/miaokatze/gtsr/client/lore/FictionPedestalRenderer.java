package com.miaokatze.gtsr.client.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode;

/** Classical white plinth and an actual relic icon; the empty projection is 70 percent transparent. */
public final class FictionPedestalRenderer {

    private FictionPedestalRenderer() {}

    public static float itemAlpha(boolean deposited) {
        return deposited ? 1F : .30F;
    }

    public static boolean render(TileRemasterNode node, double x, double y, double z, float partial) {
        boolean starter = "core-story-reading".equals(node.nodeId);
        if ((!starter && !node.nodeId.startsWith("witness-")) || !node.siteId.contains(":fiction_expansion_project:"))
            return false;
        JsonObject view;
        try {
            view = new JsonParser().parse(node.display)
                .getAsJsonObject();
        } catch (RuntimeException invalid) {
            return true;
        }
        double time = node.getWorldObj() == null ? partial
            : node.getWorldObj()
                .getTotalWorldTime() + partial;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x + .5, y, z + .5);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            box(-.375, 0, -.375, .375, .125, .375, .91F, .91F, .89F);
            box(-.32, .125, -.32, .32, .20, .32, .98F, .98F, .96F);
            box(-.21, .20, -.21, .21, .80, .21, .92F, .92F, .90F);
            box(-.30, .80, -.30, .30, .90, .30, .98F, .98F, .96F);
            box(-.375, .90, -.375, .375, 1, .375, .91F, .91F, .89F);
            if (starter) {
                box(-.28, 1, -.28, .28, 1.06, .28, .18F, .13F, .22F);
                box(-.15, 1.06, -.15, .15, 1.12, .15, .65F, .30F, .88F);
                return true;
            }
            if (!view.has("relicId")) return true;
            Item item = LoreRegistry.RELICS.get(
                view.get("relicId")
                    .getAsString());
            if (item == null) return true;
            ItemStack stack = new ItemStack(item);
            boolean deposited = view.has("deposited") && view.get("deposited")
                .getAsBoolean();
            GL11.glTranslated(0, 1.38 + Math.sin(time * .07) * .055, 0);
            GL11.glRotated(time * 1.2, 0, 1, 0);
            GL11.glScalef(.55F, .55F, .55F);
            GL11.glTranslated(-.5, -.5, 0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01F);
            GL11.glDepthMask(deposited);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            for (int pass = 0; pass < item.getRenderPasses(stack.getItemDamage()); pass++) {
                IIcon icon = item.getIcon(stack, pass);
                if (icon == null) continue;
                int color = item.getColorFromItemStack(stack, pass);
                GL11.glColor4f(
                    (color >> 16 & 255) / 255F,
                    (color >> 8 & 255) / 255F,
                    (color & 255) / 255F,
                    itemAlpha(deposited));
                ItemRenderer.renderItemIn2D(
                    Tessellator.instance,
                    icon.getMaxU(),
                    icon.getMinV(),
                    icon.getMinU(),
                    icon.getMaxV(),
                    icon.getIconWidth(),
                    icon.getIconHeight(),
                    .0625F);
            }
        }
        return true;
    }

    private static void box(double x0, double y0, double z0, double x1, double y1, double z1, float r, float g,
        float b) {
        GL11.glColor4f(r, g, b, 1);
        GL11.glBegin(GL11.GL_QUADS);
        face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        GL11.glColor4f(r * .85F, g * .85F, b * .85F, 1);
        face(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        GL11.glColor4f(r * .75F, g * .75F, b * .75F, 1);
        face(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        face(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        GL11.glEnd();
    }

    private static void face(double... v) {
        for (int i = 0; i < v.length; i += 3) GL11.glVertex3d(v[i], v[i + 1], v[i + 2]);
    }
}
