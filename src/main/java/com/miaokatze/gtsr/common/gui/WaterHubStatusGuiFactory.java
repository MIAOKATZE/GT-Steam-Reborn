package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;

/**
 * 蓄水枢纽阵列「缓存节点状态管理界面」的 MUI2 工厂（通用骨架见 AbstractGTSRPosUiFactory）。
 * 与钻井枢纽 HubStatusGuiFactory 同模式：独立 factory 注册（CommonProxy.init 中
 * GuiManager.registerFactory），枢纽主 GUI 仍是空手普通右击打开，
 * 本界面由手持枢纽终端右击触发。
 */
public class WaterHubStatusGuiFactory extends AbstractGTSRPosUiFactory<MTEWaterHubArray> {

    public static final WaterHubStatusGuiFactory INSTANCE = new WaterHubStatusGuiFactory();

    private WaterHubStatusGuiFactory() {
        super("gtsr:water_hub_status", MTEWaterHubArray.class, MTEWaterHubStatusGui::new, "Water Hub Array");
    }

    /**
     * 服务端调用：为玩家打开指定蓄水枢纽阵列的状态管理界面。
     */
    public static void open(EntityPlayer player, MTEWaterHubArray hub) {
        INSTANCE.openGui(player, hub);
    }
}
