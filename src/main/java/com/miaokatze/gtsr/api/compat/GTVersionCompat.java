package com.miaokatze.gtsr.api.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;

/**
 * GT 版本兼容层：通过运行时探测 GT5U 版本号，实现 beta-1 / beta-2 / beta-3 的单代码库兼容。
 *
 * <p>
 * 背景：GTSR 原本需要维护两个分支——master 用 beta-2 的 GT5U (5.09.54.20) 编译，beta-1 分支用 beta-1 的 GT5U
 * (5.09.52.594) 编译。本兼容层通过运行时探测 GT5U 版本号，让单一代码库同时兼容多个版本，免去双分支维护负担；
 * 现扩展为三态（beta-3 = GT5U 5.09.54.133+，亦为当前编译目标）。
 * </p>
 *
 * <p>
 * 当前处理的差异点：
 * <ul>
 * <li><b>防爆玻璃</b>：beta-1 环境下 IC2 仍保留 {@code blockAlloyGlass}（meta 0）；
 * beta-2 与 beta-3 的 GT5U 改用 {@link GregTechAPI#sBlockGlass1}（meta 10 = ReinforcedGlass）替代。
 * 见 GT5U PosteaTransformers.java 的 {@code addSimpleReplacement("IC2:blockAlloyGlass", ...)} 迁移逻辑。</li>
 * <li><b>ICasingTextureProvider#getCasingTexture</b>：beta-2 与 beta-3 的 {@link MTEHatch} 实现该接口提供仓体底材贴图；
 * beta-1 整个接口不存在（v1.10.88 诊断编译实证），直接调用会在运行时抛 NoSuchMethodError。
 * 该符号无法双版本编译，按维护指南改用反射适配（{@link #getCasingTextureOrNull(Object)}），
 * beta-1 由调用方以 null 回退默认机壳贴图。</li>
 * <li><b>Textures.BlockIcons#customAlpha</b>：beta-3 为双参签名（编译目标，直接调用）；
 * beta-1 / beta-2 仅有单参签名，经 {@link #customAlphaCompat(String, String)} 反射适配。</li>
 * <li><b>GTPP RecipeMap</b>：GTPP 与 GT5U 的 RecipeMap 静态字段分属不同 FQCN，
 * 经 {@link #gppRecipeMap(String)} 反射按序探测适配。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 探测策略：通过 {@link Loader#getIndexedModList()} 获取 GT5U 的版本号字符串，
 * 按第三段（PATCH）与第四段（BUILD）三态判定：
 * <ul>
 * <li>beta-1：GT5U 5.09.<b>52</b>.594 → PATCH 52 &lt; 54 → beta-1 模式</li>
 * <li>beta-2：GT5U 5.09.<b>54</b>.20 → PATCH == 54 且 BUILD &lt; 133 → beta-2 模式</li>
 * <li>beta-3：GT5U 5.09.<b>54</b>.133+（或 PATCH &gt; 54）→ beta-3 模式（防爆玻璃复用 beta-2 路径）</li>
 * </ul>
 * 旧三段形态（无 BUILD）按 BUILD = 0 等价折算；解析失败 / unknown / 段数不足时默认 beta-2 并一次性 WARN。
 * 该方案比「检测 IC2 blockAlloyGlass 是否存在」更可靠，因为 IC2 在两个版本中可能都注册了 blockAlloyGlass，
 * 但 GT5U 对防爆玻璃的引用方式不同。
 * </p>
 *
 * <p>
 * 编译期保证：本类以 beta-3 的 GT5U 为编译目标（{@code Textures.BlockIcons.customAlpha(String, String)}
 * 双参签名直调）；其余差异点所引用的 API 符号（如 {@link GregTechAPI#sBlockGlass1} 字段）在 beta-1 / beta-2 /
 * beta-3 均存在，直接引用即可多版本编译。
 * </p>
 *
 * <p>
 * 维护指南：当发现新的版本差异点时，在本类中新增对应的静态探测字段与适配方法，
 * 并在各调用处将硬编码引用替换为本类提供的统一 API。详见 plan/sum/12_兼容层维护_sum.md。
 * </p>
 */
public class GTVersionCompat {

    /** log4j2 日志器（仅版本判定失败的一次性 WARN 使用）。 */
    private static final Logger LOGGER = LogManager.getLogger("gtsr");

    /**
     * GT5U 版本三态枚举。
     */
    public enum GTVersion {

