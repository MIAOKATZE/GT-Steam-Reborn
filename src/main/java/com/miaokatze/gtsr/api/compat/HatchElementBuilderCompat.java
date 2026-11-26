// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.api.compat;

import java.util.function.BiPredicate;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;

/** Existing facade over public builder delegates shared by GT5U .265/.275/.277; no version dispatch. */
public final class HatchElementBuilderCompat {

    private HatchElementBuilderCompat() {}

    public static <T> IGTHatchAdder<? super T> adder(HatchElementBuilder<T> builder) {
        return builder.getAdder();
    }

    public static <T> BiPredicate<? super T, ? super IGregTechTileEntity> shouldSkip(HatchElementBuilder<T> builder) {
        return builder.getShouldSkip();
    }
}
