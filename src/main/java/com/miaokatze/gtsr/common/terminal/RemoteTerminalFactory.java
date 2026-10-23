package com.miaokatze.gtsr.common.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.gui.MTECriticalMachineTerminalGui;
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.gui.mui2.TcdsTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterTerminalFactory.ClusterGuiData;
import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.entanglement.EntanglementTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalFactory;
import com.miaokatze.gtsr.common.gui.mui2.hub.HubTerminalPanel;
import com.miaokatze.gtsr.common.gui.mui2.inventory.AggregatorConfigPanel;
import com.miaokatze.gtsr.common.gui.mui2.worker.RemoteWorkerNodePanel;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** A server-issued route to the same dedicated panels and real authoritative inventories/actions. */
public final class RemoteTerminalFactory implements UIFactory<ClusterGuiData> {

    private static final RemoteTerminalFactory INSTANCE = new RemoteTerminalFactory();
    private static boolean registered;

    private RemoteTerminalFactory() {}

    public static void register() {
        if (registered) return;
        registered = true;
        GuiManager.registerFactory(INSTANCE);
    }

    static boolean open(EntityPlayerMP player, MachineTerminalHistory.Entry entry) {
        if (!"ready".equals(MachineTerminalHistory.status(player, entry))) return false;
        IGregTechTileEntity base = MachineTerminalHistory.resolve(entry);
        MachineTerminalHistory.Grant grant = new MachineTerminalHistory.Grant(player, entry, base);
        ClusterGuiData data = new ClusterGuiData(
            new RemoteTerminalGuiData(player, entry.x, entry.y, entry.z, entry.dimension, entry.kind, grant));
        if (!data.canUseRemote(player)) return false;
        Object previous = player.openContainer;
        GuiManager.open(INSTANCE, data, player);
        MachineTerminalHistory.recordOpened(player, base, previous);
        return player.openContainer != previous;
    }

    @Override
    public String getFactoryName() {
        return "gtsr:remote_machine_terminal";
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, ClusterGuiData data) {
        return data.canUseRemote(player);
    }

    @Override
    public ModularPanel createPanel(ClusterGuiData data, PanelSyncManager manager, UISettings settings) {
        if (!canInteractWith(data.getPlayer(), data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("remote_terminal_denied");
        }
        settings.canInteractWith(player -> data.canUseRemote(player));
        settings.customContainer(com.miaokatze.gtsr.common.gui.mui2.inventory.GuardedModularContainer::new);
        IMetaTileEntity machine = data.getTileEntity() instanceof IGregTechTileEntity base ? base.getMetaTileEntity()
            : null;
        return switch (data.terminalKind) {
            case CRITICAL -> new MTECriticalMachineTerminalGui((MTECriticalMachine) machine)
                .build(data, manager, settings);
            case TCDS -> TcdsTerminalPanel.build(data, (MTEThermoChemicalDenseSteamGenerator) machine, manager);
            case CLUSTER -> ClusterTerminalPanel.build(data, (MTESteamMineralLogisticsCluster) machine, manager);
            case AGGREGATOR -> AggregatorConfigPanel.build(data, (MTECrustMatterAggregator) machine, manager, settings);
            case ENTANGLEMENT -> EntanglementTerminalPanel.build(data, (MTESingularityMachineBase) machine, manager);
            case WORKER -> RemoteWorkerNodePanel.build((MTERemoteWorkerNode) machine, data, manager, settings);
            default -> HubTerminalPanel
                .build(HubTerminalFactory.forType(hubType(data.terminalKind)), data, machine, manager);
        };
    }

    private static TerminalUiType hubType(MachineTerminalHistory.Kind kind) {
        return switch (kind) {
            case STEAM_HUB -> TerminalUiType.STEAM_HUB;
            case WATER_HUB -> TerminalUiType.WATER_HUB;
            case DRILLING_HUB -> TerminalUiType.SINGULARITY_HUB;
            case CACHE_NODE -> TerminalUiType.CACHE_NODE;
            default -> throw new IllegalArgumentException("Not a hub: " + kind);
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(ClusterGuiData data, ModularPanel panel) {
        return switch (data.terminalKind) {
            case CRITICAL, AGGREGATOR -> new BoundedTerminalScreen("gtsr", panel, 475, 350, 5);
            case CLUSTER, ENTANGLEMENT -> new BoundedTerminalScreen("gtsr", panel, 620, 340);
            case WORKER -> new BoundedTerminalScreen("gtsr", panel, 240, 274);
            default -> new BoundedTerminalScreen("gtsr", panel, 400, 240);
        };
    }

    @Override
    public void writeGuiData(ClusterGuiData data, PacketBuffer buffer) {
        buffer.writeInt(data.getX());
        buffer.writeInt(data.getY());
        buffer.writeInt(data.getZ());
        buffer.writeInt(data.targetDimension);
        buffer.writeByte(data.terminalKind.ordinal());
        buffer.writeLong(data.epoch);
    }

    @Override
    public ClusterGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        int x = buffer.readInt(), y = buffer.readInt(), z = buffer.readInt(), dimension = buffer.readInt();
        int kind = buffer.readUnsignedByte();
        long epoch = buffer.readLong();
        if (kind >= MachineTerminalHistory.Kind.values().length || buffer.isReadable())
            throw new IllegalArgumentException("Invalid remote terminal descriptor");
        // A C2S UIFactory open packet receives no grant and cannot bind server slots/actions.
        return new ClusterGuiData(
            new RemoteTerminalGuiData(player, x, y, z, dimension, MachineTerminalHistory.Kind.values()[kind], null),
            epoch);
    }
}
