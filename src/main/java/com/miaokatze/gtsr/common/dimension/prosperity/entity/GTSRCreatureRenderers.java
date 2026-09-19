package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBat;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster.Species;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * 三档实体的渲染接线（<b>client only</b>；由 {@code CommonProxy.preInit} 的
 * {@code event.side.isClient()} 分支调用，专用服永不加载本类）。
 * <p>
 * ═══ 为什么"复用原版"必须自己写一层薄 {@link RenderLiving} ═══
 * 最省事的写法是直接把原版 {@code RenderChicken}/{@code RenderBat} 塞进注册表——但那正是本风险
 * 最高的一片会踩的坑：原版具体渲染器的桥接方法会<b>向下转型</b>到自己的实体类
 * （{@code RenderLivingEntity.func_77038_a(EntityLiving)} → {@code (EntityChicken)p_77038_1_}），
 * 传进我们的实体就是 {@code ClassCastException}；而 GTNH 的 {@code RendererLivingEntity}
 * <b>吞异常</b>，于是失败形态不是崩溃而是"隐形实体"（plan §5 P9 风险条）。
 * 所以这里只继承通用的 {@link RenderLiving}（其 {@code doRender} 链最细只到
 * {@code EntityLiving}，我们三档都在这条链上），<b>复用</b>原版的 {@link ModelBase} 实例与
 * 原版纹理 ⇒ 仍然零新素材，但把"隐形实体"的成因从机制上排掉。
 * <p>
 * ═══ 纹理纪律 ═══
 * 三张纹理都写 {@link Species#vanillaTexturePath()} 那一个路径常量（名册是唯一申报处），
 * 且 {@code tools/dim1/CreatureSpawnAuthorityCheck} 判据 4 会对每个路径做
 * {@code ClassLoader.getResource("assets/minecraft/<path>")} 的<b>实际可解析</b>断言——
 * "注册了渲染器"不等于"贴图找得到"，而后者才是隐形实体的另一半成因。
 */
public final class GTSRCreatureRenderers {

    private GTSRCreatureRenderers() {}

    /**
     * 注册三档渲染器（preInit，client）。
     * <p>
     * {@code FML 的 RenderingRegistry} 只是在 preInit 期把 {@code (Class, Render)} 收进一张表、
     * 由 {@code RenderManager} 在后期装载，故本方法必须在 {@code EntityRegistry.registerModEntity}
     * 之后调用（同一 preInit 内，见 {@code CommonProxy} 的新增调用顺序）。
     */
    public static void registerAll() {
        for (final Species species : Species.values()) {
            RenderingRegistry.registerEntityRenderingHandler(species.entityClass(), newCreatureRenderer(species));
        }
        GTSteamReborn.LOG.info(
            "[GTSR] creature renderers registered: {} handlers (vanilla models, zero new assets)",
            Species.values().length);
    }

    /** 名册档 → 渲染器实例（模型与纹理都取自原版；判据 4 用同一个入口，避免两处清单漂移）。 */
    public static RenderLiving newCreatureRenderer(Species species) {
        final ResourceLocation texture = textureOf(species);
        switch (species) {
            case GEAR_PIGEON: {
                return new RenderLivingVanillaModel(new ModelChicken(), 0.5F, texture);
            }
            case STEAM_FIREFLY: {
                // 原版蝙蝠的 0.35 缩放档（RenderBat.preRenderCallback 实测同值）；不缩会画成门板大
                return new RenderScaledVanillaModel(new ModelBat(), 0.25F, texture, 0.35F);
            }
            default: {
                return new RenderLivingVanillaModel(new ModelSkeleton(), 0.5F, texture);
            }
        }
    }

    /** 名册申报的原版纹理路径 → {@link ResourceLocation}（domain 恒 {@code minecraft}）。 */
    public static ResourceLocation textureOf(Species species) {
        return new ResourceLocation(species.vanillaTexturePath());
    }

    /** 无额外 GL 状态改动的通用渲染器（鸽 / 猎手）。 */
    static class RenderLivingVanillaModel extends RenderLiving {

        private final ResourceLocation texture;

        RenderLivingVanillaModel(ModelBase model, float shadowSize, ResourceLocation texture) {
            super(model, shadowSize);
            this.texture = texture;
        }

        @Override
        protected ResourceLocation getEntityTexture(Entity entity) {
            return this.texture;
        }
    }

    /** 带固定缩放的通用渲染器（萤；只覆写 {@link #preRenderCallback}，不覆写任何转型入口）。 */
    static class RenderScaledVanillaModel extends RenderLivingVanillaModel {

        private final float scale;

        RenderScaledVanillaModel(ModelBase model, float shadowSize, ResourceLocation texture, float scale) {
            super(model, shadowSize, texture);
            this.scale = scale;
        }

        @Override
        protected void preRenderCallback(EntityLivingBase entity, float partialTickTime) {
            // 本 MC 映射无 GlStateManager（1.8+ 才有），与原版 RenderBat 一样直调 GL11（同仓
            // client/gui/terminal/GtsrGuiDrawing 的口径）
            GL11.glScalef(this.scale, this.scale, this.scale);
        }
    }
}
