package com.miaokatze.gtsr.common.gui.mui2;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** MUI2 packet reads enqueue work; world mutations run at server tick END, like TerminalNet. */
public final class Mui2ServerActions {

    private static final ConcurrentLinkedQueue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    private static boolean registered;

    private Mui2ServerActions() {}

    public static void register() {
        if (!registered) {
            FMLCommonHandler.instance()
                .bus()
                .register(new Drain());
            registered = true;
        }
    }

    /** Callers bound outstanding work per session and revalidate their active container inside the task. */
    public static void enqueue(Runnable action) {
        PENDING.offer(action);
    }

    /** Drop pending closures after server shutdown; never execute actions against the stopped world. */
    public static void clear() {
        PENDING.clear();
    }

    public static final class Drain {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Runnable action;
            while ((action = PENDING.poll()) != null) {
                try {
                    action.run();
                } catch (RuntimeException error) {
                    GTSteamReborn.LOG.error("[MUI2] Terminal action failed", error);
                }
            }
        }
    }
}
