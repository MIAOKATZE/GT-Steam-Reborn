package com.miaokatze.gtsr.common.gui.mui2.entanglement;

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
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Dedicated handheld route for SSE/CESS. Normal GT controller right-click retains its normal GUI. */
public final class EntanglementTerminalFactory implements UIFactory<PosGuiData> {

    public static final String PANEL_NAME = "entanglement_terminal";
    private static final EntanglementTerminalFactory INSTANCE = new EntanglementTerminalFactory();
    private static boolean registered;

    private EntanglementTerminalFactory() {}

    public static void register() {
        if (registered) return;
        GuiManager.registerFactory(INSTANCE);
        Mui2ServerActions.register();
        registered = true;
    }

    public static MTESingularityMachineBase machine(PosGuiData data) {
        TileEntity tile = data.getTileEntity();
        return tile instanceof IGregTechTileEntity base && !tile.isInvalid()
            && base.canAccessData()
            && base.getMetaTileEntity() instanceof MTESingularityMachineBase machine
            && machine.hasEntanglementTerminal() ? machine : null;
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || base == null || base.getWorld() != player.worldObj)
            return false;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (data.getTileEntity() != base || !INSTANCE.canInteractWith(player, data)) return false;
        Object previousContainer = player.openContainer;
        GuiManager.open(INSTANCE, data, serverPlayer);
        com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.recordOpened(player, base, previousContainer);
        return true;
    }

    @Override
    public String getFactoryName() {
        return "gtsr:entanglement_terminal";
    }

    static boolean canUse(PosGuiData data) {
        return INSTANCE.canInteractWith(data.getPlayer(), data);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        if (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data))
            return com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.canUseRemote(player, data);
        if (!TerminalServerSessions.canInspectLocalPosition(player, data, 64 * 64)) return false;
        ItemStack held = player.getHeldItem();
        double range = TerminalServerSessions.MAX_INTERACTION_DISTANCE;
        return player == data.getPlayer() && !player.isDead
            && !(player instanceof FakePlayer)
            && player.worldObj == data.getWorld()
            && data.getSquaredDistance(player) <= range * range
            && machine(data) != null
            && (data.getWorld().isRemote || held != null && held.getItem() instanceof HubTerminal
                && ((IGregTechTileEntity) data.getTileEntity()).isUseableByPlayer(player));
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        if (!canInteractWith(data.getPlayer(), data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("entanglement_denied");
        }
        MTESingularityMachineBase machine = machine(data);
        TileEntity tile = data.getTileEntity();
        settings.canInteractWith(
            player -> data.getTileEntity() == tile && machine(data) == machine && canInteractWith(player, data));
        return EntanglementTerminalPanel.build(data, machine, manager);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 620, 340);
    }

    @Override
    public void writeGuiData(PosGuiData data, PacketBuffer buffer) {
        buffer.writeInt(data.getX());
        buffer.writeInt(data.getY());
        buffer.writeInt(data.getZ());
    }

    @Override
    public PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(player, buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
