// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.bootstrap;

import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** Reports the frozen compatibility profile without initializing GregTech business classes. */
public final class GTNHCompatibilityBootstrap {

    private static boolean reported;

    private GTNHCompatibilityBootstrap() {}

    public static synchronized void initialize() {
        if (reported) return;
        ModContainer gregtech = Loader.instance()
            .getIndexedModList()
            .get("gregtech");
        String version = gregtech == null ? "missing" : gregtech.getVersion();
        String profile = profileFor(version);
        if (profile == null) {
            GTSteamReborn.LOG
                .warn("[GTSR] Unverified GregTech environment {}; expected GTNH 2.9.0–2.9.2 (.265/.275/.277)", version);
        } else {
            GTSteamReborn.LOG.info("[GTSR] Compatibility profile GTNH {} (GregTech {})", profile, version);
        }
        reported = true;
    }

    static String profileFor(String version) {
        if ("5.09.54.265".equals(version)) return "2.9.0";
        if ("5.09.54.275".equals(version)) return "2.9.1";
        if ("5.09.54.277".equals(version)) return "2.9.2";
        return null;
    }
}
