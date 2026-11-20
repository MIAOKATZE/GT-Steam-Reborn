// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.orecenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Frame;
import com.miaokatze.gtsr.common.machine.orecenter.CenterStructure.Shape;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility.ItemId;

/** Derives the North-facing test fixture's journal footprint from the shipped layout. */
public final class CenterFixtureGeometry {

    private CenterFixtureGeometry() {}

    public static List<int[]> oneSector() {
        List<int[]> positions = new ArrayList<>();
        add(positions, "tower-service-0", 0, -2, 0, 0);
        add(positions, "outer-0", 0, -2, 0, 0);
        addModule(positions, 1, "crusher");
        addModule(positions, 3, "logistics");
        return positions;
    }

    public static List<int[]> fullCenter() {
        List<int[]> positions = new ArrayList<>();
        add(positions, "tower-service-1", 0, -2, 0, 0);
        add(positions, "outer-full", 0, -2, 0, 0);
        addModule(positions, 1, "crusher");
        addModule(positions, 3, "logistics");
        return positions;
    }

    public static List<int[]> fullPreset(CenterModule[] modules) {
        List<int[]> positions = new ArrayList<>();
        add(positions, "tower-service-3", 0, -2, 0, 0);
        add(positions, "outer-full", 0, -2, 0, 0);
        for (int slot = 0; slot < modules.length; slot++) {
            if (modules[slot] == null) continue;
            int[] at = CenterStructure.SLOTS[slot];
            String key = modules[slot].key.toLowerCase(java.util.Locale.ROOT);
            add(positions, "module-" + slot % 6 + "-" + key + "-3", at[0], at[1] - 66, at[2], at[3]);
        }
        return positions;
    }

    public static List<int[]> towerPorts() {
        Shape tower = CenterStructure.optionalShape("tower-service-3");
        List<int[]> slots = new ArrayList<>();
        for (int i = tower.count() - 1; i >= 128; i--) if (CenterStructure.portCasing(tower, i))
            slots.add(new int[] { tower.x[i], tower.y[i] - 2, tower.z[i] + 19 });
        return slots;
    }

    public static List<ItemStack> presetFeed(CenterGraph graph, int count, long voltage) {
        Map<ItemId, ItemStack> ores = new LinkedHashMap<>();
        Map<ItemId, ItemStack> variants = new LinkedHashMap<>();
        Set<ItemId> visited = new HashSet<>();
        Set<String> materials = new HashSet<>();
        String[] names = OreDictionary.getOreNames();
        Arrays.sort(
            names,
            Comparator.<String, Boolean>comparing(name -> !name.startsWith("rawOre"))
                .thenComparing(Comparator.naturalOrder()));
        for (String name : names) {
            if (!name.startsWith("rawOre") && !name.startsWith("ore") && !name.startsWith("variantOre")) continue;
            for (ItemStack ore : OreDictionary.getOres(name, false)) {
                if (ore == null || ore.getItem() == null || ore.getItemDamage() == OreDictionary.WILDCARD_VALUE)
                    continue;
                ItemId key = CenterPlanner.itemKey(ore);
                if (!visited.add(key)) continue;
                String material = oreMaterial(ore);
                if (variants.size() >= count && materials.contains(material)) continue;
                String[] tags = CenterGraph.tags(ore);
                if (!graph.matches(ore, tags)) continue;
                CenterProcess process = graph.nodes.get(graph.entry(ore, tags)).process;
                if (CenterPlanner.findRecipe(
                    process.recipes(),
                    new ItemStack[] { ore },
                    new net.minecraftforge.fluids.FluidStack[0],
                    CenterPlanner.recipeVoltage(process, 3, voltage),
                    CenterPlanner.HEAT[3]) == null) continue;
                if (materials.add(material)) {
                    ores.put(key, ore.copy());
                    if (ores.size() == count) return new ArrayList<>(ores.values());
                } else if (variants.size() < count) variants.put(key, ore.copy());
            }
        }
        for (var entry : variants.entrySet()) {
            ores.put(entry.getKey(), entry.getValue());
            if (ores.size() == count) return new ArrayList<>(ores.values());
        }
        throw new IllegalStateException("Only " + ores.size() + " registered ores match the native preset entrance");
    }

