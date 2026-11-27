// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.miaokatze.gtsr.common.util.GtsrChunkLoadingPolicy;

import gregtech.api.objects.GTChunkManager;

/** Retires legacy GT-owned structure tickets without changing any other mod's loading policy. */
@Mixin(value = GTChunkManager.class, remap = false)
public abstract class MixinGtsrChunkLoadingPolicy {

    @Inject(method = "requestPlayerChunkLoad", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtsr$restrictOwner(TileEntity owner, ChunkCoordIntPair chunk, String player,
        CallbackInfoReturnable<Boolean> cir) {
        if (GtsrChunkLoadingPolicy.isGtsrOwner(owner) && !GtsrChunkLoadingPolicy.permitsPersistentLoading(owner))
            cir.setReturnValue(false);
    }

    @ModifyVariable(
        method = "ticketsLoaded(Ljava/util/List;Lnet/minecraft/world/World;I)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true,
        require = 1)
    private List<Ticket> gtsr$filterRestoredOwners(List<Ticket> tickets, List<Ticket> original, World world,
        int limit) {
        return GtsrChunkLoadingPolicy.filterLegacyGtTickets(tickets, world);
    }

    @ModifyExpressionValue(
        method = "releaseTicket",
        at = @At(value = "FIELD", target = "Lgregtech/api/enums/GTValues;enableChunkloaders:Z"),
        require = 1)
    private static boolean gtsr$alwaysAllowCleanup(boolean enabled, TileEntity owner) {
        return enabled || GtsrChunkLoadingPolicy.isGtsrOwner(owner);
    }
}
