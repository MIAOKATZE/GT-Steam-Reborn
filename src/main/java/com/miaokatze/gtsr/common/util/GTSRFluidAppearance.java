package com.miaokatze.gtsr.common.util;

import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.enums.CondensateType;

/**
 * 流体 NEI 外观解析器：复刻 NEI 流体显示（ItemFluidDisplay/FluidDisplayStackRenderer）的
 * 取图标与取色公式，供"流体窗口"覆材获得与 NEI 逐像素一致的外观。
 * 无状态、只读静态工具，线程安全。
 */
public final class GTSRFluidAppearance {

    private GTSRFluidAppearance() {}

    /** 解析结果：icon 为 still 图标（null 表示无可绘图标，调用方跳过绘制）；tint 为 0xRRGGBB。 */
    public static final class Appearance {

        public final IIcon icon;
        public final int tint;

        private Appearance(IIcon icon, int tint) {
            this.icon = icon;
            this.tint = tint;
        }
    }

    /** 空外观：流体缺失时跳过绘制、着色为白。 */
    private static final Appearance EMPTY = new Appearance(null, 0xFFFFFF);

    /**
     * 解析流体的窗口绘制外观。
     * 图标回退链与 ItemFluidDisplay.getIconFromDamage 一致：fluid.getStillIcon() →
     * FluidRegistry.WATER.getStillIcon() → null（调用方跳过绘制）；
     * 取色与 FluidDisplayStackRenderer 一致：凝缩态特判走 CondensateType.getRenderColor
     * （凝缩贴图已烘焙无 tint，需按源流体着色），否则 fluid.getColor()；
     * 结果统一 & 0xFFFFFF，防御个别流体返回带 alpha/符号位的颜色。
     * 已知可接受偏差：不实现 Universium/Infinity 特殊材质动画，也不叠加凝缩态 64 帧覆膜
     * ——窗口仅显示静态 still 图 + tint，与 NEI 的动画表现略有差异。
     */
    public static Appearance resolve(Fluid fluid) {
        if (fluid == null) return EMPTY;
        IIcon icon = fluid.getStillIcon();
        if (icon == null && FluidRegistry.WATER != null) icon = FluidRegistry.WATER.getStillIcon();
        final int tint = (CondensateType.getCondensateType(fluid) != null ? CondensateType.getRenderColor(fluid)
            : fluid.getColor()) & 0xFFFFFF;
        return new Appearance(icon, tint);
    }
}
