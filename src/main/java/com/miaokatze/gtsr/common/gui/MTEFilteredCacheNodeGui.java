// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.gui;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.singleblock.base.MTEDigitalTankBaseGui;

/** Cache-node GUI for GTNH 2.9.0–2.9.2; both upstream hooks share the same filtered slot. */
public class MTEFilteredCacheNodeGui extends MTEDigitalTankBaseGui<MTEFilteredCacheNode> {

    public MTEFilteredCacheNodeGui(MTEFilteredCacheNode machine) {
        super(machine);
    }

    @Override
    protected FluidSlot createSlot(ModularPanel panel, PanelSyncManager syncManager) {
        return createFilteredSlot();
    }

    // GT .265 dispatches this exact descriptor; it no longer exists in the .277 superclass.
    protected FluidSlot createFluidSlot(ModularPanel panel, PanelSyncManager syncManager, IFluidTank fluidTank) {
        return createFilteredSlot();
    }

    private FluidSlot createFilteredSlot() {
        // 重建 FluidSlotSyncHandler 以保留 setLockIfEmpty 回调，
        // 同时重新施加流体过滤，避免非目标流体通过拖动流体单元注入。
        FluidSlotSyncHandler fluidSlotSH = new FluidSlotSyncHandler(new IFluidTank() {

            @Override
            public FluidStack getFluid() {
                return machine.getFillableStack();
            }

            @Override
            public int getFluidAmount() {
                return machine.getFluidAmount();
            }

            @Override
            public int getCapacity() {
                return machine.getRealCapacity();
            }

            @Override
            public FluidTankInfo getInfo() {
                return new FluidTankInfo(getFluid(), getCapacity());
            }

            @Override
            public int fill(FluidStack stack, boolean execute) {
                return machine.fill(stack, execute);
            }

            @Override
            public FluidStack drain(int amount, boolean execute) {
                return machine.drain(amount, execute);
            }
        });
        fluidSlotSH.setChangeListener(machine::setLockIfEmpty);
        fluidSlotSH.filter(machine::isFluidInputAllowed);

        return new FluidSlot().syncHandler(fluidSlotSH)
            .bottomRel(0)
            .rightRel(0)
            .background(GTGuiTextures.SLOT_FLUID_TANK);
    }
}
