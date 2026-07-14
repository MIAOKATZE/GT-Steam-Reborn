package com.miaokatze.gtsr.main;

import com.miaokatze.gtsr.Tags;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.loader.GTSRRecipeLoader;
import com.miaokatze.gtsr.loader.ItemLoader;
import com.miaokatze.gtsr.loader.MachineLoader;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import gregtech.api.GregTechAPI;

/**
 * 通用代理类
 * 处理服务端和客户端共有的逻辑，如配置加载、机器注册、创造模式物品栏初始化等。
 */
public class CommonProxy {

    /**
     * 预初始化阶段 (PreInit)
     * 在此阶段读取配置文件，并将机器注册任务添加到 GregTech 的处理队列中。
     */
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        GTSteamReborn.LOG.info("GTSteamReborn 开始初始化 (版本: " + Tags.VERSION + ")");

        try {
            ItemLoader.initItems();
            GTSteamReborn.LOG.info("[0/3] 物品注册完成。");
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[0/3] 物品注册过程中发生严重错误，请检查日志", t);
        }

        Runnable registerRunnable = () -> {
            GTSteamReborn.LOG.info("[1/3] 开始执行机器注册流程...");
            try {
                MachineLoader.initMachines();
                GTSteamReborn.LOG.info("[1/3] 机器注册流程执行完毕。");
            } catch (Throwable t) {
                GTSteamReborn.LOG.error("[1/3] 机器注册过程中发生严重错误，请检查日志", t);
            }
        };

        // 使用 sAfterGTPreload 队列（GT PreInit 末尾执行），而非 sAfterGTLoad（GT Init 末尾执行）。
        // 这是 GT5U 官方推荐模式（参考 ggfab/GigaGramFab.java:65-98）：
        // - sAfterGTPreload 执行时 sPreloadStarted=true、sPostloadStarted=false，MTE 注册构造函数阶段检查通过
        // - GT 自身的 MTE 已在 LoaderMetaTileEntities.run()（GTMod.java:325）中注册完毕，避免 ID 冲突
        // - 机器注册时机更早，避免错过 GT Init 阶段的 RecipeMap/NEI 处理窗口（根因 B 修复）
        // MachineLoader.initMachines() 只做 MTE 注册（构造函数 + ItemList.set），不查询 GT 配方，符合 sAfterGTPreload 使用场景
        GregTechAPI.sAfterGTPreload.add(registerRunnable);
        GTSteamReborn.LOG.info("[1/3] 已将机器注册任务加入 GregTech PreInit 加载队列。");
    }

    /**
     * 初始化阶段 (Init)
     * 在此阶段完成创造模式物品栏的初始化，并注册服务端 Tick 事件处理器。
     */
    @SuppressWarnings({ "unused" })
    public void init(FMLInitializationEvent event) {
        GTSteamReborn.LOG.info("[2/3] 开始初始化创造模式物品栏...");

        CreativeTabManager.initCreativeTab();
        GTSteamReborn.LOG.info(
            "[2/3] 创造模式物品栏初始化完成，当前包含 " + CreativeTabManager.getItemsToAdd()
                .size() + " 个物品。");
    }

    /**
     * 后初始化阶段 (PostInit)
     * 处理与其他模组的交互或完成最终设置，如注册测试配方。
     */
    @SuppressWarnings({ "unused" })
    public void postInit(FMLPostInitializationEvent event) {
        GTSteamReborn.LOG.info("[3/3] 开始注册 GTSR 配方...");
        try {
            new GTSRRecipeLoader().run();
            GTSteamReborn.LOG.info("[3/3] GTSR 配方注册完成。");
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[3/3] GTSR 配方注册过程中发生错误", t);
        }
    }

    /**
     * 服务器启动阶段
     * 用于注册服务器端命令。
     */
    @SuppressWarnings({ "unused" })
    public void serverStarting(FMLServerStartingEvent event) {}

    /**
     * 模组加载完成阶段
     * 如果之前注册失败，可以在此处进行最后的补救尝试。
     */
    public void loadComplete(cpw.mods.fml.common.event.FMLLoadCompleteEvent event) {}
}
