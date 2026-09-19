package com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * 城外废墟族的模板库（<b>P8，plan §5 P8「新族骨架」/ §2.1 L5 结构层</b>）：8 条破坏结构，
 * <b>每一条都由既有 GTSR 结构经 {@link RuinDamageOps} 破败化派生</b>，不新造任何方块/记号/方块状态
 * （任务包硬约束"只能复用既有方块与记号族"）。
 * <p>
 * ═══ 派生谱系（母体 → 算子 → 破坏结构；每条都在 {@link RuinTemplate#mother()} /
 * {@link RuinTemplate#ops()} 里申报，由 {@code tools/dim1/RuinFamilyCheck} 逐条复核"派生可复算"）═══
 * 母体只取 {@link com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes}（5 机型）
 * 与 {@link com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer}（6 中型废墟）
 * 两族——它们的 {@code layers} 是 public 字段，跨包只读即可；城 26 变体的 {@code Variant.layers}
 * 是包内字段，读它要么改旧模板文件（禁止项）要么反射破封装，故本片不取城母体。
 * <p>
 * ═══ 规模档（scaleTier）与 micro 强度层（判据 6：收口 P6 的悬空层）═══
 * 按 footprint 面积分 0 小 / 1 中 / 2 大。规模档上界是 <b>micro 强度</b>调的两件事之一
 * （另一件是密度，见 {@code RuinPlacer.roll}）：P6 交付的 {@code microStrengthAt} 到此为止
 * 结构侧消费点一直是 0（plan §5 P8 点名必须收口），本片由本族真实消费它。
 * 未接线/降级时 micro = 中性 1.0F ⇒ 上界回到中档，即"链路活着、调制最弱"。
 */
public final class RuinShapes {

    /** 废墟族注册名前缀（机检按它把本族与既有三族分开数，不靠"名字看起来像"）。 */
    public static final String NAME_PREFIX = "ruin_";

    /** 规模档上界的定义域（0 小 / 1 中 / 2 大）——{@link #scaleTier} 与 {@link #MICRO_TO_MAX_SCALE} 共用。 */
    public static final int MAX_SCALE_TIER = 2;

    /**
     * micro 结构档 → 允许的<b>最大</b>规模档。
     * <p>
     * 索引 = {@link RuinDamageOps#structuralTier(float)}（0 = 强 cell 1.3 / 1 = 中 1.0 / 2 = 弱 0.7）：
     * 强 cell 才放大件、弱 cell 只出小件——"荒到尽头连墙都剩不下"的观感分级。
     * 这张表是规模档调制的<b>唯一</b>出处（{@link #allowedMaxScaleTier(int)} 是唯一读它的地方）。
     */
    private static final int[] MICRO_TO_MAX_SCALE = { 2, 1, 0 };

    /** ① 断跨渠段残段 16×8×5 ← {@code outpost_broken_aqueduct}：中跨断口 + 镀铜件锈回壳层。 */
    public static final RuinTemplate AQUEDUCT_SPAN = new RuinTemplate(
        "ruin_aqueduct_span",
        "outpost_broken_aqueduct",
        new String[] { RuinDamageOps.OP_BREAK_SPAN, RuinDamageOps.OP_RUST_SWAP },
        0x52714348L);

    /** ② 塌桁扇形残骸 16×7×6 ← {@code outpost_collapsed_truss}：上 2/3 甩成扇形散料 + 四角啃缺。 */
    public static final RuinTemplate TRUSS_FAN = new RuinTemplate(
        "ruin_truss_fan",
        "outpost_collapsed_truss",
        new String[] { RuinDamageOps.OP_COLLAPSE_FAN, RuinDamageOps.OP_CHIP_CORNERS, RuinDamageOps.OP_RUST_SWAP },
        0x54524641L);

    /** ③ 砖窑残冠 8×9×8 ← {@code outpost_brick_kiln}：四角啃缺 + 整体下沉半埋 + 壳层锈变（档 3）。 */
    public static final RuinTemplate KILN_STUMP = new RuinTemplate(
        "ruin_kiln_stump",
        "outpost_brick_kiln",
        new String[] { RuinDamageOps.OP_CHIP_CORNERS, RuinDamageOps.OP_HALF_BURY, RuinDamageOps.OP_RUST_SWAP },
        0x4B4C4E53L);

    /** ④ 倾覆锅炉座 9×6×7 ← {@code outpost_toppled_boiler}：逐层错动歪倒 + 一侧散料覆压。 */
    public static final RuinTemplate BOILER_LEAN = new RuinTemplate(
        "ruin_boiler_lean",
        "outpost_toppled_boiler",
        new String[] { RuinDamageOps.OP_TOPPLE, RuinDamageOps.OP_HALF_BURY, RuinDamageOps.OP_RUST_SWAP },
        0x424F494CL);

    /** ⑤ 半埋瞭望残躯 7×11×7 ← {@code outpost_watch_post}：只下沉 1-2 层（损毁档 1 = 最轻的一档）。 */
    public static final RuinTemplate WATCH_BURIED = new RuinTemplate(
        "ruin_watch_buried",
        "outpost_watch_post",
        new String[] { RuinDamageOps.OP_HALF_BURY },
        0x57415443L);

    /** ⑥ 烟囱塌落扇 5×8×5 ← 机型 {@code chimney_base}：顶部锯齿整段甩到基座 + 缺角。 */
    public static final RuinTemplate CHIMNEY_FAN = new RuinTemplate(
        "ruin_chimney_fan",
        "chimney_base",
        new String[] { RuinDamageOps.OP_COLLAPSE_FAN, RuinDamageOps.OP_CHIP_CORNERS },
        0x43484D46L);

    /** ⑦ 断跨管廊 13×4×3 ← 机型 {@code steam_gallery}：后跨整段消失 + 墩柱锈变。 */
    public static final RuinTemplate GALLERY_SPAN = new RuinTemplate(
        "ruin_gallery_span",
        "steam_gallery",
        new String[] { RuinDamageOps.OP_BREAK_SPAN, RuinDamageOps.OP_RUST_SWAP },
        0x474C5259L);

    /** ⑧ 缺角泵座残墩 5×3×5 ← 机型 {@code pump_base}：四角啃缺 + 座圈锈成三色壳。 */
    public static final RuinTemplate PUMP_CHIP = new RuinTemplate(
        "ruin_pump_chip",
        "pump_base",
        new String[] { RuinDamageOps.OP_CHIP_CORNERS, RuinDamageOps.OP_RUST_SWAP },
        0x504D5043L);

    /** 8 条破坏结构（放置时在其内均匀掷选，候选集上界由 micro 规模档放开）。 */
    public static final RuinTemplate[] ALL = { AQUEDUCT_SPAN, TRUSS_FAN, KILN_STUMP, BOILER_LEAN, WATCH_BURIED,
        CHIMNEY_FAN, GALLERY_SPAN, PUMP_CHIP };

    static {
        for (final RuinTemplate t : ALL) {
            if (!t.name.startsWith(NAME_PREFIX)) {
                throw new IllegalStateException("[GTSR] ruin name outside family prefix: " + t.name);
            }
            if (t.layers.length != t.sizeY) {
                throw new IllegalStateException(
                    "[GTSR] ruin " + t.name + ": layer count " + t.layers.length + " != sizeY " + t.sizeY);
            }
            for (int y = 0; y < t.sizeY; y++) {
                if (t.layers[y].length != t.sizeZ) {
                    throw new IllegalStateException("[GTSR] ruin " + t.name + ": layer " + y + " row count");
                }
                for (int z = 0; z < t.sizeZ; z++) {
                    if (t.layers[y][z].length() != t.sizeX) {
                        throw new IllegalStateException(
                            "[GTSR] ruin " + t.name
                                + ": layer "
                                + y
                                + " row "
                                + z
                                + " length "
                                + t.layers[y][z].length()
                                + " != sizeX "
                                + t.sizeX);
                    }
                    for (int x = 0; x < t.sizeX; x++) {
                        final char c = t.charAt(y, x, z);
                        if (c == ' ' || c == '.') {
                            continue;
                        }
                        if (isForbiddenChar(c)) {
                            throw new IllegalStateException(
                                "[GTSR] ruin " + t.name
                                    + ": derived template still carries forbidden char '"
                                    + c
                                    + "'");
                        }
                        if (CityVariants.blockKeyOf(c) == null) {
                            throw new IllegalStateException(
                                "[GTSR] ruin " + t.name + ": char '" + c + "' has no existing block key");
                        }
                    }
                }
            }
            // 派生结果必须还"是一座结构"：残段太少等于放置器永远 commit(0)（判据 2 的假绿温床）
            if (t.solidChars() < 24) {
                throw new IllegalStateException(
                    "[GTSR] ruin " + t.name + ": derived silhouette too thin (solid=" + t.solidChars() + ")");
            }
            if (t.sizeX > 16 || t.sizeZ > 16 || t.sizeY > 12) {
                throw new IllegalStateException(
                    "[GTSR] ruin " + t.name
                        + ": exceeds single-chunk contract "
                        + t.sizeX
                        + "x"
                        + t.sizeY
                        + "x"
                        + t.sizeZ);
            }
        }
    }

    private RuinShapes() {}

    /** 废墟词汇的禁项（GT5U 两键 + 机型核心位）；真值在 {@link RuinDamageOps#FORBIDDEN_CHARS} 一处。 */
    public static boolean isForbiddenChar(char c) {
        for (final char f : RuinDamageOps.FORBIDDEN_CHARS) {
            if (f == c) {
                return true;
            }
        }
        return false;
    }

    /** 单模板的规模档（按 footprint 面积；0 小 / 1 中 / 2 大）。 */
    public static int scaleTier(RuinTemplate t) {
        final int area = t.sizeX * t.sizeZ;
        if (area <= 40) {
            return 0;
        }
        return area <= 90 ? 1 : 2;
    }

    /** micro 结构档允许的规模档上界（越界输入按最紧档处理，只会更保守、不会放大）。 */
    public static int allowedMaxScaleTier(int structuralTier) {
        if (structuralTier < 0 || structuralTier >= MICRO_TO_MAX_SCALE.length) {
            return 0;
        }
        return MICRO_TO_MAX_SCALE[structuralTier];
    }

    /** micro 强度 → 规模档上界的一行委托（放置器与机检都读它；本类内唯一读表点是上面那张表）。 */
    public static int allowedByMicro(float microStrength) {
        return allowedMaxScaleTier(RuinDamageOps.structuralTier(microStrength));
    }

    /** 规模档 ≤ 上界的候选模板（放置器的掷选域；上界 = {@link #MAX_SCALE_TIER} 时即全族）。 */
    public static RuinTemplate[] candidates(int maxScaleTier) {
        final List<RuinTemplate> out = new ArrayList<>();
        for (final RuinTemplate t : ALL) {
            if (scaleTier(t) <= maxScaleTier) {
                out.add(t);
            }
        }
        return out.toArray(new RuinTemplate[0]);
    }

    /** 按注册名取模板（未知名返回 null，与 {@code CityVariants.byName} 同口径）。 */
    public static RuinTemplate byName(String name) {
        for (final RuinTemplate t : ALL) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        return null;
    }

    /** 是否属于本族（按注册名前缀，不按 roster 里的 family 字段——机检两边都要读，这里给字符串口径）。 */
    public static boolean isRuinName(String name) {
        return name != null && name.startsWith(NAME_PREFIX);
    }
}
