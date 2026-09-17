package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 结构放置原语（dim1 S4a，plan D1 / §1.2 S4a）：setBlock/fill/hollowBox/column/line/ramp/clear
 * 统一走注入的 {@link BlockSink}（钳制/直写/栅格化语义由 Sink 决定，本类零 Minecraft 依赖）；
 * 四元旋转原语（0/90/180/270）在原语层完成（plan §3.2 朝向口径，S4b 城市消费）。
 * <p>
 * 坐标口径：原语一律世界坐标绝对值（调用方自行换算原点）；旋转辅助为纯函数（本地坐标→旋转后本地坐标）。
 */
public final class StructureBuilder {

    private final BlockSink sink;

    public StructureBuilder(BlockSink sink) {
        this.sink = sink;
    }

    public BlockSink getSink() {
        return this.sink;
    }

    /** 单方块写入（转 {@link BlockSink#setBlock}）。 */
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        return this.sink.setBlock(x, y, z, block, meta, flags);
    }

    /** 实心长方体（含边界；坐标任意序，内部取 min/max）。 */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Object block, int meta, int flags) {
        final int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        final int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        final int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    this.sink.setBlock(x, y, z, block, meta, flags);
                }
            }
        }
    }

    /** 空腔长方体（六面壳，内部清空为 {@code interiorBlock}）。 */
    public void hollowBox(int x0, int y0, int z0, int x1, int y1, int z1, Object block, int meta, Object interiorBlock,
        int flags) {
        final int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        final int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        final int minZ = Math.min(z0, z1), maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    this.sink.setBlock(x, y, z, shell ? block : interiorBlock, shell ? meta : 0, flags);
                }
            }
        }
    }

    /** 竖柱（x,z 固定，y 从 y0 到 y1）。 */
    public void column(int x, int z, int y0, int y1, Object block, int meta, int flags) {
        final int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        for (int y = minY; y <= maxY; y++) {
            this.sink.setBlock(x, y, z, block, meta, flags);
        }
    }

    /** 轴对齐直线（三点共线或单点；支持 X/Y/Z 任意主轴）。 */
    public void line(int x0, int y0, int z0, int x1, int y1, int z1, Object block, int meta, int flags) {
        final int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        final int steps = Math.max(dx, Math.max(dy, dz));
        for (int i = 0; i <= steps; i++) {
            final int x = steps == 0 ? x0 : x0 + (x1 - x0) * i / steps;
            final int y = steps == 0 ? y0 : y0 + (y1 - y0) * i / steps;
            final int z = steps == 0 ? z0 : z0 + (z1 - z0) * i / steps;
            this.sink.setBlock(x, y, z, block, meta, flags);
        }
    }

    /**
     * 45° 台阶坡道：沿主轴（dx 跨度较大者）逐格上升，副轴恒定；每步一格高差。
     * 上升方向由 y1-y0 符号决定；跨度 0 时退化为竖柱。
     */
    public void ramp(int x0, int y0, int z0, int x1, int y1, int z1, Object block, int meta, int flags) {
        final int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        final int span = Math.max(dx, dz);
        if (span == 0) {
            this.column(x0, z0, y0, y1, block, meta, flags);
            return;
        }
        for (int i = 0; i <= span; i++) {
            final int x = dx >= dz ? x0 + Integer.compare(x1, x0) * i : x0;
            final int z = dx >= dz ? z0 : z0 + Integer.compare(z1, z0) * i;
            final int y = y0 + (y1 - y0) * i / span;
            this.sink.setBlock(x, y, z, block, meta, flags);
        }
    }

    /** 清空长方体为空气句柄（等价 fill(interior=air)；air 句柄由调用方给——游戏内 Blocks.air）。 */
    public void clear(int x0, int y0, int z0, int x1, int y1, int z1, Object airHandle, int flags) {
        this.fill(x0, y0, z0, x1, y1, z1, airHandle, 0, flags);
    }

    // —— 四元旋转原语（本地坐标顺时针 90°/步；S4b 朝向用，plan §3.2） ——

    /** 旋转后 footprint 尺寸（奇数转 X/Z 互换）。rot ∈ 0..3。 */
    public static int[] rotateSize(int sizeX, int sizeZ, int rot) {
        return (rot & 1) == 0 ? new int[] { sizeX, sizeZ } : new int[] { sizeZ, sizeX };
    }

    /**
     * 本地坐标 (dx,dz)（0..size-1）经 rot 次顺时针 90° 旋转后的本地坐标（旋转后 footprint 内）。
     * rot=0 → 原样；rot=1 → (sizeZ-1-dz, dx)；rot=2 → (sizeX-1-dx, sizeZ-1-dz)；rot=3 → (dz, sizeX-1-dx)。
     */
    public static int[] rotateDelta(int dx, int dz, int rot, int sizeX, int sizeZ) {
        switch (rot & 3) {
            case 1:
                return new int[] { sizeZ - 1 - dz, dx };
            case 2:
                return new int[] { sizeX - 1 - dx, sizeZ - 1 - dz };
            case 3:
                return new int[] { dz, sizeX - 1 - dx };
            default:
                return new int[] { dx, dz };
        }
    }
}
