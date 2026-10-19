package com.miaokatze.gtsr.common.machine.cluster;

import com.miaokatze.gtsr.api.compat.HatchElementBuilderCompat;

import gregtech.api.util.HatchElementBuilder;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.tileentities.machines.IDualInputHatch;

/** The cluster's fluid/item consumers use ordinary hatch lists, never crafting dual inventories. */
final class ClusterInputHatchPolicy {

    private ClusterInputHatchPolicy() {}

    /** Configure native paths consistently without materializing the predicate-only hatch catalog. */
    static <T> HatchElementBuilder<T> rejectUnsupportedDualInput(HatchElementBuilder<T> builder) {
        var nativeAdder = HatchElementBuilderCompat.adder(builder);
        var nativeSkip = HatchElementBuilderCompat.shouldSkip(builder);
        return builder
            .hatchItemFilterAnd(
                controller -> stack -> !(ItemMachines.getMetaTileEntity(stack) instanceof IDualInputHatch))
            .adder(
                (controller, tile, casingIndex) -> tile != null
                    && !(tile.getMetaTileEntity() instanceof IDualInputHatch)
                    && nativeAdder.apply(controller, tile, casingIndex))
            .shouldSkip(
                (controller, tile) -> tile != null && !(tile.getMetaTileEntity() instanceof IDualInputHatch)
                    && nativeSkip.test(controller, tile));
    }
}
