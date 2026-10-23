package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/** Immutable full-job price basis. Equipment is installed at one deterministic carrier per macro unit. */
public final class CriticalConstructionEconomy {

    public static final int VERSION = 2;
    private final String layoutKey;
    private final ItemStack controller;
    private final Map<String, ItemStack> shells;
    private final Map<Long, ItemStack[]> carriers;
    private final ItemStack[] demand;

    private CriticalConstructionEconomy(String layoutKey, ItemStack controller, Map<String, ItemStack> shells,
        Map<Long, ItemStack[]> carriers, ItemStack[] demand) {
        this.layoutKey = layoutKey;
        this.controller = controller;
        this.shells = shells;
        this.carriers = carriers;
        this.demand = demand;
    }

    /** Input includes exactly the automatic paid positions; loom and real hatch positions are excluded by caller. */
    public static CriticalConstructionEconomy freeze(CriticalConfiguration config, List<CriticalGeometry.Voxel> paid,
        String layoutKey) {
        Map<String, ItemStack> shells = new LinkedHashMap<>();
        Map<Long, ItemStack[]> carriers = new LinkedHashMap<>();
        ItemStack controller = controller(config.kind);
        for (CriticalGeometry.Voxel voxel : paid) if (!CriticalStructures.isController(voxel)) {
            String canonical = CriticalMaterialDefinitions.canonicalId(voxel.material);
            shells.putIfAbsent(voxel.material, CriticalShellCatalog.shell(canonical, 1));
        }
        switch (config.kind) {
            case SOLAR -> {
                for (int n = 0; n < 8; n++) carrier(
                    carriers,
                    paid,
                    "petal_" + n,
                    new ItemStack[] { ItemList.Electric_Motor_UHV.get(8), ItemList.Electric_Pump_UHV.get(8),
                        ItemList.Field_Generator_UHV.get(4), ItemList.Emitter_UHV.get(8), ItemList.Sensor_UHV.get(4),
                        ore(OrePrefixes.circuit, Materials.UHV, 8),
                        ore(OrePrefixes.pipeMedium, Materials.Neutronium, 64) });
                carrier(
                    carriers,
                    paid,
                    "receiver",
                    new ItemStack[] { ItemList.Electric_Pump_UHV.get(32), ItemList.Sensor_UHV.get(16),
                        ore(OrePrefixes.circuit, Materials.UHV, 16),
                        ore(OrePrefixes.pipeMedium, Materials.Neutronium, 128),
                        ore(OrePrefixes.plate, Materials.Europium, 256) });
            }
            case TURBINE -> {
                for (int n = 0; n < 4; n++) {
                    carrier(
                        carriers,
                        paid,
                        "rotor_" + n,
                        new ItemStack[] { ItemList.Electric_Motor_UHV.get(32), ItemList.Electric_Piston_UHV.get(16),
                            ItemList.Field_Generator_UHV.get(16), ItemList.Sensor_UHV.get(8),
                            ore(OrePrefixes.circuit, Materials.UHV, 8),
                            ore(OrePrefixes.gearGt, Materials.NaquadahAlloy, 64),
                            ore(OrePrefixes.stickLong, Materials.Neutronium, 64),
                            ore(OrePrefixes.rotor, Materials.NaquadahAlloy, 128) });
                    carrier(
                        carriers,
                        paid,
                        "guide_vane_" + n,
                        new ItemStack[] { ItemList.Electric_Pump_UHV.get(16),
                            ore(OrePrefixes.circuit, Materials.UHV, 8),
                            ore(OrePrefixes.pipeMedium, Materials.Neutronium, 32) });
                }
            }
            case BATTERY -> {
                for (int n = 0; n < 4; n++) carrier(
                    carriers,
                    paid,
                    "electrode_" + n,
                    new ItemStack[] { ItemList.Electric_Pump_UHV.get(32), ItemList.Electric_Motor_UHV.get(8),
                        ItemList.Emitter_UHV.get(16), ItemList.Sensor_UHV.get(16),
                        ore(OrePrefixes.circuit, Materials.UHV, 16), ore(OrePrefixes.plate, Materials.Platinum, 256),
                        ore(OrePrefixes.plate, Materials.Iridium, 128), ore(OrePrefixes.plate, Materials.Osmium, 64),
                        CriticalShellCatalog.carbon(128), ore(OrePrefixes.pipeMedium, Materials.Neutronium, 128) });
            }
            case SUN -> {
                for (int n = 0; n < 4; n++) carrier(
                    carriers,
                    paid,
                    "confinement_" + n,
                    new ItemStack[] { ItemList.Field_Generator_UHV.get(64), ItemList.Emitter_UHV.get(32),
                        ItemList.Sensor_UHV.get(16), ore(OrePrefixes.circuit, Materials.UHV, 32),
                        ore(OrePrefixes.plate, Materials.CosmicNeutronium, 128),
                        ore(OrePrefixes.wireFine, Materials.Neutronium, 256), ItemList.Gravistar.get(64) });
            }
            default -> throw new IllegalArgumentException("Unsupported RC2 construction kind " + config.kind);
        }
        CriticalConstructionEconomy partial = new CriticalConstructionEconomy(
            layoutKey,
            controller,
            shells,
            carriers,
            new ItemStack[0]);
        return new CriticalConstructionEconomy(
            layoutKey,
            controller,
            shells,
            carriers,
            partial.costs(paid, 0, paid.size()));
    }

