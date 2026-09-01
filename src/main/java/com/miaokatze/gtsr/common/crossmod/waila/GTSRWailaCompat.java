package com.miaokatze.gtsr.common.crossmod.waila;

import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import gregtech.api.enums.Mods;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * Waila 兼容层入口（参照 GT5U gregtech.crossmod.waila.Waila 的 IMC 注册模式）。
 * <p>
 * 本类在类级别包含 Waila 类型（{@link #callbackRegister(IWailaRegistrar)} 的签名）并实例化
 * {@code GTSRNodeWailaProvider}，因此类加载要求 Waila 位于 classpath。唯一保护是
 * {@link com.miaokatze.gtsr.main.CommonProxy#init} 中的外置 {@code isModLoaded} 守卫：
 * Waila 缺失时该调用不会发生，本类根本不会被加载。{@link #callbackRegister(IWailaRegistrar)}
 * 仍仅由 Waila 经 IMC 反射调用。
 */
public class GTSRWailaCompat {

    /**
     * 向 Waila 发送 "register" IMC 消息，登记回调方法全限定名；Waila 缺失时为空操作。
     */
    public static void init() {
        if (Loader.isModLoaded(Mods.Waila.ID)) {
            FMLInterModComms
                .sendMessage(Mods.Waila.ID, "register", GTSRWailaCompat.class.getName() + ".callbackRegister");
            GTSteamReborn.LOG.info("已检测到 Waila，注册 GTSR 节点自定义名 WAILA 显示兼容。");
        }
    }

    /**
     * Waila 经 IMC 反射调用的注册回调（仅在 Waila 存在时执行）。
     * 注册头部 provider（替换 tooltip 第一行方块名）与 NBT provider（服务端→客户端同步自定义名）。
     */
    public static void callbackRegister(IWailaRegistrar registrar) {
        GTSRNodeWailaProvider provider = new GTSRNodeWailaProvider();
        registrar.registerHeadProvider(provider, BaseMetaTileEntity.class);
        registrar.registerNBTProvider(provider, BaseMetaTileEntity.class);
    }
}
