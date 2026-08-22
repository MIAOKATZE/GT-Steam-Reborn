package com.miaokatze.gtsr.common.util;

/**
 * 1000 进制大数单位格式化（K/M/B/G）：K=10^3、M=10^6、B=10^9、G=10^12（即 1000B）。
 * 用于枢纽阵列等超大容量数值显示，避免科学计数法难以直读。
 * 最多保留两位小数，末尾零自动裁剪（如 320M、1.28B、76.8G）。
 *
 * 口径差异注记（SR-BUG-03 注记、B2-04 修正事实错误）：GTNHLib NumberFormatUtil 并非 SI 前缀体系，
 * 其后缀为纯十进制 K/M/B/T/Q（GTNHLib-0.11.24 Constants.java 实测，无 G/giga 档）——
 * 原注记「SI 的 G=giga…两套 G 相差 1000 倍」不成立，GTNHLib 全链路根本不输出 G 后缀。
 * 真实差异仅在 10^12 档：本类后缀 G，GTNHLib 后缀 T，同值仅字母不同；10^9 档两套均为 B，数值口径一致。
 * 当前消费方 6 文件（MTESteamHubArray/MTEWaterHubArray/MTESteamHubInputHatch/
 * MTESteamHubOutputHatch/MTEWaterHubInputHatch/MTEWaterHubOutputHatch）全部走本类口径，
 * 口径内部自洽；因 B 档同值同字母、差异仅 10^12+ 档字母，迁移收益有限（O2-14 维持 P3 中期），
 * 收敛前不要在新显示面混用两套后缀（10^12 以上本类 G 与 GTNHLib T 的字母差异仍会误导直读）。
 */
public final class UnitFormatUtil {

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    /** 10^9，本类后缀 B（与 GTNHLib 十进制后缀 B 同值同字母，见类 javadoc 口径注记）。 */
    private static final long B = 1_000_000_000L;
    /** 10^12，本类后缀 G（GTNHLib 同档后缀为 T：同值仅字母不同，见类 javadoc 口径注记）。 */
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
