package com.miaokatze.gtsr.common.dimension.prosperity.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.config.Config;

/**
 * dim78 生物层名册（<b>L7 的单一真值表</b>，plan §2.1「横切 roster」+ §5 P9 + §7.1 生物裁决）。
 * <p>
 * ═══ 三档（回归 02 册 §1.4 的原设计，不是新增设定）═══
 * <table border="1">
 * <tr>
 * <th>档</th>
 * <th>实体类</th>
 * <th>EnumCreatureType</th>
 * <th>母体（Phase 1 复用，零新素材）</th>
 * <th>原版纹理</th>
 * <th>基权（Config）</th>
 * <th>满群</th>
 * </tr>
 * <tr>
 * <td>齿轮鸽</td>
 * <td>{@link EntityGearPigeon}</td>
 * <td>creature</td>
 * <td>原版鸡（ModelChicken）</td>
 * <td>{@code textures/entity/chicken.png}</td>
 * <td>12</td>
 * <td>4-8</td>
 * </tr>
 * <tr>
 * <td>汽雾萤</td>
 * <td>{@link EntitySteamFirefly}</td>
 * <td>ambient</td>
 * <td>原版蝙蝠（ModelBat）</td>
 * <td>{@code textures/entity/bat.png}</td>
 * <td>8</td>
 * <td>6-12</td>
 * </tr>
 * <tr>
 * <td>渣脊猎手</td>
 * <td>{@link EntitySlagRidgeHunter}</td>
 * <td>monster</td>
 * <td>原版骷髅（ModelSkeleton）</td>
 * <td>{@code textures/entity/skeleton/skeleton.png}</td>
 * <td>6</td>
 * <td>2-4</td>
 * </tr>
 * </table>
 * 基权是 <b>02 册 §1.4 的 12/8</b> 与敌对档 6，落在 {@code Config} 三键（唯一声明处）；
 * 母体模型/纹理只在 {@code GTSRCreatureRenderers} 用一次（本表存纹理<b>路径字符串</b>供
 * "资源实际可解析"断言用，不引客户端类，避免 common 侧加载 {@code net.minecraft.client}）。
 * <p>
 * ═══ 分布规则（判据 2 的被断言对象）═══
 * <ul>
 * <li><b>按 macro 群系带给权重</b>：{@link #BAND_MULTIPLIERS} 是本表独有的 4×3 乘子矩阵
 * （行=档、列={@link BiomeId#rosterIndex()}），生效权 = {@code Config 基权 × 带乘子}，
 * 乘子 0 ⇒ 该带<b>不写入</b>该档（{@code GTSRBiomeBase.addCreatureSpawn} 的 weight≤0 门）。
 * 两半各自唯一：基权只在 Config、乘子只在本表 ⇒ 符合 plan §2.4 判据 4；</li>
 * <li><b>城窗 ×2</b>：{@link #CITY_WINDOW_WEIGHT_MULTIPLIER}，生效点在
 * {@code GTSRChunkProviderBase.getPossibleCreatures} → {@link #scaleForLocation}
 * （<b>不</b>改进册声明表，只在读取时产出调制后的副本）；城市归属复用 P6 的
 * {@link CityPlanner#citiesNear} 唯一真值（与渲染/抑制同一判定，不另造第二套城窗数法）；</li>
 * <li><b>结构联动 ≤2/座</b>：{@link #STRUCTURE_LINK_CAP_PER_STRUCTURE} + {@link #LINKED_FAMILIES}。
 * <b>本片只落数据结构与断言</b>（任务包 P9 判据 3 的明确口径）——vanilla 的刷怪入口
 * {@code getPossibleCreatures} 拿不到"最近的第几座结构"，逐座封顶需要 populate 侧钩子，
 * 归 P12（观测）之后的另派切片；行为生效与否一律标"待实机目检"。</li>
 * </ul>
 * <b>不注册刷怪蛋</b>（plan §7.1 U-B）：本表因此不持有任何蛋色字段，注册链也不调
 * {@code registerGlobalEntityID}。
 * <p>
 * <b>dim79 恒零</b>：{@link #bandMultiplier} 对非 {@code prosperity-ruins} 的 def key 一律返回 0，
 * 故 {@code shattered-lands} 四群系的 4 张列表在 P9 之后仍然全空 ⇒ 与 P8 基线逐位一致
 * （判据 7「非本片路径零漂移」的 roster 侧保证）。
 */
public final class GTSRCreatureRoster {

