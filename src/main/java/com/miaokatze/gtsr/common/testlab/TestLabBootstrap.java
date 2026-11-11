// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.testlab;

import java.lang.reflect.InvocationTargetException;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/** Production entry point: disabled means no test implementation is resolved or initialized. */
public final class TestLabBootstrap {

    private static final String SUPPORT = "com.miaokatze.gtsr.common.dimension.testlab.TestLabSupport";
    private static Class<?> support;

    private TestLabBootstrap() {}

    public static void preInit(boolean enabled) {
        if (!enabled) return;
        try {
            support = Class.forName(SUPPORT, true, TestLabBootstrap.class.getClassLoader());
            invoke("preInit", new Class<?>[0]);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(
                "GTSR testlab explicitly enabled but support is absent; replace the normal GTSR and Core jars with their complete -testlab variants (same mod ids; do not install both)",
                missing);
        }
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (support != null) invoke("serverStarting", new Class<?>[] { FMLServerStartingEvent.class }, event);
    }

    private static void invoke(String name, Class<?>[] parameters, Object... args) {
        try {
            support.getMethod(name, parameters)
                .invoke(null, args);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("GTSR testlab initialization failed: " + name, failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Incompatible GTSR testlab support: " + name, failure);
        }
    }
}
