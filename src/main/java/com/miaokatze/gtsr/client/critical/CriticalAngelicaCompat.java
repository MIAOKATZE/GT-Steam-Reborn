package com.miaokatze.gtsr.client.critical;

import net.minecraft.block.Block;

import com.miaokatze.gtsr.common.critical.BlockCriticalRenderAnchor;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A bounded exception for Critical meshes, without changing any global renderer option. */
@SideOnly(Side.CLIENT)
public final class CriticalAngelicaCompat {

    private static final ThreadLocal<Boolean> CAPTURE = new ThreadLocal<>();

    private CriticalAngelicaCompat() {}

    public static void register() {
        try {
            Class<?> registry = Class.forName("com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry");
            registry.getMethod("registerDynamicClass", String.class)
                .invoke(null, TileEntityCriticalRenderAnchor.class.getName());
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            // Optional Angelica, including older versions without the bounds registry.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot register Critical's dynamic Angelica render bounds", failure);
        }
    }

    public static Boolean beginCapture(Block block) {
        Boolean previous = CAPTURE.get();
        CAPTURE.set(block instanceof CriticalMaterials.MaterialBlock || block instanceof BlockCriticalRenderAnchor);
        return previous;
    }

    public static boolean isCapturing() {
        return Boolean.TRUE.equals(CAPTURE.get());
    }

    public static void endCapture(Boolean previous) {
        if (previous == null) CAPTURE.remove();
        else CAPTURE.set(previous);
    }
}
