package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import bwcrossmod.galacticgreg.VoidMinerUtility;

/**
 * 跨 mod 反射 shim：读取 GalacticGreg {@link VoidMinerUtility} 的维度掉落表静态字段。
 * SR-BUG-05 修复口径：反射查找仅执行一次（{@code initialized} 先置位再反射，失败缓存为 null、
 * 永不重试，读点 {@link #getDropMap}/{@link #getExtraDropMap} 恒返回空 DropMap 安全默认）；
 * 失败分支补一次性 warn（静态布尔防重复，含失败目标字段与后果），消除上游签名漂移后的静默退化。
 */
public class VoidMinerUtilityShim {

    // [GT-compat] beta 兼容层（beta1/beta2/beta3）：GTLog.err/GTMod.GT_FML_LOGGER 于 beta-3 移除，改用环境 log4j2（三版本通用）
    private static final Logger LOG = LogManager.getLogger("gtsr");

    private static Map<String, VoidMinerUtility.DropMap> dropMapsByName = null;
    private static Map<String, VoidMinerUtility.DropMap> extraDropsByName = null;
    private static boolean initialized = false;
    // SR-BUG-05：失败告警一次性开关（init 本身单次执行，此布尔为日志防重复兜底）
    private static boolean initFailureLogged = false;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        dropMapsByName = readDropMapField("dropMapsByDimName");
        extraDropsByName = readDropMapField("extraDropsByDimName");
        if (dropMapsByName == null || extraDropsByName == null) {
            warnInitFailureOnce();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, VoidMinerUtility.DropMap> readDropMapField(String fieldName) {
        try {
            Field f = VoidMinerUtility.class.getDeclaredField(fieldName);
            return (Map<String, VoidMinerUtility.DropMap>) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // SR-BUG-05：失败态由外层缓存（null + initialized）不再重试；此处仅返回 null，由 warnInitFailureOnce 统一告警
            return null;
        }
    }

    /**
     * SR-BUG-05：init 失败的一次性告警（静态布尔防重复）。失败即缓存失败态，此后不再重试；
     * 后果：受影响维度的虚空矿掉落表恒为空（不掉矿/无额外掉落），无崩溃、仅静默退化。
     * 典型成因是 GalacticGreg 升级后字段改名/重构，需同步更新本 shim 的字段名。
     */
    private static synchronized void warnInitFailureOnce() {
        if (initFailureLogged) return;
        initFailureLogged = true;
        // [GT-compat] beta 兼容层（beta1/beta2/beta3）：GTLog.err/GTMod.GT_FML_LOGGER 于 beta-3 移除，改用环境 log4j2（三版本通用）
        LOG.warn(
            "[GTSR] VoidMinerUtilityShim failed to read GalacticGreg VoidMinerUtility drop tables"
                + " (dropMapsByDimName present: "
                + (dropMapsByName != null)
                + ", extraDropsByDimName present: "
                + (extraDropsByName != null)
                + "). Affected dimensions will drop nothing / lose extra drops (silent degradation, no crash;"
                + " no retry until restart). Likely a GalacticGreg version change - update the shim field names.");
    }

    /**
     * Converts a dimension ID to the corresponding dimension name used by VoidMinerUtility.
     * v1.10.61：扩展 GTNH 常见维度（维度名以 MTECrustMatterAggregator.ABBR_TO_DIM_NAME 表为准）；
     * 其余 GTNH 维度（星系行星等无固定 dimId）经 GTNEIOrePlugin 维度物品的 abbr → dimName 映射访问。
     *
     * @param dimId the dimension ID
     * @return the dimension name string, or null if the dimension is not recognized
     */
    public static String dimIdToName(int dimId) {
        switch (dimId) {
            case 0:
                return "Overworld";
            case -1:
                return "Nether";
            case 1:
                return "The End";
            case 7:
                return "Twilight Forest";
            case -7:
                return "Underdark";
            default:
                return null;
        }
    }

    public static VoidMinerUtility.DropMap getDropMap(String dimName) {
        init();
        if (dropMapsByName != null && dimName != null) {
            return dropMapsByName.getOrDefault(dimName, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }

    public static VoidMinerUtility.DropMap getExtraDropMap(String dimName) {
        init();
        if (extraDropsByName != null && dimName != null) {
            return extraDropsByName.getOrDefault(dimName, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }
}
