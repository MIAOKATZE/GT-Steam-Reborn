package com.miaokatze.gtsr.common.dimension.prosperity;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldProviderBase;

/**
 * 繁荣维度 WorldProvider（dim1 S4b 挂 def78，plan §1.2 S2 行"微暖橙雾/天空色覆写"口径）。
 * <p>
 * S2 落地时 def78 仍持 S1 Skeleton 占位；本切片以专属实现替换（CommonProxy def78 段，
 * 照 shattered 段样式）。暖橙锈雾 = "被上一轮文明锈蚀的世界"观感（02 §0.1）；
 * 昼夜循环/天光保留原版（繁荣维度无 S6a 式无太阳需求）。canRespawnHere=false /
 * isSurfaceWorld=true 继承基类。ChunkGenerator 经 def 工厂 = {@link ChunkProviderProsperityRuins}。
 */
public class WorldProviderProsperityRuins extends GTSRWorldProviderBase {

    /** 微暖橙雾色（锈橙暖调；S7 艺术轮可调）。 */
    private static final double FOG_R = 0.72D, FOG_G = 0.55D, FOG_B = 0.38D;
    /** 天色（同族暖调，略亮）。 */
    private static final double SKY_R = 0.78D, SKY_G = 0.62D, SKY_B = 0.45D;

    @Override
    public Vec3 getFogColor(float partialTicks, float renderPass) {
        return Vec3.createVectorHelper(FOG_R, FOG_G, FOG_B);
    }

    @Override
    public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
        return Vec3.createVectorHelper(SKY_R, SKY_G, SKY_B);
    }
}
