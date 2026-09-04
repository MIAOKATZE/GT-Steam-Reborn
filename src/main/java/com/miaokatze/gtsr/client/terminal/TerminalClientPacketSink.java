package com.miaokatze.gtsr.client.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;

import com.miaokatze.gtsr.client.gui.terminal.GuiAggregatorConfigScreen;
import com.miaokatze.gtsr.client.gui.terminal.GuiClusterTerminalScreen;
import com.miaokatze.gtsr.client.gui.terminal.GuiSingularityHubStatusScreen;
import com.miaokatze.gtsr.client.gui.terminal.GuiSteamHubStatusScreen;
import com.miaokatze.gtsr.client.gui.terminal.GuiTerminalBase;
import com.miaokatze.gtsr.client.gui.terminal.GuiWaterHubStatusScreen;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.PacketOpenTerminalUi;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 终端 S2C 唯一落地类（terminal-native-ui N19，PLAN §4.1 轨 A / §4.7）。
 * <p>
 * 类级 @SideOnly(CLIENT)：服务端永不加载；common 侧 handler 仅允许
 * 「本类静态方法直调」一种形态引用（GTSRFXNet → GTSRSingularityFX 生产先例）。
 * <ul>
 * <li><b>open 包</b>：func_152344_a 切客户端主线程 → 双校验（玩家 dim 匹配 +
 * pos 处 TE 为目标机器类）不符静默忽略（防伪造/竞态钓鱼，PLAN R3）→
 * 通过后 displayGuiScreen（轨 A 四分支全落地：S3 三枢纽 + S5b CLUSTER_TERMINAL）；</li>
 * <li><b>data 包</b>：切主线程后按 uiType 分派写客户端缓存（S3：HubTerminalClientCache、
 * S4：AggregatorClientCache、S5b：ClusterTerminalClientCache；snapshotVersion 单调门控由缓存持有，
 * 防迟到旧包回写；valid=false 清缓存 + 锚点一致的自关）。</li>
 * </ul>
 * GUI/缓存实例只在本类内构造，不得被 common 直接 new（PLAN §4.7-4）。
 */
@SideOnly(Side.CLIENT)
public final class TerminalClientPacketSink {

    private TerminalClientPacketSink() {}

