package com.miaokatze.gtsr.register;

import static com.miaokatze.gtsr.common.api.enums.GTSRItemList.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.items.MetaGeneratedItemX32;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

/**
 * GTSR 的唯一展示目录。创造入口保留原可见集合，NEI 另认领已经生成的自有 GT 材料形态。
 * 不扫描整个 GT ItemMachines，也不从枚举补入未展示的冻结控制器或世界内部方块。
 * Core 负责 NEI 比较器；本供应器只读原始注册快照，避免相互调用形成递归。
 */
public final class GTSRItemOrderCatalog {

    private static final String OWNER = "gtsr";

    /** 顺序也是创造页中的领域顺序；同族内普通、强化、超压按等级递进。 */
    private static final GTSRItemList[][] DOMAINS = {
        { LargeCokeOven, ReinforcedBrickBlastFurnace, SiemensMartinFurnace, LargeSteamFurnace,
            LargeSolarOverpressureArray, LargeGeothermalSteamBoiler, TcdsController, MegaSteamTurbineArray,
            SteamFluidDrill, CrustSteamBorer, CrustMatterAggregator, VeinSteamPyrolyzer, AirCompressor,
            AtmosphericCentrifuge, KineticProcessingArray, AmmoniaPlant, GearSteamCompressor, SteamHubArray,
            WaterHubArray, SingularityDrillingHub, SteamSingularityEntangler, DenseStateManipulator,
            CriticalSingularityCompressor, CriticalLoom, CriticalSolar, CriticalTurbine, CriticalSun, CriticalBattery,
            ClusterController, ClusterUnitCrusher, ClusterUnitOreWasher, ClusterUnitCentrifuge,
            ClusterUnitThermalCentrifuge, ClusterUnitSifter, ClusterUnitMagneticSeparator, ClusterUnitFurnace,
            ClusterBoosterParallel, ClusterBoosterSpeed, ClusterBoosterPrimary, ClusterBoosterSecondary,
            ClusterBoosterSteamSaver, ClusterUnitLogistics },
        { SteamInputHatchGeneric, PressureSteamHatch, SteamOutputHatchGeneric, SteamOutputHatch,
            PressureSteamOutputHatch, SteamCoolingHatch, PressureSteamCoolingHatch, DistilledWaterHatch,
            MegaAirInputHatch, OverpressureTurbineInputHatch, RedstoneHatch, SteamHubInputHatch, SteamHubOutputHatch,
            WaterHubInputHatch, WaterHubOutputHatch, HubStorageUnit, ReinforcedHubStorageUnit,
            OverpressureHubStorageUnit, SteamCacheNode, ReinforcedSteamCacheNode, OverpressureSteamCacheNode,
            WaterCacheNode, ReinforcedWaterCacheNode, OverpressureWaterCacheNode, SingularitySteamCompartment,
            SingularitySteamOutputCompartment, SingularityFluidInputCompartment, SingularityFluidOutputCompartment,
            SingularityMinerNode, SingularityDrillingNode, HubTerminal },
        { VeinPyrolyzerChipT1, VeinPyrolyzerChipT2, VeinPyrolyzerChipT3, GeothermalOverheatChip, TcdsBoostChip,
            HubSingularityChip, ReinforcedHubSingularityChip, SteamTurbineCycleOverlimitChip, RareGasSeparationChip,
            AmmoniaCatalystNickel, AmmoniaCatalystPlatinum, AmmoniaCatalystUranium, AmmoniaCatalystOsmium,
            AmmoniaCatalystFeCo, AmmoniaCatalystRuthenium, AmmoniaCatalystQuantum },
        { SteamEntangledSingularity, CriticalSteamEntangledSingularity, IrradiatedMoonDust, IrradiatedStoneDust },
        { SingularityTuningWand, PortableLM12, Ammo762Pack, MimicAmmo762Pack, PortableT20, Ammo20Pack, MimicAmmo20Pack,
            PortableQLZ04, Ammo35Pack, MimicAmmo35Pack, PortableSingularity } };

    // 这些可见物品原先只在 GT 页注册，纳入完整目录而不遍历共享机器物品的全部 metadata。
    private static final GTSRItemList[] ADDITIONAL_VISIBLE_ITEMS = { PortableLM12, Ammo762Pack, MimicAmmo762Pack,
        PortableT20, Ammo20Pack, MimicAmmo20Pack, PortableQLZ04, Ammo35Pack, MimicAmmo35Pack, PortableSingularity };

