package com.miaokatze.gtsr.api;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public interface IAutoInputHatch {

    boolean gtsr$isAutoInput();

    void gtsr$setAutoInput(boolean value);

    void gtsr$doAutoInput(IGregTechTileEntity aBaseMetaTileEntity, long aTick);
}
