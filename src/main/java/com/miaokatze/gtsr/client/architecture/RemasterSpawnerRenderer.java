package com.miaokatze.gtsr.client.architecture;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.client.encounter.FineRuneRenderer;
import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSpawnerContract;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Chest calligraphy uses the synchronized quota: more tier glyphs, fewer glyphs after each spawn. */
@SideOnly(Side.CLIENT)
public final class RemasterSpawnerRenderer extends TileEntitySpecialRenderer {

    public static void registerRenderer() {
        ClientRegistry.bindTileEntitySpecialRenderer(TileRemasterNode.class, new RemasterSpawnerRenderer());
    }

    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partial) {
        TileRemasterNode node = (TileRemasterNode) tile;
        if (com.miaokatze.gtsr.client.lore.FictionPedestalRenderer.render(node, x, y, z, partial)) return;
        if (!"spawner".equals(node.role) || node.getWorldObj() == null) return;
        JsonObject view;
        try {
            view = new JsonParser().parse(node.display)
                .getAsJsonObject();
        } catch (RuntimeException invalid) {
            return;
        }
        if (!view.has("tier")) return;
        int tier = Math.max(1, Math.min(3, (int) number(view, "tier", 1)));
        boolean spent = view.has("spent") && view.get("spent")
            .getAsBoolean();
        double time = node.getWorldObj()
            .getTotalWorldTime() + partial;
        double unlock = spent ? Math.max(0, Math.min(1, (time - number(view, "unlockAt", time)) / 40D)) : 0;
        double pulse = Math.max(0, Math.min(1, 1 - (time - number(view, "pulseAt", time - 24)) / 24D));
        int count = spent ? 8 : RemasterSpawnerContract.runes(tier, (int) number(view, "count", 0));
        float r = spent ? 1 : tier == 3 ? .72F : 1, g = spent ? .85F : tier == 1 ? .45F : .12F,
            b = spent ? .14F : tier == 3 ? 1 : .08F;
        double radius = .76 + pulse * .10 + Math.sin(unlock * Math.PI) * .45;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(x + .5, y + .56, z + .5);
            FineRuneRenderer.orbit(radius, time, count, r, g, b, .84F, pulse);
            if (spent && unlock > 0 && unlock < 1) {
                GL11.glColor4f(r, g, b, (float) ((1 - unlock) * .6));
                FineRuneRenderer.arc(.6 + unlock * 1.4, .012, -.32, time, 1, 1);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + time * .02;
                    FineRuneRenderer.voxel(
                        Math.cos(a) * radius,
                        unlock * .45,
                        Math.sin(a) * radius,
                        .032,
                        r,
                        g,
                        b,
                        (float) (.5 * Math.sin(unlock * Math.PI)),
                        i % 3 == 0);
                }
            }
        }
    }

    private static double number(JsonObject view, String key, double fallback) {
        return view.has(key) && !view.get(key)
            .isJsonNull() ? view.get(key)
                .getAsDouble() : fallback;
    }
}
