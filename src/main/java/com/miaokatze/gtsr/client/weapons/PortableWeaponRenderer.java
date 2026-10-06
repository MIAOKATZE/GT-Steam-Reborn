package com.miaokatze.gtsr.client.weapons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.Snapshot;
import com.miaokatze.gtsr.common.weapons.WeaponKind;
import com.miaokatze.gtsr.common.weapons.WeaponPose;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Current Outpost meshes extracted into portable weapons. Lists cache geometry, never animation state. */
public final class PortableWeaponRenderer implements IItemRenderer {

    private static final String[] PARTS = { "body", "barrel", "magazine", "belt", "charging_handle", "cover", "grip",
        "stock", "support_hand" };
    private static final Map<String, IModelCustom> MODELS = new HashMap<>();
    private static final Map<String, Integer> LISTS = new HashMap<>();

    public static void register() {
        PortableWeaponRenderer renderer = new PortableWeaponRenderer();
        PortableWeaponPlayerPose.register();
        for (WeaponKind kind : WeaponKind.values()) {
            MinecraftForgeClient.registerItemRenderer(PortableWeapons.weapons[kind.id], renderer);
            MinecraftForgeClient.registerItemRenderer(PortableWeapons.ammo[kind.id], renderer);
        }
        RenderingRegistry
            .registerEntityRenderingHandler(EntityWeaponProjectile.class, new PortableProjectileRenderer());
    }

    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        return PortableWeapons.kind(stack) != null || PortableWeapons.ammoKind(stack) != null;
    }

    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_BOBBING || helper == ItemRendererHelper.ENTITY_ROTATION);
    }

    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        WeaponKind kind = PortableWeapons.kind(stack);
        boolean ammo = kind == null;
        if (ammo) kind = PortableWeapons.ammoKind(stack);
        if (kind == null) return;
        // Held guns are drawn in shared world coordinates, independent of native equip/use/bobbing matrices.
        if (!ammo && (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON)) return;
        EntityPlayer player = null;
        for (Object value : data) if (value instanceof EntityPlayer) player = (EntityPlayer) value;
        float partial = PortableWeaponClient.renderPartialTicks();
        float reload = player == null ? 0 : PortableWeaponClient.reloadProgress(player, partial);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            if (type == ItemRenderType.INVENTORY) {
                GL11.glTranslatef(8, 8, 0);
                GL11.glScalef(5.7F, -5.7F, 5.7F);
                GL11.glRotatef(65, 0, 1, 0);
                GL11.glRotatef(-15, 1, 0, 0);
                GL11.glTranslatef(0, .05F, -.07F);
            } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.EQUIPPED) {
                // Ammo packs retain native item placement; held weapons never enter this branch.
                GL11.glTranslatef(.55F, .4F, .2F);
                GL11.glScalef(.7F, .7F, .7F);
            } else {
                GL11.glScalef(.35F, .35F, .35F);
                GL11.glTranslatef(0, .22F, 0);
            }
            bind(kind.modelKey);
            if (ammo) {
                GL11.glScalef(2, 2, 2);
                GL11.glTranslatef(kind == WeaponKind.QLZ04 ? -.27F : -.31F, .23F, -.23F);
                part(kind.modelKey, "magazine");
            } else {
                drawWeapon(kind, player, partial, reload);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
        }
    }

    static void renderHeld(EntityPlayer player, WeaponKind kind, float partial, boolean firstPerson) {
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            Vec3 origin = WeaponPose.origin(player, partial);
            GL11.glTranslated(
                origin.xCoord - RenderManager.renderPosX,
                origin.yCoord - RenderManager.renderPosY,
                origin.zCoord - RenderManager.renderPosZ);
            GL11.glRotatef(180 - WeaponPose.yaw(player, partial), 0, 1, 0);
            GL11.glRotatef(-WeaponPose.pitch(player, partial), 1, 0, 0);
            GL11.glScaled(WeaponPose.SCALE, WeaponPose.SCALE, WeaponPose.SCALE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1, 1, 1, 1);
            bind(kind.modelKey);
            drawWeapon(kind, player, partial, PortableWeaponClient.reloadProgress(player, partial));
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
        }
        if (firstPerson && player instanceof AbstractClientPlayer)
            PortableWeaponPlayerPose.renderArms((AbstractClientPlayer) player, kind, partial);
    }

    private static void drawWeapon(WeaponKind kind, EntityPlayer player, float partial, float reload) {
        for (String part : PARTS) {
            GL11.glPushMatrix();
            try {
                if ("barrel".equals(part) && kind == WeaponKind.LM12) {
                    // Real imported spin axis has a small Y offset; its muzzle centroid must remain fixed.
                    GL11.glTranslatef(0, -.0006249854F, 0);
                    GL11.glRotatef(player == null ? 0 : PortableWeaponClient.spinAngle(player, partial), 0, 0, 1);
                    GL11.glTranslatef(0, .0006249854F, 0);
                }
                if ("cover".equals(part) && kind != WeaponKind.QLZ04) {
                    GL11.glTranslatef(0, .125F, .68F);
                    GL11.glRotatef((float) Math.sin(reload * Math.PI) * -75, 1, 0, 0);
                    GL11.glTranslatef(0, -.125F, -.68F);
                }
                if ("belt".equals(part)) GL11.glTranslatef((float) Math.sin(reload * Math.PI) * .35F, 0, 0);
                if ("magazine".equals(part) && kind == WeaponKind.QLZ04) {
                    float reach = (float) Math.sin(reload * Math.PI);
                    // Bring the extracted clip into the first-person view instead of below its frustum.
                    GL11.glTranslatef(-.6F * reach, .35F * reach, -.7F * reach);
                }
                if ("charging_handle".equals(part))
                    GL11.glTranslatef(0, 0, reload > .7F ? (float) Math.sin((reload - .7F) / .3F * Math.PI) * .18F : 0);
                part(kind.modelKey, part);
                if ("barrel".equals(part) && player != null) renderHeat(kind, player);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private static void renderHeat(WeaponKind kind, EntityPlayer player) {
        Snapshot snapshot = PortableWeaponClient.snapshot(player);
        float h = snapshot == null ? 0 : Math.max(0, Math.min(1, snapshot.heat));
        if (h <= 0) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(new ResourceLocation("gtsr", "textures/weapons/turret_heat_ramp.png"));
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
            GL11.glColor4f(.65F * h, .65F * .18F * h, .65F * .05F * h, 1);
            part(kind.modelKey + "_heat", "barrel");
        } finally {
            GL11.glPopAttrib();
        }
    }

    static void bind(String key) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(new ResourceLocation("gtsr", "textures/weapons/" + key + ".png"));
    }

    static void part(String key, String group) {
        String cacheKey = key + ":" + group;
        Integer list = LISTS.get(cacheKey);
        if (list == null) {
            IModelCustom model = MODELS.get(key);
            if (model == null) {
                model = AdvancedModelLoader.loadModel(new ResourceLocation("gtsr", "models/weapons/" + key + ".obj"));
                MODELS.put(key, model);
            }
            list = GL11.glGenLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            try {
                if ("all".equals(group)) model.renderAll();
                else model.renderOnly(group);
            } finally {
                GL11.glEndList();
            }
            LISTS.put(cacheKey, list);
        }
        GL11.glCallList(list);
    }
}
