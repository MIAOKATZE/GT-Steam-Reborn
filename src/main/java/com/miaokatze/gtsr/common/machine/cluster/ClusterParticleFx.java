package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

/**
 * 集群工作粒子 FX（仅客户端调用）：结构 'G' 空气标记位上方喷 "cloud" 上升云雾粒子。
 *
 * <p>
 * 形状缓存以层在前矩阵扫描：控制器相对坐标为
 * {@code (column-offsetA, layer-offsetB, depthRow-offsetC)}。延伸段与主段完整 X 对齐；每个额外段
 * 仅沿 C（深度）轴平移 8 格。本类镜像 {@link ClusterStructureDef} 的私有矩阵，改形时必须逐字符同步。
 */
public final class ClusterParticleFx {

    public static final long WORKING_WINDOW_TICKS = 40L;

    private static List<int[]> mainGasOffsets = null;
    private static List<int[]> extGasOffsets = null;

    private ClusterParticleFx() {}

    public static void spawnParticles(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        World world = cluster.getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;
        spawnOne(cluster, world, getMainGasOffsets(), 0);
        for (int k = 0, count = cluster.getExtensionCount(); k < count; k++) {
            spawnOne(
                cluster,
                world,
                getExtGasOffsets(),
                ClusterStructureDef.extOffsetC(k) - ClusterStructureDef.extOffsetC(0));
        }
    }

    public static boolean isFxWorking(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return false;
        long lastBatch = cluster.getLastBatchServerTick();
        if (lastBatch == Long.MIN_VALUE) return false;
        return cluster.getBaseMetaTileEntity()
            .getTimer() - lastBatch < WORKING_WINDOW_TICKS;
    }

