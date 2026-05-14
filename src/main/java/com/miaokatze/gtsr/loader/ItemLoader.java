package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.GeothermalOverheatChip;
import com.miaokatze.gtsr.common.items.HubSingularityChip;
import com.miaokatze.gtsr.common.items.SteamEntangledSingularity;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;

public class ItemLoader {

    public static void initItems() {
        registerPyrolyzerChips();
        registerGeothermalOverheatChip();
        registerHubSingularityChip();
        registerSteamEntangledSingularity();
    }

    private static void registerPyrolyzerChips() {
        GTSRItemList.VeinPyrolyzerChipT1
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT1", 1), "VeinPyrolyzerChipT1", true);

        GTSRItemList.VeinPyrolyzerChipT2
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT2", 3), "VeinPyrolyzerChipT2", true);

        GTSRItemList.VeinPyrolyzerChipT3
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChipT3", 7), "VeinPyrolyzerChipT3", true);
    }

    private static void registerGeothermalOverheatChip() {
        GTSRItemList.GeothermalOverheatChip
            .setAndRegister(new GeothermalOverheatChip("GeothermalOverheatChip"), "GeothermalOverheatChip", true);
    }

    private static void registerHubSingularityChip() {
        GTSRItemList.HubSingularityChip.setAndRegister(new HubSingularityChip(), "HubSingularityChip", true);
    }

    private static void registerSteamEntangledSingularity() {
        GTSRItemList.SteamEntangledSingularity
            .setAndRegister(new SteamEntangledSingularity(), "SteamEntangledSingularity", true);
    }
}
