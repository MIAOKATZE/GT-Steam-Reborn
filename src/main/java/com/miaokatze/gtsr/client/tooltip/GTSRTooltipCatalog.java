package com.miaokatze.gtsr.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalMaterialCatalog;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemOathFragment;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityJournal;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityRelic;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.FictionPedestalBlock;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlock;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/** Additive descriptions. Registration identities, never names or ore aliases, establish ownership. */
public final class GTSRTooltipCatalog {

    private static final String PREFIX = "gtsr.tooltip.catalog.";
    private static final Map<Item, Map<Integer, Generated>> GENERATED = new IdentityHashMap<>();
    private static final Map<Item, Set<Integer>> MACHINES = new IdentityHashMap<>();

    private static final class Generated {

        private final Fluid fluid;
        private final String key, form;

        private Generated(Fluid fluid, String key, String form) {
            this.fluid = fluid;
            this.key = key;
            this.form = form;
        }
    }

    private GTSRTooltipCatalog() {}

    /** Called after GT has generated its materials. The authoritative NEI catalog supplies the exact stacks. */
    public static void initialize() {
        GENERATED.clear();
        MACHINES.clear();
        for (GTSRItemList entry : GTSRItemList.values()) {
            if (!entry.hasBeenSet() || entry.getItem() == null) continue;
            MACHINES.computeIfAbsent(entry.getItem(), item -> new java.util.HashSet<>())
                .add(entry.getMeta());
        }
        for (ItemStack stack : GTSRItemOrderCatalog.generatedDimensionItems()) {
            Fluid fluid = null;
            String key;
            String form;
            if (ItemList.Display_Fluid.hasBeenSet() && stack.getItem() == ItemList.Display_Fluid.getItem()) {
                FluidStack decoded = GTUtility.getFluidFromDisplayStack(stack);
                if (decoded == null) continue;
                fluid = decoded.getFluid();
                key = fluidPurpose(fluid);
                form = "fluid_display";
            } else {
                ItemData data = GTOreDictUnificator.getItemData(stack);
                if (data == null || data.mMaterial == null) continue;
                key = materialPurpose(data.mMaterial.mMaterial);
                form = data.mPrefix == OrePrefixes.dustTiny ? "dust_tiny"
                    : data.mPrefix == OrePrefixes.dustSmall ? "dust_small"
                        : data.mPrefix == OrePrefixes.dust ? "dust" : "cell";
            }
            GENERATED.computeIfAbsent(stack.getItem(), item -> new HashMap<>())
                .put(stack.getItemDamage(), new Generated(fluid, key, form));
        }
    }

    private static Materials[] airMaterials() {
        return new Materials[] { GTSRProsperityAirMaterials.WastesSigh, GTSRProsperityAirMaterials.ThickGrease,
            GTSRProsperityAirMaterials.MetalGrit, GTSRProsperityAirMaterials.UmbralMire,
            GTSRProsperityAirMaterials.SanzuResidualSteam, GTSRProsperityAirMaterials.WitheredBreath };
    }

    private static String materialPurpose(Materials material) {
        if (material == GTSRProsperityAirMaterials.AbyssalObsession) return "abyssal_material";
        for (Materials air : airMaterials()) if (air != null && air == material) return "raw_gas";
        return "industrial_material";
    }

    private static String fluidPurpose(Fluid fluid) {
        Materials abyssal = GTSRProsperityAirMaterials.AbyssalObsession;
        if (abyssal != null && sameFluid(abyssal.getFluid(1), fluid)) return "abyssal_material";
        for (Materials air : airMaterials()) if (air != null && sameFluid(air.getGas(1), fluid)) return "raw_gas";
        return "industrial_material";
    }

    private static boolean sameFluid(FluidStack stack, Fluid fluid) {
        return stack != null && stack.getFluid() == fluid;
    }

    /** Null means foreign: the event must leave every original line untouched. Empty means brand only. */
    public static List<String> descriptionKeys(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Map<Integer, Generated> forms = GENERATED.get(stack.getItem());
        Generated generated = forms == null ? null : forms.get(stack.getItemDamage());
        if (generated != null) {
            if (generated.fluid != null) {
                // Display NBT is not an ownership hint: decode the real GT item and verify its fluid ID too.
                FluidStack decoded = GTUtility.getFluidFromDisplayStack(stack);
                if (stack.getItem() != ItemList.Display_Fluid.getItem() || decoded == null
                    || decoded.getFluid() != generated.fluid
                    || stack.getItemDamage() != generated.fluid.getID()) return null;
            }
            return keys(generated.key, generated.form);
        }
        GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (id == null || !"gtsr".equals(id.modId)) {
            Set<Integer> metas = MACHINES.get(stack.getItem());
            return metas != null && metas.contains(stack.getItemDamage()) ? Collections.emptyList() : null;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block != null && block != net.minecraft.init.Blocks.air) return blockKeys(block, id.name);
        if (stack.getItem() instanceof ItemProsperityJournal) return keys("journal");
        if (stack.getItem() instanceof ItemOathFragment) return keys("oath_fragment");
        if (stack.getItem() instanceof ItemProsperityRelic) {
            return keys(stack.getItem() == LoreRegistry.RELICS.get(NavigatorNetwork.RELIC_ID) ? "navigator" : "relic");
        }
        return switch (id.name) {
            case "IrradiatedMoonDust" -> keys("irradiated_moon", "irradiated_unused");
            case "IrradiatedStoneDust" -> keys("irradiated_stone", "irradiated_unused");
            case "SingularityTuningWand" -> keys("debug_permission");
            case "AbyssalObsessionBucket" -> keys("abyssal_bucket", "abyssal_fallback");
            // Existing chip, catalyst, beacon, terminal and weapon classes retain their actual use/NBT text.
            default -> Collections.emptyList();
        };
    }

    private static List<String> blockKeys(Block block, String name) {
        List<String> result;
        if (block instanceof CriticalMaterials.MaterialBlock material) {
            CriticalMaterialCatalog.Usage usage = CriticalMaterialCatalog.current()
                .usage(material.materialId);
            result = keys(
                "critical." + usage.name()
                    .toLowerCase(java.util.Locale.ROOT));
        } else if ("critical_render_anchor".equals(name)) {
            result = keys("internal_anchor");
        } else if (block instanceof FictionPedestalBlock) {
            result = keys("story_node");
        } else if (block instanceof RemasterBlock remaster) {
            result = keys(remaster.isInteractive() ? "story_node" : "decoration");
        } else {
            result = switch (name) {
                // Existing ItemBlocks own all four runaway variants and the separate spacetime identity.
                case "RunawaySingularity", "SpacetimeSingularity" -> new ArrayList<>();
                case "AbyssalFluid" -> keys("abyssal_block", "abyssal_fallback");
                case "SealedChest" -> keys("sealed_chest");
                case "UnsealedChest" -> keys("unsealed_chest");
                case "RuinMechanism" -> keys("ruin_mechanism");
                case "SpacetimeAltarCore" -> keys("altar");
                case "RuinedCasing" -> keys("ruined_casing");
                case "RuinDebris" -> keys("ruin_debris");
                default -> keys(name.startsWith("Ruins_") || name.startsWith("Royal_") ? "decoration" : "terrain");
            };
        }
        if (block instanceof BlockFalling) result.add(PREFIX + "falling");
        if (block.getLightValue() > 0) result.add(PREFIX + "light");
        return result;
    }

    private static List<String> keys(String... names) {
        List<String> result = new ArrayList<>(names.length);
        for (String name : names) result.add(PREFIX + name);
        return result;
    }
}
