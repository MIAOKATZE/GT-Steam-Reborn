package com.miaokatze.gtsr.register;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtsr.api.compat.GTVersionCompat;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/**
 * 材质注册管理器
 * 统一管理模组内的所有材质资源，提供材质缓存、自定义图标定义以及资源路径创建功能。
 */
public class TextureManager {

    // v0.3.0 奇点节点机器自定义材质 (32x32 高分辨率)
    public static final IIconContainer TEX_SINGULARITY_MINER_OFF = Textures.BlockIcons
        .custom("gtsr:MTESingularityMinerNode_OFF");
    public static final IIconContainer TEX_SINGULARITY_MINER_ON = Textures.BlockIcons
        .custom("gtsr:MTESingularityMinerNode_ON");
    public static final IIconContainer TEX_SINGULARITY_DRILLING_OFF = Textures.BlockIcons
        .custom("gtsr:MTESingularityDrillingNode_OFF");
    public static final IIconContainer TEX_SINGULARITY_DRILLING_ON = Textures.BlockIcons
        .custom("gtsr:MTESingularityDrillingNode_ON");

    private static final Logger LOGGER = LogManager.getLogger("gtsr");

    // v0.4.x 枢纽框架覆盖层材质 (pass1 alpha 渲染, 32x352 动画条)
    public static final IIconContainer HUB_FRAME_RECEIVE;
    public static final IIconContainer HUB_FRAME_SEND;
    public static final IIconContainer HUB_FRAME_UNBOUND;

    static {
        IIconContainer receive;
        IIconContainer send;
        IIconContainer unbound;
        try {
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
            // beta-3 双参 customAlpha(domain,path) 直调；beta-1/beta-2 反射单参 customAlpha(domain+":"+path)
            receive = GTVersionCompat.customAlphaCompat("gtsr", "hub_frame_receive");
            send = GTVersionCompat.customAlphaCompat("gtsr", "hub_frame_send");
            unbound = GTVersionCompat.customAlphaCompat("gtsr", "hub_frame_unbound");
        } catch (Throwable t) {
            // 兼容层自身意外异常：置空进入下方 BlockIcons.VOID 兜底，绝不让静态初始化抛错
            receive = null;
            send = null;
            unbound = null;
        }
        boolean fallbackUsed = false;
        if (receive == null) {
            receive = Textures.BlockIcons.VOID;
            fallbackUsed = true;
        }
        if (send == null) {
            send = Textures.BlockIcons.VOID;
            fallbackUsed = true;
        }
        if (unbound == null) {
            unbound = Textures.BlockIcons.VOID;
            fallbackUsed = true;
        }
        if (fallbackUsed) {
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：正式版发布时移除本分支并切换至最新 API
            // 兜底常量 BlockIcons.VOID：三版均为 BlockIcons 首位 IIconContainer 常量（GlobalIcons.VOID 空纹理，无资源加载）
            LOGGER.warn("GTSR hub frame alpha icons unavailable, falling back to Textures.BlockIcons.VOID");
        }
        HUB_FRAME_RECEIVE = receive;
        HUB_FRAME_SEND = send;
        HUB_FRAME_UNBOUND = unbound;
    }

    private static final Map<String, ITexture> textureCache = new HashMap<>();

    public static ITexture getOrCreateTexture(String name, IIconContainer icon) {
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
