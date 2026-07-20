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
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 钻井枢纽「节点状态管理界面」的 MUI2 工厂。
 * 参照 GT5U CoverUIFactory 模式：作为独立 factory 注册（CommonProxy.init 中
 * GuiManager.registerFactory），与枢纽主 GUI 的 MetaTileEntityGuiHandler 路径互不干扰——
 * 主 GUI 仍是空手普通右击打开，本界面由空手 + 潜行右击触发。
 */
public class HubStatusGuiFactory extends AbstractUIFactory<PosGuiData> {

    public static final HubStatusGuiFactory INSTANCE = new HubStatusGuiFactory();

    /** 与 MetaTileEntityGuiHandler 一致的最大交互距离 */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    private HubStatusGuiFactory() {
        super("gtsr:hub_status");
    }

    /**
     * 服务端调用：为玩家打开指定枢纽的状态管理界面。
     */
    public static void open(EntityPlayer player, MTESingularityDrillingHub hub) {
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
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof MTESingularityDrillingHub hub) {
            return new MTESingularityHubStatusGui(hub);
        }
        throw new IllegalStateException(
            String.format(
                "TileEntity at (%s, %s, %s) is not a Singularity Drilling Hub!",
                data.getX(),
                data.getY(),
                data.getZ()));
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
