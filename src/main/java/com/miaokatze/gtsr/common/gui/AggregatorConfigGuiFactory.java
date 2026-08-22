package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

/**
 * 地壳物质聚合器「终端配置界面」的 MUI2 工厂（通用骨架见 AbstractGTSRPosUiFactory）。
 * 参照 HubStatusGuiFactory：作为独立 factory 注册（CommonProxy.init 中 GuiManager.registerFactory），
 * 与聚合器主 GUI 的 MetaTileEntityGuiHandler 路径互不干扰——主 GUI 仍是空手普通右击打开，
 * 本界面由手持枢纽终端右击触发（MTECrustMatterAggregator.onRightclick → openConfigGui）。
 */
public class AggregatorConfigGuiFactory extends AbstractGTSRPosUiFactory<MTECrustMatterAggregator> {

    public static final AggregatorConfigGuiFactory INSTANCE = new AggregatorConfigGuiFactory();

    private AggregatorConfigGuiFactory() {
        super(
            "gtsr:aggregator_config",
            MTECrustMatterAggregator.class,
            MTECrustMatterAggregatorConfigGui::new,
            "Crust Matter Aggregator");
    }

    /**
     * 服务端调用：为玩家打开指定聚合器的终端配置界面。
     */
    public static void open(EntityPlayer player, MTECrustMatterAggregator aggregator) {
        INSTANCE.openGui(player, aggregator);
    }
}
