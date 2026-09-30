package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.util.DamageSource;

import cpw.mods.fml.common.eventhandler.Event;

/** Server-only notification after Forge accepted death. Never emitted on a cancelled death. */
public final class EchoDeathEvent extends Event {

    public final EntityOldEcho entity;
    public final DamageSource source;

    public EchoDeathEvent(EntityOldEcho entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }
}
