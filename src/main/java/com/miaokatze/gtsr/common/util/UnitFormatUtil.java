package com.miaokatze.gtsr.common.util;

/**
 * 1000 进制大数单位格式化（K/M/B/G）：K=10^3、M=10^6、B=10^9、G=10^12（即 1000B）。
 * 用于枢纽阵列等超大容量数值显示，避免科学计数法难以直读。
 * 最多保留两位小数，末尾零自动裁剪（如 320M、1.28B、76.8G）。
 *
 * 口径差异注记（SR-BUG-03，本轮仅注记、不改任何输出）：本类 B/G 后缀与 GTNHLib
 * NumberFormatUtil 的 SI 前缀体系冲突——SI 的 G=giga=10^9，而本类 B=10^9、G=10^12，
 * 同屏并用时两套「G」相差 1000 倍（同一数值本类显示 20.48B，SI 口径显示 20.5G）。
 * 当前消费方 6 文件（MTESteamHubArray/MTEWaterHubArray/MTESteamHubInputHatch/
 * MTESteamHubOutputHatch/MTEWaterHubInputHatch/MTEWaterHubOutputHatch）全部走本类口径，
 * 口径内部自洽；中期收敛方向是迁移到 GTNHLib NumberFormatUtil（枢纽容量峰值 20.48e9
 * 小于 1T，不触发其科学计数法分支，迁移兼容性良好），收敛前不要在新显示面混用两套后缀。
 */
public final class UnitFormatUtil {

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    /** 10^9，本类后缀 B（注意：SI/GTNHLib 口径下 10^9 的前缀是 G/giga，见类 javadoc 口径差异注记）。 */
    private static final long B = 1_000_000_000L;
    /** 10^12，本类后缀 G（与 SI giga=10^9 冲突最直观的一档：本类 1G = SI 口径 1000G，见类 javadoc 注记）。 */
    private static final long G = 1_000_000_000_000L;

    private UnitFormatUtil() {}

    public static String format(long value) {
        if (value < M) {
            if (value < K) return String.format("%,d", value);
            return scaled(value, K, "K");
        }
        if (value < B) return scaled(value, M, "M");
        if (value < G) return scaled(value, B, "B");
        return scaled(value, G, "G");
    }

    private static String scaled(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long frac = value % divisor * 100 / divisor;
        if (frac == 0) return whole + suffix;
        if (frac % 10 == 0) return whole + "." + frac / 10 + suffix;
        return whole + "." + frac + suffix;
    }
}
