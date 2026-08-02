package com.miaokatze.gtsr.common.util;

/**
 * 1000 进制大数单位格式化（K/M/B/G）：K=10^3、M=10^6、B=10^9、G=10^12（即 1000B）。
 * 用于枢纽阵列等超大容量数值显示，避免科学计数法难以直读。
 * 最多保留两位小数，末尾零自动裁剪（如 320M、1.28B、76.8G）。
 */
public final class UnitFormatUtil {

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long B = 1_000_000_000L;
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
