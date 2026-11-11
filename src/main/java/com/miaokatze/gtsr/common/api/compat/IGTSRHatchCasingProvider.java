// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.api.compat;

/** Provides the casing texture index used by GTSR hatches. */
public interface IGTSRHatchCasingProvider {

    /**
     * Returns the casing texture index for GTSR hatches.
     *
     * @return the casing texture index
     */
    int getGTSRHatchCasingTextureIndex();
}