    private static void carrier(Map<Long, ItemStack[]> carriers, List<CriticalGeometry.Voxel> paid, String group,
        ItemStack[] bill) {
        long position = carrierPosition(paid, group);
        if (carriers.put(position, bill) != null) throw new IllegalStateException("Duplicate equipment carrier");
    }

    private static long carrierPosition(List<CriticalGeometry.Voxel> paid, String group) {
        CriticalGeometry.Voxel first = null;
        for (CriticalGeometry.Voxel voxel : paid) if (voxel.stage == 4 && group.equals(voxel.group)
            && (first == null || voxel.positionKey() < first.positionKey())) first = voxel;
        if (first == null) throw new IllegalStateException("Missing RC2 equipment carrier " + group);
        return first.positionKey();
    }

    /** Read-only layout gate, using the exact carrier selector used when freezing prices. */
    public static Map<String, Long> carrierPositions(CriticalMachineKind kind, List<CriticalGeometry.Voxel> paid) {
        Map<String, Long> result = new LinkedHashMap<>();
        String[] groups = switch (kind) {
            case SOLAR -> new String[] { "petal_0", "petal_1", "petal_2", "petal_3", "petal_4", "petal_5", "petal_6",
                "petal_7", "receiver" };
            case TURBINE -> new String[] { "rotor_0", "rotor_1", "rotor_2", "rotor_3", "guide_vane_0", "guide_vane_1",
                "guide_vane_2", "guide_vane_3" };
            case BATTERY -> new String[] { "electrode_0", "electrode_1", "electrode_2", "electrode_3" };
            case SUN -> new String[] { "confinement_0", "confinement_1", "confinement_2", "confinement_3" };
            default -> throw new IllegalArgumentException("Unsupported RC2 equipment layout " + kind);
        };
        for (String group : groups) result.put(group, carrierPosition(paid, group));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static ItemStack controller(CriticalMachineKind kind) {
        return switch (kind) {
            case SOLAR -> GTSRItemList.CriticalSolar.get(1);
            case TURBINE -> GTSRItemList.CriticalTurbine.get(1);
            case BATTERY -> GTSRItemList.CriticalBattery.get(1);
            case SUN -> GTSRItemList.CriticalSun.get(1);
            default -> throw new IllegalArgumentException("No RC2 main controller for " + kind);
        };
    }

    public String layoutKey() {
        return layoutKey;
    }

    public ItemStack[] demand() {
        return copy(demand);
    }

    ItemStack controller() {
        return controller.copy();
    }

    ItemStack[] displayDemand() {
        List<ItemStack> rows = new ArrayList<>();
        for (ItemStack item : demand) merge(rows, item);
        for (String id : CriticalShellCatalog.identities()
            .keySet()) {
            ItemStack shell = CriticalShellCatalog.shell(id, 1);
            boolean present = false;
            for (ItemStack item : rows) if (same(shell, item)) present = true;
            if (!present) {
                shell.stackSize = 0;
                rows.add(shell);
            }
        }
        return rows.toArray(new ItemStack[0]);
    }

    public ItemStack[] bill(CriticalGeometry.Voxel voxel) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack shell = CriticalStructures.isController(voxel) ? controller : shells.get(voxel.material);
        if (shell == null)
            throw new IllegalStateException("Position outside frozen RC2 material basis " + voxel.material);
        merge(result, shell);
        ItemStack[] equipment = carriers.get(voxel.positionKey());
        if (equipment != null) for (ItemStack stack : equipment) merge(result, stack);
        return result.toArray(new ItemStack[0]);
    }

