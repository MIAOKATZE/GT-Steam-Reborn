// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.nei;

import static com.miaokatze.gtsr.common.api.enums.GTSRItemList.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.miaokatze.gtsr.client.nei.GTSRNEIGroups.Group;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityJournal;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.ItemProsperityRelic;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts;
import com.miaokatze.gtsr.register.CreativeTabManager;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;

import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.CollapsibleItems;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.config.ConfigSet;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

/**
 * Native NEI API registration/filter contracts in an isolated JVM. Registries and item identities are fixtures;
 * production cataloguing/grouping and NEI's iterable group/filter implementation run unchanged. No client,
 * world, GUI, configuration loading or full mod lifecycle is started. The legacy fixture suppresses
 * NEIClientConfig's initializer (which opens client.cfg and starts update/GUI setup), then supplies in-memory
 * config/NBT holders, and skips PresetsList's two GUI filter registrations during initialization. The native
 * presets list starts empty; preexisting-group preservation uses a native-parser-created prefix. The enable flag,
 * parser, native clear/load, event callback and state restoration stay native. The early legacy transformer
 * reproduces NEI's own nei_at.cfg access rule for NBTTagCompound.tagMap, absent from the isolated bootstrap.
 */
public final class NEIGroupingContracts {

    private static final Map<Group, List<ItemStack>> EXPECTED = new EnumMap<>(Group.class);
    private static final List<ItemStack> UNGROUPED = new ArrayList<>();
    private static final List<ItemStack> ABSENT = new ArrayList<>();
    private static int checks;
    private static int nextMeta = 100;
    private static Item machines;
    private static Item components;
    private static Method fixtureItem;

