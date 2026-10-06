package com.miaokatze.gtsr.client.weapons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponKind;

import cpw.mods.fml.client.registry.RenderingRegistry;

/** Current Outpost meshes extracted into portable weapons. Lists cache geometry, never animation state. */
public final class PortableWeaponRenderer implements IItemRenderer {

    private static final String[] PARTS = { "body", "barrel", "magazine", "belt", "charging_handle", "cover", "grip",
        "stock", "support_hand" };
    private static final Map<String, IModelCustom> MODELS = new HashMap<>();
    private static final Map<String, Integer> LISTS = new HashMap<>();
    private static final ModelBiped ARM = new ModelBiped();

    public static void register() {
        PortableWeaponRenderer renderer = new PortableWeaponRenderer();
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
        EntityPlayer player = null;
        for (Object value : data) if (value instanceof EntityPlayer) player = (EntityPlayer) value;
        float partial = PortableWeaponClient.renderPartialTicks();
        float reload = player == null ? 0 : PortableWeaponClient.reloadProgress(player, partial);
        float recoil = player == null ? 0 : Math.max(0, 1 - PortableWeaponClient.shotAge(player, partial) / 4F);
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
            } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                float focus = PortableWeaponClient.focusProgress();
                GL11.glTranslatef(.72F - focus * .37F, .20F - reload * .16F, .24F + recoil * .075F);
                GL11.glRotatef(180, 0, 1, 0);
                GL11.glRotatef(-8 + reload * 24, 1, 0, 0);
                GL11.glScalef(.61F, .61F, .61F);
            } else if (type == ItemRenderType.EQUIPPED) {
                GL11.glTranslatef(.58F, .45F, .18F);
                GL11.glRotatef(105, 0, 1, 0);
                GL11.glRotatef(-12, 1, 0, 0);
                GL11.glScalef(.50F, .50F, .50F);
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
                for (String part : PARTS) {
                    GL11.glPushMatrix();
                    try {
                        if ("barrel".equals(part) && kind == WeaponKind.LM12) {
                            float spin = player == null ? 0 : PortableWeaponClient.spin(player, partial);
                            GL11.glRotatef((player == null ? 0 : player.ticksExisted + partial) * spin * 55, 0, 0, 1);
                        }
                        if ("cover".equals(part) && kind != WeaponKind.QLZ04) {
                            GL11.glTranslatef(0, .125F, .68F);
                            GL11.glRotatef((float) Math.sin(reload * Math.PI) * -75, 1, 0, 0);
                            GL11.glTranslatef(0, -.125F, -.68F);
                        }
                        if ("belt".equals(part)) GL11.glTranslatef((float) Math.sin(reload * Math.PI) * .35F, 0, 0);
                        if ("magazine".equals(part) && kind == WeaponKind.QLZ04)
                            GL11.glTranslatef(0, -(float) Math.sin(reload * Math.PI) * .7F, 0);
                        if ("charging_handle".equals(part)) GL11.glTranslatef(
                            0,
                            0,
                            reload > .7F ? (float) Math.sin((reload - .7F) / .3F * Math.PI) * .18F : 0);
                        part(kind.modelKey, part);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && reload > 0
                    && player instanceof AbstractClientPlayer)
                    renderLoadingArm((AbstractClientPlayer) player, kind, reload);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
        }
    }

    private static void renderLoadingArm(AbstractClientPlayer player, WeaponKind kind, float progress) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(player.getLocationSkin());
        GL11.glPushMatrix();
        try {
            float reach = (float) Math.sin(progress * Math.PI);
            GL11.glTranslatef(.27F + reach * .15F, -.20F - (kind == WeaponKind.QLZ04 ? reach * .4F : 0), .30F);
            GL11.glRotatef(-95, 1, 0, 0);
            GL11.glRotatef(-25 + reach * 35, 0, 0, 1);
            ARM.bipedLeftArm.rotateAngleX = ARM.bipedLeftArm.rotateAngleY = ARM.bipedLeftArm.rotateAngleZ = 0;
            ARM.bipedLeftArm.render(.045F);
        } finally {
            GL11.glPopMatrix();
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
