package com.miaokatze.gtsr.common.dimension.shattered.biome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 破碎之地（dim79）四群系持有者 + 注册入口（dim79 重做 S-B3，plan §5 S-B3；<b>P1 改写配槽口径</b>，
 * 与 {@code ProsperityBiomes} 同款；旧三群系 BiomeShatteredIsles/BiomeRiftCanyon/BiomeSingularityWaste
 * 随浮岛模型删除）。
 * <p>
 * 职责：
 * <ol>
 * <li>preInit（BlockLoader 之后，群系 top/filler 引用的 BlocksGTSR.shattered* 已注册）调用
 * {@link #init}：<b>逐群系独立配槽</b>——首选 id 为 {@link Config#shatteredBiomeIdStart} + 维内下标，
 * 被占则经 {@link GTSRBiomeBase#allocate(int, int)} 向后顺延（<b>允许非连续</b>），扫到
 * {@link GTSRBiomeBase#HARD_ID_MAX}（254）仍无槽才记入 {@link GTSRBiomeAuthority} 账本并显式降级
 * （改造前是"槽被占即静默跳过"，用户实机 190-193 全占 ⇒ 整维空表 ⇒ plains 草方块，见 plan §3 S4）；</li>
 * <li>把注册成功的群系按 S-B3 权重表 40/30/20/10 挂入 def（{@link GTSRDimensionDef#addBiome}），
 * 由 {@code GTSRWorldChunkManager} 消费（def 挂 BiomeZoneSelector 空间连贯分区，CommonProxy 接线）；
 * <b>空表不再回退 plains</b>。</li>
 * </ol>
 * BiomeDictionary（END+DEAD+SPOOKY）在各群系构造器内自持；不调 addSpawnBiome（维度专属群系）。
 * <p>
 * <b>日志锚点（L8）</b>：{@code [GTSR] shattered biomes: allocated=[190->190, ...] degraded=NONE}；
 * 三态由 {@code degraded=} 后的 {@code NONE}/{@code SHORT}/{@code EMPTY} 直接分辨
 * （空表级另有 {@code [GTSR] shattered biomes DEGRADED: EMPTY} 单独告警行）。
 */
public final class ShatteredBiomes {

    /** 本维度名册群系数（首选 id = {@code biomeIdStart + 0..3}；实际 id 由配槽顺延决定）。 */
    private static final int BIOME_SLOT_COUNT = 4;

    /** owner 快照聚合行的最大列出条数（超出折叠为 "…(+N)"）。 */
    private static final int OCCUPANT_LIST_CAP = 12;

    private ShatteredBiomes() {}

    /**
     * P1 注册入口：逐群系独立配槽 → 构造 → 挂入 def 权重表 → 账本与汇总日志。
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
        final int maxId = scanCeiling();
        if (start > maxId) {
            GTSteamReborn.LOG.warn(
                "[GTSR] shatteredBiomeIdStart={} exceeds usable ceiling {} (byte-plane hard max {})"
                    + " — no slot will be allocated, check config", start, maxId, GTSRBiomeBase.HARD_ID_MAX);
        }
        final Map<Integer, String> scannedOccupants = new LinkedHashMap<>();
        int registered = 0;
        registered += attachBiome(def, start + 0, BiomeAshenPrairie.WEIGHT, BiomeAshenPrairie::new, BiomeId.ASHEN_PRAIRIE,
            maxId, scannedOccupants);
        registered += attachBiome(def, start + 1, BiomeSlagwoodGrove.WEIGHT, BiomeSlagwoodGrove::new, BiomeId.SLAGWOOD_GROVE,
            maxId, scannedOccupants);
        registered += attachBiome(def, start + 2, BiomeVitreousWaste.WEIGHT, BiomeVitreousWaste::new, BiomeId.VITREOUS_WASTE,
            maxId, scannedOccupants);
        registered += attachBiome(def, start + 3, BiomeTarBasin.WEIGHT, BiomeTarBasin::new, BiomeId.TAR_BASIN,
            maxId, scannedOccupants);
        logAllocation(def, start, maxId, registered, scannedOccupants);
    }

    /**
     * 单群系配槽 + 挂接：拿到空槽才构造（构造即占槽）；无槽记 {@code recordNoSlot} 并告警。
     *
     * @return 实际注册数（0 或 1）
     */
    private static int attachBiome(GTSRDimensionDef def, int preferredId, int weight,
        IntFunction<? extends GTSRBiomeBase> factory, BiomeId key, int maxId,
        Map<Integer, String> scannedOccupants) {
        final int actualId = GTSRBiomeBase.allocate(preferredId, maxId, occupiedId -> {
            final String owner = GTSRBiomeBase.occupantName(occupiedId);
            if (occupiedId == preferredId) {
                GTSRBiomeAuthority.recordPreferredOccupant(key, preferredId, owner);
                GTSteamReborn.LOG.warn(
                    "[GTSR] shattered slot {} already occupied by {} (owner snapshot; {} slides forward)",
                    preferredId, owner, key.name());
            } else {
                scannedOccupants.put(occupiedId, owner);
            }
        });
        if (actualId == GTSRBiomeBase.NO_SLOT) {
            GTSRBiomeAuthority.recordNoSlot(
                key,
                preferredId,
                preferredId <= maxId ? GTSRBiomeBase.occupantName(preferredId) : "out of range");
            GTSteamReborn.LOG.error(
                "[GTSR] shattered biome {} got NO slot in {}..{} (byte-plane ceiling {}) — roster degrades",
                key.name(), preferredId, maxId, GTSRBiomeBase.HARD_ID_MAX);
            return 0;
        }
        final GTSRBiomeBase biome = factory.apply(actualId);
        GTSRBiomeAuthority.recordAllocation(key, preferredId, actualId, biome);
        def.addBiome(biome, weight);
        return 1;
    }

    /** 本次配槽的扫描上界：Config 只能收紧，永不能超过 byte 平面硬上界 {@link GTSRBiomeBase#HARD_ID_MAX}。 */
    private static int scanCeiling() {
        return Math.min(Config.biomeIdScanLimit, GTSRBiomeBase.HARD_ID_MAX);
    }

    /** 一行可 grep 的分配汇总（L8）+ 非首选槽快照聚合行 + 降级态单独告警行。 */
    private static void logAllocation(GTSRDimensionDef def, int start, int maxId, int registered,
        Map<Integer, String> scannedOccupants) {
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        final GTSRBiomeAuthority.Degraded degraded = authority.degraded();
        GTSteamReborn.LOG.info(
            "[GTSR] shattered biomes: allocated=[{}] degraded={} (idStart={}, scanMax={}, {}/{} registered, def table={})",
            authority.allocationSummary(),
            degraded.name(),
            start,
            maxId,
            registered,
            BIOME_SLOT_COUNT,
            def.getBiomeTable().size());
        if (!scannedOccupants.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (final Map.Entry<Integer, String> e : scannedOccupants.entrySet()) {
                if (shown++ >= OCCUPANT_LIST_CAP) {
                    sb.append(" …(+")
                        .append(scannedOccupants.size() - OCCUPANT_LIST_CAP)
                        .append(')');
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e.getKey())
                    .append('=')
                    .append(e.getValue());
            }
            GTSteamReborn.LOG
                .info("[GTSR] shattered scan passed occupied slots: [{}] (owner snapshot)", sb);
        }
        if (degraded == GTSRBiomeAuthority.Degraded.EMPTY) {
            GTSteamReborn.LOG.error(
                "[GTSR] shattered biomes DEGRADED: EMPTY — 空表级降级，本维度没有任何群系（plains 回退已删除，"
                    + "表层将不铺方块）；请调高 shatteredBiomeIdStart 或释放 biomeList 槽位");
        } else if (degraded == GTSRBiomeAuthority.Degraded.SHORT) {
            GTSteamReborn.LOG.warn(
                "[GTSR] shattered biomes DEGRADED: SHORT — 短表级降级，名册 {}/{} 拿到槽位（缺席群系的权重被吞）",
                registered,
                BIOME_SLOT_COUNT);
        }
    }

    /**
     * 天空/雾色按群系取色（S-B3 四变体；非本维群系回退灰烬草原色）。
     * <p>
     * <b>P2 身份读面收口</b>（plan §2.1 L1 禁止项，P1 交给本片的 4 处之一）：改造前是
     * {@code instanceof BiomeXxx} 四分支链——隐含"四群系连号"式实例判定。现走
     * {@link GTSRBiomeAuthority}：① 账本实例命中（运行时唯一路径，L0 注册时 {@code recordAllocation}
     * 写入）；② 未命中时按账本的<b>实际 id</b> 反查（{@link GTSRBiomeAuthority#actualIdOf}，
     * 供离线合成实例与注册后新建实例同样可命名）。<b>不</b>读 Chunk 保存的 byte biome id，
     * <b>不</b>用 {@code id - shatteredBiomeIdStart} 减法，也不留 instanceof 链。
     * 颜色常量本身零改动（差异只在"怎么判定是哪个群系"）。
     */
    public static int skyFogColorFor(BiomeGenBase biome) {
        return skyFogColorFor(identityOf(biome));
    }

    /** 按 L1 身份枚举取色；{@code null}（空表降级 / 外来群系）回退灰烬草原色。 */
    public static int skyFogColorFor(GTSRBiomeAuthority.BiomeId key) {
        if (key != null) {
            switch (key) {
                case ASHEN_PRAIRIE: {
                    return BiomeAshenPrairie.SKY_FOG_COLOR;
                }
                case SLAGWOOD_GROVE: {
                    return BiomeSlagwoodGrove.SKY_FOG_COLOR;
                }
                case VITREOUS_WASTE: {
                    return BiomeVitreousWaste.SKY_FOG_COLOR;
                }
                case TAR_BASIN: {
                    return BiomeTarBasin.SKY_FOG_COLOR;
                }
                default: {
                    break;
                }
            }
        }
        return BiomeAshenPrairie.SKY_FOG_COLOR;
    }

    /**
     * L1 群系身份（两跳：实例账本 → 实际 id 反查）。返回 {@code null} = 本维名册点名不到。
     * 与本类注册入口 {@link #init} 共用同一份 {@link GTSRBiomeAuthority} 账本，故身份与
     * 配槽结果永不分叉。
     */
    public static GTSRBiomeAuthority.BiomeId identityOf(BiomeGenBase biome) {
        if (biome == null) {
            return null;
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        final GTSRBiomeAuthority.Resolution byInstance = authority.of(biome);
        if (byInstance.resolved()) {
            return byInstance.biomeId;
        }
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (key.dimKey()
                .equals(GTSRBiomeAuthority.DIM_KEY_SHATTERED)
                && authority.actualIdOf(key) == biome.biomeID) {
                return key;
            }
        }
        return null;
    }
}
