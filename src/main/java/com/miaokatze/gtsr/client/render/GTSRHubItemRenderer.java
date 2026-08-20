package com.miaokatze.gtsr.client.render;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_BRONZE_SIDE;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASING_RHODIUM_PALLADIUM;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_STEEL_SIDE;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizon.gtnhlib.util.ItemRenderUtil;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.util.GTSRFluidAppearance;
import com.miaokatze.gtsr.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;

/**
 * 缓存节点/奇点仓物品平贴图渲染器：按物品 NBT（gtsr.hubPos）绘制「基材 + 流体窗 + 框架」三层
 * 16 单位平面图标（S2 起补基材层，修复手持仅框架+窗悬空平面的问题；z 序 0/0.001/0.002 递进）。
 *
 * 委托边界（零回归关键）：{@link #handleRenderType} 只对 GT 机器物品（ItemMachines）且 damage 命中
 * 白名单（枚举常量构建，见下）的物品返回 true；其余一切物品/类型返回 false，Forge getItemRenderer
 * 视为无自定义渲染器，vanilla 原路径零差异。
 *
 * 绘制模板：FluidDisplayStackRenderer（INVENTORY 16×16 quad + glColor3ub 染色）与 FlaskRenderer
 * （applyStandardItemTransform + blocks 图集 + z=0.001 叠层 + 五型处理）。五型统一平面绘制：
 * INVENTORY 16 单位 y 向下；EQUIPPED 系与 ENTITY(fancy) 为 renderItemIn2D 面空间 [0,1]（y 向上、U 反转，
 * 见 1.7.10 ItemRenderer.renderItemIn2D 前脸几何）；ENTITY(fast) 为 billboard（-0.5/-0.25 偏移 + 面向
 * 玩家旋转），与这些物品 vanilla 平面图标表现同型。
 *
 * 基材层（z=0，白 tint 全脸）：按 damage 逐项映射（{@link #resolveBaseIcon}）——缓存节点取各自
 * getTexture 正面基材（基础=青铜、耐压=钢、超压=Casings8:6 铑钯，与方块世界材质一致）；
 * 四奇点仓=钢机壳（S1 buildCompartmentTextures 近亲基材回退 firstKinTexture 同款兜底口径）。
 *
 * 框架选择：未绑定缓存节点→UNBOUND；绑定 output=false(接收)→RECEIVE、output=true(发送)→SEND；
 * 四奇点仓恒定语义色：SINGULARITY_STEAM/FLUID_INPUT（收）=RECEIVE、SINGULARITY_STEAM_OUTPUT/FLUID_OUTPUT
 * （发）=SEND（未绑定也保持）。窗口流体：绑定按 hubPos.type（含 steam→蒸汽、含 water→水，其余只画框）；
 * 未绑定缓存节点不画窗；未绑定奇点仓画默认流体（蒸汽仓系→蒸汽、流体仓系→水）。
 */
@SideOnly(Side.CLIENT)
public class GTSRHubItemRenderer implements IItemRenderer {

    /** GT 机器物品（gt.blockmachines.ItemMachines 的 Item 单例）。 */
    private static final Item MACHINES_ITEM = Item.getItemFromBlock(GregTechAPI.sBlockMachines);

    /** 绑定 NBT 平铺键（与 MTEFilteredCacheNode saveNBTData/setItemNBT 写入口径一致）。 */
    private static final String TAG_HUB_POS = "gtsr.hubPos";

    /** 窗口内缩边界（16 单位坐标）：=方块面窗口 6/32..26/32 的换算，与 GTSRFluidWindowTexture 对齐。 */
    private static final double WINDOW_MIN = 6.0D / 32.0D * 16.0D; // = 3
    private static final double WINDOW_MAX = 26.0D / 32.0D * 16.0D; // = 13

    /** 流体窗相对基材层的抬升（FlaskRenderer/ItemRenderUtil 同款叠层量级）。 */
    private static final double WINDOW_Z = 0.001D;

