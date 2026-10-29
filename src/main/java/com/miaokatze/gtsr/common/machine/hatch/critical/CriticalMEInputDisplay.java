package com.miaokatze.gtsr.common.machine.hatch.critical;

import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.modularui2.GTGuiTextures;

/** Replaces only the native GUI's read-only stock grid, using public MUI widget APIs before initialization. */
final class CriticalMEInputDisplay {

    private CriticalMEInputDisplay() {}

    private static LongSyncValue[] amounts(PanelSyncManager manager, IntToLongFunction available) {
        LongSyncValue[] values = new LongSyncValue[16];
        for (int i = 0; i < values.length; i++) {
            final int slot = i;
            values[i] = new LongSyncValue(() -> available.applyAsLong(slot));
            manager.syncValue("criticalLongStock" + i, values[i]);
        }
        return values;
    }

    private static void replace(ParentWidget<?> content, Grid stock) {
        // GT205's native content has one filter/centre/stock row. No slots or handlers have been initialized yet.
        ParentWidget<?> row = (ParentWidget<?>) content.getChildren()
            .get(0);
        row.remove(2);
        row.addChild(stock, 2);
    }

    static void items(ParentWidget<?> content, PanelSyncManager manager, IntFunction<ItemStack> config,
        IntToLongFunction available) {
        LongSyncValue[] values = amounts(manager, available);
        IItemHandlerModifiable items = new IItemHandlerModifiable() {

            @Override
            public int getSlots() {
                return 16;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                ItemStack stack = config.apply(slot);
                if (stack == null || values[slot].getLongValue() <= 0) return null;
                stack.stackSize = 1;
                return stack;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return null;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {}
        };
        replace(
            content,
            new Grid().coverChildren()
                .gridOfWidthHeight(4, 4, (x, y, index) -> new ItemSlot() {

                    @Override
                    protected void drawSlotAmountText(int ignored, String format) {
                        GuiDraw.drawStandardSlotAmountText(values[index].getLongValue(), format, getArea());
                    }
                }.slot(
                    new ModularSlot(items, index).accessibility(false, false)
                        .slotGroup("stock_inv"))
                    .backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK)
                    .tooltip(
                        t -> t.addLine(
                            IKey.dynamic(
                                () -> IKey
                                    .lang("gtsr.gui.critical_me.available", Long.toString(values[index].getLongValue()))
                                    .get())))));
    }

    static void fluids(ParentWidget<?> content, PanelSyncManager manager, IntFunction<FluidStack> config,
        IntToLongFunction available) {
        LongSyncValue[] values = amounts(manager, available);
        replace(
            content,
            new Grid().coverChildren()
                .gridOfWidthHeight(4, 4, (x, y, index) -> {
                    IFluidTank tank = new IFluidTank() {

                        @Override
                        public FluidStack getFluid() {
                            FluidStack stack = config.apply(index);
                            if (stack == null || values[index].getLongValue() <= 0) return null;
                            stack.amount = 1;
                            return stack;
                        }

                        @Override
                        public int getFluidAmount() {
                            return getFluid() == null ? 0 : 1;
                        }

                        @Override
                        public int getCapacity() {
                            return 1;
                        }

                        @Override
                        public FluidTankInfo getInfo() {
                            return new FluidTankInfo(this);
                        }

                        @Override
                        public int fill(FluidStack fluid, boolean commit) {
                            return 0;
                        }

                        @Override
                        public FluidStack drain(int amount, boolean commit) {
                            return null;
                        }
                    };
                    return new FluidSlot() {

                        @Override
                        protected boolean displayAmountText() {
                            return false;
                        }

                        @Override
                        public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
                            super.drawOverlay(context, theme);
                            if (getFluidStack() != null)
                                GuiDraw.drawStandardSlotAmountText(values[index].getLongValue(), null, getArea());
                        }

                        @Override
                        protected void addToolTip(RichTooltip tooltip) {
                            FluidStack fluid = getFluidStack();
                            if (fluid == null) tooltip.addLine(IKey.lang("modularui2.fluid.empty"));
                            else {
                                tooltip.addFromFluid(fluid);
                                tooltip.addLine(
                                    IKey.dynamic(
                                        () -> IKey
                                            .lang(
                                                "gtsr.gui.critical_me.available",
                                                Long.toString(values[index].getLongValue()))
                                            .get()));
                                addAdditionalFluidInfo(tooltip, fluid);
                            }
                        }
                    }.syncHandler(new FluidSlotSyncHandler(tank) {

                        @Override
                        protected void tryClickPhantom(MouseData mouse, ItemStack cursor) {}

                        @Override
                        public void tryScrollPhantom(MouseData mouse) {}
                    }.phantom(true)
                        .controlsAmount(false))
                        .backgroundOverlay(GTGuiTextures.SLOT_ITEM_DARK);
                }));
    }
}
