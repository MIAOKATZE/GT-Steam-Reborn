package com.miaokatze.gtsgu.loader;

import com.miaokatze.gtsgu.common.api.enums.GTSGUItemList;
import com.miaokatze.gtsgu.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsgu.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsgu.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsgu.common.machine.MTESteamHubArray;
import com.miaokatze.gtsgu.common.machine.MTEVoidCrustSteamBorer;
import com.miaokatze.gtsgu.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsgu.common.machine.base.MTEPressureSteamStorageUnit;
import com.miaokatze.gtsgu.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsgu.common.machine.base.MTEReinforcedSteamStorageUnit;
import com.miaokatze.gtsgu.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsgu.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsgu.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsgu.register.CreativeTabManager;

public class MachineLoader {

    public static void initMachines() {
        registerSteamMachines();
        registerSteamHubMachines();
        registerSteamFluidDrill();
        registerCrustSteamBorers();
        registerPressureSteamHatch();
    }

    private static void registerSteamMachines() {
        GTSGUItemList.SteamCacheNode.set(
            new MTESteamCacheNode(MetaTileEntityID.STEAM_CACHE_NODE.ID, "gtsgu.steam.cache.node", "Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamCacheNode.get(1));

        GTSGUItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsgu.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSGUItemList.ReinforcedSteamCacheNode.get(1));
    }

    private static void registerSteamHubMachines() {
        GTSGUItemList.SteamHubOutputHatch.set(
            new MTESteamHubOutputHatch(
                MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID,
                "gtsgu.steam.hub.output.hatch",
                "Steam Hub Output Hatch"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamHubOutputHatch.get(1));

        GTSGUItemList.SteamHubInputHatch.set(
            new MTESteamHubInputHatch(
                MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID,
                "gtsgu.steam.hub.input.hatch",
                "Steam Hub Input Hatch"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamHubInputHatch.get(1));

        GTSGUItemList.PressureSteamStorageUnit.set(
            new MTEPressureSteamStorageUnit(
                MetaTileEntityID.PRESSURE_STEAM_STORAGE_UNIT.ID,
                "gtsgu.pressure.steam.storage.unit",
                "Pressure Steam Storage Unit"));
        CreativeTabManager.addItemToTab(GTSGUItemList.PressureSteamStorageUnit.get(1));

        GTSGUItemList.ReinforcedSteamStorageUnit.set(
            new MTEReinforcedSteamStorageUnit(
                MetaTileEntityID.REINFORCED_STEAM_STORAGE_UNIT.ID,
                "gtsgu.reinforced.steam.storage.unit",
                "Reinforced Steam Storage Unit"));
        CreativeTabManager.addItemToTab(GTSGUItemList.ReinforcedSteamStorageUnit.get(1));

        GTSGUItemList.SteamHubArray
            .set(new MTESteamHubArray(MetaTileEntityID.STEAM_HUB_ARRAY.ID, "gtsgu.steam.hub.array", "Steam Hub Array"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamHubArray.get(1));
    }

    private static void registerSteamFluidDrill() {
        GTSGUItemList.SteamFluidDrill.set(
            new MTESteamFluidDrill(
                MetaTileEntityID.STEAM_FLUID_DRILL.ID,
                "gtsgu.steam.fluid.drill",
                "Steam Fluid Drill"));
        CreativeTabManager.addItemToTab(GTSGUItemList.SteamFluidDrill.get(1));
    }

    private static void registerCrustSteamBorers() {
        GTSGUItemList.CrustSteamBorer.set(
            new MTECrustSteamBorer(
                MetaTileEntityID.CRUST_STEAM_BORER.ID,
                "gtsgu.crust.steam.borer",
                "Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSGUItemList.CrustSteamBorer.get(1));

        GTSGUItemList.VoidCrustSteamBorer.set(
            new MTEVoidCrustSteamBorer(
                MetaTileEntityID.VOID_CRUST_STEAM_BORER.ID,
                "gtsgu.void.crust.steam.borer",
                "Void Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSGUItemList.VoidCrustSteamBorer.get(1));
    }

    private static void registerPressureSteamHatch() {
        GTSGUItemList.PressureSteamHatch.set(
            new MTEHatchPressureSteamInput(
                MetaTileEntityID.PRESSURE_STEAM_HATCH.ID,
                "gtsgu.pressure.steam.hatch",
                "Pressure Steam Hatch",
                0));
        CreativeTabManager.addItemToTab(GTSGUItemList.PressureSteamHatch.get(1));
    }
}