    private static void spawnOne(MTESteamMineralLogisticsCluster cluster, World world, List<int[]> offsets,
        int shiftC) {
        if (offsets.isEmpty()) return;
        int[] off = offsets.get(world.rand.nextInt(offsets.size()));
        Vec3Impl worldOff = cluster.getExtendedFacing()
            .getWorldOffset(new Vec3Impl(off[0], off[1], off[2] + shiftC));
        world.spawnParticle(
            "cloud",
            cluster.getBaseMetaTileEntity()
                .getXCoord() + worldOff.get0()
                + 0.5D
                + (world.rand.nextDouble() - 0.5D) * 0.8D,
            cluster.getBaseMetaTileEntity()
                .getYCoord() + worldOff.get1()
                + 0.5D,
            cluster.getBaseMetaTileEntity()
                .getZCoord() + worldOff.get2()
                + 0.5D
                + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
    }

    private static List<int[]> getMainGasOffsets() {
        if (mainGasOffsets == null) {
            mainGasOffsets = scanShape(
                SHAPE_MAIN,
                'G',
                ClusterStructureDef.mainOffsetA(),
                ClusterStructureDef.mainOffsetB(),
                ClusterStructureDef.mainOffsetC());
        }
        return mainGasOffsets;
    }

    private static List<int[]> getExtGasOffsets() {
        if (extGasOffsets == null) {
            extGasOffsets = scanShape(
                SHAPE_EXT,
                'G',
                ClusterStructureDef.extOffsetA(0),
                ClusterStructureDef.extOffsetB(),
                ClusterStructureDef.extOffsetC(0));
        }
        return extGasOffsets;
    }

    private static List<int[]> scanShape(String[][] shape, char target, int offsetA, int offsetB, int offsetC) {
        List<int[]> offsets = new ArrayList<>();
        for (int layer = 0; layer < shape.length; layer++) {
            for (int depthRow = 0; depthRow < shape[layer].length; depthRow++) {
                String line = shape[layer][depthRow];
                for (int column = 0; column < line.length(); column++) {
                    if (line.charAt(column) == target) {
                        offsets.add(new int[] { column - offsetA, layer - offsetB, depthRow - offsetC });
                    }
                }
            }
        }
        return offsets;
    }

    // Script-generated mirror of ClusterStructureDef.SHAPE_MAIN/SHAPE_EXT. Do not hand-edit.
    private static final String[][] SHAPE_MAIN = {
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "                             ",
            "                             ", " AAA                         ", "A   A                        ",
            "A   A                        ", "A   A                        ", "A   A                        ",
            "A   A                        ", " AAA                         " },
        { "DDDDDDDDDDDD                 ", "D          D                 ", "D   AAAA   D                 ",
            "D  A----A  D                 ", "D A------A D                 ", "D A------A D                 ",
            "D A------A D                 ", "D A------A D                 ", "D  A----A  D                 ",
            "D   AAAA   D                 ", "D          D                 ", "DDDDDDDDDDDD                 ",
            "D   D                        ", "DAAAD                        ", "AGGGA                        ",
            "AGGGA                        ", "AGGGA                        ", "AGGGA                        ",
            "AGGGA                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "    AAAA                     ",
            "   A----A                    ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "   A----A                    ",
            "    AAAA                     ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "ADDDA                        ",
            "ADDDA                        ", "ADDDA                        ", "ADDDA                        ",
            "ADDDA                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "                             ", "   DAAAAD                    ",
            "  DA----AD                   ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", "  DA----AD                   ",
            "   DAAAAD                    ", "                             ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   D    D                    ", "   AAAAAA                    ",
            " DA------AD                  ", "  A------A                   ", "  A------A                   ",
            "  A------A                   ", "  A------A                   ", " DA------AD                  ",
            "   AAAAAA                    ", "   D    D                    ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A            AAA   ", " A--------A    AAAA   AEEEA  ",
            " A--------A   AEEEEAAAEEEEEA ", " A--------A   AEEEEAAAEEEEEA ", " DA------AD    AAAA   AEEEA  ",
            "   A----A              AAA   ", "   DAAAAD                    ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  A              A", " A--------A  A              A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 ",
            "                             ", "DAAAD                        ", "A---A           D   D        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A           D   D        ", "DAAAD                        " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    AAAA   A   A  ", " A--------A   A    AAA     A ",
            " A--------A  ABBBB          A", " A--------A  ABBBB          A", " DA------AD   A    AAA     A ",
            "   A----A      AAAA   A   A  ", "   DAAAAD              AAA   ", "D          D                 ",
            "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "D          D                 ", "   DAAAAD                    ", "   A----A                    ",
            " DA------AD                  ", " A--------A    EEEE   A   A  ", " A--------A   A    EEE     E ",
            " A--------A  ABBBB          E", " A--------A  ABBBB     A~A  E", " DA------AD   A    EEE     E ",
            "   A----A      EEEE   E   E  ", "   DAAAAD              EEE   ", "D          D                 ",
            "                             ", "DAAAD  DD   DD         D    D", "A---A  DFFFFFD DDD DDD  DA   ",
            "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ",
            "A---A  DFFFFFD DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "D   ----   D                 ", "   D----D                    ", "   --GG---                   ",
            " D--GGGG--D                  ", "---GGGGGG---   AAAA   A   A  ", "--GGGGGGGG--  A    AAA     A ",
            "--GGGGGGGG-- A  BB          A", "---GGGGGG--- A  BB     AAA  A", " D--GGGG--D   A    AAA     A ",
            "  ---GG---     AAAA   A   A  ", "   D----D              AAA   ", "D   ----   D                 ",
            "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEAFFFAEEAAFFF ", "A---BBBBBBBBBBBBBFFFBBBBBFFF ", "A---AEEEAABAAEEEAFFFAEEAAFFF ",
            "A---A  DAAAAAD DAAAAAD  AAAAD", "DAAAD  DD   DD  DD DD  D   DD" },
        { "D   AAAA   D                 ", "   DAAAAD                    ", "   AABBAAA                   ",
            " DAABBBBAAD    AAAA   AAAAA  ", "AAABBBBBBAAA  AAAAAAAAAAAAAA ", "AABBBBBBBBAA AACCCCAAAAAAAAAA",
            "AABBBBBBBBAA AAABBCAAAAAAAAAA", "AAABBBBBBAAA AAABBCAAAAAAAAAA", " DAABBBBAAD  AACCCCAAAAAAAAAA",
            "  AAABBAAA    AAAAAAAAAAAAAA ", "   DAAAAD      AAAA   AAAAA  ", "D   AAAA   D           AAA   ",
            "                             ", "DAAAD  DACCCAD  ACCCA  DACCAD", "ACCCA  AAAAAAA DAAAAAD AAAAAA",
            "ACCCAEEAAAAAAAEEAAAAAEEAAAAAA", "ACCCAAAAAAAAAAAAAAAAAAAAAAAAA", "ACCCAEEAAAAAAA  AAAAAEEAAAAAA",
            "ACCCA  AAAAAAA DAAAAAD AAAAAA", "DAAAD  DACCCAD  ACCCA  DACCAD" } };
    private static final String[][] SHAPE_EXT = {
        { "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", "                             ", "                             ",
            "                             ", "                             ", "                             ",
            "                             ", "                             " },
        { "                             ", " AAA                         ", "A   A                        ",
            "A   A                        ", "A   A                        ", "A   A                        ",
            "A   A                        ", " AAA                         " },
        { "D   D                        ", "DAAAD                        ", "AGGGA                        ",
            "AGGGA                        ", "AGGGA                        ", "AGGGA                        ",
            "AGGGA                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "ADDDA                        ",
            "ADDDA                        ", "ADDDA                        ", "ADDDA                        ",
            "ADDDA                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A                        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A                        ", "DAAAD                        " },
        { "                             ", "DAAAD                        ", "A---A           D   D        ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A           D   D        ", "DAAAD                        " },
        { "                             ", "DAAAD  D     D               ", "A---A          DD   DD  D    ",
            "A---A                        ", "A---A                        ", "A---A                        ",
            "A---A          DD   DD  D    ", "DAAAD  D     D               " },
        { "                             ", "DAAAD  DD   DD         D    D", "A---A  DFFFFFD DDD DDD  DA   ",
            "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ", "A---AEEEFFFFFEEEE   EEEEA    ",
            "A---A  DFFFFFD DDD DDD  DA   ", "DAAAD  DD   DD         D    D" },
        { "                             ", "DAAAD  DD   DD  DD DD  D   DD", "A---A  DAAAAAD DAAAAAD  AAAAD",
            "A---AEEEAABAAEEEAFFFAEEAAFFF ", "A---BBBBBBBBBBBBBFFFBBBBBFFF ", "A---AEEEAABAAEEEAFFFAEEAAFFF ",
            "A---A  DAAAAAD DAAAAAD  AAAAD", "DAAAD  DD   DD  DD DD  D   DD" },
        { "                             ", "DAAAD  DACCCAD  ACCCA  DACCAD", "ACCCA  AAAAAAA DAAAAAD AAAAAA",
            "ACCCAEEAAAAAAAEEAAAAAEEAAAAAA", "ACCCAAAAAAAAAAAAAAAAAAAAAAAAA", "ACCCAEEAAAAAAA  AAAAAEEAAAAAA",
            "ACCCA  AAAAAAA DAAAAAD AAAAAA", "DAAAD  DACCCAD  ACCCA  DACCAD" } };
}
