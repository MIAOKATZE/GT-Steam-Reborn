package com.miaokatze.gtsr.common.dimension.prosperity.echo;

/** Wire indices are append-only; saves and structure contracts always use the stable code. */
public enum EchoKind {

    DR01("dr-01", "裂隙行者", 60, 1.2F, 2.5F, BattleStyle.STALKER, "attack"),
    DR03("dr-03", "碎地爆掷者", 70, 1.4F, 1.8F, BattleStyle.BOMBER, "crystal_lob"),
    DR05("dr-05", "蚀壳裂虫", 80, .8F, .55F, BattleStyle.CHARGER, "shell_charge"),
    DR07("dr-07", "岸蚀蟹", 65, .96F, .6F, BattleStyle.SWEEPER, "attack"),
    DR10("dr-10", "断旗傀", 90, 1.5F, 2.8F, BattleStyle.SENTINEL, "attack"),
    DR11("dr-11", "锈祷者", 50, 1.2F, 2.5F, BattleStyle.MENDER, "chant"),
    DR12("dr-12", "灰烬使徒", 60, 1.2F, 2.5F, BattleStyle.CINDER, "attack"),
    DR15("dr-15", "窥隙眼", 40, .8F, 1.1F, BattleStyle.GAZER, "mark"),
    DR20("dr-20", "喑铃", 50, .7F, 1.2F, BattleStyle.BELL, "muffling_ring"),
    DI02("di-02", "蚀辉螺台", 500, 3.6F, 4.5F, BattleStyle.SPIRAL, "attack"),
    DI05("di-05", "织网缄虫", 450, 3.6F, 1.8F, BattleStyle.WEAVER, "silence_weave"),
    DI08("di-08", "裂弦狙手", 400, 1.4F, 5F, BattleStyle.SNIPER, "needle_snipe"),
    DI10("di-10", "镜铠卫", 800, 2.2F, 4.5F, BattleStyle.MIRROR, "attack"),
    DI13("di-13", "共振钟螺", 600, 2.8F, 4.5F, BattleStyle.RESONATOR, "attack"),
    DC02("dc-02", "崩垣", 1800, 10.4F, 14F, BattleStyle.SEISMIC, "seismic_impact"),
    DC08("dc-08", "巢识", 1500, 9F, 16F, BattleStyle.HIVE, "command_pulse"),
    DO01("do-01", "天外神庭", 1, .1F, .1F, BattleStyle.RITUAL, "idle");

    public enum BattleStyle {
        STALKER,
        BOMBER,
        CHARGER,
        SWEEPER,
        SENTINEL,
        MENDER,
        CINDER,
        GAZER,
        BELL,
        SPIRAL,
        WEAVER,
        SNIPER,
        MIRROR,
        RESONATOR,
        SEISMIC,
        HIVE,
        RITUAL
    }

    public final String code, displayName, skillClip;
    public final float maxHealth, width, height;
    public final BattleStyle style;

    EchoKind(String code, String name, float hp, float width, float height, BattleStyle style, String clip) {
        this.code = code;
        this.displayName = name;
        this.maxHealth = hp;
        this.width = width;
        this.height = height;
        this.style = style;
        this.skillClip = clip;
    }

    public boolean hasBossBar() {
        return code.startsWith("di-") || code.startsWith("dc-");
    }

    public boolean isRitual() {
        return style == BattleStyle.RITUAL;
    }

    public boolean isHeavy() {
        return code.startsWith("dc-");
    }

    public static EchoKind byCode(String code) {
        for (EchoKind kind : values()) if (kind.code.equalsIgnoreCase(code)) return kind;
        throw new IllegalArgumentException("Unknown old echo code: " + code);
    }

    public static EchoKind byWireId(int id) {
        return id >= 0 && id < values().length ? values()[id] : DR01;
    }
}
