// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.Tags;

import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** NEI invokes plugins before loading collapsible groups, including its first asynchronous config load. */
public final class NEIGTSRConfig implements IConfigureNEI {

    private static boolean listening;

    @Override
    public void loadConfig() {
        GTSRNEIGroups.register();
        if (!NEICollapsibleCompat.usesNativeApi() && !listening) {
            MinecraftForge.EVENT_BUS.register(this);
            listening = true;
        }
    }

    @SubscribeEvent
    public void configsLoaded(NEIConfigsLoadedEvent event) {
        NEICollapsibleCompat.onConfigsLoaded();
    }

    @Override
    public String getName() {
        return "GTSR Item Groups";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
