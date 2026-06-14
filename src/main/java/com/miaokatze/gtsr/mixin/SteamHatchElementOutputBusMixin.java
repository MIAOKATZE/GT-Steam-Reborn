package com.miaokatze.gtsr.mixin;

import java.util.Arrays;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

/**
 * Mixin for SteamHatchElement.OutputBus_Steam to also accept MTEHatchOutput subclasses.
 *
 * GT5U's OutputBus_Steam only matches MTEHatchSteamBusOutput, preventing cooling hatches
 * (MTESteamCoolingHatch extends MTEHatchOutput) from being placed in GT5U native steam
 * machines. We overwrite mteClasses() to also include MTEHatchOutput.class, so that
 * StructureLib's couldBeValid filter and hatch item filter will accept cooling hatches.
 *
 * The actual registration of cooling hatches is handled by
 * {@link MTESteamMultiBaseMixin#gtsr$onAddSteamBusOutput} which intercepts addSteamBusOutput.
 */
@Mixin(
    targets = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase$SteamHatchElement$2",
    remap = false)
public class SteamHatchElementOutputBusMixin {

    /**
     * @reason Add MTEHatchOutput.class to the list of accepted MTE classes, so that
     *         cooling hatches (MTESteamCoolingHatch extends MTEHatchOutput) and fluid
     *         output hatches can be placed in OutputBus_Steam slots of GT5U native
     *         steam machines.
     * @author GTSR
     */
    @Overwrite
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return Arrays.asList(MTEHatchSteamBusOutput.class, MTEHatchOutput.class);
    }
}
