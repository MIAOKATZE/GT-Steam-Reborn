package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.machine.MTESteamHubArray;

/**
 * 蒸汽枢纽「缓存节点状态管理界面」的 MUI2 工厂（通用骨架见 AbstractGTSRPosUiFactory）。
 * 与钻井枢纽 HubStatusGuiFactory 同模式：独立 factory 注册（CommonProxy.init 中
 * GuiManager.registerFactory），枢纽主 GUI 仍是空手普通右击打开，
 * 本界面由手持枢纽终端右击触发。
 */
public class SteamHubStatusGuiFactory extends AbstractGTSRPosUiFactory<MTESteamHubArray> {

    public static final SteamHubStatusGuiFactory INSTANCE = new SteamHubStatusGuiFactory();

    private SteamHubStatusGuiFactory() {
        super("gtsr:steam_hub_status", MTESteamHubArray.class, MTESteamHubStatusGui::new, "Steam Hub Array");
    }

    /**
     * 服务端调用：为玩家打开指定蒸汽枢纽的状态管理界面。
     */
    public static void open(EntityPlayer player, MTESteamHubArray hub) {
        INSTANCE.openGui(player, hub);
    }
}
