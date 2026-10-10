package com.miaokatze.gtsr.client.travel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;

/** Only the beacon receives the moving bound-material sheen and three-second stretch. */
public final class SpacetimeBeaconRenderer implements IItemRenderer {

    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    public static void register() {
        MinecraftForgeClient.registerItemRenderer(
            GTSRItemList.SpacetimeAnchorBeacon.get(1)
                .getItem(),
            new SpacetimeBeaconRenderer());
        SpacetimeBeaconChargeVisual.register();
        BeaconNavigationClient.register();
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION);
    }

    static boolean charging(EntityPlayer player, ItemStack item) {
        return player != null && !player.isDead
            && player.isSneaking()
            && player.getHeldItem() == item
            && player.getItemInUse() == item
            && item != null
            && item.getItem() instanceof SpacetimeAnchorBeacon
            && player.getItemInUseDuration() < SpacetimeAnchorBeacon.RECALL_TICKS;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft mc = Minecraft.getMinecraft();
        IIcon icon = item.getIconIndex();
        if (icon == null) return;
        IIcon needle = ((SpacetimeAnchorBeacon) item.getItem()).needleIcon();
        EntityLivingBase holder = null;
        for (Object object : data) if (object instanceof EntityLivingBase) holder = (EntityLivingBase) object;
        boolean charging = holder instanceof EntityPlayer && charging((EntityPlayer) holder, item);
        float progress = charging
            ? Math.min(1F, ((EntityPlayer) holder).getItemInUseDuration() / (float) SpacetimeAnchorBeacon.RECALL_TICKS)
            : 0F;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (type == ItemRenderType.INVENTORY) {
                GL11.glScalef(16, 16, 1);
            } else {
                if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-.5F, -.5F, 0);
                GL11.glScalef(1, 1 + .4F * progress, 1);
            }
            mc.getTextureManager()
                .bindTexture(TextureMap.locationItemsTexture);
            GL11.glColor4f(1, 1, 1, 1);
            geometry(
                type,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                icon.getIconWidth(),
                icon.getIconHeight());
            if (needle != null) {
                GL11.glPushMatrix();
                try {
                    // Rotate only the transparent pointer about the dial center, never the casing.
                    GL11.glTranslatef(.5F, .5F, type == ItemRenderType.INVENTORY ? -.002F : .002F);
                    GL11.glRotatef(
                        (type == ItemRenderType.INVENTORY ? 1 : -1) * BeaconNavigationClient.angle(charging),
                        0,
                        0,
                        1);
                    GL11.glTranslatef(-.5F, -.5F, 0);
                    geometry(
                        type,
                        needle.getMaxU(),
                        needle.getMinV(),
                        needle.getMinU(),
                        needle.getMaxV(),
                        needle.getIconWidth(),
                        needle.getIconHeight());
                } finally {
                    GL11.glPopMatrix();
                }
            }
            if (item.hasEffect(0)) {
                GL11.glDepthMask(false);
                GL11.glDepthFunc(GL11.GL_EQUAL);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                mc.getTextureManager()
                    .bindTexture(GLINT);
                double seconds = Minecraft.getSystemTime() / 1000D;
                for (int pass = 0; pass < 2; pass++) {
                    GL11.glColor4f(.24F + .12F * progress, .38F + .18F * progress, .7F, 1F);
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPushMatrix();
                    try {
                        GL11.glLoadIdentity();
                        GL11.glScalef(.8F, .8F, .8F);
                        GL11.glTranslatef((float) ((seconds * (pass == 0 ? .12 : -.08)) % 1), 0, 0);
                        GL11.glRotatef(pass == 0 ? -50 : 10, 0, 0, 1);
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        geometry(type, 0, 0, 1, 1, 256, 256);
                    } finally {
                        GL11.glMatrixMode(GL11.GL_TEXTURE);
                        GL11.glPopMatrix();
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    }
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private static void geometry(ItemRenderType type, float u1, float v1, float u2, float v2, int width, int height) {
        Tessellator tess = Tessellator.instance;
        if (type != ItemRenderType.INVENTORY) {
            ItemRenderer.renderItemIn2D(tess, u1, v1, u2, v2, width, height, .0625F);
            return;
        }
        // GUI Y points down; preserve the atlas icon orientation.
        tess.startDrawingQuads();
        tess.addVertexWithUV(0, 0, 0, u2, v1);
        tess.addVertexWithUV(0, 1, 0, u2, v2);
        tess.addVertexWithUV(1, 1, 0, u1, v2);
        tess.addVertexWithUV(1, 0, 0, u1, v1);
        tess.draw();
    }
}