        /** beta-1：GT5U 5.09.52.x（如 5.09.52.594）。 */
        BETA1,

        /** beta-2：GT5U 5.09.54.0 ~ 5.09.54.132（如 5.09.54.20）。 */
        BETA2,

        /** beta-3：GT5U 5.09.54.133+ 或 PATCH &gt; 54（如 5.09.54.133），当前编译目标。 */
        BETA3
    }

    /**
     * 当前运行环境的 GT5U 版本三态判定结果（静态缓存，判定仅在类初始化执行一次）。
     * <p>
     * 解析失败 / unknown / 段数不足时默认 {@link GTVersion#BETA2}（初始化时已一次性 WARN，不再重复告警）。
     * </p>
     */
    private static final GTVersion GT_VERSION;

    /**
     * 防爆玻璃方块实例（缓存，避免每次调用都探测）。
     * <p>
     * beta-1 模式下为 IC2 {@code blockAlloyGlass}；beta-2 / beta-3 模式下为 {@link GregTechAPI#sBlockGlass1}。
     * </p>
     */
    private static final Block REINFORCED_GLASS_BLOCK;

    /**
     * 防爆玻璃方块的 meta 值（缓存）。
     * <p>
     * beta-1 模式下为 0；beta-2 / beta-3 模式下为 10（ReinforcedGlass）。
     * </p>
     */
    private static final int REINFORCED_GLASS_META;

    /**
     * beta-2 / beta-3 {@code ICasingTextureProvider#getCasingTexture} 的反射句柄（类加载时一次性解析）。
     * <p>
     * {@link MTEHatch} 类多版本均存在，可直接引用；但该方法仅 beta-2 / beta-3 存在，
     * beta-1 解析失败置 null（运行期间不变）。
     * </p>
     */
    private static final Method CASING_TEXTURE_METHOD = resolveCasingTextureMethod();

    /**
     * {@link #customAlphaCompat} 结果缓存（键为 domain:path，null 值表示该参数已判定失败）。线程假设：仅服务器/主线程与类初始化单线程调用；若未来接入 NEI 等客户端多线程路径需换
     * ConcurrentHashMap。
     */
    private static final Map<String, IIconContainer> CUSTOM_ALPHA_CACHE = new HashMap<>();

    /** {@link #gppRecipeMap} 结果缓存（键为字段名，null 值表示两个 FQCN 均失败的终态）。线程假设同 {@link #CUSTOM_ALPHA_CACHE}。 */
    private static final Map<String, RecipeMap<?>> GPP_RECIPE_MAP_CACHE = new HashMap<>();

    static {
        // 通过 GT5U 版本号检测运行环境（三态）
        // beta-1: GT5U 5.09.52.594（IC2 仍有 blockAlloyGlass，GTSR 用 IC2 blockAlloyGlass meta 0）
        // beta-2: GT5U 5.09.54.20（GT5U 改用 sBlockGlass1 meta 10 = ReinforcedGlass）
        // beta-3: GT5U 5.09.54.133+（防爆玻璃与 beta-2 同路径，sBlockGlass1 meta 10）
        GT_VERSION = resolveGTVersion(detectGTVersion());

        if (GT_VERSION == GTVersion.BETA1) {
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
            // beta-1 模式：使用 IC2 原生防爆玻璃 blockAlloyGlass meta 0
            REINFORCED_GLASS_BLOCK = GameRegistry.findBlock("IC2", "blockAlloyGlass");
            REINFORCED_GLASS_META = 0;
        } else {
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
            // beta-2 / beta-3 模式：GT5U 改用 sBlockGlass1 meta 10（ReinforcedGlass）
            REINFORCED_GLASS_BLOCK = GregTechAPI.sBlockGlass1;
            REINFORCED_GLASS_META = 10;
        }
    }

    private GTVersionCompat() {
        // 工具类，禁止实例化
    }

    /**
     * 通过 Forge 的 mod 列表获取 GT5U 的版本号字符串。
     *
     * @return GT5U 版本号（如 "5.09.54.20"）；获取失败时返回 "unknown"
     */
    private static String detectGTVersion() {
        try {
            ModContainer gtContainer = Loader.instance()
                .getIndexedModList()
                .get("gregtech");
            if (gtContainer != null) {
                return gtContainer.getVersion();
            }
        } catch (Exception e) {
            // 忽略异常，返回 unknown
        }
        return "unknown";
    }

