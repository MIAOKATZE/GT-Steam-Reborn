package com.miaokatze.gtsr.common.machine.cluster;

/**
 * 集群单元的六态运行状态枚举，用于状态机判定与 GUI 单元状态着色。
 * <p>
 * 色值来源为 UI 规格 HTML v2.5，为 0xRRGGBB 形式的 24 位真彩色，禁止修改数值；
 * GUI 侧若需要 ARGB 形式，可自行通过 {@code (rgb << 8) | 0xFF} 组装透明度。
 */
public enum ClusterUnitStatus {

    /** 正常运行中（绿色）。 */
    WORKING(0x1E821E, "gtsr.gui.cluster.state.working"),

    /** 空闲等待（黄色）。 */
    IDLE(0xBE9614, "gtsr.gui.cluster.state.idle"),

    /** 缺少工作流体（蓝色）。 */
    FLUID_MISSING(0x286EBE, "gtsr.gui.cluster.state.fluid_missing"),

    /** 缺少助推流体（紫色）。 */
    BOOSTER_FLUID_MISSING(0x8C3CB4, "gtsr.gui.cluster.state.booster_fluid_missing"),

    /** 待机，结构或条件未满足（灰色）。 */
    STANDBY(0x6E6E6E, "gtsr.gui.cluster.state.standby"),

    /** 无功率或无效单元（红色）。 */
    NO_POWER_OR_INVALID(0xBE2D2D, "gtsr.gui.cluster.state.no_power");

    private final int colorRgb;
    private final String langKey;

    private ClusterUnitStatus(int colorRgb, String langKey) {
        this.colorRgb = colorRgb;
        this.langKey = langKey;
    }

    /**
     * 返回该状态的显示颜色，0xRRGGBB 形式的 24 位真彩色。
     */
    public int getColorRgb() {
        return colorRgb;
    }

    /**
     * 返回该状态本地化文本的 lang key（gtsr.gui.cluster.state.* 命名空间）。
     */
    public String getLangKey() {
        return langKey;
    }

    /**
     * 将 0xRRGGBB 颜色值转换为原版颜色表示；本枚举色值即 0xRRGGBB 形式，直接原样返回。
     *
     * @param rgb 0xRRGGBB 形式的颜色值
     * @return 原样返回的同一数值
     */
    public static int toVanillaColor(int rgb) {
        return rgb;
    }
}
