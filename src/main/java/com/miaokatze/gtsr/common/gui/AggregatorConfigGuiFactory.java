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
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 地壳物质聚合器「终端配置界面」的 MUI2 工厂。
 * 参照 HubStatusGuiFactory：作为独立 factory 注册（CommonProxy.init 中 GuiManager.registerFactory），
 * 与聚合器主 GUI 的 MetaTileEntityGuiHandler 路径互不干扰——主 GUI 仍是空手普通右击打开，
 * 本界面由手持枢纽终端右击触发（MTECrustMatterAggregator.onRightclick → openConfigGui）。
 */
public class AggregatorConfigGuiFactory extends AbstractUIFactory<PosGuiData> {

    public static final AggregatorConfigGuiFactory INSTANCE = new AggregatorConfigGuiFactory();

    /** 与 MetaTileEntityGuiHandler 一致的最大交互距离 */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    private AggregatorConfigGuiFactory() {
        super("gtsr:aggregator_config");
    }

    /**
     * 服务端调用：为玩家打开指定聚合器的终端配置界面。
     */
    public static void open(EntityPlayer player, MTECrustMatterAggregator aggregator) {
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        if (player instanceof FakePlayer) return;
        IGregTechTileEntity base = aggregator.getBaseMetaTileEntity();
        if (base == null) return;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        GuiManager.open(INSTANCE, data, playerMP);
    }

    @Override
    public @Nonnull IGuiHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te instanceof IGregTechTileEntity gte
            && gte.getMetaTileEntity() instanceof MTECrustMatterAggregator aggregator) {
            return new MTECrustMatterAggregatorConfigGui(aggregator);
        }
        throw new IllegalStateException(
            String.format(
                "TileEntity at (%s, %s, %s) is not a Crust Matter Aggregator!",
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
