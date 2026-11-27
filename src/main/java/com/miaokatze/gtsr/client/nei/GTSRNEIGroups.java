// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import static com.miaokatze.gtsr.common.api.enums.GTSRItemList.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;

import gregtech.api.GregTechAPI;

/** Client-only NEI presentation. Only stacks already supplied by the owned catalog can join a group. */
public final class GTSRNEIGroups {

    private static boolean registered;

    public enum Group {

        WEAPONS("weapons", PortableLM12, PortableT20, PortableQLZ04, PortableSingularity),
        MAGAZINES("magazines", Ammo762Pack, MimicAmmo762Pack, Ammo20Pack, MimicAmmo20Pack, Ammo35Pack, MimicAmmo35Pack),
        CHIPS("chips", VeinPyrolyzerChipT1, VeinPyrolyzerChipT2, VeinPyrolyzerChipT3, GeothermalOverheatChip,
            TcdsBoostChip, HubSingularityChip, ReinforcedHubSingularityChip, SteamTurbineCycleOverlimitChip,
            RareGasSeparationChip),
        CATALYSTS("catalysts", AmmoniaCatalystNickel, AmmoniaCatalystPlatinum, AmmoniaCatalystUranium,
            AmmoniaCatalystOsmium, AmmoniaCatalystFeCo, AmmoniaCatalystRuthenium, AmmoniaCatalystQuantum),
        MODULE_PACKS("module_packs"),
        MODULE_SHELLS("module_shells"),
        CRITICAL_CASINGS("critical_casings"),
        MACHINES("machines"),
        CRITICAL_MACHINES("critical_machines", CriticalLoom, CriticalSolar, CriticalTurbine, CriticalSun,
            CriticalBattery, CriticalProcessing, CriticalEntangler, CriticalDimension, CriticalAssembly,
            CriticalAccelerator),
        HATCHES("hatches", SteamInputHatchGeneric, PressureSteamHatch, SteamOutputHatchGeneric, SteamOutputHatch,
            PressureSteamOutputHatch, SteamCoolingHatch, PressureSteamCoolingHatch, DistilledWaterHatch,
            MegaAirInputHatch, OverpressureTurbineInputHatch, RedstoneHatch, SteamHubInputHatch, SteamHubOutputHatch,
            WaterHubInputHatch, WaterHubOutputHatch, HubStorageUnit, ReinforcedHubStorageUnit,
            OverpressureHubStorageUnit, SingularitySteamCompartment, SingularitySteamOutputCompartment,
            SingularityFluidInputCompartment, SingularityFluidOutputCompartment, CriticalInputHatchME,
            CriticalInputBusME, CriticalOutputHatchME, CriticalOutputBusME),
        HUBS_AND_NODES("hubs_and_nodes", SteamHubArray, WaterHubArray, SingularityDrillingHub, SteamCacheNode,
            ReinforcedSteamCacheNode, OverpressureSteamCacheNode, WaterCacheNode, ReinforcedWaterCacheNode,
            OverpressureWaterCacheNode, SingularityMinerNode, SingularityDrillingNode, HubTerminal),
        PROSPERITY_RELICS("prosperity_relics"),
        PROSPERITY_BLOCKS("prosperity_blocks");

        public final String id;
        private final GTSRItemList[] entries;

        Group(String id, GTSRItemList... entries) {
            this.id = id;
            this.entries = entries;
        }
    }

    private GTSRNEIGroups() {}

    public static synchronized void register() {
        if (registered) return;
        Map<Group, List<ItemStack>> groups = collect(GTSRItemOrderCatalog.orderedItems());
        NEICollapsibleCompat.register(groups);
        registered = true;
    }

    /** Preserve catalog order and NBT; no registry scan, generated placeholders or additional visible stacks. */
    public static Map<Group, List<ItemStack>> collect(List<ItemStack> catalog) {
        Map<Group, List<ItemStack>> result = new EnumMap<>(Group.class);
        for (ItemStack stack : catalog) {
            Group group = groupOf(stack);
            if (group != null) result.computeIfAbsent(group, ignored -> new ArrayList<>())
                .add(stack.copy());
        }
        return result;
    }

    public static Group groupOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        for (Group group : Group.values()) {
            for (GTSRItemList entry : group.entries) if (matches(entry, stack)) return group;
        }
        Item item = stack.getItem();
        if (item instanceof CenterModuleItems) {
            if (CenterModuleItems.moduleOf(stack) != null) return Group.MODULE_PACKS;
            if (CenterModuleItems.isShell(stack)) return Group.MODULE_SHELLS;
            return null;
        }
        if (Block.getBlockFromItem(item) instanceof CriticalMaterials.MaterialBlock) return Group.CRITICAL_CASINGS;
        if (LoreRegistry.RELICS.containsValue(item)) return Group.PROSPERITY_RELICS;
        if (item == Item.getItemFromBlock(GregTechAPI.sBlockMachines)) {
            for (GTSRItemList entry : GTSRItemList.values()) if (matches(entry, stack)) return Group.MACHINES;
            return null;
        }
        String name = Item.itemRegistry.getNameForObject(item);
        if (item instanceof ItemBlock && name != null
            && name.startsWith("gtsr:")
            && GTSRItemOrderCatalog.isDimensionItem(stack)) return Group.PROSPERITY_BLOCKS;
        return null;
    }

    private static boolean matches(GTSRItemList entry, ItemStack stack) {
        return entry.hasBeenSet() && entry.getItem() == stack.getItem() && entry.getMeta() == stack.getItemDamage();
    }
}
