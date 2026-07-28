package com.miaokatze.gtsr.api.compat;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/**
 * GT 版本兼容层：通过运行时特征探测，实现 beta-1 与 beta-2 的单代码库兼容。
 *
 * <p>
 * 背景：GTSR 原本需要维护两个分支——master 用 beta-2 的 GT5U (5.09.54.20) 编译，beta-1 分支用 beta-1 的 GT5U
 * (5.09.52.594) 编译。本兼容层通过运行时探测 GTNH 版本特征，让单一代码库同时兼容两个版本，免去双分支维护负担。
 * </p>
 *
 * <p>
 * 当前处理的差异点：
 * <ul>
 * <li><b>防爆玻璃</b>：beta-1 环境下 IC2 仍保留 {@code blockAlloyGlass}（meta 0）；
 * beta-2 移除了该方块，GT5U 改用 {@link GregTechAPI#sBlockGlass1}（meta 10 = ReinforcedGlass）替代。
 * 见 GT5U PosteaTransformers.java 第 52 行的迁移逻辑。</li>
 * </ul>
 * </p>
 *
 * <p>
 * 探测策略：IC2 的 {@code blockAlloyGlass} 仅在 beta-1 存在、在 beta-2 被移除，
 * 因此运行时检测 {@link GameRegistry#findBlock(String, String)} 对 {@code "IC2", "blockAlloyGlass"} 的返回值即可区分版本。
 * 该字段在 beta-1 与 beta-2 的 GT5U 中均存在于编译期（beta-1 在 GregTechAPI.java:180，beta-2 在 :185），
 * 故无需反射，直接引用即可在两个版本下编译通过。
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
     * 由静态初始化块根据 IC2 {@code blockAlloyGlass} 是否存在一次性确定，运行期间不会变化。
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
        // 探测 IC2 blockAlloyGlass 是否存在：beta-1 仍保留该方块，beta-2 已移除。
        // 以此作为版本特征区分运行环境。
        Block ic2Glass = GameRegistry.findBlock("IC2", "blockAlloyGlass");
        IS_BETA_1 = (ic2Glass != null);
        if (IS_BETA_1) {
            // beta-1 模式：使用 IC2 原生防爆玻璃 blockAlloyGlass meta 0
            REINFORCED_GLASS_BLOCK = ic2Glass;
            REINFORCED_GLASS_META = 0;
        } else {
            // beta-2 模式：IC2 已移除 blockAlloyGlass，GT5U 改用 sBlockGlass1 meta 10（ReinforcedGlass）
            REINFORCED_GLASS_BLOCK = GregTechAPI.sBlockGlass1;
            REINFORCED_GLASS_META = 10;
        }
    }

    private GTVersionCompat() {
        // 工具类，禁止实例化
    }

    /**
     * 判断当前运行环境是否为 beta-1 模式。
     *
     * @return true 表示当前为 beta-1（IC2 blockAlloyGlass 存在）；false 表示为 beta-2
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