    /**
     * 一"档"生物。
     * <p>
     * {@link #creatureType()} 的选择不是装饰：vanilla {@code Entity.canSpawnHere(EnumCreatureType)}
     * 用 {@code type.getCreatureClass().isAssignableFrom(getClass())} 决定是否允许落地刷出，
     * 1.7.10 的三条真值（{@code EnumCreatureType.java:3-7} 实测）是
     * {@code creature → EntityAnimal}、{@code ambient → EntityAmbientCreature}、
     * {@code monster → IMob}——所以三个实体类必须分别继承 {@code EntityAnimal} /
     * {@code EntityAmbientCreature} / {@code EntityMob}（后者 implements IMob），
     * 否则表现为"注册了、表里也有、但永不落地"（无头冒烟测不出来，只能实机目检）。
     */
    public enum Species {

        /** 齿轮鸽：02 册 §1.4 的维度特产被动生物，替换原版鸡的生态位。 */
        GEAR_PIGEON("gtsr.gear_pigeon", EntityGearPigeon.class, EnumCreatureType.creature, 4, 8,
            "textures/entity/chicken.png", "ModelChicken"),

        /** 汽雾萤：02 册 §1.4 的环境生物（蝙蝠档位），沼泽/森林夜间成群。 */
        STEAM_FIREFLY("gtsr.steam_firefly", EntitySteamFirefly.class, EnumCreatureType.ambient, 6, 12,
            "textures/entity/bat.png", "ModelBat"),

        /** 渣脊猎手：敌对档（plan §7.1 U-D 用户确认保留）。 */
        SLAG_RIDGE_HUNTER("gtsr.slag_ridge_hunter", EntitySlagRidgeHunter.class, EnumCreatureType.monster, 2, 4,
            "textures/entity/skeleton/skeleton.png", "ModelSkeleton");

        private final String registryName;
        private final Class<? extends EntityLiving> entityClass;
        private final EnumCreatureType creatureType;
        private final int minGroup;
        private final int maxGroup;
        private final String vanillaTexturePath;
        private final String vanillaModelName;

        Species(String registryName, Class<? extends EntityLiving> entityClass, EnumCreatureType creatureType,
            int minGroup, int maxGroup, String vanillaTexturePath, String vanillaModelName) {
            this.registryName = registryName;
            this.entityClass = entityClass;
            this.creatureType = creatureType;
            this.minGroup = minGroup;
            this.maxGroup = maxGroup;
            this.vanillaTexturePath = vanillaTexturePath;
            this.vanillaModelName = vanillaModelName;
        }

        /** FML {@code registerModEntity} 用的实体名（命名空间内唯一；不占全局 id ⇒ 无蛋）。 */
        public String registryName() {
            return this.registryName;
        }

        public Class<? extends EntityLiving> entityClass() {
            return this.entityClass;
        }

        public EnumCreatureType creatureType() {
            return this.creatureType;
        }

        public int minGroup() {
            return this.minGroup;
        }

        public int maxGroup() {
            return this.maxGroup;
        }

        /** 复用的<b>原版</b>纹理路径（{@code assets/<domain>/} 之后的部分；domain 恒 minecraft）。 */
        public String vanillaTexturePath() {
            return this.vanillaTexturePath;
        }

        /** 复用的原版模型类名（仅申报与断言用；真正的模型实例在客户端渲染器里）。 */
        public String vanillaModelName() {
            return this.vanillaModelName;
        }

        /** 基权（{@code Config} 三键之一；唯一声明处，本表不复制数字）。 */
        public int baseWeight() {
            switch (this) {
                case GEAR_PIGEON: {
                    return Config.prosperityCreatureGearPigeonWeight;
                }
                case STEAM_FIREFLY: {
                    return Config.prosperityCreatureSteamFireflyWeight;
                }
                default: {
                    return Config.prosperityCreatureSlagRidgeHunterWeight;
                }
            }
        }
    }

    /** 城窗权重乘子（L7 分布规则第二条；生效点见 {@link #scaleForLocation}）。 */
    public static final int CITY_WINDOW_WEIGHT_MULTIPLIER = 2;

    /** 结构联动每座的封顶（plan §5 P9「结构联动 ≤2/座」；本片仅数据 + 断言）。 */
    public static final int STRUCTURE_LINK_CAP_PER_STRUCTURE = 2;

    /** dim78 名册规模（带乘子表的列数）。 */
    private static final int PROSPERITY_ROSTER_SIZE = 4;

    /**
     * 带乘子矩阵：行 = {@link Species} 声明序，列 = {@link BiomeId#rosterIndex()}
     * （0 锈蚀草原 / 1 齿轮林 / 2 黄铜荒地 / 3 喷气口沼泽）。
     * <p>
     * 语义（02 册 §1.4 的"鸽=草原与齿轮林的群栖、萤=沼泽夜间成群"，加上敌对档避开草原的取舍）：
     * 
     * <pre>
     *                 草原  齿轮林  荒地  沼泽
     * 齿轮鸽             3      5    1     0
     * 汽雾萤             1      2    0     5
     * 渣脊猎手           2      1    3     2
     * </pre>
     */
    private static final int[][] BAND_MULTIPLIERS = { { 3, 5, 1, 0 }, { 1, 2, 0, 5 }, { 2, 1, 3, 2 } };

