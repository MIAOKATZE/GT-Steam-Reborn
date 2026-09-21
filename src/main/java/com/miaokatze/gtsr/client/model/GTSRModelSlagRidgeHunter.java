package com.miaokatze.gtsr.client.model;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelZombie;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 渣脊猎手模型（<b>D2 · 修 C-02「上屏即崩」</b>）。
 * <p>
 * ═══ 为什么不挂原版 {@code ModelSkeleton} ═══
 * {@code build/rfg/minecraft-src/java/net/minecraft/client/model/ModelSkeleton.java:44} 是
 * {@code setLivingAnimations()} 的第一句、<b>无条件</b>
 * {@code this.aimedBow = ((EntitySkeleton)p_78086_1_).getSkeletonType() == 1;}，
 * 而该方法每帧由 {@code RendererLivingEntity.java:164} 调用 ⇒ 实体不是 {@code EntitySkeleton}
 * 就每帧 {@code ClassCastException}；1.7.10 的 {@code RenderManager.java:300-304} 不吞异常
 * ⇒ "看见即崩客户端"（与 C-01 同形，第二处）。旧离线判据 4 只看模型 SimpleName 是否等于申报值 ⇒ 漏网，
 * 现由 {@code CreatureSpawnAuthorityCheck} 的 I1 组沿继承链钉住。
 * <p>
 * ═══ 形状与画布 ═══
 * 本类<b>只</b>做 {@code ModelSkeleton} 做过的事：继承 {@link ModelZombie}（⇒ 走的是与骷髅同一条
 * {@code setRotationAngles} 手臂姿态），按 {@code ModelSkeleton.java:22-35} 逐箱把四肢换成细骨尺寸、
 * 保持同一 UV（臂 {@code (40,16)}、腿 {@code (0,16)}、左半 {@code mirror = true}）与同一
 * {@code super(offset, 0.0F, 64, 32)} ⇒ <b>画布口径钉死 64×32</b>，与 {@code ModelSkeleton.java:21}
 * 完全一致；复刻过渡期曾借原版 {@code textures/entity/skeleton/skeleton.png}，P16-D1 起改吃自有
 * {@code gtsr:textures/entity/slag-ridge-hunter.png}（<b>自有皮肤画布按 64×32 画</b>，由同工具 I2 按贴图 IHDR 实测核对）。
 * <p>
 * <b>与原版骷髅的唯一差异</b>：{@code aimedBow} 恒 false——那行赋值正是崩因本身，且猎手是近战档
 * （{@code EntitySlagRidgeHunter} 无远程 AI、无持弓，清单 C-10），拉弓臂姿本来就用不到。
 */
@SideOnly(Side.CLIENT)
public class GTSRModelSlagRidgeHunter extends ModelZombie {

    public GTSRModelSlagRidgeHunter() {
        this(0.0F);
    }

    /**
     * @param extraOffset 传给 {@link ModelZombie} 的箱体外扩（原版 {@code ModelSkeleton(float)} 同参数；
     *                    现接线用 0.0F，与 {@code GTSRCreatureRenderers} 申报的模型名同处一改点）
     */
    public GTSRModelSlagRidgeHunter(float extraOffset) {
        super(extraOffset, 0.0F, 64, 32);
        this.bipedRightArm = new ModelRenderer(this, 40, 16);
        this.bipedRightArm.addBox(-1.0F, -2.0F, -1.0F, 2, 12, 2, extraOffset);
        this.bipedRightArm.setRotationPoint(-5.0F, 2.0F, 0.0F);
        this.bipedLeftArm = new ModelRenderer(this, 40, 16);
        this.bipedLeftArm.mirror = true;
        this.bipedLeftArm.addBox(-1.0F, -2.0F, -1.0F, 2, 12, 2, extraOffset);
        this.bipedLeftArm.setRotationPoint(5.0F, 2.0F, 0.0F);
        this.bipedRightLeg = new ModelRenderer(this, 0, 16);
        this.bipedRightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 12, 2, extraOffset);
        this.bipedRightLeg.setRotationPoint(-2.0F, 12.0F, 0.0F);
        this.bipedLeftLeg = new ModelRenderer(this, 0, 16);
        this.bipedLeftLeg.mirror = true;
        this.bipedLeftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 12, 2, extraOffset);
        this.bipedLeftLeg.setRotationPoint(2.0F, 12.0F, 0.0F);
    }
}
