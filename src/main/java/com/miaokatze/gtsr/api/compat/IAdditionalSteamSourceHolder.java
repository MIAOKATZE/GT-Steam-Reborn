// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.api.compat;

import java.util.List;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/** Additional steam tanks whose hatch type differs from the native input lists. */
public interface IAdditionalSteamSourceHolder {

    List<MTEHatch> gtsr$getAdditionalSteamSources();

    default boolean gtsr$addAdditionalSteamSource(IGregTechTileEntity tile, int casing) {
        return false;
    }
}
