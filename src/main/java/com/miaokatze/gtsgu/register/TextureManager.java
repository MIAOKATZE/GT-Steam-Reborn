package com.miaokatze.gtsgu.register;

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

    public static final IIconContainer TEX_TEST_EV = new Textures.BlockIcons.CustomIcon("gtsgu:MTETEST_1");
    public static final IIconContainer TEX_TEST_IV = new Textures.BlockIcons.CustomIcon("gtsgu:MTETEST_2");
    public static final IIconContainer TEX_TEST_LUV = new Textures.BlockIcons.CustomIcon("gtsgu:MTETEST_3");

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
        return new ResourceLocation("gtsgu", path);
    }

    public static void clearCache() {
        textureCache.clear();
    }
}