    public static String oreMaterial(ItemStack ore) {
        var associated = GTOreDictUnificator.getAssociation(ore);
        if (associated != null && associated.mMaterial != null && associated.mMaterial.mMaterial != null)
            return associated.mMaterial.mMaterial.mName;
        for (String tag : CenterGraph.tags(ore)) {
            if (tag.startsWith("rawOre")) return tag.substring(6);
            if (tag.startsWith("variantOre")) return tag.substring(10);
            if (tag.startsWith("ore")) return tag.substring(3);
        }
        return "";
    }

    public static final class SaturationLoad {

        public final CenterPlanner.Batch batch;
        public final int oresPerMaterial, executions, workLimit, rejectedOresPerMaterial;

        private SaturationLoad(CenterPlanner.Session session, int oresPerMaterial, int rejectedOresPerMaterial) {
            this.batch = session.batch();
            this.oresPerMaterial = oresPerMaterial;
            this.executions = session.executions();
            this.workLimit = session.workLimit();
            this.rejectedOresPerMaterial = rejectedOresPerMaterial;
        }
    }

    /** Use equal lots of all 256 ores and bracket the native work cap without any warehouse limit. */
    public static SaturationLoad calibrateSaturation(CenterGraph graph, List<ItemStack> ores, CenterModule[] modules,
        long voltage, long inputEu) {
        int[][] grades = new int[CenterModule.values().length][3];
        for (CenterModule module : modules) if (module != null) grades[module.ordinal()][2]++;
        FluidStack[] supply = { new FluidStack(FluidRegistry.WATER, 2_000_000_000),
            new FluidStack(FluidRegistry.WATER, 2_000_000_000), new FluidStack(FluidRegistry.WATER, 2_000_000_000),
            new FluidStack(FluidRegistry.WATER, 2_000_000_000), new FluidStack(FluidRegistry.WATER, 2_000_000_000),
            new FluidStack(CenterPlanner.boostFluid(0), 650_000_000),
            new FluidStack(CenterPlanner.boostFluid(1), 550_000_000) };
        int lo = 0, hi = CenterPlanner.MAX_BATCH_ROOTS / ores.size();
        while (lo + 1 < hi) {
            int quantity = (lo + hi) / 2;
            CenterPlanner.Session attempt = saturationPlan(graph, ores, grades, supply, voltage, inputEu, quantity);
            if (attempt.batch() != null && attempt.batch().roots == quantity * ores.size()) lo = quantity;
            else if (attempt.smallerRootLimit() > 0) hi = quantity;
            else throw new IllegalStateException(
                "Unexpected saturation calibration limit at " + quantity
                    + " ores/material, work="
                    + attempt.executions()
                    + "/"
                    + attempt.workLimit());
        }
        // Native bus order changes the random-roll order. Leave enough room for those independent rolls.
        int quantity = (int) (lo * .995);
        CenterPlanner.Session plan = saturationPlan(graph, ores, grades, supply, voltage, inputEu, quantity);
        if (plan.batch() == null || plan.batch().roots != quantity * ores.size()
            || plan.executions() < plan.workLimit() * .98)
            throw new IllegalStateException("Balanced saturation calibration failed at " + quantity + " ores/material");
        return new SaturationLoad(plan, quantity, hi);
    }

    private static CenterPlanner.Session saturationPlan(CenterGraph graph, List<ItemStack> ores, int[][] grades,
        FluidStack[] supply, long voltage, long inputEu, int quantity) {
        ItemStack[] stock = new ItemStack[ores.size()];
        for (int i = 0; i < stock.length; i++) {
            stock[i] = ores.get(i)
                .copy();
            stock[i].stackSize = quantity;
        }
        CenterPlanner.Session plan = new CenterPlanner.Session(
            java.util.Collections.singletonList(graph),
            stock,
            supply,
            grades,
            3,
            6,
            voltage,
            inputEu,
            CenterPlanner.HEAT[3],
            246813579L,
            false,
            false,
            CenterPlanner.MAX_BATCH_ROOTS,
            128);
        while (!plan.advance(64, Long.MAX_VALUE)) {
            // Calibration completes before the timed run.
        }
        return plan;
    }

