package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;

/** Stable catalog definitions. Pending definitions never create items, entities or world generation. */
public final class ProsperityAchievements {

    public static final class Definition {

        public final String id, name, description, source, kind, icon;
        /** False until this source has an implemented, server-owned evidence hook. */
        public final boolean earnable;

        private Definition(String id, String name, String description, String source, String kind, String icon,
            boolean earnable) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.source = source;
            this.kind = kind;
            this.icon = icon;
            this.earnable = earnable;
        }
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, Achievement> REGISTERED = new LinkedHashMap<>();
    public static final Map<String, Definition> CATALOG = Collections.unmodifiableMap(DEFINITIONS);
    public static final Map<String, Achievement> ACHIEVEMENTS = Collections.unmodifiableMap(REGISTERED);

    static {
        define(
            "explore.pressure_registry",
            "探访失压登记所",
            "首次进入该结构实例范围；每种结构只授予一次",
            "pressure_registry",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_pressure_witness",
            "见证·停压核验印",
            "首次有效获得对应唯一信物",
            "pressure_registry",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-pressure-auditor",
            "解除阀印监核",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "pressure_registry",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.recovery_exchange",
            "探访回收交换站",
            "首次进入该结构实例范围；每种结构只授予一次",
            "recovery_exchange",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_recovery_witness",
            "见证·未销回收签",
            "首次有效获得对应唯一信物",
            "recovery_exchange",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-return-overseer",
            "解除拒收督工",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "recovery_exchange",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.weft_memory_hall",
            "探访纬忆织造馆",
            "首次进入该结构实例范围；每种结构只授予一次",
            "weft_memory_hall",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_weft_witness",
            "见证·姓名织谱",
            "首次有效获得对应唯一信物",
            "weft_memory_hall",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-weft-curator",
            "解除断纬主织",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "weft_memory_hall",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.canal_tollhouse",
            "探访逆流税关",
            "首次进入该结构实例范围；每种结构只授予一次",
            "canal_tollhouse",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_canal_witness",
            "见证·末航税票",
            "首次有效获得对应唯一信物",
            "canal_tollhouse",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-tide-collector",
            "解除逆潮收税官",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "canal_tollhouse",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.signal_lensworks",
            "探访偏光镜务所",
            "首次进入该结构实例范围；每种结构只授予一次",
            "signal_lensworks",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_lens_witness",
            "见证·归正视标",
            "首次有效获得对应唯一信物",
            "signal_lensworks",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-parallax-master",
            "解除偏光鉴定师",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "signal_lensworks",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.oath_checkpoint",
            "探访换誓关堡",
            "首次进入该结构实例范围；每种结构只授予一次",
            "oath_checkpoint",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_oath_witness",
            "见证·双面换岗牌",
            "首次有效获得对应唯一信物",
            "oath_checkpoint",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-oath-captain",
            "解除换誓长官",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "oath_checkpoint",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.ash_name_vault",
            "探访存名灰堂",
            "首次进入该结构实例范围；每种结构只授予一次",
            "ash_name_vault",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_ash_witness",
            "见证·未焚名册",
            "首次有效获得对应唯一信物",
            "ash_name_vault",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-ash-keeper",
            "解除存名司炉",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "ash_name_vault",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.resonance_exchange",
            "探访异拍钟务院",
            "首次进入该结构实例范围；每种结构只授予一次",
            "resonance_exchange",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_sound_witness",
            "见证·反相钟舌",
            "首次有效获得对应唯一信物",
            "resonance_exchange",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-counterbeat",
            "解除反拍钟师",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "resonance_exchange",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.roadbed_testyard",
            "探访旧路试验院",
            "首次进入该结构实例范围；每种结构只授予一次",
            "roadbed_testyard",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_road_witness",
            "见证·归途路标",
            "首次有效获得对应唯一信物",
            "roadbed_testyard",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-route-warden",
            "解除归途路监",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "roadbed_testyard",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.anchor_calibration_house",
            "探访定锚校准馆",
            "首次进入该结构实例范围；每种结构只授予一次",
            "anchor_calibration_house",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_anchor_witness",
            "见证·定锚坐标章",
            "首次有效获得对应唯一信物",
            "anchor_calibration_house",
            "relic",
            "brass_chronicle",
            false);
        define(
            "defeat.mb-anchor-surveyor",
            "解除定锚测记官",
            "该小首领实际死亡且玩家有效参与；进入结构或阅读不替代击败",
            "anchor_calibration_house",
            "minor-boss",
            "brass_chronicle",
            false);
        define(
            "explore.last_shift_shelter",
            "探访末班候车亭",
            "首次进入该结构实例范围；每种结构只授予一次",
            "last_shift_shelter",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.seal_membrane_post",
            "探访换膜值守屋",
            "首次进入该结构实例范围；每种结构只授予一次",
            "seal_membrane_post",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.empty_payroll_kiosk",
            "探访空薪票亭",
            "首次进入该结构实例范围；每种结构只授予一次",
            "empty_payroll_kiosk",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.canal_measure_hut",
            "探访水尺小屋",
            "首次进入该结构实例范围；每种结构只授予一次",
            "canal_measure_hut",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.silt_sampling_booth",
            "探访沉渣采样亭",
            "首次进入该结构实例范围；每种结构只授予一次",
            "silt_sampling_booth",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.evacuation_notice_house",
            "探访撤离告示屋",
            "首次进入该结构实例范围；每种结构只授予一次",
            "evacuation_notice_house",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.fractured_flag_room",
            "探访断旗更衣间",
            "首次进入该结构实例范围；每种结构只授予一次",
            "fractured_flag_room",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.return_label_shed",
            "探访退件标签棚",
            "首次进入该结构实例范围；每种结构只授予一次",
            "return_label_shed",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.song_roll_archive",
            "探访歌卷藏室",
            "首次进入该结构实例范围；每种结构只授予一次",
            "song_roll_archive",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.broken_clock_balcony",
            "探访停钟阳台站",
            "首次进入该结构实例范围；每种结构只授予一次",
            "broken_clock_balcony",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.lens_inspection_box",
            "探访镜片检验间",
            "首次进入该结构实例范围；每种结构只授予一次",
            "lens_inspection_box",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.family_address_counter",
            "探访旧址登记柜",
            "首次进入该结构实例范围；每种结构只授予一次",
            "family_address_counter",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.slag_sorter_annex",
            "探访筛渣附房",
            "首次进入该结构实例范围；每种结构只授予一次",
            "slag_sorter_annex",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.anchor_rope_locker",
            "探访旧锚绳具房",
            "首次进入该结构实例范围；每种结构只授予一次",
            "anchor_rope_locker",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.silent_tea_room",
            "探访缄默茶室",
            "首次进入该结构实例范围；每种结构只授予一次",
            "silent_tea_room",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.freight_weigh_cabin",
            "探访拒收磅房",
            "首次进入该结构实例范围；每种结构只授予一次",
            "freight_weigh_cabin",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.filter_pressure_nook",
            "探访滤压小庐",
            "首次进入该结构实例范围；每种结构只授予一次",
            "filter_pressure_nook",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.unposted_letter_booth",
            "探访未寄书信台",
            "首次进入该结构实例范围；每种结构只授予一次",
            "unposted_letter_booth",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.gauge_keeper_hut",
            "探访读表员小居",
            "首次进入该结构实例范围；每种结构只授予一次",
            "gauge_keeper_hut",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.maintenance_oil_cache",
            "探访保养油封库",
            "首次进入该结构实例范围；每种结构只授予一次",
            "maintenance_oil_cache",
            "explore",
            "brass_chronicle",
            false);
        define(
            "explore.abandoned_city",
            "探访废弃城市",
            "首次进入该结构实例范围；每种结构只授予一次",
            "abandoned_city",
            "explore",
            "brass_chronicle",
            false);
        define(
            "relic.future_city_address_witness",
            "见证·旧址原件",
            "首次有效获得对应唯一信物",
            "abandoned_city",
            "relic",
            "brass_chronicle",
            false);
        define(
            "relic.future_city_return_witness",
            "见证·未归名单",
            "首次有效获得对应唯一信物",
            "abandoned_city",
            "relic",
            "brass_chronicle",
            false);
        define("city.old_era", "探索旧时代", "首次有效阅读三个不同分区的现场档案", "abandoned_city", "city", "brass_chronicle", false);
        define("city.address_original", "旧址仍可寻", "实际获得旧址原件", "abandoned_city", "city", "brass_chronicle", false);
        define("city.unreturned", "让姓名归来", "实际击败未归者守录人并获得对应证据", "abandoned_city", "city", "brass_chronicle", false);
        define("city.districts", "走过空城六区", "实际进入全部六个不同分区", "abandoned_city", "city", "brass_chronicle", false);
        define(
            "relic.foundry_heart_fragment",
            "见证·铸造之心碎片",
            "首次有效获得对应唯一信物",
            "fallen_foundry",
            "relic",
            "foundry_heart_fragment",
            true);
        define(
            "explore.fallen_foundry",
            "探访崩垣铸造战场",
            "首次进入真实结构范围",
            "fallen_foundry",
            "explore",
            "foundry_heart_fragment",
            true);
        define(
            "defeat.dc-02",
            "击败崩垣",
            "实际死亡事件结算且玩家有效参与",
            "fallen_foundry",
            "major-boss",
            "foundry_heart_fragment",
            true);
        define(
            "relic.hive_memory_knot",
            "见证·巢忆结",
            "首次有效获得对应唯一信物",
            "subsided_factory",
            "relic",
            "hive_memory_knot",
            true);
        define(
            "explore.subsided_factory",
            "探访巢识沉降工厂",
            "首次进入真实结构范围",
            "subsided_factory",
            "explore",
            "hive_memory_knot",
            true);
        define("defeat.dc-08", "击败巢识", "实际死亡事件结算且玩家有效参与", "subsided_factory", "major-boss", "hive_memory_knot", true);
        define("relic.old_crown", "见证·旧王冠", "首次有效获得对应唯一信物", "forgotten_lake_court", "relic", "old_crown", true);
        define(
            "explore.forgotten_lake_court",
            "探访遗忘湖巨树王庭",
            "首次进入真实结构范围",
            "forgotten_lake_court",
            "explore",
            "old_crown",
            true);
        define("defeat.dc-10", "击败缄王", "实际死亡事件结算且玩家有效参与", "forgotten_lake_court", "major-boss", "old_crown", true);
        define(
            "ending.fifteen_witnesses",
            "十五见证同在",
            "十五种不同终局信物全部有效获得",
            "fiction_expansion_project",
            "ending",
            "brass_chronicle",
            false);
        define(
            "ending.suppressed",
            "重新命名世界",
            "归档成功且逆模因压制状态确认",
            "fiction_expansion_project",
            "ending",
            "brass_chronicle",
            false);
    }