    /** 框架层抬升（基材 0 → 窗 0.001 → 框架 0.002 等距递进，避免 z-fighting）。 */
    private static final double FRAME_Z = 0.002D;

    // ===== 白名单（引用 MetaTileEntityID 枚举常量，不硬编码数字；ID 已含 Config.metaIdOffset） =====

    /** 六个缓存节点（蒸汽/水 × 基础/耐压/超压）：框架按绑定状态选择。 */
    private static final ImmutableSet<Integer> CACHE_NODE_IDS = ImmutableSet.of(
        MetaTileEntityID.STEAM_CACHE_NODE.ID,
        MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
        MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.ID,
        MetaTileEntityID.WATER_CACHE_NODE.ID,
        MetaTileEntityID.REINFORCED_WATER_CACHE_NODE.ID,
        MetaTileEntityID.OVERPRESSURE_WATER_CACHE_NODE.ID);

    /** 恒定 RECEIVE 语义色的奇点仓（蒸汽收/流体输入仓）。 */
    private static final ImmutableSet<Integer> COMPARTMENT_RECEIVE_IDS = ImmutableSet
        .of(MetaTileEntityID.SINGULARITY_STEAM_COMPARTMENT.ID, MetaTileEntityID.SINGULARITY_FLUID_INPUT_COMPARTMENT.ID);

    /** 恒定 SEND 语义色的奇点仓（蒸汽输出/流体输出仓）。 */
    private static final ImmutableSet<Integer> COMPARTMENT_SEND_IDS = ImmutableSet.of(
        MetaTileEntityID.SINGULARITY_STEAM_OUTPUT_COMPARTMENT.ID,
        MetaTileEntityID.SINGULARITY_FLUID_OUTPUT_COMPARTMENT.ID);

    /** 蒸汽仓系（未绑定时的默认窗口流体=蒸汽）。 */
    private static final ImmutableSet<Integer> STEAM_COMPARTMENT_IDS = ImmutableSet.of(
        MetaTileEntityID.SINGULARITY_STEAM_COMPARTMENT.ID,
        MetaTileEntityID.SINGULARITY_STEAM_OUTPUT_COMPARTMENT.ID);

