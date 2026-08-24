package com.miaokatze.gtsr.common.machine.cluster;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 集群预热控制器：独立状态机，负责预热进度 0..10000 的推进与衰减。
 *
 * <p>
 * 与 {@link com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant} 预热范式的对应关系：
 * AmmoniaPlant 以 {@code mHeatLevel} 0..10000 口径在预热期升温（HEAT_MAX/HEAT_INCREASE_PER_SEC 常量组）、
 * 断供或停机时按 HEAT_DECREASE_PER_SEC 衰减、以 {@code mMachine} 与 {@code isAllowedToWork()} 双门控分段；
 * 本类沿用同一 0..10000 口径、同一"成型 + 启用"门控优先级与同一"增长封顶 / 衰减钳底"结构。
 *
 * <p>
 * 与范式的差异：
 * <ul>
 * <li>AmmoniaPlant 每 20 tick 整数阶梯增减；本类每 tick 以 double 连续累计，速率由
 * {@link ClusterParams#PREHEAT_SECONDS} 与两个衰减百分比常量换算，本类零调参硬编码。</li>
 * <li>AmmoniaPlant 在预热循环内自行扣蒸汽（consumeFuelAndSteam）；本类不自己扣蒸汽，
 * 由 ClusterSteamEconomy 统一双流体原子结算后以锁存布尔传入（tickServer 的
 * thermalSupplyOkLatched 参数），本类只消费判定结果。</li>
 * <li>衰减分两档：断汽走 STEAM_LOSS_DECAY_PCT_PER_SEC，停机/未成型走 SHUTDOWN_DECAY_PCT_PER_SEC。</li>
 * </ul>
 *
 * <p>
 * 三态互斥优先级（每 tick 单路径）：成型且启用且有汽 → 升温；成型且启用但断汽 → 断汽衰减；
 * 停机或未成型 → 停机衰减（其中停机判定优先于供汽状态）。
 */
public final class ClusterPreheatController {

    /** NBT 存取键（非调参数值，仅序列化标识）。 */
    private static final String NBT_KEY = "clusterPreheat";

    /** 满值刻度：AmmoniaPlant 口径 10000（内部计量上限，非调参数值）。 */
    private static final double HEAT_MAX = 10000.0;

    /** 每秒 tick 数：Minecraft 物理常量，仅作 /s → /tick 换算。 */
    private static final double TICKS_PER_SECOND = 20.0;

    /** 预热进度：0..HEAT_MAX，构造从 0 开始。 */
    private double heatLevel = 0.0;

    /** 集群预热状态机构造器：从 0 开始预热。 */
    public ClusterPreheatController() {}

    /**
     * 服务端每 tick 推进（每 tick 纯算法，不扣任何流体——经济实扣是
     * {@link ClusterSteamEconomy} 的职责）：按三态互斥优先级执行升温或衰减，
     * 速率与附录 B 对齐——升温 {@code +10000/30/20 ≈ +16.6667/tick}（30 s 预热）、
     * 断供降温 {@code -0.5%/s = -2.5/tick}、停机/未成型降温 {@code -1%/s = -5/tick}。
     *
     * <p>
     * 升温：每秒增长 100%/PREHEAT_SECONDS，即每 tick 增加
     * (10000.0 / PREHEAT_SECONDS) / 20.0，到 10000 封顶。
     * PREHEAT_SECONDS=30 时每 tick 16.666...，600 tick 累计存在浮点误差，
     * 至第 601 tick 封顶，属可容忍误差。
     *
     * @param machineEnabled         机器是否启用（停机闸门，最高优先级）
     * @param machineFormed          多方块结构是否成型（未成型同停机衰减）
     * @param thermalSupplyOkLatched 当前秒段热供应锁存（20t 双流体原子结算结果：
     *                               蒸汽且润滑均足=true，由调用方传入，本类只消费判定结果）
     */
    public void tickServer(boolean machineEnabled, boolean machineFormed, boolean thermalSupplyOkLatched) {
        if (machineEnabled && machineFormed) {
            if (thermalSupplyOkLatched) {
                double gainPerTick = (HEAT_MAX / ClusterParams.PREHEAT_SECONDS) / TICKS_PER_SECOND;
                heatLevel = Math.min(HEAT_MAX, heatLevel + gainPerTick);
            } else {
                decay(ClusterParams.STEAM_LOSS_DECAY_PCT_PER_SEC);
            }
        } else {
            decay(ClusterParams.SHUTDOWN_DECAY_PCT_PER_SEC);
        }
    }

    /**
     * 按百分比速率衰减：每 tick 减少 (pctPerSec / 100) * 10000 / 20，钳底 0。
     *
     * @param pctPerSec 每秒衰减百分比（取自 ClusterParams 的 0..10000 尺度换算系数）
     */
    private void decay(double pctPerSec) {
        if (heatLevel <= 0.0) {
            return;
        }
        double lossPerTick = pctPerSec / 100.0 * HEAT_MAX / TICKS_PER_SECOND;
        heatLevel = Math.max(0.0, heatLevel - lossPerTick);
    }

    /** @return 预热进度 0..1（heatLevel / 10000）。 */
    public double getProgress() {
        return heatLevel / HEAT_MAX;
    }

    /** @return 是否预热完成（heatLevel >= 10000，等价 progress >= 1.0）。 */
    public boolean isReady() {
        return heatLevel >= HEAT_MAX;
    }

    /** 归零预热进度（结构重建成型后重新预热）。 */
    public void reset() {
        heatLevel = 0.0;
    }

    /**
     * 直接设定进度（NBT 载入用）：外部 0..1 口径，越界钳到边界。
     *
     * @param p 目标进度 0..1
     */
    public void setProgress(double p) {
        heatLevel = Math.max(0.0, Math.min(1.0, p)) * HEAT_MAX;
    }

    /**
     * 序列化：内部 heatLevel 以单个 double 直存到键 {@value #NBT_KEY}。
     *
     * @param nbt 目标 NBT 标签
     */
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setDouble(NBT_KEY, heatLevel);
    }

    /**
     * 反序列化：缺键安全（保持现值不变）；有键时经 setProgress 钳位 0..1 后载入。
     *
     * @param nbt 来源 NBT 标签
     */
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBT_KEY)) {
            setProgress(nbt.getDouble(NBT_KEY) / HEAT_MAX);
        }
    }
}
