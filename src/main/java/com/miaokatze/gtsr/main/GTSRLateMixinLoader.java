package com.miaokatze.gtsr.main;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/** Optional mod targets must be inspected after FML has added the discovered mod jars. */
@LateMixin
public final class GTSRLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtsr.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        // Probing this target in the early config poisons classic LaunchWrapper's negative resource cache.
        return loadedMods.contains("personalspace")
            ? Collections.singletonList("PersonalWorldProviderCriticalNightMixin")
            : Collections.emptyList();
    }
}
