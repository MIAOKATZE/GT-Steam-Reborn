import java.io.File;

import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 验收④ 离线驱动（不进 jar，tools/ 惯例）：把 {@link CityVariants} 代表城变体经
 * StructureBuilder → RasterSink 栅格化为俯视 PNG，落 plan/维度计划/设计册与实施计划/review/dim1/structures/city/
 * （S7b HTML 核对页输入，命名=变体名.png）。覆盖全部 5 类别 ≥6 变体（含核心/工业/边缘各≥1），
 * plotSeed 取中损档（tier=1）样本种子（世界生成同一 place 管线，损伤档/朝向可复现）。
 * 纯 JEP 330 单文件运行，零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityRasterPreview.java [outDir]}
 */
public class CityRasterPreview {

    /** 代表变体（类别覆盖：civic 核心 / industry 工业 / infra / tower / rubble 边缘）。 */
    private static final String[] REPRESENTATIVES = { "dome_hall", "market_colonnade", "boiler_house", "gas_holder",
        "pressure_tank_row", "viaduct", "broken_bridge", "watch_tower", "cooling_tower", "clock_tower", "slag_heap",
        "broken_pillars" };

    public static void main(String[] args) throws Exception {
        final File outDir = new File(
            args.length > 0 ? args[0]
                : "plan/维度计划/设计册与实施计划/review/dim1/structures/city");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("cannot create output dir: " + outDir.getAbsolutePath());
        }
        int done = 0;
        for (final String name : REPRESENTATIVES) {
            final CityVariants.Variant v = CityVariants.byName(name);
            if (v == null) {
                throw new IllegalStateException("variant not found: " + name);
            }
            // 取中损档（tier=1）样本 plotSeed（与世界生成同一 damageTier/rotationOf 纯函数）
            long plotSeed = -1;
            for (long s = 0; s < 100000; s++) {
                if (CityVariants.damageTier(s) == 1) {
                    plotSeed = s;
                    break;
                }
            }
            final RasterSink sink = new RasterSink(0, 0, v.sizeX, v.sizeZ);
            // 离线预览过滤 '.' 内腔清空气写入（游戏内语义 = 清空内腔；俯视投影则保留其下垫层轮廓）
            final com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink previewSink =
                (x, y, z, block, meta, flags) -> String.valueOf(block).endsWith("air")
                    ? true
                    : sink.setBlock(x, y, z, block, meta, flags);
            CityVariants.place(v, previewSink, 0, 0, (x, z) -> 0, plotSeed, 0);
            final File out = new File(outDir, v.name + ".png");
            sink.renderPng(out, RasterSink.DEFAULT_CELL_PX, gtAccentPalette());
            System.out.println(
                "RASTER " + v.name + " (" + v.category + ") " + v.sizeX + "x" + v.sizeY + "x" + v.sizeZ + " placed="
                    + sink.getPlacedCount()
                    + " plotSeed="
                    + plotSeed
                    + " -> "
                    + out.getAbsolutePath());
            done++;
        }
        System.out.println("RASTER DONE " + done + " city variants -> " + outDir.getAbsolutePath());
    }

    /**
     * 调色板 = defaultPalette() + GT5U casing 白名单两键色（S-A4：'X' 镀铜砖块 meta10 铜橙 /
     * 'Z' 固体钢机械外壳 meta0 冷钢蓝灰；framework/ 零改动，包装仅在预览侧补名）。
     */
    private static RasterSink.Palette gtAccentPalette() {
        final RasterSink.Palette base = RasterSink.defaultPalette();
        return (block, meta) -> {
            final String key = block + ":" + meta;
            if ("gt5u:CasingBronzePlated:10".equals(key)) {
                return 0xFFB87333; // 镀铜砖块：铜橙
            }
            if ("gt5u:CasingSolidSteel:0".equals(key)) {
                return 0xFF8C9BA5; // 固体钢机械外壳：冷钢蓝灰
            }
            return base.applyAsInt(block, meta);
        };
    }
}
