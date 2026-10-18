package com.miaokatze.gtsr.common.gui.mui2.inventory;

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
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class AggregatorConfigFactory implements UIFactory<PosGuiData> {

    public static final String FACTORY_NAME = "gtsr:aggregator_config";
    public static final String PANEL_NAME = "aggregator_config";
    private static final AggregatorConfigFactory INSTANCE = new AggregatorConfigFactory();
    private static boolean registered;

    private AggregatorConfigFactory() {}

    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            Mui2ServerActions.register();
            registered = true;
        }
    }

    public static boolean open(EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof EntityPlayerMP server) || base == null || player.worldObj != base.getWorld())
            return false;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (data.getTileEntity() != base || !INSTANCE.canInteractWith(player, data)) return false;
        GuiManager.open(INSTANCE, data, server);
        return true;
    }

    static MTECrustMatterAggregator machine(PosGuiData data) {
        return data.getTileEntity() instanceof IGregTechTileEntity base && base.canAccessData()
            && !data.getTileEntity()
                .isInvalid()
            && base.getMetaTileEntity() instanceof MTECrustMatterAggregator machine ? machine : null;
    }

    static boolean canUse(PosGuiData data) {
        return INSTANCE.canInteractWith(data.getPlayer(), data);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        return (data.getWorld().isRemote || Mui2TerminalRoutes.aggregatorConfig) && player == data.getPlayer()
            && player.isEntityAlive()
            && !(player instanceof FakePlayer)
            && player.worldObj == data.getWorld()
            && data.getSquaredDistance(player) <= 64
            && machine(data) != null;
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        // Check the submitted position/range before resolving a tile for a forged open request.
        if (!canInteractWith(data.getPlayer(), data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("aggregator_config_denied");
        }
        MTECrustMatterAggregator machine = machine(data);
        TileEntity tile = data.getTileEntity();
        if (machine == null) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("aggregator_config_denied");
        }
        settings.canInteractWith(
            player -> data.getTileEntity() == tile && machine(data) == machine && canInteractWith(player, data));
        return AggregatorConfigPanel.build(data, machine, manager, settings);
    }

    @Override
    public String getFactoryName() {
        return FACTORY_NAME;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 475, 350, 5);
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
