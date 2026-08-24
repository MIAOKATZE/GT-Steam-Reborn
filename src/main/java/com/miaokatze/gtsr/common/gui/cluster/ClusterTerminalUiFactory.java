package com.miaokatze.gtsr.common.gui.cluster;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtsr.common.gui.AbstractGTSRPosUiFactory;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 集群终端界面的 MUI2 工厂（通用骨架见 AbstractGTSRPosUiFactory）。
 * <p>
 * 主入口：持枢纽终端右击总控打开（{@link #open} 三参入口，按 initialPage 定初始页）。
 * 物流模块右击现打开其自身状态页（{@link MTEBasicLogisticsUnitGui.LogisticsUnitGuiFactory}，
 * 批2 E6 起）；本工厂保留物流模块解析分支作为兼容入口（解析所属 cluster 后固定链路编辑页
 * {@link #PAGE_CHAIN_EDIT}，供其他调用方直达链路编辑）。基类 {@code getGuiHolder} 的 TE 校验
 * 只认总控——物流入口在 open 里先换算成总控坐标再开界面，故 holder 校验无需放宽；服务端守卫
 * （EntityPlayerMP/FakePlayer/基 TE 判空）由基类 openGui 单点承载，{@code setGuiInitialPage}
 * 在委托基类 openGui 前写入（调用方已按服务端侧过滤）。
 */
public final class ClusterTerminalUiFactory extends AbstractGTSRPosUiFactory<MTESteamMineralLogisticsCluster> {

    /** 物流模块入口的固定初始页：链路编辑页（零基页序 1）。 */
    public static final int PAGE_CHAIN_EDIT = 1;

    public static final ClusterTerminalUiFactory INSTANCE = new ClusterTerminalUiFactory();

    private ClusterTerminalUiFactory() {
        super(
            "gtsr.cluster_terminal",
            MTESteamMineralLogisticsCluster.class,
            MTESteamMineralLogisticsClusterGui::new,
            "Steam Mineral Logistics Cluster");
    }

    /**
     * 服务端调用：打开集群终端界面。te 为总控时按 initialPage 定初始页；为物流模块时解析其
     * cluster 并固定初始页 {@link #PAGE_CHAIN_EDIT}；未入集群（cluster null）或总控基 TE 失联
     * （getBaseMetaTileEntity null）时静默返回。
     */
    public static void open(EntityPlayer player, IGregTechTileEntity te, int initialPage) {
        if (te == null) return;
        IMetaTileEntity mte = te.getMetaTileEntity();
        MTESteamMineralLogisticsCluster cluster;
        if (mte instanceof MTESteamMineralLogisticsCluster controller) {
            cluster = controller;
        } else if (mte instanceof MTEBasicLogisticsUnit unit) {
            cluster = unit.getCluster();
            initialPage = PAGE_CHAIN_EDIT;
        } else {
            return;
        }
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        cluster.setGuiInitialPage(initialPage);
        INSTANCE.openGui(player, cluster);
    }
}
