package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster.Species;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * dim78 生物层的注册链与 L7 策略实现（plan §5 P9 / §2.1 L0+L7）。
 * <p>
 * ═══ 配方（wiki 优先，已核实）═══
 * <ul>
 * <li>{@code mods/gto/machines/wave-creature-system.md:48-49}：preInit 调
 * {@code EntityRegistry.registerModEntity}、{@code updateFrequency=3}；</li>
 * <li>GT++ {@code core/entity/InternalEntityRegistry.java:14-59}：mod 内<b>自增</b> entity id +
 * {@code registerModEntity(..., 64, 3, true)}。本仓<b>不</b>抄它的蛋色（plan §7.1 U-B：Phase 1
 * 不注册刷怪蛋 ⇒ 不调 {@code registerGlobalEntityID}，也就不占 FML 全局 id 段，
 * 与他 mod 的冲突面机制性为零）；</li>
 * <li>GT5U {@code BiomeEverglades.java:45-56} 的"clear 后按 {@code SpawnListEntry(weight,min,max)}
 * 填"落在 {@link GTSRBiomeBase} 的构造清空 + {@link #POLICY} 的 {@code fill} 幂等重填上，
 * <b>不</b>走 {@code EntityRegistry.addSpawn}（漏传 biomes 会污染主世界原版群系，
 * 判据 1 的防线就是根本不引入那条调用）。</li>
 * </ul>
 * <p>
 * ═══ 两段可分性（离线可机检的关键）═══
 * {@link #planRegistrations()} 只做"自增 id 分配 + 快照"，<b>零 FML 调用</b>，所以
 * {@code tools/dim1/CreatureSpawnAuthorityCheck} 能在没有 {@code ModContainer} 的离线 JVM 里
 * 跑判据 5/6；FML 侧的 {@code registerModEntity} 只在真实 preInit 走（{@link #preInit()}）。
 * 同理 {@link #POLICY} 是纯数据 ⇒ 填充链也能离线断言。
 */
public final class GTSRCreatureRegistry {

    /** mod 内 entity id 自增起点（GT++ 配方同档：1 起，不硬编码到具体档）。 */
    public static final int FIRST_ENTITY_ID = 1;

    /** wiki 钉死的 updateFrequency（不做 Config 键，见 {@code Config} 的 P9 段注释）。 */
    public static final int UPDATE_FREQUENCY = 3;

    /** {@code sendsVelocityUpdates}（敌对档要被打到 ⇒ 必须 true；三档统一取 true，与配方一致）。 */
    public static final boolean SENDS_VELOCITY_UPDATES = true;

    private static final Map<String, Integer> ID_BY_NAME = new LinkedHashMap<>();
    private static final Map<Class<? extends EntityLiving>, Integer> ID_BY_CLASS = new LinkedHashMap<>();

    /** 城窗调制后的生效表缓存（键 = biomeID/type/乘子；条目内容由 roster 纯函数决定 ⇒ 可缓存）。 */
    private static final Map<String, List<BiomeGenBase.SpawnListEntry>> SCALED_CACHE = new ConcurrentHashMap<>();

    /** 已分到的下一个自增 id（只增不减；{@link #planRegistrations} 重复执行不重复分配）。 */
    private static int nextId = FIRST_ENTITY_ID;
    /** FML 侧注册是否已发生（防重复 registerModEntity）。 */
    private static boolean fmlRegistered;

    private GTSRCreatureRegistry() {}

    // ————————————————————————— L0：id 分配与注册 —————————————————————————

    /**
     * 自增 id 分配（幂等；零 FML 调用 ⇒ 离线可跑）。
     *
     * @return 本次分配后在册的档数（恒等于 {@link Species#values()} 长度）
     */
    public static synchronized int planRegistrations() {
        for (final Species species : Species.values()) {
            if (ID_BY_NAME.containsKey(species.registryName())) {
                continue;
            }
            ID_BY_NAME.put(species.registryName(), nextId);
            ID_BY_CLASS.put(species.entityClass(), nextId);
            nextId++;
        }
        return ID_BY_NAME.size();
    }

    /** name → mod-local id 的只读快照（判据 5 的"注册表快照"；未分配 ⇒ 不含该键）。 */
    public static synchronized Map<String, Integer> idSnapshot() {
        planRegistrations();
        return Collections.unmodifiableMap(new LinkedHashMap<>(ID_BY_NAME));
    }

    /** 已注册的档数（判据 5 快照口径）。 */
    public static synchronized int registeredSpeciesCount() {
        return ID_BY_NAME.size();
    }

    /** 某档的 mod-local id（未分配返回 -1）。 */
    public static synchronized int idOf(Species species) {
        final Integer id = ID_BY_NAME.get(species.registryName());
        return id == null ? -1 : id;
    }

    /**
     * preInit 注册入口（{@code CommonProxy.preInit} 的<b>新增</b>调用；必须在群系挂接之前，
     * 这样 {@code GTSRBiomeBase} 第一次被读列表时策略已在位）。
     * <p>
     * 总开关 {@link Config#prosperityCreaturesEnabled} 关闭时整段跳过 ⇒ 既不注册实体、
     * 也不安装填充策略 ⇒ 四张列表停在构造清空态，与 P8 基线逐位一致（判据 2 的回退档）。
     */
    public static synchronized void preInit() {
        if (!Config.prosperityCreaturesEnabled) {
            GTSteamReborn.LOG.info(
                "[GTSR] creatures skipped: prosperityCreaturesEnabled=false (spawn lists stay cleared, P8 baseline)");
            return;
        }
        final int trackingRange = Config.prosperityCreatureTrackingRange;
        final Object mod = resolveModInstance();
        if (mod == null) {
            GTSteamReborn.LOG.error(
                "[GTSR] creatures: @Mod instance unresolvable, registerModEntity skipped "
                    + "(spawn policy NOT installed => tables stay cleared, P8 baseline)");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final Species species : Species.values()) {
            final int id = idOf(species);
            EntityRegistry.registerModEntity(
                species.entityClass(),
                species.registryName(),
                id,
                mod,
                trackingRange,
                UPDATE_FREQUENCY,
                SENDS_VELOCITY_UPDATES);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(species.registryName())
                .append('#')
                .append(id)
                .append('/')
                .append(species.creatureType());
        }
        fmlRegistered = true;
        // 策略在 FML 注册<b>全部成功之后</b>才装：中途抛异常时宁可让 4 张列表停在清空态，
        // 也不要出现"表里有条目、实体却没注册"的半截状态（那条目的存在会让刷怪白跑一趟）。
        GTSRBiomeBase.setCreatureSpawnPolicy(POLICY);
        GTSteamReborn.LOG.info(
            "[GTSR] creatures: registered=[{}] trackingRange={} updateFrequency={} eggs=none (U-B) policy=installed",
            sb,
            trackingRange,
            UPDATE_FREQUENCY);
    }

    /** FML 侧注册是否已发生（只读；离线断言里应为 false，因为离线 JVM 没有 ModContainer）。 */
    public static synchronized boolean isRegisteredWithFml() {
        return fmlRegistered;
    }

    /** @Mod 实例（与 {@code AggregatorGuiHandler.modInstance} 同一 Loader 索引口径；失败返回 null）。 */
    private static Object resolveModInstance() {
        try {
            final ModContainer container = Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID);
            return container == null ? null : container.getMod();
        } catch (Throwable t) {
            GTSteamReborn.LOG.warn("[GTSR] creatures: Loader 索引取 @Mod 实例失败", t);
            return null;
        }
    }

    // ————————————————————————— L7：策略实现 —————————————————————————

    /**
     * 生物层策略（L7）——框架侧唯一的注入点实现，全部数字来自 {@link GTSRCreatureRoster}
     * 与 {@link Config}，本类不持有权重常量。
     */
    public static final GTSRBiomeBase.CreatureSpawnPolicy POLICY = new GTSRBiomeBase.CreatureSpawnPolicy() {

        @Override
        public void fill(GTSRBiomeBase biome, BiomeId key) {
            if (!Config.prosperityCreaturesEnabled) {
                return;
            }
            for (final Species species : Species.values()) {
                final int weight = GTSRCreatureRoster.effectiveWeight(species, key);
                if (weight <= 0) {
                    continue;
                }
                biome.addCreatureSpawn(
                    species.creatureType(),
                    species.entityClass(),
                    weight,
                    species.minGroup(),
                    species.maxGroup());
            }
        }

        @Override
        public List<BiomeGenBase.SpawnListEntry> scaleForLocation(BiomeGenBase biome, EnumCreatureType creatureType,
            List<BiomeGenBase.SpawnListEntry> declared, long worldSeed, int chunkX, int chunkZ) {
            if (!Config.prosperityCreaturesEnabled || declared.isEmpty()) {
                return declared;
            }
            final BiomeId key = GTSRBiomeAuthority.identityOf(biome);
            if (key == null || !GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                return declared;
            }
            if (!GTSRCreatureRoster.inCityWindow(worldSeed, chunkX, chunkZ)) {
                return declared;
            }
            final String cacheKey = biome.biomeID + "/"
                + creatureType.name()
                + "/"
                + GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER;
            List<BiomeGenBase.SpawnListEntry> scaled = SCALED_CACHE.get(cacheKey);
            if (scaled == null) {
                scaled = GTSRCreatureRoster
                    .scaleForLocation(new ArrayList<>(declared), GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER);
                SCALED_CACHE.put(cacheKey, scaled);
            }
            return scaled;
        }

        @Override
        public String toString() {
            return "GTSRCreatureRegistry.POLICY";
        }
    };
}
