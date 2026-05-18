package com.miaokatze.gtsr.register;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * 材质注册管理器
 * 统一管理模组内的所有材质资源，提供材质缓存、自定义图标定义以及资源路径创建功能。
 */
public class TextureManager {

    public static final IIconContainer TEX_TEST_EV = new Textures.BlockIcons.CustomIcon("gtsr:MTETEST_1");
    public static final IIconContainer TEX_TEST_IV = new Textures.BlockIcons.CustomIcon("gtsr:MTETEST_2");
    public static final IIconContainer TEX_TEST_LUV = new Textures.BlockIcons.CustomIcon("gtsr:MTETEST_3");

    // v0.3.0 奇点节点机器自定义材质 (32x32 高分辨率)
    public static final IIconContainer TEX_SINGULARITY_MINER_OFF = new Textures.BlockIcons.CustomIcon(
        "gtsr:MTESingularityMinerNode_OFF");
    public static final IIconContainer TEX_SINGULARITY_MINER_ON = new Textures.BlockIcons.CustomIcon(
        "gtsr:MTESingularityMinerNode_ON");
    public static final IIconContainer TEX_SINGULARITY_DRILLING_OFF = new Textures.BlockIcons.CustomIcon(
        "gtsr:MTESingularityDrillingNode_OFF");
    public static final IIconContainer TEX_SINGULARITY_DRILLING_ON = new Textures.BlockIcons.CustomIcon(
        "gtsr:MTESingularityDrillingNode_ON");

    private static final Map<String, ITexture> textureCache = new HashMap<>();

    public static ITexture getOrCreateTexture(String name, Textures.BlockIcons icon) {
        return textureCache.computeIfAbsent(name, k -> TextureFactory.of(icon));
    }

    public static ITexture getTexture(String name) {
        return textureCache.get(name);
    }

    public static void registerTexture(String name, ITexture texture) {
        textureCache.put(name, texture);
    }

    public static ResourceLocation createResourceLocation(String path) {
        return new ResourceLocation("gtsr", path);
    }

    public static void clearCache() {
        textureCache.clear();
    }
}
