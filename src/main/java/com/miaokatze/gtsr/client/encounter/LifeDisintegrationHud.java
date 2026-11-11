// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.encounter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.LifeDisintegrationPotion;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Swap only the HEALTH atlas; Forge retains all heart layout, absorption, blinking and status logic. */
@SideOnly(Side.CLIENT)
public final class LifeDisintegrationHud implements IResourceManagerReloadListener {

    private static final ResourceLocation CRACKED_ICONS = new ResourceLocation("gtsr", "dynamic/life_disintegration");
    private DynamicTexture atlas;
    private boolean failed;
    private boolean bound;

    private LifeDisintegrationHud() {}

    public static void register() {
        LifeDisintegrationHud hud = new LifeDisintegrationHud();
        MinecraftForge.EVENT_BUS.register(hud);
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(hud);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void beforeHealth(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HEALTH) return;
        Minecraft mc = Minecraft.getMinecraft();
        LifeDisintegrationPotion potion = LifeDisintegrationPotion.instance();
        if (potion == null || mc.thePlayer == null || !mc.thePlayer.isPotionActive(potion.id)) return;
        if (atlas == null && !failed) loadAtlas(mc);
        if (atlas != null) {
            // GuiIngameForge binds icons immediately before its HEALTH Pre event.
            mc.getTextureManager()
                .bindTexture(CRACKED_ICONS);
            bound = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterHealth(RenderGameOverlayEvent.Post event) {
        if (bound && (event.type == RenderGameOverlayEvent.ElementType.HEALTH
            || event.type == RenderGameOverlayEvent.ElementType.ALL)) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(Gui.icons);
            bound = false;
        }
    }

    private void loadAtlas(Minecraft mc) {
        try (InputStream stream = mc.getResourceManager()
            .getResource(Gui.icons)
            .getInputStream()) {
            BufferedImage original = ImageIO.read(stream);
            if (original == null) throw new IOException("Unreadable GUI icons image");
            atlas = new DynamicTexture(crackHearts(original));
            mc.getTextureManager()
                .loadTexture(CRACKED_ICONS, atlas);
        } catch (IOException exception) {
            failed = true;
            GTSteamReborn.LOG.warn("Could not prepare life disintegration heart icons", exception);
        }
    }

    /** Pure pixel transform; preserve the active resource pack and every pixel outside the heart cells. */
    public static BufferedImage crackHearts(BufferedImage original) {
        int width = original.getWidth(), height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, original.getRGB(0, 0, width, height, null, 0, width), 0, width);
        // GUI coordinates use a 256px atlas even when the resource pack supplies a higher resolution.
        int scaleX = Math.max(1, width / 256), scaleY = Math.max(1, height / 256);
        int[] fissure = { 4, 4, 3, 4, 4, 3, 4 };
        for (int top : new int[] { 0, 45 }) {
            for (int cell = 0; cell < 18; cell++) {
                for (int row = 0; row < fissure.length; row++) {
                    for (int dy = 0; dy < scaleY; dy++) {
                        for (int dx = 0; dx < scaleX; dx++) {
                            int x = (16 + cell * 9 + fissure[row]) * scaleX + dx;
                            int y = (top + row + 1) * scaleY + dy;
                            if (x >= width || y >= height) continue;
                            int color = original.getRGB(x, y);
                            // Retain transparency of empty/half hearts and draw a dark, jagged fracture inside.
                            if ((color >>> 24) != 0) result.setRGB(x, y, (color & 0xFF000000) | 0x170911);
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        if (atlas != null) Minecraft.getMinecraft()
            .getTextureManager()
            .deleteTexture(CRACKED_ICONS);
        atlas = null;
        failed = false;
        bound = false;
    }
}
