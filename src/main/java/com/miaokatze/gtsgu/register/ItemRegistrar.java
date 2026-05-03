package com.miaokatze.gtsgu.register;

// import static com.miaokatze.gtsgu.common.api.enums.GTSGUItemList.TestCoin; // [GTSGU-DEV] 测试物品已禁用

import com.miaokatze.gtsgu.main.GTSteamGeologyUtilities;

/**
 * 物品注册器
 * 负责模组内所有普通物品（非机器方块）的注册与初始化逻辑
 */
public class ItemRegistrar {

    /**
     * 初始化并注册所有物品
     */
    public static void init() {
        GTSteamGeologyUtilities.LOG.info("开始通过 ItemRegistrar 注册物品...");
        // [GTSGU-DEV] 临时禁用所有测试物品注册，为后续开发清理环境。源码完整保留。
        // registerTestCoin();
        // registerTestCoinE(); // 取消测试物品注册，源码保留
        GTSteamGeologyUtilities.LOG.info("物品注册完成（当前已禁用所有测试物品）。");
    }

    /**
     * 注册测试硬币
     */
    // [GTSGU-DEV] 测试物品已禁用
    // private static void registerTestCoin() {
    // TestCoin.setAndRegister(com.miaokatze.gtsgu.common.items.TestCoin::new);
    // }

    /**
     * 注册电子测试硬币
     */
    // [GTSGU-DEV] 测试物品已禁用
    // private static void registerTestCoinE() {
    // TestCoinE.setAndRegister(com.miaokatze.gtsgu.common.items.TestCoinE::new);
    // }
}
