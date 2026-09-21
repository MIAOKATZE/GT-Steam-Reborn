package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.model.GTSRModelSlagRidgeHunter;
import com.miaokatze.gtsr.client.model.GTSRModelSteamFirefly;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster.Species;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * 三档实体的渲染接线（<b>client only</b>；由 {@code CommonProxy.preInit} 的
 * {@code event.side.isClient()} 分支调用，专用服永不加载本类）。
 * <p>
 * ═══ 为什么要自己写一层薄 {@link RenderLiving} <b>并且</b>自己写模型 ═══
 * 这里有两层独立的下行转型，v1.20.35 只排掉了第一层：
 * <ol>
 * <li><b>渲染器层</b>：原版具体渲染器的桥接方法会转成自己的实体类
 * （{@code RenderBat.java:97,106,111,119,130,138,149} 全在转 {@code EntityBat}）⇒ 故只继承通用的
 * {@link RenderLiving}（其 {@code doRender} 链最细只到 {@code EntityLiving}）；</li>
 * <li><b>模型层（D2 补的两处崩溃）</b>：换了通用渲染器不等于安全——原版模型自己在每帧热方法里转型。
 * {@code ModelBat.java:73} 是 {@code render()} 第一句无条件 {@code (EntityBat)}（C-01），
 * {@code ModelSkeleton.java:44} 是 {@code setLivingAnimations()} 第一句无条件 {@code (EntitySkeleton)}
 * （C-02，每帧由 {@code RendererLivingEntity.java:164} 调）。异常<b>不被吞</b>：
 * 1.7.10 没有 {@code entityRenderErrors} 限流器，{@code RenderManager.java:300-304} 直接把
 * {@code doRender} 的 Throwable 包成 {@code ReportedException} ⇒ 表现是"进入视野即崩客户端"，
 * 不是旧记录里写的"隐形实体"（该记载已证伪）。⇒ 萤与猎手改挂<b>我方</b>零转型模型
 * {@link GTSRModelSteamFirefly} / {@link GTSRModelSlagRidgeHunter}（几何与 UV 逐箱照抄原版，
 * 画布仍 64×64 与 64×32 ⇒ D1 换自有皮肤时 UV 一个像素都不用动）；鸽挂 {@code ModelChicken}——实测全类零
 * {@code (Entity…)} 强转，故不在此列。</li>
 * </ol>
 * 两层现在都由 {@code tools/dim1/CreatureSpawnAuthorityCheck} 的 D 组（渲染器类名前缀）与
 * <b>I1 组（模型继承链上三个每帧热方法的外类强转）</b>分别钉住。
 * <p>
 * ═══ 纹理纪律 ═══
 * 三张皮肤都只写 {@link Species#skinTexturePath()} 那一个路径常量（名册是唯一申报处，
 * 本类不出现第二个路径字面量），装载点就是下面的 {@link #textureOf(Species)} →
 * {@code new ResourceLocation("gtsr:textures/entity/<档>.png")}，由 {@code Render#bindEntityTexture}
 * 直绑单图（1.7.10 实体贴图不入方块图集，{@code Render.java:53-61}）。
 * {@code tools/dim1/CreatureSpawnAuthorityCheck} 判据 4 的 D 组会对每个路径做
 * {@code ClassLoader.getResource("assets/<domain>/<path>")} 的<b>实际可解析</b>断言（自有皮肤不在
 * 离线 classpath 上时回落到 {@code src/main/resources/} 仓库路径，并打印用了哪一条通道），
 * 并由 I2 组把<b>贴图 IHDR 尺寸 == 模型申报画布</b>钉住——"注册了渲染器"不等于"贴图找得到"，
 * 更不等于"UV 不错位"。D1 前借的是 {@code minecraft} 域的 bat/chicken/skeleton 三张皮，
 * D1 起三档全部走 {@code gtsr} 域自有皮肤（画法档案：{@code tools/artgen/entity/manifest.json}）。
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
            "[GTSR] creature renderers registered: {} handlers, own skins under assets/gtsr/textures/entity/",
            Integer.valueOf(Species.values().length));
    }

    /** 名册档 → 渲染器实例（模型按申报的 {@code modelClassName()} 取；纹理路径也只在名册申报一次）。 */
    public static RenderLiving newCreatureRenderer(Species species) {
        final ResourceLocation texture = textureOf(species);
        switch (species) {
            case GEAR_PIGEON: {
                // ModelChicken 经 I1 组实测：三个每帧热方法零 (Entity…) 强转 ⇒ 可直接复用
                return new RenderLivingVanillaModel(new ModelChicken(), 0.5F, texture);
            }
            case STEAM_FIREFLY: {
                // 原版蝙蝠的 0.35 缩放档（RenderBat.preRenderCallback 实测同值）；不缩会画成门板大。
                // 模型必须是我方复刻件：ModelBat.java:73 无条件 (EntityBat) ⇒ 上屏即崩（C-01）
                return new RenderScaledVanillaModel(new GTSRModelSteamFirefly(), 0.25F, texture, 0.35F);
            }
            default: {
                // 模型必须是我方复刻件：ModelSkeleton.java:44 无条件 (EntitySkeleton) ⇒ 上屏即崩（C-02）
                return new RenderLivingVanillaModel(new GTSRModelSlagRidgeHunter(), 0.5F, texture);
            }
        }
    }

    /** 名册申报的自有皮肤路径 → {@link ResourceLocation}（D1 起 domain 恒 {@code gtsr}）。 */
    public static ResourceLocation textureOf(Species species) {
        return new ResourceLocation(species.skinTexturePath());
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
