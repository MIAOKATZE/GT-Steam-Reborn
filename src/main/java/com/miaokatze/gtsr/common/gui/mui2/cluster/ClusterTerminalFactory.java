package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Formal, independent handheld-terminal route. */
public final class ClusterTerminalFactory implements UIFactory<ClusterTerminalFactory.ClusterGuiData> {

    public static final String FACTORY_NAME = "gtsr:cluster_terminal";
    public static final String PANEL_NAME = "cluster_terminal";
    private static final ClusterTerminalFactory INSTANCE = new ClusterTerminalFactory();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static boolean registered;

    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            Mui2ServerActions.register();
            registered = true;
        }
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity clicked, int initialPage) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || player instanceof FakePlayer || clicked == null)
            return false;
        MTESteamMineralLogisticsCluster cluster;
        if (clicked.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster controller) {
            cluster = controller;
        } else if (clicked.getMetaTileEntity() instanceof MTEBasicLogisticsUnit unit) {
            cluster = unit.getCluster();
            if (cluster == null || !cluster.getTopology()
                .getLogisticsUnits()
                .contains(unit)) return false;
            initialPage = 1;
        } else return false;
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        if (base == null || base.getWorld() != player.worldObj) return false;
        ClusterGuiData data = new ClusterGuiData(
            player,
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            initialPage,
            EPOCH.incrementAndGet());
        if (data.getTileEntity() != base || !canUse(data)) return false;
        GuiManager.open(INSTANCE, data, serverPlayer);
        return true;
    }

    static MTESteamMineralLogisticsCluster machine(ClusterGuiData data) {
        TileEntity tile = data.getTileEntity();
        return tile != null && !tile.isInvalid()
            && tile instanceof IGregTechTileEntity base
            && !base.isDead()
            && base.canAccessData()
            && base.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster machine ? machine : null;
    }

    static boolean canUse(ClusterGuiData data) {
        EntityPlayer player = data.getPlayer();
        double range = TerminalServerSessions.MAX_INTERACTION_DISTANCE;
        return player != null && !player.isDead
            && !(player instanceof FakePlayer)
            && player.worldObj == data.getWorld()
            && machine(data) != null
            && data.getSquaredDistance(player) <= range * range
            && (data.getWorld().isRemote || Mui2TerminalRoutes.clusterTerminal);
    }

    @Override
    public String getFactoryName() {
        return FACTORY_NAME;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, ClusterGuiData data) {
        return player == data.getPlayer() && canUse(data);
    }

    @Override
    public ModularPanel createPanel(ClusterGuiData data, PanelSyncManager manager, UISettings settings) {
        MTESteamMineralLogisticsCluster machine = machine(data);
        TileEntity tile = data.getTileEntity();
        if (machine == null || !canUse(data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("cluster_terminal_denied");
        }
        settings.canInteractWith(
            player -> player == data.getPlayer() && data.getTileEntity() == tile
                && machine(data) == machine
                && canUse(data));
        return ClusterTerminalPanel.build(data, machine, manager);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(ClusterGuiData data, ModularPanel panel) {
        return new com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen("gtsr", panel, 620, 340);
    }

    @Override
    public void writeGuiData(ClusterGuiData data, PacketBuffer buffer) {
        buffer.writeInt(data.getX());
        buffer.writeInt(data.getY());
        buffer.writeInt(data.getZ());
        buffer.writeByte(data.initialPage);
        buffer.writeLong(data.epoch);
    }

    @Override
    public ClusterGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new ClusterGuiData(
            player,
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readUnsignedByte(),
            buffer.readLong());
    }

    public static final class ClusterGuiData extends PosGuiData {

        public final int initialPage;
        public final long epoch;

        public ClusterGuiData(EntityPlayer player, int x, int y, int z, int initialPage, long epoch) {
            super(player, x, y, z);
            this.initialPage = Math.max(0, Math.min(4, initialPage));
            this.epoch = epoch;
        }
    }
}
