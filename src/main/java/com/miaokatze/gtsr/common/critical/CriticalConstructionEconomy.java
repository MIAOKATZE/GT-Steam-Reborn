package com.miaokatze.gtsr.common.critical;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/** Immutable full-job price basis. Equipment is installed at one deterministic carrier per macro unit. */
public final class CriticalConstructionEconomy {

    public static final int VERSION = 6;
    private final int version;
    private final Set<String> installationMaterials = new HashSet<>();
    private final Map<String, FluidStack[]> materialFluids = new LinkedHashMap<>();
    private final Map<Integer, FluidStack[]> baseFluids = new LinkedHashMap<>();
    private final Map<Long, FluidStack[]> carrierFluids = new LinkedHashMap<>();
    private final Map<Long, Integer> workStages = new LinkedHashMap<>();
    private final Map<Long, FluidStack[]> positionFluids = new LinkedHashMap<>();
    private final Map<Integer, DisplayRow> stepProfiles = new LinkedHashMap<>();
    private final String layoutKey;
    private final ItemStack controller;
    private final Map<String, ItemStack> shells;
    private final Map<Long, ItemStack[]> carriers;
    private final ItemStack[] demand;

    private CriticalConstructionEconomy(String layoutKey, ItemStack controller, Map<String, ItemStack> shells,
        Map<Long, ItemStack[]> carriers, ItemStack[] demand) {
        this(2, layoutKey, controller, shells, carriers, demand);
    }

