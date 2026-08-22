package com.miaokatze.gtsr.common.gui;

import java.util.function.Function;

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

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * GTSR 位置型 MUI2 工厂通用骨架（SR-O2-06，原四工厂逐行同构收敛）：
 * 构造参数化四个差异点（GUI id、机器类、GUI 构造、异常文案显示名），
 * open() 服务端守卫 / canInteractWith 64 格 / writeGuiData-readGuiData xyz 三连由基类单点承载。
 * 序列化字节流与原四工厂逐位相同，无网络/存档影响；INSTANCE 属各子类保留。
 */
public abstract class AbstractGTSRPosUiFactory<M extends MetaTileEntity> extends AbstractUIFactory<PosGuiData> {

    /** 与 MetaTileEntityGuiHandler 一致的最大交互距离 */
    protected static final int MAX_INTERACTION_DISTANCE = 64;

    private final Class<M> machineType;
    private final Function<M, IGuiHolder<PosGuiData>> guiCtor;
    private final String machineDisplayName;

    protected AbstractGTSRPosUiFactory(String guiId, Class<M> machineType, Function<M, IGuiHolder<PosGuiData>> guiCtor,
        String machineDisplayName) {
        super(guiId);
        this.machineType = machineType;
        this.guiCtor = guiCtor;
        this.machineDisplayName = machineDisplayName;
    }

    /**
     * 服务端调用：为玩家打开该工厂对应的界面（子类静态 open 委托至此，避免与静态入口同名冲突）。
     */
    public final void openGui(EntityPlayer player, M machine) {
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        if (player instanceof FakePlayer) return;
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null) return;
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        GuiManager.open(this, data, playerMP);
    }

    @Override
    public @Nonnull IGuiHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te instanceof IGregTechTileEntity gte && machineType.isInstance(gte.getMetaTileEntity())) {
            return guiCtor.apply(machineType.cast(gte.getMetaTileEntity()));
        }
        throw new IllegalStateException(
            String.format(
                "TileEntity at (%s, %s, %s) is not a %s!",
                data.getX(),
                data.getY(),
                data.getZ(),
                machineDisplayName));
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