    /** 完整白名单：白名单外 handleRenderType 恒 false（vanilla 原路径零差异）。 */
    private static final ImmutableSet<Integer> WHITELIST = ImmutableSet.<Integer>builder()
        .addAll(CACHE_NODE_IDS)
        .addAll(COMPARTMENT_RECEIVE_IDS)
        .addAll(COMPARTMENT_SEND_IDS)
        .build();

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        if (type == ItemRenderType.FIRST_PERSON_MAP) return false;
        if (stack == null || stack.getItem() != MACHINES_ITEM) return false;
        return WHITELIST.contains(stack.getItemDamage());
    }

    /** 镜像 FlaskRenderer#shouldUseRenderHelper：ENTITY 掉落浮沉 + fancy 时旋转。 */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY && helper == ItemRendererHelper.ENTITY_BOBBING
            || helper == ItemRendererHelper.ENTITY_ROTATION && Minecraft.getMinecraft().gameSettings.fancyGraphics;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (!handleRenderType(stack, type)) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);

            ItemRenderUtil.applyStandardItemTransform(type);

            final NBTTagCompound hubTag = stack.hasTagCompound() && stack.getTagCompound()
                .hasKey(TAG_HUB_POS) ? stack.getTagCompound()
                    .getCompoundTag(TAG_HUB_POS) : null;
            final int damage = stack.getItemDamage();

            // 基材/框架/流体窗图标同在 blocks 图集，绑定一次
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);

            // 基材层（z=0）：方块本体正面基材，白 tint 全脸（10 项白名单逐项映射，见 resolveBaseIcon）
            final IIcon baseIcon = resolveBaseIcon(damage);
            GL11.glColor3ub((byte) -1, (byte) -1, (byte) -1);
            drawFace(type, baseIcon, 0, 0, 16, 16, 0.0D);

            // 流体窗层（z=0.001）：NEI 同源 tint（GTSRFluidAppearance），无图标跳过
            final Fluid fluid = resolveWindowFluid(damage, hubTag);
            if (fluid != null) {
                final GTSRFluidAppearance.Appearance appearance = GTSRFluidAppearance.resolve(fluid);
                if (appearance.icon != null) {
                    final int tint = appearance.tint;
                    GL11.glColor3ub((byte) (tint >> 16 & 0xFF), (byte) (tint >> 8 & 0xFF), (byte) (tint & 0xFF));
                    drawFace(type, appearance.icon, WINDOW_MIN, WINDOW_MIN, WINDOW_MAX, WINDOW_MAX, WINDOW_Z);
                }
            }

            // 框架层（z=0.002）：白色全脸
            final IIconContainer frame = resolveFrame(damage, hubTag);
            GL11.glColor3ub((byte) -1, (byte) -1, (byte) -1);
            drawFace(type, frame.getIcon(), 0, 0, 16, 16, FRAME_Z);

            GL11.glColor3ub((byte) -1, (byte) -1, (byte) -1);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 基材层图标（S2 逐项映射，与各类方块 getTexture 正面基材层一致）：
     * 基础蒸汽/通用流体节点=青铜侧面；耐压两节点=钢侧面；超压两节点=Casings8:6 铑钯
     * （对应子类 CASING_INDEX=sBlockCasings8 meta 6）；四奇点仓=钢机壳
     * （S1 近亲基材回退 firstKinTexture 的兜底口径）。白名单 10 项全覆盖。
     */
    private static IIcon resolveBaseIcon(int damage) {
        if (damage == MetaTileEntityID.STEAM_CACHE_NODE.ID || damage == MetaTileEntityID.WATER_CACHE_NODE.ID)
            return MACHINE_BRONZE_SIDE.getIcon();
        if (damage == MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID
            || damage == MetaTileEntityID.REINFORCED_WATER_CACHE_NODE.ID) return MACHINE_STEEL_SIDE.getIcon();
        if (damage == MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.ID
            || damage == MetaTileEntityID.OVERPRESSURE_WATER_CACHE_NODE.ID)
            return MACHINE_CASING_RHODIUM_PALLADIUM.getIcon();
        // 四奇点仓（COMPARTMENT_RECEIVE/SEND_IDS）：钢机壳
        return MACHINE_STEEL_SIDE.getIcon();
    }

    /** 框架图标选择（见类注释语义表）。 */
    private static IIconContainer resolveFrame(int damage, NBTTagCompound hubTag) {
        if (COMPARTMENT_RECEIVE_IDS.contains(damage)) return TextureManager.HUB_FRAME_RECEIVE;
        if (COMPARTMENT_SEND_IDS.contains(damage)) return TextureManager.HUB_FRAME_SEND;
        if (hubTag == null) return TextureManager.HUB_FRAME_UNBOUND;
        // output 反转语义：false=枢纽→节点(接收)、true=节点→枢纽(发送)
        return hubTag.getBoolean("output") ? TextureManager.HUB_FRAME_SEND : TextureManager.HUB_FRAME_RECEIVE;
    }

    /** 窗口流体选择（见类注释语义表）；null=不画窗。 */
    private static Fluid resolveWindowFluid(int damage, NBTTagCompound hubTag) {
        if (COMPARTMENT_RECEIVE_IDS.contains(damage) || COMPARTMENT_SEND_IDS.contains(damage)) {
            // 奇点仓：绑定按 hubPos.type，未绑定画默认流体（蒸汽仓系→蒸汽、流体仓系→水）
            if (hubTag != null) return fluidFromHubType(hubTag.getString("type"));
            return STEAM_COMPARTMENT_IDS.contains(damage) ? FluidRegistry.getFluid("steam") : FluidRegistry.WATER;
        }
        // 缓存节点：未绑定不画窗
        if (hubTag == null) return null;
        return fluidFromHubType(hubTag.getString("type"));
    }

    /** hubPos.type → 窗口流体：含 steam→蒸汽、含 water→水，其余 null（只画框）。 */
    private static Fluid fluidFromHubType(String hubType) {
        if (hubType == null) return null;
        if (hubType.contains("steam")) return FluidRegistry.getFluid("steam");
        if (hubType.contains("water")) return FluidRegistry.WATER;
        return null;
    }

    /**
     * 按类型把 16 单位面坐标的平贴 quad 映射到各 ItemRenderType 的原生面空间（顶点序与 UV 朝向
     * 逐一对照 vanilla/gtnhlib 模板，见类注释）。icon 的完整 min/maxUV 整图铺满 quad（整图缩放进窗）。
     */
    private static void drawFace(ItemRenderType type, IIcon icon, double x0, double y0, double x1, double y1,
        double z) {
        if (icon == null) return;
        final Tessellator tess = Tessellator.instance;
        final float uMin = icon.getMinU(), uMax = icon.getMaxU();
        final float vMin = icon.getMinV(), vMax = icon.getMaxV();
        tess.startDrawingQuads();
        switch (type) {
            case INVENTORY -> {
                // y 向下 16 单位 GUI 空间（模板 FluidDisplayStackRenderer）
                tess.setNormal(0.0F, 0.0F, 1.0F);
                tess.addVertexWithUV(x0, y1, z, uMin, vMax);
                tess.addVertexWithUV(x1, y1, z, uMax, vMax);
                tess.addVertexWithUV(x1, y0, z, uMax, vMin);
                tess.addVertexWithUV(x0, y0, z, uMin, vMin);
            }
            case EQUIPPED, EQUIPPED_FIRST_PERSON -> {
                // renderItemIn2D 前脸空间 [0,1]：y 向上、U 反转（模板 ItemRenderer.renderItemIn2D）
                tess.setNormal(0.0F, 0.0F, 1.0F);
                tess.addVertexWithUV(x0 / 16.0D, y0 / 16.0D, z, uMax, vMax);
                tess.addVertexWithUV(x1 / 16.0D, y0 / 16.0D, z, uMin, vMax);
                tess.addVertexWithUV(x1 / 16.0D, y1 / 16.0D, z, uMin, vMin);
                tess.addVertexWithUV(x0 / 16.0D, y1 / 16.0D, z, uMax, vMin);
            }
            case ENTITY -> {
                if (Minecraft.getMinecraft().gameSettings.fancyGraphics) {
                    // fancy：applyStandardItemTransform 已平移，前脸空间同 EQUIPPED
                    tess.setNormal(0.0F, 0.0F, 1.0F);
                    tess.addVertexWithUV(x0 / 16.0D, y0 / 16.0D, z, uMax, vMax);
                    tess.addVertexWithUV(x1 / 16.0D, y0 / 16.0D, z, uMin, vMax);
                    tess.addVertexWithUV(x1 / 16.0D, y1 / 16.0D, z, uMin, vMin);
                    tess.addVertexWithUV(x0 / 16.0D, y1 / 16.0D, z, uMax, vMin);
                } else {
                    // fast billboard：-0.5/-0.25 偏移 + 面向玩家旋转（模板 ItemRenderUtil ENTITY fast 分支）
                    GL11.glPushMatrix();
                    if (!RenderItem.renderInFrame) {
                        GL11.glRotatef(180.0F - RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
                    }
                    tess.setNormal(0.0F, 1.0F, 0.0F);
                    tess.addVertexWithUV(x0 / 16.0D - 0.5D, y0 / 16.0D - 0.25D, z, uMin, vMax);
                    tess.addVertexWithUV(x1 / 16.0D - 0.5D, y0 / 16.0D - 0.25D, z, uMax, vMax);
                    tess.addVertexWithUV(x1 / 16.0D - 0.5D, y1 / 16.0D - 0.25D, z, uMax, vMin);
                    tess.addVertexWithUV(x0 / 16.0D - 0.5D, y1 / 16.0D - 0.25D, z, uMin, vMin);
                    tess.draw();
                    GL11.glPopMatrix();
                    return;
                }
            }
            default -> {}
        }
        tess.draw();
    }
}
