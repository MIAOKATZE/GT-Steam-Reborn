package com.miaokatze.gtsr.common.gui.mui2.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class UnsealedChestFactory implements UIFactory<PosGuiData> {

    public static final String FACTORY_NAME = "gtsr:unsealed_chest";
    public static final String PANEL_NAME = "unsealed_chest";
    private static final UnsealedChestFactory INSTANCE = new UnsealedChestFactory();
    private static boolean registered;

    private UnsealedChestFactory() {}

    public static void register() {
        if (!registered) {
            GuiManager.registerFactory(INSTANCE);
            Mui2ServerActions.register();
            registered = true;
        }
    }

    public static boolean open(EntityPlayer player, TileEntityUnsealedChest chest) {
        if (!(player instanceof EntityPlayerMP server) || chest == null || chest.getWorldObj() != player.worldObj)
            return false;
        PosGuiData data = new PosGuiData(player, chest.xCoord, chest.yCoord, chest.zCoord);
        if (data.getTileEntity() != chest || !INSTANCE.canOpen(data)) return false;
        GuiManager.open(INSTANCE, data, server);
        return true;
    }

    private boolean canOpen(PosGuiData data) {
        return canInteractWith(data.getPlayer(), data) && !data.getWorld()
            .isSideSolid(data.getX(), data.getY() + 1, data.getZ(), ForgeDirection.DOWN);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        return (data.getWorld().isRemote || Mui2TerminalRoutes.unsealedChest) && player == data.getPlayer()
            && player.isEntityAlive()
            && !(player instanceof FakePlayer)
            && player.worldObj == data.getWorld()
            && data.getSquaredDistance(player) <= 64
            && data.getTileEntity() instanceof TileEntityUnsealedChest chest
            && !chest.isInvalid()
            && chest.isUseableByPlayer(player);
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        if (!canOpen(data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("unsealed_chest_denied");
        }
        TileEntityUnsealedChest chest = (TileEntityUnsealedChest) data.getTileEntity();
        settings.canInteractWith(player -> data.getTileEntity() == chest && canInteractWith(player, data));
        return UnsealedChestPanel.build(data, chest, manager, settings);
    }

    @Override
    public String getFactoryName() {
        return FACTORY_NAME;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new ModularScreen("gtsr", panel);
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