    public ItemStack[] costs(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = start; i < end; i++) for (ItemStack stack : bill(voxels.get(i))) merge(result, stack);
        return result.toArray(new ItemStack[0]);
    }

    public String family(ItemStack item) {
        if (same(item, controller)) return "controller";
        for (Map.Entry<String, ItemStack> entry : shells.entrySet())
            if (same(item, entry.getValue())) return CriticalShellCatalog.family(entry.getKey())
                .name();
        for (Map.Entry<String, CriticalShellCatalog.Family> entry : CriticalShellCatalog.identities()
            .entrySet())
            if (same(item, CriticalShellCatalog.shell(entry.getKey(), 1))) return entry.getValue()
                .name();
        return "equipment";
    }

    public String identity(ItemStack item) {
        if (same(item, controller)) return "controller";
        for (Map.Entry<String, ItemStack> entry : shells.entrySet())
            if (same(item, entry.getValue())) return CriticalMaterialDefinitions.canonicalId(entry.getKey());
        for (String id : CriticalShellCatalog.identities()
            .keySet()) if (same(item, CriticalShellCatalog.shell(id, 1))) return id;
        return net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()) + ":" + item.getItemDamage();
    }

    public boolean isController(ItemStack item) {
        return same(item, controller);
    }

    void write(NBTTagCompound n) {
        n.setString("layout", layoutKey);
        n.setTag("controller", CriticalResourceNbt.writeItems(new ItemStack[] { controller }));
        n.setTag("demand", CriticalResourceNbt.writeItems(demand));
        NBTTagList materials = new NBTTagList();
        for (Map.Entry<String, ItemStack> entry : shells.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("alias", entry.getKey());
            row.setTag("item", CriticalResourceNbt.writeItems(new ItemStack[] { entry.getValue() }));
            materials.appendTag(row);
        }
        n.setTag("materials", materials);
        NBTTagList units = new NBTTagList();
        for (Map.Entry<Long, ItemStack[]> entry : carriers.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setLong("position", entry.getKey());
            row.setTag("bill", CriticalResourceNbt.writeItems(entry.getValue()));
            units.appendTag(row);
        }
        n.setTag("carriers", units);
    }

    static CriticalConstructionEconomy read(NBTTagCompound n) {
        ItemStack[] main = CriticalResourceNbt.readItems(n.getTagList("controller", 10), 1);
        if (main[0] == null || main[0].stackSize != 1
            || n.getString("layout")
                .isEmpty())
            throw new IllegalArgumentException("Incomplete frozen construction identity");
        Map<String, ItemStack> shells = new LinkedHashMap<>();
        Map<Long, ItemStack[]> carriers = new LinkedHashMap<>();
        NBTTagList materials = n.getTagList("materials", 10), units = n.getTagList("carriers", 10);
        for (int i = 0; i < materials.tagCount(); i++) {
            NBTTagCompound row = materials.getCompoundTagAt(i);
            ItemStack shell = CriticalResourceNbt.readItems(row.getTagList("item", 10), 1)[0];
            if (shell == null || shell.stackSize != 1)
                throw new IllegalArgumentException("Missing frozen shell identity");
            shells.put(row.getString("alias"), shell);
        }
        for (int i = 0; i < units.tagCount(); i++) {
            NBTTagCompound row = units.getCompoundTagAt(i);
            carriers.put(row.getLong("position"), CriticalResourceNbt.readItems(row.getTagList("bill", 10), -1));
        }
        return new CriticalConstructionEconomy(
            n.getString("layout"),
            main[0],
            shells,
            carriers,
            CriticalResourceNbt.readItems(n.getTagList("demand", 10), -1));
    }

    static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    static void merge(List<ItemStack> list, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        for (ItemStack current : list)
            if (same(current, stack) && current.stackSize <= Integer.MAX_VALUE - stack.stackSize) {
                current.stackSize += stack.stackSize;
                return;
            }
        list.add(stack.copy());
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int count) {
        ItemStack stack = GTOreDictUnificator.get(prefix, material, count);
        if (stack == null) throw new IllegalStateException("Missing RC2 equipment " + prefix + material);
        return stack;
    }
}
