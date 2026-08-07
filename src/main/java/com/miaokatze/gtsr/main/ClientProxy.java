package com.miaokatze.gtsr.main;

import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.tick.SingularityClientFXHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

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

}
