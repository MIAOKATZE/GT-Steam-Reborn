package com.miaokatze.gtsr.client.encounter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** One local bar owner, stable identity frames, with 100-HP layers and no vanilla BossStatus mutation. */
public final class EchoBossOverlay extends Gui {

    private static final int[] metals = { 0xC98556, 0xD7B674, 0x6CA991, 0x799FBB, 0xAF94B6 };
    private static final int[] PALETTES = { 0xB57456, 0x84B49B, 0xB9ADC8, 0xC9BD80, 0x68A6C1, 0xCD824D, 0x84A362 };

    private static int layerColor(int base, int layers) {
        // Steam-metal palette shifts between copper, amber, patina and steel for each hundred HP.

        int metal = metals[Math.max(0, layers - 1) % metals.length];
        int r = (((base >> 16) & 255) + ((metal >> 16) & 255)) / 2;
        int g = (((base >> 8) & 255) + ((metal >> 8) & 255)) / 2;
        int b = ((base & 255) + (metal & 255)) / 2;
        return (r << 16) | (g << 8) | b;
    }

    public void drawScene(RenderGameOverlayEvent.Post event,
        com.miaokatze.gtsr.common.dimension.prosperity.encounter.SceneBossSignal scene) {
        Minecraft mc = Minecraft.getMinecraft();
        int layers = scene.layers(event.partialTicks);
        float segment = scene.segment(event.partialTicks);
        int x = (event.resolution.getScaledWidth() - 256) / 2;
        int y = BossStatus.statusBarTime > 0 ? 42 : 17;
        int color = scene.kind == 1 ? EncounterClient.kingLayerColor(layers)
            : layerColor(PALETTES[scene.kind == 2 ? 6 : 5], layers);
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawRect(x + 32, y + 13, x + 224, y + 23, 0xFF151918);
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 22, 0xFF000000 | color);
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 16, 0x66FFFFFF);
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(
                    new ResourceLocation(
                        "gtsr",
                        "textures/gui/" + (scene.kind == 1 ? "silent_king_frame" : scene.code + "_boss_frame")
                            + ".png"));
            func_152125_a(x, y, 0, 0, 256, 40, 256, 40, 256, 40);
            String name = scene.kind == 1 ? "巨树王庭 · 缄王" : scene.kind == 2 ? "巢识沉降工厂 · 巢识" : "崩垣铸造战场 · 崩垣";
            String count = "×" + layers;
            int width = mc.fontRenderer.getStringWidth(count);
            int cx = Math.max(4, Math.min(x + 260, event.resolution.getScaledWidth() - width - 4));
            drawRect(cx - 2, y + 12, cx + width + 2, y + 24, 0xCC151918);
            mc.fontRenderer.drawStringWithShadow(count, cx, y + 14, 0xE9D7AD);
            name = "§l" + name + "§r";
            mc.fontRenderer.drawStringWithShadow(
                name,
                (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(name)) / 2,
                y - 9,
                0xE9D7AD);
            String caption = scene.state == 3 ? "已完成"
                : scene.state == 1 ? "正在复苏 · " + scene.revivalTicks + "/" + scene.revivalDuration()
                    : scene.state == 2 ? "战斗中"
                        : scene.kind == 1 ? scene.remaining > 0 ? "休眠 · 剩余守卫 " + scene.remaining + "/" + scene.total
                            : "守卫已肃清 · 靠近王座唤醒缄王" : "休眠 · 待拆毁刷怪笼 " + scene.remaining + "/" + scene.total;
            if (scene.state == 2 && mc.theWorld != null) {
                net.minecraft.entity.Entity candidate = mc.theWorld.getEntityByID(scene.entity);
                if (candidate instanceof EntityOldEcho) {
                    EntityOldEcho echo = (EntityOldEcho) candidate;
                    if (scene.site.equals(echo.getEncounterId()) && echo.getSkillId() != 0)
                        caption = net.minecraft.util.StatCollector
                            .translateToLocal("echo.warning." + echo.getKind().code);
                }
            }
            caption = "§l" + caption + "§r";
            BossSkillVisuals.draw(
                mc.theWorld == null ? null : mc.theWorld.getEntityByID(scene.entity),
                event.resolution.getScaledWidth(),
                x,
                y);
            mc.fontRenderer.drawStringWithShadow(
                caption,
                (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(caption)) / 2,
                y + 31,
                0xCCA977);
        }
    }

    public static EntityOldEcho nearest(Minecraft mc) {
        EntityOldEcho nearest = null;
        double distance = 64 * 64;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityOldEcho)) continue;
            EntityOldEcho echo = (EntityOldEcho) object;
            if (echo.getEncounterId()
                .startsWith("echo:r7:")) continue;
            double d = mc.thePlayer.getDistanceSqToEntity(echo);
            if (echo.getKind()
                .hasBossBar() && echo.isEntityAlive()
                && echo.getEncounterState() == EntityOldEcho.COMBAT
                && d < distance) {
                distance = d;
                nearest = echo;
            }
        }
        return nearest;
    }

    public void draw(RenderGameOverlayEvent.Post event, EntityOldEcho echo) {
        Minecraft mc = Minecraft.getMinecraft();
        float health = Math.max(0, Math.min(echo.getKind().maxHealth, echo.getHealth()));
        int layers = (int) Math.ceil(health / 100F);
        float segment = layers == 0 ? 0 : (health - (layers - 1) * 100) / 100F;
        int x = (event.resolution.getScaledWidth() - 256) / 2;
        int y = BossStatus.statusBarTime > 0 ? 42 : 17;
        int color = layerColor(EchoBossStyle.palette(echo.getKind().code), layers);
        try (GlScope scope = new GlScope()) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawRect(x + 32, y + 13, x + 224, y + 23, 0xFF151918);
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 22, 0xFF000000 | color);
            drawRect(x + 33, y + 14, x + 33 + Math.round(190 * segment), y + 16, 0x66FFFFFF);
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(EchoBossStyle.frame(echo.getKind().code));
            func_152125_a(x, y, 0, 0, 256, 40, 256, 40, 256, 40);
            String name = "(" + net.minecraft.util.StatCollector.translateToLocal("echo.name.prefix")
                + ")"
                + net.minecraft.util.StatCollector.translateToLocal("echo.name." + echo.getKind().code),
                count = "×" + layers;
            int countWidth = mc.fontRenderer.getStringWidth(count);
            int cx = Math.max(4, Math.min(x + 260, event.resolution.getScaledWidth() - countWidth - 4));
            drawRect(cx - 2, y + 12, cx + countWidth + 2, y + 24, 0xCC151918);
            mc.fontRenderer.drawStringWithShadow(count, cx, y + 14, 0xE9D7AD);
            name = "§l" + name + "§r";
            mc.fontRenderer.drawStringWithShadow(
                name,
                (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(name)) / 2,
                y - 9,
                0xE9D7AD);
            BossSkillVisuals.draw(echo, event.resolution.getScaledWidth(), x, y);
            if (echo.getSkillId() != 0) {
                String caption = net.minecraft.util.StatCollector
                    .translateToLocal("echo.warning." + echo.getKind().code);
                caption = "§l" + caption + "§r";
                mc.fontRenderer.drawStringWithShadow(
                    caption,
                    (event.resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(caption)) / 2,
                    y + 31,
                    0xCCA977);
            }
        }
    }
}
