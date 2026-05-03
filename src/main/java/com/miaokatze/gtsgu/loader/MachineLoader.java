package com.miaokatze.gtsgu.loader;

import com.miaokatze.gtsgu.common.api.enums.GTSGUItemList;
import com.miaokatze.gtsgu.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsgu.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsgu.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsgu.register.CreativeTabManager;
import com.miaokatze.gtsgu.register.MultiblockMachineRegistrar;
import com.miaokatze.gtsgu.register.RegistrationManager;
import com.miaokatze.gtsgu.register.StandardMachineRegistrar;

/**
 * 机器加载器
 * 负责初始化并执行所有元机器实体 (MTE) 的注册逻辑。
 * 通过 RegistrationManager 统一管理注册流程，确保注册顺序可控。
 */
public class MachineLoader {

    /**
     * 初始化所有机器
     * 该方法应在模组预初始化 (PreInit) 或初始化 (Init) 阶段调用
     */
    public static void initMachines() {
        // [GTSGU-DEV] 临时禁用所有测试机器注册（含单方块和多方块），为后续开发清理环境。源码完整保留。
        // RegistrationManager manager = RegistrationManager.getInstance();

        // // 创建单方块机器的注册器实例
        // StandardMachineRegistrar standardMachineRegistrar = new StandardMachineRegistrar();
        // manager.addRegistrar(standardMachineRegistrar::registerAll);

        // // 创建多方块机器的注册器实例
        // MultiblockMachineRegistrar multiblockMachineRegistrar = new MultiblockMachineRegistrar();
        // manager.addRegistrar(multiblockMachineRegistrar::registerAll);

        // // 统一执行所有已添加的注册任务
        // manager.registerAll();

        // --- v0.0.1: 蒸汽缓存节点 ---
        registerSteamMachines();
    }

    private static void registerSteamMachines() {
        GTSGUItemList.SteamCacheNode.set(
            new MTESteamCacheNode(
                MetaTileEntityID.STEAM_CACHE_NODE.ID,
                "gtsgu.steam.cache.node",
                "Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamCacheNode.get(1));

        GTSGUItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsgu.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSGUItemList.ReinforcedSteamCacheNode.get(1));
    }
}
