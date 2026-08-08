package com.miaokatze.gtsr.common.loot;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

/**
 * 蒸汽纠缠奇点箱子战利品注入。
 * <p>
 * 注入到村庄铁匠铺、地牢、废弃矿井、要塞(走廊/十字口/图书馆)六类箱子。
 * 目标概率约 2%(每次抽取该物品出现概率),单次出现 1 个。
 * <p>
 * 1.7.10 的 ChestGenHooks 通过 WeightedRandomChestContent 的 weight 相对池子总权重
 * 决定选中概率。选取概率 ≈ weight / (池子总权重 + weight)。
 * <p>
 * 权重取值依据: vanilla 地牢池子总权约 120(dungeonChest)、村庄铁匠铺约 94,GTNH 可能更高。
 * 取 WEIGHT=2 对应抽取概率约 1.6%~2.1%(地牢/铁匠铺),接近规格要求的 2%。
 * 精确 2% 依赖 GTNH 实际池子总权重,实机概率待用户校验。
 */
public final class LootInjectionRunawaySingularity {

    /** 单次出现最小数量 */
    private static final int MIN_AMOUNT = 1;
    /** 单次出现最大数量 */
    private static final int MAX_AMOUNT = 1;
    /**
     * 权重:相对池子总权重,目标 ~2% 选中概率。
     * 依据: vanilla dungeonChest 总权 ~120、villageBlacksmith ~94。
     * WEIGHT=2 → 抽取概率约 1.6%(地牢)~2.1%(铁匠铺),接近 2%。
     */
    private static final int WEIGHT = 2;

    private LootInjectionRunawaySingularity() {}

    /** 在 postInit 调用:把蒸汽纠缠奇点注入六类箱子战利品池 */
    public static void init() {
        ItemStack stack = GTSRItemList.SteamEntangledSingularity.get(1);
        if (stack == null) {
            return; // 物品未注册,跳过
        }
        WeightedRandomChestContent content = new WeightedRandomChestContent(stack, MIN_AMOUNT, MAX_AMOUNT, WEIGHT);
        ChestGenHooks.getInfo(ChestGenHooks.VILLAGE_BLACKSMITH)
            .addItem(content);
        ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST)
            .addItem(content);
        ChestGenHooks.getInfo(ChestGenHooks.MINESHAFT_CORRIDOR)
            .addItem(content);
        ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_CORRIDOR)
            .addItem(content);
        ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_CROSSING)
            .addItem(content);
        ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_LIBRARY)
            .addItem(content);
    }
}
