package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.function.IntFunction;

import net.minecraft.block.Block;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 繁荣蒸汽时代遗迹（dim78）四群系持有者 + 注册入口（dim1 S2，plan §1.2 S2）。
 * <p>
 * 职责：
 * <ol>
 * <li>preInit（BlockLoader 之后，地表方块已注册）调用 {@link #init}：按
 * {@link Config#prosperityBiomeIdStart}（默认 180..183）逐个经
 * {@link GTSRBiomeBase#isBiomeIdFree} 校验后再构造（构造即占 biomeList 槽且不可回退），
 * 被占群系跳过 + 告警，维度以剩余群系降级运行（plan R3）；</li>
 * <li>把 4 群系按 02 §1.1 权重表 45/30/15/10 挂入 def（{@link GTSRDimensionDef#addBiome}），
 * 由 GTSRWorldChunkManager 展开为 per-chunk 选择表，空表回退逻辑（S1）保持；</li>
 * <li>补齐 fillerBlock meta 写入（{@link #applyFillerMeta}）。</li>
 * </ol>
 * BiomeDictionary 注册与草色覆写在各群系类构造器/方法内自持；不调 addSpawnBiome（维度专属群系）。
 * 成功日志锚点（plan §6.1 grep 点）：{@code [GTSR] prosperity biomes: 180 181 182 183 registered}。
 */
public final class ProsperityBiomes {

    /** 本维度群系槽数（biomeIdStart .. biomeIdStart+3）。 */
    private static final int BIOME_SLOT_COUNT = 4;

    /** meta 补写扫描带上界（原版 genBiomeTerrain 的 topBand 从 y=62 起，filler 只会出现在其下方）。 */
    private static final int TOP_BAND_MIN_Y = 62;

    private ProsperityBiomes() {}

    /**
     * S2 注册入口：构造四群系（先空闲校验后构造）并挂入 def 权重表。
     * 总开关关闭时跳过全部构造（biomeList 零占用，维度已由 DimensionRegistrar 按开关禁用）。
     */
    public static void init(GTSRDimensionDef def) {
        if (def == null) {
            return;
        }
        if (!Config.planDimension.prosperityDimension) {
            GTSteamReborn.LOG
                .info("[GTSR] prosperity biomes skipped: dimension disabled by master switch (planDimension config)");
            return;
        }
        final int start = Config.prosperityBiomeIdStart;
        final StringBuilder ids = new StringBuilder();
        int registered = 0;
        registered += attachBiome(def, start + 0, BiomeRustedSteppe.WEIGHT, BiomeRustedSteppe::new, ids);
        registered += attachBiome(def, start + 1, BiomeGearworkForest.WEIGHT, BiomeGearworkForest::new, ids);
        registered += attachBiome(def, start + 2, BiomeBrassWastes.WEIGHT, BiomeBrassWastes::new, ids);
        registered += attachBiome(def, start + 3, BiomeFumaroleSwamp.WEIGHT, BiomeFumaroleSwamp::new, ids);
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity biomes: " + (ids.length() == 0 ? "(none)"
                : ids.toString()
                    .trim())
                + " registered (idStart="
                + start
                + ", "
                + registered
                + "/"
                + BIOME_SLOT_COUNT
                + " slots free)");
    }

    /**
     * 单群系挂接：biomeList 槽位空闲才构造（构造即占槽），权重表入 def；被占跳过 + 告警（降级运行）。
     *
     * @return 实际注册数（0 或 1）
     */
    private static int attachBiome(GTSRDimensionDef def, int biomeId, int weight,
        IntFunction<? extends GTSRBiomeBase> factory, StringBuilder ids) {
        if (!GTSRBiomeBase.isBiomeIdFree(biomeId)) {
            GTSteamReborn.LOG.warn(
                "[GTSR] prosperity biome id " + biomeId
                    + " already occupied by "
                    + describeOccupant(biomeId)
                    + ", biome skipped (dimension degrades, plan R3; check config prosperityBiomeIdStart)");
            return 0;
        }
        final GTSRBiomeBase biome = factory.apply(biomeId);
        def.addBiome(biome, weight);
        ids.append(biomeId)
            .append(' ');
        return 1;
    }

    private static String describeOccupant(int biomeId) {
        final BiomeGenBase occupant = BiomeGenBase.getBiome(biomeId);
        return occupant != null ? occupant.biomeName : "unknown";
    }

    /**
     * 补齐 fillerBlock meta 写入。
     * <p>
     * 背景：GTNH 1.7.10 原版 {@code BiomeGenBase.genBiomeTerrain} 只把 topBlock meta（field_150604_aj）
     * 写入表面带（y>=62），fillerBlock 一律以 meta 0 落地——本切片四群系的 filler meta（1/1/5/4）若不补写，
     * 地下夹层会错成 meta0（锈草）。此处仅在 y&lt;62 带内把与 fillerBlock 同实例的方块 meta 补为 fillerMeta；
     * top 带（y>=62）不受影响。扫描与 ChunkProvider 的 Block[] 排布转置方向无关（全列扫描）。
     * <p>
     * 边界：本实现以 S1 {@code GTSRChunkProviderBase}（平坦石层地形）为运行前提；后续切片若替换为
     * 专属噪声 ChunkProvider，surface/meta 逻辑由该切片承接（plan §1.2 S2 主表）。
     */
    static void applyFillerMeta(Block[] blocks, byte[] metadata, Block fillerBlock, int fillerMeta) {
        if (fillerMeta == 0) {
            return;
        }
        for (int column = 0; column < 256; column++) {
            final int base = column << 8;
            for (int y = 0; y < TOP_BAND_MIN_Y; y++) {
                final int index = base | y;
                if (blocks[index] == fillerBlock) {
                    metadata[index] = (byte) fillerMeta;
                }
            }
        }
    }
}
