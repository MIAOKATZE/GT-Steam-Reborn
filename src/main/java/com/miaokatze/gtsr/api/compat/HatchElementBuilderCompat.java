package com.miaokatze.gtsr.api.compat;

import java.util.function.BiPredicate;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;

/** Source-compatible facade over the RC2 builder's public delegates. */
public final class HatchElementBuilderCompat {

    private HatchElementBuilderCompat() {}

    public static <T> IGTHatchAdder<? super T> adder(HatchElementBuilder<T> builder) {
        return builder.getAdder();
    }

    public static <T> BiPredicate<? super T, ? super IGregTechTileEntity> shouldSkip(HatchElementBuilder<T> builder) {
        return builder.getShouldSkip();
    }
}