    /** 维界入口按功能排列；其后的自然方块、建筑、遗物保持各注册族内的原始顺序。 */
    private static final GTSRItemList[] DIMENSION_ENTRIES = { SpacetimeCalibration, SpacetimeAnchorBeacon,
        AbyssalObsessionBucket };

    // 位于 common.blocks 而非 dimension 包的真实维度注册身份。
    private static final Set<String> DIMENSION_PATHS = new HashSet<>(
        Arrays.asList(
            "prosperitysilicasand",
            "prosperitycoarsesand",
            "prosperityrivergravel",
            "prosperitystone",
            "spacetimealtarcore",
            "ruinmechanism",
            "sealedchest",
            "unsealedchest"));

    private GTSRItemOrderCatalog() {}

    /** NEI 权威原始目录（领域顺序）；晚于 init 的 GT 自动形态通过 postInit invalidate 纳入。 */
    public static List<ItemStack> rawItems() {
        List<ItemStack> registered = rawCreativeItems();
        List<ItemStack> ordinary = new ArrayList<>();
        List<ItemStack> dimension = new ArrayList<>();
        for (ItemStack stack : registered) {
            if (isInternal(stack)) continue;
            appendUnique(isDimensionItem(stack) ? dimension : ordinary, stack);
        }
        List<ItemStack> result = new ArrayList<>();
        for (GTSRItemList[] domain : DOMAINS) appendEntries(result, ordinary, domain);
        // 奇点方块四个 meta 仍按 0/1/2/3 的自然等级排列；其后为 canonical 临界建筑材料。
        appendRegistryPath(result, ordinary, "RunawaySingularity");
        appendRegistryPath(result, ordinary, "SpacetimeSingularity");
        result.addAll(ordinary);
        appendEntries(result, dimension, DIMENSION_ENTRIES);
        result.addAll(dimension);
        result.addAll(generatedDimensionItems());
        return result;
    }

    /** 创造页候选集合：原有显式入口加原先只在 GT 页可见的十件武器，不补 GT 自动材料。 */
    public static List<ItemStack> rawCreativeItems() {
        List<ItemStack> result = CreativeTabManager.getRegisteredItems();
        for (GTSRItemList entry : ADDITIONAL_VISIBLE_ITEMS) {
            if (entry.hasBeenSet()) appendUnique(result, entry.get(1));
        }
        return result;
    }

    /**
     * NEI 专用的自有 GT 生成项。只查注册声明的粉三形态或 Cell，以及真实流体 display；
     * 不扫描 OrePrefixes，不接受矿辞替代品，不将潜在但尚未生成的形式塞进创造页。
     */
    public static List<ItemStack> generatedDimensionItems() {
        List<ItemStack> result = new ArrayList<>();
        for (Materials material : new Materials[] { GTSRProsperityAirMaterials.WastesSigh,
            GTSRProsperityAirMaterials.ThickGrease, GTSRProsperityAirMaterials.MetalGrit,
            GTSRProsperityAirMaterials.UmbralMire, GTSRProsperityAirMaterials.SanzuResidualSteam,
            GTSRProsperityAirMaterials.AbyssalObsession, GTSRProsperityAirMaterials.WitheredBreath }) {
            if (material == null) continue;
            appendGenerated(result, material, OrePrefixes.cell);
            appendFluidDisplay(result, material.getGas(1));
            appendFluidDisplay(result, material.getFluid(1));
        }
        for (IndustrialRecipeLedger.MaterialDefinition definition : IndustrialRecipeLedger.get().materials) {
            Materials material;
            try {
                material = ProsperityIndustrialMaterials.get(definition.id);
            } catch (IllegalStateException notRegisteredYet) {
                continue;
            }
            if (!definition.name.equals(material.mName)) continue;
            if ("dust".equals(definition.phase)) {
                appendGenerated(result, material, OrePrefixes.dustTiny);
                appendGenerated(result, material, OrePrefixes.dustSmall);
                appendGenerated(result, material, OrePrefixes.dust);
            } else {
                appendGenerated(result, material, OrePrefixes.cell);
                appendFluidDisplay(result, "gas".equals(definition.phase) ? material.getGas(1) : material.getFluid(1));
            }
        }
        return result;
    }

