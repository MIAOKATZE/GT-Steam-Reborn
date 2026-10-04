package com.miaokatze.gtsr.common.dimension.prosperity.entity;

/** Shared renderer contract; does not change entity inheritance or spawning categories. */
public interface ProsperityDeathVisual {

    int getDeathAnimationTicks();

    int getDeathAnimationDuration();

    int getDeathAnimationHoldTicks();

    boolean isGoldenDeath();

    float getDeathAlpha(float partial);
}
