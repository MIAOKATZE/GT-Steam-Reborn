import java.io.File;

import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;

/**
 * S4a 验收④ 离线驱动（不进 jar，tools/ 惯例）：把 {@link RuinedMachineShapes} 5 机型经
 * StructureBuilder → RasterSink 栅格化为俯视 PNG，落 plan/新维度计划/review/dim1/structures/
 * （S7b HTML 核对页输入）。纯 JEP 330 单文件运行，零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/RasterStructurePreview.java [outDir]}
 * <p>
 * char→(方块键, meta) 映射取自 RuinedMachineShapes.blockKeyOf/metaOf（与游戏内放置器同一张表）；
 * '.'（游戏内清空气）与空格（不触碰）离线投影不写=透明背景。
 */
public class RasterStructurePreview {

    public static void main(String[] args) throws Exception {
        final File outDir = new File(
            args.length > 0 ? args[0]
                : "plan/新维度计划/review/dim1/structures");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("cannot create output dir: " + outDir.getAbsolutePath());
        }
        for (RuinedMachineShapes.Shape shape : RuinedMachineShapes.ALL) {
            final RasterSink sink = new RasterSink(0, 0, shape.sizeX, shape.sizeZ);
            final StructureBuilder builder = new StructureBuilder(sink);
            for (int y = 0; y < shape.sizeY; y++) {
                for (int dx = 0; dx < shape.sizeX; dx++) {
                    for (int dz = 0; dz < shape.sizeZ; dz++) {
                        final char c = shape.charAt(y, dx, dz);
                        final String key = RuinedMachineShapes.blockKeyOf(c);
                        if (key == null) {
                            continue;
                        }
                        builder.setBlock(dx, y, dz, key, RuinedMachineShapes.metaOf(c), 0);
                    }
                }
            }
            final File out = new File(outDir, shape.name + ".png");
            sink.renderPng(out, RasterSink.DEFAULT_CELL_PX, RasterSink.defaultPalette());
            System.out.println(
                "RASTER " + shape.name + " " + shape.sizeX + "x" + shape.sizeY + "x" + shape.sizeZ + " placed="
                    + sink.getPlacedCount()
                    + " -> "
                    + out.getAbsolutePath()
                    + " ("
                    + shape.sizeX * RasterSink.DEFAULT_CELL_PX
                    + "x"
                    + shape.sizeZ * RasterSink.DEFAULT_CELL_PX
                    + "px)");
        }
        System.out.println("RASTER DONE " + RuinedMachineShapes.ALL.length + " structures");
    }
}