    public static void verifyBulkSampler() {
        int trials = 1_500_000, samples = 4000;
        for (double chance : new double[] { .0001, .07, .5, .95 }) {
            java.util.Random random = new java.util.Random(246813579L);
            double expected = trials * chance, variance = expected * (1 - chance);
            double first = 0, second = 0;
            for (int i = 0; i < samples; i++) {
                int value = CenterBinomial.sample(random, trials, chance);
                if (value < 0 || value > trials) throw new IllegalStateException("Bulk sampler range");
                double error = value - expected;
                first += error;
                second += error * error;
            }
            if (Math.abs(first / samples) > 6 * Math.sqrt(variance / samples)
                || Math.abs(second / samples - variance) > .12 * variance)
                throw new IllegalStateException("Bulk sampler statistics at " + chance);
        }
    }

    public static void constructTcpPreset(IGregTechTileEntity controller) {
        if (new CenterStructure().construct(controller, 3, 63, false)) return;
        Frame frame = new Frame(controller);
        for (String key : new String[] { "tower-service-3", "outer-full" }) {
            List<int[]> points = new ArrayList<>();
            add(points, key, 0, -2, 0, 0);
            for (int[] point : points) {
                int x = frame.x + point[0], y = frame.y + point[1], z = frame.z + point[2];
                if (!frame.loaded(x, z))
                    throw new IllegalStateException("Unloaded native build cell " + x + "," + y + "," + z);
                if (frame.world.getTileEntity(x, y, z) != null)
                    throw new IllegalStateException("Occupied native build cell " + x + "," + y + "," + z);
            }
        }
        throw new IllegalStateException("Native full structure preflight rejected the owned empty site");
    }

    public static ChunkCoordIntPair unusedChunkInSectorBounds(List<int[]> footprint, int x, int z) {
        Set<ChunkCoordIntPair> used = new HashSet<>();
        for (int[] point : footprint) used.add(new ChunkCoordIntPair((x + point[0]) >> 4, (z + point[2]) >> 4));
        for (int sector = 0; sector < 6; sector++) {
            Shape part = CenterStructure.optionalShape("sector-" + sector);
            for (int cx = (x + part.minX) >> 4; cx <= (x + part.maxX) >> 4; cx++) {
                for (int cz = (z + 19 + part.minZ) >> 4; cz <= (z + 19 + part.maxZ) >> 4; cz++) {
                    ChunkCoordIntPair candidate = new ChunkCoordIntPair(cx, cz);
                    if (!used.contains(candidate)) return candidate;
                }
            }
        }
        throw new IllegalStateException("No unused chunk inside the sector bounds");
    }

    private static void addModule(List<int[]> positions, int slot, String module) {
        int[] at = CenterStructure.SLOTS[slot];
        add(positions, "module-" + slot + "-" + module + "-1", at[0], at[1] - 66, at[2], at[3]);
    }

    private static void add(List<int[]> positions, String resource, int x, int y, int z, int direction) {
        Shape shape = CenterStructure.optionalShape(resource);
        if (shape == null) throw new IllegalStateException("Missing fixture layout " + resource);
        for (int i = 0; i < shape.count(); i++) {
            int px = x + CenterStructure.rotateX(shape.x[i], shape.z[i], direction);
            int pz = z + CenterStructure.rotateZ(shape.x[i], shape.z[i], direction) + 19;
            positions.add(new int[] { px, y + Byte.toUnsignedInt(shape.y[i]), pz });
        }
    }

    /** Leaves terrain regeneration to the fixture journal and removes only claims created by this fixture. */
    public static void release(IGregTechTileEntity controller, int slot) {
        Frame frame = new Frame(controller);
        CenterClaims.get(frame.world)
            .release(frame, slot);
    }
}
