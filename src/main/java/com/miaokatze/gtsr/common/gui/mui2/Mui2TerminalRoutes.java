package com.miaokatze.gtsr.common.gui.mui2;

import net.minecraftforge.common.config.Configuration;

/** Per-entry rollout switches; never changes GregTech's global machine GUI selection. */
public final class Mui2TerminalRoutes {

    public static boolean tcdsTerminal = true;
    public static boolean cacheHubTerminal = true;
    public static boolean singularityHubTerminal = true;
    public static boolean cacheNodeEditor = true;
    public static boolean remoteWorkerNodes = true;
    public static boolean aggregatorConfig = true;
    public static boolean unsealedChest = true;
    public static boolean clusterTerminal = true;
    public static boolean toolMenus = true;
    public static boolean storyScreens = true;

    private Mui2TerminalRoutes() {}

    public static void load(Configuration config) {
        tcdsTerminal = config.getBoolean(
            "tcdsTerminal",
            "mui2",
            true,
            "Use the MUI2 handheld TCDS terminal. False restores the legacy terminal; restart required.");
        cacheHubTerminal = route(config, "cacheHubTerminal", "steam and water cache hub terminals");
        singularityHubTerminal = route(config, "singularityHubTerminal", "singularity drilling hub terminal");
        cacheNodeEditor = route(config, "cacheNodeEditor", "nearby cache node editor");
        remoteWorkerNodes = route(config, "remoteWorkerNodes", "remote miner and drilling worker nodes");
        aggregatorConfig = route(config, "aggregatorConfig", "crust matter aggregator configuration");
        unsealedChest = route(config, "unsealedChest", "unsealed chest inventory");
        clusterTerminal = route(config, "clusterTerminal", "steam mineral logistics cluster terminal");
        toolMenus = route(config, "toolMenus", "tuning wand and machine debug menus");
        storyScreens = route(config, "storyScreens", "journal, navigator and altar story screens");
    }

    private static boolean route(Configuration config, String key, String description) {
        return config.getBoolean(
            key,
            "mui2",
            true,
            "Use MUI2 for " + description + ". False restores its legacy UI; restart required.");
    }
}
