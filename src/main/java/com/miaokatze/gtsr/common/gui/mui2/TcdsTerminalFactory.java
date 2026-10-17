package com.miaokatze.gtsr.common.gui.mui2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
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
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Handheld terminal route; intentionally independent of GT's normal machine GUI switch. */
public final class TcdsTerminalFactory implements UIFactory<PosGuiData> {

    public static final String FACTORY_NAME = "gtsr:tcds_terminal";
    public static final String PANEL_NAME = "tcds_terminal";
    private static final TcdsTerminalFactory INSTANCE = new TcdsTerminalFactory();
    private static boolean registered;

    private TcdsTerminalFactory() {}

    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            Mui2ServerActions.register();
            registered = true;
        }
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || base == null || base.getWorld() != player.worldObj)
            return false;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (data.getTileEntity() != base || !INSTANCE.canInteractWith(player, data)) return false;
        GuiManager.open(INSTANCE, data, serverPlayer);
        return true;
    }

    static MTEThermoChemicalDenseSteamGenerator machine(PosGuiData data) {
        TileEntity tile = data.getTileEntity();
        return tile != null && !tile.isInvalid()
            && tile instanceof IGregTechTileEntity base
            && base.canAccessData()
            && base.getMetaTileEntity() instanceof MTEThermoChemicalDenseSteamGenerator machine ? machine : null;
    }

    @Override
    public String getFactoryName() {
        return FACTORY_NAME;
    }

    static boolean canUse(PosGuiData data) {
        return INSTANCE.canInteractWith(data.getPlayer(), data);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        ItemStack held = player.getHeldItem();
        double range = TerminalServerSessions.MAX_INTERACTION_DISTANCE;
        return (data.getWorld().isRemote || Mui2TerminalRoutes.tcdsTerminal) && player == data.getPlayer()
            && !player.isDead
            && !(player instanceof FakePlayer)
            && player.worldObj == data.getWorld()
            && data.getSquaredDistance(player) <= range * range
            && machine(data) != null
            && (data.getWorld().isRemote || held != null && held.getItem() instanceof HubTerminal);
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        MTEThermoChemicalDenseSteamGenerator machine = machine(data);
        TileEntity tile = data.getTileEntity();
        if (machine == null || !canInteractWith(data.getPlayer(), data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("tcds_terminal_denied");
        }
        settings.canInteractWith(
            player -> player.worldObj == tile.getWorldObj() && data.getTileEntity() == tile
                && machine(data) == machine
                && canInteractWith(player, data));
        return TcdsTerminalPanel.build(data, machine, manager);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 400, 286);
    }

    @Override
    public void writeGuiData(PosGuiData data, PacketBuffer buffer) {
        buffer.writeVarIntToBuffer(data.getX());
        buffer.writeVarIntToBuffer(data.getY());
        buffer.writeVarIntToBuffer(data.getZ());
    }

    @Override
    public PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(
            player,
            buffer.readVarIntFromBuffer(),
            buffer.readVarIntFromBuffer(),
            buffer.readVarIntFromBuffer());
    }
}
