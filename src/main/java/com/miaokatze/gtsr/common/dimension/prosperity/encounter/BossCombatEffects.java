// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Shared player damage boundary for the three prosperity bosses, including their projectiles. */
public final class BossCombatEffects {

    private BossCombatEffects() {}

    public static boolean isBoss(Entity attacker) {
        if (attacker instanceof EntitySilentKing) return true;
        if (!(attacker instanceof EntityOldEcho)) return false;
        EchoKind kind = ((EntityOldEcho) attacker).getKind();
        return kind == EchoKind.DC02 || kind == EchoKind.DC08;
    }

    /** Keep vanilla's accepted-hit result for the caller's knockback and mixed-damage follow-up. */
    public static boolean hit(Entity attacker, EntityPlayer target, DamageSource source, float amount) {
        boolean boss = isBoss(attacker);
        float before = target.getHealth();
        boolean accepted = target.attackEntityFrom(source, boss ? amount * (source.isMagicDamage() ? 2F : 3F) : amount);
        float after = target.getHealth();
        // Absorption, cancellation and invulnerability can accept a hit without costing any actual HP.
        if (boss && !target.worldObj.isRemote && after < before && after > 0 && target.isEntityAlive()) {
            LifeDisintegrationPotion potion = LifeDisintegrationPotion.instance();
            if (potion == null) throw new IllegalStateException("Life disintegration potion was not registered");
            potion.lock(target);
        }
        return accepted;
    }
}
