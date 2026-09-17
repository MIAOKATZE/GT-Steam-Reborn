package com.miaokatze.gtsr.common.dimension.framework.structure;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;

import javax.imageio.ImageIO;

/**
 * 离线俯视 PNG 栅格化 Sink（dim1 S4a，plan D1 / §1.2 S4a / S4a 验收④）：把结构写入流栅格化为
 * XZ 俯视图（X→图像右 / Z→图像下，即图像上缘 = 北），方块→调色板颜色，供
 * {@code plan/新维度计划/review/dim1/}（S4a 验收 + S7b HTML 核对页）消费。
 * <p>
 * <b>零 Minecraft 依赖</b>（java.awt/javax.imageio + 本包纯类）：方块以 String 键（如
 * {@code "gtsr:RuinedCasing"}）参与调色板，可在无游戏运行时实例化（tools/dim1 驱动直接跑）。
 * 同列多次写入取最高 y（俯视投影露出轮廓顶面），按 y 高度做轻衰减着色增强立体可读性。
 */
public final class RasterSink implements BlockSink {

    /** 调色板：方块句柄+meta → ARGB（alpha 0 = 空气/未放置，透明）。 */
    public interface Palette extends ToIntBiFunction<Object, Integer> {

        @Override
        int applyAsInt(Object block, Integer meta);
    }

    /** 每格默认像素边长（plan：结构格数×16px）。 */
    public static final int DEFAULT_CELL_PX = 16;

    private final int originX;
    private final int originZ;
    private final int sizeX;
    private final int sizeZ;

    /** 俯视投影最高写入：下标 (lx * sizeZ + lz)。 */
    private final Object[] topBlock;
    private final int[] topMeta;
    private final int[] topY;
    private int placedCount;
    private int maxYPlaced;

    /** 默认调色板：GTSR 废墟方块族键色 + 未知句柄哈希色 + 空气族透明。 */
    public static Palette defaultPalette() {
        final Map<String, Integer> named = new HashMap<>();
        named.put("gtsr:RuinedCasing:0", 0xFFB0653A); // 锈壳：锈橙
        named.put("gtsr:RuinedCasing:1", 0xFF2E2B28); // 积碳壳：烟黑
        named.put("gtsr:RuinedCasing:2", 0xFFDDD8CE); // 碎瓷壳：青白
        named.put("gtsr:RuinDebris:0", 0xFF8A6B45); // 轨枕：木锈
        named.put("gtsr:RuinDebris:1", 0xFF7A8087); // 管道：铁灰
        named.put("gtsr:RuinDebris:2", 0xFF9AA0A6); // 铆接板：亮钢
        named.put("gtsr:RuinDebris:3", 0xFFA0522D); // 烟囱残段：砖红
        named.put("gtsr:ProsperitySurface:5", 0xFF6E6A5E); // 锈石铺面
        named.put("minecraft:stone", 0xFF7D7D7D);
        return (block, meta) -> {
            if (block == null) {
                return 0;
            }
            final String key = block + ":" + meta;
            final Integer namedColor = named.get(key);
            if (namedColor != null) {
                return namedColor;
            }
            final String name = String.valueOf(block);
            if (name.endsWith("air") || name.endsWith("Air")) {
                return 0;
            }
            // 未知句柄：由名字+meta 稳定散列出一个可辨色（避免静默黑块）
            int h = key.hashCode();
            return 0xFF000000 | ((h >> 16 & 0x7F) + 0x40) << 16 | ((h >> 8 & 0x7F) + 0x40) << 8 | (h & 0x7F) + 0x40;
        };
    }

    public RasterSink(int originX, int originZ, int sizeX, int sizeZ) {
        this.originX = originX;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.topBlock = new Object[sizeX * sizeZ];
        this.topMeta = new int[sizeX * sizeZ];
        this.topY = new int[sizeX * sizeZ];
    }

    @Override
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        final int lx = x - this.originX;
        final int lz = z - this.originZ;
        if (lx < 0 || lx >= this.sizeX || lz < 0 || lz >= this.sizeZ || y < 0 || y > 255 || block == null) {
            return false;
        }
        final int index = lx * this.sizeZ + lz;
        if (this.topBlock[index] != null && this.topY[index] > y) {
            return true; // 已有更高写入（俯视投影只保留最高层），写入本身有效但不可见
        }
        this.topBlock[index] = block;
        this.topMeta[index] = meta;
        this.topY[index] = y;
        this.placedCount++;
        this.maxYPlaced = Math.max(this.maxYPlaced, y);
        return true;
    }

    /** 已栅格化写入数（顶层投影计数，验收“非空”的数值口径）。 */
    public int getPlacedCount() {
        return this.placedCount;
    }

    /**
     * 渲染俯视 PNG。
     *
     * @param out     目标文件（父目录须已存在）
     * @param cellPx  每结构格边长像素（常用 {@link #DEFAULT_CELL_PX}）
     * @param palette 方块句柄+meta → ARGB 调色板（常用 {@link #defaultPalette()}）
     */
    public void renderPng(File out, int cellPx, Palette palette) throws IOException {
        final BufferedImage image = new BufferedImage(
            this.sizeX * cellPx,
            this.sizeZ * cellPx,
            BufferedImage.TYPE_INT_ARGB);
        for (int lx = 0; lx < this.sizeX; lx++) {
            for (int lz = 0; lz < this.sizeZ; lz++) {
                final int index = lx * this.sizeZ + lz;
                int argb = 0;
                if (this.topBlock[index] != null) {
                    argb = palette.applyAsInt(this.topBlock[index], this.topMeta[index]);
                    if (argb != 0 && this.maxYPlaced > 0) {
                        // 高度衰减着色：顶面越高越亮（0.70..1.00），轮廓层次可读
                        final double shade = 0.70 + 0.30 * ((double) this.topY[index] / Math.max(1, this.maxYPlaced));
                        argb = 0xFF000000 | scaleChannel(argb >> 16 & 0xFF, shade) << 16
                            | scaleChannel(argb >> 8 & 0xFF, shade) << 8
                            | scaleChannel(argb & 0xFF, shade);
                    }
                }
                for (int px = 0; px < cellPx; px++) {
                    for (int pz = 0; pz < cellPx; pz++) {
                        image.setRGB(lx * cellPx + px, lz * cellPx + pz, argb);
                    }
                }
            }
        }
        ImageIO.write(image, "png", out);
    }

    private static int scaleChannel(int channel, double shade) {
        return Math.min(0xFF, (int) Math.round(channel * shade));
    }
}