    private ProsperityAchievements() {}

    private static void define(String id, String name, String description, String source, String kind, String icon,
        boolean earnable) {
        if (DEFINITIONS.put(id, new Definition(id, name, description, source, kind, icon, earnable)) != null)
            throw new IllegalStateException("Duplicate prosperity definition " + id);
    }

    public static boolean earnable(String id) {
        Definition definition = DEFINITIONS.get(id);
        return definition != null && definition.earnable;
    }

    public static void register() {
        if (!REGISTERED.isEmpty()) return;
        // New graph starts beyond the legacy page; six lanes have deterministic, distinct coordinates.
        String[] kinds = { "explore", "relic", "major-boss", "minor-boss", "city", "ending" };
        for (int lane = 0; lane < kinds.length; lane++) {
            int index = 0;
            for (Definition definition : DEFINITIONS.values()) {
                if (!kinds[lane].equals(definition.kind)) continue;
                Item icon = LoreRegistry.RELICS.get(definition.icon);
                if (icon == null) throw new IllegalStateException("Missing real relic icon " + definition.icon);
                // Each definition owns independent evidence; receiving a relic must not require a prior visit.
                Achievement parent = null;
                Achievement achievement = new Achievement(
                    "achievement.gtsr." + definition.id,
                    "gtsr." + definition.id,
                    12 + lane * 4,
                    -12 + index++ * 2,
                    new ItemStack(icon),
                    parent);
                if ("major-boss".equals(definition.kind) || "ending".equals(definition.kind)) achievement.setSpecial();
                REGISTERED.put(definition.id, achievement.registerStat());
            }
        }
    }
}
