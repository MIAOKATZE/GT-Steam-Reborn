package com.miaokatze.gtsr.api.compat;

import java.lang.reflect.Field;
import java.util.function.BiPredicate;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.HatchElementBuilder;
import gregtech.api.util.IGTHatchAdder;

/** Preserve the native builder delegates on GT594 as well as GT205; beta-1 has no getters. */
public final class HatchElementBuilderCompat {

    private static final Field ADDER = field("mAdder");
    private static final Field SHOULD_SKIP = field("mShouldSkip");

    private HatchElementBuilderCompat() {}

    private static Field field(String name) {
        try {
            Field field = HatchElementBuilder.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported GT hatch builder: " + name, exception);
        }
    }

    private static Object read(Field field, Object builder) {
        try {
            return field.get(builder);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read GT hatch builder delegate", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> IGTHatchAdder<? super T> adder(HatchElementBuilder<T> builder) {
        return (IGTHatchAdder<? super T>) read(ADDER, builder);
    }

    @SuppressWarnings("unchecked")
    public static <T> BiPredicate<? super T, ? super IGregTechTileEntity> shouldSkip(HatchElementBuilder<T> builder) {
        return (BiPredicate<? super T, ? super IGregTechTileEntity>) read(SHOULD_SKIP, builder);
    }
}
