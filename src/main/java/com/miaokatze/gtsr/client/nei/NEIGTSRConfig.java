// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import com.miaokatze.gtsr.Tags;

import codechicken.nei.api.IConfigureNEI;

/** NEI invokes plugins before loading collapsible groups, including its first asynchronous config load. */
public final class NEIGTSRConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        GTSRNEIGroups.register();
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
