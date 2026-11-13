// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.network.login.server.S02PacketLoginSuccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.authlib.GameProfile;

/** Forge 1.7.10 has no public login-profile getter. Only the client config installs this accessor. */
@Mixin(S02PacketLoginSuccess.class)
public interface LoginSuccessProfileAccess {

    @Accessor("field_149602_a")
    GameProfile gtsr$getLoginProfile();
}
