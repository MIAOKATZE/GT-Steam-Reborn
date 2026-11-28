// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.
package com.miaokatze.gtsr.client.render;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** One wall-time observation per rendered frame, shared by TESR and logical scene consumers. */
public final class ClientRenderFrame {

    private static boolean registered;
    private static long frame;
    private static double seconds = System.nanoTime() / 1_000_000_000D;
    private static int uploads = 4;

    private ClientRenderFrame() {}

    public static void register() {
        if (registered) return;
        registered = true;
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientRenderFrame());
    }

    @SubscribeEvent
    public void begin(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        seconds = System.nanoTime() / 1_000_000_000D;
        uploads = 4;
    }

    public static long id() {
        return frame;
    }

    public static double seconds() {
        return seconds;
    }

    /** A bounded number of first-upload rigid groups across all scene and TESR consumers. */
    public static boolean tryBuild() {
        if (uploads == 0) return false;
        uploads--;
        return true;
    }
}