    /**
     * 将 GT5U 版本号解析为三态判定结果。
     * <p>
     * 版本号格式：MAJOR.MINOR.PATCH.BUILD（如 5.09.52.594 或 5.09.54.133）。
     * <br>
     * 判定逻辑（四段形态）：PATCH &lt; 54 → BETA1；PATCH &gt; 54 → BETA3；
     * PATCH == 54 时 BUILD &lt; 133 → BETA2、BUILD ≥ 133 → BETA3。
     * <br>
     * 旧三段形态（无 BUILD）：PATCH &lt; 54 → BETA1；PATCH == 54 → BETA2（等价 BUILD = 0）；PATCH &gt; 54 → BETA3。
     * </p>
     *
     * @param version GT5U 版本号字符串
     * @return 三态判定结果；解析失败 / unknown / 段数不足 → BETA2（默认，与既有行为一致）
     */
    private static GTVersion resolveGTVersion(String version) {
        if (version != null && !"unknown".equals(version)) {
            try {
                String[] parts = version.split("\\.");
                if (parts.length >= 4) {
                    int patch = Integer.parseInt(parts[2]);
                    if (patch < 54) return GTVersion.BETA1; // beta-1: 5.09.52.594 → patch=52 < 54
                    if (patch > 54) return GTVersion.BETA3;
                    int build = Integer.parseInt(parts[3]);
                    return build < 133 ? GTVersion.BETA2 : GTVersion.BETA3; // patch == 54
                }
                if (parts.length == 3) {
                    int patch = Integer.parseInt(parts[2]);
                    if (patch < 54) return GTVersion.BETA1;
                    if (patch == 54) return GTVersion.BETA2; // 旧三段形态，无 BUILD，等价 BUILD = 0
                    return GTVersion.BETA3;
                }
                // 段数不足（既非三段也非四段形态），落入默认分支
            } catch (NumberFormatException e) {
                // 版本号解析失败，落入默认分支
            }
        }
        // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
        // 解析失败/unknown/段数不足 → 默认 BETA2；仅类初始化调用一次，WARN 天然只触发一次
        LOGGER.warn("Failed to resolve GT5U version \"{}\", defaulting to beta-2 compat mode", version);
        return GTVersion.BETA2;
    }

    /**
     * 判断当前运行环境是否为 beta-1 模式。
     *
     * @return true 表示当前为 beta-1（GT5U 5.09.52.x）
     */
    public static boolean isBeta1() {
        return GT_VERSION == GTVersion.BETA1;
    }

    /**
     * 判断当前运行环境是否为 beta-2 模式。
     *
     * @return true 表示当前为 beta-2（GT5U 5.09.54.[0, 133)）
     */
    public static boolean isBeta2() {
        return GT_VERSION == GTVersion.BETA2;
    }

    /**
     * 判断当前运行环境是否为 beta-3 模式。
     *
     * @return true 表示当前为 beta-3（GT5U 5.09.54.133+ 或更高）
     */
    public static boolean isBeta3() {
        return GT_VERSION == GTVersion.BETA3;
    }

    /**
     * 获取当前运行环境的 GT5U 版本三态（缓存判定结果）。
     *
     * @return BETA1 / BETA2 / BETA3；解析失败默认 BETA2（初始化时已一次性 WARN）
     */
    public static GTVersion gtVersion() {
        return GT_VERSION;
    }

    /**
     * 解析 beta-2 / beta-3 的 {@code MTEHatch#getCasingTexture} 反射句柄。
     *
     * @return 方法句柄；beta-1（方法不存在）或解析被拒时返回 null
     */
    private static Method resolveCasingTextureMethod() {
        try {
            return MTEHatch.class.getMethod("getCasingTexture");
        } catch (NoSuchMethodException | SecurityException e) {
            // beta-1：ICasingTextureProvider 接口与 getCasingTexture 均不存在
            return null;
        }
    }

