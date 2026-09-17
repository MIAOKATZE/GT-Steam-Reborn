package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

/**
 * 残缺蒸汽机器形状库（dim1 S4a，plan §1.2 S4a / 02 §6 意象）：5 机型纯数据形状，
 * 尺寸 5-13 格（任务包口径），'C' 核心位无 TileEntity（放置时落积碳壳 meta1，plan S4a）。
 * <p>
 * 记号（02 §6.1 同款）：{@code #}=锈壳(meta0) {@code @}=积碳壳(meta1) {@code %}=碎瓷壳(meta2)
 * {@code C}=核心位(落积碳壳 meta1) {@code .}=清空为空气 {@code 空格}=不触碰（地形让行）。
 * <p>
 * 层序：{@code layers[0]} 为<b>顶层</b>（y = h-1），逐层向下到 {@code layers[h-1]}（y = 0 基座层）。
 * 本类<b>零 Minecraft 依赖</b>：char→(方块键, meta) 经 {@link #blockKeyOf(char)}/{@link #metaOf(char)}
 * 以 String 键表达（如 {@code "gtsr:RuinedCasing"}），离线 RasterSink 驱动与游戏内放置器共用同一张表。
 * 类加载即校验全部形状串尺寸（fail fast，防止坏数据静默进世界）。
 */
public final class RuinedMachineShapes {

    /** 单个机型形状：注册名 + footprint (sizeX×sizeZ) + 层数 sizeY + 自上而下形状串。 */
    public static final class Shape {

        public final String name;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        /** layers[0] = 顶层；layers[layer] 为该层 sizeZ 行、每行 sizeX 字符。 */
        public final String[][] layers;

        Shape(String name, int sizeX, int sizeY, int sizeZ, String[][] layers) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layers = layers;
        }

