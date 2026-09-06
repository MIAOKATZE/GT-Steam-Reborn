package com.miaokatze.gtsr.common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import gregtech.api.render.ISBRContext;
import gregtech.common.render.GTTextureBase;

/**
 * "流体窗口"渲染核心：自定义 ITexture，在方块面上绘制流体 still 贴图，
 * 用于边框+流体覆材革新——外层机器贴图留出窗框，本层把流体图标（NEI 同款解析，见
 * {@link GTSRFluidAppearance}）画进窗内。两种变体：
 * 内缩窗（{@link #getOrCreate}）——面中央 20×20 窗（纹理坐标系 6/32..26/32，即每边 6 像素
 * 边框、20 像素窗），整图缩放进窗（{@link WindowIcon} 重映射）；缓存节点顶面与奇点仓正面使用。
 * 两种变体均固定 alpha pass（pass 1）绘制以保留半透明混合。
 * Tessellator 会话归属沿用 beta-3 GTTextureBase 契约：世界路径直写区块缓冲，物品栏路径由
 * GTRendererBlock.renderInventoryBlockImmediate 统一开会话、本类仅设法线不重开（beta-2 的
 * ITexture.startDrawingQuads 自开会话旧契约在 beta-3 下会 Already tesselating! 崩溃）。
 * 机制沿用 GTRenderedTexture 的 RenderBlocks 边界内缩技巧：仅内缩变体改动面内四周边界，
 * 平面方向字段保持层循环传入值（每层 nextUp 外推 1ulp）不动，内缩变体平面字段沿外法向
 * +0.001 显式偏移（层循环 1ulp 外推 float 化丢失的确定性深度分离，tectech RenderDoubleSidedGlass 先例），
 * 结束后恢复全部六字段；整面变体不动任何边界。
 */
public final class GTSRFluidWindowTexture extends GTTextureBase {

    /** 窗口下界（单位立方坐标）：每边向内缩 6/32。 */
    private static final float WINDOW_MIN = 6.0F / 32.0F;
    /** 窗口上界：26/32，窗边长 20/32 = 0.625，对边向中心对称内缩。 */
    private static final float WINDOW_MAX = 26.0F / 32.0F;
    private static final double WINDOW_PLANE_OFFSET = 0.001D;

    /** 流体属半透明覆材：固定 pass 1（alpha pass）绘制，混合才生效；等价 customAlpha 图标容器。 */
    private static final IntPredicate ALPHA_PASS_ONLY = pass -> pass == 1;

    /**
     * 内缩变体实例缓存（fluid → 窗口纹理）。GTRendererBlock 为 perThread 并行区块重建，
     * 缓存会被多线程同时访问，故用 ConcurrentHashMap。
     */
    private static final Map<Fluid, GTSRFluidWindowTexture> CACHE = new ConcurrentHashMap<>();
    /** 独立的整面纹理缓存，避免与内缩窗口实例/语义混用。 */
    private static final Map<Fluid, GTSRFluidWindowTexture> FULL_FACE_CACHE = new ConcurrentHashMap<>();

    /** null 流体的共享空实例：无图标 → isValidTexture=false，各面直接跳过（两变体共用，从不绘制）。 */
    private static final GTSRFluidWindowTexture NULL_WINDOW = new GTSRFluidWindowTexture(null, false);
    private static final GTSRFluidWindowTexture NULL_FULL_FACE = new GTSRFluidWindowTexture(null, true);

    private final Fluid fluid;
    private final boolean fullFace;
    private volatile GTSRFluidAppearance.Appearance appearance;

    private GTSRFluidWindowTexture(Fluid fluid) {
        this(fluid, false);
    }

    private GTSRFluidWindowTexture(Fluid fluid, boolean fullFace) {
        this.fluid = fluid;
        this.fullFace = fullFace;
        this.appearance = GTSRFluidAppearance.resolve(fluid);
    }

