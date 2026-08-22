package com.miaokatze.gtsr.common.machine.base;

import net.minecraftforge.fluids.FluidStack;

public interface IHubArray {

    int receiveFluid(FluidStack fluid, boolean doFill);

    FluidStack extractFluid(int amount, boolean doDrain);

    void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode);

    void unregisterCacheNode(int x, int y, int z, int dim);

    void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode);

    boolean acceptsNodeType(String type);

    /**
     * 远程工作节点注册就绪门控（O2-B02 接口化，替代 base→machine 的钻井枢纽 instanceof）：
     * 钻井枢纽为「成形且允许运作」，双枢纽恒就绪——历史语义为非钻井枢纽不设此门。
     */
    boolean isReadyForRemoteRegistration();
}
