package com.miaokatze.gtsr.common.crossmod.ae2;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.machine.base.IHubArray;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 枢纽阵列（蒸汽/蓄水）控制器 → AE2 外部存储接入处理器（GT5U MTEHatchTFFT.AE2TFFTHatchHandler 同构，
 * kekztech MTEHatchTFFT.java:38-56）。
 *
 * canHandle：流体通道 + BaseMetaTileEntity 且其 metaTileEntity 实现了 IHubArray。
 * 注册入口在 {@code CommonProxy#postInit}；AE2U ExternalStorageRegistry 保证自定义处理器先于内置回退
 * （ExternalStorageRegistry.java:45-49 / 61-65），IAEStackType 变体的默认方法逐级委托到本类的
 * StorageChannel 变体（IExternalStorageHandler.java:49-57 / 82-90）。
 */
public class GTSRAE2ExternalStorageHandler implements IExternalStorageHandler {

    @Override
    public boolean canHandle(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource mySrc) {
        return channel == StorageChannel.FLUIDS && te instanceof BaseMetaTileEntity baseMetaTileEntity
            && baseMetaTileEntity.getMetaTileEntity() instanceof IHubArray;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public IMEInventory getInventory(TileEntity te, ForgeDirection d, StorageChannel channel, BaseActionSource src) {
        if (channel == StorageChannel.FLUIDS && te instanceof BaseMetaTileEntity baseMetaTileEntity
            && baseMetaTileEntity.getMetaTileEntity() instanceof IHubArray) {
            return new HubArrayMEInventory(baseMetaTileEntity);
        }
        return null;
    }
}
