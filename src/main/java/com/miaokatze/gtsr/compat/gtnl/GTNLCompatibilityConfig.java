package com.miaokatze.gtsr.compat.gtnl;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.relauncher.FMLInjectionData;

/** The Mixin plugin reads the same file before controller classes are transformed. */
public final class GTNLCompatibilityConfig {

    private GTNLCompatibilityConfig() {}

    public static boolean enhancement(Configuration configuration) {
        return configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                "gtnlEnhancement",
                false,
                "启用GTNL蒸汽机增强：真实足额过热供汽时叠加4倍耗汽/4倍速度，并启用GTSR冷却仓。默认关闭，修改后重启。")
            .setRequiresMcRestart(true)
            .getBoolean(false);
    }

    public static boolean pipeless(Configuration configuration) {
        return configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                "gtnlPipelessSteam",
                false,
                "允许GTSR耗汽机器使用GTNL无管蒸汽仓的本地储罐；保留各机原有费用和汽种。默认关闭，修改后重启。")
            .setRequiresMcRestart(true)
            .getBoolean(false);
    }

    public static Configuration startupConfiguration() {
        File gameDirectory = (File) FMLInjectionData.data()[6];
        File configurationDirectory = new File(gameDirectory, "config");
        File current = new File(configurationDirectory, "gtsr/gtsr.cfg");
        File legacy = new File(configurationDirectory, "gtsr.cfg");
        if (!current.exists() && !legacy.exists()) return new Configuration();
        Configuration configuration = new Configuration(!current.exists() && legacy.exists() ? legacy : current);
        configuration.load();
        return configuration;
    }
}
