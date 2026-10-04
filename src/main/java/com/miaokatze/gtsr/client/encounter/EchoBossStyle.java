package com.miaokatze.gtsr.client.encounter;

import net.minecraft.util.ResourceLocation;

/** Stable asset identities, independent of append-only wire enum positions. */
public final class EchoBossStyle {

    private EchoBossStyle() {}

    public static int palette(String code) {
        if ("di-02".equals(code)) return 0xB57456;
        if ("di-05".equals(code)) return 0x84B49B;
        if ("di-08".equals(code)) return 0xB9ADC8;
        if ("di-10".equals(code)) return 0xC9BD80;
        if ("di-13".equals(code)) return 0x68A6C1;
        if ("dc-02".equals(code)) return 0xCD824D;
        if ("dc-08".equals(code)) return 0x84A362;
        // Added DI identities share a neutral purple-steel frame until a dedicated asset exists.
        return 0xB9ADC8;
    }

    public static ResourceLocation frame(String code) {
        String asset = "di-10";
        if ("di-02".equals(code) || "di-05".equals(code)
            || "di-08".equals(code)
            || "di-10".equals(code)
            || "di-13".equals(code)
            || "dc-02".equals(code)
            || "dc-08".equals(code)) asset = code;
        return new ResourceLocation("gtsr", "textures/gui/" + asset + "_boss_frame.png");
    }
}
