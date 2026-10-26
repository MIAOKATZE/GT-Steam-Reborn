package com.miaokatze.gtsr.common.critical;

import java.util.List;

import com.google.common.collect.ImmutableList;

/** Stable chemical identities and ideal volumetric proportions shared by execution and NEI. */
public final class CompoundRecipe {

    public final String id;
    public final List<Component> components;
    public final int peakMultiplier;
    /** Non-consumed integrated-circuit setting, equal to the required fluid count. */
    public final int selectorCircuit;

    CompoundRecipe(String id, int peakMultiplier, Component... components) {
        if (components.length < 2 || components.length > 9 || peakMultiplier < 1 || peakMultiplier > 100)
            throw new IllegalArgumentException("Invalid compound recipe");
        this.id = id;
        this.peakMultiplier = peakMultiplier;
        this.selectorCircuit = components.length;
        this.components = ImmutableList.copyOf(components);
    }

    public static final class Component {

        public final CriticalGTRecipes.ChemicalFuel fuel;
        public final int ratio;

        Component(CriticalGTRecipes.ChemicalFuel fuel, int ratio) {
            if (fuel == null || ratio <= 0) throw new IllegalArgumentException("Invalid compound component");
            this.fuel = fuel;
            this.ratio = ratio;
        }
    }
}
