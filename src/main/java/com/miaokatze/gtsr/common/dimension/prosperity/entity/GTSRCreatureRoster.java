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
 * <th>渲染模型（D2 后：我方复刻件不再写"原版"）</th>
 * <th>皮肤画布</th>
 * <th>贴图（D1 起为<b>自有</b>皮肤，不再借原版；画法档案见 {@code tools/artgen/entity/}）</th>
 * <th>基权（Config）</th>
 * <th>满群</th>
 * </tr>
 * <tr>
 * <td>齿轮鸽</td>
 * <td>{@link EntityGearPigeon}</td>
 * <td>creature <b>（低频客串，见下方密度口径）</b></td>
 * <td>原版鸡（{@code ModelChicken}，实测零实体强转）</td>
 * <td>64×32</td>
 * <td>{@code gtsr:textures/entity/gear-pigeon.png}</td>
 * <td>12</td>
 * <td>4-8</td>
 * </tr>
 * <tr>
 * <td>汽雾萤</td>
 * <td>{@link EntitySteamFirefly}</td>
 * <td>ambient</td>
 * <td>{@code GTSRModelSteamFirefly}（原版 ModelBat 无条件转 EntityBat ⇒ 崩，故复刻）</td>
 * <td><b>64×64</b></td>
 * <td>{@code gtsr:textures/entity/steam-firefly.png}</td>
 * <td>8</td>
 * <td>6-12</td>
 * </tr>
 * <tr>
 * <td>渣脊猎手</td>
 * <td>{@link EntitySlagRidgeHunter}</td>
 * <td>monster</td>
 * <td>{@code GTSRModelSlagRidgeHunter}（原版 ModelSkeleton 无条件转 EntitySkeleton ⇒ 崩，故复刻；
 * 几何/UV/画布逐箱照抄，只丢恒 false 的 aimedBow）</td>
 * <td><b>64×32</b></td>
 * <td>{@code gtsr:textures/entity/slag-ridge-hunter.png}</td>
 * <td>6</td>
 * <td>2-4</td>
 * </tr>
 * </table>
 * 基权是 <b>02 册 §1.4 的 12/8</b> 与敌对档 6，落在 {@code Config} 三键（唯一声明处）；
 * 模型/纹理只在 {@code GTSRCreatureRenderers} 用一次（本表存<b>路径与模型类名字符串</b>供
 * "资源实际可解析 + 模型确为申报件"断言用，不引客户端类，避免 common 侧加载 {@code net.minecraft.client}）。
 * 画布两列是 D1 画皮肤的唯一尺寸口径，由 {@code CreatureSpawnAuthorityCheck} 的 I2 组按
 * 贴图 IHDR 与模型 {@code textureWidth/Height} 三方对钉（申报值 ≠ 实测 ⇒ 红）。
 * 皮肤<b>像素本身</b>不在本表：三张皮的色板角色、"哪个 UV 矩形归属哪个身体部件"与画法理由
 * 归档在 {@code tools/artgen/entity/manifest.json}（入库），生成器是同目录的
 * {@code draw_entity_skins.py}（无随机、双跑字节一致；它还会正则回读本表的三行申报做画布预检）。
 * <p>
 * ═══ 密度口径（C-07，读权重表前必须知道的事）═══
 * 基权只是"同类别内的相对抽签权"，<b>不等于</b>游戏内密度。{@code creature} 是
 * {@code EnumCreatureType} 四档里唯一 {@code isAnimal = true} 的（{@code EnumCreatureType.java:11-14}），
 * 于是 {@code WorldServer.java:169} 的 {@code worldInfo.getWorldTotalTime() % 400L == 0L} 门
 * 让它<b>每 400 tick（20 秒）才被尝试一次</b>，而 monster/ambient 每 20 tick 就试；再叠上
 * {@code SpawnerAnimals.java:91} 末条的全维上限折算（creature {@code getMaxNumberOfCreature() = 10}、
 * ambient 15、monster 70，按 {@code × eligibleChunks / 256}）⇒ 鸽的 12 权重最高，实际却是最稀的一档。
 * 本表因此读作"同类别内谁先被抽中"，<b>不</b>读作"玩家转一圈先看见谁"；改档（把鸽挪去 ambient）
 * 会连带丢 {@code EntityAnimal} 的繁殖/喂食链并要重做 C-03 的地表判定 ⇒ 本轮只钉口径不动档。
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
        GEAR_PIGEON("gear_pigeon", EntityGearPigeon.class, EnumCreatureType.creature, 4, 8,
            "gtsr:textures/entity/gear-pigeon.png", "ModelChicken", 64, 32),

        /** 汽雾萤：02 册 §1.4 的环境生物（蝙蝠档位），沼泽/森林夜间成群。 */
        STEAM_FIREFLY("steam_firefly", EntitySteamFirefly.class, EnumCreatureType.ambient, 6, 12,
            "gtsr:textures/entity/steam-firefly.png", "GTSRModelSteamFirefly", 64, 64),

        /** 渣脊猎手：敌对档（plan §7.1 U-D 用户确认保留）。 */
        SLAG_RIDGE_HUNTER("slag_ridge_hunter", EntitySlagRidgeHunter.class, EnumCreatureType.monster, 2, 4,
            "gtsr:textures/entity/slag-ridge-hunter.png", "GTSRModelSlagRidgeHunter", 64, 32);

        /**
         * FML {@code registerModEntity} 的<b>命名空间内</b>名——<b>不带</b> modid 前缀。
         * <p>
         * D2 修 C-04：{@code cpw EntityRegistry.java:164} 自己会做
         * {@code String.format("%s.%s", mc.getModId(), entityName)}，v1.20.35 的名册又自带
         * {@code "gtsr."} ⇒ 运行时串名成 {@code gtsr.gtsr.gear_pigeon}，名牌/Waila/F3 取
         * {@code entity.gtsr.gtsr.gear_pigeon.name}（{@code Entity.java:2276-2286}）而两份 lang
         * 里 {@code ^entity.} 键 0 个 ⇒ 直接把原始 key 画在头顶。串名前缀的唯一出处因此是 FML，
         * 名册只申报命名空间内名，两份 lang 按 {@code entity.<modid>.<本字段>.name} 追加键
         * （I3 组逐条钉住"串名不带 modid 前缀"与"两份 lang 都有该键"）。
         * <b>代价（登记）</b>：存档里已写入的实体 {@code id} 字符串（{@code Entity.java:1482-1491}）
         * 与新串名失配 ⇒ 旧存档中的这三档会读不回；v1.20.35 尚无玩家进入 dim78（清单 C-04 同记），
         * 现在改成本最低，越晚越贵。
         */
        private final String registryName;
        private final Class<? extends EntityLiving> entityClass;
        private final EnumCreatureType creatureType;
        private final int minGroup;
        private final int maxGroup;
        private final String skinTexturePath;
        /** 渲染用的模型类 SimpleName（<b>不再承诺"原版"</b>：C-01/C-02 逼出两个我方复刻件）。 */
        private final String modelClassName;
        /** 皮肤画布宽（I2 组按贴图 IHDR 实测核对；D1 画皮的唯一尺寸口径出处）。 */
        private final int skinWidth;
        /** 皮肤画布高。 */
        private final int skinHeight;

        Species(String registryName, Class<? extends EntityLiving> entityClass, EnumCreatureType creatureType,
            int minGroup, int maxGroup, String skinTexturePath, String modelClassName, int skinWidth, int skinHeight) {
            this.registryName = registryName;
            this.entityClass = entityClass;
            this.creatureType = creatureType;
            this.minGroup = minGroup;
            this.maxGroup = maxGroup;
            this.skinTexturePath = skinTexturePath;
            this.modelClassName = modelClassName;
            this.skinWidth = skinWidth;
            this.skinHeight = skinHeight;
        }

        /**
         * FML {@code registerModEntity} 用的实体名（命名空间内唯一；不占全局 id ⇒ 无蛋）。
         * 运行时串名 = {@code gtsr.<本返回值>}，前缀唯一出处是 FML，见字段注释。
         */
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

        /**
         * <b>自有</b>皮肤路径（D1 起不再借原版皮；{@code assets/<domain>/} 之前的完整
         * {@link net.minecraft.util.ResourceLocation} 串，domain 恒 {@code gtsr}）。
         * <p>
         * v1.20.35 到 D2 这一段借的是 {@code minecraft} 域的 bat / chicken / skeleton 三张皮，
         * 字段名因此叫 {@code vanillaTexturePath}；D1 上自有皮肤后改名 {@code skinTexturePath}，
         * 免得名字继续宣称一件已经不再成立的事。路径的唯一申报处就是这里，
         * 渲染器只调 {@link #skinTexturePath()} 取（见 {@code GTSRCreatureRenderers#textureOf}），
         * 由 {@code CreatureSpawnAuthorityCheck} 判据 4 的 D 组钉"渲染器实际读到的 == 本字段"，
         * 并由 I2 组钉"该路径下贴图的 IHDR == {@link #skinWidth()}×{@link #skinHeight()} == 模型画布"。
         */
        public String skinTexturePath() {
            return this.skinTexturePath;
        }

        /** 复用的模型类 SimpleName（仅申报与断言用；真正的模型实例在客户端渲染器里）。 */
        public String modelClassName() {
            return this.modelClassName;
        }

        /** 皮肤画布宽像素（D1 的硬口径；与 {@link #modelClassName()} 同一批申报，由 I2 组三方对钉）。 */
        public int skinWidth() {
            return this.skinWidth;
        }

        /** 皮肤画布高像素。 */
        public int skinHeight() {
            return this.skinHeight;
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

    /**
     * 生物档诊断摘要（<b>P12 新增，plan §2.1 L8</b>）：总开关 + 三实体基权 + 该维四群系的
     * <b>在册声明表条目数</b>（dim79 恒 0/0/0/0，与 P9「dim79 恒零」同一口径）。
     * <p>
     * 条目数走 {@link #declaredEntries}（声明真值的纯派生复算，不读群系实例的运行时列表、
     * 不触发 {@code GTSRBiomeBase} 的延迟填充），零行为介入；缺席群系（SHORT/EMPTY 未入账成员）
     * 的权重被吞这件事由框架侧 {@code logCreatureWeightAbsorbedOnce} 锚点负责可见。
     */
    public static String diagSummary(String dimKey) {
        final StringBuilder sb = new StringBuilder();
        sb.append("enabled=")
            .append(Config.prosperityCreaturesEnabled)
            .append(" weights=")
            .append(Species.GEAR_PIGEON.baseWeight())
            .append('/')
            .append(Species.STEAM_FIREFLY.baseWeight())
            .append('/')
            .append(Species.SLAG_RIDGE_HUNTER.baseWeight());
        if (dimKey == null) {
            return sb.append(" tables=NA(no-dim-key)")
                .toString();
        }
        sb.append(" tables=[");
        boolean first = true;
        for (final BiomeId id : BiomeId.values()) {
            if (!dimKey.equals(id.dimKey())) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(id.name())
                .append(':')
                .append(declaredEntries(id).size());
        }
        return sb.append(']')
            .toString();
    }
}
