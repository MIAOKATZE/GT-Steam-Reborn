package com.miaokatze.gtsr.common.util;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;

import gregtech.api.metatileentity.implementations.MTEHatch;

/** Constructor-only defaults: updateTexture requires an attached base tile and sends a client update. */
public final class HatchTextureDefaults {

    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final Field[] FIELDS = resolve();

    private HatchTextureDefaults() {}

    public static void set(MTEHatch hatch, int page, int index) {
        if (FIELDS == null) return;
        try {
            FIELDS[0].setInt(hatch, page);
            FIELDS[1].setInt(hatch, index);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            warn(e);
        }
    }

    private static Field[] resolve() {
        try {
            Field page = MTEHatch.class.getDeclaredField("texturePage");
            Field index = MTEHatch.class.getDeclaredField("textureIndex");
            page.setAccessible(true);
            index.setAccessible(true);
            return new Field[] { page, index };
        } catch (ReflectiveOperationException | SecurityException e) {
            warn(e);
            return null;
        }
    }

    private static void warn(Exception error) {
        if (WARNED.compareAndSet(false, true)) {
            LogManager.getLogger("gtsr")
                .warn("[GTSR] Hatch constructor texture defaults unavailable", error);
        }
    }
}
