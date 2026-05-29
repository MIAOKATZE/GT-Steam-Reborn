package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.AmmoniaCatalyst;
import com.miaokatze.gtsr.common.items.GeothermalOverheatChip;
import com.miaokatze.gtsr.common.items.HubSingularityChip;
import com.miaokatze.gtsr.common.items.RareGasSeparationChip;
import com.miaokatze.gtsr.common.items.SteamEntangledSingularity;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;

public class ItemLoader {

    public static void initItems() {
        registerPyrolyzerChips();
        registerGeothermalOverheatChip();
        registerHubSingularityChip();
        registerRareGasSeparationChip();
        registerAmmoniaCatalysts();
        registerSteamEntangledSingularity();
    }

    private static void registerPyrolyzerChips() {
        GTSRItemList.VeinPyrolyzerChipT1
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChip_T1", 1), "VeinPyrolyzerChipT1", true);

        GTSRItemList.VeinPyrolyzerChipT2
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChip_T2", 3), "VeinPyrolyzerChipT2", true);

        GTSRItemList.VeinPyrolyzerChipT3
            .setAndRegister(new VeinPyrolyzerChip("VeinPyrolyzerChip_T3", 7), "VeinPyrolyzerChipT3", true);
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

    private static void registerAmmoniaCatalysts() {
        GTSRItemList.AmmoniaCatalystNickel
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystNickel"), "AmmoniaCatalystNickel", true);

        GTSRItemList.AmmoniaCatalystPlatinum
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystPlatinum"), "AmmoniaCatalystPlatinum", true);

        GTSRItemList.AmmoniaCatalystUranium
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystUranium"), "AmmoniaCatalystUranium", true);

        GTSRItemList.AmmoniaCatalystOsmium
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystOsmium"), "AmmoniaCatalystOsmium", true);

        GTSRItemList.AmmoniaCatalystFeCo
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystFeCo"), "AmmoniaCatalystFeCo", true);

        GTSRItemList.AmmoniaCatalystRuthenium
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystRuthenium"), "AmmoniaCatalystRuthenium", true);

        GTSRItemList.AmmoniaCatalystQuantum
            .setAndRegister(new AmmoniaCatalyst("AmmoniaCatalystQuantum"), "AmmoniaCatalystQuantum", true);
    }

    private static void registerRareGasSeparationChip() {
        GTSRItemList.RareGasSeparationChip.setAndRegister(new RareGasSeparationChip(), "RareGasSeparationChip", true);
    }
}
