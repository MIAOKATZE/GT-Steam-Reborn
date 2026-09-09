package com.miaokatze.gtsr.common.util;

/**
 * 集群终端 GUI 专用数字格式化（千位分组 + 紧凑档），终端各页共用单源自洽口径：
 * <ul>
 * <li>千位分隔符 = 半角空格，小数点 = 半角点 '.'（{@link #grouped(long)}）；</li>
 * <li>紧凑档 K/M/B/T/Q 为纯十进制千位进位（K=10^3、M=10^6、B=10^9、T=10^12、Q=10^15），
 * 最多 3 位有效数字（整数部位数 n 时小数保留 max(0, 3-n) 位，一次 HALF_UP 后按窗口截取显示），
 * 尾零与小数点自动裁剪（{@link #compact3(long)}）；</li>
 * <li>进位防御：舍入后整数部达 1000 时晋档（如 999 999→1M、999 999 999 999→1T、
 * 999 999 999 999 999→1Q）；Q 为最高档，≥Q 后不再晋档、整数部可增长
 * （Long.MAX_VALUE→"9223Q"）；负数一律防御回 "0"。</li>
 * </ul>
 * 与 {@link UnitFormatUtil} 的边界：UnitFormatUtil 服务枢纽阵列容量显示，其 10^12 档后缀为 G
 * （GTNHLib 同档为 T 的口径差异见其类 javadoc）；本类 10^12 档后缀为 T、10^15 档为 Q，
 * 与终端 GUI 及 GTNHLib 十进制后缀习惯单源自洽。两套口径消费方互不重叠，
 * 严禁在同一显示面混用（枢纽面板走 UnitFormatUtil，集群终端各页只走本类）。
 *
 * 纯 Java 字符串/长整逻辑，无任何 Minecraft/GTNHLib 依赖，双端 Side-safe。
 */
public final class GtsrNumFormat {

    /** 千位分组分隔符：半角空格，单点可替换。 */
    private static final char GROUP_SEP = ' ';

    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long B = 1_000_000_000L;
    private static final long T = 1_000_000_000_000L;
    /** 10^15，最高档 Q；≥Q 后不晋档，整数部可增长。 */
    private static final long Q = 1_000_000_000_000_000L;

    private GtsrNumFormat() {}

    /**
     * 千位分组：每 3 位插入 {@link #GROUP_SEP}（半角空格）。
     * 如 1234567→"1 234 567"、0→"0"、-1234→"-1 234"；完整覆盖 Long.MIN_VALUE。
     */
    public static String grouped(long value) {
        String s = Long.toString(value);
        boolean negative = s.charAt(0) == '-';
        String digits = negative ? s.substring(1) : s;
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(GROUP_SEP);
            }
            sb.append(digits.charAt(i));
        }
        if (negative) {
            sb.insert(0, '-');
        }
        return sb.toString();
    }

    /**
     * 千位进位紧凑式：≤3 位有效数字、HALF_UP、去尾零与小数点；&lt;1000 原样十进制；
     * 进位防御与 Q 档增长规则见类 javadoc；负数与 0 统一返回 "0"。
     */
    public static String compact3(long value) {
        if (value <= 0L) {
            return "0";
        }
        if (value < K) {
            return Long.toString(value);
        }
        long d = Q;
        while (d > K && d > value) {
            d /= 1000L;
        }
        // Q 档整数部已达 4 位（≥1000Q）：最高档无处晋档，整数部直接增长（截断整除）。
        if (d == Q && value / Q >= K) {
            return Long.toString(value / Q) + "Q";
        }
        long s100 = roundScaled100(value, d);
        // 进位防御：HALF_UP 后整数部达 1000 且仍有更高档 → 晋档重算（如 999 999→1M）。
        if (s100 >= 100_000L && d < Q) {
            d *= 1000L;
            s100 = roundScaled100(value, d);
        }
        long intPart = s100 / 100L;
        int frac = (int) (s100 % 100L);
        // 3 位有效数字窗口：整数部 n 位时小数保留 max(0, 3-n) 位（显示截取，不做二次舍入）。
        int decimals = Math.max(
            0,
            3 - Long.toString(intPart)
                .length());
        StringBuilder sb = new StringBuilder(8);
        sb.append(intPart);
        if (frac != 0 && decimals > 0) {
            String raw = decimals == 1 ? Integer.toString(frac / 10) : twoDigits(frac);
            int end = raw.length();
            while (end > 0 && raw.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0) {
                sb.append('.')
                    .append(raw, 0, end);
            }
        }
        sb.append(suffix(d));
        return sb.toString();
    }

    /** HALF_UP 到 2 位小数的百倍整型：value/d 保留两位小数后 ×100。中途无 long 溢出。 */
    private static long roundScaled100(long value, long d) {
        long q = value / d;
        long r = value % d;
        long frac100 = (r * 100L + d / 2L) / d;
        return q * 100L + frac100;
    }

    /** 0..99 定宽两位十进制（免依赖 String.format，热路径无格式化开销）。 */
    private static String twoDigits(int v) {
        return String.valueOf(new char[] { (char) ('0' + v / 10), (char) ('0' + v % 10) });
    }

    private static String suffix(long d) {
        if (d == K) return "K";
        if (d == M) return "M";
        if (d == B) return "B";
        if (d == T) return "T";
        return "Q";
    }
}
