package com.miaokatze.gtsr.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 汽雾萤模型（<b>D2 · 修 C-01「上屏即崩」</b>）。
 * <p>
 * ═══ 为什么不挂原版 {@code ModelBat} ═══
 * {@code build/rfg/minecraft-src/java/net/minecraft/client/model/ModelBat.java:73} 是
 * {@code render()} 的<b>第一句、无条件</b> {@code EntityBat entitybat = (EntityBat)p_78088_1_;}
 * ——它不在悬挂分支里，所以只要实体不是 {@code EntityBat}，每帧渲染入口
 * （{@code RendererLivingEntity.java:165/247/266/309/319}）都会抛 {@code ClassCastException}，
 * 而 1.7.10 的 {@code RenderManager.java:300-304} <b>不吞异常</b>（直接包成 {@code ReportedException}
 * 建 "Rendering entity in world" 崩溃报告）⇒ 表现是"看见即崩客户端"，不是"看不见"。
 * 旧离线判据（{@code CreatureSpawnAuthorityCheck} 判据 4）只核对渲染器类名前缀与模型 SimpleName，
 * <b>从不看所选模型对实体做什么</b> ⇒ 该缺陷一路全绿到实机。现由同工具的 I1 组钉住
 * （模型继承链上 {@code render}/{@code setLivingAnimations}/{@code setRotationAngles} 不得强转非自家实体类型）。
 * <p>
 * ═══ 为什么是"复刻几何"而不是"换个零强转的原版模型" ═══
 * 零强转候选（实测全类无 {@code (Entity…)}）是 {@code ModelChicken}/{@code ModelZombie}/{@code ModelBiped}
 * 这一类——把萤火虫画成鸡或人的剪影会连带改掉已申报的<b>画布口径</b>，而 {@code ModelBat} 的字段
 * 全是 private（{@code ModelBat.java:12-22}），继承复用不了。故本类按原版几何逐箱复刻：
 * <b>UV 偏移与 {@code textureWidth=64 / textureHeight=64} 与 {@code ModelBat.java:27-28,29-52} 逐位一致</b>
 * ⇒ 复刻过渡期曾借原版 {@code textures/entity/bat.png}（现网无一只萤"画错"），P16-D1 起改吃自有
 * {@code gtsr:textures/entity/steam-firefly.png}，且 <b>自有皮肤画布钉死为 64×64</b>（由同工具 I2 按贴图 IHDR 实测核对）。
 * <p>
 * <b>刻意不承载的东西</b>：原版悬挂分支（{@code ModelBat.java:76-92}，判 {@code getIsBatHanging()}）——
 * 萤不悬挂，该分支连同它唯一的强转需求一起消失；非悬挂分支（{@code :95-107}）逐式照抄。
 * 左右翼仍按原版 {@code mirror = true}（{@code ModelBat.java:35,47,50}）⇒ 左半是右半的镜像采样，
 * 非对称细节画不出来（清单 C-16 ② 已把这条记为 D1 的硬约束）；改 {@code mirror} 会让复用期的
 * 原版 bat.png 立刻错采样，故本切片不动。
 */
@SideOnly(Side.CLIENT)
public class GTSRModelSteamFirefly extends ModelBase {

    private final ModelRenderer head;
    private final ModelRenderer body;
    private final ModelRenderer rightWing;
    private final ModelRenderer leftWing;
    private final ModelRenderer outerRightWing;
    private final ModelRenderer outerLeftWing;

    public GTSRModelSteamFirefly() {
        // 画布（I2 钉；与 bat.png 的 IHDR 64x64 同一真值）
        this.textureWidth = 64;
        this.textureHeight = 64;
        this.head = new ModelRenderer(this, 0, 0);
        this.head.addBox(-3.0F, -3.0F, -3.0F, 6, 6, 6);
        final ModelRenderer rightEar = new ModelRenderer(this, 24, 0);
        rightEar.addBox(-4.0F, -6.0F, -2.0F, 3, 4, 1);
        this.head.addChild(rightEar);
        final ModelRenderer leftEar = new ModelRenderer(this, 24, 0);
        leftEar.mirror = true;
        leftEar.addBox(1.0F, -6.0F, -2.0F, 3, 4, 1);
        this.head.addChild(leftEar);
        this.body = new ModelRenderer(this, 0, 16);
        this.body.addBox(-3.0F, 4.0F, -3.0F, 6, 12, 6);
        // 膜翼（贴身那片）单独占一条 UV 带：原版在此链式改偏移，偏移值 0/34 必须照抄
        this.body.setTextureOffset(0, 34)
            .addBox(-5.0F, 16.0F, 0.0F, 10, 6, 1);
        this.rightWing = new ModelRenderer(this, 42, 0);
        this.rightWing.addBox(-12.0F, 1.0F, 1.5F, 10, 16, 1);
        this.outerRightWing = new ModelRenderer(this, 24, 16);
        this.outerRightWing.setRotationPoint(-12.0F, 1.0F, 1.5F);
        this.outerRightWing.addBox(-8.0F, 1.0F, 0.0F, 8, 12, 1);
        this.leftWing = new ModelRenderer(this, 42, 0);
        this.leftWing.mirror = true;
        this.leftWing.addBox(2.0F, 1.0F, 1.5F, 10, 16, 1);
        this.outerLeftWing = new ModelRenderer(this, 24, 16);
        this.outerLeftWing.mirror = true;
        this.outerLeftWing.setRotationPoint(12.0F, 1.0F, 1.5F);
        this.outerLeftWing.addBox(0.0F, 1.0F, 0.0F, 8, 12, 1);
        this.body.addChild(this.rightWing);
        this.body.addChild(this.leftWing);
        this.rightWing.addChild(this.outerRightWing);
        this.leftWing.addChild(this.outerLeftWing);
    }

    /**
     * 逐帧摆姿势并绘制。
     * <p>
     * <b>本方法不读 {@code entity} 的任何字段</b>（I1 钉的就是这条）：原版靠 {@code (EntityBat)} 取
     * 悬挂标志，我们不悬挂，于是整段只需要 {@code ageInTicks} 的振翅三角函数与头 yaw/pitch。
     */
    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scale) {
        final float toRadians = 180F / (float) Math.PI;
        this.head.rotateAngleX = headPitch / toRadians;
        this.head.rotateAngleY = netHeadYaw / toRadians;
        this.head.rotateAngleZ = 0.0F;
        this.head.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.rightWing.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.leftWing.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.body.rotateAngleX = (float) Math.PI / 4F + MathHelper.cos(ageInTicks * 0.1F) * 0.15F;
        this.body.rotateAngleY = 0.0F;
        this.rightWing.rotateAngleY = MathHelper.cos(ageInTicks * 1.3F) * (float) Math.PI * 0.25F;
        this.leftWing.rotateAngleY = -this.rightWing.rotateAngleY;
        this.outerRightWing.rotateAngleY = this.rightWing.rotateAngleY * 0.5F;
        this.outerLeftWing.rotateAngleY = -this.rightWing.rotateAngleY * 0.5F;
        this.head.render(scale);
        this.body.render(scale);
    }
}
