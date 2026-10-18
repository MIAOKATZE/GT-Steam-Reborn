package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.HatchElementBuilder;

/** Actual production input element, native predicate/adder/skip, and real registered hatch instances. */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class TestLabStructurePolicyTask implements TestLabTask {

    private final MetaTileEntityID machineId;
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final List<int[]> owned = new ArrayList<>();
    private MTEMultiBlockBase machine;
    private int stationX = 28672, stationZ = 28672;
    private TestLabReport.Result result;

    private TestLabStructurePolicyTask(MetaTileEntityID machineId) {
        this.machineId = machineId;
    }

    static List<TestLabTask> select(String requested) {
        List<TestLabTask> selected = new ArrayList<>();
        for (MetaTileEntityID id : new MetaTileEntityID[] { MetaTileEntityID.CLUSTER_CONTROLLER,
            MetaTileEntityID.CLUSTER_UNIT_LOGISTICS, MetaTileEntityID.CLUSTER_BOOSTER_PARALLEL,
            MetaTileEntityID.CLUSTER_BOOSTER_SPEED, MetaTileEntityID.CLUSTER_BOOSTER_PRIMARY,
            MetaTileEntityID.CLUSTER_BOOSTER_SECONDARY, MetaTileEntityID.CLUSTER_BOOSTER_STEAM_SAVER }) {
            TestLabStructurePolicyTask task = new TestLabStructurePolicyTask(id);
            if ("all".equals(requested) || "negative".equals(requested)
                || "policy".equals(requested)
                || task.id()
                    .equals(requested))
                selected.add(task);
        }
        return selected;
    }

    @Override
    public String id() {
        return "structure.policy." + machineId.name()
            .toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String displayName() {
        return machineId.name() + " native input policy";
    }

    @Override
    public int timeoutTicks() {
        return 200;
    }

    @Override
    public boolean requiresFixtureChunks() {
        return false;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        // Two one-block fixtures in an explicitly separate station; never clear a pre-existing block.
        TestLabFixtures.requireChunk(world, stationX >> 4, stationZ >> 4);
        while (!world.blockExists(stationX, 120, stationZ) || !world.blockExists(stationX + 2, 120, stationZ)
            || !world.isAirBlock(stationX, 120, stationZ)
            || !world.isAirBlock(stationX + 2, 120, stationZ)) {
            stationX += 32;
            if (stationX > 29696) throw new IllegalStateException("FIXTURE_BLOCKED policy stations occupied");
            TestLabFixtures.requireChunk(world, stationX >> 4, stationZ >> 4);
        }
        owned.add(new int[] { stationX, 120, stationZ });
        owned.add(new int[] { stationX + 2, 120, stationZ });
        IGregTechTileEntity tile = TestLabFixtures.machine(world, stationX, 120, stationZ, machineId.ID);
        machine = (MTEMultiBlockBase) tile.getMetaTileEntity();
        IStructureElementChain chain;
        if (machineId == MetaTileEntityID.CLUSTER_CONTROLLER) {
            Method method = ClusterStructureDef.class.getDeclaredMethod("casingOrControllerInputSlot");
            method.setAccessible(true);
            chain = (IStructureElementChain) method.invoke(null);
        } else {
            Method method = findMethod(machine.getClass(), "tieredCasingElement");
            chain = (IStructureElementChain) method.invoke(machine);
        }
        IStructureElement hatchElement = chain.fallbacks()[0];
        HatchElementBuilder builder = nativeBuilder(hatchElement);
        evidence.put(
            "productionElement",
            hatchElement.getClass()
                .getName());
        evidence.put(
            "controllerClass",
            machine.getClass()
                .getName());
        evidence.put(
            "gtBuilderSource",
            HatchElementBuilder.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString());
        evidence.put(
            "productionSource",
            machine.getClass()
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toString());
        List<Map<String, Object>> observations = new ArrayList<>();
        for (ItemList hatch : new ItemList[] { ItemList.Hatch_CraftingInput_Bus_ME,
            ItemList.Hatch_CraftingInput_Bus_ME_ItemOnly, ItemList.Hatch_CraftingInput_Bus_Slave }) {
            observe(world, hatchElement, builder, hatch, false, observations);
        }
        for (ItemList hatch : new ItemList[] { ItemList.Hatch_Input_LV, ItemList.Hatch_Input_ME }) {
            observe(world, hatchElement, builder, hatch, true, observations);
        }
        if (machineId == MetaTileEntityID.CLUSTER_UNIT_LOGISTICS) {
            for (ItemList hatch : new ItemList[] { ItemList.Hatch_Input_Bus_LV, ItemList.Hatch_Input_Bus_ME }) {
                observe(world, hatchElement, builder, hatch, true, observations);
            }
        }
        machine.clearHatches();
        evidence.put("observations", observations);
        evidence.put("failures", failures);
        evidence.put(
            "boundary",
            "Real registered GT hatches and production native input element only; no full machine shape or recipe execution.");
        result = new TestLabReport.Result(
            failures.isEmpty() ? TestLabReport.Status.PASS : TestLabReport.Status.FAIL,
            failures.isEmpty() ? "Unsupported dual rejected; standard and stocking ME inputs retained"
                : String.join("; ", failures),
            evidence);
    }

    private void observe(WorldServer world, IStructureElement element, HatchElementBuilder builder, ItemList hatch,
        boolean expected, List<Map<String, Object>> observations) {
        machine.clearHatches();
        world.setBlockToAir(stationX + 2, 120, stationZ);
        ItemStack stack = hatch.get(1);
        IGregTechTileEntity hatchTile = TestLabFixtures
            .machine(world, stationX + 2, 120, stationZ, stack.getItemDamage());
        ItemStack trigger = new ItemStack(net.minecraft.init.Items.stick);
        IStructureElement.BlocksToPlace blocks = element
            .getBlocksToPlace(machine, world, stationX + 2, 120, stationZ, trigger, null);
        boolean filter = blocks.getPredicate()
            .test(stack);
        boolean skip = builder.getShouldSkip()
            .test(machine, hatchTile);
        boolean accepted = element.check(machine, world, stationX + 2, 120, stationZ);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hatch", hatch.name());
        row.put(
            "class",
            hatchTile.getMetaTileEntity()
                .getClass()
                .getName());
        row.put("expectedAccepted", expected);
        row.put("predicateOnly", blocks.getStacks() == null);
        row.put("filter", filter);
        row.put("shouldSkip", skip);
        row.put("worldAdderAccepted", accepted);
        row.put("ordinaryFluidInputs", machine.mInputHatches.size());
        row.put("ordinaryItemInputs", machine.mInputBusses.size());
        row.put("dualInputs", machine.mDualInputHatches.size());
        observations.add(row);
        if (filter != expected || skip != expected
            || accepted != expected
            || blocks.getStacks() != null
            || !machine.mDualInputHatches.isEmpty())
            failures.add(
                hatch.name() + ": filter="
                    + filter
                    + ", skip="
                    + skip
                    + ", adder="
                    + accepted
                    + ", dual="
                    + machine.mDualInputHatches.size());
    }

    private static Method findMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    private static HatchElementBuilder nativeBuilder(IStructureElement element) throws IllegalAccessException {
        for (Field field : element.getClass()
            .getDeclaredFields()) {
            if (!HatchElementBuilder.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            return (HatchElementBuilder) field.get(element);
        }
        throw new IllegalStateException("Production input element is not a native HatchElementBuilder element");
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        return result;
    }

    @Override
    public void stop(WorldServer world) {
        if (machine != null) machine.clearHatches();
        for (int[] position : owned) world.setBlockToAir(position[0], position[1], position[2]);
        owned.clear();
    }
}
