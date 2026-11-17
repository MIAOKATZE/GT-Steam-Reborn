// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.enums.Mods;

public enum StoneSite {

    OVERWORLD("gtsr.rockbreaker.overworld", 0),
    END("gtsr.rockbreaker.end", 1),
    NETHER("gtsr.rockbreaker.nether", -1),
    OUTSIDE_NETHER("gtsr.rockbreaker.outside_nether", -1),
    MOON("moon.moon"),
    MARS("planet.mars"),
    PHOBOS("moon.Phobos"),
    DEIMOS("moon.Deimos"),
    ASTEROIDS("planet.asteroids"),
    CERES("planet.Ceres"),
    EUROPA("moon.Europa"),
    GANYMEDE("moon.Ganymed"),
    CALLISTO("moon.Callisto"),
    IO("moon.ioJupiter"),
    VENUS("planet.venus"),
    MERCURY("planet.mercury"),
    ENCELADUS("moon.Enceladus"),
    TITAN("moon.Titan"),
    MIRANDA("moon.Miranda"),
    OBERON("moon.Oberon"),
    PROTEUS("moon.Proteus"),
    TRITON("moon.Triton"),
    PLUTO("planet.pluto"),
    MAKEMAKE("planet.makemake"),
    HAUMEA("planet.haumea"),
    CENTAURI_BB("planet.CentauriBb"),
    VEGA_B("planet.Vega1"),
    BARNARDA_E("planet.Barnarda4"),
    BARNARDA_F("planet.Barnarda5"),
    T_CETI_E("planet.TCetiE");

    private final String nameKey;
    private Integer dimension;

    StoneSite(String key) {
        this(key, null);
    }

    StoneSite(String key, Integer dimension) {
        this.nameKey = key;
        this.dimension = dimension;
    }

    // Bind once after the optional space mods registered their bodies. An unresolved/disabled
    // destination never falls back to dimension 0 or to a vanilla world with a similar name.
    boolean resolve() {
        if (dimension != null) return true;
        if (!Mods.GalacticraftCore.isModLoaded()) return false;
        try {
            Class<?> registry = Class.forName("micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry");
            Object body = registry.getMethod("getCelestialBodyFromUnlocalizedName", String.class)
                .invoke(null, nameKey);
            if (body == null || !(boolean) body.getClass()
                .getMethod("getReachable")
                .invoke(body)) return false;
            dimension = (Integer) body.getClass()
                .getMethod("getDimensionID")
                .invoke(body);
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not resolve Galacticraft body " + nameKey, failure);
        }
    }

    public boolean allows(World world) {
        if (world == null || world.provider == null || dimension == null) return false;
        int actual = world.provider.dimensionId;
        return this == OUTSIDE_NETHER ? actual != dimension : actual == dimension;
    }

    public String displayName() {
        return StatCollector.translateToLocal(nameKey);
    }
}
