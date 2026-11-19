// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.dimension.testlab;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterGraph;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;
import com.miaokatze.gtsr.common.machine.orecenter.CenterProcess;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams.SteamGrade;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/** Natural world ticks with owned structure, assembled packs and ordinary GT hatches. */
final class TestLabIntegrationCenterMachine implements TestLabTask {

    static final String ID = "ore_integration_center.machine";
    private static final String PREFIX = "/assets/gtsr/orecenter/";
    private static final int ORE_COUNT = 8, STEAM_FILL = 4096;

    private final List<int[]> positions = new ArrayList<>();
    private int x, y, z, cursor, phase, pauseAt, steamAtPause, expected, outputCount;
    private long buildNanos, initialSequence;
    private IGregTechTileEntity controller, input, output;
    private MTEMineralIntegrationCenter center;
    private MTEHatchInput steam;
    private ItemStack ore, product;
    private FluidStack withheld;
    private NBTTagCompound checkpoint;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ore center assembled one-sector batch, native buses and saved progress";
    }

    @Override
    public int timeoutTicks() {
        return 3600;
    }

    @Override
    public void start(WorldServer world, int originX, int originY, int originZ) throws IOException {
        require(world.provider instanceof TestLabWorldProvider, "Expected the owned Lab dimension");
        x = originX + 120;
        y = originY;
        z = originZ + 36;
        cursor = phase = 0;
        pauseAt = -1;
        positions.clear();
        addOwnership("tower-0", 0, 0, 0, 0, false);
        addOwnership("outer-0", 0, 0, 0, 0, false);
        addOwnership("module-1-crusher-1", -46, 9, 49, 0, true);
        addOwnership("module-3-logistics-1", -76, 1, 66, 3, true);

        ore = GTOreDictUnificator.get(OrePrefixes.rawOre, Materials.Copper, ORE_COUNT);
        require(ore != null, "Missing GT copper");
        GTRecipe recipe = RecipeMaps.maceratorRecipes.findRecipeQuery().items(ore.copy()).voltage(32L).find();
        require(recipe != null && recipe.getOutputChance(0) == 10000, "Missing deterministic GT copper product");
        product = recipe.mOutputs[0].copy();
        expected = ORE_COUNT * product.stackSize;
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) {
        if (phase == 0) {
            int end = Math.min(positions.size(), cursor + 1024);
            while (cursor < end) {
                int[] pos = positions.get(cursor++);
                TestLabFixtures.track(world, pos[0], pos[1], pos[2]);
            }
            if (cursor == positions.size()) {
                controller = TestLabFixtures.machine(world, x, y, z, GTSRItemList.OreIntegrationCenter.get(1).getItemDamage());
                controller.disableWorking();
                center = (MTEMineralIntegrationCenter) controller.getMetaTileEntity();
                long begin = System.nanoTime();
                center.construct(GTSRItemList.OreIntegrationCenter.get(1), false);
                buildNanos = System.nanoTime() - begin;
                input = place(world, -5, ItemList.Hatch_Input_Bus_LV);
                output = place(world, -3, ItemList.Hatch_Output_Bus_LV);
                steam = (MTEHatchInput) place(world, -1, ItemList.Hatch_Input_IV).getMetaTileEntity();
                input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.CRUSHER, 1));
                phase = 1;
            }
        } else if (phase == 1 && center.installModuleFromBus(1)) {
            require(input.getStackInSlot(0) == null, "Crusher pack was not charged through real input bus");
            input.setInventorySlotContents(0, CenterModuleItems.stack(CenterModule.LOGISTICS, 1));
            phase = 2;
        } else if (phase == 2 && center.installModuleFromBus(3)) {
            require(input.getStackInSlot(0) == null, "Logistics pack was not charged through real input bus");
            require(center.setGraphs(Arrays.asList(new CenterGraph(7, true, Arrays.asList("rawOreCopper"),
                Arrays.asList(new CenterGraph.Node(CenterProcess.CRUSHER, new int[] { -1, -1, -1 }))))),
                "Controller rejected valid copper graph");
            supply();
            for (int i = 0; i < output.getSizeInventory(); i++) output.setInventorySlotContents(i, new ItemStack(Blocks.cobblestone, 64));
            input.setInventorySlotContents(0, ore.copy());
            initialSequence = center.batchSequence();
            controller.enableWorking();
            phase = 3;
        } else if (phase == 3 && center.batchSequence() > initialSequence && center.batchProgress() >= 8) {
            require(input.getStackInSlot(0) == null, "Paid copper remains in real input bus");
            require(quantity(output, product) == 0, "Outputs appeared before recipe completion");
            checkpoint = new NBTTagCompound();
            center.saveNBTData(checkpoint);
            steamAtPause = steam.getFillableStack().amount;
            withheld = steam.getFillableStack().copy();
            steam.setFillableStack(null);
            pauseAt = elapsedTicks;
            phase = 4;
        } else if (phase == 4 && elapsedTicks >= pauseAt + 8) {
            require(center.batchProgress() == checkpoint.getInteger("CenterElapsed"), "Discontinuous steam advanced progress");
            center.loadNBTData(checkpoint);
            require(center.batchSequence() == initialSequence + 1, "NBT restore changed batch identity");
            require(center.batchProgress() == checkpoint.getInteger("CenterElapsed"), "NBT restore lost saved progress");
            steam.setFillableStack(withheld);
            phase = 5;
        } else if (phase == 5 && center.batchProgress() >= checkpoint.getInteger("CenterDuration")) {
            require(quantity(output, product) == 0, "Full output bus accepted more products");
            NBTTagCompound after = new NBTTagCompound();
            center.saveNBTData(after);
            require(after.getTagList("CenterItemOutputs", 10).tagCount() > 0, "Full bus lost pending outputs");
            long paid = STEAM_FILL - steam.getFillableStack().amount;
            int density = SteamGrade.DENSE_STEAM.getDivisor();
            require(paid == (checkpoint.getLong("CenterSteam") + density - 1) / density, "Steam paid is not the frozen batch total");
            for (int i = 0; i < output.getSizeInventory(); i++) output.setInventorySlotContents(i, null);
            phase = 6;
        } else if (phase == 6) {
            outputCount = quantity(output, product);
            if (outputCount == expected) {
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("ore", ORE_COUNT);
                report.put("nativeOutput", expected);
                report.put("actualOutput", outputCount);
                report.put("steamAtCheckpoint", steamAtPause);
                report.put("footprintSamples", positions.size());
                report.put("constructNanos", buildNanos);
                report.put("restoreScope", "live controller NBT save/load");
                return new TestLabReport.Result(TestLabReport.Status.PASS,
                    "Live assembly, steam stall, in-place NBT restoration and unblocking the output", report);
            }
            require(outputCount < expected, "Output was duplicated");
        }
        return null;
    }

    private IGregTechTileEntity place(WorldServer world, int offset, ItemList item) {
        return TestLabFixtures.machine(world, x + offset, y, z + 1, item.get(1).getItemDamage());
    }

    private void supply() {
        FluidStack dense = FluidRegistry.getFluidStack(SteamGrade.DENSE_STEAM.getFluidName(), STEAM_FILL);
        require(dense != null && steam.fill(ForgeDirection.UNKNOWN, dense, true) == STEAM_FILL, "Native input hatch rejected dense steam");
    }

    private void addOwnership(String key, int ox, int oy, int oz, int rotation, boolean module) throws IOException {
        InputStream asset = TestLabIntegrationCenterMachine.class.getResourceAsStream(PREFIX + key + ".gz");
        if (asset == null) throw new IOException("No packaged fixture geometry " + key);
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(asset))) {
            int palette = in.readUnsignedShort(), count = in.readInt();
            for (int i = 0; i < palette; i++) {
                byte[] entry = new byte[in.readUnsignedByte() + 1];
                in.readFully(entry);
            }
            for (int i = 0; i < count; i++) {
                int px = in.readShort(), py = in.readUnsignedByte(), pz = in.readShort();
                in.readUnsignedByte();
                int rx = rotation == 3 ? pz : px;
                int rz = rotation == 3 ? -px : pz;
                positions.add(new int[] { x + ox + rx, y + oy + py - (module ? 66 : 2), z + oz + rz + 19 });
            }
        }
    }

    private static int quantity(IGregTechTileEntity bus, ItemStack stack) {
        int sum = 0;
        for (int i = 0; i < bus.getSizeInventory(); i++) {
            ItemStack entry = bus.getStackInSlot(i);
            if (GTUtility.areStacksEqual(entry, stack, false)) sum += entry.stackSize;
        }
        return sum;
    }

    @Override
    public void stop(WorldServer world) {
        if (controller != null) controller.disableWorking();
        if (steam != null) steam.setFillableStack(null);
    }

    @Override
    public String debug(WorldServer world) {
        return "ore center machine phase=" + phase + " journal=" + cursor
            + " progress=" + (center == null ? 0 : center.batchProgress()) + " output=" + outputCount;
    }

    private static void require(boolean condition, String text) {
        if (!condition) throw new IllegalStateException(text);
    }
}
