package com.miaokatze.gtsr.common.dimension.prosperity.biome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.minecraft.block.Block;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 繁荣蒸汽时代遗迹（dim78）四群系持有者 + 注册入口（dim1 S2；<b>P1 改写配槽口径</b>，
 * plan §2.1 L0 + §7.1 U7 已锁定）。
 * <p>
 * 职责：
 * <ol>
 * <li>preInit（BlockLoader 之后，地表方块已注册）调用 {@link #init}：<b>逐群系独立配槽</b>——
 * 每个群系以 {@link Config#prosperityBiomeIdStart} + 维内下标为<b>首选</b> id，经
 * {@link GTSRBiomeBase#allocate(int, int)} 向后扫描空槽（<b>允许非连续</b>，不再假设四连号），
 * 拿到槽才构造（构造即占 biomeList 槽且不可回退）；扫到 {@link GTSRBiomeBase#HARD_ID_MAX}
 * 仍无槽才算无槽，记入 {@link GTSRBiomeAuthority} 账本并<b>显式</b>降级；</li>
 * <li>把注册成功的群系按 02 §1.1 权重表 45/30/15/10 挂入 def（{@link GTSRDimensionDef#addBiome}），
 * 由 {@code GTSRWorldChunkManager} 展开为 per-chunk 选择表；<b>空表不再回退 plains</b>；</li>
 * <li>补齐 fillerBlock meta 写入（{@link #applyFillerMeta}）。</li>
 * </ol>
 * 草色覆写在各群系类方法内自持；R1 起不向 BiomeDictionary 登记群系类型、不调 addSpawnBiome
 * （维度专属群系，不参与主世界检索面）。
 * <p>
 * <b>日志锚点（L8，用户只 grep {@code [GTSR]} 即可分辨三态）</b>：
 * 汇总行 {@code [GTSR] prosperity biomes: allocated=[180->180, ...] degraded=NONE} ——
 * {@code degraded=} 取 {@code NONE}（正常）/ {@code SHORT}（短表级，部分群系无槽）/
 * {@code EMPTY}（空表级，一个群系都没拿到槽，另有 {@code DEGRADED: EMPTY} 单独告警行）。
 * 被占槽另有 owner 快照行 {@code [GTSR] prosperity slot 180 already occupied by other:Firefly Forest}。
 */
public final class ProsperityBiomes {

    /**
     * 本维度名册群系数（首选 id = {@code biomeIdStart + 0..4}；实际 id 由配槽顺延决定）。
     * v1.20.39 T5 起为 5：第 5 元遗忘之川 {@link BiomeSanzuRiver} 走<b>名册配槽但不挂 def 表</b>
     * （不进 GenLayer 链 selector，plan §3.3），见 {@link #attachSanzuRiver}。
     */
    private static final int BIOME_SLOT_COUNT = 5;

    /** meta 补写扫描带上界（原版 genBiomeTerrain 的 topBand 从 y=62 起，filler 只会出现在其下方）。 */
    private static final int TOP_BAND_MIN_Y = 62;

    /** owner 快照聚合行的最大列出条数（超出折叠为 "…(+N)"，防止极端整合包刷日志）。 */
    private static final int OCCUPANT_LIST_CAP = 12;

    private ProsperityBiomes() {}

    /**
     * P1 注册入口：逐群系独立配槽 → 构造 → 挂入 def 权重表 → 账本与汇总日志。
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
        final int maxId = scanCeiling();
        if (start > maxId) {
            GTSteamReborn.LOG.warn(
                "[GTSR] prosperityBiomeIdStart={} exceeds usable ceiling {} (byte-plane hard max {})"
                    + " — no slot will be allocated, check config",
                start,
                maxId,
                GTSRBiomeBase.HARD_ID_MAX);
        }
        // 非首选槽的占用者快照（顺延途中撞到的既有槽，聚合打一行，避免逐槽刷屏）
        final Map<Integer, String> scannedOccupants = new LinkedHashMap<>();
        int registered = 0;
        registered += attachBiome(
            def,
            start + 0,
            BiomeRustedSteppe.WEIGHT,
            BiomeRustedSteppe::new,
            BiomeId.RUSTED_STEPPE,
            maxId,
            scannedOccupants);
        registered += attachBiome(
            def,
            start + 1,
            BiomeGearworkForest.WEIGHT,
            BiomeGearworkForest::new,
            BiomeId.GEARWORK_FOREST,
            maxId,
            scannedOccupants);
        registered += attachBiome(
            def,
            start + 2,
            BiomeBrassWastes.WEIGHT,
            BiomeBrassWastes::new,
            BiomeId.BRASS_WASTES,
            maxId,
            scannedOccupants);
        registered += attachBiome(
            def,
            start + 3,
            BiomeFumaroleSwamp.WEIGHT,
            BiomeFumaroleSwamp::new,
            BiomeId.FUMAROLE_SWAMP,
            maxId,
            scannedOccupants);
        // v1.20.39 T5（plan §3.3）：第 5 群系遗忘之川——同一条配槽扫描机制（首选 start+4，默认 184；
        // 顺延上界 254），但<b>只进名册账本不挂 def 群系表</b> ⇒ 不进 GenLayer 链 selector（4 家
        // 等权名册不动）；平面由 populate 后置写入（onPopulate → BiomePlaneAccess）
        registered += attachSanzuRiver(start + 4, maxId, scannedOccupants);
        logAllocation(def, start, maxId, registered, scannedOccupants);
    }

    /**
     * <b>遗忘之川的专用配槽（v1.20.39 T5，plan §3.3）</b>：与 {@link #attachBiome} 同一条
     * {@link GTSRBiomeBase#allocate} 扫描（首选 id、占用者快照、无槽降级记录全部同款），差别只有
     * 一处——<b>不调 {@code def.addBiome}</b>：def 群系表是 GenLayer 链 selector 的入参面
     * （{@code GTSRWorldChunkManager} 构造链），挂进去就等于进等权名册，违反"selector 4 家不动"
     * 红线。本群系的平面列由 populate 后置写入（{@code ChunkProviderProsperityRuins.onPopulate}
     * → {@code BiomePlaneAccess}），实际落位 id 由本方法的 INFO 行 + 汇总 allocationSummary 记录。
     *
     * @return 实际注册数（0 或 1）
     */
    private static int attachSanzuRiver(int preferredId, int maxId, Map<Integer, String> scannedOccupants) {
        final BiomeId key = BiomeId.SANZU_RIVER;
        final int actualId = GTSRBiomeBase.allocate(preferredId, maxId, occupiedId -> {
            final String owner = GTSRBiomeBase.occupantName(occupiedId);
            if (occupiedId == preferredId) {
                GTSRBiomeAuthority.recordPreferredOccupant(key, preferredId, owner);
                GTSteamReborn.LOG.warn(
                    "[GTSR] prosperity slot {} already occupied by {} (owner snapshot; {} slides forward)",
                    preferredId,
                    owner,
                    key.name());
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
                "[GTSR] prosperity biome {} got NO slot in {}..{} (byte-plane ceiling {}) — roster degrades",
                key.name(),
                preferredId,
                maxId,
                GTSRBiomeBase.HARD_ID_MAX);
            return 0;
        }
        final GTSRBiomeBase biome = new BiomeSanzuRiver(actualId);
        GTSRBiomeAuthority.recordAllocation(key, preferredId, actualId, biome);
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity biome SANZU_RIVER allocated {}->{} (roster-only: not in selector/def biome table;"
                + " plane written post-populate)",
            preferredId,
            actualId);
        return 1;
    }

    /**
     * 单群系配槽 + 挂接：拿到空槽才构造（构造即占槽）；无槽记 {@code recordNoSlot} 并告警。
     *
     * @return 实际注册数（0 或 1）
     */
    private static int attachBiome(GTSRDimensionDef def, int preferredId, int weight,
        IntFunction<? extends GTSRBiomeBase> factory, BiomeId key, int maxId, Map<Integer, String> scannedOccupants) {
        final int actualId = GTSRBiomeBase.allocate(preferredId, maxId, occupiedId -> {
            final String owner = GTSRBiomeBase.occupantName(occupiedId);
            if (occupiedId == preferredId) {
                GTSRBiomeAuthority.recordPreferredOccupant(key, preferredId, owner);
                GTSteamReborn.LOG.warn(
                    "[GTSR] prosperity slot {} already occupied by {} (owner snapshot; {} slides forward)",
                    preferredId,
                    owner,
                    key.name());
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
                "[GTSR] prosperity biome {} got NO slot in {}..{} (byte-plane ceiling {}) — roster degrades",
                key.name(),
                preferredId,
                maxId,
                GTSRBiomeBase.HARD_ID_MAX);
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
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRBiomeAuthority.Degraded degraded = authority.degraded();
        GTSteamReborn.LOG.info(
            "[GTSR] prosperity biomes: allocated=[{}] degraded={} (idStart={}, scanMax={}, {}/{} registered, def table={})",
            authority.allocationSummary(),
            degraded.name(),
            start,
            maxId,
            registered,
            BIOME_SLOT_COUNT,
            def.getBiomeTable()
                .size());
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
            GTSteamReborn.LOG.info("[GTSR] prosperity scan passed occupied slots: [{}] (owner snapshot)", sb);
        }
        if (degraded == GTSRBiomeAuthority.Degraded.EMPTY) {
            GTSteamReborn.LOG.error(
                "[GTSR] prosperity biomes DEGRADED: EMPTY — 空表级降级，本维度没有任何群系（plains 回退已删除，"
                    + "表层将不铺方块）；请调高 prosperityBiomeIdStart 或释放 biomeList 槽位");
        } else if (degraded == GTSRBiomeAuthority.Degraded.SHORT) {
            GTSteamReborn.LOG.warn(
                "[GTSR] prosperity biomes DEGRADED: SHORT — 短表级降级，名册 {}/{} 拿到槽位（缺席群系的权重被吞）",
                registered,
                BIOME_SLOT_COUNT);
        }
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
