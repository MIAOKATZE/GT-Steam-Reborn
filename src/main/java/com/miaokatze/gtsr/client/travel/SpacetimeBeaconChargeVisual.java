package com.miaokatze.gtsr.client.travel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.CubeRuneParticle;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** At most eight charging players, eight translucent cubes each; cancellation removes the whole visual. */
public final class SpacetimeBeaconChargeVisual {

    private final List<ChargeParticle> active = new ArrayList<>();

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new SpacetimeBeaconChargeVisual());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        active.removeIf(p -> {
            if (p.valid()) return false;
            p.setDead();
            return true;
        });
        if (mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) return;
        for (Object object : mc.theWorld.playerEntities) {
            if (active.size() >= 8) break;
            EntityPlayer player = (EntityPlayer) object;
            if (mc.thePlayer.getDistanceSqToEntity(player) > 32 * 32
                || !SpacetimeBeaconRenderer.charging(player, player.getHeldItem())) continue;
            boolean found = false;
            for (ChargeParticle particle : active) if (particle.player == player) found = true;
            if (!found) {
                ChargeParticle particle = new ChargeParticle(player);
                active.add(particle);
                mc.effectRenderer.addEffect(particle);
            }
        }
    }

    private static final class ChargeParticle extends EntityFX {

        final EntityPlayer player;
        final ItemStack stack;

        ChargeParticle(EntityPlayer player) {
            super(player.worldObj, player.posX, player.posY + 1, player.posZ);
            this.player = player;
            stack = player.getHeldItem();
            particleMaxAge = SpacetimeAnchorBeacon.RECALL_TICKS;
        }

        boolean valid() {
            return !isDead && Minecraft.getMinecraft().theWorld == worldObj
                && SpacetimeBeaconRenderer.charging(player, stack);
        }

        @Override
        public void onUpdate() {
            if (++particleAge >= particleMaxAge || !valid()) setDead();
            setPosition(player.posX, player.posY + 1, player.posZ);
        }

        @Override
        public int getFXLayer() {
            return 0;
        }

        @Override
        public void renderParticle(Tessellator tess, float partialTicks, float rx, float rz, float ry, float rxz,
            float ryz) {
            if (!valid()) return;
            Entity camera = Minecraft.getMinecraft().renderViewEntity;
            if (camera == null) return;
            double progress = Math
                .min(1, (player.getItemInUseDuration() + partialTicks) / SpacetimeAnchorBeacon.RECALL_TICKS);
            double age = player.ticksExisted + partialTicks;
            double yaw = Math
                .toRadians(player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks);
            double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
            double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
            double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
            double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
            double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
            double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
            tess.draw();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(
                    px - cx - Math.sin(yaw) * .55 - Math.cos(yaw) * .28,
                    py - cy + player.getEyeHeight() - .35,
                    pz - cz + Math.cos(yaw) * .55 - Math.sin(yaw) * .28);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glDepthMask(false);
                GL11.glColor4f(.25F, .68F, 1F, (float) (.3 + .5 * progress));
                double radius = .25 - .13 * progress;
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4 + age * (.06 + .16 * progress);
                    CubeRuneParticle.box(
                        Math.cos(angle) * radius,
                        Math.sin(angle) * radius,
                        Math.sin(angle * 2) * .07,
                        .018 + progress * .012);
                }
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
                tess.startDrawingQuads();
            }
        }
    }
}
