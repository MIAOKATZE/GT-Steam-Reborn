// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

/** Shared by the Mixin plugin and the transformed renderer. */
public final class PreviewCacheHooks {

    private static volatile boolean worldReady;
    private static volatile boolean sceneReady;

    private PreviewCacheHooks() {}

    public static boolean isReady() {
        return worldReady && sceneReady;
    }

    public static void setWorldReady(boolean ready) {
        worldReady = ready;
    }

    public static void setSceneReady(boolean ready) {
        sceneReady = ready;
    }
}
