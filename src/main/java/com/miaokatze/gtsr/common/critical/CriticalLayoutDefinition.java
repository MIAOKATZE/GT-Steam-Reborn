package com.miaokatze.gtsr.common.critical;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Frozen layout selection and structural roles, independent of a controller's mutable runtime. */
public final class CriticalLayoutDefinition {

    public static final int V1_CONTROLLER_X = 40, V1_CONTROLLER_Y = 0, V1_CONTROLLER_Z = 33;
    public static final int V1_SHAPE_MAX = 76;
    public static final int V1_LOOM_TOP = 35, V1_LOOM_MIN_Z = 32, V1_LOOM_MAX_X = 47;
    public static final int V1_LOOM_SIZE_X = 15, V1_LOOM_SIZE_Z = 16;
    public static final int V1_LOOM_CONTROLLER_A = 7, V1_LOOM_CONTROLLER_C = 1;
    public static final int V2_SIZE = 112;
    public static final int V2_CONTROLLER_X = 56, V2_CONTROLLER_Y = 4;
    public static final int V2_MAIN_Z = 35, V2_LOOM_Z = 47;

    public enum Family {
        V1,
        LEGACY_V2,
        V25,
        V27,
        V30,
        CURRENT
    }

    private static final Map<String, CriticalLayoutDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    public final Family family;
    public final CriticalTier tier;
    public final CriticalMachineKind kind;
    public final String loomSlug, baseSlug, machineSlug;
    public final boolean v2, legacy, frozenPlugins, optionalWorkPorts;
    public final int frameSize, frameHeight;
    private final boolean separatePaidPorts;
    private final String cacheKey;

    private CriticalLayoutDefinition(Family family, boolean optionalWorkPorts, CriticalTier tier,
        CriticalMachineKind kind) {
        this.tier = tier;
        this.kind = kind;
        this.family = family;
        v2 = family != Family.V1;
        legacy = family == Family.LEGACY_V2;
        String prefix = switch (family) {
            case V1 -> "";
            case LEGACY_V2 -> "v2_legacy_";
            case V25 -> "v2_v25_";
            case V27 -> "v2_v27_";
            case V30 -> "v2_v30_";
            case CURRENT -> "v2_";
        };
        loomSlug = prefix + "loom";
        baseSlug = v2 ? prefix + "base_t3"
            : "base_" + tier.name()
                .toLowerCase(Locale.ROOT);
        frozenPlugins = v2 && family != Family.CURRENT;
        machineSlug = v2
            ? frozenPlugins ? kind == CriticalMachineKind.SOLAR ? "v2_v30_solar" : "v2_v30_sun" : "v2_" + kind.key
            : kind.name()
                .toLowerCase(Locale.ROOT);
        this.optionalWorkPorts = optionalWorkPorts;
        separatePaidPorts = optionalWorkPorts;
        frameSize = v2 ? V2_SIZE : 80;
        frameHeight = v2 ? 180 : 162;
        cacheKey = family + ":" + tier + ":" + kind + ":" + optionalWorkPorts;
    }

    public static CriticalLayoutDefinition forState(CriticalMachineState state) {
        return select(
            state.getGeometryVersion(),
            state.getV2LayoutRevision(),
            state.usesLegacyV2Geometry(),
            state.getTier(),
            state.getKind());
    }

    public static CriticalLayoutDefinition select(int geometryVersion, int revision, boolean legacy, CriticalTier tier,
        CriticalMachineKind kind) {
        Family family = geometryVersion != 2 ? Family.V1
            : legacy ? Family.LEGACY_V2
                : revision == 25 ? Family.V25
                    : revision <= 27 ? Family.V27 : revision <= 30 ? Family.V30 : Family.CURRENT;
        boolean optionalPorts = geometryVersion == 2 && revision >= 30;
        String key = family + ":" + tier + ":" + kind + ":" + optionalPorts;
        return DEFINITIONS
            .computeIfAbsent(key, ignored -> new CriticalLayoutDefinition(family, optionalPorts, tier, kind));
    }

