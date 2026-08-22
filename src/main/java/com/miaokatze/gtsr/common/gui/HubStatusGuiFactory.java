package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

/**
 * 钻井枢纽「节点状态管理界面」的 MUI2 工厂（通用骨架见 AbstractGTSRPosUiFactory）。
 * 参照 GT5U CoverUIFactory 模式：作为独立 factory 注册（CommonProxy.init 中
 * GuiManager.registerFactory），与枢纽主 GUI 的 MetaTileEntityGuiHandler 路径互不干扰——
 * 主 GUI 仍是空手普通右击打开，本界面由空手 + 潜行右击触发。
 */
public class HubStatusGuiFactory extends AbstractGTSRPosUiFactory<MTESingularityDrillingHub> {

    public static final HubStatusGuiFactory INSTANCE = new HubStatusGuiFactory();

    private HubStatusGuiFactory() {
        super(
            "gtsr:hub_status",
            MTESingularityDrillingHub.class,
            MTESingularityHubStatusGui::new,
            "Singularity Drilling Hub");
    }

    /**
     * 服务端调用：为玩家打开指定枢纽的状态管理界面。
     */
    public static void open(EntityPlayer player, MTESingularityDrillingHub hub) {
        INSTANCE.openGui(player, hub);
    }
}
