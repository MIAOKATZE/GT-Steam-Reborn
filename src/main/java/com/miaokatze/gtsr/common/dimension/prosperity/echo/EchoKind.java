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
    DC02("dc-02", "崩垣", 2400, 10.4F, 14F, BattleStyle.SEISMIC, "seismic_impact"),
    DC08("dc-08", "巢识", 2000, 9F, 16F, BattleStyle.HIVE, "command_pulse"),
    DO01("do-01", "天外神庭", 1, .1F, .1F, BattleStyle.RITUAL, "idle"),
    DI01("di-01", "奇点幽影", 300, 3.241F, 5.000F, BattleStyle.AUTHORED, "attack"),
    DI03("di-03", "悬针浮垒", 220, 4.445F, 7.000F, BattleStyle.AUTHORED, "attack"),
    DI04("di-04", "跃隙猎手", 145, 3.575F, 2.936F, BattleStyle.AUTHORED, "attack"),
    DI06("di-06", "腐壤蔓母", 160, 2.200F, 5.800F, BattleStyle.AUTHORED, "vine_sweep"),
    DI07("di-07", "驮隙巨兽", 260, 4.550F, 6.014F, BattleStyle.AUTHORED, "brood_release"),
    DI09("di-09", "拆垒工螯", 160, 3.575F, 2.064F, BattleStyle.AUTHORED, "attack"),
    DI11("di-11", "噬雷藤", 130, 1.013F, 4.500F, BattleStyle.AUTHORED, "attack"),
    DI12("di-12", "蚀渊鳐", 160, 4.225F, 1.807F, BattleStyle.AUTHORED, "attack"),
    DI14("di-14", "裂口花", 180, 2.079F, 5.000F, BattleStyle.AUTHORED, "attack"),
    DI15("di-15", "引线傀儡", 100, 1.450F, 3.800F, BattleStyle.AUTHORED, "attack"),
    DO02("do-02", "逆模因实体", 900, 4.000F, 8.000F, BattleStyle.CONTROLLED, "cognitive_erasure"),
    DR02("dr-02", "护盾蛀虫", 20, 0.650F, 0.282F, BattleStyle.AUTHORED, "attack"),
    DR04("dr-04", "灰蚀蛆簇", 12, 0.750F, 0.500F, BattleStyle.AUTHORED, "brood_bite"),
    DR06("dr-06", "缝织蟎", 16, 0.650F, 0.128F, BattleStyle.AUTHORED, "attack"),
    DR08("dr-08", "碎锚蜗", 46, 0.780F, 0.995F, BattleStyle.AUTHORED, "attack"),
    DR09("dr-09", "残誓兵", 52, 1.274F, 2.500F, BattleStyle.AUTHORED, "attack"),
    DR13("dr-13", "鸣咽油囊", 36, 0.701F, 1.200F, BattleStyle.AUTHORED, "acid_lament"),
    DR14("dr-14", "隙管水母", 30, 0.896F, 1.600F, BattleStyle.AUTHORED, "crystal_triplet"),
    DR16("dr-16", "浮渣聚块", 32, 1.050F, 1.180F, BattleStyle.AUTHORED, "attack"),
    DR17("dr-17", "缝隙根须", 45, 0.750F, 2.150F, BattleStyle.AUTHORED, "attack"),
    DR18("dr-18", "采蚀工蜂", 24, 0.700F, 0.600F, BattleStyle.AUTHORED, "attack"),
    DR19("dr-19", "回响残响", 34, 1.081F, 2.500F, BattleStyle.AUTHORED, "echo_reprise");

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
        RITUAL,
        AUTHORED,
        CONTROLLED
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

    /** Physical movement is independent of combat state and authored spawn-zone labels. */
    public boolean flies() {
        return this == DI01 || this == DI03
            || this == DI12
            || this == DR14
            || this == DR15
            || this == DR18
            || this == DR19
            || this == DR20
            || this == DO02
            || this == DO01;
    }

    public boolean stationary() {
        return this == DI06 || this == DI11 || this == DI14 || this == DR17;
    }

    public static EchoKind byCode(String code) {
        for (EchoKind kind : values()) if (kind.code.equalsIgnoreCase(code)) return kind;
        throw new IllegalArgumentException("Unknown old echo code: " + code);
    }

    public static EchoKind byWireId(int id) {
        return id >= 0 && id < values().length ? values()[id] : DR01;
    }
}
