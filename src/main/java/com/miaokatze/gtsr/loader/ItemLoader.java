package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.items.AmmoniaCatalyst;
import com.miaokatze.gtsr.common.items.CriticalSteamEntangledSingularity;
import com.miaokatze.gtsr.common.items.GeothermalOverheatChip;
import com.miaokatze.gtsr.common.items.HubSingularityChip;
import com.miaokatze.gtsr.common.items.HubTerminal;
import com.miaokatze.gtsr.common.items.ItemAbyssalBucket;
import com.miaokatze.gtsr.common.items.RareGasSeparationChip;
import com.miaokatze.gtsr.common.items.ReinforcedHubSingularityChip;
import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.items.SteamEntangledSingularity;
import com.miaokatze.gtsr.common.items.SteamTurbineCycleOverlimitChip;
import com.miaokatze.gtsr.common.items.TCDSDenseSteamChip;
import com.miaokatze.gtsr.common.items.VeinPyrolyzerChip;
import com.miaokatze.gtsr.main.GTSteamReborn;

public class ItemLoader {

    public static void initItems() {
        registerPyrolyzerChips();
        registerGeothermalOverheatChip();
        registerTcdsDenseSteamChip();
        registerHubSingularityChip();
        registerReinforcedHubSingularityChip();
        registerSteamTurbineCycleOverlimitChip();
        registerRareGasSeparationChip();
        registerAmmoniaCatalysts();
        registerSteamEntangledSingularity();
        registerCriticalSteamEntangledSingularity();
        registerHubTerminal();
        registerSingularityTuningWand();
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

    private static void registerTcdsDenseSteamChip() {
        GTSRItemList.TcdsBoostChip.setAndRegister(new TCDSDenseSteamChip("TcdsBoostChip"), "TcdsBoostChip", true);
    }

    private static void registerHubSingularityChip() {
        GTSRItemList.HubSingularityChip.setAndRegister(new HubSingularityChip(), "HubSingularityChip", true);
    }

    private static void registerReinforcedHubSingularityChip() {
        GTSRItemList.ReinforcedHubSingularityChip
            .setAndRegister(new ReinforcedHubSingularityChip(), "ReinforcedHubSingularityChip", true);
    }

    private static void registerSteamTurbineCycleOverlimitChip() {
        GTSRItemList.SteamTurbineCycleOverlimitChip
            .setAndRegister(new SteamTurbineCycleOverlimitChip(), "SteamTurbineCycleOverlimitChip", true);
    }

    private static void registerSteamEntangledSingularity() {
        GTSRItemList.SteamEntangledSingularity
            .setAndRegister(new SteamEntangledSingularity(), "SteamEntangledSingularity", true);
    }

    private static void registerCriticalSteamEntangledSingularity() {
        GTSRItemList.CriticalSteamEntangledSingularity
            .setAndRegister(new CriticalSteamEntangledSingularity(), "CriticalSteamEntangledSingularity", true);
    }

    private static void registerHubTerminal() {
        GTSRItemList.HubTerminal.setAndRegister(new HubTerminal(), "HubTerminal", true);
    }

    private static void registerSingularityTuningWand() {
        GTSRItemList.SingularityTuningWand.setAndRegister(new SingularityTuningWand(), "SingularityTuningWand", true);
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

    /**
     * 深渊执念桶注册（v1.20.40 P19-U1，plan §I）——<b>init 段调用</b>（CommonProxy.init，
     * 紧随 {@code BlockLoader.initAbyssalFluid()}）：桶 isFull 依赖 BlocksGTSR.abyssalFluid
     * （init 段才构造，见其时序注释），且容器互认需 FluidRegistry 已含 abyssal_obsession。
     * setAndRegister 自带 GameRegistry.registerItem + 创造页签（GTSRItemList.java:223-236）。
     * 容器互认（1000mB ↔ 原版空桶，FluidContainerRegistry.java:100 三参重载）使桶可被通用
     * 流体管道/容器灌装与抽取。流体缺失（材料链降级）时跳过并告警，零崩溃面。
     */
    public static void initAbyssalBucket() {
        if (BlocksGTSR.abyssalFluid == null) {
            GTSteamReborn.LOG.warn("[GTSR] abyssal fluid block missing, abyssal bucket registration skipped");
            return;
        }
        GTSRItemList.AbyssalObsessionBucket
            .setAndRegister(new ItemAbyssalBucket(BlocksGTSR.abyssalFluid), "AbyssalObsessionBucket", true);
        final net.minecraftforge.fluids.Fluid fluid = ((net.minecraftforge.fluids.IFluidBlock) BlocksGTSR.abyssalFluid)
            .getFluid();
        if (fluid != null) {
            net.minecraftforge.fluids.FluidContainerRegistry.registerFluidContainer(
                new net.minecraftforge.fluids.FluidStack(fluid, 1000),
                GTSRItemList.AbyssalObsessionBucket.get(1),
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.bucket));
        }
        GTSteamReborn.LOG.info("[GTSR] abyssal bucket registered: AbyssalObsessionBucket container=1000mB");
    }
}
