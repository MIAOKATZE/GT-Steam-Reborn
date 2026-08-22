package com.miaokatze.gtsr.api.compat;

/**
 * 压力冷却舱室契约（api 层，O2-B03②：api.compat 不再引用 machine 具体类）。
 * <p>
 * 由 {@code MTEPressureSteamCoolingHatch} 实现；冷却水推入语义继承自父类
 * {@code MTESteamCoolingHatch}（同时满足 {@link ICoolingHatch}）。
 */
public interface IPressureSteamCoolingHatch extends ICoolingHatch {

    /**
     * 推入冷却蒸汽（过热蒸汽消耗产物），返回实际放入量。
     */
    int pushCoolingSteam(int superheatedSteamConsumed);
}