    private NEIGroupingContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, NEIGroupingContracts.class.getName(), args)) return;
        boolean legacy = Arrays.asList(args)
            .contains("legacy");
        check(
            NEICollapsibleCompat.usesNativeApi() != legacy,
            "task selects the intended NEI generation; legacy=" + legacy);
        System.out.println(
            "SOURCE NEI API " + API.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
        System.out.println(
            "SOURCE NEI groups " + CollapsibleItems.class.getProtectionDomain()
                .getCodeSource()
                .getLocation());
        if (legacy) {
            check(NbtAccessTransformer.transformed, "early bootstrap applied NEI's native NBT access rule");
            Field tagMap;
            try {
                tagMap = NBTTagCompound.class.getDeclaredField("tagMap");
            } catch (NoSuchFieldException srgName) {
                tagMap = NBTTagCompound.class.getDeclaredField("field_74784_a");
            }
            check(Modifier.isPublic(tagMap.getModifiers()), "native NBT map field is public as required by NEI AT");
            // Forge normally installs this before event classes load. The isolated Launch bootstrap does not.
            Launch.classLoader.registerTransformer(NeiEventSubscriptionTransformer.class.getName());
            Launch.classLoader.registerTransformer(ConfigIsolationTransformer.class.getName());
            Launch.classLoader.registerTransformer(PresetStartupIsolationTransformer.class.getName());
        }
        Method setup = PortableWeaponRecipeContracts.class.getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        fixtureItem = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
        fixtureItem.setAccessible(true);
        fixtures();
        List<ItemStack> catalog = GTSRItemOrderCatalog.rawItems();
        catalogContracts(catalog);
        collectionContracts(catalog);
        if (NEICollapsibleCompat.usesNativeApi()) nativeApiContracts(catalog);
        else legacyContracts(catalog);
        System.out.println(
            "PASS NEI grouping: checks=" + checks
                + " groups=13 module_packs=51 shells=3 native_api="
                + NEICollapsibleCompat.usesNativeApi()
                + " game_started=false gui=false full_nei_init=false user_config_io=false"
                + " in_memory_state_restore="
                + !NEICollapsibleCompat.usesNativeApi());
    }

    private static void fixtures() throws Exception {
        Block machineBlock = new Block(Material.iron) {};
        GameRegistry.registerBlock(machineBlock, "nei_contract_machines");
        GregTechAPI.sBlockMachines = machineBlock;
        machines = Item.getItemFromBlock(machineBlock);
        components = item("components");
        entries(Group.WEAPONS, components, PortableLM12, PortableT20, PortableQLZ04, PortableSingularity);
        entries(
            Group.MAGAZINES,
            components,
            Ammo762Pack,
            MimicAmmo762Pack,
            Ammo20Pack,
            MimicAmmo20Pack,
            Ammo35Pack,
            MimicAmmo35Pack);
        entries(
            Group.CHIPS,
            components,
            VeinPyrolyzerChipT1,
            VeinPyrolyzerChipT2,
            VeinPyrolyzerChipT3,
            GeothermalOverheatChip,
            TcdsBoostChip,
            HubSingularityChip,
            ReinforcedHubSingularityChip,
            SteamTurbineCycleOverlimitChip,
            RareGasSeparationChip);
        entries(
            Group.CATALYSTS,
            components,
            AmmoniaCatalystNickel,
            AmmoniaCatalystPlatinum,
            AmmoniaCatalystUranium,
            AmmoniaCatalystOsmium,
            AmmoniaCatalystFeCo,
            AmmoniaCatalystRuthenium,
            AmmoniaCatalystQuantum);
        entries(Group.MACHINES, machines, LargeCokeOven, ClusterUnitCrusher, SteamSingularityEntangler);
        entries(
            Group.CRITICAL_MACHINES,
            machines,
            CriticalLoom,
            CriticalSolar,
            CriticalTurbine,
            CriticalSun,
            CriticalBattery,
            CriticalProcessing,
            CriticalEntangler,
            CriticalDimension,
            CriticalAssembly,
            CriticalAccelerator);
        entries(
            Group.HATCHES,
            machines,
            SteamInputHatchGeneric,
            PressureSteamHatch,
            SteamOutputHatchGeneric,
            SteamOutputHatch,
            PressureSteamOutputHatch,
            SteamCoolingHatch,
            PressureSteamCoolingHatch,
            DistilledWaterHatch,
            MegaAirInputHatch,
            OverpressureTurbineInputHatch,
            RedstoneHatch,
            SteamHubInputHatch,
            SteamHubOutputHatch,
            WaterHubInputHatch,
            WaterHubOutputHatch,
            HubStorageUnit,
            ReinforcedHubStorageUnit,
            OverpressureHubStorageUnit,
            SingularitySteamCompartment,
            SingularitySteamOutputCompartment,
            SingularityFluidInputCompartment,
            SingularityFluidOutputCompartment,
            CriticalInputHatchME,
            CriticalInputBusME,
            CriticalOutputHatchME,
            CriticalOutputBusME);
        entries(
            Group.HUBS_AND_NODES,
            machines,
            SteamHubArray,
            WaterHubArray,
            SingularityDrillingHub,
            SteamCacheNode,
            ReinforcedSteamCacheNode,
            OverpressureSteamCacheNode,
            WaterCacheNode,
            ReinforcedWaterCacheNode,
            OverpressureWaterCacheNode,
            SingularityMinerNode,
            SingularityDrillingNode,
            HubTerminal);

        Constructor<CenterModuleItems> moduleConstructor = CenterModuleItems.class.getDeclaredConstructor();
        moduleConstructor.setAccessible(true);
        CenterModuleItems modules = moduleConstructor.newInstance();
        Field instance = CenterModuleItems.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, modules);
        GameRegistry.registerItem(modules, "nei_contract_modules");
        CenterModule[] savedKinds = { CenterModule.CRUSHER, CenterModule.WASHER, CenterModule.SIFTER,
            CenterModule.CENTRIFUGE, CenterModule.ELECTROLYZER, CenterModule.DRYER, CenterModule.FURNACE,
            CenterModule.REACTOR, CenterModule.SEPARATOR, CenterModule.EXTRACTOR, CenterModule.THERMAL_CENTRIFUGE,
            CenterModule.SMELTING, CenterModule.SPEED, CenterModule.PARALLEL, CenterModule.YIELD,
            CenterModule.SECONDARY, CenterModule.LOGISTICS };
        List<CenterModule> types = new ArrayList<>();
        for (int meta = 0; meta < 51; meta++) {
            ItemStack stack = new ItemStack(modules, 1, meta);
            CenterModule module = CenterModuleItems.moduleOf(stack);
            check(module != null && CenterModuleItems.gradeOf(stack) == meta % 3 + 1, "valid module grade " + meta);
            check(module == savedKinds[meta / 3], "saved module identity " + meta);
            if (meta % 3 == 0) {
                check(!types.contains(module), "distinct module kind " + meta);
                types.add(module);
            } else check(types.get(types.size() - 1) == module, "three grades share a kind " + meta);
            expect(Group.MODULE_PACKS, stack);
        }
        check(types.size() == 17, "seventeen module kinds");
        for (int grade = 1; grade <= 3; grade++) {
            ItemStack shell = CenterModuleItems.shell(grade);
            check(shell.getItemDamage() == 50 + grade && CenterModuleItems.isShell(shell), "shell grade " + grade);
            check(
                CenterModuleItems.moduleOf(shell) == null && CenterModuleItems.gradeOf(shell) == 0,
                "shell is not an installable module");
            expect(Group.MODULE_SHELLS, shell);
        }
        for (int meta : new int[] { -1, 54, 32767 }) {
            ItemStack invalid = new ItemStack(modules, 1, 0);
            Field damage = ItemStack.class.getDeclaredField("itemDamage");
            damage.setAccessible(true);
            damage.setInt(invalid, meta);
            check(
                CenterModuleItems.moduleOf(invalid) == null && !CenterModuleItems.isShell(invalid)
                    && CenterModuleItems.gradeOf(invalid) == 0,
                "invalid module metadata " + meta);
            check(GTSRNEIGroups.groupOf(invalid) == null, "invalid module is not grouped " + meta);
            // Vanilla ItemStack.copy normalizes negative damage; test that malformed stack directly above.
            if (meta >= 0) UNGROUPED.add(invalid);
        }
        CenterModuleItems lookalike = moduleConstructor.newInstance();
        GameRegistry.registerItem(lookalike, "nei_contract_other_modules");
        UNGROUPED.add(new ItemStack(lookalike, 1, 0));
        UNGROUPED.add(new ItemStack(lookalike, 1, 51));

        Constructor<CriticalMaterials.MaterialBlock> materialConstructor = CriticalMaterials.MaterialBlock.class
            .getDeclaredConstructor(CriticalMaterialDefinitions.Definition.class);
        materialConstructor.setAccessible(true);
        CriticalMaterialDefinitions.Definition definition = CriticalMaterialDefinitions.catalog()
            .definitions()
            .values()
            .iterator()
            .next();
        Block casing = materialConstructor.newInstance(definition);
        GameRegistry.registerBlock(casing, "nei_contract_critical_casing");
        expect(Group.CRITICAL_CASINGS, new ItemStack(casing));
        Block prosperity = new Block(Material.rock) {};
        GameRegistry.registerBlock(prosperity, "prosperity_contract_stone");
        expect(Group.PROSPERITY_BLOCKS, new ItemStack(prosperity));
        ItemProsperityRelic relic = new ItemProsperityRelic("nei_contract_relic");
        GameRegistry.registerItem(relic, "nei_contract_relic");
        LoreRegistry.RELICS.put("nei_contract_relic", relic);
        expect(Group.PROSPERITY_RELICS, new ItemStack(relic));
        LoreRegistry.journal = new ItemProsperityJournal();
        GameRegistry.registerItem(LoreRegistry.journal, "nei_contract_journal");
        UNGROUPED.add(new ItemStack(LoreRegistry.journal));
        // Same base class/package does not make an unregistered relic a member of RELICS.
        ItemProsperityRelic otherRelic = new ItemProsperityRelic("nei_contract_other_relic");
        GameRegistry.registerItem(otherRelic, "nei_contract_other_relic");
        UNGROUPED.add(new ItemStack(otherRelic));
        SpacetimeCalibration.set(item("dimension_tool"));
        UNGROUPED.add(SpacetimeCalibration.get(1));
        UNGROUPED.add(new ItemStack(components, 1, 32000));
        UNGROUPED.add(new ItemStack(machines, 1, 32000)); // Another mod's metadata on GT's shared item.
        UNGROUPED.add(new ItemStack(item("ordinary")));
        UNGROUPED.add(new ItemStack(item("foreign_weapon_lookalike"), 1, PortableLM12.getMeta()));
        UNGROUPED.add(foreignProsperityBlock());

        // Registered identities absent from the supplied catalog must never be introduced by grouping.
        ItemStack omittedMachine = new ItemStack(machines, 1, 31999);
        DenseStateManipulator.set(omittedMachine);
        ABSENT.add(omittedMachine);
        ABSENT.add(new ItemStack(casing, 1, 1));
        ABSENT.add(new ItemStack(relic, 1, 1));
        ABSENT.add(new ItemStack(item("foreign_absent")));

        List<ItemStack> visible = new ArrayList<>();
        for (List<ItemStack> family : EXPECTED.values()) visible.addAll(family);
        visible.addAll(UNGROUPED);
        Collections.reverse(visible); // Force the real catalog to establish domain order.
        for (ItemStack stack : visible) CreativeTabManager.addItemToTab(stack);
    }

    private static void catalogContracts(List<ItemStack> catalog) {
        check(Group.values().length == 13 && EXPECTED.size() == 13, "all thirteen groups represented");
        int lastWeapon = -1;
        for (ItemStack weapon : EXPECTED.get(Group.WEAPONS)) {
            int position = indexOf(catalog, weapon);
            check(position > lastWeapon, "LM12/T20/QLZ04/singularity weapon order");
            lastWeapon = position;
        }
        for (ItemStack magazine : EXPECTED.get(Group.MAGAZINES))
            check(indexOf(catalog, magazine) > lastWeapon, "every weapon precedes every magazine");
        for (ItemStack stack : UNGROUPED) {
            check(indexOf(catalog, stack) >= 0, "ungrouped visible variant remains in catalog");
            check(GTSRNEIGroups.groupOf(stack) == null, "ungrouped/foreign/invalid variant has no group");
        }
        check(GTSRItemOrderCatalog.isDimensionItem(new ItemStack(LoreRegistry.journal)), "journal is a dimension item");
        check(GTSRItemOrderCatalog.isDimensionItem(SpacetimeCalibration.get(1)), "non-block dimension tool recognized");
        for (ItemStack stack : ABSENT) check(indexOf(catalog, stack) < 0, "omitted identity is absent from catalog");
        check(GTSRNEIGroups.groupOf(null) == null, "null stack has no group");
        check(GTSRNEIGroups.groupOf(new ItemStack((Item) null, 1, 0)) == null, "empty item has no group");
    }

    private static void collectionContracts(List<ItemStack> catalog) {
        Map<Group, List<ItemStack>> actual = GTSRNEIGroups.collect(catalog);
        check(actual.size() == 13, "collect returns thirteen non-empty groups");
        for (Map.Entry<Group, List<ItemStack>> family : EXPECTED.entrySet()) {
            List<ItemStack> members = actual.get(family.getKey());
            check(
                members.size() == family.getValue()
                    .size(),
                "exact family cardinality " + family.getKey());
            int previous = -1;
            for (ItemStack stack : members) {
                check(indexOf(family.getValue(), stack) >= 0, "exact family membership " + family.getKey());
                int rank = indexOf(catalog, stack);
                check(rank > previous && stack != catalog.get(rank), "catalog order and stack copy " + family.getKey());
                previous = rank;
            }
        }
        for (ItemStack stack : catalog) {
            int memberships = 0;
            for (List<ItemStack> family : actual.values()) if (indexOf(family, stack) >= 0) memberships++;
            check(memberships == (GTSRNEIGroups.groupOf(stack) == null ? 0 : 1), "groups are disjoint");
        }
        for (ItemStack stack : ABSENT) for (List<ItemStack> family : actual.values())
            check(indexOf(family, stack) < 0, "grouping cannot introduce omitted/foreign stacks");

        ItemStack named = PortableLM12.get(7);
        named.setTagCompound(new NBTTagCompound());
        named.getTagCompound()
            .setString("contract", "original");
        List<ItemStack> input = new ArrayList<>(Arrays.asList(null, new ItemStack((Item) null, 1, 0), named));
        Map<Group, List<ItemStack>> copied = GTSRNEIGroups.collect(input);
        ItemStack copy = copied.get(Group.WEAPONS)
            .get(0);
        check(
            copy != named && copy.stackSize == 7
                && copy.getTagCompound()
                    .equals(named.getTagCompound()),
            "collect preserves count and full NBT on an independent stack");
        copy.getTagCompound()
            .setString("contract", "changed");
        copy.stackSize = 1;
        check(
            named.stackSize == 7 && "original".equals(
                named.getTagCompound()
                    .getString("contract")),
            "output NBT and count mutations do not affect input");
        copied.get(Group.WEAPONS)
            .clear();
        check(
            input.size() == 3 && GTSRNEIGroups.collect(input)
                .get(Group.WEAPONS)
                .size() == 1,
            "output lists do not alias input or later collections");
        input.clear();
        check(
            GTSRNEIGroups.collect(input)
                .isEmpty(),
            "empty catalog produces no generated members");
    }

    private static void nativeApiContracts(List<ItemStack> catalog) throws Exception {
        ItemOrderCatalog.register("gtsr", GTSRItemOrderCatalog::rawItems);
        Field apiField = CollapsibleItems.class.getDeclaredField("apiGroups");
        apiField.setAccessible(true);
        List<?> apiGroups = (List<?>) apiField.get(null);
        int before = apiGroups.size();
        List<ItemStack> hiddenBefore = new ArrayList<>(ItemInfo.hiddenItems.keys());
        int overridesBefore = ItemInfo.itemOverrides.size();
        IConfigureNEI plugin = configPlugin();
        check(
            plugin.getName()
                .equals("GTSR Item Groups"),
            "native NEI plugin has a public discoverable entry");
        plugin.loadConfig();
        check(apiGroups.size() == before + 13, "production register adds thirteen native NEI API groups");
        plugin.loadConfig();
        check(apiGroups.size() == before + 13, "registration is idempotent");
        int ordinal = 0;
        for (Group expected : Group.values()) {
            Object nativeGroup = ((Supplier<?>) apiGroups.get(before + ordinal++)).get();
            Class<?> type = nativeGroup.getClass();
            check(
                !type.getField("expanded")
                    .getBoolean(nativeGroup),
                "native default is collapsed " + expected);
            check(
                ("gtsr.nei.group." + expected.id).equals(
                    type.getField("unlocalizedName")
                        .get(nativeGroup)),
                "native localization key " + expected);
            ItemFilter filter = (ItemFilter) type.getField("filter")
                .get(nativeGroup);
            for (ItemStack stack : catalog) check(
                filter.matches(stack) == (indexOf(EXPECTED.get(expected), stack) >= 0),
                "native exact item/meta filter " + expected);
            for (ItemStack absent : ABSENT) check(!filter.matches(absent), "native filter excludes omitted variant");
        }
        check(
            ItemInfo.hiddenItems.keys()
                .equals(hiddenBefore),
            "registration adds no hidden items");
        check(ItemInfo.itemOverrides.size() == overridesBefore, "registration adds no item-list entries");
    }

    private static Item item(String key) throws Exception {
        return (Item) fixtureItem.invoke(null, "gtsr_nei_contract:" + key);
    }

    private static void legacyContracts(List<ItemStack> catalog) throws Exception {
        isolatedNeiConfig();
        check(ConfigIsolationTransformer.transformed, "legacy config initializer was isolated before first use");
        Event initialEvent = configsLoadedEvent();
        check(NeiEventSubscriptionTransformer.transformed, "official transformer processed NEI event before use");
        check(
            initialEvent.getListenerList() != new Event().getListenerList(),
            "NEI configuration event has its native dedicated listener list");
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(false);
        ItemOrderCatalog.register("gtsr", GTSRItemOrderCatalog::rawItems);
        Field groupsField = CollapsibleItems.class.getDeclaredField("groups");
        groupsField.setAccessible(true);
        List<?> groups = (List<?>) groupsField.get(null);
        Method parse = CollapsibleItems.class.getDeclaredMethod("parseFile", List.class);
        parse.setAccessible(true);
        List<String> presetRule = Arrays.asList(
            "; {\"displayName\":\"Unrelated native preset\",\"expanded\":true}",
            "<gtsr_nei_contract:foreign_absent:0>");
        parse.invoke(null, presetRule);
        check(groups.size() == 1, "native parser establishes an unrelated group prefix as the preset surrogate");
        Object preset = groups.get(0);
        List<ItemStack> hiddenBefore = new ArrayList<>(ItemInfo.hiddenItems.keys());
        int overridesBefore = ItemInfo.itemOverrides.size();

        IConfigureNEI plugin = configPlugin();
        plugin.loadConfig();
        plugin.loadConfig();
        check(groups.size() == 1, "legacy registration waits until native configuration has loaded");
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(groups.size() == 1 && groups.get(0) == preset, "disabled legacy grouping preserves native presets");
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(true);
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(groups.size() == 14 && groups.get(0) == preset, "event appends thirteen groups after native presets");
        check(CollapsibleItems.isExpanded(0), "unrelated preset expansion remains unchanged");
        List<String> guids = new ArrayList<>();
        int index = 1;
        for (Group expected : Group.values()) {
            Object group = groups.get(index);
            check(!CollapsibleItems.isExpanded(index++), "legacy default is collapsed " + expected);
            check(
                StatCollector.translateToLocal("gtsr.nei.group." + expected.id)
                    .equals(
                        group.getClass()
                            .getField("displayName")
                            .get(group)),
                "legacy localized name " + expected);
            String guid = (String) group.getClass()
                .getField("guid")
                .get(group);
            check(guid != null && !guid.isEmpty() && !guids.contains(guid), "native distinct GUID " + expected);
            guids.add(guid);
            ItemFilter filter = (ItemFilter) group.getClass()
                .getField("filter")
                .get(group);
            for (ItemStack stack : catalog) check(
                filter.matches(stack) == (indexOf(EXPECTED.get(expected), stack) >= 0),
                "legacy native exact item/meta filter " + expected);
            for (ItemStack absent : ABSENT) check(!filter.matches(absent), "legacy excludes omitted/foreign variants");
        }
        CollapsibleItems.setExpanded(1, true);
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(
            groups.size() == 14 && CollapsibleItems.isExpanded(1),
            "duplicate event preserves user changes and does not append duplicates");

        NBTTagCompound saved = new NBTTagCompound();
        saved.setBoolean(guids.get(0), true);
        NEIClientConfig.world.nbt.setTag("collapsibleitems", saved);
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(false);
        // With the flag disabled native load() clears groups/cache and restores NBT without opening cfg files.
        CollapsibleItems.load();
        check(
            PresetStartupIsolationTransformer.skippedRegistrations == 2,
            "only the two native preset GUI filter registrations were isolated");
        check(
            ((List<?>) Class.forName("codechicken.nei.PresetsList")
                .getField("presets")
                .get(null)).isEmpty(),
            "native preset collection is genuinely initialized and empty in this isolated fixture");
        check(groups.isEmpty(), "actual native load clears the existing groups in place");
        parse.invoke(null, presetRule);
        preset = groups.get(0);
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(true);
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(groups.size() == 14 && groups.get(0) == preset, "legacy groups rebuild after native load clears them");
        for (int i = 0; i < guids.size(); i++) {
            Object group = groups.get(i + 1);
            check(
                guids.get(i)
                    .equals(
                        group.getClass()
                            .getField("guid")
                            .get(group)),
                "sorted exact selectors retain native GUID across reload " + i);
            check(CollapsibleItems.isExpanded(i + 1) == (i == 0), "native NBT state restoration " + i);
        }
        ItemStack cachedWeapon = PortableLM12.get(1);
        CollapsibleItems.putItem(cachedWeapon);
        check(CollapsibleItems.getGroupIndex(cachedWeapon) == 1, "native item cache recognizes appended group");
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(false);
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(
            groups.size() == 1 && groups.get(0) == preset && CollapsibleItems.isExpanded(0),
            "disabling removes only owned groups and retains native preset state");
        check(CollapsibleItems.getGroupIndex(cachedWeapon) == -1, "owned group removal clears stale native indices");

        // Fault injection at the adapter boundary exercises rollback after native parsing appended a prefix.
        Field rulesField = NEICollapsibleCompat.class.getDeclaredField("legacyRules");
        rulesField.setAccessible(true);
        Object validRules = rulesField.get(null);
        rulesField.set(null, Arrays.asList(presetRule.get(0), presetRule.get(1), presetRule.get(0), ""));
        NEIClientConfig.getSetting("inventory.collapsibleItems.enabled")
            .setBooleanValue(true);
        boolean rejected = false;
        try {
            NEICollapsibleCompat.onConfigsLoaded();
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            rulesField.set(null, validRules);
        }
        check(
            rejected && groups.size() == 1 && groups.get(0) == preset,
            "incomplete native parse fails clearly and rolls back only this adapter's append");
        MinecraftForge.EVENT_BUS.post(configsLoadedEvent());
        check(groups.size() == 14 && CollapsibleItems.isExpanded(1), "adapter can retry a failed append");
        check(
            ItemInfo.hiddenItems.keys()
                .equals(hiddenBefore),
            "legacy registration does not hide items");
        check(ItemInfo.itemOverrides.size() == overridesBefore, "legacy registration adds no item-list entries");
    }

    private static void isolatedNeiConfig() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
        ConfigSet memory = (ConfigSet) allocate.invoke(unsafe, ConfigSet.class);
        memory.nbt = new NBTTagCompound();
        ConfigTagParent settings = new ConfigTagParent() {

            @Override
            public void saveConfig() {}

            @Override
            public String getNameQualifier() {
                return "";
            }
        };
        Field config = ConfigSet.class.getDeclaredField("config");
        config.setAccessible(true);
        config.set(memory, settings);
        NEIClientConfig.logger = LogManager.getLogger("NEI isolated grouping contract");
        NEIClientConfig.global = memory;
        NEIClientConfig.world = memory;
    }

    private static IConfigureNEI configPlugin() throws Exception {
        return (IConfigureNEI) Class
            .forName("com.miaokatze.gtsr.client.nei.NEIGTSRConfig", true, NEIGroupingContracts.class.getClassLoader())
            .getDeclaredConstructor()
            .newInstance();
    }

    private static Event configsLoadedEvent() throws Exception {
        // Keep this type out of the test class's descriptors so verification cannot resolve it before injection.
        return (Event) Class
            .forName("codechicken.nei.event.NEIConfigsLoadedEvent", true, NEIGroupingContracts.class.getClassLoader())
            .getDeclaredConstructor()
            .newInstance();
    }

    /**
     * Installed by the bootstrap before Minecraft classes load. Reproduces only NEI's native rule:
     * public net.minecraft.nbt.NBTTagCompound field_74784_a #tagMap
     */
    public static final class NbtAccessTransformer implements IClassTransformer {

        static boolean transformed;

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"net.minecraft.nbt.NBTTagCompound".equals(transformedName)) return bytes;
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {

                @Override
                public FieldVisitor visitField(int access, String fieldName, String descriptor, String signature,
                    Object value) {
                    if ("tagMap".equals(fieldName) || "field_74784_a".equals(fieldName)) {
                        access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                        transformed = true;
                    }
                    return super.visitField(access, fieldName, descriptor, signature, value);
                }
            }, 0);
            return writer.toByteArray();
        }
    }

    /** Only isolates file/network/GUI setup; no production grouping, parser, filtering or state method changes. */
    public static final class ConfigIsolationTransformer implements IClassTransformer {

        static boolean transformed;

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"codechicken.nei.NEIClientConfig".equals(transformedName)) return bytes;
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {

                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                    if ("<clinit>".equals(methodName)) return null;
                    return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                }
            }, 0);
            transformed = true;
            return writer.toByteArray();
        }
    }

    /** Restore Forge's native listener-list injection only for the one event used by this offline fixture. */
    public static final class NeiEventSubscriptionTransformer implements IClassTransformer {

        static boolean transformed;

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"codechicken.nei.event.NEIConfigsLoadedEvent".equals(transformedName)) return bytes;
            byte[] result = new EventSubscriptionTransformer().transform(name, transformedName, bytes);
            transformed = true;
            return result;
        }
    }

    /** Retain native preset storage/construction while preventing its two filter APIs from initializing GUI. */
    public static final class PresetStartupIsolationTransformer implements IClassTransformer {

        static int skippedRegistrations;

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"codechicken.nei.PresetsList".equals(transformedName)) return bytes;
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {

                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                    String[] exceptions) {
                    MethodVisitor method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    if (!"<clinit>".equals(methodName)) return method;
                    return new MethodVisitor(Opcodes.ASM5, method) {

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calledName,
                            String calledDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC && "codechicken/nei/api/API".equals(owner)
                                && (("addItemFilter".equals(calledName)
                                    && "(Lcodechicken/nei/api/ItemFilter$ItemFilterProvider;)V"
                                        .equals(calledDescriptor))
                                    || ("addRecipeFilter".equals(calledName)
                                        && "(Lcodechicken/nei/api/IRecipeFilter$IRecipeFilterProvider;)V"
                                            .equals(calledDescriptor)))) {
                                super.visitInsn(Opcodes.POP);
                                skippedRegistrations++;
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        }
    }

    private static ItemStack foreignProsperityBlock() throws Exception {
        Field controllerField = Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(Loader.instance());
        Field active = LoadController.class.getDeclaredField("activeContainer");
        active.setAccessible(true);
        Object previous = active.get(controller);
        ModMetadata metadata = new ModMetadata();
        metadata.modId = "foreign";
        metadata.name = "Foreign NEI block fixture";
        active.set(controller, new DummyModContainer(metadata));
        Block block = new Block(Material.rock) {};
        try {
            GameRegistry.registerBlock(block, "prosperityStone");
        } finally {
            active.set(controller, previous);
        }
        return new ItemStack(block);
    }

    private static void entries(Group group, Item item, GTSRItemList... entries) {
        for (GTSRItemList entry : entries) {
            ItemStack stack = new ItemStack(item, 1, nextMeta++);
            entry.set(stack);
            expect(group, stack);
        }
    }

    private static void expect(Group group, ItemStack stack) {
        EXPECTED.computeIfAbsent(group, ignored -> new ArrayList<>())
            .add(stack);
    }

    private static int indexOf(List<ItemStack> list, ItemStack target) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (stack.getItem() == target.getItem() && stack.getItemDamage() == target.getItemDamage()) return i;
        }
        return -1;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
