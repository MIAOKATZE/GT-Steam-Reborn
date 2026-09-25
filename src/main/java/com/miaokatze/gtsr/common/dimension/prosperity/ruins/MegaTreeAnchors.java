package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * 岛心巨树<b>锚点纯函数</b>（P22 版 B · S3，p21 §4 需求"巨湖中心岛固定生成巨型垂天玄柯"）：
 * 回答"世界列 (x,z) 属于哪座活湖 ⇒ 岛心锚点在哪、树基 y0 是多少"，<b>零 World 读</b>
 * （三腿全部走 {@link GTSRVoronoiRiverField} 与 {@link ProsperityTerrainProfile} 的公开纯函数）。
 * <p>
 * <b>为什么锚点必须是纯函数</b>：单树冠幅 31 ⇒ 至多跨 3×3 = 9 chunk ⇒ 任何一个被跨的 chunk
 * 在自己的 populate 趟里<b>独立重算</b>同一棵树、只写自己 owns 的那一片（{@code ChunkSliceSink}
 * 单射，先例 {@code RuinedMachinePlacer.renderForeignSpans}）。锚点若掺任何 World 读
 * （表面扫描/占位检测），不同 chunk 装饰时点不同就会各得各的锚 ⇒ 同一棵树在不同 chunk 里
 * 长成两副模样。故本类一条 World 路径都不存在；让行只发生在 {@code IslandMegaTree} 的
 * <b>逐格</b>叶门（owner-local，见该类注释）。
 * <p>
 * <b>锚点定义（三腿）</b>：
 * <ol>
 * <li>湖心 = {@link GTSRVoronoiRiverField#lakeCellCenterAt} 的压力零点（含 domain-warp 反解，
 * 而不是湖站本身——S5b 修过的 26.86 格中位位移缺陷，见该方法注释）；主干带外（{@code trunkAt
 * ≤ 0} ⇒ 无湖）返回失败；</li>
 * <li><b>活湖门</b> = 岛心列 {@code lakeAt < LAKE_ISLAND}（与 {@code islandPillarAt} 同一条
 * 压力腿域门）：死湖（域值 ≥ LAKE_ISLAND，湖心无岛穹）无岛无树；</li>
 * <li>岛心列 = {@code round(湖心)}，<b>锚点去重按 Worley 格 (gx,gz)</b>：同一格的全部查询列
 * 得同一锚点（湖心反解只依赖获胜格的站坐标，与查询列无关），不同格 = 不同锚点。</li>
 * </ol>
 * 树基 {@code y0 = heightAt(worldSeed, ax, az)} <b>实值</b>（岛面平台 = {@code SEA_LEVEL +
 * LAKE_ISLAND_LIFT} = 72 量级，但禁止写死假设值——heightAt 已含巨湖压低链与钳制，判据
 * {@code tools/dim1/MegaTreeCheck} C 组按实测带钉）。
 */
public final class MegaTreeAnchors {

    /** 冠半径（直径 30 硬值的半边；巨树形态常量归锚点侧，{@code IslandMegaTree} 只消费）。 */
    public static final int CANOPY_RADIUS = 15;

    /** anchorAt 输出缓冲长度：[0]=ax、[1]=az、[2]=gx、[3]=gz、[4]=y0。 */
    public static final int ANCHOR_OUT_LEN = 5;

    /** 枚举采样步长：任意列到最近采样列 ≤ 4 格，warp 场波长 320 / 幅度 70 ⇒ 采样列与岛心同格（见类注释采样论证）。public = 判据镜像同一网格。 */
    public static final int ENUM_STEP = 8;

    /** 单 chunk 窗内可能相交的锚点数上限：湖格间隔 {@code LAKE_INTERVAL}=1200 ≫ 窗宽 48，>1 已不可能，留 4 防御。 */
    static final int ENUM_CAP = 4;

    private MegaTreeAnchors() {}

    /**
     * 跨 chunk 枚举窗边长（chunk 数，恒奇数）：派生式 {@code 2*ceil((2*radius+1)/16)-1}——
     * 锚点列距本 chunk 任一列 ≤ radius ⇒ 落在以本 chunk 为心的 (2h+1)² 窗内（h = 派生半窗）。
     * radius=15 ⇒ 3；<b>禁写死 3</b>：换冠幅档时窗口自动跟随（判据 B 组按公式对拍 + 逐半径穷举钉）。
     * <b>前提（S3 偏离 1 申报，MegaTreeCheck B 组容差腿覆盖）</b>：本公式对 r≤7 的档几何不足
     * （窗宽 < 2r+1 的穷举覆盖），完备性由采样容差腿（B 组 r=4 实测 gap=12、drift=40.3<180）
     * 保证；本类唯一消费档 r=15 时窗与穷举等价。
     */
    public static int windowChunks(int radius) {
        return 2 * ((2 * radius + 1 + 15) / 16) - 1;
    }

    /**
     * 世界列 (x,z) 的活湖锚点。失败（主干带外 / 死湖）返回 false 且不承诺 out 内容；
     * 成功写 [0]=岛心 ax、[1]=岛心 az、[2]=格 gx、[3]=格 gz、[4]=树基 y0（heightAt 实值）。
     */
    public static boolean anchorAt(long worldSeed, int x, int z, double[] out) {
        GTSRVoronoiRiverField.lakeCellCenterAt(worldSeed, x, z, out);
        if (((int) out[2]) == Integer.MIN_VALUE) {
            return false;
        }
        final int ax = (int) Math.round(out[0]);
        final int az = (int) Math.round(out[1]);
        if (GTSRVoronoiRiverField.lakeAt(worldSeed, ax, az) >= GTSRVoronoiRiverField.LAKE_ISLAND) {
            return false;
        }
        out[0] = ax;
        out[1] = az;
        out[4] = ProsperityTerrainProfile.heightAt(worldSeed, ax, az);
        return true;
    }

    /** 锚点树冠 bbox [ax−r, ax+r]×[az−r, az+r] 是否与本 chunk 相交（{@code >>4} 同 ChunkSliceSink 语义）。 */
    public static boolean intersectsChunk(int ax, int az, int radius, int chunkX, int chunkZ) {
        final int minX = chunkX << 4;
        final int minZ = chunkZ << 4;
        return ax + radius >= minX && ax - radius <= minX + 15 && az + radius >= minZ && az - radius <= minZ + 15;
    }

    /**
     * 枚举与本 chunk 相交的全部活湖锚点（窗 = {@link #windowChunks} 派生；采样步 {@link #ENUM_STEP}，
     * 去重按 (gx,gz)）。<b>采样论证</b>（步 8 够用的原因）：岛心 c 是其格压力零点 ⇒ 列 c 的 warped
     * 位与湖站距离 ≈ 0；最近采样列 s 距 c ≤ 4 格，warp 场梯度上界 ≈ {@code LAKE_WARP·2π/
     * LAKE_WARP_SCALE} ≈ 1.37/格 ⇒ s 的 warped 位距本格湖站 ≤ ~8 格，而相邻湖站最小间距 =
     * {@code LAKE_INTERVAL − 2·CELL_JITTER·LAKE_INTERVAL} = 360 格 ⇒ 获胜格不变，锚点不漏。
     * out 行数 ≥ {@link #ENUM_CAP}（每行长 ≥ {@link #ANCHOR_OUT_LEN}）；返回写入行数。
     */
    public static int enumerateAnchors(long worldSeed, int chunkX, int chunkZ, int radius, double[][] out) {
        final int window = windowChunks(radius);
        final int half = (window - 1) >> 1;
        final int baseX = (chunkX - half) << 4;
        final int baseZ = (chunkZ - half) << 4;
        final double[] buf = new double[ANCHOR_OUT_LEN];
        int n = 0;
        for (int z = baseZ + (ENUM_STEP >> 1); z < baseZ + window * 16 && n < ENUM_CAP; z += ENUM_STEP) {
            for (int x = baseX + (ENUM_STEP >> 1); x < baseX + window * 16 && n < ENUM_CAP; x += ENUM_STEP) {
                if (!anchorAt(worldSeed, x, z, buf)) {
                    continue;
                }
                final int ax = (int) buf[0];
                final int az = (int) buf[1];
                if (!intersectsChunk(ax, az, radius, chunkX, chunkZ)) {
                    continue;
                }
                boolean dup = false;
                for (int i = 0; i < n; i++) {
                    if ((int) out[i][2] == (int) buf[2] && (int) out[i][3] == (int) buf[3]) {
                        dup = true;
                        break;
                    }
                }
                if (dup) {
                    continue;
                }
                System.arraycopy(buf, 0, out[n], 0, ANCHOR_OUT_LEN);
                n++;
            }
        }
        return n;
    }
}
