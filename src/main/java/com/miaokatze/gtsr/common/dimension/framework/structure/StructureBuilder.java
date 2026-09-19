package com.miaokatze.gtsr.common.dimension.framework.structure;

/**
 * 结构放置门面（dim1 S4a，plan D1 / §1.2 S4a）：把逐格写入统一收进注入的 {@link BlockSink}
 * （钳制/直写/栅格化语义由 Sink 决定，本类零 Minecraft 依赖）；四元旋转原语（0/90/180/270）
 * 在本层完成（plan §3.2 朝向口径，S4b 城市消费）。
 * <p>
 * 坐标口径：一律世界坐标绝对值（调用方自行换算原点）；旋转辅助为纯函数（本地坐标→旋转后本地坐标）。
 * <p>
 * <b>P13 收口（原语面按实测消费方裁剪）</b>：本类曾申报 {@code setBlock/fill/hollowBox/column/
 * line/ramp/clear} 七件套（旧类注释即那份清单）。P13 全仓穷举 grep（{@code src} 359 个 .java +
 * {@code tools/dim1} 34 个 .java，含全部离线驱动与断言件）实测：<b>真实结构/装饰/城/废墟/husk 的
 * 落块全部走字符盘逐格 {@code setBlock}</b>（模板本身是字符串层，不是几何体，故 fill/box/column/line
 * 从未被消费），{@code fill}/{@code hollowBox}/{@code column}/{@code line}/{@code clear}/{@code ramp}/
 * {@code getSink} 七个成员消费方计数均为 <b>0</b>（{@code column} 的唯一调用方是同样零消费的
 * {@code ramp}，{@code clear} 的唯一调用面是同样零消费的 {@code fill}——即一整片死簇），故整片
 * 删除。删除后本类可见面 = 构造器 + {@link #setBlock} + 两个静态旋转件，与实测消费面逐一对齐；
 * 512 chunk 逐字节对拍（{@code surface_checks.sh --parity} 的 [18]）实测 {@code diff_lines=0}
 * 证零行为变化。Phase 2 若确有几何体模板需求，按 git 历史原样取回即可（无需保留空壳）。
 */
public final class StructureBuilder {

    private final BlockSink sink;

    public StructureBuilder(BlockSink sink) {
        this.sink = sink;
    }

    /** 单方块写入（转 {@link BlockSink#setBlock}）——名册模板/装饰/机件的唯一落块通道。 */
    public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
        return this.sink.setBlock(x, y, z, block, meta, flags);
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
            case 1: {
                return new int[] { sizeZ - 1 - dz, dx };
            }
            case 2: {
                return new int[] { sizeX - 1 - dx, sizeZ - 1 - dz };
            }
            case 3: {
                return new int[] { dz, sizeX - 1 - dx };
            }
            default: {
                return new int[] { dx, dz };
            }
        }
    }
}
