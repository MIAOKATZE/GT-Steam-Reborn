package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C02PacketUseEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.EncounterHitboxes;

@Mixin(NetHandlerPlayServer.class)
public abstract class EncounterReachMixin {

    @Redirect(
        method = "processUseEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;getDistanceSqToEntity(Lnet/minecraft/entity/Entity;)D"),
        require = 1)
    private double gtsr$attackDistance(EntityPlayerMP player, Entity target, C02PacketUseEntity packet) {
        return packet.func_149565_c() == C02PacketUseEntity.Action.ATTACK
            ? EncounterHitboxes.attackDistanceSq(player, target)
            : player.getDistanceSqToEntity(target);
    }
}
