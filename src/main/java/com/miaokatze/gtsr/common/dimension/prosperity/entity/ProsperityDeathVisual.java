// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.prosperity.entity;

/** Shared renderer contract; does not change entity inheritance or spawning categories. */
public interface ProsperityDeathVisual {

    int getDeathAnimationTicks();

    int getDeathAnimationDuration();

    int getDeathAnimationHoldTicks();

    boolean isGoldenDeath();

    float getDeathAlpha(float partial);
}