    private CriticalConstructionEconomy(int version, String layoutKey, ItemStack controller,
        Map<String, ItemStack> shells, Map<Long, ItemStack[]> carriers, ItemStack[] demand) {
        this.version = version;
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
                    new ItemStack[] { ItemList.Electric_Motor_UHV.get(4), ItemList.Electric_Pump_UHV.get(16),
                        ItemList.Field_Generator_UHV.get(2), ItemList.Emitter_UHV.get(16), ItemList.Sensor_UHV.get(4),
                        ore(OrePrefixes.circuit, Materials.UHV, 8),
                        ore(OrePrefixes.pipeMedium, Materials.Neutronium, 64) });
                carrier(
                    carriers,
                    paid,
                    "receiver",
                    new ItemStack[] { ItemList.Electric_Pump_UHV.get(48), ItemList.Sensor_UHV.get(16),
                        ore(OrePrefixes.circuit, Materials.UHV, 16),
                        quantity(ore(OrePrefixes.pipeMedium, Materials.Neutronium, 1), 96),
                        ore(OrePrefixes.plate, Materials.Europium, 256) });
            }
            case TURBINE -> {
                for (int n = 0; n < 4; n++) {
                    carrier(
                        carriers,
                        paid,
                        "rotor_" + n,
                        new ItemStack[] { ItemList.Electric_Motor_UHV.get(48), ItemList.Electric_Piston_UHV.get(32),
                            ItemList.Field_Generator_UHV.get(8), ItemList.Sensor_UHV.get(8),
                            ore(OrePrefixes.circuit, Materials.UHV, 8),
                            quantity(ore(OrePrefixes.gearGt, Materials.NaquadahAlloy, 1), 96),
                            quantity(ore(OrePrefixes.stickLong, Materials.Neutronium, 1), 96),
                            quantity(ore(OrePrefixes.rotor, Materials.NaquadahAlloy, 1), 96) });
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
                    new ItemStack[] { ItemList.Electric_Pump_UHV.get(48), ItemList.Electric_Motor_UHV.get(16),
                        ItemList.Emitter_UHV.get(8), ItemList.Sensor_UHV.get(16),
                        ore(OrePrefixes.circuit, Materials.UHV, 16),
                        quantity(ore(OrePrefixes.plate, Materials.Platinum, 1), 96),
                        quantity(ore(OrePrefixes.plate, Materials.Iridium, 1), 96),
                        ore(OrePrefixes.plate, Materials.Osmium, 64), CriticalShellCatalog.carbon(128),
                        ore(OrePrefixes.pipeMedium, Materials.Neutronium, 128) });
            }
            case SUN -> {
                for (int n = 0; n < 4; n++) carrier(
                    carriers,
                    paid,
                    "confinement_" + n,
                    new ItemStack[] { quantity(ItemList.Field_Generator_UHV.get(1), 96), ItemList.Emitter_UHV.get(48),
                        ItemList.Sensor_UHV.get(16), ore(OrePrefixes.circuit, Materials.UHV, 32),
                        quantity(ore(OrePrefixes.plate, Materials.CosmicNeutronium, 1), 96),
                        ore(OrePrefixes.wireFine, Materials.Neutronium, 256),
                        quantity(ItemList.Gravistar.get(1), 96) });
            }
            default -> throw new IllegalArgumentException("Unsupported RC2 construction kind " + config.kind);
        }
        CriticalConstructionEconomy partial = new CriticalConstructionEconomy(
            layoutKey,
            controller,
            shells,
            carriers,
            new ItemStack[0]);
        CriticalConstructionEconomy result = new CriticalConstructionEconomy(
            VERSION,
            layoutKey,
            controller,
            shells,
            carriers,
            partial.costs(paid, 0, paid.size()));
        // UV fusion plasmas join/condition the structure; UHV grade-7 water flushes and cools the work.
        // Volumes are per paid position, except equipment recipes which belong to one macro-unit carrier.
        result.baseFluids.put(1, media(9, 0, 0, 125));
        result.baseFluids.put(2, media(0, 9, 0, 125));
        result.baseFluids.put(3, media(9, 9, 4, 125));
        for (String material : shells.keySet()) {
            CriticalShellCatalog.Family family = CriticalShellCatalog.family(material);
            boolean structural = family == CriticalShellCatalog.Family.S || family == CriticalShellCatalog.Family.H;
            if (!structural) result.installationMaterials.add(material);
            result.materialFluids.put(material, switch (family) {
                case S, H -> media(9, 9, 0, 125);
                case E -> media(18, 18, 0, 250);
                case O -> media(9, 0, 9, 250);
                case F -> media(0, 18, 4, 250);
                case M -> media(9, 18, 0, 125);
                case C -> media(18, 18, 0, 250);
                case G -> media(18, 36, 18, 500);
            });
        }
        for (Long position : carriers.keySet()) result.carrierFluids.put(position, switch (config.kind) {
            case SOLAR -> media(1152, 576, 144, 8000);
            case TURBINE -> media(576, 1152, 72, 16000);
            case BATTERY -> media(2304, 1152, 144, 16000);
            case SUN -> media(1152, 2304, 1152, 32000);
            default -> throw new IllegalArgumentException("Unsupported construction equipment " + config.kind);
        });
        return result.threeRowSchedule(paid, config.kind);
    }

    /** Silver: conductive joining; bismuth: active joining; radon: surface conditioning; grade 7: flush/cooling. */
    private static FluidStack[] media(int silver, int bismuth, int radon, int water) {
        List<FluidStack> fluids = new ArrayList<>();
        if (silver > 0) fluids.add(required(Materials.Silver.getPlasma(silver), "silver plasma"));
        if (bismuth > 0) fluids.add(required(Materials.Bismuth.getPlasma(bismuth), "bismuth plasma"));
        if (radon > 0) fluids.add(required(Materials.Radon.getPlasma(radon), "radon plasma"));
        if (water > 0) fluids.add(required(Materials.Grade7PurifiedWater.getFluid(water), "grade 7 purified water"));
        return fluids.toArray(new FluidStack[0]);
    }

    public int version() {
        return version;
    }

    static boolean supportsVersion(int version) {
        return version >= 2 && version <= VERSION;
    }

    /** Frozen work order; the geometry and ownership fingerprint never change. Legacy jobs retain stage 4. */
    public int stage(CriticalGeometry.Voxel voxel) {
        if (version >= 5 && !workStages.isEmpty()) {
            // User-built loom/hatch positions were excluded when the complete price basis was frozen.
            return workStages.getOrDefault(voxel.positionKey(), 0);
        }
        if (version < 3 || voxel.stage != 4) return voxel.stage;
        return CriticalStructures.isController(voxel) || carriers.containsKey(voxel.positionKey())
            || installationMaterials.contains(voxel.material) ? 5 : 4;
    }

    public FluidStack[] fluidCosts(List<CriticalGeometry.Voxel> voxels, int start, int end) {
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = start; i < end; i++) {
            CriticalGeometry.Voxel voxel = voxels.get(i);
            if (version >= 5 && !workStages.isEmpty()) {
                FluidStack[] frozen = positionFluids.get(voxel.positionKey());
                if (frozen != null) for (FluidStack fluid : frozen) mergeFluid(fluids, fluid);
                continue;
            }
            FluidStack[] shell = version >= 4 && voxel.stage >= 1 && voxel.stage <= 3 ? baseFluids.get(voxel.stage)
                : materialFluids.get(voxel.material);
            if (!CriticalStructures.isController(voxel) && shell != null)
                for (FluidStack fluid : shell) mergeFluid(fluids, fluid);
            FluidStack[] equipment = carrierFluids.get(voxel.positionKey());
            if (equipment != null) for (FluidStack fluid : equipment) mergeFluid(fluids, fluid);
        }
        return fluids.toArray(new FluidStack[0]);
    }

    /** Three actual payment/work rows per macro stage. No display-only truncation or hidden continuation. */
    public ItemStack[] itemsForDisplayRow(int group, int row, List<CriticalGeometry.Voxel> paid) {
        checkDisplayRow(group, row);
        if (group == 0) return row == 0 ? CriticalFoundationMaterials.items(true) : new ItemStack[0];
        List<CriticalGeometry.Voxel> selected = displayRow(group, row, paid);
        return costs(selected, 0, selected.size());
    }

    public FluidStack[] fluidsForDisplayRow(int group, int row, List<CriticalGeometry.Voxel> paid) {
        checkDisplayRow(group, row);
        if (group == 0) return row == 0 ? new FluidStack[] { CriticalFoundationMaterials.fluid(1_000_000) }
            : row == 1 ? new FluidStack[] { gregtech.api.util.GTModHandler.getSuperHeatedSteam(1_800_000_000) }
                : new FluidStack[0];
        List<CriticalGeometry.Voxel> selected = displayRow(group, row, paid);
        return fluidCosts(selected, 0, selected.size());
    }

    private static void checkDisplayRow(int group, int row) {
        if (group < 0 || group > 2 || row < 0 || row > 2)
            throw new IllegalArgumentException("Invalid construction row");
    }

    private List<CriticalGeometry.Voxel> displayRow(int group, int row, List<CriticalGeometry.Voxel> paid) {
        if (version < 5) throw new IllegalStateException("Legacy jobs retain their original payment stages");
        int wanted = (group == 1 ? 4 : 7) + row;
        List<CriticalGeometry.Voxel> selected = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid) if (stage(voxel) == wanted) selected.add(voxel);
        return selected;
    }

    /** Same-family finishes share one real manufactured standard part, one part per physical position. */
    private CriticalConstructionEconomy threeRowSchedule(List<CriticalGeometry.Voxel> paid, CriticalMachineKind kind) {
        Map<String, ItemStack> standard = new LinkedHashMap<>();
        for (String alias : shells.keySet())
            standard.put(alias, CriticalShellCatalog.shell(CriticalShellCatalog.family(alias).identities.get(0), 1));
        Map<Long, ItemStack[]> equipment = new LinkedHashMap<>();
        // Keep each macro unit's complete equipment count. Each component gets a genuine deterministic
        // installation carrier of the same shell family, instead of hiding a ten-item bill in one row.
        for (Map.Entry<Long, ItemStack[]> unit : carriers.entrySet()) {
            CriticalGeometry.Voxel anchor = null;
            for (CriticalGeometry.Voxel voxel : paid) if (voxel.positionKey() == unit.getKey()) anchor = voxel;
            if (anchor == null) throw new IllegalStateException("Missing equipment anchor");
            List<CriticalGeometry.Voxel> candidates = new ArrayList<>();
            for (CriticalGeometry.Voxel voxel : paid) if (voxel.stage == 4 && voxel.group.equals(anchor.group)
                && !CriticalStructures.isController(voxel)
                && same(standard.get(voxel.material), standard.get(anchor.material))) candidates.add(voxel);
            candidates.sort(java.util.Comparator.comparingLong(CriticalGeometry.Voxel::positionKey));
            if (candidates.size() < unit.getValue().length)
                throw new IllegalStateException("Insufficient equipment carriers");
            for (int i = 0; i < unit.getValue().length; i++) {
                long key = candidates.get(i)
                    .positionKey();
                if (equipment.put(key, new ItemStack[] { unit.getValue()[i].copy() }) != null)
                    throw new IllegalStateException("Overlapping equipment carriers");
            }
        }
        CriticalConstructionEconomy basis = new CriticalConstructionEconomy(
            VERSION,
            layoutKey,
            controller,
            standard,
            equipment,
            new ItemStack[0]);
        CriticalConstructionEconomy next = new CriticalConstructionEconomy(
            VERSION,
            layoutKey,
            controller,
            standard,
            equipment,
            basis.costs(paid, 0, paid.size()));
        next.baseFluids.putAll(baseFluids);
        next.materialFluids.putAll(materialFluids);
        List<CriticalGeometry.Voxel> body = new ArrayList<>(), installation = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid)
            (CriticalStructures.isController(voxel) || equipment.containsKey(voxel.positionKey()) ? installation : body)
                .add(voxel);
        next.assignRows(body, 4);
        next.assignRows(installation, 7);
        // Each new job freezes a local machine-specific commissioning recipe at every position.
        for (CriticalGeometry.Voxel voxel : paid)
            next.positionFluids.put(voxel.positionKey(), commissioningMedia(kind, next.stage(voxel)));
        for (int group = 1; group <= 2; group++) for (int row = 0; row < 3; row++) {
            List<CriticalGeometry.Voxel> positions = next.displayRow(group, row, paid);
            ItemStack[] items = next.costs(positions, 0, positions.size());
            FluidStack[] fluids = next.fluidCosts(positions, 0, positions.size());
            int steps = group == 2 && row == 2 ? 1 : exactStepCount(items, fluids);
            next.stepProfiles
                .put((group == 1 ? 4 : 7) + row, new DisplayRow(steps, divide(items, steps), divide(fluids, steps)));
        }
        for (int group = 1; group <= 2; group++)
            for (int row = 0; row < 3; row++) if (next.itemsForDisplayRow(group, row, paid).length > 6
                || next.fluidsForDisplayRow(group, row, paid).length > 2)
                throw new IllegalStateException("Construction row exceeds six items/two fluids");
        return next;
    }

    private void assignRows(List<CriticalGeometry.Voxel> voxels, int firstStage) {
        List<List<ItemStack>> identities = new ArrayList<>();
        for (int row = 0; row < 3; row++) identities.add(new ArrayList<>());
        for (CriticalGeometry.Voxel voxel : voxels) {
            ItemStack[] bill = bill(voxel);
            int row;
            if (firstStage == 4) row = switch (CriticalShellCatalog.family(voxel.material)) {
                case S, H -> 0;
                case E, F -> 1;
                case O, G, M, C -> 2;
            };
            else row = CriticalStructures.isController(voxel) ? 2 : equipmentRow(carriers.get(voxel.positionKey())[0]);
            for (ItemStack item : bill) merge(identities.get(row), item);
            workStages.put(voxel.positionKey(), firstStage + row);
        }
        for (List<ItemStack> row : identities)
            if (row.isEmpty() || row.size() > 6) throw new IllegalStateException("Invalid semantic construction row");
    }

    /** Mechanical support, active work components, then sensing/wiring: exact manufactured identities. */
    private static int equipmentRow(ItemStack item) {
        if (same(item, ItemList.Electric_Motor_UHV.get(1)) || same(item, ItemList.Electric_Piston_UHV.get(1))
            || same(item, ore(OrePrefixes.gearGt, Materials.NaquadahAlloy, 1))
            || same(item, ore(OrePrefixes.stickLong, Materials.Neutronium, 1))
            || same(item, ore(OrePrefixes.rotor, Materials.NaquadahAlloy, 1))
            || same(item, ore(OrePrefixes.plate, Materials.Europium, 1))
            || same(item, ore(OrePrefixes.plate, Materials.Platinum, 1))
            || same(item, ore(OrePrefixes.plate, Materials.Iridium, 1))
            || same(item, ore(OrePrefixes.plate, Materials.Osmium, 1))
            || same(item, ore(OrePrefixes.plate, Materials.CosmicNeutronium, 1))
            || same(item, CriticalShellCatalog.carbon(1))) return 0;
        if (same(item, ItemList.Electric_Pump_UHV.get(1)) || same(item, ItemList.Field_Generator_UHV.get(1))
            || same(item, ItemList.Emitter_UHV.get(1))
            || same(item, ItemList.Gravistar.get(1))
            || same(item, ore(OrePrefixes.pipeMedium, Materials.Neutronium, 1))) return 1;
        if (same(item, ItemList.Sensor_UHV.get(1)) || same(item, ore(OrePrefixes.circuit, Materials.UHV, 1))
            || same(item, ore(OrePrefixes.wireFine, Materials.Neutronium, 1))) return 2;
        throw new IllegalArgumentException("Unclassified construction equipment");
    }

    /** Joining, heat exchange, catalyst preparation and confinement use distinct real process media. */
    private static FluidStack[] commissioningMedia(CriticalMachineKind kind, int stage) {
        int row = stage - 4;
        switch (kind) {
            case SOLAR:
                switch (row) {
                    case 0:
                        return pair(Materials.Silver.getPlasma(9), Materials.Grade7PurifiedWater.getFluid(125));
                    case 1:
                        return pair(
                            Materials.SodiumPotassium.getFluid(250),
                            Materials.Grade8PurifiedWater.getFluid(125));
                    case 2:
                        return pair(Materials.Radon.getPlasma(9), Materials.Argon.getGas(125));
                    case 3:
                        return pair(Materials.Lubricant.getFluid(250), Materials.Silver.getPlasma(144));
                    case 4:
                        return pair(
                            Materials.SodiumPotassium.getFluid(1000),
                            Materials.Grade7PurifiedWater.getFluid(500));
                    default:
                        return pair(
                            Materials.SolderingAlloy.getMolten(144),
                            Materials.Grade7PurifiedWater.getFluid(1000));
                }
            case TURBINE:
                switch (row) {
                    case 0:
                        return pair(Materials.Bismuth.getPlasma(9), Materials.Grade7PurifiedWater.getFluid(125));
                    case 1:
                        return pair(Materials.Lubricant.getFluid(250), Materials.Grade7PurifiedWater.getFluid(250));
                    case 2:
                        return pair(Materials.Silver.getPlasma(18), Materials.Argon.getGas(125));
                    case 3:
                        return pair(Materials.Lubricant.getFluid(1000), Materials.Bismuth.getPlasma(144));
                    case 4:
                        return pair(
                            Materials.SodiumPotassium.getFluid(500),
                            Materials.Grade7PurifiedWater.getFluid(1000));
                    default:
                        return pair(
                            Materials.SolderingAlloy.getMolten(144),
                            Materials.Grade7PurifiedWater.getFluid(1000));
                }
            case BATTERY:
                switch (row) {
                    case 0:
                        return pair(Materials.Silver.getPlasma(18), Materials.Grade7PurifiedWater.getFluid(250));
                    case 1:
                        return pair(Materials.Platinum.getMolten(144), Materials.Grade7PurifiedWater.getFluid(250));
                    case 2:
                        return pair(Materials.Radon.getPlasma(9), Materials.Nitrogen.getGas(250));
                    case 3:
                        return pair(Materials.Platinum.getMolten(144), Materials.Grade8PurifiedWater.getFluid(1000));
                    case 4:
                        return pair(
                            Materials.SulfuricAcid.getFluid(1000),
                            Materials.Grade7PurifiedWater.getFluid(1000));
                    default:
                        return pair(
                            Materials.SolderingAlloy.getMolten(144),
                            Materials.Grade7PurifiedWater.getFluid(500));
                }
            case SUN:
                switch (row) {
                    case 0:
                        return pair(Materials.Bismuth.getPlasma(18), Materials.Grade7PurifiedWater.getFluid(500));
                    case 1:
                        return pair(Materials.Helium.getGas(1000), Materials.Radon.getPlasma(18));
                    case 2:
                        return pair(Materials.Silver.getPlasma(36), Materials.Argon.getGas(500));
                    case 3:
                        return pair(Materials.Naquadria.getMolten(144), Materials.Grade8PurifiedWater.getFluid(1000));
                    case 4:
                        return pair(Materials.Helium.getGas(2000), Materials.Radon.getPlasma(144));
                    default:
                        return pair(Materials.SolderingAlloy.getMolten(288), Materials.Radon.getPlasma(144));
                }
            default:
                throw new IllegalArgumentException("Unsupported construction process " + kind);
        }
    }

    private static FluidStack[] pair(FluidStack first, FluidStack second) {
        return new FluidStack[] { required(first, "commissioning first medium"),
            required(second, "commissioning second medium") };
    }

    /** One exact repeated work unit; callers never receive the mutable frozen arrays. */
    public static final class DisplayRow {

        public final int steps;
        private final ItemStack[] items;
        private final FluidStack[] fluids;

        private DisplayRow(int steps, ItemStack[] items, FluidStack[] fluids) {
            this.steps = steps;
            this.items = copy(items);
            this.fluids = copyFluids(fluids);
        }

        public ItemStack[] perStepItems() {
            return copy(items);
        }

        public FluidStack[] perStepFluids() {
            return copyFluids(fluids);
        }
    }

    public DisplayRow displayRowProfile(int group, int row, List<CriticalGeometry.Voxel> paid) {
        checkDisplayRow(group, row);
        if (version < 6 || group == 0)
            throw new IllegalStateException("Repeated work profiles require a v6 body/equipment row");
        return stepProfiles.get((group == 1 ? 4 : 7) + row);
    }

    /** Complete rows retain the frozen count; repairs derive a count from their own local bill. */
    int stepsForStage(int stage, ItemStack[] items, FluidStack[] fluids) {
        DisplayRow frozen = stepProfiles.get(stage);
        if (frozen != null && frozen.items.length == items.length && frozen.fluids.length == fluids.length) {
            boolean exact = true;
            for (ItemStack item : items) {
                boolean found = false;
                for (ItemStack unit : frozen.items)
                    if (same(item, unit) && (long) unit.stackSize * frozen.steps == item.stackSize) found = true;
                if (!found) exact = false;
            }
            for (FluidStack fluid : fluids) {
                boolean found = false;
                for (FluidStack unit : frozen.fluids)
                    if (fluid.isFluidEqual(unit) && (long) unit.amount * frozen.steps == fluid.amount) found = true;
                if (!found) exact = false;
            }
            if (exact) return frozen.steps;
        }
        return stage == 9 ? 1 : exactStepCount(items, fluids);
    }

    /** Largest divisor at most 200: capping a larger GCD with min would silently lose units. */
    static int exactStepCount(ItemStack[] items, FluidStack[] fluids) {
        if (items.length == 0 && fluids.length == 0) return 1;
        for (int steps = 200; steps > 1; steps--) {
            boolean exact = true;
            for (ItemStack item : items) if (item.stackSize % steps != 0) exact = false;
            for (FluidStack fluid : fluids) if (fluid.amount % steps != 0) exact = false;
            if (exact) return steps;
        }
        return 1;
    }

    private static ItemStack[] divide(ItemStack[] totals, int steps) {
        ItemStack[] result = copy(totals);
        for (ItemStack item : result) item.stackSize /= steps;
        return result;
    }

    private static FluidStack[] divide(FluidStack[] totals, int steps) {
        FluidStack[] result = copyFluids(totals);
        for (FluidStack fluid : result) fluid.amount /= steps;
        return result;
    }

    private static FluidStack[] copyFluids(FluidStack[] source) {
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i].copy();
        return result;
    }

    private static FluidStack required(FluidStack fluid, String name) {
        if (fluid == null || fluid.amount <= 0) throw new IllegalStateException("Missing construction fluid " + name);
        return fluid.copy();
    }

    private static void mergeFluid(List<FluidStack> fluids, FluidStack fluid) {
        for (FluidStack current : fluids) if (current.isFluidEqual(fluid)) {
            current.amount = Math.addExact(current.amount, fluid.amount);
            return;
        }
        fluids.add(fluid.copy());
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
        for (Map.Entry<String, ItemStack> entry : shells.entrySet()) if (same(item, entry.getValue()))
            return version >= 5 ? CriticalShellCatalog.family(entry.getKey()).identities.get(0)
                : CriticalMaterialDefinitions.canonicalId(entry.getKey());
        for (String id : CriticalShellCatalog.identities()
            .keySet()) if (same(item, CriticalShellCatalog.shell(id, 1))) return id;
        return net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()) + ":" + item.getItemDamage();
    }

    public boolean isController(ItemStack item) {
        return same(item, controller);
    }

    void write(NBTTagCompound n) {
        n.setInteger("version", version);
        n.setString("layout", layoutKey);
        n.setTag("controller", CriticalResourceNbt.writeItems(new ItemStack[] { controller }));
        n.setTag("demand", CriticalResourceNbt.writeItems(demand));
        NBTTagList materials = new NBTTagList();
        for (Map.Entry<String, ItemStack> entry : shells.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("alias", entry.getKey());
            row.setBoolean("installation", installationMaterials.contains(entry.getKey()));
            row.setTag(
                "fluids",
                CriticalResourceNbt.writeFluids(materialFluids.getOrDefault(entry.getKey(), new FluidStack[0])));
            row.setTag("item", CriticalResourceNbt.writeItems(new ItemStack[] { entry.getValue() }));
            materials.appendTag(row);
        }
        n.setTag("materials", materials);
        NBTTagList units = new NBTTagList();
        for (Map.Entry<Long, ItemStack[]> entry : carriers.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setLong("position", entry.getKey());
            row.setTag(
                "fluids",
                CriticalResourceNbt.writeFluids(carrierFluids.getOrDefault(entry.getKey(), new FluidStack[0])));
            row.setTag("bill", CriticalResourceNbt.writeItems(entry.getValue()));
            units.appendTag(row);
        }
        n.setTag("carriers", units);
        if (version >= 6) {
            NBTTagList steps = new NBTTagList();
            for (Map.Entry<Integer, DisplayRow> entry : stepProfiles.entrySet()) {
                NBTTagCompound row = new NBTTagCompound();
                row.setInteger("stage", entry.getKey());
                row.setInteger("steps", entry.getValue().steps);
                row.setTag(
                    "items",
                    CriticalResourceNbt.writeItems(
                        entry.getValue()
                            .perStepItems()));
                row.setTag(
                    "fluids",
                    CriticalResourceNbt.writeFluids(
                        entry.getValue()
                            .perStepFluids()));
                steps.appendTag(row);
            }
            n.setTag("stepProfiles", steps);
        }
        if (version >= 4) {
            NBTTagList bases = new NBTTagList();
            for (Map.Entry<Integer, FluidStack[]> entry : baseFluids.entrySet()) {
                NBTTagCompound row = new NBTTagCompound();
                row.setInteger("stage", entry.getKey());
                row.setTag("fluids", CriticalResourceNbt.writeFluids(entry.getValue()));
                bases.appendTag(row);
            }
            n.setTag("baseFluids", bases);
        }
        if (version >= 5) {
            NBTTagList schedule = new NBTTagList();
            for (Map.Entry<Long, Integer> entry : workStages.entrySet()) {
                NBTTagCompound row = new NBTTagCompound();
                row.setLong("position", entry.getKey());
                row.setInteger("stage", entry.getValue());
                row.setTag(
                    "fluids",
                    CriticalResourceNbt.writeFluids(positionFluids.getOrDefault(entry.getKey(), new FluidStack[0])));
                schedule.appendTag(row);
            }
            n.setTag("workSchedule", schedule);
        }
    }

    static CriticalConstructionEconomy read(NBTTagCompound n) {
        int version = n.hasKey("version") ? n.getInteger("version") : 2;
        if (!supportsVersion(version)) throw new IllegalArgumentException("Unsupported frozen economy version");
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
        CriticalConstructionEconomy result = new CriticalConstructionEconomy(
            version,
            n.getString("layout"),
            main[0],
            shells,
            carriers,
            CriticalResourceNbt.readItems(n.getTagList("demand", 10), -1));
        if (version >= 3) {
            for (int i = 0; i < materials.tagCount(); i++) {
                NBTTagCompound row = materials.getCompoundTagAt(i);
                String alias = row.getString("alias");
                if (row.getBoolean("installation")) result.installationMaterials.add(alias);
                result.materialFluids.put(alias, readFrozenFluids(row));
            }
            for (int i = 0; i < units.tagCount(); i++) {
                NBTTagCompound row = units.getCompoundTagAt(i);
                result.carrierFluids.put(
                    row.getLong("position"),
                    version >= 5 ? CriticalResourceNbt.readFluids(row.getTagList("fluids", 10), -1)
                        : readFrozenFluids(row));
            }
        }
        if (version >= 4) {
            NBTTagList bases = n.getTagList("baseFluids", 10);
            for (int i = 0; i < bases.tagCount(); i++) {
                NBTTagCompound row = bases.getCompoundTagAt(i);
                int stage = row.getInteger("stage");
                if (stage < 1 || stage > 3 || result.baseFluids.put(stage, readFrozenFluids(row)) != null)
                    throw new IllegalArgumentException("Invalid frozen base fluid stage");
            }
            if (result.baseFluids.size() != 3) throw new IllegalArgumentException("Incomplete frozen base fluids");
        }
        if (version >= 5) {
            NBTTagList schedule = n.getTagList("workSchedule", 10);
            if (schedule.tagCount() == 0) throw new IllegalArgumentException("Missing frozen construction schedule");
            for (int i = 0; i < schedule.tagCount(); i++) {
                NBTTagCompound row = schedule.getCompoundTagAt(i);
                long position = row.getLong("position");
                int stage = row.getInteger("stage");
                if (stage < 4 || stage > 9 || result.workStages.put(position, stage) != null)
                    throw new IllegalArgumentException("Invalid frozen construction schedule");
                NBTTagList tags = row.getTagList("fluids", 10);
                FluidStack[] fluids = CriticalResourceNbt.readFluids(tags, -1);
                if (fluids.length != tags.tagCount() || fluids.length > 2)
                    throw new IllegalArgumentException("Invalid frozen construction row fluids");
                if (fluids.length > 0) result.positionFluids.put(position, fluids);
            }
            long expectedPositions = 0;
            for (ItemStack item : result.demand) {
                boolean physical = result.isController(item);
                for (ItemStack shell : shells.values()) if (same(item, shell)) physical = true;
                if (physical) expectedPositions += item.stackSize;
            }
            if (expectedPositions != result.workStages.size())
                throw new IllegalArgumentException("Incomplete frozen construction positions");
            for (Long position : carriers.keySet())
                if (!result.workStages.containsKey(position) || result.workStages.get(position) < 7)
                    throw new IllegalArgumentException("Equipment outside frozen installation rows");
            for (int stage = 4; stage <= 9; stage++) {
                boolean present = false;
                List<FluidStack> fluids = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : result.workStages.entrySet()) if (entry.getValue() == stage) {
                    present = true;
                    FluidStack[] bill = result.positionFluids.get(entry.getKey());
                    if (bill != null) for (FluidStack fluid : bill) mergeFluid(fluids, fluid);
                }
                if (!present || fluids.size() > 2) throw new IllegalArgumentException("Invalid frozen work row");
            }
        }
        if (version >= 6) {
            NBTTagList steps = n.getTagList("stepProfiles", 10);
            List<ItemStack> totalItems = new ArrayList<>();
            for (int i = 0; i < steps.tagCount(); i++) {
                NBTTagCompound row = steps.getCompoundTagAt(i);
                int stage = row.getInteger("stage"), count = row.getInteger("steps");
                ItemStack[] items = CriticalResourceNbt.readItems(row.getTagList("items", 10), -1);
                FluidStack[] fluids = readFrozenFluids(row);
                if (stage < 4 || stage > 9
                    || count < 1
                    || count > 200
                    || stage == 9 && count != 1
                    || items.length == 0
                    || items.length > 6
                    || fluids.length > 2
                    || result.stepProfiles.put(stage, new DisplayRow(count, items, fluids)) != null)
                    throw new IllegalArgumentException("Invalid frozen repeated work profile");
                for (ItemStack item : items) {
                    if (item == null || item.stackSize <= 0)
                        throw new IllegalArgumentException("Invalid repeated item debit");
                    ItemStack total = item.copy();
                    total.stackSize = Math.multiplyExact(total.stackSize, count);
                    merge(totalItems, total);
                }
                List<FluidStack> actual = new ArrayList<>();
                for (Map.Entry<Long, Integer> position : result.workStages.entrySet())
                    if (position.getValue() == stage) {
                        FluidStack[] local = result.positionFluids.get(position.getKey());
                        if (local != null) for (FluidStack fluid : local) mergeFluid(actual, fluid);
                    }
                if (actual.size() != fluids.length)
                    throw new IllegalArgumentException("Repeated fluid identity mismatch");
                for (FluidStack fluid : fluids) {
                    boolean matches = false;
                    for (FluidStack total : actual)
                        if (fluid.isFluidEqual(total) && (long) fluid.amount * count == total.amount) matches = true;
                    if (!matches) throw new IllegalArgumentException("Repeated fluid total mismatch");
                }
            }
            if (result.stepProfiles.size() != 6 || totalItems.size() != result.demand.length)
                throw new IllegalArgumentException("Incomplete frozen repeated work profiles");
            for (ItemStack wanted : result.demand) {
                boolean matches = false;
                for (ItemStack actual : totalItems)
                    if (same(wanted, actual) && wanted.stackSize == actual.stackSize) matches = true;
                if (!matches) throw new IllegalArgumentException("Repeated item total mismatch");
            }
        }
        return result;
    }

    private static FluidStack[] readFrozenFluids(NBTTagCompound row) {
        NBTTagList tags = row.getTagList("fluids", 10);
        FluidStack[] fluids = CriticalResourceNbt.readFluids(tags, -1);
        if (tags.tagCount() == 0 || fluids.length != tags.tagCount())
            throw new IllegalArgumentException("Incomplete frozen construction fluids");
        for (FluidStack fluid : fluids) if (fluid == null || fluid.amount <= 0)
            throw new IllegalArgumentException("Missing frozen construction fluid identity");
        return fluids;
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

    /** GT's ore lookup may cap requests at 64; this is an exact ledger quantity, not an inventory stack. */
    private static ItemStack quantity(ItemStack identity, int count) {
        ItemStack item = identity.copy();
        item.stackSize = count;
        return item;
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int count) {
        ItemStack stack = GTOreDictUnificator.get(prefix, material, count);
        if (stack == null) throw new IllegalStateException("Missing RC2 equipment " + prefix + material);
        return stack;
    }
}
