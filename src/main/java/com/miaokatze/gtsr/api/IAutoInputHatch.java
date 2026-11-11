// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.api;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public interface IAutoInputHatch {

    boolean gtsr$isAutoInput();

    void gtsr$setAutoInput(boolean value);

    void gtsr$doAutoInput(IGregTechTileEntity aBaseMetaTileEntity, long aTick);
}
