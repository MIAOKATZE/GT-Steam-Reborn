package com.miaokatze.gtsr.api.compat;

/**
 * 普通冷却舱室契约（api 层，O2-B03②：api.compat 不再引用 machine 具体类）。
 * <p>
 * 由 {@code MTESteamCoolingHatch} 实现（含其子类）。方法签名与既有实现逐字对齐：
 * {@code isValid}/{@code updateTexture} 继承自 GT5U MTEHatch 链，{@code pushCoolingWater}
 * 为 GTSR 既有公有方法——接入接口零行为变化，仅为 api 契约侧的类型锚点。
 */
public interface ICoolingHatch {

    /**
     * 仓室是否仍有效（GT5U MTEHatch 既有语义）。
     */
    boolean isValid();

    /**
     * 更新仓室外壳底材纹理（GT5U MTEHatch 既有语义）。
     */
    void updateTexture(int aCasingIndex);

    /**
     * 推入冷却水，返回实际放入量。
     */
    int pushCoolingWater(int waterAmount);
}
