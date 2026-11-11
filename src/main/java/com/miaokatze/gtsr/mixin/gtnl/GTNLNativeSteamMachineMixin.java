// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin.gtnl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import com.miaokatze.gtsr.api.compat.ISelfManagedSteamMachine;
import com.miaokatze.gtsr.config.Config;

/** Required identity bridge: retains upstream ownership even when optional enhancement is disabled. */
@Pseudo
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase", remap = false)
public abstract class GTNLNativeSteamMachineMixin implements ISelfManagedSteamMachine {

    @Override
    public boolean gtsr$allowsCoolingRegistration() {
        return Config.gtnlEnhancement;
    }
}
