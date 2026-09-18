package com.miaokatze.gtsr.common.dimension.shattered.biome;

import java.util.function.IntFunction;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 破碎之地（dim79）四群系持有者 + 注册入口（dim79 重做 S-B3，plan §5 S-B3；旧三群系
 * BiomeShatteredIsles/BiomeRiftCanyon/BiomeSingularityWaste 随浮岛模型删除）。
 * <p>
 * 职责：
 * <ol>
 * <li>preInit（BlockLoader 之后，群系 top/filler 引用的 BlocksGTSR.shattered* 已注册）调用
 * {@link #init}：按 {@link Config#shatteredBiomeIdStart}（默认 190..193）逐个经
 * {@link GTSRBiomeBase#isBiomeIdFree} 校验后再构造（构造即占 biomeList 槽且不可回退），
 * 被占群系跳过 + 告警，维度以剩余群系降级运行（plan R3，ProsperityBiomes 同款）；</li>
 * <li>把 4 群系按 S-B3 权重表 40/30/20/10 挂入 def（{@link GTSRDimensionDef#addBiome}），
 * 由 GTSRWorldChunkManager 消费（def 挂 BiomeZoneSelector 空间连贯分区，CommonProxy 接线）。</li>
 * </ol>
 * BiomeDictionary（END+DEAD+SPOOKY）在各群系构造器内自持；不调 addSpawnBiome（维度专属群系）。
 * 成功日志锚点（S-B3 验收 grep 点，四槽全注册时）：{@code [GTSR] shattered biomes: 190..193 registered}。
 */
public final class ShatteredBiomes {

    /** 本维度群系槽数（biomeIdStart .. biomeIdStart+3）。 */
    private static final int BIOME_SLOT_COUNT = 4;

    private ShatteredBiomes() {}

    /**
     * S-B3 注册入口：构造四群系（先空闲校验后构造）并挂入 def 权重表。
     * 总开关关闭时跳过全部构造（biomeList 零占用，维度已由 DimensionRegistrar 按开关禁用）。
     */
    public static void init(GTSRDimensionDef def) {
        if (def == null) {
            return;
        }
        if (!Config.planDimension.shatteredDimension) {
            GTSteamReborn.LOG
                .info("[GTSR] shattered biomes skipped: dimension disabled by master switch (planDimension config)");
            return;
        }
        final int start = Config.shatteredBiomeIdStart;
        final StringBuilder ids = new StringBuilder();
        int registered = 0;
        registered += attachBiome(def, start + 0, BiomeAshenPrairie.WEIGHT, BiomeAshenPrairie::new, ids);
        registered += attachBiome(def, start + 1, BiomeSlagwoodGrove.WEIGHT, BiomeSlagwoodGrove::new, ids);
        registered += attachBiome(def, start + 2, BiomeVitreousWaste.WEIGHT, BiomeVitreousWaste::new, ids);
        registered += attachBiome(def, start + 3, BiomeTarBasin.WEIGHT, BiomeTarBasin::new, ids);
        // 四槽全注册且连续时输出区间锚点（S-B3 验收 grep 串：shattered biomes: 190..193 registered）
        final String anchor = registered == BIOME_SLOT_COUNT ? start + ".." + (start + BIOME_SLOT_COUNT - 1)
            : ids.length() == 0 ? "(none)"
                : ids.toString()
                    .trim();
        GTSteamReborn.LOG.info(
            "[GTSR] shattered biomes: " + anchor
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
                "[GTSR] shattered biome id " + biomeId
                    + " already occupied by "
                    + describeOccupant(biomeId)
                    + ", biome skipped (dimension degrades, plan R3; check config shatteredBiomeIdStart)");
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

    /** 天空/雾色按群系取色（S-B3 四变体；非本维群系回退灰烬草原色）。 */
    public static int skyFogColorFor(BiomeGenBase biome) {
        if (biome instanceof BiomeAshenPrairie) {
            return BiomeAshenPrairie.SKY_FOG_COLOR;
        }
        if (biome instanceof BiomeSlagwoodGrove) {
            return BiomeSlagwoodGrove.SKY_FOG_COLOR;
        }
        if (biome instanceof BiomeVitreousWaste) {
            return BiomeVitreousWaste.SKY_FOG_COLOR;
        }
        if (biome instanceof BiomeTarBasin) {
            return BiomeTarBasin.SKY_FOG_COLOR;
        }
        return BiomeAshenPrairie.SKY_FOG_COLOR;
    }
}
