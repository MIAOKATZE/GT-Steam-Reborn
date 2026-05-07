package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer;
import com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class MachineLoader {

    public static void initMachines() {
        registerSteamMachines();
        registerSteamHubMachines();
        registerSteamFluidDrill();
        registerCrustSteamBorers();
        registerPressureSteamHatch();
        registerVeinSteamPyrolyzer();
    }

    private static void registerSteamMachines() {
        GTSRItemList.SteamCacheNode.set(
            new MTESteamCacheNode(MetaTileEntityID.STEAM_CACHE_NODE.ID, "gtsr.steam.cache.node", "Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamCacheNode.get(1));

        GTSRItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsr.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedSteamCacheNode.get(1));
    }

    private static void registerSteamHubMachines() {
        GTSRItemList.SteamHubOutputHatch.set(
            new MTESteamHubOutputHatch(
                MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID,
                "gtsr.steam.hub.output.hatch",
                "Steam Hub Output Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubOutputHatch.get(1));

        GTSRItemList.SteamHubInputHatch.set(
            new MTESteamHubInputHatch(
                MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID,
                "gtsr.steam.hub.input.hatch",
                "Steam Hub Input Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubInputHatch.get(1));

        GTSRItemList.PressureSteamStorageUnit.set(
            new MTEPressureSteamStorageUnit(
                MetaTileEntityID.PRESSURE_STEAM_STORAGE_UNIT.ID,
                "gtsr.pressure.steam.storage.unit",
                "Pressure Steam Storage Unit"));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamStorageUnit.get(1));

        GTSRItemList.ReinforcedSteamStorageUnit.set(
            new MTEReinforcedSteamStorageUnit(
                MetaTileEntityID.REINFORCED_STEAM_STORAGE_UNIT.ID,
                "gtsr.reinforced.steam.storage.unit",
                "Reinforced Steam Storage Unit"));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedSteamStorageUnit.get(1));

        GTSRItemList.SteamHubArray
            .set(new MTESteamHubArray(MetaTileEntityID.STEAM_HUB_ARRAY.ID, "gtsr.steam.hub.array", "Steam Hub Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubArray.get(1));
    }

    private static void registerSteamFluidDrill() {
        GTSRItemList.SteamFluidDrill.set(
            new MTESteamFluidDrill(
                MetaTileEntityID.STEAM_FLUID_DRILL.ID,
                "gtsr.steam.fluid.drill",
                "Steam Fluid Drill"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamFluidDrill.get(1));
    }

    private static void registerCrustSteamBorers() {
        GTSRItemList.CrustSteamBorer.set(
            new MTECrustSteamBorer(
                MetaTileEntityID.CRUST_STEAM_BORER.ID,
                "gtsr.crust.steam.borer",
                "Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSRItemList.CrustSteamBorer.get(1));

        GTSRItemList.VoidCrustSteamBorer.set(
            new MTEVoidCrustSteamBorer(
                MetaTileEntityID.VOID_CRUST_STEAM_BORER.ID,
                "gtsr.void.crust.steam.borer",
                "Void Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSRItemList.VoidCrustSteamBorer.get(1));
    }

    private static void registerPressureSteamHatch() {
        GTSRItemList.PressureSteamHatch.set(
            new MTEHatchPressureSteamInput(
                MetaTileEntityID.PRESSURE_STEAM_HATCH.ID,
                "gtsr.pressure.steam.hatch",
                "Pressure Steam Hatch",
                0));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamHatch.get(1));
    }

    private static void registerVeinSteamPyrolyzer() {
        GTSRItemList.VeinSteamPyrolyzer.set(
            new MTEVeinSteamPyrolyzer(
                MetaTileEntityID.VEIN_STEAM_PYROLYZER.ID,
                "gtsr.vein.steam.pyrolyzer",
                "Vein Steam Pyrolyzer"));
        CreativeTabManager.addItemToTab(GTSRItemList.VeinSteamPyrolyzer.get(1));
    }
}
