package com.miaokatze.gtsr.register;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 物品注册器
 * 负责模组内所有普通物品（非机器方块）的注册与初始化逻辑。
 * 当前 GTSR 的物品（芯片/催化剂/奇点）均通过 ItemLoader 直接注册，此处保留作为扩展点。
 */
public class ItemRegistrar {

    /**
     * 初始化并注册所有物品
     */
    public static void init() {
        GTSteamReborn.LOG.info("开始通过 ItemRegistrar 注册物品...");
        // 当前 GTSR 物品注册由 ItemLoader 统一处理，此处保留作为扩展点
        GTSteamReborn.LOG.info("物品注册完成。");
    }
}
