package com.miaokatze.gtsr.common.machine.base;

import net.minecraftforge.fluids.FluidStack;

public interface IHubArray {

    int receiveFluid(FluidStack fluid, boolean doFill);

    FluidStack extractFluid(int amount, boolean doDrain);

    void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode);

    void unregisterCacheNode(int x, int y, int z, int dim);

    void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode);

    boolean acceptsNodeType(String type);
}
