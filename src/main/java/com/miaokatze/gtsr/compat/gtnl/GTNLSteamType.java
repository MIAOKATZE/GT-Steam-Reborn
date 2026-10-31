package com.miaokatze.gtsr.compat.gtnl;

import gregtech.api.util.GTUtility;

/** Read-only bridge to the supported upstream SteamTypes values. */
public interface GTNLSteamType {

    GTUtility.FluidId gtsr$getFluid();

    int gtsr$getEfficiencyFactor();
}
