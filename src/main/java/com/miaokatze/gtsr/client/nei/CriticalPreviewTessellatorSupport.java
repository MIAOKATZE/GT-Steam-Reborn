// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import net.minecraft.client.renderer.Tessellator;

import cpw.mods.fml.common.Loader;

/** Extra per-vertex collectors cannot be bypassed by a vanilla raw-buffer append. */
public final class CriticalPreviewTessellatorSupport {

    private CriticalPreviewTessellatorSupport() {}

    public static boolean allows(Tessellator tessellator) {
        return tessellator.getClass() == Tessellator.class
            && (!Loader.isModLoaded("angelica") || Angelica.allows(tessellator));
    }

    private static final class Angelica {

        private static boolean allows(Tessellator tessellator) {
            return tessellator instanceof com.gtnewhorizons.angelica.rendering.StateAwareTessellator state
                && !state.angelica$isCeleritasMeshing()
                && tessellator instanceof com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator blend
                && blend.angelica$getBiomeBlender() == null;
        }
    }
}
