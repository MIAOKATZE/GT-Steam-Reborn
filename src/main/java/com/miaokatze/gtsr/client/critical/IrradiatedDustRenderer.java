package com.miaokatze.gtsr.client.critical;

import net.minecraftforge.client.MinecraftForgeClient;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.miaokatzecore.api.itemfx.GlintStyle;
import com.miaokatze.miaokatzecore.client.itemfx.AtlasGlintRenderer;

/** Existing registration and public construction API; rendering is supplied by Core. */
public final class IrradiatedDustRenderer extends AtlasGlintRenderer {

    public IrradiatedDustRenderer() {
        super(new GlintStyle(.9F, .63F, .12F, 1F, .8F, new double[] { .12, -.08 }, new float[] { -50, 10 }));
    }

    public static void register() {
        IrradiatedDustRenderer renderer = new IrradiatedDustRenderer();
        MinecraftForgeClient.registerItemRenderer(
            GTSRItemList.IrradiatedMoonDust.get(1)
                .getItem(),
            renderer);
        MinecraftForgeClient.registerItemRenderer(
            GTSRItemList.IrradiatedStoneDust.get(1)
                .getItem(),
            renderer);
    }
}
