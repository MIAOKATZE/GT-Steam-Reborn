package com.miaokatze.gtsr.common.machine.cluster;

import gregtech.api.util.HatchElementBuilder;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** The cluster's fluid/item consumers use ordinary hatch lists, never crafting dual inventories. */
final class ClusterInputHatchPolicy {

    private ClusterInputHatchPolicy() {}

    /** Configure native paths consistently without materializing the predicate-only hatch catalog. */
    static <T> HatchElementBuilder<T> rejectUnsupportedDualInput(HatchElementBuilder<T> builder) {
        var nativeFilter = builder.getHatchItemFilter();
        var nativeAdder = builder.getAdder();
        var nativeSkip = builder.getShouldSkip();
        return builder.hatchItemFilter(
            (controller, trigger) -> nativeFilter.apply(controller, trigger)
                .and(stack -> !(ItemMachines.getMetaTileEntity(stack) instanceof IDualInputHatch)))
            .adder(
                (controller, tile, casingIndex) -> tile != null
                    && !(tile.getMetaTileEntity() instanceof IDualInputHatch)
                    && nativeAdder.apply(controller, tile, casingIndex))
            .shouldSkip(
                (controller, tile) -> tile != null && !(tile.getMetaTileEntity() instanceof IDualInputHatch)
                    && nativeSkip.test(controller, tile));
    }
}
