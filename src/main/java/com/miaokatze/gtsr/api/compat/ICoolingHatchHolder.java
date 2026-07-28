package com.miaokatze.gtsr.api.compat;

import java.util.ArrayList;

import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

/**
 * 冷却舱室持有者接口。
 * <p>
 * 由 {@link com.miaokatze.gtsr.mixin.MTESteamMultiBaseMixin} implements（注入到
 * {@code MTESteamMultiBlockBase} 父类），GTNL {@code SteamMultiMachineBase} 子类继承自动获得。
 * GTNL 专用 mixin 通过此接口访问父类注入的冷却舱室列表与累积蒸汽状态，
 * 因为子类 mixin 无法直接访问父类 mixin 的 {@code @Unique private} 字段。
 */
public interface ICoolingHatchHolder {

    /**
     * 获取普通冷却舱室列表（接收冷却水）。
     *
     * @return 普通冷却舱室列表
     */
    ArrayList<MTESteamCoolingHatch> gtsr$getCoolingHatches();

    /**
     * 获取压力冷却舱室列表（接收冷却蒸汽）。
     *
     * @return 压力冷却舱室列表
     */
    ArrayList<MTEPressureSteamCoolingHatch> gtsr$getPressureHatches();

    /**
     * 获取累积的普通蒸汽量（每 160L 转 1L 水）。
     *
     * @return 累积蒸汽量
     */
    int gtsr$getAccumulatedSteam();

    /**
     * 设置累积的普通蒸汽量。
     *
     * @param value 新的累积蒸汽量
     */
    void gtsr$setAccumulatedSteam(int value);
}
