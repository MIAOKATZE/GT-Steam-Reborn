package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.entity.player.EntityPlayer;

/**
 * 枢纽缓存节点共同接口（S1 类型拓宽）：{@link MTEFilteredCacheNode}（量子缸族缓存节点）与四个奇点仓
 * （S1 起脱离该继承链、各自改继承对应仓室近亲）的统一类型。
 * <p>
 * 两枢纽（MTESteamHubArray/MTEWaterHubArray）的绑定解析（resolveCacheNode 族）/状态列表
 * （getCacheNodeListTag）/GUI 远程操作（cycleCacheNodeRateFromGui 等）/周期传输
 * （transferWithBoundNodes/getNodeTransferRate）与 HubTerminal.onItemUseFirst 终端交互一律面向本接口，
 * 方法行为语义与拓宽前保持一致。
 * <p>
 * 速率档口径：缓存节点=基础速率×百分比；奇点仓=固定常量（{@link #getTransferRatePercent} 恒 100，
 * {@link #cycleTransferRatePercent} 为 no-op）。
 * <p>
 * 容量档口径（S4）：缓存节点与接收类奇点仓支持容量上限档位（{@link #CAPACITY_LIMIT_CYCLE} 循环，
 * NBT 键 mCapacityLimitPercent 与既有 mTransferRatePercent 对称）；发送类奇点仓罐只出不进、
 * 容量上限无意义（{@link #supportsCapacityTier}=false，循环 no-op）。降档后超出部分温和保留
 * （拒新入不销毁）。
 */
public interface IHubCacheNode {

    /**
     * 容量档循环值域（100 → 80 → 60 → 40 → 20 → 10 → 5 → 回 100）。
     * 约定只读：实现方不得改写数组元素。
     */
    int[] CAPACITY_LIMIT_CYCLE = { 100, 80, 60, 40, 20, 10, 5 };

    // ===== 方向模式（奇点仓恒定锁定，见 isOutputModeLocked）=====

    /** 方向模式：true=接收（枢纽→节点），false=发送（节点→枢纽）。 */
    boolean isOutputMode();

    /** 设置方向模式（锁定节点拒改）。 */
    void setOutputMode(boolean output);

    /**
     * 方向模式是否锁定（奇点仓=true：setOutputMode 拒改、终端切换只发提示、枢纽 GUI 模式按钮拒改、
     * 右键已绑定分支只解绑不翻转）。默认 false（普通节点可自由切换）。
     */
    boolean isOutputModeLocked();

    /**
     * 枢纽终端潜行右击：切换方向模式并同步枢纽注册记录（IHubArray.updateCacheNodeMode）。
     * 锁定节点只发提示不切换。必须在服务端调用。
     */
    void toggleOutputModeFromTerminal(EntityPlayer player);

    // ===== 绑定状态 =====

    /** 是否已绑定到枢纽（独立于 dim 字段，主世界 dim=0 不被误判为未绑定）。 */
    boolean isBoundToHub();

    // ===== 状态 UI 列表与传输 =====

    /** 自定义名（无则空串；奇点仓无自定义名机制）。 */
    String getCustomName();

    void setCustomName(String name);

    /** 当前存储流体的注册名（FluidRegistry 名）；无流体时空串。 */
    String getStoredFluidName();

    /** 当前存储量（long，大容量节点超出 int 范围）。 */
    long getStoredFluidAmount();

    /** 容量（long）。 */
    long getFluidCapacityLong();

    /** 交互速率百分比（奇点仓无速率档，恒 100）。 */
    int getTransferRatePercent();

    /** 循环到下一档速率百分比并返回新值（奇点仓 no-op 恒 100）。 */
    int cycleTransferRatePercent();

    /** 实际枢纽交互速率 L/s（节点=基础×百分比；奇点仓=固定常量）。 */
    long getEffectiveHubTransferRate();

    // ===== 容量上限档（S4）=====

    /**
     * 容量档是否适用：缓存节点与接收类奇点仓 true；发送类奇点仓 false
     * （罐只出不进，容量上限无意义，循环 no-op、GUI 按钮禁用、NBT 不写档位）。
     */
    boolean supportsCapacityTier();

    /** 容量上限百分比（默认 100=基量；不支持容量档的节点恒 100）。 */
    int getCapacityLimitPercent();

    /** 在 CAPACITY_LIMIT_CYCLE 中循环到下一档容量百分比并返回新值（不支持容量档的节点 no-op）。 */
    int cycleCapacityLimitPercent();

    /** 自动输出开关（奇点仓自动外排机制已随 S1 删除，恒 false）。 */
    boolean isAutoOutput();

    void setAutoOutput(boolean auto);
}
