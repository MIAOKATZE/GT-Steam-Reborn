package com.miaokatze.gtsr.common.gui;

import java.util.function.Predicate;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;

import gregtech.common.gui.modularui.singleblock.base.MTEDigitalTankBaseGui;

/**
 * 缓存节点专用 GUI。
 *
 * <p>
 * 父类 {@link MTEDigitalTankBaseGui#createFluidSlot} 在调用 super 后，
 * 会用一个全新的 {@link FluidSlotSyncHandler} 替换掉原本已经带过滤的同步器，
 * 导致 GUI 流体槽不再尊重 {@link MTEFilteredCacheNode#isFluidInputAllowed}。
 * 玩家因此可以在打开 GUI 后，通过拖动流体单元把非目标流体（如蒸馏水）注入蒸汽缓存节点。
 * </p>
 *
 * <p>
 * 此类在替换同步器时重新施加
 * {@link MTEFilteredCacheNode#isFluidInputAllowed(net.minecraftforge.fluids.FluidStack)}，
 * 使流体槽仅接受该节点允许的目标流体（蒸汽/过热蒸汽/水等）。
 * </p>
 */
public class MTEFilteredCacheNodeGui extends MTEDigitalTankBaseGui<MTEFilteredCacheNode> {

    private static final Logger LOGGER = LogManager.getLogger("gtsr");

    public MTEFilteredCacheNodeGui(MTEFilteredCacheNode machine) {
        super(machine);
    }

    @Override
    protected FluidSlot createFluidSlot(ModularPanel panel, PanelSyncManager syncManager, IFluidTank fluidTank) {
        // 先获取父类已经设置好样式（位置、背景）的 FluidSlot
        FluidSlot slot = super.createFluidSlot(panel, syncManager, fluidTank);

        // 重建 FluidSlotSyncHandler 以保留 setLockIfEmpty 回调，
        // 同时重新施加流体过滤，避免非目标流体通过拖动流体单元注入。
        FluidSlotSyncHandler fluidSlotSH = new FluidSlotSyncHandler(fluidTank);
        fluidSlotSH.setChangeListener(machine::setLockIfEmpty);
        // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API。
        // MUI2 2.3.70（beta-1）无 FluidSlotSyncHandler#filter（beta-2 2.3.79+ 引入）；
        // 反射探测调用，beta-1 下降级为不过滤（流体槽接受拖入任意流体，GUI 仍可正常打开）。
        try {
            FluidSlotSyncHandler.class.getMethod("filter", Predicate.class)
                .invoke(fluidSlotSH, (Predicate<FluidStack>) machine::isFluidInputAllowed);
        } catch (ReflectiveOperationException e) {
            LOGGER
                .warn("MUI2 FluidSlotSyncHandler#filter unavailable; fluid slot filter disabled for cache node GUI", e);
        }

        return slot.syncHandler(fluidSlotSH);
    }
}
