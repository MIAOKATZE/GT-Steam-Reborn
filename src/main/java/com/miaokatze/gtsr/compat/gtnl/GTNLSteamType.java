// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.compat.gtnl;

import gregtech.api.util.GTUtility;

/** Read-only bridge to the supported upstream SteamTypes values. */
public interface GTNLSteamType {

    GTUtility.FluidId gtsr$getFluid();

    int gtsr$getEfficiencyFactor();
}