        /** 取（世界向上 y ∈ 0..sizeY-1，dx ∈ 0..sizeX-1，dz ∈ 0..sizeZ-1）处字符。 */
        public char charAt(int y, int dx, int dz) {
            return this.layers[this.sizeY - 1 - y][dz].charAt(dx);
        }
    }

    /** ① 锅炉框架（7×6×7）：立式锅炉方筒外壳，东墙炉门拱口，顶部大面积塌失（02 §6 蒸汽锅炉意象）。 */
    public static final Shape BOILER_FRAME = new Shape(
        "boiler_frame",
        7,
        6,
        7,
        new String[][] {
            // y=5 顶层（塌失残顶）
            { "       ", " ##### ", " ##.## ", " #...# ", "       ", "       ", "       " },
            // y=4 筒壁环
            { "       ", " ##### ", " #...# ", " #...# ", " #...# ", " ##### ", "       " },
            // y=3 筒壁 + 南墙瞭望口
            { "       ", " ##### ", " #...# ", " #..@# ", " #...# ", " ##.## ", "       " },
            // y=2 筒壁 + 东墙炉门 + 核心位
            { "       ", " ##### ", " #...# ", " #.C.. ", " #...# ", " ##### ", "       " },
            // y=1 炉膛积碳底
            { "       ", " ##### ", " #@@@# ", " #@C@# ", " #@@@# ", " ##### ", "       " },
            // y=0 碎瓷壳垫层
            { "%%%%%%%", "%%%%%%%", "%%%%%%%", "%%%%%%%", "%%%%%%%", "%%%%%%%", "%%%%%%%" } });

    /** ② 泵座（5×3×5）：矮台基座 + 四角柱脚残梗 + 中央泵心积碳残芯。 */
    public static final Shape PUMP_BASE = new Shape(
        "pump_base",
        5,
        3,
        5,
        new String[][] {
            // y=2 四角柱脚 + 中央泵梗
            { "#...#", ".....", "..@..", ".....", "#...#" },
            // y=1 座圈 + 泵心（核心位）
            { "#####", "#.@.#", "#@C@#", "#.@.#", "#####" },
            // y=0 碎瓷壳垫层
            { "%%%%%", "%%%%%", "%%%%%", "%%%%%", "%%%%%" } });

    /** ③ 齿轮碾磨台（6×4×6）：四腿台面 + 台面中央碎瓷齿环包积碳磨芯（02 §6 齿轮化意象）。 */
    public static final Shape GEAR_MILL_TABLE = new Shape(
        "gear_mill_table",
        6,
        4,
        6,
        new String[][] {
            // y=3 齿环 + 磨芯
            { "      ", "  %%  ", " %@@% ", " %@@% ", "  %%  ", "      " },
            // y=2 台面板
            { "######", "######", "######", "######", "######", "######" },
            // y=1 四腿 + 中梁残梗
            { "#....#", "......", "..@@..", "......", "#....#", "......" },
            // y=0 碎瓷壳垫层
            { "%%%%%%", "%%%%%%", "%%%%%%", "%%%%%%", "%%%%%%", "%%%%%%" } });

    /** ④ 蒸汽管廊（13×4×3）：墩柱 + 双排管槽直线段，顶梁中跨坍塌（端头/中跨断口）。 */
    public static final Shape STEAM_GALLERY = new Shape(
        "steam_gallery",
        13,
        4,
        3,
        new String[][] {
            // y=3 顶梁：中跨坍塌断口
            { "#...#   #...#", "#...#   #...#", "#...#   #...#" },
            // y=2 墩柱上层 + 第二排管
            { "#   #   #   #", "@@@@@@@@@@@@@", "#   #   #   #" },
            // y=1 墩柱 + 主管排
            { "#   #   #   #", "@@@@@@@@@@@@@", "#   #   #   #" },
            // y=0 碎瓷壳墩基
            { "%   %   %   %", "%   %   %   %", "%   %   %   %" } });

    /** ⑤ 烟囱基座（5×8×5）：方形烟囱束，中空烟道积烟，顶部破口锯齿。 */
    public static final Shape CHIMNEY_BASE = new Shape(
        "chimney_base",
        5,
        8,
        5,
        new String[][] {
            // y=7 破口锯齿
            { " ##  ", "  #  ", "  #  ", "     ", "   ##" },
            // y=6 锯齿檐口
            { "##.##", "#...#", "#...#", "#...#", "#####" },
            // y=5..1 筒身（烟道积烟）
            { "#####", "#...#", "#.@.#", "#...#", "#####" }, { "#####", "#...#", "#.@.#", "#...#", "#####" },
            { "#####", "#...#", "#.@.#", "#...#", "#####" }, { "#####", "#...#", "#.@.#", "#...#", "#####" },
            { "#####", "#@@.#", "#@..#", "#...#", "#####" },
            // y=0 碎瓷壳垫层
            { "%%%%%", "%%%%%", "%%%%%", "%%%%%", "%%%%%" } });

    /** 5 机型（放置时均匀掷选）。 */
    public static final Shape[] ALL = { BOILER_FRAME, PUMP_BASE, GEAR_MILL_TABLE, STEAM_GALLERY, CHIMNEY_BASE };

    static {
        for (Shape shape : ALL) {
            if (shape.layers.length != shape.sizeY) {
                throw new IllegalStateException(
                    "[GTSR] ruined machine shape " + shape.name
                        + ": layer count "
                        + shape.layers.length
                        + " != sizeY "
                        + shape.sizeY);
            }
            for (int y = 0; y < shape.sizeY; y++) {
                final String[] rows = shape.layers[y];
                if (rows.length != shape.sizeZ) {
                    throw new IllegalStateException(
                        "[GTSR] ruined machine shape " + shape.name
                            + ": layer "
                            + y
                            + " row count "
                            + rows.length
                            + " != sizeZ "
                            + shape.sizeZ);
                }
                for (int z = 0; z < rows.length; z++) {
                    if (rows[z].length() != shape.sizeX) {
                        throw new IllegalStateException(
                            "[GTSR] ruined machine shape " + shape.name
                                + ": layer "
                                + y
                                + " row "
                                + z
                                + " length "
                                + rows[z].length()
                                + " != sizeX "
                                + shape.sizeX);
                    }
                }
            }
        }
    }

    private RuinedMachineShapes() {}

    /**
     * char → 方块键（游戏内放置器解析为 Block 实例；离线 RasterSink 直接作调色板键）。
     * '.'（空气）与空格（不触碰）返回 null。
     */
    public static String blockKeyOf(char c) {
        switch (c) {
            case '#':
            case '@':
            case '%':
            case 'C':
                return "gtsr:RuinedCasing";
            default:
                return null;
        }
    }

    /** char → meta（与 {@link #blockKeyOf(char)} 配对；'C' = 积碳壳 meta1，无 TE）。 */
    public static int metaOf(char c) {
        switch (c) {
            case '@':
            case 'C':
                return 1;
            case '%':
                return 2;
            default:
                return 0;
        }
    }
}
