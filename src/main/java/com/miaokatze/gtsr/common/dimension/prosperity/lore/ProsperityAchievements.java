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
            "探访引线校压所",
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
            "future_pressure_witness",
            true);
        define("defeat.di-15", "击败引线傀儡", "既有精英实际死亡且玩家有效参与", "pressure_registry", "minor-boss", "brass_chronicle", true);
        define(
            "explore.recovery_exchange",
            "探访退件拆垒场",
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
            "future_recovery_witness",
            true);
        define("defeat.di-09", "击败拆垒工螯", "既有精英实际死亡且玩家有效参与", "recovery_exchange", "minor-boss", "brass_chronicle", true);
        define(
            "explore.weft_memory_hall",
            "探访缄网织谱馆",
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
            "future_weft_witness",
            true);
        define("defeat.di-05", "击败织网缄虫", "既有精英实际死亡且玩家有效参与", "weft_memory_hall", "minor-boss", "brass_chronicle", true);
        define(
            "explore.canal_tollhouse",
            "探访蚀渊税关",
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
            "future_canal_witness",
            true);
        define("defeat.di-12", "击败蚀渊鳐", "既有精英实际死亡且玩家有效参与", "canal_tollhouse", "minor-boss", "brass_chronicle", true);
        define(
            "explore.signal_lensworks",
            "探访裂弦观测所",
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
            "future_lens_witness",
            true);
        define("defeat.di-08", "击败裂弦狙手", "既有精英实际死亡且玩家有效参与", "signal_lensworks", "minor-boss", "brass_chronicle", true);
        define(
            "explore.oath_checkpoint",
            "探访镜铠换誓关堡",
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
            "future_oath_witness",
            true);
        define("defeat.di-10", "击败镜铠卫", "既有精英实际死亡且玩家有效参与", "oath_checkpoint", "minor-boss", "brass_chronicle", true);
        define(
            "explore.ash_name_vault",
            "探访腐壤存名灰庭",
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
            "future_ash_witness",
            true);
        define("defeat.di-06", "击败腐壤蔓母", "既有精英实际死亡且玩家有效参与", "ash_name_vault", "minor-boss", "brass_chronicle", true);
        define(
            "explore.resonance_exchange",
            "探访共振钟务院",
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
            "future_sound_witness",
            true);
        define(
            "defeat.di-13",
            "击败共振钟螺",
            "既有精英实际死亡且玩家有效参与",
            "resonance_exchange",
            "minor-boss",
            "brass_chronicle",
            true);
        define(
            "explore.roadbed_testyard",
            "探访跃隙路试场",
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
            "future_road_witness",
            true);
        define("defeat.di-04", "击败跃隙猎手", "既有精英实际死亡且玩家有效参与", "roadbed_testyard", "minor-boss", "brass_chronicle", true);
        define(
            "explore.anchor_calibration_house",
            "探访悬针定锚台",
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
            "future_anchor_witness",
            true);
        define(
            "defeat.di-03",
            "击败悬针浮垒",
            "既有精英实际死亡且玩家有效参与",
            "anchor_calibration_house",
            "minor-boss",
            "brass_chronicle",
            true);
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
            "relic.future_city_address_witness",
            "见证·繁荣测绘罗盘",
            "首次进入繁荣维度并有效收到导航信物",
            "prosperity_arrival",
            "relic",
            "future_city_address_witness",
            true);
        define(
            "relic.future_city_return_witness",
            "见证·扩建工程见证",
            "阅读扩建校准记录后开启工程见证箱",
            "fiction_expansion_project",
            "relic",
            "future_city_return_witness",
            true);
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
            "old_crown",
            true);
        define(
            "ending.suppressed",
            "重新命名世界",
            "归档成功且逆模因压制状态确认",
            "fiction_expansion_project",
            "ending",
            "old_crown",
            true);
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