    private static void appendGenerated(List<ItemStack> items, Materials material, OrePrefixes prefix) {
        int id = material.mMetaItemSubID;
        if (id < 0 || id >= GregTechAPI.sGeneratedMaterials.length || GregTechAPI.sGeneratedMaterials[id] != material)
            return;
        ItemStack stack = GTOreDictUnificator.get(prefix, material, 1L);
        if (stack == null || !(stack.getItem() instanceof MetaGeneratedItemX32)) return;
        ItemData data = GTOreDictUnificator.getItemData(stack);
        // MetaBaseItem.getMaterial 的确切映射为 sGeneratedMaterials[damage % 1000]；原生矿辞数据确认前缀。
        // 不直接调用 MetaGeneratedItemX32 方法，避免编译期引入父类可选的 AppleCore IEdible 接口。
        if (!Materials.isMaterialItem(stack.getItemDamage()) || stack.getItemDamage() % 1000 != id
            || data == null
            || data.mPrefix != prefix
            || data.mMaterial == null
            || data.mMaterial.mMaterial != material
            || !prefix.doGenerateItem(material)) return;
        appendUnique(items, stack);
    }

    private static void appendFluidDisplay(List<ItemStack> items, FluidStack fluid) {
        if (fluid == null || FluidRegistry.getFluid(
            fluid.getFluid()
                .getName())
            != fluid.getFluid() || !ItemList.Display_Fluid.hasBeenSet() || ItemList.Display_Fluid.getItem() == null)
            return;
        ItemStack display = GTUtility.getFluidDisplayStack(fluid, false);
        if (display == null || display.getItem() != ItemList.Display_Fluid.getItem()
            || display.getItemDamage() != fluid.getFluid()
                .getID())
            return;
        FluidStack decoded = GTUtility.getFluidFromDisplayStack(display);
        if (decoded != null && decoded.getFluid() == fluid.getFluid()) appendUnique(items, display);
    }

    public static List<ItemStack> orderedItems() {
        return ItemOrderCatalog.orderedItems(OWNER);
    }

    /** 供覆盖核验读取；识别物品类和实际方块类，兼容通用 ItemBlock 包装。 */
    public static boolean isDimensionItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof MetaGeneratedItemX32
            || (ItemList.Display_Fluid.hasBeenSet() && stack.getItem() == ItemList.Display_Fluid.getItem())) {
            for (ItemStack generated : generatedDimensionItems()) {
                if (generated.getItem() == stack.getItem() && generated.getItemDamage() == stack.getItemDamage())
                    return true;
            }
        }
        for (GTSRItemList entry : DIMENSION_ENTRIES) {
            if (matches(entry, stack)) return true;
        }
        if (dimensionClass(
            stack.getItem()
                .getClass()))
            return true;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block != null && dimensionClass(block.getClass())) return true;
        String path = registryPath(stack).toLowerCase(Locale.ROOT);
        return DIMENSION_PATHS.contains(path) || path.startsWith("prosperity")
            || path.startsWith("shattered")
            || path.startsWith("ruins_")
            || path.startsWith("royal_")
            || path.startsWith("remaster_");
    }

    private static boolean dimensionClass(Class<?> type) {
        return type.getName()
            .startsWith("com.miaokatze.gtsr.common.dimension.");
    }

    private static boolean isInternal(ItemStack stack) {
        return stack == null || stack.getItem() == null || "critical_render_anchor".equals(registryPath(stack));
    }

    private static String registryPath(ItemStack stack) {
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null) return "";
        return name.substring(name.indexOf(':') + 1);
    }

    private static void appendEntries(List<ItemStack> result, List<ItemStack> remaining, GTSRItemList[] entries) {
        for (GTSRItemList entry : entries) {
            for (int i = 0; i < remaining.size(); i++) {
                if (matches(entry, remaining.get(i))) {
                    result.add(remaining.remove(i));
                    break;
                }
            }
        }
    }

    private static boolean matches(GTSRItemList entry, ItemStack stack) {
        return entry.hasBeenSet() && entry.getItem() == stack.getItem() && entry.getMeta() == stack.getItemDamage();
    }

    private static void appendRegistryPath(List<ItemStack> result, List<ItemStack> remaining, String path) {
        List<ItemStack> variants = new ArrayList<>();
        for (int i = remaining.size() - 1; i >= 0; i--) {
            if (path.equals(registryPath(remaining.get(i)))) variants.add(remaining.remove(i));
        }
        variants.sort((left, right) -> Integer.compare(left.getItemDamage(), right.getItemDamage()));
        result.addAll(variants);
    }

    private static void appendUnique(List<ItemStack> items, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        for (ItemStack item : items) {
            if (item.getItem() == stack.getItem() && item.getItemDamage() == stack.getItemDamage()) return;
        }
        items.add(stack.copy());
    }
}
