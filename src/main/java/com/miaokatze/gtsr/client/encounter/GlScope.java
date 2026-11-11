// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.encounter;

/** Compatibility facade; implementation is owned by MIAOKATZE Core. */
public final class GlScope extends com.miaokatze.miaokatzecore.client.render.GlScope {

    public GlScope() {
        super();
    }

    @Override
    public void close() {
        super.close();
    }
}
