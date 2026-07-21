package com.miaokatze.gtsr.common.gui;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 蓄水枢纽「缓存节点状态管理界面」的 MUI2 工厂。
 * 与钻井枢纽 HubStatusGuiFactory 同模式：独立 factory 注册（CommonProxy.init 中
 * GuiManager.registerFactory），枢纽主 GUI 仍是空手普通右击打开，
 * 本界面由手持枢纽终端右击触发。
 */
public class WaterHubStatusGuiFactory extends AbstractUIFactory<PosGuiData> {

    public static final WaterHubStatusGuiFactory INSTANCE = new WaterHubStatusGuiFactory();

    /** 与 MetaTileEntityGuiHandler 一致的最大交互距离 */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    private WaterHubStatusGuiFactory() {
        super("gtsr:water_hub_status");
    }

    /**
     * 服务端调用：为玩家打开指定蓄水枢纽的状态管理界面。
     */
    public static void open(EntityPlayer player, MTEWaterHubArray hub) {
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        if (player instanceof FakePlayer) return;
        IGregTechTileEntity base = hub.getBaseMetaTileEntity();
        if (base == null) return;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        GuiManager.open(INSTANCE, data, playerMP);
    }

    @Override
    public @Nonnull IGuiHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof MTEWaterHubArray hub) {
            return new MTEWaterHubStatusGui(hub);
        }
        throw new IllegalStateException(
            String
                .format("TileEntity at (%s, %s, %s) is not a Water Hub Array!", data.getX(), data.getY(), data.getZ()));
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData guiData) {
        return super.canInteractWith(player, guiData) && guiData.getTileEntity() instanceof IGregTechTileEntity baseTE
            && baseTE.canAccessData()
            && guiData.getSquaredDistance(player) <= MAX_INTERACTION_DISTANCE;
    }

    @Override
    public void writeGuiData(PosGuiData guiData, PacketBuffer buffer) {
        buffer.writeVarIntToBuffer(guiData.getX());
        buffer.writeVarIntToBuffer(guiData.getY());
        buffer.writeVarIntToBuffer(guiData.getZ());
    }

    @Override
    public @Nonnull PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(
            player,
            buffer.readVarIntFromBuffer(),
            buffer.readVarIntFromBuffer(),
            buffer.readVarIntFromBuffer());
    }
}
