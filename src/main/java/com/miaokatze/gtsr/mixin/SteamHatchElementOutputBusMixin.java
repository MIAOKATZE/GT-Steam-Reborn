package com.miaokatze.gtsr.mixin;

import java.util.Arrays;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

/**
 * Mixin for SteamHatchElement.OutputBus_Steam to also accept cooling hatches.
 *
 * GT5U's OutputBus_Steam only matches MTEHatchSteamBusOutput, preventing cooling hatches
 * (MTESteamCoolingHatch extends MTEHatchOutput) from being placed in GT5U native steam
 * machines. We overwrite mteClasses() to also include MTESteamCoolingHatch.class, so that
 * StructureLib's couldBeValid filter and hatch item filter will accept cooling hatches
 * (including MTEPressureSteamCoolingHatch which extends MTESteamCoolingHatch).
 *
 * However, adding cooling hatches to mteClasses() also causes StructureLib's
 * hatchItemFilter (used by NEI projection) to match them. Because the hatch element is
 * chained before casing blocks via buildAndChain(), the NEI preview would otherwise place
 * cooling hatches at every 'A' position instead of casing blocks. To prevent this while
 * keeping cooling hatches valid for structure detection, we also add an mteBlacklist()
 * method to the anonymous OutputBus_Steam subclass. The method overrides the default
 * implementation inherited from IHatchElement and excludes the cooling hatch classes from
 * the NEI hatchItemFilter.
 *
 * The actual registration of cooling hatches is handled by
 * {@link MTESteamMultiBaseMixin#gtsr$onAddSteamBusOutput} which intercepts addSteamBusOutput.
 */
@Mixin(
    targets = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase$SteamHatchElement$2",
    remap = false)
public class SteamHatchElementOutputBusMixin {

    /**
     * @reason Add MTESteamCoolingHatch.class to the list of accepted MTE classes, so that
     *         cooling hatches (MTESteamCoolingHatch and MTEPressureSteamCoolingHatch) can
     *         be placed in OutputBus_Steam slots of GT5U native steam machines.
     * @author GTSR
     */
    @Overwrite
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return Arrays.asList(MTEHatchSteamBusOutput.class, MTESteamCoolingHatch.class);
    }

    /**
     * @reason Exclude cooling hatches from the StructureLib hatchItemFilter used by NEI
     *         projection, so they do not override casing-block previews at every 'A'
     *         position. This method is injected into the anonymous OutputBus_Steam
     *         subclass to override the default implementation inherited from IHatchElement.
     *         It does not affect actual structure detection, which uses the adder
     *         (addSteamBusOutput) and still accepts cooling hatches via the
     *         MTESteamMultiBaseMixin hook.
     * @author GTSR
     */
    public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
        return Arrays.asList(MTESteamCoolingHatch.class, MTEPressureSteamCoolingHatch.class);
    }
}
