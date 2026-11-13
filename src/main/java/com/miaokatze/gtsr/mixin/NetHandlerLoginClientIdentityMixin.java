// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.S02PacketLoginSuccess;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.client.network.ClientRecipientIdentity;

/** Capture before Forge's login handler starts the modded handshake and discards the login profile. */
@Mixin(NetHandlerLoginClient.class)
public abstract class NetHandlerLoginClientIdentityMixin {

    @Shadow
    @Final
    private NetworkManager field_147393_d;

    @Inject(
        method = "handleLoginSuccess(Lnet/minecraft/network/login/server/S02PacketLoginSuccess;)V",
        at = @At("HEAD"),
        require = 1)
    private void gtsr$captureLoginIdentity(S02PacketLoginSuccess packet, CallbackInfo callback) {
        ClientRecipientIdentity
            .recordLogin(field_147393_d, ((LoginSuccessProfileAccess) (Object) packet).gtsr$getLoginProfile());
    }
}
