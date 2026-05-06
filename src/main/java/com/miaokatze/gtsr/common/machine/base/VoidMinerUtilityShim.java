package com.miaokatze.gtsr.common.machine.base;

import java.lang.reflect.Field;
import java.util.Map;

import bwcrossmod.galacticgreg.VoidMinerUtility;

public class VoidMinerUtilityShim {

    private static Map<Integer, VoidMinerUtility.DropMap> dropMapsById = null;
    private static Map<Integer, VoidMinerUtility.DropMap> extraDropsById = null;
    private static Map<String, VoidMinerUtility.DropMap> dropMapsByName = null;
    private static Map<String, VoidMinerUtility.DropMap> extraDropsByName = null;
    private static boolean initialized = false;
    private static boolean useById = false;
    private static boolean useByName = false;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Field f = VoidMinerUtility.class.getDeclaredField("dropMapsByDimId");
            @SuppressWarnings("unchecked")
            Map<Integer, VoidMinerUtility.DropMap> map = (Map<Integer, VoidMinerUtility.DropMap>) f.get(null);
            dropMapsById = map;
            useById = true;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            dropMapsById = null;
        }
        try {
            Field f = VoidMinerUtility.class.getDeclaredField("extraDropsDimMap");
            @SuppressWarnings("unchecked")
            Map<Integer, VoidMinerUtility.DropMap> map = (Map<Integer, VoidMinerUtility.DropMap>) f.get(null);
            extraDropsById = map;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            extraDropsById = null;
        }
        try {
            Field f = VoidMinerUtility.class.getDeclaredField("dropMapsByDimName");
            @SuppressWarnings("unchecked")
            Map<String, VoidMinerUtility.DropMap> map = (Map<String, VoidMinerUtility.DropMap>) f.get(null);
            dropMapsByName = map;
            useByName = true;
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

    public static VoidMinerUtility.DropMap getDropMapById(int dimId) {
        init();
        if (useById && dropMapsById != null) {
            return dropMapsById.getOrDefault(dimId, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }

    public static VoidMinerUtility.DropMap getExtraDropMapById(int dimId) {
        init();
        if (useById && extraDropsById != null) {
            return extraDropsById.getOrDefault(dimId, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }

    public static VoidMinerUtility.DropMap getDropMap(String dimName) {
        init();
        if (useByName && dropMapsByName != null) {
            return dropMapsByName.getOrDefault(dimName, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }

    public static VoidMinerUtility.DropMap getExtraDropMap(String dimName) {
        init();
        if (useByName && extraDropsByName != null) {
            return extraDropsByName.getOrDefault(dimName, new VoidMinerUtility.DropMap());
        }
        return new VoidMinerUtility.DropMap();
    }
}
