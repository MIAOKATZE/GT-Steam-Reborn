package com.miaokatze.gtsr.main;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.client.render.GTSRHubItemRenderer;
import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.tick.SingularityClientFXHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gregtech.api.GregTechAPI;

/**
 * 客户端代理类
 * 继承自 CommonProxy，用于处理仅在客户端（Client Side）执行的逻辑。
 * 例如：渲染注册、按键绑定、GUI 打开等。
 */
public class ClientProxy extends CommonProxy {

    /**
     * 初始化阶段 (Init)
     * 在此阶段注册客户端特定的事件处理器，如 HUD 渲染器。
     */
    @Override
    public void init(FMLInitializationEvent event) {
        // 调用父类的 init 方法，确保通用逻辑正常执行
        super.init(event);

        FMLCommonHandler.instance()
            .bus()
            .register(new SingularityClientFXHandler());

        MinecraftForge.EVENT_BUS.register(GTSRFXEngine.instance());
        FMLCommonHandler.instance()
            .bus()
            .register(GTSRFXEngine.instance());

        GTSteamReborn.LOG.info("[2/3] 客户端初始化完成");
    }

    /**
     * 后初始化阶段 (PostInit)
     * 晚于全部 mod（含 GT5U）的 Init 执行：GT5U 在其 Init（GTClient.onInitialization）注册自有物品渲染器，
     * 但从未给 ItemMachines 注册过渲染器，此处唯一注册点无覆盖冲突；缓存节点/奇点仓以外的机器物品
     * 由 GTSRHubItemRenderer.handleRenderType 恒 false 委托回 vanilla 原路径，零差异。
     */
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        MinecraftForgeClient
            .registerItemRenderer(Item.getItemFromBlock(GregTechAPI.sBlockMachines), new GTSRHubItemRenderer());
        GTSteamReborn.LOG.info("[3/3] 缓存节点/奇点仓物品平贴图渲染器注册完成。");
    }

}
