package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;
import java.util.Map;

import bwcrossmod.galacticgreg.VoidMinerUtility;

public class VoidMinerUtilityShim {

    private static Map<String, VoidMinerUtility.DropMap> dropMapsByName = null;
    private static Map<String, VoidMinerUtility.DropMap> extraDropsByName = null;
    private static boolean initialized = false;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Field f = VoidMinerUtility.class.getDeclaredField("dropMapsByDimName");
            @SuppressWarnings("unchecked")
            Map<String, VoidMinerUtility.DropMap> map = (Map<String, VoidMinerUtility.DropMap>) f.get(null);
            dropMapsByName = map;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            dropMapsByName = null;
        }
        try {
            Field f = VoidMinerUtility.class.getDeclaredField("extraDropsByDimName");
            @SuppressWarnings("unchecked")
            Map<String, VoidMinerUtility.DropMap> map = (Map<String, VoidMinerUtility.DropMap>) f.get(null);
            extraDropsByName = map;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            extraDropsByName = null;
        }
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