    /** open 包落地入口（Netty 线程调用）：切主线程后校验（零 lambda 纪律：匿名 Runnable） */
    public static void handleOpenScheduled(final PacketOpenTerminalUi msg) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    TerminalClientPacketSink.handleOpen(msg);
                }
            });
    }

    /** data 包落地入口（Netty 线程调用）：切主线程后写缓存分派 */
    public static void handleDataScheduled(final PacketTerminalData msg) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    TerminalClientPacketSink.handleData(msg);
                }
            });
    }

    // ==================== 主线程处理 ====================

    /**
     * open 包主线程处理：双校验不符静默忽略；通过后打开目标 UI（轨 A，
     * 无服务端 Container、无 windowId）。
     */
    private static void handleOpen(PacketOpenTerminalUi msg) {
        if (msg == null || msg.isCorrupt() || msg.getUiType() == null) {
            return;
        }
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null || world.provider.dimensionId != msg.getDim()) {
            return; // dim 不匹配：静默忽略
        }
        Class<? extends MetaTileEntity> target = TerminalClientPacketSink.resolveTargetMachineClass(msg.getUiType());
        if (target == null) {
            return; // 目标类映射未就位 / AGGREGATOR 不收 open 包：静默忽略
        }
        TileEntity te = world.getTileEntity(msg.getX(), msg.getY(), msg.getZ());
        if (!(te instanceof IGregTechTileEntity)) {
            return; // 非 GT 基 TE：静默忽略
        }
        IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
        if (mte == null || !target.isInstance(mte)) {
            return; // 机器类不符：静默忽略（防钓鱼第二校验）
        }
        // 双校验通过：轨 A 本地打开（无服务端 Container、无 windowId，PLAN §4.1）
        switch (msg.getUiType()) {
            case SINGULARITY_HUB:
                Minecraft.getMinecraft()
                    .displayGuiScreen(
                        new GuiSingularityHubStatusScreen(msg.getX(), msg.getY(), msg.getZ(), msg.getDim()));
                return;
            case STEAM_HUB:
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiSteamHubStatusScreen(msg.getX(), msg.getY(), msg.getZ(), msg.getDim()));
                return;
            case WATER_HUB:
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiWaterHubStatusScreen(msg.getX(), msg.getY(), msg.getZ(), msg.getDim()));
                return;
            case CLUSTER_TERMINAL:
                // S5b：initialPage 随 open 包传输（1=链路页，物流模块兼容入口；M6）
                Minecraft.getMinecraft()
                    .displayGuiScreen(
                        new GuiClusterTerminalScreen(
                            msg.getX(),
                            msg.getY(),
                            msg.getZ(),
                            msg.getDim(),
                            msg.getInitialPage()));
                return;
            default:
                return; // AGGREGATOR 不收 open 包
        }
    }

    /**
     * data 包主线程处理：锚点 dim 校验后按 uiType 分派写客户端缓存。
     * valid=false = 服务端复核失败：S3-S5 接入缓存后清空对应缓存并
     * displayGuiScreen(null) 自关（等价旧轨 canInteractWith 64 格语义）；
     * snapshotVersion 单调门控（仅接受版本 ≥ 已缓存回包）由各缓存实现。
     */
    private static void handleData(PacketTerminalData msg) {
        if (msg == null || msg.isCorrupt() || msg.getUiType() == null) {
            return;
        }
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null || world.provider.dimensionId != msg.getDim()) {
            return; // 锚点 dim 不匹配：静默忽略
        }
        switch (msg.getUiType()) {
            case SINGULARITY_HUB:
            case STEAM_HUB:
            case WATER_HUB:
                if (!msg.isValid()) {
                    // 服务端复核失败（TE 失活/超 64 格/机器类不符）：清对应仓 + 自关（等价旧 canInteractWith）
                    HubTerminalClientCache.invalidate(msg);
                    closeIfAnchored(msg);
                    return;
                }
                HubTerminalClientCache.accept(msg); // 整体替换写缓存（内部单调门控 + 防撕裂）
                return;
            case CLUSTER_TERMINAL:
                // S5b：写 ClusterTerminalClientCache（29 通道增量命中键分型存取，仅覆盖命中键）
                if (!msg.isValid()) {
                    // 服务端复核失败（TE 失活/超 64 格/机器类不符）：清缓存 + 锚点自关（等价 canInteractWith）
                    ClusterTerminalClientCache.invalidate(msg);
                    closeIfAnchored(msg);
                    return;
                }
                ClusterTerminalClientCache.accept(msg);
                return;
            case AGGREGATOR:
                // S4：写 AggregatorClientCache（8 标量 + oreList；AGGREGATOR 不在 open 分派——
                // 它由 openGui 双端打开，gui 构造在 ClientProxy 委托，PLAN §4.1 轨 B）
                if (!msg.isValid()) {
                    // 服务端复核失败（TE 失活/超 64 格/机器类不符）：清缓存 + 锚点自关（等价 canInteractWith）
                    AggregatorClientCache.invalidate(msg);
                    closeIfAnchored(msg);
                    return;
                }
                AggregatorClientCache.accept(msg);
                return;
            default:
                return;
        }
    }

    /**
     * valid=false 自关（等价旧 canInteractWith 64 格语义）：仅当当前打开的终端 GUI 锚点与回包一致。
     * 轨 A = GuiTerminalBase（纯展示，displayGuiScreen(null)）；轨 B 聚合器 = GuiContainer，
     * 先 displayGuiScreen(null)（触发 GuiContainer.onGuiClosed→Container.onContainerClosed）
     * 再 player.closeScreen()（EntityClientPlayerMP 覆写：发 C0D 关窗包 + openContainer 复位，
     * 与 vanilla GuiContainer.keyTyped 关闭路径一致）。服务端超距亦由 canInteractWith
     * tick 复核自动关窗（双保险）。
     */
    private static void closeIfAnchored(PacketTerminalData msg) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof GuiTerminalBase terminal
            && terminal.isAnchoredAt(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
        } else if (current instanceof GuiAggregatorConfigScreen aggregator
            && aggregator.isAnchoredAt(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) {
                Minecraft.getMinecraft()
                    .displayGuiScreen(null);
                Minecraft.getMinecraft().thePlayer.closeScreen();
            }
    }

    /**
     * uiType → 目标机器类映射（客户端双校验第二校验）。S3-S5 全分支已落地；
     * AGGREGATOR 恒 null：聚合器由 openGui 双端打开，不收 open 包（PLAN §4.1 轨 B）。
     */
    private static Class<? extends MetaTileEntity> resolveTargetMachineClass(TerminalUiType uiType) {
        switch (uiType) {
            case SINGULARITY_HUB:
                return MTESingularityDrillingHub.class;
            case STEAM_HUB:
                return MTESteamHubArray.class;
            case WATER_HUB:
                return MTEWaterHubArray.class;
            case CLUSTER_TERMINAL:
                return MTESteamMineralLogisticsCluster.class;
            default:
                return null; // AGGREGATOR 恒 null（openGui 轨 B）
        }
    }
}