    /** 取（或创建）流体的内缩窗口纹理；null 流体返回不可绘制的共享空实例。 */
    public static GTSRFluidWindowTexture getOrCreate(Fluid fluid) {
        if (fluid == null) return NULL_WINDOW;
        final GTSRFluidWindowTexture texture = CACHE.computeIfAbsent(fluid, GTSRFluidWindowTexture::new);
        if (texture.appearance.icon == null) {
            // 图标未注册时（启动早期/材质未就绪）构造的实例：下次取用重试解析，注册后自愈
            texture.appearance = GTSRFluidAppearance.resolve(fluid);
        }
        return texture;
    }

    /** 取（或创建）流体的整面纹理；与内缩窗口使用独立缓存。 */
    public static GTSRFluidWindowTexture getOrCreateFullFace(Fluid fluid) {
        if (fluid == null) return NULL_FULL_FACE;
        final GTSRFluidWindowTexture texture = FULL_FACE_CACHE
            .computeIfAbsent(fluid, f -> new GTSRFluidWindowTexture(f, true));
        if (texture.appearance.icon == null) texture.appearance = GTSRFluidAppearance.resolve(fluid);
        return texture;
    }

    @Override
    public boolean isValidTexture() {
        return appearance.icon != null;
    }

    @Override
    public void renderXPos(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.EAST);
    }

    @Override
    public void renderXNeg(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.WEST);
    }

    @Override
    public void renderYPos(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.UP);
    }

    @Override
    public void renderYNeg(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.DOWN);
    }

    @Override
    public void renderZPos(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.SOUTH);
    }

    @Override
    public void renderZNeg(ISBRContext ctx) {
        renderWindow(ctx, ForgeDirection.NORTH);
    }

    /**
     * 六面共用的窗口绘制：pass 门控 → beginDrawingQuads（GTTextureBase 会话归属检测：世界路径
     * 空操作直写区块缓冲，物品栏路径按归属决定是否自开会话，恒设法线）+reset → 保存六边界字段 →
     * 内缩变体仅内缩面内四边并沿外法向偏移平面字段 +0.001 + 整图缩放重映射；整面变体不动任何边界、原图直绘 →
     * 面光照/AO 一致的 setupColor → renderFace*(Blocks.air) → endDrawingQuads（仅自开会话时 draw）。
     */
    private void renderWindow(ISBRContext ctx, ForgeDirection side) {
        final GTSRFluidAppearance.Appearance appearance = this.appearance;
        final IIcon icon = appearance.icon;
        if (icon == null) return;
        if (!ctx.canRenderInPass(ALPHA_PASS_ONLY)) return;
        final RenderBlocks rb = ctx.getRenderBlocks();
        final boolean startedDrawing = beginDrawingQuads(rb, side.offsetX, side.offsetY, side.offsetZ);
        ctx.reset();
        final double oldMinX = rb.renderMinX, oldMaxX = rb.renderMaxX;
        final double oldMinY = rb.renderMinY, oldMaxY = rb.renderMaxY;
        final double oldMinZ = rb.renderMinZ, oldMaxZ = rb.renderMaxZ;
        if (fullFace) {
            rb.renderMinX = oldMinX;
            rb.renderMaxX = oldMaxX;
            rb.renderMinY = oldMinY;
            rb.renderMaxY = oldMaxY;
            rb.renderMinZ = oldMinZ;
            rb.renderMaxZ = oldMaxZ;
            ctx.setupColor(side, appearance.tint);
            final int x = ctx.getX(), y = ctx.getY(), z = ctx.getZ();
            switch (side) {
                case EAST -> rb.renderFaceXPos(Blocks.air, x, y, z, icon);
                case WEST -> rb.renderFaceXNeg(Blocks.air, x, y, z, icon);
                case UP -> rb.renderFaceYPos(Blocks.air, x, y, z, icon);
                case DOWN -> rb.renderFaceYNeg(Blocks.air, x, y, z, icon);
                case SOUTH -> rb.renderFaceZPos(Blocks.air, x, y, z, icon);
                case NORTH -> rb.renderFaceZNeg(Blocks.air, x, y, z, icon);
                default -> {}
            }
            endDrawingQuads(rb, startedDrawing);
            return;
        }
        switch (side) {
            case EAST -> {
                rb.renderMinY = WINDOW_MIN;
                rb.renderMaxY = WINDOW_MAX;
                rb.renderMinZ = WINDOW_MIN;
                rb.renderMaxZ = WINDOW_MAX;
                rb.renderMaxX = oldMaxX + WINDOW_PLANE_OFFSET;
            }
            case WEST -> {
                rb.renderMinY = WINDOW_MIN;
                rb.renderMaxY = WINDOW_MAX;
                rb.renderMinZ = WINDOW_MIN;
                rb.renderMaxZ = WINDOW_MAX;
                rb.renderMinX = oldMinX - WINDOW_PLANE_OFFSET;
            }
            case UP -> {
                rb.renderMinX = WINDOW_MIN;
                rb.renderMaxX = WINDOW_MAX;
                rb.renderMinZ = WINDOW_MIN;
                rb.renderMaxZ = WINDOW_MAX;
                rb.renderMaxY = oldMaxY + WINDOW_PLANE_OFFSET;
            }
            case DOWN -> {
                rb.renderMinX = WINDOW_MIN;
                rb.renderMaxX = WINDOW_MAX;
                rb.renderMinZ = WINDOW_MIN;
                rb.renderMaxZ = WINDOW_MAX;
                rb.renderMinY = oldMinY - WINDOW_PLANE_OFFSET;
            }
            case SOUTH -> {
                rb.renderMinX = WINDOW_MIN;
                rb.renderMaxX = WINDOW_MAX;
                rb.renderMinY = WINDOW_MIN;
                rb.renderMaxY = WINDOW_MAX;
                rb.renderMaxZ = oldMaxZ + WINDOW_PLANE_OFFSET;
            }
            case NORTH -> {
                rb.renderMinX = WINDOW_MIN;
                rb.renderMaxX = WINDOW_MAX;
                rb.renderMinY = WINDOW_MIN;
                rb.renderMaxY = WINDOW_MAX;
                rb.renderMinZ = oldMinZ - WINDOW_PLANE_OFFSET;
            }
            default -> {}
        }
        ctx.setupColor(side, appearance.tint);
        final IIcon windowed = new WindowIcon(icon);
        final int x = ctx.getX(), y = ctx.getY(), z = ctx.getZ();
        switch (side) {
            case EAST -> rb.renderFaceXPos(Blocks.air, x, y, z, windowed);
            case WEST -> rb.renderFaceXNeg(Blocks.air, x, y, z, windowed);
            case UP -> rb.renderFaceYPos(Blocks.air, x, y, z, windowed);
            case DOWN -> rb.renderFaceYNeg(Blocks.air, x, y, z, windowed);
            case SOUTH -> rb.renderFaceZPos(Blocks.air, x, y, z, windowed);
            case NORTH -> rb.renderFaceZNeg(Blocks.air, x, y, z, windowed);
            default -> {}
        }
        rb.renderMinX = oldMinX;
        rb.renderMaxX = oldMaxX;
        rb.renderMinY = oldMinY;
        rb.renderMaxY = oldMaxY;
        rb.renderMinZ = oldMinZ;
        rb.renderMaxZ = oldMaxZ;
        endDrawingQuads(rb, startedDrawing);
    }

    /**
     * 窗口图标恒等裁剪：窗口面素坐标 [3,13] 直接对应父图标 UV [3/16,13/16]，
     * 不缩放、不偏移，保持流体图标中心 10×10 texel 的 1:1 显示。
     */
    private static final class WindowIcon implements IIcon {

        private final IIcon parent;

        WindowIcon(IIcon parent) {
            this.parent = parent;
        }

        @Override
        public int getIconWidth() {
            return parent.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return parent.getIconHeight();
        }

        @Override
        public float getMinU() {
            return parent.getMinU();
        }

        @Override
        public float getMaxU() {
            return parent.getMaxU();
        }

        @Override
        public float getInterpolatedU(double face) {
            return parent.getInterpolatedU(face);
        }

        @Override
        public float getMinV() {
            return parent.getMinV();
        }

        @Override
        public float getMaxV() {
            return parent.getMaxV();
        }

        @Override
        public float getInterpolatedV(double face) {
            return parent.getInterpolatedV(face);
        }

        @Override
        public String getIconName() {
            return parent.getIconName();
        }
    }
}
