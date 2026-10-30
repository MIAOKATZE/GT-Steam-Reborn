package com.miaokatze.gtsr.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** A distinct, synced native MUI2 route for the handheld terminal, independent of GT's controller GUI. */
public final class CriticalMachineTerminalFactory implements UIFactory<PosGuiData> {

    private static final CriticalMachineTerminalFactory INSTANCE = new CriticalMachineTerminalFactory();
    private static boolean registered;

    private CriticalMachineTerminalFactory() {}

    /** Called while the common HubTerminal item is constructed on both physical sides. */
    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            registered = true;
        }
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || base == null || base.getWorld() != player.worldObj)
            return false;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (data.getTileEntity() != base || !INSTANCE.canInteractWith(player, data) || !hasTerminal(player))
            return false;
        Object previousContainer = player.openContainer;
        GuiManager.open(INSTANCE, data, serverPlayer);
        com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.recordOpened(player, base, previousContainer);
        return true;
    }

    private static boolean hasTerminal(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof HubTerminal;
    }

    private static MTECriticalMachine machine(PosGuiData data) {
        TileEntity tile = data.getTileEntity();
        return tile != null && !tile.isInvalid()
            && tile instanceof IGregTechTileEntity base
            && base.getMetaTileEntity() instanceof MTECriticalMachine machine ? machine : null;
    }

    @Override
    public String getFactoryName() {
        return "gtsr:critical_terminal";
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        if (!TerminalServerSessions.canInspectLocalPosition(player, data, 64)) return false;
        MTECriticalMachine machine = machine(data);
        return player == data.getPlayer() && !player.isDead
            && data.getSquaredDistance(player) <= 64
            && machine != null
            && machine.getCriticalState()
                .canUse(player);
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // MUI2 also accepts client-requested open packets: validate coordinates before any tile lookup.
        if (!canInteractWith(data.getPlayer(), data)
            || !data.getWorld().isRemote && !hasTerminal(data.getPlayer())) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("critical_terminal_denied");
        }
        MTECriticalMachine machine = machine(data);
        settings.canInteractWith(player -> machine(data) == machine && canInteractWith(player, data));
        return new MTECriticalMachineTerminalGui(machine).build(data, manager, settings);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 475, 350, 5);
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