    public String cacheKey() {
        return cacheKey;
    }

    public String pluginSlug(String pluginKind) {
        return (frozenPlugins ? "v2_v30_plugin_" : "v2_plugin_") + pluginKind;
    }

    public int controllerX() {
        return v2 ? V2_CONTROLLER_X : V1_CONTROLLER_X;
    }

    public int controllerY() {
        return v2 ? V2_CONTROLLER_Y : V1_CONTROLLER_Y;
    }

    public int controllerZ(boolean selector) {
        return v2 ? selector ? V2_LOOM_Z : V2_MAIN_Z : V1_CONTROLLER_Z;
    }

    public boolean isHatch(CriticalGeometry.Voxel voxel) {
        return isOptionalWorkPort(voxel)
            || isHatchPosition(voxel) && !(separatePaidPorts && !isLoom(voxel) && paidRow(voxel));
    }

    public static boolean isLoom(CriticalGeometry.Voxel voxel) {
        return switch (voxel.model) {
            case "loom", "v2_loom", "v2_v25_loom", "v2_v27_loom", "v2_v30_loom", "v2_legacy_loom" -> true;
            default -> false;
        };
    }

    public static boolean isController(CriticalGeometry.Voxel voxel) {
        return voxel.model.startsWith("v2_") && voxel.x == V2_CONTROLLER_X
            && voxel.y == V2_CONTROLLER_Y
            && (voxel.z == V2_MAIN_Z || voxel.z == V2_LOOM_Z);
    }

    /** Historical material and coordinate rules; these deliberately do not infer roles from metadata. */
    public static boolean isHatchPosition(CriticalGeometry.Voxel voxel) {
        if (isOptionalWorkPort(voxel)) return true;
        if (voxel.model.startsWith("v2_")) {
            if (isController(voxel) || voxel.model.startsWith("v2_plugin_") || voxel.model.startsWith("v2_v30_plugin_"))
                return false;
            if (isLoom(voxel) && !"v2_legacy_loom".equals(voxel.model)) return voxel.y == 4
                && (voxel.z == 47 && (voxel.x == 50 || voxel.x == 53 || voxel.x == 59 || voxel.x == 62)
                    || "static".equals(voxel.group) && voxel.x >= 47
                        && voxel.x <= 65
                        && voxel.z >= 47
                        && voxel.z <= 65
                        && voxel.block instanceof CriticalMaterials.MaterialBlock shell
                        && "v2_reinforced_panel".equals(shell.materialId));
            return voxel.block instanceof CriticalMaterials.MaterialBlock block
                && ("v2_alloy_frame".equals(block.materialId) || "v2_service_hatch".equals(block.materialId)
                    || "v2_gt_hatch".equals(block.materialId))
                || "v2_legacy_loom".equals(voxel.model) && voxel.y == 4
                    && voxel.z == 36
                    && (voxel.x == 50 || voxel.x == 53 || voxel.x == 59 || voxel.x == 62);
        }
        return voxel.block instanceof CriticalMaterials.MaterialBlock material
            && "alloy_frame".equals(material.materialId);
    }

    public static boolean isOptionalWorkPort(CriticalGeometry.Voxel voxel) {
        return "v2_optional_work_port".equals(voxel.model) && voxel.y == 5
            && voxel.z == 34
            && voxel.x >= 50
            && voxel.x <= 62;
    }

    public static boolean isPaidChamber(CriticalGeometry.Voxel voxel) {
        return isOptionalWorkPort(voxel) || !isLoom(voxel) && isHatchPosition(voxel) && paidRow(voxel);
    }

    private static boolean paidRow(CriticalGeometry.Voxel voxel) {
        return voxel.y == 4 && voxel.z == 34 && voxel.x >= 50 && voxel.x <= 62;
    }
}