    /**
     * 版本安全地读取仓体底材贴图（beta-2 / beta-3 {@code MTEHatch#getCasingTexture} 反射适配）。
     *
     * @param aMetaTileEntity 目标 MTE（须为 MTEHatch 后代）
     * @return beta-2 / beta-3 返回 {@code getCasingTexture()} 结果；beta-1、非 MTEHatch 或调用异常返回 null
     *         （调用方应回退默认机壳贴图）
     */
    public static ITexture getCasingTextureOrNull(Object aMetaTileEntity) {
        if (CASING_TEXTURE_METHOD == null || aMetaTileEntity == null) return null;
        try {
            return (ITexture) CASING_TEXTURE_METHOD.invoke(aMetaTileEntity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前版本下防爆玻璃的方块实例。
     * <p>
     * beta-1 返回 IC2 {@code blockAlloyGlass}；beta-2 / beta-3 返回 {@link GregTechAPI#sBlockGlass1}。
     * </p>
     *
     * @return 防爆玻璃方块
     */
    public static Block getReinforcedGlassBlock() {
        return REINFORCED_GLASS_BLOCK;
    }

    /**
     * 获取当前版本下防爆玻璃的 meta 值。
     * <p>
     * beta-1 返回 0；beta-2 / beta-3 返回 10。
     * </p>
     *
     * @return 防爆玻璃 meta 值
     */
    public static int getReinforcedGlassMeta() {
        return REINFORCED_GLASS_META;
    }

    /**
     * 版本安全地创建 customAlpha 贴图容器（供 TextureManager 消费，结果按参数缓存）。
     * <p>
     * beta-3：{@code Textures.BlockIcons.customAlpha(domain, path)} 双参签名（编译目标，直接调用）。
     * <br>
     * beta-1 / beta-2：仅有单参签名 {@code customAlpha(String aName)}，反射拼接 domain:path 调用；
     * 任何 Throwable（含 NoSuchMethodException / InvocationTargetException / LinkageError）返回 null，调用方自行兜底。
     * </p>
     *
     * @param domain 资源域（如 "gtsr"）
     * @param path   贴图路径
     * @return IIconContainer 实例；当前版本无对应签名或调用失败返回 null
     */
    public static IIconContainer customAlphaCompat(String domain, String path) {
        final String key = domain + ":" + path;
        if (CUSTOM_ALPHA_CACHE.containsKey(key)) return CUSTOM_ALPHA_CACHE.get(key);
        IIconContainer result = null;
        try {
            if (GT_VERSION == GTVersion.BETA3) {
                // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
                // beta-3：双参签名为编译目标，直接调用
                result = Textures.BlockIcons.customAlpha(domain, path);
            } else {
                // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
                // beta-1 / beta-2：单参签名，反射拼接 domain:path 调用
                Method customAlpha = Textures.BlockIcons.class.getMethod("customAlpha", String.class);
                result = (IIconContainer) customAlpha.invoke(null, key);
            }
        } catch (Throwable t) {
            // 任何反射/链接失败 → null，由调用方兜底（null 终态同样缓存）
            result = null;
        }
        CUSTOM_ALPHA_CACHE.put(key, result);
        return result;
    }

    /**
     * 反射读取 GTPP / GT5U 的 RecipeMap 同名静态字段（供 GTPP 配方映射消费，结果缓存含失败终态）。
     * <p>
     * 按序探测两个 FQCN 的同名字段：先 {@code gtPlusPlus.api.recipe.GTPPRecipeMaps}，
     * 后 {@code gregtech.api.recipe.RecipeMaps}（Class.forName → getDeclaredField → setAccessible → get(null)）。
     * 任一命中即返回；两者均失败返回 null（终态缓存），调用方自行兜底。
     * </p>
     *
     * @param fieldName RecipeMap 静态字段名
     * @return 对应 {@link RecipeMap} 实例；类 / 字段不存在或读取异常返回 null
     */
    public static RecipeMap<?> gppRecipeMap(String fieldName) {
        if (GPP_RECIPE_MAP_CACHE.containsKey(fieldName)) return GPP_RECIPE_MAP_CACHE.get(fieldName);
        RecipeMap<?> result = null;
        // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
        // 按序探测 GTPP 与 GT5U 两个 FQCN 的同名字段
        for (String fqcn : new String[] { "gtPlusPlus.api.recipe.GTPPRecipeMaps", "gregtech.api.recipe.RecipeMaps" }) {
            try {
                Field field = Class.forName(fqcn)
                    .getDeclaredField(fieldName);
                field.setAccessible(true);
                result = (RecipeMap<?>) field.get(null);
                break;
            } catch (Throwable t) {
                // 当前 FQCN 不存在该字段或读取失败，继续探测下一个
            }
        }
        GPP_RECIPE_MAP_CACHE.put(fieldName, result);
        return result;
    }
}
