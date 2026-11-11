// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.weapons;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSourceIndirect;

/** Keeps native killer attribution while carrying the launch-time loot modifier. */
public final class WeaponDamageSource extends EntityDamageSourceIndirect {

    public final int looting;

    public WeaponDamageSource(String name, Entity direct, EntityLivingBase shooter, int looting) {
        super(name, direct, shooter);
        this.looting = looting;
    }
}