    /** 结构联动族（取 {@link PlacementGate} 的族常量本身，不抄字符串）。 */
    private static final String[][] LINKED_FAMILIES = {
        { PlacementGate.FAMILY_OUTPOST, PlacementGate.FAMILY_RUIN, PlacementGate.FAMILY_CITY },
        { PlacementGate.FAMILY_RUIN, PlacementGate.FAMILY_OUTPOST },
        { PlacementGate.FAMILY_MACHINE, PlacementGate.FAMILY_RUIN } };

    private GTSRCreatureRoster() {}

    /** 本档在该群系带的乘子（dim79 与越界下标一律 0 = 不出现）。 */
    public static int bandMultiplier(Species species, BiomeId biome) {
        if (species == null || biome == null) {
            return 0;
        }
        if (!GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(biome.dimKey())) {
            return 0;
        }
        final int column = biome.rosterIndex();
        if (column < 0 || column >= PROSPERITY_ROSTER_SIZE) {
            return 0;
        }
        return BAND_MULTIPLIERS[species.ordinal()][column];
    }

    /** 生效权 = 基权 × 带乘子（乘子 0 或基权 0 都 ⇒ 0 ⇒ 该带不出现该档）。 */
    public static int effectiveWeight(Species species, BiomeId biome) {
        return species.baseWeight() * bandMultiplier(species, biome);
    }

    /**
     * 名册<b>声明</b>的该群系刷怪表（按 {@link Species} 声明序，权重 ≤0 的缺席档不出现）。
     * <p>
     * 这是判据 2 的"另一方"：{@code GTSRBiomeBase} 在册的列表必须与它逐位一致，
     * 由 {@code tools/dim1/CreatureSpawnAuthorityCheck} 逐条比对。
     */
    public static List<BiomeGenBase.SpawnListEntry> declaredEntries(BiomeId biome) {
        final List<BiomeGenBase.SpawnListEntry> out = new ArrayList<>(Species.values().length);
        for (final Species species : Species.values()) {
            final int weight = effectiveWeight(species, biome);
            if (weight <= 0) {
                continue;
            }
            out.add(
                new BiomeGenBase.SpawnListEntry(species.entityClass(), weight, species.minGroup(), species.maxGroup()));
        }
        return out;
    }

    /** 城窗乘子（生效权调制；{@code false} 时恒 1）。 */
    public static int cityWindowMultiplier(boolean inCityWindow) {
        return inCityWindow ? CITY_WINDOW_WEIGHT_MULTIPLIER : 1;
    }

    /**
     * 该 chunk 是否在城窗内——<b>只走 P6 的唯一真值</b> {@link CityPlanner#citiesNear}
     * （与城市渲染、城内散布抑制同一判定），不另造第二套数法。
     */
    public static boolean inCityWindow(long worldSeed, int chunkX, int chunkZ) {
        return CityPlanner.citiesNear(worldSeed, chunkX, chunkZ).length > 0;
    }

    /**
     * 把声明表按乘子调制为<b>新</b>列表（不改在册条目——它们是群系的声明真值）。
     * 乘子 1 时原样返回（同一实例，零分配）。
     */
    public static List<BiomeGenBase.SpawnListEntry> scaleForLocation(List<BiomeGenBase.SpawnListEntry> declared,
        int multiplier) {
        if (declared == null || declared.isEmpty() || multiplier == 1) {
            return declared;
        }
        final List<BiomeGenBase.SpawnListEntry> out = new ArrayList<>(declared.size());
        for (final BiomeGenBase.SpawnListEntry entry : declared) {
            out.add(
                new BiomeGenBase.SpawnListEntry(
                    entry.entityClass,
                    entry.itemWeight * multiplier,
                    entry.minGroupCount,
                    entry.maxGroupCount));
        }
        return Collections.unmodifiableList(out);
    }

    /** 该档是否与该结构族联动（数据结构申报；本片不接入放置链）。 */
    public static boolean linksStructure(Species species, String family) {
        for (final String linked : LINKED_FAMILIES[species.ordinal()]) {
            if (linked.equals(family)) {
                return true;
            }
        }
        return false;
    }

    /** 该档在该族结构上的封顶（不联动的族返回 0；联动返回 {@link #STRUCTURE_LINK_CAP_PER_STRUCTURE}）。 */
    public static int structureLinkCap(Species species, String family) {
        return linksStructure(species, family) ? STRUCTURE_LINK_CAP_PER_STRUCTURE : 0;
    }

    /** 联动族清单（只读快照，断言与日志用）。 */
    public static List<String> linkedFamilies(Species species) {
        final List<String> out = new ArrayList<>();
        Collections.addAll(out, LINKED_FAMILIES[species.ordinal()]);
        return Collections.unmodifiableList(out);
    }
}
