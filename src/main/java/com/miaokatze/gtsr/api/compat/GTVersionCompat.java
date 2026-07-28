package com.miaokatze.gtsr.api.compat;

import net.minecraft.block.Block;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/**
 * GT 版本兼容层：通过运行时探测 GT5U 版本号，实现 beta-1 与 beta-2 的单代码库兼容。
 *
 * <p>
 * 背景：GTSR 原本需要维护两个分支——master 用 beta-2 的 GT5U (5.09.54.20) 编译，beta-1 分支用 beta-1 的 GT5U
 * (5.09.52.594) 编译。本兼容层通过运行时探测 GT5U 版本号，让单一代码库同时兼容两个版本，免去双分支维护负担。
 * </p>
 *
 * <p>
 * 当前处理的差异点：
 * <ul>
 * <li><b>防爆玻璃</b>：beta-1 环境下 IC2 仍保留 {@code blockAlloyGlass}（meta 0）；
 * beta-2 的 GT5U 改用 {@link GregTechAPI#sBlockGlass1}（meta 10 = ReinforcedGlass）替代。
 * 见 GT5U PosteaTransformers.java 的 {@code addSimpleReplacement("IC2:blockAlloyGlass", ...)} 迁移逻辑。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 探测策略：通过 {@link Loader#getIndexedModList()} 获取 GT5U 的版本号字符串，
 * 比较版本号的第三段（patch 号）：
 * <ul>
 * <li>beta-1：GT5U 5.09.<b>52</b>.594 → 第三段 52 &lt; 54 → beta-1 模式</li>
 * <li>beta-2：GT5U 5.09.<b>54</b>.20 → 第三段 54 ≥ 54 → beta-2 模式</li>
 * </ul>
 * 该方案比「检测 IC2 blockAlloyGlass 是否存在」更可靠，因为 IC2 在两个版本中可能都注册了 blockAlloyGlass，
 * 但 GT5U 对防爆玻璃的引用方式不同。
 * </p>
 *
 * <p>
 * 编译期保证：beta-1 与 beta-2 的 GT5U 都包含差异点所引用的全部 API 符号（如 {@link GregTechAPI#sBlockGlass1}
 * 字段在 beta-1 的 GregTechAPI.java:180、beta-2 的 :185 均存在），因此无需反射，直接引用即可双版本编译。
 * </p>
 *
 * <p>
 * 维护指南：当发现新的版本差异点时，在本类中新增对应的静态探测字段与适配方法，
 * 并在各调用处将硬编码引用替换为本类提供的统一 API。详见 plan/sum/12_兼容层维护_sum.md。
 * </p>
 */
public class GTVersionCompat {

    /**
     * 当前运行环境是否为 beta-1 模式。
     * <p>
     * 由静态初始化块根据 GT5U 版本号一次性确定，运行期间不会变化。
     * </p>
     */
    private static final boolean IS_BETA_1;

    /**
     * 防爆玻璃方块实例（缓存，避免每次调用都探测）。
     * <p>
     * beta-1 模式下为 IC2 {@code blockAlloyGlass}；beta-2 模式下为 {@link GregTechAPI#sBlockGlass1}。
     * </p>
     */
    private static final Block REINFORCED_GLASS_BLOCK;

    /**
     * 防爆玻璃方块的 meta 值（缓存）。
     * <p>
     * beta-1 模式下为 0；beta-2 模式下为 10（ReinforcedGlass）。
     * </p>
     */
    private static final int REINFORCED_GLASS_META;

    static {
        // 通过 GT5U 版本号检测运行环境
        // beta-1: GT5U 5.09.52.594（IC2 仍有 blockAlloyGlass，GTSR 用 IC2 blockAlloyGlass meta 0）
        // beta-2: GT5U 5.09.54.20（GT5U 改用 sBlockGlass1 meta 10 = ReinforcedGlass）
        String gtVersion = detectGTVersion();
        IS_BETA_1 = isBeta1Version(gtVersion);

        if (IS_BETA_1) {
            // beta-1 模式：使用 IC2 原生防爆玻璃 blockAlloyGlass meta 0
            REINFORCED_GLASS_BLOCK = GameRegistry.findBlock("IC2", "blockAlloyGlass");
            REINFORCED_GLASS_META = 0;
        } else {
            // beta-2 模式：GT5U 改用 sBlockGlass1 meta 10（ReinforcedGlass）
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
     * 判断给定版本号是否为 beta-1 版本。
     * <p>
     * 版本号格式：MAJOR.MINOR.PATCH.BUILD（如 5.09.52.594 或 5.09.54.20）
     * <br>
     * 判定逻辑：第三段（PATCH）&lt; 54 → beta-1；≥ 54 → beta-2。
     * </p>
     *
     * @param version GT5U 版本号字符串
     * @return true 表示为 beta-1 版本；false 表示为 beta-2 或解析失败（默认 beta-2）
     */
    private static boolean isBeta1Version(String version) {
        if (version == null || "unknown".equals(version)) {
            return false; // 默认 beta-2
        }
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 3) {
                int patch = Integer.parseInt(parts[2]);
                // beta-1: 5.09.52.594 → patch=52 < 54
                // beta-2: 5.09.54.20 → patch=54 >= 54
                return patch < 54;
            }
        } catch (NumberFormatException e) {
            // 版本号解析失败，默认 beta-2
        }
        return false;
    }

    /**
     * 判断当前运行环境是否为 beta-1 模式。
     *
     * @return true 表示当前为 beta-1（GT5U 5.09.52.x）；false 表示为 beta-2（GT5U 5.09.54+）
     */
    public static boolean isBeta1() {
        return IS_BETA_1;
    }

    /**
     * 获取当前版本下防爆玻璃的方块实例。
     * <p>
     * beta-1 返回 IC2 {@code blockAlloyGlass}；beta-2 返回 {@link GregTechAPI#sBlockGlass1}。
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
     * beta-1 返回 0；beta-2 返回 10。
     * </p>
     *
     * @return 防爆玻璃 meta 值
     */
    public static int getReinforcedGlassMeta() {
        return REINFORCED_GLASS_META;
    }
}
